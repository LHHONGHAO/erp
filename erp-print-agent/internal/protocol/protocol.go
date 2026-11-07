// Package protocol 定义代理与云端 ERP 之间的 WebSocket 消息格式。
// 注意：本协议只负责任务信封与状态，不解析、不修改 TSPL 指令内容。
package protocol

import (
	"encoding/json"
	"fmt"
	"time"
)

// Version 协议版本，出现在每条消息的 v 字段。
const Version = 1

// 消息类型。
const (
	TypeAuthOK      = "auth.ok"
	TypePing        = "ping"
	TypePong        = "pong"
	TypePrintTask   = "print.task"
	TypePrintAck    = "print.ack"
	TypePrintResult = "print.result"
	TypePrintCancel = "print.cancel"
	TypeAgentStatus = "agent.status"
	TypeError       = "error"
)

// 错误码。
const (
	CodeAuthFailed         = "AUTH_FAILED"
	CodeUnknownPrinter     = "UNKNOWN_PRINTER"
	CodePrinterDisabled    = "PRINTER_DISABLED"
	CodeQueueFull          = "QUEUE_FULL"
	CodePrinterUnreachable = "PRINTER_UNREACHABLE"
	CodePrintWriteTimeout  = "PRINT_WRITE_TIMEOUT"
	CodeTaskTooLarge       = "TASK_TOO_LARGE"
	CodeChecksumMismatch   = "TASK_CHECKSUM_MISMATCH"
	CodeTaskInterrupted    = "TASK_INTERRUPTED"
	CodeProtocolError      = "PROTOCOL_ERROR"
	CodeCancelled          = "CANCELLED"
	CodeInternal           = "INTERNAL_ERROR"
)

// 任务状态。
const (
	StatusAccepted  = "accepted"
	StatusRejected  = "rejected"
	StatusSuccess   = "success"
	StatusFailed    = "failed"
	StatusCancelled = "cancelled"
)

// Envelope 是所有文本帧的统一信封。
type Envelope struct {
	V    int             `json:"v"`
	Type string          `json:"type"`
	ID   string          `json:"id,omitempty"`
	TS   int64           `json:"ts"`
	Data json.RawMessage `json:"data,omitempty"`
}

// NewEnvelope 构造一条消息，data 为 nil 时省略 data 字段。
func NewEnvelope(typ, id string, data any) (*Envelope, error) {
	env := &Envelope{V: Version, Type: typ, ID: id, TS: time.Now().UnixMilli()}
	if data != nil {
		raw, err := json.Marshal(data)
		if err != nil {
			return nil, fmt.Errorf("序列化 %s 失败: %w", typ, err)
		}
		env.Data = raw
	}
	return env, nil
}

// Marshal 序列化为文本帧内容。
func (e *Envelope) Marshal() ([]byte, error) {
	return json.Marshal(e)
}

// Decode 解析 data 字段到 v。
func (e *Envelope) Decode(v any) error {
	if len(e.Data) == 0 {
		return fmt.Errorf("消息 %s 缺少 data 字段", e.Type)
	}
	if err := json.Unmarshal(e.Data, v); err != nil {
		return fmt.Errorf("解析 %s.data 失败: %w", e.Type, err)
	}
	return nil
}

// AuthOKData 服务端鉴权通过后下发。
type AuthOKData struct {
	SessionID    string `json:"sessionId"`
	ServerTime   int64  `json:"serverTime"`
	HeartbeatSec int    `json:"heartbeatSec"`
	MaxTaskBytes int64  `json:"maxTaskBytes"`
}

// PrintTaskData 服务端下发任务公告，紧随其后的是原始 TSPL 二进制帧。
type PrintTaskData struct {
	TaskID      string `json:"taskId"`
	PrinterID   string `json:"printerId"`
	TotalBytes  int64  `json:"totalBytes"`
	SHA256      string `json:"sha256,omitempty"`
	ContentType string `json:"contentType,omitempty"`
	TimeoutMs   int    `json:"timeoutMs,omitempty"`
}

// PrintAckData 代理对任务公告的即时应答。
type PrintAckData struct {
	TaskID  string `json:"taskId"`
	Status  string `json:"status"`
	Code    string `json:"code,omitempty"`
	Message string `json:"message,omitempty"`
}

// PrintResultData 打印结束后的最终结果。
type PrintResultData struct {
	TaskID       string `json:"taskId"`
	PrinterID    string `json:"printerId"`
	Status       string `json:"status"`
	BytesWritten int64  `json:"bytesWritten"`
	DurationMs   int64  `json:"durationMs"`
	StartedAt    int64  `json:"startedAt"`
	FinishedAt   int64  `json:"finishedAt"`
	ErrorCode    string `json:"errorCode,omitempty"`
	ErrorMessage string `json:"errorMessage,omitempty"`
}

// PrinterStatus 单台打印机的运行状态。
type PrinterStatus struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	Online     bool   `json:"online"`
	Printing   bool   `json:"printing"`
	LastError  string `json:"lastError,omitempty"`
	LastTaskAt int64  `json:"lastTaskAt,omitempty"`
}

// AgentStatusData 代理周期性上报的状态。
type AgentStatusData struct {
	DeviceID      string           `json:"deviceId"`
	Version       string           `json:"version"`
	UptimeSec     int64            `json:"uptimeSec"`
	Printers      []PrinterStatus  `json:"printers"`
	QueueDepth    int              `json:"queueDepth"`
	QueueCapacity int              `json:"queueCapacity"`
	LastResult    *PrintResultData `json:"lastResult,omitempty"`
}

// PingData / PongData 应用层心跳，用于穿透代理与 NAT 检测链路存活。
type PingData struct {
	Seq int64 `json:"seq"`
}

type PongData struct {
	Seq int64 `json:"seq"`
}

// CancelData 服务端请求取消任务（只对尚未开始打印的任务生效）。
type CancelData struct {
	TaskID string `json:"taskId"`
}

// ErrorData 通用错误消息。
type ErrorData struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	TaskID  string `json:"taskId,omitempty"`
}
