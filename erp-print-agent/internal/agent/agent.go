// Package agent 把云端长连接、打印队列和任务状态机编排在一起。
package agent

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/jsherp/erp-print-agent/internal/config"
	"github.com/jsherp/erp-print-agent/internal/logx"
	"github.com/jsherp/erp-print-agent/internal/printer"
	"github.com/jsherp/erp-print-agent/internal/protocol"
	"github.com/jsherp/erp-print-agent/internal/wsclient"
)

// maxSpool 断线期间最多缓存多少条待补报结果。
const maxSpool = 32

// pendingTask 表示一条已经公告、正在接收二进制数据的任务。
type pendingTask struct {
	task protocol.PrintTaskData
	buf  []byte
}

// Agent 是代理主对象。
type Agent struct {
	cfg     *config.Config
	log     *logx.Logger
	version string
	started time.Time

	ws      *wsclient.Client
	queues  map[string]*printer.Queue
	order   []string
	names   map[string]string
	results chan printer.Result

	mu         sync.Mutex
	pending    *pendingTask
	lastResult *protocol.PrintResultData

	spoolMu sync.Mutex
	spool   []protocol.PrintResultData
}

// New 构建代理实例。
func New(cfg *config.Config, log *logx.Logger, version string) *Agent {
	a := &Agent{
		cfg:     cfg,
		log:     log,
		version: version,
		started: time.Now(),
		queues:  make(map[string]*printer.Queue),
		names:   make(map[string]string),
		results: make(chan printer.Result, 64),
	}
	for _, p := range cfg.Printers {
		if !p.IsEnabled() {
			continue
		}
		q := printer.NewQueue(p.ID, p.Name, printer.NewClient(p), cfg.Limits.QueueSize, a.results, log)
		a.queues[p.ID] = q
		a.order = append(a.order, p.ID)
		a.names[p.ID] = p.Name
	}

	a.ws = wsclient.New(cfg, version, log, wsclient.Handlers{
		OnAuthOK:     a.onAuthOK,
		OnTask:       a.onTask,
		OnBinary:     a.onBinary,
		OnCancel:     a.onCancel,
		OnStatus:     a.statusSnapshot,
		OnDisconnect: a.onDisconnect,
	})
	return a
}

// Run 启动队列与连接，阻塞直到 ctx 结束。
func (a *Agent) Run(ctx context.Context) {
	for _, id := range a.order {
		a.queues[id].Start(ctx)
	}
	go a.resultLoop(ctx)
	go func() {
		for _, id := range a.order {
			a.queues[id].HealthCheck()
		}
	}()
	a.ws.Run(ctx, a.cfg.MinBackoff(), a.cfg.MaxBackoff())
}

func (a *Agent) onAuthOK(d protocol.AuthOKData) {
	a.flushSpool()
	a.ws.SendStatus()
}

func (a *Agent) onTask(t protocol.PrintTaskData) error {
	if strings.TrimSpace(t.TaskID) == "" {
		return fmt.Errorf("%s: print.task 缺少 taskId", protocol.CodeProtocolError)
	}

	a.mu.Lock()
	if a.pending != nil {
		prev := a.pending.task.TaskID
		a.mu.Unlock()
		return fmt.Errorf("%s: 任务 %s 数据未接收完，又收到任务 %s",
			protocol.CodeProtocolError, prev, t.TaskID)
	}
	if _, ok := a.queues[t.PrinterID]; !ok {
		a.mu.Unlock()
		a.log.Warnf("拒绝任务 %s: 未配置打印机 %q", t.TaskID, t.PrinterID)
		a.ack(t.TaskID, protocol.StatusRejected, protocol.CodeUnknownPrinter, "未配置该打印机")
		return nil
	}
	if t.TotalBytes <= 0 || t.TotalBytes > a.cfg.Limits.MaxTaskBytes {
		a.mu.Unlock()
		a.log.Warnf("拒绝任务 %s: 数据长度 %d 超出限制", t.TaskID, t.TotalBytes)
		a.ack(t.TaskID, protocol.StatusRejected, protocol.CodeTaskTooLarge,
			fmt.Sprintf("数据长度 %d 超出上限 %d", t.TotalBytes, a.cfg.Limits.MaxTaskBytes))
		return nil
	}

	a.pending = &pendingTask{task: t, buf: make([]byte, 0, int(t.TotalBytes))}
	a.mu.Unlock()

	a.log.Infof("收到任务 %s: 打印机=%s 大小=%d 字节", t.TaskID, t.PrinterID, t.TotalBytes)
	a.ack(t.TaskID, protocol.StatusAccepted, "", "")
	return nil
}

