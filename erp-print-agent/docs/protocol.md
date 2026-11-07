# 通信协议 v1

代理与云端 ERP 之间的协议。约定：文本帧承载 JSON 控制消息，二进制帧承载原始 TSPL 数据。

## 1. 连接与认证

```
wss://<host>:<port>/ws/print-agent
```

握手请求头：

| Header | 说明 |
|---|---|
| `Authorization: Bearer <token>` | 设备 Token，由服务端下发 |
| `X-Device-Id: <deviceId>` | 设备唯一标识 |
| `X-Agent-Version: <version>` | 代理版本，便于服务端统计 |
| `Sec-WebSocket-Protocol: erp-print-agent.v1` | 子协议 |

服务端校验失败时返回 HTTP 401/403，代理会以不低于 30s 的间隔重试，避免错误凭据刷日志。

连接建立后，服务端必须先下发 `auth.ok`，代理收到后才发送业务消息；10 秒内未收到视为连接失败。

## 2. 消息信封

```json
{
  "v": 1,
  "type": "print.task",
  "id": "msg-8f3a",
  "ts": 1762500000000,
  "data": {}
}
```

| 字段 | 说明 |
|---|---|
| `v` | 协议版本，当前固定 1 |
| `type` | 消息类型 |
| `id` | 消息 ID，可为空 |
| `ts` | 毫秒时间戳 |
| `data` | 消息体，部分消息可省略 |

## 3. 消息清单

### auth.ok（服务端 → 代理）

```json
{"v":1,"type":"auth.ok","ts":1762500000000,"data":{
  "sessionId":"a1b2c3","serverTime":1762500000000,
  "heartbeatSec":30,"maxTaskBytes":4194304
}}
```

`heartbeatSec` 决定应用层心跳周期（默认 30s），代理在 3 倍周期内收不到任何消息即判定断线。

### ping / pong（双向心跳）

```json
{"v":1,"type":"ping","data":{"seq":12}}
{"v":1,"type":"pong","data":{"seq":12}}
```

代理每 `heartbeatSec` 发一次 `ping`，服务端回 `pong`。

### print.task（服务端 → 代理）

任务公告，**紧随其后**的是 1..N 个二进制帧，内容为该任务的原始 TSPL 数据。

```json
{"v":1,"type":"print.task","data":{
  "taskId":"20261107-0001",
  "printerId":"default",
  "totalBytes":183240,
  "sha256":"9f2c...",
  "contentType":"raw-tspl",
  "timeoutMs":15000
}}
```

- `sha256` 可为空；非空时代理会校验，不一致则任务失败，数据不会送去打印机
- `timeoutMs` 为服务端建议值，当前代理以本地配置 `writeTimeoutMs` 为准

### print.ack（代理 → 服务端）

收到公告后立即回复：

```json
{"v":1,"type":"print.ack","data":{"taskId":"20261107-0001","status":"accepted"}}
{"v":1,"type":"print.ack","data":{"taskId":"20261107-0001","status":"rejected","code":"UNKNOWN_PRINTER","message":"未配置该打印机"}}
```

拒绝原因：`UNKNOWN_PRINTER`、`TASK_TOO_LARGE`。

### print.result（代理 → 服务端）

打印结束后上报：

```json
{"v":1,"type":"print.result","data":{
  "taskId":"20261107-0001","printerId":"default","status":"success",
  "bytesWritten":183240,"durationMs":842,
  "startedAt":1762500001000,"finishedAt":1762500001842
}}
```

失败时附带 `errorCode` / `errorMessage`；被取消的任务 `status` 为 `cancelled`。

### print.cancel（服务端 → 代理）

```json
{"v":1,"type":"print.cancel","data":{"taskId":"20261107-0001"}}
```

只对**尚未开始打印**的任务生效（排队中或数据未收完），结果回 `cancelled`；已经开始写入打印机的任务不可中断。

### agent.status（代理 → 服务端）

认证成功后、每 60s、以及每次结果上报之后发送：

```json
{"v":1,"type":"agent.status","data":{
  "deviceId":"router-01","version":"0.1.0","uptimeSec":3600,
  "printers":[{"id":"default","name":"标签打印机","online":true,"printing":false,"lastTaskAt":1762500001842}],
  "queueDepth":0,"queueCapacity":8,
  "lastResult":{"taskId":"20261107-0001","printerId":"default","status":"success"}
}}
```

### error（双向）

```json
{"v":1,"type":"error","data":{"code":"AUTH_FAILED","message":"token 无效"}}
```

## 4. 二进制帧规则

1. 二进制帧**没有额外头部**，内容就是 TSPL 原始字节，归属最近一条 `print.task` 公告
2. 同一条连接同一时刻只允许一个任务在传输，服务端必须串行下发
3. 单帧建议不超过 256KB；总长度必须等于 `totalBytes`
4. 出现以下情况代理判定为协议错误并断开重连（`PROTOCOL_ERROR`）：
   - 未收到 `print.task` 就收到二进制帧
   - 累计长度超过 `totalBytes`
   - 上一任务未收完就收到新任务公告
5. 代理整包接收完成后才写入打印机，因此不会出现"半张标签"；连接中断时该任务直接失败（`TASK_INTERRUPTED`）

## 5. 错误码

| 错误码 | 含义 |
|---|---|
| `AUTH_FAILED` | 设备认证失败 |
| `UNKNOWN_PRINTER` | 未配置该 printerId |
| `PRINTER_DISABLED` | 打印机被禁用 |
| `QUEUE_FULL` | 该打印机队列已满 |
| `PRINTER_UNREACHABLE` | 连接 p910nd 失败 |
| `PRINT_WRITE_TIMEOUT` | 写入 p910nd 超时 |
| `TASK_TOO_LARGE` | 超过 `maxTaskBytes` |
| `TASK_CHECKSUM_MISMATCH` | SHA256 与公告不一致 |
| `TASK_INTERRUPTED` | 数据传输未完成连接中断 |
| `PROTOCOL_ERROR` | 消息顺序/格式非法 |
| `CANCELLED` | 任务被取消 |
| `INTERNAL_ERROR` | 代理内部错误 |

## 6. 断线、重连与结果补报

- 重连退避：`minMs` 起，指数增长到 `maxMs`（默认 1s → 30s），带 ±20% 抖动；连接稳定 60s 后重置
- 断线时正在打印的任务**继续打完**，结果暂存内存（最多 32 条），重连并认证成功后按顺序补报
- 代理不持久化任务、不落盘；任务重发由服务端负责
- 服务端需按 `taskId` 去重，因为补报可能重复送达

## 7. 典型时序

```
服务端                          代理                        p910nd
  |---- auth.ok ----------------->|                            |
  |<--- agent.status -------------|                            |
  |---- print.task --------------->|                            |
  |<--- print.ack(accepted) ------|                            |
  |---- binary chunk 1..N ------->|                            |
  |                               |---- TCP: 原始 TSPL ------>|
  |                               |<--- 写完关闭连接 ----------|
  |<--- print.result(success) ----|                            |
```
