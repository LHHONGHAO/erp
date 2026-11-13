<#
.SYNOPSIS
  www.innas.xyz 首次部署 / 环境重建：准备目录、compose、.env，启动 mysql+redis，然后调用增量部署脚本。
.DESCRIPTION
  适用场景：
    - 新服务器第一次上线 jshERP；
    - 目录被误删或 compose/.env 丢失后重建运行环境；
    - 需要重置数据库与 Redis 密码（配合 -RotateSecrets）。

  已存在的 .env 默认不会被覆盖，避免把线上数据库密码改掉导致数据打不开。

  日常发版请直接用 deploy-innas.ps1，本脚本只在环境层面需要调整时使用。
.PARAMETER RotateSecrets
  重新生成 .env 中的数据库/Redis 密码（会导致新密码无法连接旧数据卷，请确认后再用）。
.PARAMETER ForceCompose
  用仓库里的 deploy\remote\docker-compose.yml 覆盖远端文件（远端旧文件会备份）。
.PARAMETER NoDeploy
  只准备环境，不触发 jar / 前端部署。
.EXAMPLE
  .\deploy-innas-init.ps1 -IPv6 -Frontend
  .\deploy-innas-init.ps1 -IPv6 -ForceCompose -NoDeploy
#>
param(
    [switch]$Frontend,
    [switch]$SkipBuild,
    [switch]$RotateSecrets,
    [switch]$ForceCompose,
    [switch]$NoDeploy,
    [string]$Server    = 'www.innas.xyz',
    [int]   $Port      = 22,
    [string]$SshUser   = 'root',
    [string]$RemoteDir = '/mnt/mmc0-4/jsherp',
    [string]$HostKey   = '',
    [switch]$IPv6,
    [string]$SshPassword = '',
    [int]   $TimeoutSec = 90
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8; $OutputEncoding = [Text.Encoding]::UTF8 } catch {}

$root     = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolsDir = Join-Path $root 'tools'
$composeSrc = Join-Path $root 'deploy\remote\docker-compose.yml'

if (-not $IPv6 -and $Server -match ':') { $IPv6 = [switch]$true }

$commonModule = Join-Path $toolsDir 'innas-common.ps1'
if (-not (Test-Path $commonModule)) { throw "缺少公共模块: $commonModule" }
. $commonModule

function New-RandomSecret([int]$Bytes = 16) {
    $buf = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buf) } finally { $rng.Dispose() }
    return (-join ($buf | ForEach-Object { $_.ToString('x2') }))
}

# ---------------- 0. 连接 ----------------
Write-Host '==> [0/6] 建立 SSH 会话 ...'
Ensure-InnasTools -ToolsDir $toolsDir
if (-not (Test-InnasPort -Server $Server -Port $Port -IPv6:$IPv6)) {
    $hint = Get-InnasAddressHint -Server $Server
    $msg = "无法连接 ${Server}:${Port}（IPv6=$([bool]$IPv6)）。"
    if ($hint) { $msg += " $hint。" }
    throw $msg
}
$session = Initialize-InnasSession -Server $Server -Port $Port -User $SshUser -HostKey $HostKey -IPv6:$IPv6 `
    -Password $SshPassword -ToolsDir $toolsDir
Write-Host "   目标: $($session.Target)"

# ---------------- 1. 远端运行时检查 ----------------
Write-Host '==> [1/6] 检查远端 Docker 环境 ...'
$runtimeCheck = @'
if ! command -v docker >/dev/null 2>&1; then echo "NO_DOCKER"; exit 1; fi
if docker compose version >/dev/null 2>&1; then echo "COMPOSE=docker compose"; exit 0; fi
if command -v docker-compose >/dev/null 2>&1; then echo "COMPOSE=docker-compose"; exit 0; fi
echo "NO_COMPOSE"
exit 1
'@
$runtime = Invoke-InnasScript -Script $runtimeCheck -Quiet -AllowFailure
$runtimeText = ($runtime -join "`n")
if ($runtimeText -notmatch 'COMPOSE=') {
    throw "远端缺少 Docker Compose：$runtimeText"
}
Write-Host "   $($runtimeText.Trim())"

# ---------------- 2. 目录准备 ----------------
Write-Host '==> [2/6] 创建持久化目录 ...'
$prepareDirs = @'
set -e
base=__DIR__
mkdir -p "$base/data/mysql" "$base/data/redis" "$base/data/logs" "$base/data/upload" "$base/data/export"
mkdir -p "$base/backup/mysql" "$base/frontend-dist"
chmod 750 "$base/data/mysql" 2>/dev/null || true
echo "dirs ready: $base"
'@
$prepareDirs = $prepareDirs -replace '__DIR__', $RemoteDir
Invoke-InnasScript -Script $prepareDirs | Out-Null