func (a *Agent) onBinary(chunk []byte) error {
	a.mu.Lock()
	p := a.pending
	if p == nil {
		a.mu.Unlock()
		return fmt.Errorf("%s: 未收到 print.task 就收到二进制数据", protocol.CodeProtocolError)
	}
	if int64(len(p.buf))+int64(len(chunk)) > p.task.TotalBytes {
		a.pending = nil
		a.mu.Unlock()
		return fmt.Errorf("%s: 任务 %s 数据超出声明长度 %d",
			protocol.CodeProtocolError, p.task.TaskID, p.task.TotalBytes)
	}
	p.buf = append(p.buf, chunk...)
	if int64(len(p.buf)) < p.task.TotalBytes {
		a.mu.Unlock()
		return nil
	}
	a.pending = nil
	a.mu.Unlock()

	a.dispatch(*p)
	return nil
}

func (a *Agent) dispatch(p pendingTask) {
	t := p.task
	if t.SHA256 != "" {
		sum := sha256.Sum256(p.buf)
		if !strings.EqualFold(hex.EncodeToString(sum[:]), t.SHA256) {
			a.log.Errorf("任务 %s 校验失败", t.TaskID)
			a.fail(t, protocol.CodeChecksumMismatch, "数据 SHA256 与公告不一致")
			return
		}
	}

	q, ok := a.queues[t.PrinterID]
	if !ok {
		a.fail(t, protocol.CodeUnknownPrinter, "未配置该打印机")
		return
	}
	err := q.Enqueue(printer.Job{
		TaskID:     t.TaskID,
		PrinterID:  t.PrinterID,
		Data:       p.buf,
		EnqueuedAt: time.Now(),
	})
	if err != nil {
		a.log.Warnf("任务 %s 入队失败: %v", t.TaskID, err)
		a.fail(t, protocol.CodeQueueFull, err.Error())
	}
}

func (a *Agent) onCancel(taskID string) {
	a.mu.Lock()
	if a.pending != nil && a.pending.task.TaskID == taskID {
		p := a.pending
		a.pending = nil
		a.mu.Unlock()
		a.log.Infof("任务 %s 在数据传输阶段被取消", taskID)
		a.cancel(p.task)
		return
	}
	a.mu.Unlock()

	for _, id := range a.order {
		if a.queues[id].Cancel(taskID) {
			a.log.Infof("任务 %s 已标记取消（队列 %s）", taskID, id)
			return
		}
	}
	a.log.Warnf("取消请求未命中任务 %s（可能已开始打印或已结束）", taskID)
}

func (a *Agent) onDisconnect() {
	a.mu.Lock()
	p := a.pending
	a.pending = nil
	a.mu.Unlock()
	if p == nil {
		return
	}
	a.log.Warnf("连接中断，任务 %s 数据接收未完成", p.task.TaskID)
	a.fail(p.task, protocol.CodeTaskInterrupted, "连接中断，任务数据未接收完整")
}

func (a *Agent) resultLoop(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return
		case res := <-a.results:
			a.recordResult(protocol.PrintResultData{
				TaskID:       res.TaskID,
				PrinterID:    res.PrinterID,
				Status:       res.Status,
				BytesWritten: int64(res.BytesWritten),
				DurationMs:   res.DurationMs,
				StartedAt:    res.StartedAt.UnixMilli(),
				FinishedAt:   res.FinishedAt.UnixMilli(),
				ErrorCode:    res.ErrorCode,
				ErrorMessage: res.ErrorMessage,
			})
		}
	}
}

