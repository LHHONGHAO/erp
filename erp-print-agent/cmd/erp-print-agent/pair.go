package main

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/jsherp/erp-print-agent/internal/config"
)

// pairRequest 是 /printAgent/device/pair 的请求体，门店、型号与打印机随配对一起上报。
type pairRequest struct {
	PairCode     string           `json:"pairCode"`
	StoreName    string           `json:"storeName"`
	PrinterModel string           `json:"printerModel"`
	Printers     []config.Printer `json:"printers"`
	Version      string           `json:"version"`
}

type pairResponse struct {
	Code int             `json:"code"`
	Data json.RawMessage `json:"data"`
}

type pairResult struct {
	DeviceID  string `json:"deviceId"`
	Token     string `json:"token"`
	ServerURL string `json:"serverUrl"`
}

// runPair 用一次性配对码向主机换取 deviceId 与 token，并写回配置文件。
func runPair(args []string) error {
	fs := flag.NewFlagSet("pair", flag.ExitOnError)
	cfgPath := fs.String("config", defaultConfigPath, "配置文件路径")
	code := fs.String("code", "", "6 位配对码")
	serverURL := fs.String("url", "", "通信地址，留空使用配置文件中的 server.url")
	storeName := fs.String("store", "", "门店名称")
	printerModel := fs.String("model", "", "打印机型号")
	_ = fs.Parse(args)

	codeText := strings.TrimSpace(*code)
	if len(codeText) != 6 || strings.Trim(codeText, "0123456789") != "" {
		return fmt.Errorf("配对码应为 6 位数字")
	}

	cfg, err := config.LoadLoose(*cfgPath)
	if err != nil {
		return err
	}
	if strings.TrimSpace(*serverURL) != "" {
		cfg.Server.URL = strings.TrimSpace(*serverURL)
	}
	if strings.TrimSpace(cfg.Server.URL) == "" {
		return fmt.Errorf("通信地址不能为空")
	}
	if strings.TrimSpace(*storeName) != "" {
		cfg.StoreName = strings.TrimSpace(*storeName)
	}
	if strings.TrimSpace(*printerModel) != "" {
		cfg.PrinterModel = strings.TrimSpace(*printerModel)
	}

	base, err := httpBaseURL(cfg.Server.URL)
	if err != nil {
		return err
	}
	payload, err := json.Marshal(pairRequest{
		PairCode:     codeText,
		StoreName:    cfg.StoreName,
		PrinterModel: cfg.PrinterModel,
		Printers:     cfg.Printers,
		Version:      version,
	})
	if err != nil {
		return err
	}

	client := &http.Client{
		Timeout: 15 * time.Second,
		Transport: &http.Transport{
			TLSClientConfig: &tls.Config{
				// 主机多为自签名证书，配对只针对用户指定的地址，默认跳过校验
				InsecureSkipVerify: true,
				MinVersion:         tls.VersionTLS12,
			},
		},
	}
	resp, err := client.Post(base+"/printAgent/device/pair", "application/json", bytes.NewReader(payload))
	if err != nil {
		return fmt.Errorf("连接主机失败: %w", err)
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("读取主机响应失败: %w", err)
	}

	var result pairResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return fmt.Errorf("主机响应无法解析: %w", err)
	}
	if result.Code != 200 {
		return fmt.Errorf("主机拒绝配对: %s", responseMessage(result.Data))
	}
	var data pairResult
	if err := json.Unmarshal(result.Data, &data); err != nil {
		return fmt.Errorf("主机响应缺少设备信息: %w", err)
	}
	if data.DeviceID == "" || data.Token == "" {
		return fmt.Errorf("主机未下发设备编号或 Token")
	}

	cfg.DeviceID = data.DeviceID
	cfg.Token = data.Token
	if strings.TrimSpace(data.ServerURL) != "" {
		cfg.Server.URL = data.ServerURL
	}
	if err := cfg.Validate(); err != nil {
		return fmt.Errorf("配对成功但配置校验失败: %w", err)
	}
	if err := config.Save(*cfgPath, cfg); err != nil {
		return fmt.Errorf("写入配置失败: %w", err)
	}
	fmt.Printf("配对成功: 设备=%s 云端=%s\n", cfg.DeviceID, cfg.Server.URL)
	return nil
}

// httpBaseURL 把 ws/wss 地址转换成 http/https 地址，用于调用配对接口。
func httpBaseURL(raw string) (string, error) {
	u, err := url.Parse(strings.TrimSpace(raw))
	if err != nil || u.Host == "" {
		return "", fmt.Errorf("通信地址非法: %q", raw)
	}
	switch strings.ToLower(u.Scheme) {
	case "ws":
		return "http://" + u.Host, nil
	case "wss":
		return "https://" + u.Host, nil
	default:
		return "", fmt.Errorf("通信地址仅支持 ws/wss，当前为 %q", u.Scheme)
	}
}

func responseMessage(raw json.RawMessage) string {
	var text string
	if err := json.Unmarshal(raw, &text); err == nil && strings.TrimSpace(text) != "" {
		return text
	}
	trimmed := strings.TrimSpace(string(raw))
	if trimmed == "" {
		return "未知错误"
	}
	return trimmed
}
