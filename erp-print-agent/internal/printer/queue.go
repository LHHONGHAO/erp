package printer

import (
	"context"
	"errors"
	"sync"
	"time"

	"github.com/jsherp/erp-print-agent/internal/logx"
	"github.com/jsherp/erp-print-agent/internal/protocol"
)

// ErrQueueFull 队列已满，任务应被拒绝并交由服务端重试。
var ErrQueueFull = errors.New("打印机队列已满")

// Job 一次待打印任务。
type Job struct {
	TaskID     string
	PrinterID  string
	Data       []byte
	EnqueuedAt time.Time
}

// Result 一次打印的最终结果。
type Result struct {
	TaskID       string
	PrinterID    string
	Status       string
	BytesWritten int
	StartedAt    time.Time
	FinishedAt   time.Time
	ErrorCode    string
	ErrorMessage string
	DurationMs   int64
}

// Queue 保证同一台打印机的任务串行执行。
type Queue struct {
	id       string
	name     string
	client   *Client
	jobs     chan Job
	results  chan<- Result
	log      *logx.Logger
	capacity int

	mu         sync.Mutex
	cancelled  map[string]struct{}
	queued     map[string]struct{}
	current    string
	printing   bool
	healthy    bool
	lastError  string
	lastTaskAt time.Time
}

// NewQueue 创建打印机队列。
func NewQueue(id, name string, client *Client, capacity int, results chan<- Result, log *logx.Logger) *Queue {
	return &Queue{
		id:        id,
		name:      name,
		client:    client,
		jobs:      make(chan Job, capacity),
		results:   results,
		log:       log,
		capacity:  capacity,
		cancelled: make(map[string]struct{}),
		queued:    make(map[string]struct{}),
		healthy:   true,
	}
}

// Start 启动串行打印 worker。
func (q *Queue) Start(ctx context.Context) {
	go q.loop(ctx)
}

func (q *Queue) loop(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return
		case job := <-q.jobs:
			q.handle(ctx, job)
		}
	}
}

func (q *Queue) handle(ctx context.Context, job Job) {
	q.mu.Lock()
	delete(q.queued, job.TaskID)
	q.mu.Unlock()

	if q.popCancelled(job.TaskID) {
		now := time.Now()
		q.emit(ctx, Result{
			TaskID:       job.TaskID,
			PrinterID:    job.PrinterID,
			Status:       protocol.StatusCancelled,
			StartedAt:    now,
			FinishedAt:   now,
			ErrorCode:    protocol.CodeCancelled,
			ErrorMessage: "任务在开始打印前被取消",
		})
		return
	}

	q.mu.Lock()
	q.printing = true
	q.current = job.TaskID
	q.mu.Unlock()

	started := time.Now()
	written, err := q.client.Print(job.Data)
	finished := time.Now()

	res := Result{
		TaskID:       job.TaskID,
		PrinterID:    job.PrinterID,
		BytesWritten: written,
		StartedAt:    started,
		FinishedAt:   finished,
		DurationMs:   finished.Sub(started).Milliseconds(),
	}
	if err != nil {
		res.Status = protocol.StatusFailed
		if pe, ok := err.(*PrintError); ok {
			res.ErrorCode = pe.Code
		} else {
			res.ErrorCode = protocol.CodeInternal
		}
		res.ErrorMessage = err.Error()
	} else {
		res.Status = protocol.StatusSuccess
	}

	q.mu.Lock()
	q.printing = false
	q.current = ""
	q.lastTaskAt = finished
	q.lastError = res.ErrorMessage
	q.healthy = err == nil
	q.mu.Unlock()

	if err != nil {
		q.log.Errorf("打印机 %s 任务 %s 失败: %v", q.id, job.TaskID, err)
	} else {
		q.log.Infof("打印机 %s 任务 %s 完成，%d 字节，耗时 %dms", q.id, job.TaskID, written, res.DurationMs)
	}
	q.emit(ctx, res)
}

func (q *Queue) emit(ctx context.Context, res Result) {
	select {
	case q.results <- res:
	case <-ctx.Done():
	}
}

// Enqueue 非阻塞入队，队列满时返回 ErrQueueFull。
func (q *Queue) Enqueue(j Job) error {
	q.mu.Lock()
	q.queued[j.TaskID] = struct{}{}
	q.mu.Unlock()

	select {
	case q.jobs <- j:
		return nil
	default:
		q.mu.Lock()
		delete(q.queued, j.TaskID)
		q.mu.Unlock()
		return ErrQueueFull
	}
}

// Cancel 取消尚未开始打印的任务，返回是否命中。
func (q *Queue) Cancel(taskID string) bool {
	q.mu.Lock()
	defer q.mu.Unlock()
	if q.current == taskID {
		return false
	}
	if _, ok := q.queued[taskID]; !ok {
		return false
	}
	q.cancelled[taskID] = struct{}{}
	return true
}

func (q *Queue) popCancelled(taskID string) bool {
	q.mu.Lock()
	defer q.mu.Unlock()
	if _, ok := q.cancelled[taskID]; ok {
		delete(q.cancelled, taskID)
		return true
	}
	return false
}

// Depth 返回当前排队任务数。
func (q *Queue) Depth() int { return len(q.jobs) }

// Capacity 返回队列容量。
func (q *Queue) Capacity() int { return q.capacity }

// HealthCheck 在启动时探测一次 p910nd 端口可达性。
func (q *Queue) HealthCheck() {
	err := q.client.Check()
	q.mu.Lock()
	q.healthy = err == nil
	if err != nil {
		q.lastError = err.Error()
	}
	q.mu.Unlock()
	if err != nil {
		q.log.Warnf("打印机 %s(%s) 当前不可达: %v", q.id, q.client.Address(), err)
	} else {
		q.log.Infof("打印机 %s(%s) 端口可达", q.id, q.client.Address())
	}
}

// Status 返回当前状态快照。
func (q *Queue) Status() (healthy, printing bool, lastError string, lastTaskAt time.Time) {
	q.mu.Lock()
	defer q.mu.Unlock()
	return q.healthy, q.printing, q.lastError, q.lastTaskAt
}
