// Package wsclient 维护与云端 ERP 的 WebSocket Secure 长连接：
// 设备认证、应用层心跳、断线指数退避重连。
package wsclient

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"errors"
	"fmt"
	"math/rand"
	"net"
	"net/http"
	"os"
	"strings"
	"sync/atomic"
	"time"

	"github.com/gorilla/websocket"

	"github.com/jsherp/erp-print-agent/internal/config"
	"github.com/jsherp/erp-print-agent/internal/logx"
	"github.com/jsherp/erp-print-agent/internal/protocol"
)

// Subprotocol 握手时声明的子协议。
const Subprotocol = "erp-print-agent.v1"

// 连接不可用时的错误。
var (
	ErrNotConnected = errors.New("未连接到云端")
	ErrSendBuffer   = errors.New("发送缓冲区已满")
)

// Handlers 由上层 agent 注册的回调。
type Handlers struct {
	OnAuthOK     func(protocol.AuthOKData)
	OnTask       func(protocol.PrintTaskData) error
	OnBinary     func([]byte) error
	OnCancel     func(string)
	OnStatus     func() protocol.AgentStatusData
	OnDisconnect func()
}

// Client 是云端长连接客户端。
type Client struct {
	cfg          config.Server
	deviceID     string
	token        string
	version      string
	maxTaskBytes int64
	log          *logx.Logger
	h            Handlers

	out       chan []byte
	connected atomic.Bool
	heartbeat atomic.Int64
}

// authError 表示鉴权失败，属于不可快速重试的错误。
type authError struct{ msg string }

func (e *authError) Error() string { return e.msg }

func isAuthError(err error) bool {
	var ae *authError
	return errors.As(err, &ae)
}

// New 创建连接客户端。
func New(cfg *config.Config, version string, log *logx.Logger, h Handlers) *Client {
	c := &Client{
		cfg:          cfg.Server,
		deviceID:     cfg.DeviceID,
		token:        cfg.Token,
		version:      version,
		maxTaskBytes: cfg.Limits.MaxTaskBytes,
		log:          log,
		h:            h,
		out:          make(chan []byte, 64),
	}
	c.heartbeat.Store(30)
	return c
}

// IsConnected 返回当前是否处于已认证连接。
func (c *Client) IsConnected() bool { return c.connected.Load() }

// Send 把消息放入发送队列，未连接时立即返回错误（由上层缓存补发）。
func (c *Client) Send(env *protocol.Envelope) error {
	if !c.IsConnected() {
		return ErrNotConnected
	}
	raw, err := env.Marshal()
	if err != nil {
		return err
	}
	select {
	case c.out <- raw:
		return nil
	default:
		return ErrSendBuffer
	}
}

// SendStatus 主动上报一次运行状态。
func (c *Client) SendStatus() {
	if c.h.OnStatus == nil {
		return
	}
	data := c.h.OnStatus()
	env, err := protocol.NewEnvelope(protocol.TypeAgentStatus, "", data)
	if err != nil {
		c.log.Errorf("构造状态消息失败: %v", err)
		return
	}
	if err := c.Send(env); err != nil {
		c.log.Debugf("状态上报跳过: %v", err)
	}
}

// Run 持续维持连接，直到 ctx 结束。
func (c *Client) Run(ctx context.Context, minBackoff, maxBackoff time.Duration) {
	backoff := minBackoff
	for {
		if ctx.Err() != nil {
			return
		}
		c.waitClockSync(ctx)

		wasConnected := c.IsConnected()
		start := time.Now()
		err := c.session(ctx)
		if wasConnected && c.h.OnDisconnect != nil {
			c.h.OnDisconnect()
		}
		if ctx.Err() != nil {
			return
		}
		if err != nil {
			c.log.Warnf("云端连接中断: %v", err)
		}

		if isAuthError(err) && backoff < 30*time.Second {
			backoff = 30 * time.Second
		}
		if err == nil || time.Since(start) >= 60*time.Second {
			backoff = minBackoff
		}

		delay := jitter(backoff)
		c.log.Infof("%s 后重连云端", delay.Round(time.Millisecond))
		select {
		case <-ctx.Done():
			return
		case <-time.After(delay):
		}

		backoff *= 2
		if backoff > maxBackoff {
			backoff = maxBackoff
		}
	}
}

