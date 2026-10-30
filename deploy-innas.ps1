<#
.SYNOPSIS
  jshERP 一键部署脚本（远端主机 www.innas.xyz）：打包 -> 上传 -> 重启服务 -> 验证
.DESCRIPTION
  在本仓库根目录执行。默认只部署后端；前端有改动时加 -Frontend。
  远端运行时自动探测，依次尝试：
    1) Docker Compose（存在 docker-compose.yml 且 docker 可用）
    2) systemd 服务（存在 jsherp 服务单元）
    3) 直接 java -jar（nohup 后台运行）
.EXAMPLE
  .\deploy-innas.ps1
  .\deploy-innas.ps1 -Frontend
  .\deploy-innas.ps1 -Server 192.168.100.10 -SshPass 'xxx' -Frontend
#>
param(
    [switch]$Frontend,
    [switch]$SkipBuild,
    [string]$Server    = 'www.innas.xyz',
    [int]   $Port      = 22,
    [string]$SshUser   = 'root',
    [string]$SshPass   = 'password',
    [string]$RemoteDir = '/root/jsherp',
    [string]$HostKey   = '',
    [int]   $WebPort   = 9999,
    [int]   $AppPort   = 9998,
    [int]   $WaitSec   = 50
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8; $OutputEncoding = [Text.Encoding]::UTF8 } catch {}

$root      = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolsDir  = Join-Path $root 'tools'
$plink     = Join-Path $toolsDir 'plink.exe'
$pscp      = Join-Path $toolsDir 'pscp.exe'
$mavenHome = Join-Path $toolsDir 'apache-maven-3.6.3'
$jarPath   = Join-Path $root 'jshERP-boot\target\jshERP.jar'
$webDir    = Join-Path $root 'jshERP-web'

function Ensure-Tool([string]$Path, [string]$Url) {
    if (-not (Test-Path $Path)) {
        Write-Host "下载 $(Split-Path $Path -Leaf) ..."
        Invoke-WebRequest -Uri $Url -OutFile $Path -UseBasicParsing -TimeoutSec 120
    }
}
function Ensure-Tools {
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    if (-not (Test-Path $plink)) { Ensure-Tool $plink 'https://the.earth.li/~sgtatham/putty/latest/w64/plink.exe' }
    if (-not (Test-Path $pscp))  { Ensure-Tool $pscp  'https://the.earth.li/~sgtatham/putty/latest/w64/pscp.exe' }
    if (-not (Test-Path (Join-Path $mavenHome 'bin\mvn.cmd'))) {
        if (Get-Command mvn -ErrorAction SilentlyContinue) { $script:mavenHome = $null }
        else {
            Write-Host '下载 Maven 3.6.3 ...'
            $zip = Join-Path $env:TEMP 'maven.zip'
            Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.zip' -OutFile $zip -UseBasicParsing -TimeoutSec 300
            Expand-Archive $zip -DestinationPath $toolsDir -Force
        }
    }
    # 首次连接自动信任主机密钥（未显式提供 -HostKey 时）
    if (-not $script:HostKey) {
        Write-Host "==> 首次连接 $Server，接收主机密钥 ..."
        $prev = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $out = "y`n" | & $plink -ssh -P $Port "$SshUser@$Server" -pw $SshPass "exit" 2>&1
            $code = $LASTEXITCODE
        } finally { $ErrorActionPreference = $prev }
        if ($code -ne 0) {
            throw @"
SSH 连接 ${Server}:${Port} 失败（握手后连接被关闭）。
可能原因：
  1) 域名在本机被代理/VPN 解析成了假 IP（如 198.18.x.x），请改用真实局域网 IP：-Server 192.168.100.x
  2) SSH 端口不是 ${Port}，用 -Port 指定
  3) 目标主机未开启 SSH，或需要先连 VPN