func (a *Agent) fail(t protocol.PrintTaskData, code, msg string) {
	now := time.Now().UnixMilli()
	a.recordResult(protocol.PrintResultData{
		TaskID:       t.TaskID,
		PrinterID:    t.PrinterID,
		Status:       protocol.StatusFailed,
		StartedAt:    now,
		FinishedAt:   now,
		ErrorCode:    code,
		ErrorMessage: msg,
	})
}

func (a *Agent) cancel(t protocol.PrintTaskData) {
	now := time.Now().UnixMilli()
	a.recordResult(protocol.PrintResultData{
		TaskID:       t.TaskID,
		PrinterID:    t.PrinterID,
		Status:       protocol.StatusCancelled,
		StartedAt:    now,
		FinishedAt:   now,
		ErrorCode:    protocol.CodeCancelled,
		ErrorMessage: "任务被服务端取消",
	})
}

// recordResult 记录结果：立即尝试上报，失败则留在内存缓存等重连补报。
func (a *Agent) recordResult(pd protocol.PrintResultData) {
	a.mu.Lock()
	cp := pd
	a.lastResult = &cp
	a.mu.Unlock()

	a.spoolMu.Lock()
	a.spool = append(a.spool, pd)
	if len(a.spool) > maxSpool {
		a.spool = a.spool[len(a.spool)-maxSpool:]
	}
	a.spoolMu.Unlock()

	if err := a.sendResult(pd); err == nil {
		a.dropSpool(pd.TaskID)
	}
}

func (a *Agent) sendResult(pd protocol.PrintResultData) error {
	env, err := protocol.NewEnvelope(protocol.TypePrintResult, "", pd)
	if err != nil {
		return err
	}
	return a.ws.Send(env)
}

func (a *Agent) flushSpool() {
	a.spoolMu.Lock()
	pending := make([]protocol.PrintResultData, len(a.spool))
	copy(pending, a.spool)
	a.spoolMu.Unlock()

	if len(pending) == 0 {
		return
	}
	a.log.Infof("补报 %d 条离线期间的打印结果", len(pending))
	for _, pd := range pending {
		if err := a.sendResult(pd); err != nil {
			a.log.Warnf("补报任务 %s 结果失败: %v", pd.TaskID, err)
			return
		}
		a.dropSpool(pd.TaskID)
	}
}

func (a *Agent) dropSpool(taskID string) {
	a.spoolMu.Lock()
	defer a.spoolMu.Unlock()
	for i, pd := range a.spool {
		if pd.TaskID == taskID {
			a.spool = append(a.spool[:i], a.spool[i+1:]...)
			return
		}
	}
}

func (a *Agent) ack(taskID, status, code, msg string) {
	env, err := protocol.NewEnvelope(protocol.TypePrintAck, "", protocol.PrintAckData{
		TaskID:  taskID,
		Status:  status,
		Code:    code,
		Message: msg,
	})
	if err != nil {
		return
	}
	if err := a.ws.Send(env); err != nil {
		a.log.Warnf("发送任务 %s 的 ack 失败: %v", taskID, err)
	}
}

func (a *Agent) statusSnapshot() protocol.AgentStatusData {
	printers := make([]protocol.PrinterStatus, 0, len(a.order))
	depth, capacity := 0, 0
	for _, id := range a.order {
		q := a.queues[id]
		healthy, printing, lastErr, lastAt := q.Status()
		depth += q.Depth()
		capacity += q.Capacity()

		ps := protocol.PrinterStatus{
			ID:        id,
			Name:      a.names[id],
			Online:    healthy,
			Printing:  printing,
			LastError: lastErr,
		}
		if !lastAt.IsZero() {
			ps.LastTaskAt = lastAt.UnixMilli()
		}
		printers = append(printers, ps)
	}

	a.mu.Lock()
	last := a.lastResult
	a.mu.Unlock()

	return protocol.AgentStatusData{
		DeviceID:      a.cfg.DeviceID,
		Version:       a.version,
		UptimeSec:     int64(time.Since(a.started).Seconds()),
		Printers:      printers,
		QueueDepth:    depth,
		QueueCapacity: capacity,
		LastResult:    last,
	}
}
