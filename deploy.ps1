<#
.SYNOPSIS
  jshERP 一键部署脚本：打包 -> 检查持久化卷 -> 备份数据库 -> 上传 -> 更新应用 -> 验证
.DESCRIPTION
  在本仓库根目录执行。默认只部署后端；前端有改动时加 -Frontend。
  远端使用 Docker Compose（mysql + redis + app + nginx）；部署只重建 app / nginx，不重启数据库和 Redis。
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
    [string]$RemoteDir = '/root/jsherp',
    [string]$HostKey  = 'SHA256:R2PbBdYWF37HyDs9OpcQrxw12YQUanbsS4s5gG8MxZo',
    [string]$SshPassword = ''
)

$ErrorActionPreference = 'Stop'
# 中文输出统一 UTF-8，避免控制台乱码
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8; $OutputEncoding = [Text.Encoding]::UTF8 } catch {}
$root      = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolsDir  = Join-Path $root 'tools'
$deploymentDir = Join-Path $root 'deploy'
$plink     = Join-Path $toolsDir 'plink.exe'
$pscp      = Join-Path $toolsDir 'pscp.exe'
$mavenHome = Join-Path $toolsDir 'apache-maven-3.6.3'
$jarPath   = Join-Path $root 'jshERP-boot\target\jshERP.jar'
$webDir    = Join-Path $root 'jshERP-web'
# SSH 密码不写入脚本/仓库：优先环境变量，其次本地不入库的 tools\ssh\password
$sshDir    = Join-Path $toolsDir 'ssh'
if (-not $SshPassword) { $SshPassword = $env:JSH_SSH_PASSWORD }
if (-not $SshPassword -and (Test-Path (Join-Path $sshDir 'password'))) {
    $SshPassword = (Get-Content (Join-Path $sshDir 'password') -Raw).Trim()
}
function Assert-SshPassword {
    if (-not $SshPassword) {
        throw '缺少 SSH 凭据：请设置环境变量 JSH_SSH_PASSWORD，或创建 tools\ssh\password 文件（不入库）。'
    }
}

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
    # Java 的 user.home 在此部署环境指向 C:\，优先显式使用当前 Windows 用户已有的 Maven 缓存。
    $localMavenRepo = if ($env:USERPROFILE) { Join-Path $env:USERPROFILE '.m2\repository' } else { $null }
    $mavenArgs = if ($localMavenRepo -and (Test-Path $localMavenRepo)) { @("-Dmaven.repo.local=$localMavenRepo") + $Goals } else { $Goals }
    Push-Location $WorkDir
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $mvn @mavenArgs 2>&1 | Select-Object -Last 5
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
        Pop-Location
    }
    if ($code -ne 0) { throw "maven 执行失败: $Goals" }
}
function Invoke-Plink([string]$RemoteCmd) {
    Assert-SshPassword
    & $plink -ssh "$SshUser@$Server" -pw $SshPassword -batch -hostkey $HostKey $RemoteCmd
    if ($LASTEXITCODE -ne 0) { throw "plink 执行失败: $RemoteCmd" }
}
function Invoke-Pscp([string[]]$Local, [string]$Remote) {
    Assert-SshPassword
    & $pscp -pw $SshPassword -batch -hostkey $HostKey @Local "$SshUser@$Server`:$Remote"
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
Write-Host '==> 检查远端部署凭据 ...'
Invoke-Plink "test -f '$RemoteDir/.env' || { echo '缺少远端 $RemoteDir/.env；请先按 .env.example 配置数据库密码。' >&2; exit 1; }"
Write-Host '==> 检查数据库、应用和备份均使用外置 bind mount ...'
Invoke-Plink 'docker inspect -f ''{{range .Mounts}}{{.Destination}}|{{.Type}}{{println}}{{end}}'' jsherp-mysql | grep -Fqx ''/var/lib/mysql|bind'''
Invoke-Plink 'docker inspect -f ''{{range .Mounts}}{{.Destination}}|{{.Type}}{{println}}{{end}}'' jsherp-mysql | grep -Fqx ''/backup|bind'''
Invoke-Plink 'docker inspect -f ''{{range .Mounts}}{{.Destination}}|{{.Type}}{{println}}{{end}}'' jsherp-app | grep -Fqx ''/data|bind'''
Invoke-Plink 'test -d /srv/jsherp-backup/mysql || { echo "外置备份目录未就绪；停止部署以保护数据。" >&2; exit 1; }'
Write-Host '==> 创建部署前 MySQL 一致性备份 ...'
$backupName = 'jsh_erp-predeploy-{0}.sql' -f (Get-Date -Format 'yyyyMMdd-HHmmss')
$backupCommand = 'docker exec jsherp-mysql sh -c ''MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --single-transaction --routines --triggers --default-character-set=utf8mb4 -uroot jsh_erp > /backup/' + $backupName + ''' && test -s /srv/jsherp-backup/mysql/' + $backupName + ' && ls -lh /srv/jsherp-backup/mysql/' + $backupName
Invoke-Plink $backupCommand
Write-Host '==> 上传 jar ...'
Invoke-Pscp @($jarPath) "$RemoteDir/jshERP.jar"
if ($Frontend) {
    Write-Host '==> 上传前端 dist ...'
    Invoke-Pscp @('-r', (Join-Path $webDir 'dist')) "$RemoteDir/"
}
# 容器编排与 nginx 配置一并同步，避免端口约定变更后远端仍用旧配置
Write-Host '==> 上传 compose / nginx 配置 ...'
Invoke-Pscp @((Join-Path $deploymentDir 'docker-compose.yml'), (Join-Path $deploymentDir 'nginx.conf'), (Join-Path $deploymentDir '.env.example')) "$RemoteDir/"
# HTTPS 证书（存在时同步，避免更换证书后远端仍使用旧文件）
$certDir = Join-Path $deploymentDir 'config'
if (-not (Test-Path $certDir)) { $certDir = Join-Path $toolsDir 'deploy\config' }
if (Test-Path $certDir) {
    Write-Host '==> 上传 HTTPS 证书 ...'
    Invoke-Pscp @('-r', $certDir) "$RemoteDir/"
}

# 3. 重启并验证
Write-Host '==> 重建远端 app / nginx 容器 ...'
Invoke-Plink "cd $RemoteDir && docker compose up -d --no-deps --force-recreate app nginx 2>&1 | tail -2"
Write-Host '==> 等待服务启动 (50s) ...'
Start-Sleep 50
Invoke-Plink 'curl --fail --silent --show-error --output /dev/null http://127.0.0.1:9998/user/randomImage'
Invoke-Plink 'curl --fail --silent --show-error --insecure --output /dev/null https://127.0.0.1:9999/'
Write-Host '后端 HTTP 与入口 HTTPS 检查通过。'
Write-Host "`n完成。访问地址: https://${Server}:9999"
