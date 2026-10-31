﻿<#
.SYNOPSIS
  JSH_ERP 本地一键部署脚本（Windows PowerShell 5 兼容）
.DESCRIPTION
  在本机完成：停止旧服务 → 拉代码 → 构建前端 → 打包后端 → 链接前端产物 → 启动服务 → 验证
.PARAMETER SkipWeb
  跳过前端构建
.PARAMETER SkipBoot
  跳过后端打包
.PARAMETER SkipBuild
  跳过所有打包步骤（只重启服务，使用现有产物）
.PARAMETER StartOnly
  只启动服务（不构建）
.PARAMETER StopOnly
  只停止服务
.PARAMETER NoPull
  不执行 git pull
.PARAMETER JavaHome
  指定 JDK 路径，不传则自动探测
.PARAMETER MvnPath
  指定 Maven mvn.cmd 路径，不传则自动探测
.PARAMETER Port
  服务端口，默认 9999
.EXAMPLE
  .\deploy-local.ps1                # 完整部署
  .\deploy-local.ps1 -SkipBoot      # 只更新前端
  .\deploy-local.ps1 -SkipWeb       # 只更新后端
  .\deploy-local.ps1 -StartOnly     # 只启动（不构建）
  .\deploy-local.ps1 -StopOnly      # 只停止
#>
param(
    [switch]$SkipWeb,
    [switch]$SkipBoot,
    [switch]$SkipBuild,
    [switch]$StartOnly,
    [switch]$StopOnly,
    [switch]$NoPull,
    [string]$JavaHome = '',
    [string]$MvnPath  = '',
    [int]$Port        = 9999
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8; $OutputEncoding = [Text.Encoding]::UTF8 } catch {}

# ============================================================
# 路径常量
# ============================================================
$Root    = Split-Path -Parent $MyInvocation.MyCommand.Path
$BootDir = Join-Path $Root 'jshERP-boot'
$WebDir  = Join-Path $Root 'jshERP-web'
$Jar     = Join-Path $BootDir 'target\jshERP.jar'
$Log     = Join-Path $BootDir 'deploy-backend.log'
$ErrLog  = Join-Path $BootDir 'deploy-backend.err.log'
# application.yml 中配置的外部静态资源目录（相对 jshERP-boot 启动目录）
$StaticLink = Join-Path $BootDir 'frontend-dist'
# 统一数据目录：图片、导出缓存、外部配置、数据库备份都放在这里，重新部署不受影响
$DataDir = Join-Path (Split-Path -Parent $Root) 'jshERP-data'
$DataDirs = @('upload', 'export', 'config', 'backup', 'logs')

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  JSH_ERP 本地部署脚本" -ForegroundColor Cyan
Write-Host "  项目根: $Root" -ForegroundColor DarkGray
Write-Host "  端口  : $Port" -ForegroundColor DarkGray
Write-Host "============================================================" -ForegroundColor Cyan

# ============================================================
# 工具函数
# ============================================================
function Write-Step([string]$Msg) {
    Write-Host ""
    Write-Host "==> $Msg" -ForegroundColor Yellow
}
function Write-Ok([string]$Msg) {
    Write-Host "    [OK] $Msg" -ForegroundColor Green
}
function Write-Warn([string]$Msg) {
    Write-Host "    [WARN] $Msg" -ForegroundColor DarkYellow
}
function Write-Fail([string]$Msg) {
    Write-Host "    [FAIL] $Msg" -ForegroundColor Red
}

function Initialize-DataDir {
    Write-Step "初始化统一数据目录 $DataDir"
    if (-not (Test-Path $DataDir)) {
        New-Item -ItemType Directory -Path $DataDir -Force | Out-Null
        Write-Ok "已创建数据目录"
    }
    foreach ($sub in $DataDirs) {
        $p = Join-Path $DataDir $sub
        if (-not (Test-Path $p)) { New-Item -ItemType Directory -Path $p -Force | Out-Null }
    }
    Write-Ok "子目录就绪: $($DataDirs -join ', ')"
    # 首次部署：把项目内的 OCR 等配置迁移到统一数据目录（之后更新部署不会覆盖）
    $srcCfg = Join-Path $BootDir 'config\ocr-config.properties'
    $dstCfg = Join-Path $DataDir 'config\ocr-config.properties'
    if ((Test-Path $srcCfg) -and (-not (Test-Path $dstCfg))) {
        Copy-Item $srcCfg $dstCfg -Force
        Write-Ok "已迁移 ocr-config.properties 到数据目录"
    }
}

