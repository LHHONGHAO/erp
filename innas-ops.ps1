<#
.SYNOPSIS
  www.innas.xyz 运维脚本：状态、日志、重启、回滚、数据库备份、交互式 SSH。
.DESCRIPTION
  与 deploy-innas.ps1 共用 tools\innas-common.ps1 的 SSH 配置（IPv6、主机指纹、密码来源）。

  动作说明：
    status    容器状态、产物 md5、磁盘占用、HTTP/HTTPS 健康检查
    logs      查看应用日志（默认最近 200 行，可 -Follow 实时跟踪）
    restart   仅重启应用容器（等价于 docker compose up -d --force-recreate app）
    rollback  回滚到最近一次备份的 jar / 前端并重启
    backup    导出数据库到 backup/mysql（保留最近 7 份）
    shell     打开交互式 SSH 会话
.EXAMPLE
  .\innas-ops.ps1 status -IPv6
  .\innas-ops.ps1 logs -IPv6 -Tail 500
  .\innas-ops.ps1 logs -IPv6 -Follow
  .\innas-ops.ps1 rollback -IPv6
  .\innas-ops.ps1 backup -IPv6
  .\innas-ops.ps1 shell -IPv6
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet('status', 'logs', 'restart', 'rollback', 'backup', 'shell')]
    [string]$Action = 'status',
    [int]$Tail = 200,
    [string]$Since = '',
    [switch]$Follow,
    [string]$Server    = 'www.innas.xyz',
    [int]   $Port      = 22,
    [string]$SshUser   = 'root',
    [string]$RemoteDir = '',
    [string]$HostKey   = '',
    [switch]$IPv6,
    [string]$SshPassword = '',
    [int]   $AppPort   = 9998,
    [int]   $WebPort   = 9999
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8; $OutputEncoding = [Text.Encoding]::UTF8 } catch {}

$root     = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolsDir = Join-Path $root 'tools'

if (-not $IPv6 -and $Server -match ':') { $IPv6 = [switch]$true }

$commonModule = Join-Path $toolsDir 'innas-common.ps1'
if (-not (Test-Path $commonModule)) { throw "缺少公共模块: $commonModule" }
. $commonModule

