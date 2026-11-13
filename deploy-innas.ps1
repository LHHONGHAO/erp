<#
.SYNOPSIS
  jshERP 部署到 www.innas.xyz：构建 -> 上传 -> 重启 -> 健康检查 ->（可选）自动回滚。
.DESCRIPTION
  在本仓库根目录执行。默认只部署后端；前端有改动时加 -Frontend。

  与旧版相比的改进：
    1. 部署目录自动探测（读取远端 docker-compose.yml 所在目录），不再写死路径；
    2. IPv6 连通性检测与提示（域名只有 AAAA 记录、或 IPv4 被劫持为 fake-ip 时会明确提示）；
    3. 上传前后做 md5 校验；远端 jar 与本地一致时自动跳过上传；
    4. 前端按容器挂载点 frontend-dist 覆盖，tar 打包上传，避免多次嵌套；
    5. 健康检查改为轮询等待，而不是固定 sleep；
    6. -RollbackOnFailure 可在健康检查失败时自动回滚到上一份 jar / 前端；
    7. 远端旧产物自动保留 5 份（jar）与 3 份（前端），避免备份无限堆积；
    8. 密码来源支持环境变量 / tools\ssh\password，不再要求命令行明文。
.PARAMETER Frontend
  同时构建并部署前端 dist。
.PARAMETER SkipBuild
  不重新打包，使用现有 jshERP-boot\target\jshERP.jar 与 jshERP-web\dist。
.PARAMETER OnlyRestart
  不上传任何文件，只重启远端服务（排查环境问题时使用）。
.PARAMETER Force
  即使远端 jar 的 md5 与本地一致，也重新上传。
.PARAMETER NoArchive
  上传前不备份远端旧产物。
.PARAMETER RollbackOnFailure
  健康检查失败时自动回滚到最近一次备份并重启。
.PARAMETER RemoteDir
  远端部署目录；留空则自动探测。
.EXAMPLE
  .\deploy-innas.ps1 -IPv6 -Frontend
  .\deploy-innas.ps1 -IPv6 -SkipBuild -OnlyRestart
  .\deploy-innas.ps1 -IPv6 -RollbackOnFailure
