# 部署指南（OpenWrt）

## 1. 先探测路由器环境

在路由器上执行（或把 `scripts/probe-router.sh` 上传后 `sh probe-router.sh`）：

```sh
cat /etc/openwrt_release
uname -a
opkg print-architecture
free -m
df -h / /overlay /tmp
cat /etc/config/p910nd
netstat -lntp | grep 9100
date
```

需要确认：

| 项目 | 用途 |
|---|---|
| `uname -m` = `aarch64` | 决定 `GOARCH=arm64` |
| `opkg print-architecture` | 决定 IPK 的 `Architecture` 字段（MT7981/7986 通常为 `aarch64_cortex-a53`） |
| `/overlay` 剩余空间 | 二进制约 6MB，需保证 overlay 至少 10MB 可用 |
| 内存 | 运行期常驻约 10~20MB |
| `p910nd` 监听端口 | 默认 9100，需与 `printers[].port` 一致 |
| `date` | 时间必须正确，否则 TLS 握手失败 |

## 2. 构建 IPK

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File scripts\build.ps1 -Version 0.1.0 -Arch aarch64_cortex-a53

# Linux / WSL
make -C packaging/openwrt ipk
```

产物：`build/erp-print-agent_0.1.0_aarch64_cortex-a53.ipk`

自检：

```powershell
go run ./cmd/mk-ipk -list build\erp-print-agent_0.1.0_aarch64_cortex-a53.ipk
```

## 3. 准备服务端证书

生产环境使用自签名证书，路由器需要显式信任。构建机上执行：

```powershell
go run ./cmd/erp-print-agent fetch-ca -url wss://www.innas.xyz:9999/ws/print-agent -out packaging\openwrt\files\ca.pem
```

把 `ca.pem` 放到 `packaging/openwrt/files/ca.pem` 后重新构建，IPK 会自动带上
`/etc/erp-print-agent/ca.pem`。若部署地址与证书 SAN 不一致（例如用 IP 访问），
在配置里补 `"serverName": "www.innas.xyz"`。

没有 ca.pem 时代理会报"读取 CA 证书失败"，不会静默降级为不校验。

## 4. 安装

```sh
# 上传
scp erp-print-agent_0.1.0_aarch64_cortex-a53.ipk root@192.168.1.1:/tmp/

# 安装
opkg install /tmp/erp-print-agent_0.1.0_aarch64_cortex-a53.ipk

# 编辑配置（填写 token，确认 deviceId / 打印机端口）
vi /etc/erp-print-agent/config.json
chmod 600 /etc/erp-print-agent/config.json

# 校验配置与 p910nd 连通性
erp-print-agent check -config /etc/erp-print-agent/config.json

# 本地自检打印（先验证 p910nd 能出纸）
erp-print-agent selftest -printer default -config /etc/erp-print-agent/config.json

# 启用并启动服务
/etc/init.d/erp-print-agent enable
/etc/init.d/erp-print-agent start
```

## 5. 验证

```sh
# 进程与日志
ps | grep erp-print-agent
logread -e erp-print-agent | tail -30

# 服务状态
/etc/init.d/erp-print-agent status
```

预期日志：`打印机 default(127.0.0.1:9100) 端口可达` → `已连接云端 ERP: session=...`。

断线自愈验证：

1. 断开路由器外网 30 秒后恢复，日志应出现退避重连并最终 `已连接云端 ERP`
2. 期间完成打印的任务，重连后会看到"补报 N 条离线期间的打印结果"

开机自启验证：`reboot` 后不手工干预，`ps | grep erp-print-agent` 应存在，且已连接云端。

## 6. 升级与卸载

```sh
# 升级（config.json 在 conffiles 中，会保留）
opkg install /tmp/erp-print-agent_<new>.ipk

# 卸载（保留配置）
opkg remove erp-print-agent
```

## 7. 排障

| 现象 | 排查 |
|---|---|
| `配置缺少 token` | `/etc/erp-print-agent/config.json` 未填写 token |
| `设备鉴权失败(HTTP 401)` | token 与服务端不一致；重试间隔会自动拉长到 30s |
| `读取 CA 证书失败` | 未放 `ca.pem`，或配置里的 `caFile` 路径不对 |
| `x509: certificate is valid for ...` | 域名与证书 SAN 不一致，配置 `serverName` |
| `等待 NTP 同步后再建立 TLS 连接` | 路由器无 RTC 且时间未同步，检查 NTP 是否可用 |
| `打印机 ... 不可达` | p910nd 未运行、端口不对，或 USB 打印机未识别（`ls /dev/usb/lp*`） |
| 打印内容不完整 | 检查 `writeTimeoutMs` 是否过小，或 USB 线/供电问题 |
| 服务反复重启 | `logread` 查看退出原因；`procd_set_param respawn` 会在 3600s 内最多重启 5 次 |

## 8. 资源与安全约束

- 二进制约 6MB（`-s -w`、`CGO_ENABLED=0`），运行内存约 10~20MB
- 代理只发起出站 WSS 连接，不监听任何端口，不需要在防火墙放行入站
- 不修改 `/etc/config/p910nd`，p910nd 保持原有配置
- 不解析、不修改 TSPL 数据，完全透传