function Stop-OldService([int]$ListenPort) {
    Write-Step "停止旧服务（端口 $ListenPort）"
    $conn = Get-NetTCPConnection -LocalPort $ListenPort -State Listen -ErrorAction SilentlyContinue
    if (-not $conn) {
        Write-Ok "端口 $ListenPort 未被占用"
        return
    }
    foreach ($c in $conn) {
        try {
            Stop-Process -Id $c.OwningProcess -Force -ErrorAction Stop
            Write-Ok "已终止 PID $($c.OwningProcess)"
        } catch {
            Write-Warn "终止 PID $($c.OwningProcess) 失败: $_"
        }
    }
    Start-Sleep -Milliseconds 500
}

function Find-Java {
    param([string]$Hint)
    $candidates = @()
    if ($Hint) { $candidates += $Hint }
    $candidates += @(
        "$env:USERPROFILE\.jdks\jdk8u492-b09",
        "C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot",
        "C:\Program Files\Java\jdk1.8.0_*",
        "$env:ProgramFiles\Java\jdk1.8.0_*"
    )
    foreach ($c in $candidates) {
        $resolved = Resolve-Path $c -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Path
        if ($resolved) {
            $javaExe = Join-Path $resolved 'bin\java.exe'
            if (Test-Path $javaExe) { return $resolved }
        }
    }
    if (Get-Command java -ErrorAction SilentlyContinue) {
        $cmd = Get-Command java
        $p = Split-Path $cmd.Source -Parent
        return (Split-Path $p -Parent)
    }
    throw "未找到 JDK 8，请通过 -JavaHome 指定"
}

function Find-Maven {
    param([string]$Hint)
    if ($Hint -and (Test-Path $Hint)) { return $Hint }
    $candidates = @(
        "C:\Program Files\apache-maven-3.9.16\bin\mvn.cmd",
        "C:\apache-maven-*\bin\mvn.cmd",
        "$env:USERPROFILE\apache-maven-*\bin\mvn.cmd"
    )
    foreach ($c in $candidates) {
        $r = Resolve-Path $c -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Path
        if ($r) { return $r }
    }
    if (Get-Command mvn -ErrorAction SilentlyContinue) { return 'mvn' }
    throw "未找到 Maven，请通过 -MvnPath 指定"
}

function Invoke-External {
    param(
        [string]$File,
        [string[]]$CmdArgs,
        [string]$WorkDir = $null,
        [string]$Desc = ''
    )
    Write-Host "    ${File} $($CmdArgs -join ' ')" -ForegroundColor DarkGray
    # Start-Process -ArgumentList 数组方式对 .cmd/.exe 批处理参数传递最可靠
    $oldPwd = $null
    if ($WorkDir) {
        $oldPwd = Get-Location
        Set-Location $WorkDir
    }
    try {
        $p = Start-Process -FilePath $File -ArgumentList $CmdArgs -NoNewWindow -Wait -PassThru
        if ($p.ExitCode -ne 0) {
            throw "$Desc 退出码 $($p.ExitCode)"
        }
    } finally {
        if ($oldPwd) { Set-Location $oldPwd }
    }
}

function Launch-Service {
    param([string]$DistPath)

    Initialize-DataDir

    Write-Step "启动后端服务"
    # 用 Start-Process + -ArgumentList 数组，PowerShell 会正确传递每个参数给 java.exe
    # 显式指定静态资源绝对路径，避免依赖进程工作目录
    $staticDir = ($StaticLink -replace '\\', '/') + '/'
    $dataDirArg = ($DataDir -replace '\\', '/')
    $argList = @(
        '-Xms256m', '-Xmx1024m',
        '-Dspring.profiles.active=dev',
        '-Dfile.encoding=UTF-8',
        "-Dspring.resources.static-locations=file:$staticDir",
        "-Djsh.data.dir=$dataDirArg",
        '-jar', $Jar
    )
    $proc = Start-Process -FilePath $javaExe `
        -ArgumentList $argList `
        -WorkingDirectory $BootDir `
        -WindowStyle Minimized `
        -PassThru
    Write-Ok "Java 进程已启动 PID=$($proc.Id)"

    Write-Host "    等待端口 $Port 就绪 ..." -ForegroundColor DarkGray
    $waited = 0
    while ($waited -lt 60) {
        Start-Sleep -Seconds 2
        $waited += 2
        $listening = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($listening) { break }
        Write-Host "      ${waited}s ..." -ForegroundColor DarkGray
    }

    if (-not (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)) {
        Write-Warn "$($waited)s 内未监听到 $Port 端口"
        Write-Host "    请检查日志: $Log" -ForegroundColor DarkGray
    }

    $lanIp = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
        Select-Object -First 1 -ExpandProperty IPAddress)

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "  部署完成" -ForegroundColor Green
    Write-Host "------------------------------------------------------------"
    Write-Host "  本机访问 : http://localhost:$Port/jshERP-boot/"
    if ($lanIp) {
        Write-Host "  局域网   : http://${lanIp}:${Port}/jshERP-boot/"
    }
    Write-Host "  后端日志 : $Log"
    Write-Host "  前端产物 : $StaticLink -> $DistPath"
    Write-Host "  停止命令 : .\deploy-local.ps1 -StopOnly"
    Write-Host "============================================================" -ForegroundColor Green
}

