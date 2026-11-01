<#
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
    [string]$MvnPath  = ''
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
# 部署配置唯一来源：端口、数据目录、HTTPS 证书全部固化在项目根目录 deploy.config.json
$ConfigPath = Join-Path $Root 'deploy.config.json'
if (-not (Test-Path $ConfigPath)) { throw "缺少部署配置 $ConfigPath" }
$Cfg = Get-Content $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
$ProxyPort   = [int]$Cfg.proxyPort      # 对外 HTTPS 入口
$BackendPort = [int]$Cfg.backendPort    # 后端仅监听 127.0.0.1
# application.yml 中配置的外部静态资源目录（相对 jshERP-boot 启动目录）
$StaticLink = Join-Path $BootDir 'frontend-dist'
# 前端构建产物目录
$DistDir    = Join-Path $WebDir 'dist'
# 统一数据目录：图片、导出缓存、外部配置、数据库备份、日志全部收敛到项目内，重新部署不受影响
$DataDir = $Cfg.dataDir
$DataDirs = @('upload', 'export', 'config', 'backup', 'logs', 'tmp')
# HTTPS 入口与证书
$ProxyJs  = Join-Path $Root 'tools\local-proxy.js'
$MakeCert = Join-Path $Root 'tools\make-cert.ps1'
$CertPfx  = if ($Cfg.https -and $Cfg.https.pfx) { $Cfg.https.pfx } else { Join-Path $DataDir 'config\jsh-https.p12' }
$ProxyLog = Join-Path $Root 'tools\local-proxy.log'

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  JSH_ERP 本地部署脚本" -ForegroundColor Cyan
Write-Host "  项目根: $Root" -ForegroundColor DarkGray
Write-Host "  入口  : https://<ip>:$ProxyPort  ->  127.0.0.1:$BackendPort" -ForegroundColor DarkGray
Write-Host "  数据  : $DataDir" -ForegroundColor DarkGray
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

