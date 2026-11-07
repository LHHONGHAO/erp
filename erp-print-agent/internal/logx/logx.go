// Package logx 提供轻量分级日志，输出到 stderr（交给 procd/logd），
// 可选写入本地文件并做简单的按大小轮转，避免路由器 flash 被写满。
package logx

import (
	"fmt"
	"io"
	"os"
	"strings"
	"sync"
	"time"
)

// Level 日志级别。
type Level int

// 日志级别定义。
const (
	Debug Level = iota
	Info
	Warn
	Error
)

// ParseLevel 解析级别名称，未知值回退到 info。
func ParseLevel(s string) Level {
	switch strings.ToLower(strings.TrimSpace(s)) {
	case "debug":
		return Debug
	case "warn", "warning":
		return Warn
	case "error":
		return Error
	default:
		return Info
	}
}

func (l Level) String() string {
	switch l {
	case Debug:
		return "DEBUG"
	case Warn:
		return "WARN"
	case Error:
		return "ERROR"
	default:
		return "INFO"
	}
}

// Logger 并发安全的分级日志。
type Logger struct {
	level    Level
	mu       sync.Mutex
	file     *os.File
	path     string
	maxBytes int64
	size     int64
}

// New 创建日志器。path 为空时只输出到 stderr；文件打开失败会降级为 stderr 输出。
func New(level string, path string, maxSizeKB int) (*Logger, error) {
	l := &Logger{level: ParseLevel(level), path: path}
	if maxSizeKB <= 0 {
		maxSizeKB = 512
	}
	l.maxBytes = int64(maxSizeKB) * 1024
	if path == "" {
		return l, nil
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
	if err != nil {
		return l, fmt.Errorf("打开日志文件 %s 失败，仅输出到 stderr: %w", path, err)
	}
	l.file = f
	if st, err := f.Stat(); err == nil {
		l.size = st.Size()
	}
	return l, nil
}

// Close 关闭日志文件。
func (l *Logger) Close() {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.file != nil {
		_ = l.file.Close()
		l.file = nil
	}
}

// Debugf 调试日志。
func (l *Logger) Debugf(format string, args ...any) { l.logf(Debug, format, args...) }

// Infof 普通日志。
func (l *Logger) Infof(format string, args ...any) { l.logf(Info, format, args...) }

// Warnf 警告日志。
func (l *Logger) Warnf(format string, args ...any) { l.logf(Warn, format, args...) }

// Errorf 错误日志。
func (l *Logger) Errorf(format string, args ...any) { l.logf(Error, format, args...) }

func (l *Logger) logf(lv Level, format string, args ...any) {
	if lv < l.level {
		return
	}
	line := fmt.Sprintf("%s %-5s %s\n",
		time.Now().Format("2006-01-02 15:04:05"), lv.String(), fmt.Sprintf(format, args...))

	l.mu.Lock()
	defer l.mu.Unlock()
	_, _ = io.WriteString(os.Stderr, line)
	if l.file == nil {
		return
	}
	if l.maxBytes > 0 && l.size+int64(len(line)) > l.maxBytes {
		l.rotateLocked()
	}
	if l.file != nil {
		n, _ := l.file.WriteString(line)
		l.size += int64(n)
	}
}

func (l *Logger) rotateLocked() {
	if l.file == nil {
		return
	}
	_ = l.file.Close()
	l.file = nil
	_ = os.Rename(l.path, l.path+".1")
	f, err := os.OpenFile(l.path, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644)
	if err != nil {
		return
	}
	l.file = f
	l.size = 0
}
