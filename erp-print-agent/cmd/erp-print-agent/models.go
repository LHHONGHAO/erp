package main

import (
	"crypto/tls"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/jsherp/erp-print-agent/internal/config"
)

// defaultModelsCache 型号列表缓存，主机不可达时 LuCI 用上一次下发的型号渲染下拉框。
const defaultModelsCache = "/etc/erp-print-agent/models.json"

type modelsResponse struct {
	Code int             `json:"code"`
	Data json.RawMessage `json:"data"`
}

// runModels 用设备 Token 拉取主机维护的打印机型号，输出 JSON 数组供 LuCI 型号下拉使用。
func runModels(args []string) error {
	fs := flag.NewFlagSet("models", flag.ExitOnError)
	cfgPath := fs.String("config", defaultConfigPath, "配置文件路径")
	cachePath := fs.String("cache", defaultModelsCache, "型号缓存文件")
	_ = fs.Parse(args)

	cfg, err := config.Load(*cfgPath)
	if err != nil {
		return err
	}
	base, err := httpBaseURL(cfg.Server.URL)
	if err != nil {
		return err
	}

	client := &http.Client{
		Timeout: 4 * time.Second,
		Transport: &http.Transport{
			TLSClientConfig: &tls.Config{
				// 局域网主机多为自签名证书，型号下拉只是配置页辅助信息，失败会回退缓存
				InsecureSkipVerify: true,
				MinVersion:         tls.VersionTLS12,
			},
		},
	}
	req, err := http.NewRequest(http.MethodGet, base+"/printAgent/device/models", nil)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+strings.TrimSpace(cfg.Token))
	req.Header.Set("X-Device-Id", strings.TrimSpace(cfg.DeviceID))
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("连接主机失败: %w", err)
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, 256*1024))
	if err != nil {
		return fmt.Errorf("读取型号列表失败: %w", err)
	}

	var result modelsResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return fmt.Errorf("主机响应无法解析: %w", err)
	}
	if result.Code != 200 {
		return fmt.Errorf("主机返回: %s", responseMessage(result.Data))
	}
	var models []map[string]interface{}
	if err := json.Unmarshal(result.Data, &models); err != nil {
		return fmt.Errorf("型号列表无法解析: %w", err)
	}
	out, err := json.Marshal(models)
	if err != nil {
		return err
	}
	if err := os.WriteFile(*cachePath, out, 0o644); err != nil {
		fmt.Fprintf(os.Stderr, "警告: 型号缓存写入 %s 失败(%v)\n", *cachePath, err)
	}
	fmt.Println(string(out))
	return nil
}
