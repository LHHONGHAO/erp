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
  HTTPS 服务端口，默认 9999（HTTP 固定 9998，用于证书安装与兼容访问）
.PARAMETER DbUser
  数据库账号；不传则依次读取环境变量 SPRING_DATASOURCE_USERNAME、deploy.local.json 的 dbUser
.PARAMETER DbPassword
  数据库口令；不传则依次读取环境变量 SPRING_DATASOURCE_PASSWORD、deploy.local.json 的 dbPassword
.PARAMETER UpgradeSql
  可选的数据库升级 SQL 文件（如 docs\category_unit_upgrade.sql），启动前自动执行
.EXAMPLE
  .\deploy-local.ps1                # 完整部署
  .\deploy-local.ps1 -SkipBoot      # 只更新前端
  .\deploy-local.ps1 -SkipWeb       # 只更新后端
  .\deploy-local.ps1 -StartOnly     # 只启动（不构建）
  .\deploy-local.ps1 -StopOnly      # 只停止
  .\deploy-local.ps1 -UpgradeSql .\jshERP-boot\docs\category_unit_upgrade.sql
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
    [int]$Port        = 9999,
    [string]$DbUser   = '',
    [string]$DbPassword = '',
    [string]$UpgradeSql = ''
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
# HTTP 端口固定 9998（仅用于证书安装与兼容访问），HTTPS 为 $Port
$HttpPort = 9998
# application.yml 中配置的外部静态资源目录（相对 jshERP-boot 启动目录）
$StaticLink = Join-Path $BootDir 'frontend-dist'
# 应用数据与数据库备份分开存放，重新发布程序时不触碰这两个目录
$DeployConfig = Get-Content (Join-Path $Root 'deploy.config.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$DataDir = [string]$DeployConfig.dataDir
$BackupDir = [string]$DeployConfig.backupDir
if (-not $DataDir -or -not [IO.Path]::IsPathRooted($DataDir)) { throw 'deploy.config.json 的 dataDir 必须是绝对路径' }
if (-not $BackupDir -or -not [IO.Path]::IsPathRooted($BackupDir)) { throw 'deploy.config.json 的 backupDir 必须是绝对路径' }
$DataDirFull = [IO.Path]::GetFullPath($DataDir).TrimEnd('\', '/')
$BackupDirFull = [IO.Path]::GetFullPath($BackupDir).TrimEnd('\', '/')
if (($DataDirFull -ieq $BackupDirFull) -or
    $DataDirFull.StartsWith($BackupDirFull + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    $BackupDirFull.StartsWith($DataDirFull + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'dataDir 和 backupDir 必须是互相独立的目录'
}
$DataDirs = @('upload', 'export', 'config', 'logs')

# 本机私有配置（不入库）：数据库账号口令等，避免写入仓库
$localCfgPath = Join-Path $Root 'deploy.local.json'
$localCfg = @{}
if (Test-Path $localCfgPath) {
    try {
        (Get-Content $localCfgPath -Raw -Encoding UTF8 | ConvertFrom-Json).PSObject.Properties |
            ForEach-Object { $localCfg[$_.Name] = $_.Value }
    } catch {
        Write-Warning "deploy.local.json 解析失败，已忽略: $_"
    }
}
if (-not $DbUser) {
    if ($env:SPRING_DATASOURCE_USERNAME) { $DbUser = $env:SPRING_DATASOURCE_USERNAME }
    elseif ($localCfg['dbUser']) { $DbUser = [string]$localCfg['dbUser'] }
}
if (-not $DbPassword) {
    if ($env:SPRING_DATASOURCE_PASSWORD) { $DbPassword = $env:SPRING_DATASOURCE_PASSWORD }
    elseif ($localCfg['dbPassword']) { $DbPassword = [string]$localCfg['dbPassword'] }
}
$script:DbUser = $DbUser
$script:DbPassword = $DbPassword

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  JSH_ERP 本地部署脚本" -ForegroundColor Cyan
Write-Host "  项目根: $Root" -ForegroundColor DarkGray
Write-Host "  HTTPS : $Port（HTTP: $HttpPort）" -ForegroundColor DarkGray
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
    # 旧版本把数据放在仓库根目录 data/；仅当新目录为空时复制，并保留旧目录作为回退副本。
    $legacyDataDir = Join-Path $Root 'data'
    $targetItems = @(Get-ChildItem -LiteralPath $DataDir -Force -ErrorAction SilentlyContinue)
    if ((Test-Path $legacyDataDir) -and ($legacyDataDir -ne $DataDir) -and ($targetItems.Count -eq 0)) {
        $legacyItems = @(Get-ChildItem -LiteralPath $legacyDataDir -Force)
        foreach ($item in $legacyItems) {
            Copy-Item -LiteralPath $item.FullName -Destination $DataDir -Recurse -Force
        }
        if ($legacyItems.Count -gt 0) { Write-Ok "旧数据已复制到新目录，原目录保留: $legacyDataDir" }
    }
    foreach ($sub in $DataDirs) {
        $p = Join-Path $DataDir $sub
        if (-not (Test-Path $p)) { New-Item -ItemType Directory -Path $p -Force | Out-Null }
    }
    New-Item -ItemType Directory -Path (Join-Path $BackupDir 'mysql') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $BackupDir 'files') -Force | Out-Null
    Write-Ok "数据库与文件备份目录就绪: $BackupDir"
    Write-Ok "子目录就绪: $($DataDirs -join ', ')"
    # 首次部署：把项目内的 OCR 等配置迁移到统一数据目录（之后更新部署不会覆盖）
    $srcCfg = Join-Path $BootDir 'config\ocr-config.properties'
    $dstCfg = Join-Path $DataDir 'config\ocr-config.properties'
    if ((Test-Path $srcCfg) -and (-not (Test-Path $dstCfg))) {
        Copy-Item $srcCfg $dstCfg -Force
        Write-Ok "已迁移 ocr-config.properties 到数据目录"
    }
    # HTTPS 证书：不存在时自动生成根证书 + 服务端证书，客户端安装一次根证书即可长期信任
    $httpsP12 = Join-Path $DataDir 'config\jsh-https.p12'
    if (Test-Path $httpsP12) {
        Write-Ok "HTTPS 证书已存在"
    } else {
        $certScript = Join-Path $Root 'https-cert.ps1'
        if (Test-Path $certScript) {
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $certScript -DataDir $DataDir
            if (Test-Path $httpsP12) { Write-Ok "HTTPS 证书已生成" }
            else { Write-Warn "HTTPS 证书生成失败，HTTPS 连接器将不可用" }
        } else {
            Write-Warn "未找到 https-cert.ps1，跳过 HTTPS 证书生成"
        }
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

function Test-TcpPort {
    param([string]$TargetHost, [int]$TargetPort, [int]$TimeoutMs = 1500)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($TargetHost, $TargetPort, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne($TimeoutMs)) { return $false }
        $client.EndConnect($iar)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Assert-Dependencies {
    Write-Step "检查依赖服务（MySQL / Redis）"
    if (-not (Test-TcpPort '127.0.0.1' 3306)) {
        Write-Fail "MySQL 127.0.0.1:3306 未监听，请先启动 MySQL 服务（如 MySQL84）"
        exit 1
    }
    Write-Ok "MySQL 3306 正常"
    if (-not (Test-TcpPort '127.0.0.1' 6379)) {
        Write-Fail "Redis 127.0.0.1:6379 未监听，请先启动 Redis 服务"
        exit 1
    }
    Write-Ok "Redis 6379 正常"
    if (-not $script:DbUser) {
        Write-Warn "未提供数据库账号（-DbUser / SPRING_DATASOURCE_USERNAME / deploy.local.json），外部配置口令不一致时启动会失败"
    }
}

function Find-MysqlClient {
    $found = Get-ChildItem -Path "$env:ProgramFiles\MySQL" -Recurse -Filter 'mysql.exe' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like '*\bin\mysql.exe' } |
        Select-Object -First 1 -ExpandProperty FullName
    return $found
}

function Invoke-DbUpgrade([string]$SqlFile) {
    $SqlFile = (Resolve-Path $SqlFile -ErrorAction Stop).Path
    if (-not $script:DbUser) {
        Write-Fail "数据库升级需要账号口令：请传 -DbUser/-DbPassword，或配置 deploy.local.json"
        exit 1
    }
    $mysql = Find-MysqlClient
    if (-not $mysql) {
        Write-Fail "未找到 mysql.exe，无法执行数据库升级"
        exit 1
    }
    Write-Step "执行数据库升级 $SqlFile"
    $env:MYSQL_PWD = $script:DbPassword
    try {
        $sql = Get-Content $SqlFile -Raw -Encoding UTF8
        $out = & $mysql '--host=127.0.0.1' "--user=$($script:DbUser)" '--default-character-set=utf8mb4' '--database=jsh_erp' '-e' $sql 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw ($out | Out-String)
        }
        if ($out) { Write-Host "    $($out | Out-String)".TrimEnd() -ForegroundColor DarkGray }
        Write-Ok "数据库升级完成"
    } finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

function Write-StartScript {
    # 生成/更新开机自启脚本（含本机数据库口令，放在数据目录同级，不入仓库）
    if (-not $script:DbUser) { return }
    $parent = Split-Path -Parent $DataDir
    if (-not $parent) { return }
    $cmdPath = Join-Path $parent 'start-jsh-erp.cmd'
    $staticDir = ($StaticLink -replace '\\', '/')
    $dataDirArg = ($DataDir -replace '\\', '/')
    $logDir = Join-Path $DataDir 'logs'
    $lines = @(
        '@echo off',
        'chcp 65001 >nul',
        'title JSH_ERP 服务',
        "set SPRING_DATASOURCE_USERNAME=$($script:DbUser)",
        "set SPRING_DATASOURCE_PASSWORD=$($script:DbPassword)",
        'set SPRING_REDIS_HOST=127.0.0.1',
        'set SPRING_REDIS_PORT=6379',
        "if not exist `"$logDir`" mkdir `"$logDir`"",
        "cd /d `"$BootDir`"",
        "`"$javaExe`" -Xms256m -Xmx1024m -Dspring.profiles.active=dev -Dfile.encoding=UTF-8 `"-Dspring.resources.static-locations=file:$staticDir/`" `"-Djsh.data.dir=$dataDirArg`" `"-Dlogs.home=$logDirArg`" -jar `"$Jar`" >> `"$(Join-Path $logDir 'app-console.log')`" 2>&1"
    )
    [IO.File]::WriteAllText($cmdPath, ($lines -join "`r`n") + "`r`n", (New-Object Text.UTF8Encoding($false)))
    Write-Ok "开机启动脚本已更新: $cmdPath"
}

function Show-StartupFailure {
    # 启动失败时输出日志尾部，避免只看到“端口未监听”
    $logDir = Join-Path $DataDir 'logs'
    $candidates = @(
        (Join-Path $logDir 'jshERP.log'),
        (Join-Path $BootDir 'deploy-backend.log'),
        (Join-Path $logDir 'app-console.log')
    )
    foreach ($f in $candidates) {
        if (Test-Path $f) {
            Write-Host "    ---- $f (最后 30 行) ----" -ForegroundColor DarkGray
            Get-Content $f -Tail 30 -ErrorAction SilentlyContinue | ForEach-Object {
                Write-Host "    $_" -ForegroundColor DarkGray
            }
            break
        }
    }
}

function Find-Java {
    param([string]$Hint)
    $candidates = @()
    if ($Hint) { $candidates += $Hint }
    $candidates += @(
        "$env:USERPROFILE\.jdks\jdk8u492-b09",
        "C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot",
        "$env:ProgramFiles\Eclipse Adoptium\jdk8",
        "C:\Program Files\Eclipse Adoptium\jdk8",
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

function Install-Jdk8 {
    # 本机未安装 JDK 时，自动使用项目根目录随附的 OpenJDK8 安装包静默安装
    $msi = Get-ChildItem -Path $Root -Filter 'OpenJDK8U-jdk_x64_windows_hotspot_*.msi' -File -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $msi) { return $false }
    Write-Step "未检测到 JDK8，自动安装 $($msi.Name)"
    $installDir = "$env:ProgramFiles\Eclipse Adoptium\jdk8"
    $logPath = Join-Path $Root 'jdk8-install.log'
    $p = Start-Process msiexec.exe -ArgumentList @(
        '/i', "`"$($msi.FullName)`"", '/qn',
        'ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome',
        "INSTALLDIR=`"$installDir`"", '/l*v', "`"$logPath`""
    ) -Wait -PassThru
    if ($p.ExitCode -ne 0) {
        Write-Fail "JDK 安装失败（退出码 $($p.ExitCode)），详见 $logPath"
        return $false
    }
    Write-Ok "JDK 已安装到 $installDir"
    return (Test-Path (Join-Path $installDir 'bin\java.exe'))
}

function Find-Maven {
    param([string]$Hint)
    if ($Hint -and (Test-Path $Hint)) { return $Hint }
    $candidates = @()
    if ($env:MAVEN_HOME) { $candidates += "$env:MAVEN_HOME\bin\mvn.cmd" }
    $candidates += @(
        "$Root\tools\apache-maven-3.6.3\bin\mvn.cmd",
        "$Root\tools\apache-maven-*\bin\mvn.cmd",
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
    $logDir = Join-Path $DataDir 'logs'
    $logDirArg = ($logDir -replace '\\', '/')
    $argList = @(
        '-Xms256m', '-Xmx1024m',
        '-Dspring.profiles.active=dev',
        '-Dfile.encoding=UTF-8',
        "-Dspring.resources.static-locations=file:$staticDir",
        "-Djsh.data.dir=$dataDirArg",
        "-Dlogs.home=$logDirArg",
        '-jar', $Jar
    )
    # 同步开机自启脚本（含本机数据库口令，位于数据目录同级，不入仓库）
    Write-StartScript
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
        if ($proc.HasExited) { Write-Warn "Java 进程已退出，退出码 $($proc.ExitCode)" }
        Show-StartupFailure
        Write-Host "    请检查日志: $Log" -ForegroundColor DarkGray
        exit 1
    } else {
        # HTTP 健康检查（HTTP 连接器固定 9998）
        try {
            $resp = Invoke-WebRequest -Uri "http://127.0.0.1:$HttpPort/user/randomImage" -UseBasicParsing -TimeoutSec 15
            Write-Ok "HTTP 健康检查通过（$($resp.StatusCode)）"
        } catch {
            Write-Warn "HTTP 健康检查失败: $_"
        }
    }

    # 取默认路由所在网卡的 IPv4，避免误取 WSL/Hyper-V 虚拟网卡地址
    $lanIp = (Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
        Sort-Object RouteMetric |
        ForEach-Object {
            Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $_.InterfaceIndex -ErrorAction SilentlyContinue |
                Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
                Select-Object -First 1 -ExpandProperty IPAddress
        } | Select-Object -First 1)

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "  部署完成" -ForegroundColor Green
    Write-Host "------------------------------------------------------------"
    Write-Host "  本机访问 : https://localhost:$Port"
    if ($lanIp) {
        Write-Host "  局域网   : https://${lanIp}:${Port}"
        Write-Host "  HTTP     : http://${lanIp}:${HttpPort}（证书安装/兼容）"
        Write-Host "  证书安装 : http://${lanIp}:${HttpPort}/cert"
    }
    Write-Host "  后端日志 : $(Join-Path $DataDir 'logs\jshERP.log')"
    Write-Host "  前端产物 : $StaticLink -> $DistPath"
    Write-Host "  停止命令 : .\deploy-local.ps1 -StopOnly"
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

# 自动探测工具链（未安装 JDK 时尝试用随附 msi 静默安装）
try {
    $env:JAVA_HOME = Find-Java -Hint $JavaHome
} catch {
    if (Install-Jdk8) {
        $env:JAVA_HOME = Find-Java -Hint $JavaHome
    } else {
        Write-Fail $_.Exception.Message
        exit 1
    }
}
$env:Path     = "$env:JAVA_HOME\bin;$env:Path"
$javaExe      = Join-Path $env:JAVA_HOME 'bin\java.exe'
Write-Ok "JDK  : $env:JAVA_HOME"

# 数据库凭据通过环境变量注入，优先级高于 jshERP-boot/application-dev.yml
if ($script:DbUser) {
    $env:SPRING_DATASOURCE_USERNAME = $script:DbUser
    Write-Ok "DB账号: $script:DbUser"
} else {
    Write-Warn "未提供数据库账号，将使用 application-dev.yml 中的配置"
}
if ($script:DbPassword) { $env:SPRING_DATASOURCE_PASSWORD = $script:DbPassword }

$mvn = Find-Maven -Hint $MvnPath
Write-Ok "Maven: $mvn"

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    # 回退到项目内置 Node（tools\node16-build），避免本机未配置全局 Node
    $bundledNode = Join-Path $Root 'tools\node16-build'
    if (Test-Path (Join-Path $bundledNode 'node.exe')) {
        $env:Path = "$bundledNode;$env:Path"
        Write-Warn "未找到全局 Node，使用内置 Node: $bundledNode"
    } else {
        Write-Fail "未找到 node.exe，请先安装 Node.js"
        exit 1
    }
}
$nodeExe = (Get-Command node -ErrorAction Stop).Source
Write-Ok "Node : $(node -v)"

# npm / git 也解析为完整路径，避免 ProcessStartInfo 在不同 WorkDir 下找错位置
$npmCmd = (Get-Command npm.cmd -ErrorAction Stop).Source
$gitCmd = (Get-Command git.exe -ErrorAction Stop).Source
Write-Ok "npm  : $npmCmd"
Write-Ok "git  : $gitCmd"

# ---------- 依赖检查与数据库升级 ----------
Assert-Dependencies
if ($UpgradeSql) {
    Invoke-DbUpgrade $UpgradeSql
}

# ---------- 停止旧服务 ----------
Stop-OldService $Port
Stop-OldService $HttpPort

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
    $nodeMajor = [int](((node -v) -replace '^v', '').Split('.')[0])
    $env:NODE_OPTIONS = '--max-old-space-size=4096'
    if ($nodeMajor -ge 17) { $env:NODE_OPTIONS = '--openssl-legacy-provider ' + $env:NODE_OPTIONS }
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
