﻿# 本地部署（本机跑后端，数据连远程数据库）
#
# 做法：先用 SSH 隧道把远程 MySQL / Redis 映射到本机端口，再启动本机后端，
#      后端仍连 127.0.0.1，但实际数据来自远程服务器，且不向公网暴露数据库。
#
# 用法：双击「本地启动.bat」；或本机执行
#   powershell -NoProfile -ExecutionPolicy Bypass -File start-local.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File start-local.ps1 -Frontend   # 同时起前端 dev(3000)
param(
    [string]$Server      = 'www.innas.xyz',
    [int]   $Port        = 22,
    [string]$SshUser     = 'root',
    [int]   $DbPort      = 13306,
    [int]   $RedisPort   = 16379,
    [int]   $AppPort     = 9998,
    [switch]$Frontend,
    [string]$HostKey     = 'SHA256:R2PbBdYWF37HyDs9OpcQrxw12YQUanbsS4s5gG8MxZo'
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$plink = Join-Path $root 'tools\plink.exe'
$jar = Join-Path $root 'jshERP-boot\target\jshERP.jar'
$deployConfig = Get-Content (Join-Path $root 'deploy.config.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$dataDir = [string]$deployConfig.dataDir
if (-not $dataDir -or -not [IO.Path]::IsPathRooted($dataDir)) { throw 'deploy.config.json 的 dataDir 必须是绝对路径' }
foreach ($subDir in @('upload', 'export', 'config', 'logs')) {
    New-Item -ItemType Directory -Path (Join-Path $dataDir $subDir) -Force | Out-Null
}
if (-not $env:SPRING_DATASOURCE_USERNAME -or -not $env:SPRING_DATASOURCE_PASSWORD) {
    throw '请先设置 SPRING_DATASOURCE_USERNAME 和 SPRING_DATASOURCE_PASSWORD 环境变量，使用数据库专用账号。'
}

if (-not (Test-Path $plink)) { throw "缺少 $plink" }
if (-not (Test-Path $jar)) { throw "缺少后端包 $jar，请先执行 deploy-innas.ps1 或 mvn package 生成" }

Write-Host ("==> 建立 SSH 隧道 {0}:{1} -> 远程 MySQL，{2} -> 远程 Redis ..." -f $DbPort, '3306', $RedisPort)
$tunnel = Start-Process -FilePath $plink -PassThru -WindowStyle Hidden -ArgumentList @(
    '-ssh', '-batch', '-N',
    '-P', $Port,
    '-L', ("{0}:127.0.0.1:3306" -f $DbPort),
    '-L', ("{0}:127.0.0.1:6379" -f $RedisPort),
    ("{0}@{1}" -f $SshUser, $Server),
    '-hostkey', $HostKey
)

# 等待隧道端口就绪
$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Milliseconds 500
    if ($tunnel.HasExited) { throw 'SSH 隧道启动失败，请检查网络、SSH 密钥和 HostKey' }
    try {
        $c = New-Object Net.Sockets.TcpClient
        $c.Connect('127.0.0.1', $DbPort)
        $c.Close()
        $ready = $true
        break
    } catch {}
}
if (-not $ready) { throw "隧道端口 $DbPort 未就绪" }
Write-Host '==> 隧道已就绪（远程库不对外暴露）'

$dbUrl = "jdbc:mysql://127.0.0.1:$DbPort/jsh_erp?useUnicode=true&characterEncoding=utf8&useCursorFetch=true&defaultFetchSize=500&allowMultiQueries=true&rewriteBatchedStatements=true&useSSL=false&allowPublicKeyRetrieval=true"

$javaArgs = @(
    ('-Djsh.data.dir={0}' -f ($dataDir -replace '\\', '/'))
    ('-Dlogs.home={0}' -f ((Join-Path $dataDir 'logs') -replace '\\', '/'))
    '-jar'
    $jar
    ('--server.port={0}' -f $AppPort)
    ('--spring.datasource.url={0}' -f $dbUrl)
    '--spring.redis.host=127.0.0.1'
    ('--spring.redis.port={0}' -f $RedisPort)
)

Write-Host ("==> 启动本地后端（端口 {0}，数据来自 {1}）..." -f $AppPort, $Server)
$javaExe = 'java'
if ($env:JAVA_HOME) { $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe' }

$front = $null
if ($Frontend) {
    Write-Host '==> 启动前端开发服务（http://localhost:3000，接口代理到本地后端）...'
    $env:VUE_APP_API_TARGET = "http://localhost:$AppPort"
    $npm = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source
    if (-not $npm) { throw '未找到 npm，请先安装 Node.js' }
    $front = Start-Process -FilePath $npm -WorkingDirectory (Join-Path $root 'jshERP-web') -PassThru -ArgumentList 'run', 'serve'
}

try {
    & $javaExe @javaArgs
} finally {
    if ($front -and -not $front.HasExited) { Stop-Process -Id $front.Id -Force -ErrorAction SilentlyContinue }
    if ($tunnel -and -not $tunnel.HasExited) { Stop-Process -Id $tunnel.Id -Force -ErrorAction SilentlyContinue }
    Write-Host '==> 已停止本地后端与 SSH 隧道'
}
