#!/bin/sh
# 在 OpenWrt 路由器上执行，收集编译目标与运行环境信息。
# 用法: sh probe-router.sh

echo "===== OpenWrt 版本 ====="
cat /etc/openwrt_release 2>/dev/null || echo "未找到 /etc/openwrt_release"

echo
echo "===== 内核与架构 ====="
uname -a
grep -m1 -E "model name|Hardware|system type" /proc/cpuinfo 2>/dev/null

echo
echo "===== opkg 架构 ====="
opkg print-architecture 2>/dev/null || echo "opkg 不可用"

echo
echo "===== 内存 ====="
free -m 2>/dev/null || cat /proc/meminfo | head -5

echo
echo "===== 存储 ====="
df -h / /overlay /tmp 2>/dev/null

echo
echo "===== p910nd 配置 ====="
cat /etc/config/p910nd 2>/dev/null || echo "未找到 /etc/config/p910nd"

echo
echo "===== 打印端口监听 ====="
netstat -lntp 2>/dev/null | grep -E ":910[0-9]" || echo "未发现 9100~9109 监听"

echo
echo "===== 相关软件包 ====="
opkg list-installed 2>/dev/null | grep -E "p910nd|kmod-usb-printer|libustream|ca-bundle" || echo "无匹配包"

echo
echo "===== USB 打印机设备 ====="
ls -l /dev/usb/lp* 2>/dev/null || echo "未发现 /dev/usb/lp*"

echo
echo "===== 系统时间 ====="
date
