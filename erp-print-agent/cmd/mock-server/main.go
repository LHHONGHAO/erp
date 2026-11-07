// mock-server 是本地联调工具：模拟云端 ERP 的 WSS 端点，可选同时模拟 p910nd。
// 它不属于 IPK 安装内容，只用于开发验证。
package main

import (
	"bufio"
	"bytes"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"

	"github.com/jsherp/erp-print-agent/internal/protocol"
)

var (
	addr            = flag.String("addr", ":8443", "监听地址")
	certFile        = flag.String("cert", "", "TLS 证书（与 -key 同时提供则启用 WSS）")
	keyFile         = flag.String("key", "", "TLS 私钥")
	token           = flag.String("token", "dev-token", "期望的设备 token")
	wsPath          = flag.String("path", "/ws/print-agent", "WebSocket 路径")
	autoFile        = flag.String("file", "", "连接建立后自动下发该文件")
	targetPrinter   = flag.String("printer", "default", "任务下发的打印机 id")
	fakePrinter     = flag.String("fake-printer", "", "同时模拟 p910nd 的监听地址，如 127.0.0.1:9100")
	fakePrinterOut  = flag.String("fake-printer-out", "received.bin", "模拟 p910nd 收到的数据落盘路径")
	fakePrinterHold = flag.Int("fake-printer-hold-ms", 0, "模拟打印机开始读取前的等待毫秒数（用于测试打印中掉线）")
)

// wsWriter 串行化同一连接上的写操作。
type wsWriter struct {
	mu   sync.Mutex
	conn *websocket.Conn
}

var (
	activeMu sync.Mutex
	active   *wsWriter
)

func main() {
	flag.Parse()
	log.SetFlags(log.LstdFlags | log.Lmicroseconds)

	if *fakePrinter != "" {
		go serveFakePrinter(*fakePrinter, *fakePrinterOut, *fakePrinterHold)
	}

	http.HandleFunc(*wsPath, handleWS)
	go stdinLoop()

	if *certFile != "" && *keyFile != "" {
		log.Printf("mock WSS 监听 %s%s", *addr, *wsPath)
		log.Fatal(http.ListenAndServeTLS(*addr, *certFile, *keyFile, nil))
	}
	log.Printf("mock WS 监听 %s%s", *addr, *wsPath)
	log.Fatal(http.ListenAndServe(*addr, nil))
}

func handleWS(w http.ResponseWriter, r *http.Request) {
	auth := strings.TrimSpace(strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer "))
	deviceID := r.Header.Get("X-Device-Id")
	if auth != *token {
		log.Printf("拒绝连接: token 不匹配 (remote=%s)", r.RemoteAddr)
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}
	if deviceID == "" {
		http.Error(w, "missing device id", http.StatusBadRequest)
		return
	}

	upgrader := websocket.Upgrader{CheckOrigin: func(*http.Request) bool { return true }}
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("升级 WebSocket 失败: %v", err)
		return
	}
	writer := &wsWriter{conn: conn}
	log.Printf("设备 %s 已连接 (remote=%s)", deviceID, r.RemoteAddr)

	if err := writer.writeEnv(protocol.TypeAuthOK, protocol.AuthOKData{
		SessionID:    newSessionID(),
		ServerTime:   time.Now().UnixMilli(),
		HeartbeatSec: 30,
		MaxTaskBytes: 8 * 1024 * 1024,
	}); err != nil {
		log.Printf("发送 auth.ok 失败: %v", err)
		_ = conn.Close()
		return
	}

	activeMu.Lock()
	active = writer
	activeMu.Unlock()

	defer func() {
		activeMu.Lock()
		if active == writer {
			active = nil
		}
		activeMu.Unlock()
		_ = conn.Close()
		log.Printf("设备 %s 断开", deviceID)
	}()

	if *autoFile != "" {
		go func() {
			time.Sleep(time.Second)
			if err := sendTask(*autoFile); err != nil {
				log.Printf("自动下发失败: %v", err)
			}
		}()
	}

	for {
		mt, payload, err := conn.ReadMessage()
		if err != nil {
			return
		}
		if mt == websocket.BinaryMessage {
			log.Printf("收到未预期的二进制帧 %d 字节", len(payload))
			continue
		}
		var env protocol.Envelope
		if err := json.Unmarshal(payload, &env); err != nil {
			log.Printf("收到非法 JSON 帧: %v", err)
			continue
		}
		handleMessage(writer, env)
	}
}