// waitClockSync 等待系统时间可用：无 RTC 的路由器开机时间可能是 1970，
// 此时 TLS 校验必然失败，先等 NTP 同步再拨号。
func (c *Client) waitClockSync(ctx context.Context) {
	const sanityYear = 2024
	for i := 0; i < 24; i++ {
		if time.Now().Year() >= sanityYear {
			return
		}
		c.log.Warnf("系统时间异常(%s)，等待 NTP 同步后再建立 TLS 连接",
			time.Now().Format(time.RFC3339))
		select {
		case <-ctx.Done():
			return
		case <-time.After(5 * time.Second):
		}
	}
}

func (c *Client) session(ctx context.Context) error {
	tlsCfg, err := c.tlsConfig()
	if err != nil {
		return err
	}

	dialer := websocket.Dialer{
		HandshakeTimeout: 10 * time.Second,
		TLSClientConfig:  tlsCfg,
		Subprotocols:     []string{Subprotocol},
		NetDialContext:   (&net.Dialer{Timeout: 10 * time.Second}).DialContext,
	}

	header := http.Header{}
	header.Set("Authorization", "Bearer "+c.token)
	header.Set("X-Device-Id", c.deviceID)
	header.Set("X-Agent-Version", c.version)

	conn, resp, err := dialer.DialContext(ctx, c.cfg.URL, header)
	if err != nil {
		if resp != nil && (resp.StatusCode == http.StatusUnauthorized || resp.StatusCode == http.StatusForbidden) {
			return &authError{msg: fmt.Sprintf("设备鉴权失败(HTTP %d)，请检查 deviceId/token", resp.StatusCode)}
		}
		return fmt.Errorf("握手失败: %w", err)
	}
	defer conn.Close()

	sctx, cancel := context.WithCancel(ctx)
	defer cancel()
	defer c.drainOut()
	defer c.connected.Store(false)

	// 外部取消时主动关闭连接，避免阻塞在 ReadMessage 上无法退出。
	go func() {
		select {
		case <-ctx.Done():
			_ = conn.Close()
		case <-sctx.Done():
		}
	}()

	readLimit := c.maxTaskBytes + 64*1024
	if readLimit < 1024*1024 {
		readLimit = 1024 * 1024
	}
	conn.SetReadLimit(readLimit)

	writeErr := make(chan error, 1)
	go c.writeLoop(sctx, conn, writeErr)

	// 等待服务端 auth.ok，超时视为连接不可用。
	_ = conn.SetReadDeadline(time.Now().Add(10 * time.Second))
	authData, err := c.readAuthOK(conn)
	if err != nil {
		return err
	}
	if authData.HeartbeatSec > 0 {
		c.heartbeat.Store(int64(authData.HeartbeatSec))
	}
	c.connected.Store(true)
	c.log.Infof("已连接云端 ERP: session=%s heartbeat=%ds", authData.SessionID, c.heartbeat.Load())
	if c.h.OnAuthOK != nil {
		c.h.OnAuthOK(authData)
	}

	go c.tickLoop(sctx)
	_ = c.refreshReadDeadline(conn)

	for {
		select {
		case err := <-writeErr:
			return err
		default:
		}

		mt, payload, err := conn.ReadMessage()
		if err != nil {
			return fmt.Errorf("读取失败: %w", err)
		}
		_ = c.refreshReadDeadline(conn)

		switch mt {
		case websocket.BinaryMessage:
			if c.h.OnBinary == nil {
				return errors.New("收到未预期的二进制帧")
			}
			if err := c.h.OnBinary(payload); err != nil {
				return err
			}
		case websocket.TextMessage:
			var env protocol.Envelope
			if err := json.Unmarshal(payload, &env); err != nil {
				return fmt.Errorf("收到非法 JSON 帧: %w", err)
			}
			if err := c.dispatch(env); err != nil {
				return err
			}
		}
	}
}

func (c *Client) readAuthOK(conn *websocket.Conn) (protocol.AuthOKData, error) {
	for {
		mt, payload, err := conn.ReadMessage()
		if err != nil {
			return protocol.AuthOKData{}, fmt.Errorf("等待 auth.ok 失败: %w", err)
		}
		if mt != websocket.TextMessage {
			return protocol.AuthOKData{}, errors.New("鉴权阶段收到非文本帧")
		}
		var env protocol.Envelope
		if err := json.Unmarshal(payload, &env); err != nil {
			continue
		}
		switch env.Type {
		case protocol.TypeAuthOK:
			var d protocol.AuthOKData
			if err := env.Decode(&d); err != nil {
				return d, err
			}
			return d, nil
		case protocol.TypeError:
			var d protocol.ErrorData
			_ = env.Decode(&d)
			return protocol.AuthOKData{}, &authError{msg: fmt.Sprintf("%s: %s", d.Code, d.Message)}
		}
		c.log.Debugf("鉴权阶段忽略消息 %q", env.Type)
	}
}

