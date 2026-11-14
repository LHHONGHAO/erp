// erp-print-agent 是运行在 OpenWrt 路由器上的远程打印代理。
// 它把云端 ERP 下发的原始 TSPL 数据原样转发给本机 p910nd，不解析、不改写打印内容。
package main

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/pem"
	"flag"
	"fmt"
	"net"
	"net/url"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/jsherp/erp-print-agent/internal/agent"
	"github.com/jsherp/erp-print-agent/internal/config"
	"github.com/jsherp/erp-print-agent/internal/logx"
	"github.com/jsherp/erp-print-agent/internal/printer"
)

// version 由构建时 -ldflags "-X main.version=..." 注入。
var version = "dev"

const defaultConfigPath = "/etc/erp-print-agent/config.json"

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintf(os.Stderr, "错误: %v\n", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	cmd := "serve"
	if len(args) > 0 && !strings.HasPrefix(args[0], "-") {
		cmd = args[0]
		args = args[1:]
	}
	switch cmd {
	case "serve":
		return runServe(args)
	case "pair":
		return runPair(args)
	case "print":
		return runPrint(args)
	case "selftest":
		return runSelftest(args)
	case "check":
		return runCheck(args)
	case "fetch-ca":
		return runFetchCA(args)
	case "version":
		fmt.Printf("erp-print-agent %s\n", version)
		return nil
	default:
		return fmt.Errorf("未知命令 %q，可用命令: serve | pair | print | selftest | check | fetch-ca | version", cmd)
	}
}

func runServe(args []string) error {
	fs := flag.NewFlagSet("serve", flag.ExitOnError)
	cfgPath := fs.String("config", defaultConfigPath, "配置文件路径")
	_ = fs.Parse(args)

	cfg, err := config.Load(*cfgPath)
	if err != nil {
		return err
	}
	log, logErr := logx.New(cfg.Log.Level, cfg.Log.File, cfg.Log.MaxSizeKB)
	if logErr != nil {
		fmt.Fprintf(os.Stderr, "警告: %v\n", logErr)
	}
	defer log.Close()

	enabled := 0
	for _, p := range cfg.Printers {
		if p.IsEnabled() {
			enabled++
		}
	}
	log.Infof("erp-print-agent %s 启动: 设备=%s 打印机=%d 台 云端=%s",
		version, cfg.DeviceID, enabled, cfg.Server.URL)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	agent.New(cfg, log, version).Run(ctx)
	log.Infof("erp-print-agent 已停止")
	return nil
}

func runPrint(args []string) error {
	fs := flag.NewFlagSet("print", flag.ExitOnError)
	cfgPath := fs.String("config", defaultConfigPath, "配置文件路径")
	printerID := fs.String("printer", "default", "打印机 id")
	file := fs.String("file", "", "TSPL 文件路径")
	data := fs.String("data", "", "直接指定 TSPL 文本（调试用，与 -file 二选一）")
	_ = fs.Parse(args)

	payload, err := readPayload(*file, *data)
	if err != nil {
		return err
	}
	cfg, err := config.Load(*cfgPath)
	if err != nil {
		return err
	}
	p, err := findPrinter(cfg, *printerID)
	if err != nil {
		return err
	}
	client := printer.NewClient(*p)
	start := time.Now()
	written, err := client.Print(payload)
	if err != nil {
		return fmt.Errorf("发送到 %s 失败: %w", client.Address(), err)
	}
	fmt.Printf("已发送 %d 字节到 %s（耗时 %dms）\n", written, client.Address(), time.Since(start).Milliseconds())
	return nil
}

func runSelftest(args []string) error {
	fs := flag.NewFlagSet("selftest", flag.ExitOnError)
	cfgPath := fs.String("config", defaultConfigPath, "配置文件路径")
	printerID := fs.String("printer", "default", "打印机 id")
	_ = fs.Parse(args)

	cfg, err := config.Load(*cfgPath)
	if err != nil {
		return err
	}
	p, err := findPrinter(cfg, *printerID)
	if err != nil {
		return err
	}
	client := printer.NewClient(*p)
	payload := []byte(selftestTSpl())
	start := time.Now()
	written, err := client.Print(payload)
	if err != nil {
		return fmt.Errorf("发送到 %s 失败: %w", client.Address(), err)
	}
	fmt.Printf("自检标签已发送 %d 字节到 %s（耗时 %dms）\n", written, client.Address(), time.Since(start).Milliseconds())
	return nil
}

