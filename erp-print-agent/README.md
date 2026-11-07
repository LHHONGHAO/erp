# erp-print-agent

运行在 OpenWrt 路由器上的远程打印代理。路由器的标签打印机由现有 p910nd 暴露为 TCP 服务（默认 9100），
代理与云端 ERP 建立 WebSocket Secure 长连接，把服务端下发的原始 TSPL 数据**原样**转发给 p910nd。

- Go 开发，`CGO_ENABLED=0` 静态编译，目标 `linux/arm64`（MT7981 / MT7986 / RK3588 等 aarch64 设备）
- 不解析、不修改 TSPL，不实现打印服务，不引入数据库
- 只发起出站连接，不开放路由器 WAN 端口，不修改 p910nd 配置
- procd 管理，开机自启、异常自动重启

## 快速开始（本地联调）

```powershell
# 1. 构建 Windows 调试产物
powershell -ExecutionPolicy Bypass -File scripts\build.ps1 -Local

# 2. 启动 mock 云端（同时模拟 p910nd 监听 19100）
build\win\mock-server.exe -addr 127.0.0.1:18443 -token dev-token `
    -fake-printer 127.0.0.1:19100 -fake-printer-out received.bin -file label.tspl

# 3. 另开终端启动代理（配置文件里 printer.host/port 指向 127.0.0.1:19100，
#    server.url 为 ws://127.0.0.1:18443/ws/print-agent，token 为 dev-token）
build\win\erp-print-agent.exe -config dev-config.json serve
```

`received.bin` 与 `label.tspl` 做 SHA256 比对应完全一致，mock 侧会打印 `ack` 与 `result`。

## 常用命令

| 命令 | 说明 |
|---|---|
| `erp-print-agent serve -config <path>` | 常驻运行（procd 使用的方式） |
| `erp-print-agent check -config <path>` | 校验配置并探测 p910nd 端口 |
| `erp-print-agent print -printer default -file a.tspl` | 直接把文件送到打印机 |
| `erp-print-agent selftest -printer default` | 打印一张自检标签 |
| `erp-print-agent fetch-ca -url wss://host:9999/ws/print-agent -out ca.pem` | 抓取服务端证书 |
| `erp-print-agent version` | 版本号 |

## 构建与打包

```powershell
# Windows：交叉编译 linux/arm64 并生成 IPK
powershell -ExecutionPolicy Bypass -File scripts\build.ps1 -Version 0.1.0 -Arch aarch64_cortex-a53

# Linux / WSL
make -C packaging/openwrt ipk
```

产物：`build/erp-print-agent_<version>_<arch>.ipk`

## 目录结构

```
cmd/erp-print-agent     代理主程序
cmd/mock-server         本地联调工具（不进 IPK）
cmd/mk-ipk              IPK 组装工具（跨平台）
internal/config         配置加载与校验
internal/logx           分级日志（stderr + 可选轮转文件）
internal/protocol       消息信封与错误码
internal/printer        p910nd 客户端与串行队列
internal/wsclient       WSS 连接、认证、心跳、重连
internal/agent          任务状态机、结果补报、状态上报
packaging/openwrt       procd 脚本、默认配置、Makefile
scripts                 构建与路由器环境探测脚本
docs                    协议与部署说明
```

详细内容见 [协议说明](docs/protocol.md) 与 [部署指南](docs/deploy.md)。