#>
param(
    [switch]$Frontend,
    [switch]$SkipBuild,
    [switch]$OnlyRestart,
    [switch]$Force,
    [switch]$NoArchive,
    [switch]$RollbackOnFailure,
    [string]$Server    = 'www.innas.xyz',
    [int]   $Port      = 22,
    [string]$SshUser   = 'root',
    [string]$RemoteDir = '',
    [string]$HostKey   = '',
    [switch]$IPv6,
    [string]$SshPassword = '',
    [int]   $AppPort   = 9998,
    [int]   $WebPort   = 9999,
    [int]   $TimeoutSec = 90
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

# 传 IPv6 字面量时自动启用 -6，省得漏参数
if (-not $IPv6 -and $Server -match ':') { $IPv6 = [switch]$true }

# ---------------- 工具准备 ----------------
$commonModule = Join-Path $toolsDir 'innas-common.ps1'
if (-not (Test-Path $commonModule)) { throw "缺少公共模块: $commonModule" }
. $commonModule

function Ensure-Tools {
    Ensure-InnasTools -ToolsDir $toolsDir
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
function Ensure-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { return }
    $candidates = @(
        "$env:USERPROFILE\.jdks\jdk8u492-b09",
        'C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot',
        'C:\Program Files\Java\jdk1.8.0_202'
    ) + @(Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match 'jdk8|1\.8' } | Select-Object -ExpandProperty FullName)
    foreach ($dir in $candidates) {
        if ($dir -and (Test-Path (Join-Path $dir 'bin\java.exe'))) {
            $env:JAVA_HOME = $dir
            $env:Path = "$dir\bin;$env:Path"
            Write-Host "   使用 JDK: $dir"
            return
        }
    }
    throw '未找到可用的 JDK8，请先设置 JAVA_HOME（本项目需 JDK8）'
}
function Invoke-Maven([string[]]$Goals) {
    Ensure-JavaHome
    $mvn = if ($mavenHome) { Join-Path $mavenHome 'bin\mvn.cmd' } else { 'mvn' }
    Push-Location (Join-Path $root 'jshERP-boot')
    try {
        & $mvn @Goals 2>&1 | Select-Object -Last 5
        if ($LASTEXITCODE -ne 0) { throw "maven 执行失败: $Goals" }
    } finally { Pop-Location }
}
function Invoke-WebBuild {
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

# ---------------- 0. 连接 ----------------
Write-Host '==> [0/6] 建立 SSH 会话 ...'
Ensure-Tools

if (-not (Test-InnasPort -Server $Server -Port $Port -IPv6:$IPv6)) {
    $hint = Get-InnasAddressHint -Server $Server
    $msg = "无法连接 ${Server}:${Port}（IPv6=$([bool]$IPv6)）。"
    if ($hint) { $msg += " $hint。" }
    $msg += ' 请确认主机可达、SSH 端口正确，或在 hosts 中配置真实地址。'
    throw $msg
}
$session = Initialize-InnasSession -Server $Server -Port $Port -User $SshUser -HostKey $HostKey -IPv6:$IPv6 `
    -Password $SshPassword -ToolsDir $toolsDir
if (-not $session.HasPwd) { Write-Host '   未提供密码，将使用已保存的 SSH 密钥或 Pageant 凭据。' }
Write-Host "   目标: $($session.Target)  指纹: $($session.HostKey)"

# ---------------- 1. 确定远端目录 ----------------
Write-Host '==> [1/6] 解析远端部署目录 ...'
$RemoteDir = Find-InnasRemoteDir -Preferred $RemoteDir
Write-Host "   部署目录: $RemoteDir"

# ---------------- 2. 构建 ----------------
if (-not $SkipBuild -and -not $OnlyRestart) {
    Write-Host '==> [2/6] 构建产物 ...'
    Invoke-Maven @('clean', 'package', '-DskipTests', '-q')
    if ($Frontend) { Invoke-WebBuild }
} else {
    Write-Host '==> [2/6] 跳过构建'
}
if (-not (Test-Path $jarPath)) { throw "未找到打包产物: $jarPath（去掉 -SkipBuild 重新构建）" }
$localJarMd5 = Get-LocalFileMd5 -Path $jarPath

# ---------------- 3. 上传 ----------------
Write-Host '==> [3/6] 上传产物 ...'
if ($OnlyRestart) {
    Write-Host '   已指定 -OnlyRestart，跳过上传'
} else {
    $remoteJarMd5 = Get-InnasFileMd5 -RemotePath "$RemoteDir/jshERP.jar"
    if (-not $Force -and $remoteJarMd5 -eq $localJarMd5) {
        Write-Host "   jar 已是最新（md5 $localJarMd5），跳过上传"
    } else {
        if (-not $NoArchive) {
            $archive = @'
cd __DIR__ || exit 1
if [ -f jshERP.jar ]; then
  cp jshERP.jar "jshERP.jar.bak-$(date +%Y%m%d-%H%M%S)"
fi
ls -1t jshERP.jar.bak-* 2>/dev/null | sed '1,5d' | while read f; do rm -f "$f"; done
echo "jar backup done"
'@
            $archive = $archive -replace '__DIR__', $RemoteDir
            Invoke-InnasScript -Script $archive | Out-Null
        }
        Copy-ToInnas -Path $jarPath -RemotePath "$RemoteDir/jshERP.jar"
        $afterMd5 = Get-InnasFileMd5 -RemotePath "$RemoteDir/jshERP.jar"
        if ($afterMd5 -ne $localJarMd5) {
            throw "jar 上传校验失败：本地 $localJarMd5，远端 $afterMd5"
        }
        Write-Host "   jar 上传完成并校验通过（md5 $localJarMd5）"
    }

    if ($Frontend) {
        $distDir = Join-Path $webDir 'dist'
        if (-not (Test-Path (Join-Path $distDir 'index.html'))) { throw "未找到前端产物: $distDir\index.html" }
        if (-not (Get-Command tar.exe -ErrorAction SilentlyContinue)) { throw '系统缺少 tar.exe（Windows 10 1803+ 自带），无法打包前端' }
        $tarFile = Join-Path $env:TEMP 'jsherp-dist.tar.gz'
        if (Test-Path $tarFile) { Remove-Item $tarFile -Force }
        Push-Location $distDir
        try {
            & tar.exe -czf $tarFile .
            if ($LASTEXITCODE -ne 0) { throw '前端打包失败' }
        } finally { Pop-Location }
        Copy-ToInnas -Path $tarFile -RemotePath "$RemoteDir/dist.tar.gz"

        # 容器把 ./frontend-dist 挂到 /app/frontend-dist，必须解包到该目录
        $swap = @'
cd __DIR__ || exit 1
mv frontend-dist "frontend-dist-bak-$(date +%Y%m%d-%H%M%S)" 2>/dev/null
mkdir -p frontend-dist
tar -xzf dist.tar.gz -C frontend-dist || exit 1
rm -f dist.tar.gz
ls -1dt frontend-dist-bak-* 2>/dev/null | sed '1,3d' | while read f; do rm -rf "$f"; done
echo "frontend updated"
'@
        $swap = $swap -replace '__DIR__', $RemoteDir
        Invoke-InnasScript -Script $swap | Out-Null

        $localIndexMd5 = Get-LocalFileMd5 -Path (Join-Path $distDir 'index.html')
        $remoteIndexMd5 = Get-InnasFileMd5 -RemotePath "$RemoteDir/frontend-dist/index.html"
        if ($localIndexMd5 -ne $remoteIndexMd5) {
            throw "前端上传校验失败：本地 index.html $localIndexMd5，远端 $remoteIndexMd5"
        }
        Write-Host '   dist 上传完成并校验通过'
    }
}

# ---------------- 4. 重启 ----------------
Write-Host '==> [4/6] 重启远端服务 ...'
$restartScript = @'
cd __DIR__ || exit 1
if [ -f docker-compose.yml ] && docker compose version >/dev/null 2>&1; then
  echo "MODE=docker-compose"
  docker compose up -d --force-recreate app 2>&1 | tail -3
elif [ -f docker-compose.yml ] && command -v docker-compose >/dev/null 2>&1; then
  echo "MODE=docker-compose-v1"
  docker-compose up -d --force-recreate app 2>&1 | tail -3
elif systemctl list-unit-files 2>/dev/null | grep -q '^jsherp'; then
  echo "MODE=systemd"
  systemctl restart jsherp
elif [ -f jshERP.jar ]; then
  echo "MODE=java"
  if command -v pkill >/dev/null 2>&1; then pkill -f 'jshERP.jar'; else for p in $(pidof java); do kill -9 $p; done; fi
  sleep 2
  nohup java -jar jshERP.jar > app.log 2>&1 &
  sleep 3
  pidof java >/dev/null && echo 'java started'
else
  echo 'MODE=unknown'
  exit 1
fi
'@
$restartScript = $restartScript -replace '__DIR__', $RemoteDir
Invoke-InnasScript -Script $restartScript | Out-Null

# ---------------- 5. 健康检查 ----------------
Write-Host "==> [5/6] 健康检查（最长 $TimeoutSec 秒）..."
$checks = [Math]::Max(1, [int]($TimeoutSec / 3))
$healthScript = @'
i=0
while [ $i -lt __COUNT__ ]; do
  a=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:__APP__/user/randomImage)
  w=$(curl -sk -o /dev/null -w '%{http_code}' --max-time 5 https://127.0.0.1:__WEB__/)
  echo "app=$a web=$w"
  if [ "$a" = "200" ] && [ "$w" = "200" ]; then exit 0; fi
  i=$((i+1))
  sleep 3
done
exit 1
'@
$healthScript = $healthScript -replace '__COUNT__', "$checks" -replace '__APP__', "$AppPort" -replace '__WEB__', "$WebPort"
$health = Invoke-InnasScript -Script $healthScript -AllowFailure

$healthy = $false
foreach ($line in $health) {
    if ("$line" -match 'app=200 web=200') { $healthy = $true }
}
if ($healthy) {
    Write-Host '   健康检查通过'
} else {
    Write-Warning '健康检查未通过'
    $tail = Invoke-Innas -Command "docker logs --tail 30 jsherp-app 2>&1 || tail -30 $RemoteDir/app.log" -Quiet -AllowFailure
    $tail | Select-Object -Last 30 | ForEach-Object { Write-Host "   $_" }
    if ($RollbackOnFailure) {
        Write-Host '==> 回滚到上一份产物 ...'
        $rollback = @'
cd __DIR__ || exit 1
latest=$(ls -1t jshERP.jar.bak-* 2>/dev/null | head -1)
if [ -n "$latest" ]; then cp "$latest" jshERP.jar; echo "jar 回滚: $latest"; fi
latestdir=$(ls -1dt frontend-dist-bak-* 2>/dev/null | head -1)
if [ -n "$latestdir" ]; then
  mv frontend-dist "frontend-dist-failed-$(date +%Y%m%d-%H%M%S)" 2>/dev/null
  mv "$latestdir" frontend-dist
  echo "前端回滚: $latestdir"
fi
if [ -f docker-compose.yml ]; then docker compose up -d --force-recreate app 2>&1 | tail -3; fi
'@
        $rollback = $rollback -replace '__DIR__', $RemoteDir
        Invoke-InnasScript -Script $rollback
        throw '部署失败，已回滚到上一份产物；请查看上面的日志确认原因。'
    }
    throw '部署失败，健康检查未通过（可加 -RollbackOnFailure 自动回滚）。'
}

# ---------------- 6. 汇总 ----------------
Write-Host '==> [6/6] 部署完成'
$status = Invoke-Innas -Command "docker ps --filter name=jsherp --format '{{.Names}}  {{.Status}}'" -Quiet -AllowFailure
$commit = (git -C $root rev-parse --short HEAD 2>$null)
Write-Host ''
Write-Host "  主机      : $Server"
Write-Host "  目录      : $RemoteDir"
Write-Host "  jar md5   : $localJarMd5"
if ($commit) { Write-Host "  代码版本  : $commit" }
if ($status) { $status | ForEach-Object { Write-Host "  $_" } }
Write-Host "  访问地址  : https://${Server}:${WebPort}"