function Start-HttpsProxy {
    if (-not (Test-Path $ProxyJs)) {
        Write-Warn "缺少 HTTPS 入口脚本 $ProxyJs，跳过"
        return
    }
    if (-not (Test-Path $CertPfx)) {
        Write-Step "生成 HTTPS 自签名证书"
        Invoke-External -File 'powershell' -CmdArgs @('-NoProfile','-ExecutionPolicy','Bypass','-File',$MakeCert,'-DataDir',$DataDir) -Desc 'make-cert'
    }
    Write-Step "启动 HTTPS 入口（$ProxyPort）"
    $proc = Start-Process -FilePath $nodeExe -ArgumentList @($ProxyJs) -WorkingDirectory $Root `
        -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $ProxyLog -RedirectStandardError ($ProxyLog + '.err')
    Write-Ok "HTTPS 代理进程已启动 PID=$($proc.Id)"
    $waited = 0
    while ($waited -lt 15) {
        Start-Sleep -Seconds 1
        $waited++
        if (Get-NetTCPConnection -LocalPort $ProxyPort -State Listen -ErrorAction SilentlyContinue) { break }
    }
    if (Get-NetTCPConnection -LocalPort $ProxyPort -State Listen -ErrorAction SilentlyContinue) {
        Write-Ok "HTTPS 入口已就绪: $ProxyPort"
    } else {
        Write-Warn "$ProxyPort 未监听，请检查日志: $ProxyLog"
    }
}

function Launch-Service {
    param([string]$DistPath)

    Initialize-DataDir

    Write-Step "启动后端服务（127.0.0.1:$BackendPort）"
    # 用 Start-Process + -ArgumentList 数组，PowerShell 会正确传递每个参数给 java.exe
    # 显式指定静态资源绝对路径，避免依赖进程工作目录
    $staticDir  = ($StaticLink -replace '\\', '/') + '/'
    $dataDirArg = ($DataDir -replace '\\', '/')
    $logsArg    = '-Dlogs.home=' + $dataDirArg + '/logs'
    $argList = @(
        '-Xms256m', '-Xmx1024m',
        '-Dspring.profiles.active=dev',
        '-Dfile.encoding=UTF-8',
        "-Dserver.port=$BackendPort",
        '-Dserver.address=127.0.0.1',
        "-Dspring.resources.static-locations=file:$staticDir",
        "-Djsh.data.dir=$dataDirArg",
        $logsArg,
        '-jar', $Jar
    )
    $proc = Start-Process -FilePath $javaExe `
        -ArgumentList $argList `
        -WorkingDirectory $BootDir `
        -WindowStyle Minimized `
        -RedirectStandardOutput $Log `
        -RedirectStandardError $ErrLog `
        -PassThru
    Write-Ok "Java 进程已启动 PID=$($proc.Id)"

    Write-Host "    等待端口 $BackendPort 就绪 ..." -ForegroundColor DarkGray
    $waited = 0
    while ($waited -lt 60) {
        Start-Sleep -Seconds 2
        $waited += 2
        $listening = Get-NetTCPConnection -LocalPort $BackendPort -State Listen -ErrorAction SilentlyContinue
        if ($listening) { break }
        Write-Host "      ${waited}s ..." -ForegroundColor DarkGray
    }

    if (-not (Get-NetTCPConnection -LocalPort $BackendPort -State Listen -ErrorAction SilentlyContinue)) {
        Write-Warn "$($waited)s 内未监听到 $BackendPort 端口"
        Write-Host "    请检查日志: $Log" -ForegroundColor DarkGray
    }

    Start-HttpsProxy

    $lanIp = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
        Select-Object -First 1 -ExpandProperty IPAddress)

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "  部署完成" -ForegroundColor Green
    Write-Host "------------------------------------------------------------"
    Write-Host "  本机访问 : https://localhost:$ProxyPort/"
    if ($lanIp) {
        Write-Host "  局域网   : https://${lanIp}:${ProxyPort}/"
    }
    Write-Host "  后端日志 : $Log  (仅 127.0.0.1:$BackendPort)"
    Write-Host "  数据目录 : $DataDir"
    Write-Host "  前端产物 : $StaticLink -> $DistPath"
    Write-Host "  停止命令 : .\deploy-local.ps1 -StopOnly"
    Write-Host "============================================================" -ForegroundColor Green
}

# ============================================================
# 模式分支
# ============================================================
if ($StopOnly) {
    Stop-OldService $ProxyPort
    Stop-OldService $BackendPort
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

# ---------- 停止旧服务（先停对外入口，再停后端） ----------
Stop-OldService $ProxyPort
Stop-OldService $BackendPort

# ---------- 仅启动 ----------
if ($StartOnly) {
    if (-not (Test-Path $Jar)) {
        Write-Fail "找不到 $Jar，请先完整部署"
        exit 1
    }
    # 确保前端产物存在（缺失时从构建目录补齐）
    if (-not (Test-Path (Join-Path $StaticLink 'index.html')) -and (Test-Path $DistDir)) {
        Copy-Item -Path $DistDir -Destination $StaticLink -Recurse -Force
        Write-Ok "前端产物已同步到 $StaticLink"
    }
    Launch-Service -DistPath $DistDir
    exit 0
}

# ---------- 拉代码 ----------
if (-not $NoPull) {
    Write-Step "拉取最新代码"
    try {
        Invoke-External -File $gitCmd -CmdArgs @('pull','--ff-only') -WorkDir $Root -Desc 'git pull'
        Write-Ok "代码已更新"
    } catch {
        Write-Fail "git pull 失败（网络不可达或存在本地未提交改动），已中止部署，避免用旧代码发布"
        exit 1
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
if (Test-Path $DistDir) {
    # 使用真实目录复制（Junction 在部分沙箱/独立进程中不可读，会导致前端 404）
    if (Test-Path $StaticLink) {
        $item = Get-Item $StaticLink -Force
        if ($item.Attributes -match 'ReparsePoint') { $item.Delete() }
        else { Remove-Item $StaticLink -Recurse -Force }
    }
    Copy-Item -Path $DistDir -Destination $StaticLink -Recurse -Force
    if (-not (Test-Path (Join-Path $StaticLink 'index.html'))) {
        Write-Fail "前端产物复制失败（可能被运行中的进程占用），请先执行 -StopOnly 再部署"
        exit 1
    }
    Write-Ok "已复制前端产物: $DistDir -> $StaticLink"
} else {
    Write-Fail "前端 dist 不存在（$DistDir），请先构建前端（勿使用 -SkipWeb）"
    exit 1
}

Launch-Service -DistPath $DistDir
