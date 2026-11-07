// Package config 负责 JSON 配置文件的加载、默认值与校验。
package config

import (
	"encoding/json"
	"fmt"
	"net/url"
	"os"
	"strings"
	"time"
)

// Config 是代理的全部配置。
type Config struct {
	DeviceID  string    `json:"deviceId"`
	Token     string    `json:"token"`
	Server    Server    `json:"server"`
	Printers  []Printer `json:"printers"`
	Limits    Limits    `json:"limits"`
	Reconnect Reconnect `json:"reconnect"`
	Log       Log       `json:"log"`
}

// Server 云端接入配置。
type Server struct {
	URL                string `json:"url"`
	CAFile             string `json:"caFile"`
	InsecureSkipVerify bool   `json:"insecureSkipVerify"`
	ServerName         string `json:"serverName"`
}

// Printer 单台打印机的 p910nd 接入配置。
type Printer struct {
	ID               string `json:"id"`
	Name             string `json:"name"`
	Host             string `json:"host"`
	Port             int    `json:"port"`
	ConnectTimeoutMs int    `json:"connectTimeoutMs"`
	WriteTimeoutMs   int    `json:"writeTimeoutMs"`
	Enabled          *bool  `json:"enabled"`
}

// Limits 资源与队列上限。
type Limits struct {
	MaxTaskBytes int64 `json:"maxTaskBytes"`
	QueueSize    int   `json:"queueSize"`
}

// Reconnect 重连退避区间。
type Reconnect struct {
	MinMs int `json:"minMs"`
	MaxMs int `json:"maxMs"`
}

// Log 日志配置。
type Log struct {
	Level     string `json:"level"`
	File      string `json:"file"`
	MaxSizeKB int    `json:"maxSizeKB"`
}

// Default 返回带默认值的配置，Load 时先套默认值再覆盖。
func Default() *Config {
	enabled := true
	return &Config{
		Server: Server{},
		Printers: []Printer{{
			ID:               "default",
			Name:             "标签打印机",
			Host:             "127.0.0.1",
			Port:             9100,
			ConnectTimeoutMs: 3000,
			WriteTimeoutMs:   15000,
			Enabled:          &enabled,
		}},
		Limits:    Limits{MaxTaskBytes: 4 * 1024 * 1024, QueueSize: 8},
		Reconnect: Reconnect{MinMs: 1000, MaxMs: 30000},
		Log:       Log{Level: "info", MaxSizeKB: 512},
	}
}

// Load 读取并校验配置文件。
func Load(path string) (*Config, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读取配置 %s 失败: %w", path, err)
	}
	cfg := Default()
	if err := json.Unmarshal(raw, cfg); err != nil {
		return nil, fmt.Errorf("解析配置 %s 失败: %w", path, err)
	}
	if err := cfg.Validate(); err != nil {
		return nil, err
	}
	return cfg, nil
}

// Validate 校验配置合法性，避免运行期出现难以定位的失败。
func (c *Config) Validate() error {
	if strings.TrimSpace(c.DeviceID) == "" {
		return fmt.Errorf("配置缺少 deviceId")
	}
	if strings.TrimSpace(c.Token) == "" {
		return fmt.Errorf("配置缺少 token")
	}
	u, err := url.Parse(c.Server.URL)
	if err != nil || u.Host == "" {
		return fmt.Errorf("server.url 非法: %q", c.Server.URL)
	}
	if u.Scheme != "wss" && u.Scheme != "ws" {
		return fmt.Errorf("server.url 仅支持 ws/wss，当前为 %q", u.Scheme)
	}
	if len(c.Printers) == 0 {
		return fmt.Errorf("至少需要配置一台打印机")
	}
	seen := make(map[string]struct{}, len(c.Printers))
	enabledCount := 0
	for i := range c.Printers {
		p := &c.Printers[i]
		p.ID = strings.TrimSpace(p.ID)
		if p.ID == "" {
			return fmt.Errorf("printers[%d] 缺少 id", i)
		}
		if _, ok := seen[p.ID]; ok {
			return fmt.Errorf("打印机 id 重复: %s", p.ID)
		}
		seen[p.ID] = struct{}{}
		if p.Host == "" {
			p.Host = "127.0.0.1"
		}
		if p.Port == 0 {
			p.Port = 9100
		}
		if p.Port < 1 || p.Port > 65535 {
			return fmt.Errorf("打印机 %s 的 port 非法: %d", p.ID, p.Port)
		}
		if p.ConnectTimeoutMs <= 0 {
			p.ConnectTimeoutMs = 3000
		}
		if p.WriteTimeoutMs <= 0 {
			p.WriteTimeoutMs = 15000
		}
		if p.Name == "" {
			p.Name = p.ID
		}
		if p.IsEnabled() {
			enabledCount++
		}
	}
	if enabledCount == 0 {
		return fmt.Errorf("至少需要启用一台打印机")
	}
	if c.Limits.MaxTaskBytes < 1024 || c.Limits.MaxTaskBytes > 64*1024*1024 {
		return fmt.Errorf("limits.maxTaskBytes 需在 1KB~64MB 之间，当前 %d", c.Limits.MaxTaskBytes)
	}
	if c.Limits.QueueSize < 1 || c.Limits.QueueSize > 1024 {
		return fmt.Errorf("limits.queueSize 需在 1~1024 之间，当前 %d", c.Limits.QueueSize)
	}
	if c.Reconnect.MinMs < 200 {
		c.Reconnect.MinMs = 200
	}
	if c.Reconnect.MaxMs < c.Reconnect.MinMs {
		c.Reconnect.MaxMs = c.Reconnect.MinMs
	}
	switch strings.ToLower(c.Log.Level) {
	case "debug", "info", "warn", "error":
	default:
		return fmt.Errorf("log.level 非法: %q", c.Log.Level)
	}
	return nil
}

// IsEnabled 未显式配置时默认启用。
func (p Printer) IsEnabled() bool {
	return p.Enabled == nil || *p.Enabled
}

// ConnectTimeout 返回连接 p910nd 的超时。
func (p Printer) ConnectTimeout() time.Duration {
	return time.Duration(p.ConnectTimeoutMs) * time.Millisecond
}

// WriteTimeout 返回写入数据的超时。
func (p Printer) WriteTimeout() time.Duration {
	return time.Duration(p.WriteTimeoutMs) * time.Millisecond
}

// MinBackoff 返回重连最小退避。
func (c *Config) MinBackoff() time.Duration {
	return time.Duration(c.Reconnect.MinMs) * time.Millisecond
}

// MaxBackoff 返回重连最大退避。
func (c *Config) MaxBackoff() time.Duration {
	return time.Duration(c.Reconnect.MaxMs) * time.Millisecond
}