func handleMessage(w *wsWriter, env protocol.Envelope) {
	switch env.Type {
	case protocol.TypePing:
		var d protocol.PingData
		_ = env.Decode(&d)
		_ = w.writeEnv(protocol.TypePong, protocol.PongData{Seq: d.Seq})
	case protocol.TypePong:
		// 心跳回包，无需处理
	case protocol.TypePrintAck:
		var d protocol.PrintAckData
		_ = env.Decode(&d)
		log.Printf("<- ack    task=%s status=%s code=%s %s", d.TaskID, d.Status, d.Code, d.Message)
	case protocol.TypePrintResult:
		var d protocol.PrintResultData
		_ = env.Decode(&d)
		log.Printf("<- result task=%s printer=%s status=%s bytes=%d 耗时=%dms code=%s %s",
			d.TaskID, d.PrinterID, d.Status, d.BytesWritten, d.DurationMs, d.ErrorCode, d.ErrorMessage)
	case protocol.TypeAgentStatus:
		var d protocol.AgentStatusData
		_ = env.Decode(&d)
		log.Printf("<- status device=%s version=%s uptime=%ds queue=%d/%d printers=%d",
			d.DeviceID, d.Version, d.UptimeSec, d.QueueDepth, d.QueueCapacity, len(d.Printers))
		for _, p := range d.Printers {
			log.Printf("     printer %s(%s) online=%v printing=%v lastError=%q",
				p.ID, p.Name, p.Online, p.Printing, p.LastError)
		}
	default:
		log.Printf("<- %s %s", env.Type, string(env.Data))
	}
}

func stdinLoop() {
	fmt.Println("命令: send <file>  |  cancel <taskId>  |  quit")
	scanner := bufio.NewScanner(os.Stdin)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		switch {
		case line == "":
		case line == "quit" || line == "exit":
			os.Exit(0)
		case strings.HasPrefix(line, "send "):
			file := strings.TrimSpace(strings.TrimPrefix(line, "send "))
			if err := sendTask(file); err != nil {
				log.Printf("下发失败: %v", err)
			}
		case strings.HasPrefix(line, "cancel "):
			id := strings.TrimSpace(strings.TrimPrefix(line, "cancel "))
			writer := currentWriter()
			if writer == nil {
				log.Printf("没有已连接的设备")
				continue
			}
			if err := writer.writeEnv(protocol.TypePrintCancel, protocol.CancelData{TaskID: id}); err != nil {
				log.Printf("发送取消失败: %v", err)
			}
		default:
			log.Printf("未知命令: %s", line)
		}
	}
}

func sendTask(file string) error {
	raw, err := os.ReadFile(file)
	if err != nil {
		return fmt.Errorf("读取 %s 失败: %w", file, err)
	}
	if len(raw) == 0 {
		return fmt.Errorf("文件 %s 内容为空", file)
	}
	writer := currentWriter()
	if writer == nil {
		return fmt.Errorf("没有已连接的设备")
	}

	taskID := fmt.Sprintf("task-%d", time.Now().UnixMilli())
	sum := sha256.Sum256(raw)
	task := protocol.PrintTaskData{
		TaskID:      taskID,
		PrinterID:   *targetPrinter,
		TotalBytes:  int64(len(raw)),
		SHA256:      hex.EncodeToString(sum[:]),
		ContentType: "raw-tspl",
	}
	if err := writer.writeEnv(protocol.TypePrintTask, task); err != nil {
		return err
	}
	log.Printf("-> task   task=%s printer=%s size=%d", taskID, task.PrinterID, len(raw))

	const chunkSize = 200 * 1024
	for off := 0; off < len(raw); off += chunkSize {
		end := off + chunkSize
		if end > len(raw) {
			end = len(raw)
		}
		if err := writer.writeBinary(raw[off:end]); err != nil {
			return err
		}
	}
	return nil
}

func currentWriter() *wsWriter {
	activeMu.Lock()
	defer activeMu.Unlock()
	return active
}

func (w *wsWriter) writeEnv(typ string, data any) error {
	env, err := protocol.NewEnvelope(typ, "", data)
	if err != nil {
		return err
	}
	raw, err := env.Marshal()
	if err != nil {
		return err
	}
	w.mu.Lock()
	defer w.mu.Unlock()
	_ = w.conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
	return w.conn.WriteMessage(websocket.TextMessage, raw)
}

func (w *wsWriter) writeBinary(b []byte) error {
	w.mu.Lock()
	defer w.mu.Unlock()
	_ = w.conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
	return w.conn.WriteMessage(websocket.BinaryMessage, b)
}

func serveFakePrinter(listenAddr, out string, holdMs int) {
	ln, err := net.Listen("tcp", listenAddr)
	if err != nil {
		log.Fatalf("模拟 p910nd 监听 %s 失败: %v", listenAddr, err)
	}
	log.Printf("模拟 p910nd 监听 %s，收到的数据写入 %s（延时 %dms）", listenAddr, out, holdMs)
	for {
		conn, err := ln.Accept()
		if err != nil {
			return
		}
		go func(c net.Conn) {
			defer c.Close()
			if holdMs > 0 {
				// 缩小接收窗口，制造真实的"打印机读得慢"效果，让发送端发生阻塞。
				if tcp, ok := c.(*net.TCPConn); ok {
					_ = tcp.SetReadBuffer(4096)
				}
				time.Sleep(time.Duration(holdMs) * time.Millisecond)
			}
			var buf bytes.Buffer
			if _, err := io.Copy(&buf, c); err != nil {
				log.Printf("读取模拟打印数据失败: %v", err)
			}
			if err := os.WriteFile(out, buf.Bytes(), 0o644); err != nil {
				log.Printf("写入 %s 失败: %v", out, err)
				return
			}
			log.Printf("模拟 p910nd 收到 %d 字节 -> %s", buf.Len(), out)
		}(conn)
	}
}

func newSessionID() string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}
