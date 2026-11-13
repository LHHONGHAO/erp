<#
.SYNOPSIS
  JSH_ERP Windows 客户端一键部署脚本：连远程中心库/Redis/OSS，配置挂远程 SMB
.DESCRIPTION
  客户端（销售机）只跑 app + 前端，不建库：
    - 数据库 / Redis 连中心主机（默认 www.innas.xyz，建议用 VPN 内网 IP）
    - 图片走 OSS（file.uploadType=2，OSS 参数在中心库 platform_config 表中）
    - 配置文件（ocr-config.properties、jsh-https.p12）可读写挂载中心 SMB 共享
    - logs / export 等临时数据落在本地数据目录
.PARAMETER Server
  中心主机（数据库/Redis/SMB 所在），建议填 VPN 内网 IP，默认 www.innas.xyz
.PARAMETER DbPort / RedisPort
  数据库 / Redis 端口
.PARAMETER DbName / DbUser / DbPassword
  数据库名 / 账号 / 密码
.PARAMETER RedisPassword
  Redis 访问密码（中心 Redis requirepass）
.PARAMETER ConfigShare / SmbUser / SmbPassword
  SMB 共享名 / 用户名 / 密码（中心 Samba 可读写共享）
.PARAMETER DataDir
  客户端本地数据目录（logs/export 落本地，config 挂载远程共享）
.PARAMETER Port
  客户端本地 HTTPS 端口，默认 9999（HTTP 固定 9998）
.PARAMETER NoMountConfig
  不挂载远程 SMB，改用本地 config 目录（离线/无 SMB 时使用）
.EXAMPLE
  .\deploy-client-windows.ps1 -DbPassword xxx -RedisPassword yyy -SmbPassword zzz
  .\deploy-client-windows.ps1 -Server 10.0.0.10 -SkipBuild
#>
param(
    [string]$Server       = 'www.innas.xyz',
    [int]   $DbPort       = 3306,
    [int]   $RedisPort    = 6379,
    [string]$DbName       = 'jsh_erp',
    [string]$DbUser       = 'jsherp',
    [string]$DbPassword   = '',
    [string]$RedisPassword = '',
    [string]$ConfigShare  = 'jsherp-config',
    [string]$SmbUser      = 'jsherp',
    [string]$SmbPassword  = '',
    [string]$DataDir      = 'D:\jshERP\data',
    [int]   $Port         = 9999,
    [switch]$SkipWeb,
    [switch]$SkipBoot,
    [switch]$SkipBuild,
    [switch]$StartOnly,
    [switch]$StopOnly,
    [switch]$NoMountConfig,
    [string]$JavaHome = '',
    [string]$MvnPath  = ''
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8; $OutputEncoding = [Text.Encoding]::UTF8 } catch {}

# 凭据优先参数，其次环境变量（不入库）
if (-not $DbPassword)    { $DbPassword    = $env:JSH_DB_PASSWORD }
if (-not $RedisPassword) { $RedisPassword = $env:JSH_REDIS_PASSWORD }
if (-not $SmbPassword)   { $SmbPassword   = $env:JSH_SMB_PASSWORD }

# ============================================================
# 路径常量
# ============================================================
$Root    = Split-Path -Parent $MyInvocation.MyCommand.Path
$BootDir = Join-Path $Root 'jshERP-boot'
$WebDir  = Join-Path $Root 'jshERP-web'
$Jar     = Join-Path $BootDir 'target\jshERP.jar'
$StaticLink = Join-Path $BootDir 'frontend-dist'
$HttpPort   = 9998

