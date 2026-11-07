// Package printer 负责把原始字节流写入本地 p910nd，不做任何 TSPL 解析或改写。
package printer

import (
	"fmt"
	"net"
	"strconv"
	"time"

	"github.com/jsherp/erp-print-agent/internal/config"
	"github.com/jsherp/erp-print-agent/internal/protocol"
)

// PrintError 携带协议错误码的打印错误。
type PrintError struct {
	Code string
	Err  error
}

func (e *PrintError) Error() string {
	return fmt.Sprintf("%s: %v", e.Code, e.Err)
}

// Unwrap 支持 errors.Is / errors.As。
func (e *PrintError) Unwrap() error { return e.Err }

// Client 是 p910nd 的 TCP 客户端。
type Client struct {
	host           string
	port           int
	connectTimeout time.Duration
	writeTimeout   time.Duration
}

// NewClient 根据打印机配置创建客户端。
func NewClient(p config.Printer) *Client {
	return &Client{
		host:           p.Host,
		port:           p.Port,
		connectTimeout: p.ConnectTimeout(),
		writeTimeout:   p.WriteTimeout(),
	}
}

// Address 返回 p910nd 的 TCP 地址。
func (c *Client) Address() string {
	return net.JoinHostPort(c.host, strconv.Itoa(c.port))
}

// Print 建立连接后把 data 原样写入 p910nd，写完关闭写端表示本次作业结束。
func (c *Client) Print(data []byte) (int, error) {
	conn, err := net.DialTimeout("tcp", c.Address(), c.connectTimeout)
	if err != nil {
		return 0, &PrintError{Code: protocol.CodePrinterUnreachable, Err: err}
	}
	defer conn.Close()

	if err := conn.SetWriteDeadline(time.Now().Add(c.writeTimeout)); err != nil {
		return 0, &PrintError{Code: protocol.CodeInternal, Err: err}
	}

	written := 0
	for written < len(data) {
		n, err := conn.Write(data[written:])
		written += n
		if err != nil {
			if ne, ok := err.(net.Error); ok && ne.Timeout() {
				return written, &PrintError{Code: protocol.CodePrintWriteTimeout, Err: err}
			}
			return written, &PrintError{Code: protocol.CodePrinterUnreachable, Err: err}
		}
	}

	// 只关闭写方向：p910nd 读到 EOF 后会把缓冲区数据全部送完再释放设备。
	if tcp, ok := conn.(*net.TCPConn); ok {
		_ = tcp.CloseWrite()
	}
	return written, nil
}

// Check 探测 p910nd 端口是否可连接，不发送任何打印数据。
func (c *Client) Check() error {
	conn, err := net.DialTimeout("tcp", c.Address(), c.connectTimeout)
	if err != nil {
		return err
	}
	return conn.Close()
}