func (c *Client) writeLoop(ctx context.Context, conn *websocket.Conn, errCh chan<- error) {
	for {
		select {
		case <-ctx.Done():
			return
		case raw := <-c.out:
			_ = conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if err := conn.WriteMessage(websocket.TextMessage, raw); err != nil {
				select {
				case errCh <- fmt.Errorf("写入失败: %w", err):
				default:
				}
				return
			}
		}
	}
}

func (c *Client) tickLoop(ctx context.Context) {
	hb := time.Duration(c.heartbeat.Load()) * time.Second
	if hb <= 0 {
		hb = 30 * time.Second
	}
	pingTicker := time.NewTicker(hb)
	statusTicker := time.NewTicker(60 * time.Second)
	defer pingTicker.Stop()
	defer statusTicker.Stop()

	var seq int64
	for {
		select {
		case <-ctx.Done():
			return
		case <-pingTicker.C:
			seq++
			env, err := protocol.NewEnvelope(protocol.TypePing, "", protocol.PingData{Seq: seq})
			if err != nil {
				continue
			}
			if err := c.Send(env); err != nil {
				c.log.Debugf("心跳发送失败: %v", err)
			}
		case <-statusTicker.C:
			c.SendStatus()
		}
	}
}

func (c *Client) dispatch(env protocol.Envelope) error {
	switch env.Type {
	case protocol.TypePong:
		return nil
	case protocol.TypePing:
		var d protocol.PingData
		if err := env.Decode(&d); err != nil {
			return err
		}
		pong, err := protocol.NewEnvelope(protocol.TypePong, env.ID, protocol.PongData{Seq: d.Seq})
		if err == nil {
			_ = c.Send(pong)
		}
		return nil
	case protocol.TypePrintTask:
		var d protocol.PrintTaskData
		if err := env.Decode(&d); err != nil {
			return err
		}
		if c.h.OnTask == nil {
			return errors.New("未注册任务处理器")
		}
		return c.h.OnTask(d)
	case protocol.TypePrintCancel:
		var d protocol.CancelData
		if err := env.Decode(&d); err != nil {
			return err
		}
		if c.h.OnCancel != nil {
			c.h.OnCancel(d.TaskID)
		}
		return nil
	case protocol.TypeError:
		var d protocol.ErrorData
		_ = env.Decode(&d)
		if d.Code == protocol.CodeAuthFailed {
			return &authError{msg: fmt.Sprintf("%s: %s", d.Code, d.Message)}
		}
		c.log.Warnf("服务端返回错误: %s %s", d.Code, d.Message)
		return nil
	case protocol.TypeAuthOK:
		return nil
	default:
		c.log.Warnf("忽略未知消息类型 %q", env.Type)
		return nil
	}
}

func (c *Client) refreshReadDeadline(conn *websocket.Conn) error {
	hb := time.Duration(c.heartbeat.Load()) * time.Second
	if hb <= 0 {
		hb = 30 * time.Second
	}
	return conn.SetReadDeadline(time.Now().Add(3 * hb))
}

func (c *Client) drainOut() {
	for {
		select {
		case <-c.out:
		default:
			return
		}
	}
}

func (c *Client) tlsConfig() (*tls.Config, error) {
	cfg := &tls.Config{MinVersion: tls.VersionTLS12}
	if c.cfg.InsecureSkipVerify {
		cfg.InsecureSkipVerify = true
	}
	if c.cfg.ServerName != "" {
		cfg.ServerName = c.cfg.ServerName
	}
	if c.cfg.CAFile != "" {
		raw, err := os.ReadFile(c.cfg.CAFile)
		if err != nil {
			return nil, fmt.Errorf("读取 CA 证书 %s 失败: %w", c.cfg.CAFile, err)
		}
		pool := x509.NewCertPool()
		if !pool.AppendCertsFromPEM(raw) {
			return nil, fmt.Errorf("CA 证书 %s 不含有效 PEM 证书", c.cfg.CAFile)
		}
		cfg.RootCAs = pool
	}
	return cfg, nil
}

func jitter(d time.Duration) time.Duration {
	if d <= 0 {
		return time.Second
	}
	factor := 0.8 + rand.Float64()*0.4
	return time.Duration(float64(d) * factor)
}

// TrimBearer 便于测试与调试时解析 Authorization 头。
func TrimBearer(v string) string {
	return strings.TrimSpace(strings.TrimPrefix(v, "Bearer "))
}
