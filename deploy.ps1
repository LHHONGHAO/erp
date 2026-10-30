<#
.SYNOPSIS
  jshERP 一键部署脚本：打包 -> 上传 -> 重启远端 Docker 容器 -> 验证
.DESCRIPTION
  在本仓库根目录执行。默认只部署后端；前端有改动时加 -Frontend。
  远端使用 Docker Compose（mysql + redis + app + nginx），脚本只做文件更新与 app 容器重建。
.EXAMPLE
  .\deploy.ps1                 # 仅后端
  .\deploy.ps1 -Frontend       # 前后端都部署
  .\deploy.ps1 -SkipBuild      # 不重新打包，直接上传现有产物
#>
param(
    [switch]$Frontend,
    [switch]$SkipBuild,
    [string]$Server   = '192.168.100.1',
    [string]$SshUser  = 'root',
    [string]$SshPass  = 'password',
    [string]$RemoteDir = '/root/jsherp',
    [string]$HostKey  = 'SHA256:R2PbBdYWF37HyDs9OpcQrxw12YQUanbsS4s5gG8MxZo'
)

$ErrorActionPreference = 'Stop'
# 中文输出统一 UTF-8，避免控制台乱码
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
    Ensure-Tool $plink 'https://the.earth.li/~sgtatham/putty/latest/w64/plink.exe'
    Ensure-Tool $pscp  'https://the.earth.li/~sgtatham/putty/latest/w64/pscp.exe'
    if (-not (Test-Path (Join-Path $mavenHome 'bin\mvn.cmd'))) {
        if (Get-Command mvn -ErrorAction SilentlyContinue) { $script:mavenHome = $null }
        else {
            Write-Host '下载 Maven 3.6.3 ...'
            $zip = Join-Path $env:TEMP 'maven.zip'
            Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.zip' -OutFile $zip -UseBasicParsing -TimeoutSec 300
            Expand-Archive $zip -DestinationPath $toolsDir -Force
        }
    }
}
function Invoke-Maven([string]$WorkDir, [string[]]$Goals) {
    $mvn = if ($mavenHome) { Join-Path $mavenHome 'bin\mvn.cmd' } else { 'mvn' }
    Push-Location $WorkDir
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $mvn @Goals 2>&1 | Select-Object -Last 5
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
        Pop-Location
    }
    if ($code -ne 0) { throw "maven 执行失败: $Goals" }
}
function Invoke-Plink([string]$RemoteCmd) {
    & $plink -ssh "$SshUser@$Server" -pw $SshPass -batch -hostkey $HostKey $RemoteCmd
    if ($LASTEXITCODE -ne 0) { throw "plink 执行失败: $RemoteCmd" }
}
function Invoke-Pscp([string[]]$Local, [string]$Remote) {
    & $pscp -batch -pw $SshPass -hostkey $HostKey @Local "$SshUser@$Server`:$Remote"
    if ($LASTEXITCODE -ne 0) { throw "pscp 上传失败" }
}

# 1. 打包
if (-not $SkipBuild) {
    Write-Host '==> 打包后端 jar ...'
    Invoke-Maven (Join-Path $root 'jshERP-boot') @('clean','package','-DskipTests','-q')
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
}

# 2. 上传
Ensure-Tools
Write-Host '==> 上传 jar ...'
Invoke-Pscp @($jarPath) "$RemoteDir/jshERP.jar"
if ($Frontend) {
    Write-Host '==> 上传前端 dist ...'
    Invoke-Pscp @('-r', (Join-Path $webDir 'dist')) "$RemoteDir/"
}

# 3. 重启并验证
Write-Host '==> 重建远端 app 容器 ...'
Invoke-Plink "cd $RemoteDir && docker compose up -d --force-recreate app 2>&1 | tail -1"
Write-Host '==> 等待服务启动 (50s) ...'
Start-Sleep 50
Invoke-Plink "curl -s -o /dev/null -w 'app  HTTP %{http_code}\n' http://127.0.0.1:9998/jshERP-boot/user/randomImage; curl -s -o /dev/null -w 'nginx HTTP %{http_code}\n' http://127.0.0.1:9999/"
Write-Host "`n完成。访问地址: http://${Server}:9999"