# ============================================================
# 模式分支
# ============================================================
if ($StopOnly) {
    Stop-OldService $Port
    Write-Host "`n已停止。" -ForegroundColor Green
    exit 0
}

# 自动探测工具链
$env:JAVA_HOME = Find-Java -Hint $JavaHome
$env:Path     = "$env:JAVA_HOME\bin;$env:Path"
$javaExe      = Join-Path $env:JAVA_HOME 'bin\java.exe'
Write-Ok "JDK  : $env:JAVA_HOME"

$mvn = Find-Maven -Hint $MvnPath
Write-Ok "Maven: $mvn"

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Fail "未找到 node.exe，请先安装 Node.js"
    exit 1
}
$nodeExe = (Get-Command node -ErrorAction Stop).Source
Write-Ok "Node : $(node -v)"

# npm / git 也解析为完整路径，避免 ProcessStartInfo 在不同 WorkDir 下找错位置
$npmCmd = (Get-Command npm.cmd -ErrorAction Stop).Source
$gitCmd = (Get-Command git.exe -ErrorAction Stop).Source
Write-Ok "npm  : $npmCmd"
Write-Ok "git  : $gitCmd"

# ---------- 停止旧服务 ----------
Stop-OldService $Port

# ---------- 仅启动 ----------
if ($StartOnly) {
    if (-not (Test-Path $Jar)) {
        Write-Fail "找不到 $Jar，请先完整部署"
        exit 1
    }
    # 确保前端链接存在
    if (-not (Test-Path $StaticLink)) {
        $dist = Join-Path $WebDir 'dist'
        if (Test-Path $dist) {
            New-Item -ItemType Junction -Path $StaticLink -Target $dist -Force | Out-Null
            Write-Ok "前端产物已链接到 $StaticLink"
        }
    }
    Launch-Service -DistPath (Join-Path $WebDir 'dist')
    exit 0
}

# ---------- 拉代码 ----------
if (-not $NoPull) {
    Write-Step "拉取最新代码"
    try {
        Invoke-External -File $gitCmd -CmdArgs @('pull','--ff-only') -WorkDir $Root -Desc 'git pull'
        Write-Ok "代码已更新"
    } catch {
        Write-Warn "git pull 失败（网络或本地改动），使用当前代码继续"
    }
}

# ---------- 前端构建 ----------
if (-not $SkipBuild -and -not $SkipWeb) {
    Write-Step "构建前端"
    $nodeModules = Join-Path $WebDir 'node_modules'
    if (-not (Test-Path $nodeModules)) {
        Write-Host "    首次构建，安装依赖 ..." -ForegroundColor DarkGray
        Invoke-External -File $npmCmd -CmdArgs @('install') -WorkDir $WebDir -Desc 'npm install'
    }
    $env:NODE_OPTIONS = '--max-old-space-size=4096'
    Invoke-External -File $npmCmd -CmdArgs @('run','build') -WorkDir $WebDir -Desc 'npm run build'
    Write-Ok "前端构建完成 -> $(Join-Path $WebDir 'dist')"
}

# ---------- 后端打包 ----------
if (-not $SkipBuild -and -not $SkipBoot) {
    Write-Step "打包后端"
    try {
        Invoke-External -File $mvn -CmdArgs @('-o','-DskipTests','clean','package') -WorkDir $BootDir -Desc 'mvn offline package'
    } catch {
        Write-Warn "离线打包失败，改为联网打包"
        Invoke-External -File $mvn -CmdArgs @('-DskipTests','clean','package') -WorkDir $BootDir -Desc 'mvn online package'
    }
    if (-not (Test-Path $Jar)) {
        Write-Fail "未生成 $Jar"
        exit 1
    }
    Write-Ok "后端打包完成 -> $Jar"
}

# ---------- 前端产物同步 ----------
Write-Step "同步前端静态资源"
$dist = Join-Path $WebDir 'dist'
if (Test-Path $dist) {
    # 使用真实目录复制（Junction 在部分沙箱/独立进程中不可读，会导致前端 404）
    if (Test-Path $StaticLink) {
        $item = Get-Item $StaticLink -Force
        if ($item.Attributes -match 'ReparsePoint') { $item.Delete() }
        else { Remove-Item $StaticLink -Recurse -Force }
    }
    Copy-Item -Path $dist -Destination $StaticLink -Recurse -Force
    Write-Ok "已复制前端产物: $dist -> $StaticLink"
} else {
    Write-Warn "前端 dist 不存在，跳过同步"
}

Launch-Service -DistPath $dist