plink 输出: $($out -join ' ')
"@
        }
    }
}
function Invoke-Plink([string]$RemoteCmd) {
    $argList = @('-ssh', '-P', $Port, "$SshUser@$Server", '-pw', $SshPass, '-batch')
    if ($script:HostKey) { $argList += @('-hostkey', $script:HostKey) }
    $argList += $RemoteCmd
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $out = & $plink @argList 2>&1; $code = $LASTEXITCODE } finally { $ErrorActionPreference = $prev }
    $out | ForEach-Object { Write-Host $_ }
    if ($code -ne 0) { throw "远程命令执行失败（退出码 $code）: $RemoteCmd" }
}
function Invoke-Pscp([string[]]$Local, [string]$Remote) {
    $argList = @('-batch', '-P', $Port, '-pw', $SshPass)
    if ($script:HostKey) { $argList += @('-hostkey', $script:HostKey) }
    $argList += $Local
    $argList += "$SshUser@$Server`:$Remote"
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $out = & $pscp @argList 2>&1; $code = $LASTEXITCODE } finally { $ErrorActionPreference = $prev }
    $out | ForEach-Object { Write-Host $_ }
    if ($code -ne 0) { throw '文件上传失败' }
}
function Invoke-Maven([string[]]$Goals) {
    $mvn = if ($mavenHome) { Join-Path $mavenHome 'bin\mvn.cmd' } else { 'mvn' }
    Push-Location (Join-Path $root 'jshERP-boot')
    try {
        & $mvn @Goals 2>&1 | Select-Object -Last 5
        if ($LASTEXITCODE -ne 0) { throw "maven 执行失败: $Goals" }
    } finally { Pop-Location }
}

# 0. 连通性检查
Write-Host "==> 检查 $Server 连通性 ..."
$tcp = Test-NetConnection -ComputerName $Server -Port $Port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
    throw "无法连接 ${Server}:${Port}。请确认主机可达、SSH 端口正确；若为内网域名，请先连接 VPN 或在 hosts 中配置真实 IP。"
}

# 1. 打包
if (-not $SkipBuild) {
    Ensure-Tools
    Write-Host '==> 打包后端 jar ...'
    Invoke-Maven @('clean', 'package', '-DskipTests', '-q')
    if ($Frontend) {
        Write-Host '==> 构建前端 dist ...'
        Push-Location $webDir
        try {
            $nodeDir = @(
                "$env:LOCALAPPDATA\Programs\node16\node-v16.20.2-win-x64",
                "$env:ProgramFiles\nodejs"
            ) | Where-Object { Test-Path (Join-Path $_ 'node.exe') } | Select-Object -First 1
            if ($nodeDir) { $env:Path = "$nodeDir;$env:Path" }
            $prev = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try { $out = npm run build 2>&1; $code = $LASTEXITCODE } finally { $ErrorActionPreference = $prev }
            $out | Select-Object -Last 3
            if ($code -ne 0) { throw '前端构建失败' }
        } finally { Pop-Location }
    }
} else {
    Ensure-Tools
}
if (-not (Test-Path $jarPath)) { throw "未找到打包产物: $jarPath" }

# 2. 上传
Write-Host '==> 创建远端目录并上传 jar ...'
Invoke-Plink "mkdir -p $RemoteDir"
Invoke-Pscp @($jarPath) "$RemoteDir/jshERP.jar"
if ($Frontend) {
    Write-Host '==> 上传前端 dist ...'
    Invoke-Pscp @('-r', (Join-Path $webDir 'dist')) "$RemoteDir/"
}

# 3. 重启（自动探测运行时）
Write-Host '==> 重启远端服务 ...'
$restart = @'
cd REMOTE_DIR || exit 1
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
  pgrep -f 'jshERP.jar' >/dev/null && echo "java started pid=$(pgrep -f 'jshERP.jar' | head -1)"
else
  echo "MODE=unknown"
  echo "未在 $RemoteDir 找到 docker-compose.yml / jsherp 服务 / jshERP.jar"
fi
'@
Invoke-Plink ($restart -replace 'REMOTE_DIR', $RemoteDir)

# 4. 验证
Write-Host "==> 等待服务启动 ($WaitSec s) ..."
Start-Sleep $WaitSec
$verify = "curl -s -o /dev/null -w 'app  HTTP %{http_code}\n' --max-time 10 http://127.0.0.1:$AppPort/jshERP-boot/user/randomImage; curl -s -o /dev/null -w 'web  HTTP %{http_code}\n' --max-time 10 http://127.0.0.1:$WebPort/"
Invoke-Plink $verify
Write-Host "`n完成。访问地址: http://${Server}:${WebPort}"