$DataDirFull = [IO.Path]::GetFullPath($DataDir).TrimEnd('\', '/')

function Write-Step([string]$Msg) { Write-Host ""; Write-Host "==> $Msg" -ForegroundColor Yellow }
function Write-Ok([string]$Msg)   { Write-Host "    [OK] $Msg" -ForegroundColor Green }
function Write-Warn([string]$Msg) { Write-Host "    [WARN] $Msg" -ForegroundColor DarkYellow }
function Write-Fail([string]$Msg) { Write-Host "    [FAIL] $Msg" -ForegroundColor Red }

function Find-Java {
    param([string]$Hint)
    $candidates = @()
    if ($Hint) { $candidates += $Hint }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += @(
        "$env:USERPROFILE\.jdks\jdk8u492-b09",
        "C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot",
        "C:\Program Files\Java\jdk1.8.0_*",
        "$env:ProgramFiles\Java\jdk1.8.0_*"
    )
    foreach ($c in $candidates) {
        $resolved = Resolve-Path $c -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Path
        if ($resolved -and (Test-Path (Join-Path $resolved 'bin\java.exe'))) { return $resolved }
    }
    if (Get-Command java -ErrorAction SilentlyContinue) {
        $p = Split-Path (Get-Command java).Source -Parent
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
    param([string]$File, [string[]]$CmdArgs, [string]$WorkDir = $null, [string]$Desc = '')
    Write-Host "    ${File} $($CmdArgs -join ' ')" -ForegroundColor DarkGray
    $oldPwd = $null
    if ($WorkDir) { $oldPwd = Get-Location; Set-Location $WorkDir }
    try {
        $p = Start-Process -FilePath $File -ArgumentList $CmdArgs -NoNewWindow -Wait -PassThru
        if ($p.ExitCode -ne 0) { throw "$Desc 退出码 $($p.ExitCode)" }
    } finally { if ($oldPwd) { Set-Location $oldPwd } }
}

function Stop-OldService([int]$ListenPort) {
    $conn = Get-NetTCPConnection -LocalPort $ListenPort -State Listen -ErrorAction SilentlyContinue
    if (-not $conn) { Write-Ok "端口 $ListenPort 未被占用"; return }
    foreach ($c in $conn) {
        try { Stop-Process -Id $c.OwningProcess -Force -ErrorAction Stop; Write-Ok "已终止 PID $($c.OwningProcess)" }
        catch { Write-Warn "终止 PID $($c.OwningProcess) 失败: $_" }
    }
    Start-Sleep -Milliseconds 500
}

# ============================================================
# 数据目录与配置挂载
# ============================================================
function Initialize-DataDir {
    Write-Step "初始化本地数据目录 $DataDirFull"
    New-Item -ItemType Directory -Path $DataDirFull -Force | Out-Null
    foreach ($sub in @('upload', 'export', 'logs')) {
        New-Item -ItemType Directory -Path (Join-Path $DataDirFull $sub) -Force | Out-Null
    }
    Write-Ok "upload / export / logs 就绪（本地）"

    $configDir = Join-Path $DataDirFull 'config'
    if ($NoMountConfig) {
        New-Item -ItemType Directory -Path $configDir -Force | Out-Null
        Write-Warn "已跳过 SMB 挂载，使用本地 config 目录 $configDir"
        Ensure-LocalCert $configDir
        return
    }

    Write-Step "挂载中心配置共享 \\$Server\$ConfigShare -> $configDir"
    # 1) 映射 SMB 到盘符（可读写）
    $drive = 'Z:'
    $null = net use $drive /delete /y 2>$null
    $null = net use $drive "\\$Server\$ConfigShare" /user:$SmbUser $SmbPassword /persistent:no 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "SMB 映射失败，回退为从共享复制一份只读快照到本地 config"
        Fallback-CopyConfig $configDir
        return
    }
    Write-Ok "SMB 已映射到 $drive"

    # 2) 把 config 子目录做成指向共享的目录符号链接（需要管理员权限或开发者模式）
    if (Test-Path $configDir) {
        $item = Get-Item $configDir -Force -ErrorAction SilentlyContinue
        if ($item -and ($item.Attributes -match 'ReparsePoint')) { $item.Delete() }
        else { Remove-Item $configDir -Recurse -Force -ErrorAction SilentlyContinue }
    }
    $null = cmd /c mklink /D "`"$configDir`"" "`"$drive`\`"" 2>&1
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $configDir)) {
        Write-Warn "目录符号链接创建失败（可能缺少管理员权限/开发者模式），回退为复制快照"
        Fallback-CopyConfig $configDir
        return
    }
    Write-Ok "config 已链接到中心共享（可读写）"
}

