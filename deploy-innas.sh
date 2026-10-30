#!/usr/bin/env bash
# jshERP 一键部署脚本（Linux版，远端主机 www.innas.xyz）
# 对应 deploy-innas.ps1 的 Linux 实现：打包 -> 上传 -> 重启服务 -> 验证
#
# 用法：
#   ./deploy-innas.sh                 # 仅部署后端
#   ./deploy-innas.sh --frontend      # 同时部署前端
#   ./deploy-innas.sh --skip-build    # 跳过本地打包，使用已有产物
#
set -euo pipefail

SERVER="www.innas.xyz"
PORT=22
SSH_USER="root"
SSH_PASS="password"
REMOTE_DIR="/root/jsherp"
WEB_PORT=9999
APP_PORT=9998
WAIT_SEC=50
FRONTEND=false
SKIP_BUILD=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --frontend|-f)  FRONTEND=true; shift ;;
    --skip-build)   SKIP_BUILD=true; shift ;;
    --server)       SERVER="$2"; shift 2 ;;
    --port)         PORT="$2"; shift 2 ;;
    --user)         SSH_USER="$2"; shift 2 ;;
    --pass)         SSH_PASS="$2"; shift 2 ;;
    *) echo "未知参数: $1"; exit 1 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")" && pwd)"
JAR_PATH="$ROOT/jshERP-boot/target/jshERP.jar"
WEB_DIR="$ROOT/jshERP-web"

# 需要 sshpass
if ! command -v sshpass >/dev/null 2>&1; then
  echo "请先安装 sshpass: apt-get install -y sshpass 或 brew install hudochenkov/sshpass/sshpass"
  exit 1
fi

SSH_OPTS=(-o StrictHostKeyChecking=no -o ConnectTimeout=20 -p "$PORT")

run_remote() {
  sshpass -p "$SSH_PASS" ssh "${SSH_OPTS[@]}" "$SSH_USER@$SERVER" "$@"
}

upload_file() {
  sshpass -p "$SSH_PASS" scp -o StrictHostKeyChecking=no -P "$PORT" "$@"
}

echo "==> 检查 $SERVER:$PORT 连通性 ..."
run_remote "echo SSH_OK" || { echo "SSH 连接失败"; exit 1; }

# 1. 打包
if [[ "$SKIP_BUILD" != true ]]; then
  echo "==> 打包后端 jar ..."
  ( cd "$ROOT/jshERP-boot" && mvn clean package -DskipTests -q )
  if [[ "$FRONTEND" == true ]]; then
    echo "==> 构建前端 dist ..."
    ( cd "$WEB_DIR" && npm run build )
  fi
fi

[[ -f "$JAR_PATH" ]] || { echo "未找到打包产物: $JAR_PATH"; exit 1; }

# 2. 上传
echo "==> 创建远端目录并上传 jar ..."
run_remote "mkdir -p $REMOTE_DIR"
upload_file "$JAR_PATH" "$SSH_USER@$SERVER:$REMOTE_DIR/jshERP.jar"
if [[ "$FRONTEND" == true ]]; then
  echo "==> 上传前端 dist ..."
  upload_file -r "$WEB_DIR/dist" "$SSH_USER@$SERVER:$REMOTE_DIR/"
fi

# 3. 重启（自动探测运行时）
echo "==> 重启远端服务 ..."
run_remote bash -s <<REMOTE
cd "$REMOTE_DIR" || exit 1
if command -v docker >/dev/null 2>&1 && [ -f docker-compose.yml ] && docker compose version >/dev/null 2>&1; then
  echo "MODE=docker"
  docker compose up -d --force-recreate app 2>&1 | tail -2
elif command -v docker-compose >/dev/null 2>&1 && [ -f docker-compose.yml ]; then
  echo "MODE=docker-compose"
  docker-compose up -d --force-recreate app 2>&1 | tail -2
elif systemctl list-unit-files 2>/dev/null | grep -q '^jsherp'; then
  echo "MODE=systemd"
  systemctl restart jsherp && systemctl is-active jsherp
elif [ -f jshERP.jar ]; then
  echo "MODE=java"
  pkill -f 'jshERP.jar' 2>/dev/null
  sleep 2
  nohup java -jar jshERP.jar > app.log 2>&1 &
  sleep 3
  pgrep -f 'jshERP.jar' >/dev/null && echo "java started pid=\$(pgrep -f 'jshERP.jar' | head -1)"
else
  echo "MODE=unknown"
  echo "未在 $REMOTE_DIR 找到 docker-compose.yml / jsherp 服务 / jshERP.jar"
fi
REMOTE

# 4. 验证
echo "==> 等待服务启动 ($WAIT_SEC s) ..."
sleep "$WAIT_SEC"
run_remote "curl -s -o /dev/null -w 'app  HTTP %{http_code}\n' --max-time 10 http://127.0.0.1:$APP_PORT/jshERP-boot/user/randomImage; curl -s -o /dev/null -w 'web  HTTP %{http_code}\n' --max-time 10 http://127.0.0.1:$WEB_PORT/"

echo ""
echo "完成。访问地址: http://${SERVER}:${WEB_PORT}"