Ensure-InnasTools -ToolsDir $toolsDir
if (-not (Test-InnasPort -Server $Server -Port $Port -IPv6:$IPv6)) {
    $hint = Get-InnasAddressHint -Server $Server
    $msg = "无法连接 ${Server}:${Port}（IPv6=$([bool]$IPv6)）。"
    if ($hint) { $msg += " $hint。" }
    throw $msg
}
$session = Initialize-InnasSession -Server $Server -Port $Port -User $SshUser -HostKey $HostKey -IPv6:$IPv6 `
    -Password $SshPassword -ToolsDir $toolsDir

# 交互式登录不需要走命令通道
if ($Action -eq 'shell') {
    Write-Host "==> 打开交互式 SSH：$($session.Target)（输入 exit 退出）"
    $argList = @('-ssh') + $session.ConnArgs + @('-P', $session.Port, $session.Target, '-hostkey', $session.HostKey)
    & $session.Plink @argList
    return
}

$RemoteDir = Find-InnasRemoteDir -Preferred $RemoteDir
Write-Host "==> 目标 $($session.Target)  $RemoteDir  动作=$Action"

switch ($Action) {
    'status' {
        $script = @'
cd __DIR__ || exit 1
echo "== 容器 =="
docker ps -a --filter name=jsherp --format '{{.Names}} | {{.Status}} | {{.Ports}}'
echo "== 产物 =="
ls -lh jshERP.jar 2>/dev/null | awk '{print $5, $6, $7, $8, $9}'
md5sum jshERP.jar 2>/dev/null
echo "== 备份数量 =="
echo "jar: $(ls -1 jshERP.jar.bak-* 2>/dev/null | wc -l)  前端: $(ls -1d frontend-dist-bak-* 2>/dev/null | wc -l)"
echo "== 磁盘 =="
df -h /mnt/mmc0-4 2>/dev/null | tail -1
echo "== 健康 =="
a=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:__APP__/user/randomImage)
w=$(curl -sk -o /dev/null -w '%{http_code}' --max-time 5 https://127.0.0.1:__WEB__/)
echo "app=$a web=$w"
'@
        $script = $script -replace '__DIR__', $RemoteDir -replace '__APP__', "$AppPort" -replace '__WEB__', "$WebPort"
        Invoke-InnasScript -Script $script | Out-Null
    }

    'logs' {
        $cmd = "docker logs --tail $Tail"
        if ($Since) { $cmd += " --since $Since" }
        if ($Follow) { $cmd += ' -f' }
        $cmd += " jsherp-app 2>&1"
        Invoke-Innas -Command $cmd | Out-Null
    }

    'restart' {
        $script = @'
cd __DIR__ || exit 1
if [ -f docker-compose.yml ] && docker compose version >/dev/null 2>&1; then
  docker compose up -d --force-recreate app 2>&1 | tail -3
elif [ -f docker-compose.yml ]; then
  docker-compose up -d --force-recreate app 2>&1 | tail -3
else
  echo "MODE=unknown"; exit 1
fi
'@
        $script = $script -replace '__DIR__', $RemoteDir
        Invoke-InnasScript -Script $script | Out-Null
        Write-Host '==> 重启完成，等待健康检查 ...'
        Start-Sleep -Seconds 25
        $script = @'
a=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:__APP__/user/randomImage)
w=$(curl -sk -o /dev/null -w '%{http_code}' --max-time 5 https://127.0.0.1:__WEB__/)
echo "app=$a web=$w"
'@
        $script = $script -replace '__APP__', "$AppPort" -replace '__WEB__', "$WebPort"
        Invoke-InnasScript -Script $script | Out-Null
    }

    'rollback' {
        $script = @'
cd __DIR__ || exit 1
latest=$(ls -1t jshERP.jar.bak-* 2>/dev/null | head -1)
if [ -n "$latest" ]; then
  cp "$latest" jshERP.jar
  echo "jar 已回滚: $latest"
else
  echo "没有 jar 备份，跳过"
fi
latestdir=$(ls -1dt frontend-dist-bak-* 2>/dev/null | head -1)
if [ -n "$latestdir" ]; then
  mv frontend-dist "frontend-dist-rolled-$(date +%Y%m%d-%H%M%S)" 2>/dev/null
  mv "$latestdir" frontend-dist
  echo "前端已回滚: $latestdir"
else
  echo "没有前端备份，跳过"
fi
if [ -f docker-compose.yml ]; then
  if docker compose version >/dev/null 2>&1; then docker compose up -d --force-recreate app 2>&1 | tail -3; else docker-compose up -d --force-recreate app 2>&1 | tail -3; fi
fi
'@
        $script = $script -replace '__DIR__', $RemoteDir
        Invoke-InnasScript -Script $script | Out-Null
    }

    'backup' {
        $script = @'
cd __DIR__ || exit 1
pw=$(grep '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)
mkdir -p backup/mysql
f="backup/mysql/jsh_erp-$(date +%Y%m%d-%H%M%S).sql.gz"
docker exec -e MYSQL_PWD="$pw" jsherp-mysql mysqldump -uroot --single-transaction --routines --triggers jsh_erp | gzip > "$f"
if [ -s "$f" ]; then
  echo "备份完成: $f ($(du -h "$f" | cut -f1))"
else
  echo "备份失败: 文件为空"; rm -f "$f"; exit 1
fi
ls -1t backup/mysql/*.sql.gz 2>/dev/null | sed '1,7d' | while read x; do rm -f "$x"; done
echo "保留最近 7 份备份"
'@
        $script = $script -replace '__DIR__', $RemoteDir
        Invoke-InnasScript -Script $script | Out-Null
    }
}