# ---------------- 3. compose 文件 ----------------
Write-Host '==> [3/6] 同步 docker-compose.yml ...'
$hasCompose = ((Invoke-Innas -Command "test -f $RemoteDir/docker-compose.yml && echo YES" -Quiet -AllowFailure) -join '') -match 'YES'
if (-not (Test-Path $composeSrc)) { throw "仓库缺少 compose 模板: $composeSrc" }
if ($hasCompose -and -not $ForceCompose) {
    Write-Host '   远端已存在 docker-compose.yml，跳过（加 -ForceCompose 可覆盖）'
} else {
    if ($hasCompose) {
        $backupCompose = @'
cd __DIR__ || exit 1
cp docker-compose.yml "docker-compose.yml.bak-$(date +%Y%m%d-%H%M%S)"
ls -1t docker-compose.yml.bak-* 2>/dev/null | sed '1,3d' | while read f; do rm -f "$f"; done
echo "compose backed up"
'@
        $backupCompose = $backupCompose -replace '__DIR__', $RemoteDir
        Invoke-InnasScript -Script $backupCompose | Out-Null
    }
    Copy-ToInnas -Path $composeSrc -RemotePath "$RemoteDir/docker-compose.yml"
    Write-Host '   docker-compose.yml 已上传'
}

# ---------------- 4. .env ----------------
Write-Host '==> [4/6] 准备 .env ...'
$hasEnv = ((Invoke-Innas -Command "test -f $RemoteDir/.env && echo YES" -Quiet -AllowFailure) -join '') -match 'YES'
if ($hasEnv -and -not $RotateSecrets) {
    Write-Host '   远端已存在 .env，保留现有密码（加 -RotateSecrets 可重置）'
} else {
    if ($hasEnv) {
        $backupEnv = @'
cd __DIR__ || exit 1
cp .env ".env.bak-$(date +%Y%m%d-%H%M%S)"
echo "env backed up"
'@
        $backupEnv = $backupEnv -replace '__DIR__', $RemoteDir
        Invoke-InnasScript -Script $backupEnv | Out-Null
        Write-Warning '已重置密码：旧数据卷仍用旧密码，若数据库已初始化，新密码将无法连接。'
    }
    $envText = @(
        '# 由 deploy-innas-init.ps1 生成，请勿提交到仓库',
        "MYSQL_ROOT_PASSWORD=$(New-RandomSecret 18)",
        "APP_DB_PASSWORD=$(New-RandomSecret 18)",
        "REDIS_PASSWORD=$(New-RandomSecret 18)"
    ) -join "`n"
    $envFile = Join-Path $env:TEMP 'jsherp-innas.env'
    [IO.File]::WriteAllText($envFile, $envText + "`n", (New-Object Text.UTF8Encoding($false)))
    Copy-ToInnas -Path $envFile -RemotePath "$RemoteDir/.env"
    Remove-Item $envFile -Force
    Invoke-Innas -Command "chmod 600 $RemoteDir/.env" | Out-Null
    Write-Host '   .env 已生成（随机密码，权限 600）'
}

# ---------------- 5. 启动基础服务 ----------------
Write-Host '==> [5/6] 启动 mysql / redis 并等待就绪 ...'
$startData = @'
cd __DIR__ || exit 1
if docker compose version >/dev/null 2>&1; then DC="docker compose"; else DC="docker-compose"; fi
$DC up -d mysql redis 2>&1 | tail -3
pw=$(grep '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)
i=0
while [ $i -lt 40 ]; do
  if docker exec -e MYSQL_PWD="$pw" jsherp-mysql mysqladmin ping -uroot --silent >/dev/null 2>&1; then
    echo "mysql ready"; exit 0
  fi
  i=$((i+1)); sleep 3
done
echo "mysql not ready"; exit 1
'@
$startData = $startData -replace '__DIR__', $RemoteDir
Invoke-InnasScript -Script $startData | Out-Null
Write-Host '   mysql / redis 已就绪'

# ---------------- 6. 部署应用 ----------------
if ($NoDeploy) {
    Write-Host '==> [6/6] 已指定 -NoDeploy，跳过应用部署'
    Write-Host "`n环境已准备完成。执行 .\deploy-innas.ps1$($(if ($IPv6) { ' -IPv6' } else { '' })) -Frontend 上线应用。"
    return
}

Write-Host '==> [6/6] 调用 deploy-innas.ps1 部署应用 ...'
$deployArgs = @('-Server', $Server, '-Port', "$Port", '-SshUser', $SshUser, '-RemoteDir', $RemoteDir, '-TimeoutSec', "$TimeoutSec")
if ($IPv6) { $deployArgs += '-IPv6' }
if ($HostKey) { $deployArgs += @('-HostKey', $HostKey) }
if ($SshPassword) { $deployArgs += @('-SshPassword', $SshPassword) }
if ($Frontend) { $deployArgs += '-Frontend' }
if ($SkipBuild) { $deployArgs += '-SkipBuild' }
& (Join-Path $root 'deploy-innas.ps1') @deployArgs