func runCheck(args []string) error {
	fs := flag.NewFlagSet("check", flag.ExitOnError)
	cfgPath := fs.String("config", defaultConfigPath, "配置文件路径")
	_ = fs.Parse(args)

	cfg, err := config.Load(*cfgPath)
	if err != nil {
		return err
	}
	fmt.Printf("配置有效: 设备=%s 云端=%s\n", cfg.DeviceID, cfg.Server.URL)
	for _, p := range cfg.Printers {
		if !p.IsEnabled() {
			fmt.Printf("  [跳过] %s (%s) 已禁用\n", p.ID, p.Name)
			continue
		}
		client := printer.NewClient(p)
		if err := client.Check(); err != nil {
			fmt.Printf("  [失败] %s (%s) %s 不可达: %v\n", p.ID, p.Name, client.Address(), err)
			continue
		}
		fmt.Printf("  [正常] %s (%s) %s 可达\n", p.ID, p.Name, client.Address())
	}
	return nil
}

func runFetchCA(args []string) error {
	fs := flag.NewFlagSet("fetch-ca", flag.ExitOnError)
	serverURL := fs.String("url", "", "wss:// 或 https:// 地址，用于抓取对端证书")
	out := fs.String("out", "ca.pem", "证书输出路径")
	_ = fs.Parse(args)

	if *serverURL == "" {
		return fmt.Errorf("缺少 -url 参数")
	}
	u, err := url.Parse(*serverURL)
	if err != nil || u.Host == "" {
		return fmt.Errorf("url 非法: %q", *serverURL)
	}
	host := u.Host
	if _, _, err := net.SplitHostPort(host); err != nil {
		host = net.JoinHostPort(u.Hostname(), "443")
	}

	dialer := &net.Dialer{Timeout: 10 * time.Second}
	conn, err := tls.DialWithDialer(dialer, "tcp", host, &tls.Config{
		InsecureSkipVerify: true,
		ServerName:         u.Hostname(),
		MinVersion:         tls.VersionTLS12,
	})
	if err != nil {
		return fmt.Errorf("连接 %s 失败: %w", host, err)
	}
	defer conn.Close()

	certs := conn.ConnectionState().PeerCertificates
	if len(certs) == 0 {
		return fmt.Errorf("%s 未返回证书", host)
	}
	f, err := os.Create(*out)
	if err != nil {
		return err
	}
	defer f.Close()
	for _, cert := range certs {
		if err := pem.Encode(f, &pem.Block{Type: "CERTIFICATE", Bytes: cert.Raw}); err != nil {
			return err
		}
	}
	leaf, _ := x509.ParseCertificate(certs[0].Raw)
	if leaf != nil {
		fmt.Printf("主体: %s\n有效期至: %s\n", leaf.Subject.String(), leaf.NotAfter.Format("2006-01-02"))
		if len(leaf.DNSNames) > 0 {
			fmt.Printf("SAN DNS: %s\n", strings.Join(leaf.DNSNames, ", "))
		}
		if len(leaf.IPAddresses) > 0 {
			ips := make([]string, 0, len(leaf.IPAddresses))
			for _, ip := range leaf.IPAddresses {
				ips = append(ips, ip.String())
			}
			fmt.Printf("SAN IP : %s\n", strings.Join(ips, ", "))
		}
	}
	fmt.Printf("已保存 %d 张证书到 %s\n", len(certs), *out)
	return nil
}

func readPayload(file, data string) ([]byte, error) {
	switch {
	case file != "" && data != "":
		return nil, fmt.Errorf("-file 与 -data 只能指定一个")
	case file != "":
		raw, err := os.ReadFile(file)
		if err != nil {
			return nil, fmt.Errorf("读取 %s 失败: %w", file, err)
		}
		if len(raw) == 0 {
			return nil, fmt.Errorf("文件 %s 内容为空", file)
		}
		return raw, nil
	case data != "":
		return []byte(data), nil
	default:
		return nil, fmt.Errorf("需要指定 -file 或 -data")
	}
}

func findPrinter(cfg *config.Config, id string) (*config.Printer, error) {
	for i := range cfg.Printers {
		p := &cfg.Printers[i]
		if p.ID != id {
			continue
		}
		if !p.IsEnabled() {
			return nil, fmt.Errorf("打印机 %s 已被禁用", id)
		}
		return p, nil
	}
	return nil, fmt.Errorf("未找到打印机 %q", id)
}

// selftestTSpl 返回一张最小测试标签的 TSPL 指令（仅用于自检，代理本身不解析 TSPL）。
func selftestTSpl() string {
	lines := []string{
		"SIZE 40 mm,30 mm",
		"GAP 2 mm,0",
		"CLS",
		`TEXT 16,16,"3",0,1,1,"ERP PRINT AGENT"`,
		`TEXT 16,56,"2",0,1,1,"SELFTEST OK"`,
		`BARCODE 16,96,"128",60,1,0,2,2,"12345678"`,
		"PRINT 1,1",
	}
	return strings.Join(lines, "\r\n") + "\r\n"
}