function Ensure-LocalCert([string]$ConfigDir) {
    $httpsP12 = Join-Path $ConfigDir 'jsh-https.p12'
    if (Test-Path $httpsP12) { return }
    $certScript = Join-Path $Root 'https-cert.ps1'
    if (Test-Path $certScript) {
        Write-Warn "未发现 jsh-https.p12，尝试本地生成（请安装根证书到客户端）"
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $certScript -DataDir $DataDirFull
    }
}

function Fallback-CopyConfig([string]$ConfigDir) {
    New-Item -ItemType Directory -Path $ConfigDir -Force | Out-Null
    $src = "\\$Server\$ConfigShare"
    try {
        # 先尝试直接复制，若尚未映射盘符则再次映射后复制
        Copy-Item -Path (Join-Path $src '*') -Destination $ConfigDir -Recurse -Force -ErrorAction Stop
        Write-Ok "已从共享复制配置快照到本地 config"
    } catch {
        Write-Warn "复制共享失败，config 目录为空，OCR 配置与 HTTPS 证书需手动补齐"
    }
    Ensure-LocalCert $ConfigDir
}

function Launch-Service {
    param([string]$DistPath)

    Initialize-DataDir

    Write-Step "配置远程连接参数"
    # 数据库 / Redis 通过环境变量注入（与 application.yml 的占位符对应）
    $dbUrl = "jdbc:mysql://${Server}:${DbPort}/${DbName}?useUnicode=true&characterEncoding=utf8&useCursorFetch=true&defaultFetchSize=500&allowMultiQueries=true&rewriteBatchedStatements=true&useSSL=false&allowPublicKeyRetrieval=true"
    $env:SPRING_DATASOURCE_URL      = $dbUrl
    $env:SPRING_DATASOURCE_USERNAME = $DbUser
    $env:SPRING_DATASOURCE_PASSWORD = $DbPassword
    $env:SPRING_REDIS_HOST          = $Server
    $env:SPRING_REDIS_PORT          = "$RedisPort"
    if ($RedisPassword) { $env:SPRING_REDIS_PASSWORD = $RedisPassword }
    Write-Ok "DB: ${Server}:${DbPort}/${DbName}"
    Write-Ok "Redis: ${Server}:${RedisPort}"
    Write-Ok "图片: OSS（file.uploadType=2）"

    Write-Step "启动后端服务"
    $staticDir  = ($StaticLink -replace '\\', '/') + '/'
    $dataDirArg = ($DataDirFull -replace '\\', '/')
    $argList = @(
        '-Xms256m', '-Xmx1024m',
        '-Dfile.encoding=UTF-8',
        "-Dspring.resources.static-locations=file:$staticDir",
        "-Djsh.data.dir=$dataDirArg",
        '-Dfile.uploadType=2',   # OSS 存储（参数在中心库 platform_config）
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
        if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { break }
        Write-Host "      ${waited}s ..." -ForegroundColor DarkGray
    }
    if (-not (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)) {
        Write-Warn "$($waited)s 内未监听到 $Port 端口，请检查日志"
    }

    $lanIp = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
        Select-Object -First 1 -ExpandProperty IPAddress)

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "  客户端部署完成" -ForegroundColor Green
    Write-Host "------------------------------------------------------------"
    Write-Host "  本机访问 : https://localhost:$Port"
    if ($lanIp) { Write-Host "  局域网   : https://${lanIp}:${Port}" }
    Write-Host "  中心数据库: ${Server}:${DbPort}/${DbName}"
    Write-Host "  中心 Redis : ${Server}:${RedisPort}"
    Write-Host "  图片存储  : OSS"
    Write-Host "  停止命令  : .\deploy-client-windows.ps1 -StopOnly"
    Write-Host "============================================================" -ForegroundColor Green
}

# ============================================================
# 模式分支
# ============================================================
if ($StopOnly) {
    Stop-OldService $Port
    Stop-OldService $HttpPort
    Write-Host "`n已停止。" -ForegroundColor Green
    exit 0
}

$env:JAVA_HOME = Find-Java -Hint $JavaHome
$env:Path     = "$env:JAVA_HOME\bin;$env:Path"
$javaExe      = Join-Path $env:JAVA_HOME 'bin\java.exe'
Write-Ok "JDK  : $env:JAVA_HOME"

$mvn = Find-Maven -Hint $MvnPath
Write-Ok "Maven: $mvn"

if (-not (Get-Command node -ErrorAction SilentlyContinue)) { Write-Fail "未找到 node.exe"; exit 1 }
$nodeExe = (Get-Command node -ErrorAction Stop).Source
$npmCmd  = (Get-Command npm.cmd -ErrorAction Stop).Source
$gitCmd  = (Get-Command git.exe -ErrorAction Stop).Source
Write-Ok "Node : $(node -v)"

Stop-OldService $Port
Stop-OldService $HttpPort

if ($StartOnly) {
    if (-not (Test-Path $Jar)) { Write-Fail "找不到 $Jar，请先完整部署"; exit 1 }
    if (-not (Test-Path $StaticLink)) {
        $dist = Join-Path $WebDir 'dist'
        if (Test-Path $dist) { Copy-Item -Path $dist -Destination $StaticLink -Recurse -Force }
    }
    Launch-Service -DistPath (Join-Path $WebDir 'dist')
    exit 0
}

# 拉代码（可选）
$null = git -C $Root pull --ff-only 2>&1

# 前端构建
if (-not $SkipBuild -and -not $SkipWeb) {
    Write-Step "构建前端"
    if (-not (Test-Path (Join-Path $WebDir 'node_modules'))) {
        Invoke-External -File $npmCmd -CmdArgs @('install') -WorkDir $WebDir -Desc 'npm install'
    }
    $env:NODE_OPTIONS = '--openssl-legacy-provider --max-old-space-size=4096'
    Invoke-External -File $npmCmd -CmdArgs @('run','build') -WorkDir $WebDir -Desc 'npm run build'
    Write-Ok "前端构建完成 -> $(Join-Path $WebDir 'dist')"
}

# 后端打包
if (-not $SkipBuild -and -not $SkipBoot) {
    Write-Step "打包后端"
    try {
        Invoke-External -File $mvn -CmdArgs @('-o','-DskipTests','clean','package') -WorkDir $BootDir -Desc 'mvn offline package'
    } catch {
        Write-Warn "离线打包失败，改为联网打包"
        Invoke-External -File $mvn -CmdArgs @('-DskipTests','clean','package') -WorkDir $BootDir -Desc 'mvn online package'
    }
    if (-not (Test-Path $Jar)) { Write-Fail "未生成 $Jar"; exit 1 }
    Write-Ok "后端打包完成 -> $Jar"
}

# 前端产物同步
Write-Step "同步前端静态资源"
$dist = Join-Path $WebDir 'dist'
if (Test-Path $dist) {
    if (Test-Path $StaticLink) {
        $item = Get-Item $StaticLink -Force
        if ($item.Attributes -match 'ReparsePoint') { $item.Delete() } else { Remove-Item $StaticLink -Recurse -Force }
    }
    Copy-Item -Path $dist -Destination $StaticLink -Recurse -Force
    Write-Ok "已复制前端产物: $dist -> $StaticLink"
} else {
    Write-Warn "前端 dist 不存在，跳过同步"
}

Launch-Service -DistPath $dist
