# jshERP 前端部署到 NAS（192.168.100.1）
# 关键点：bind mount 绑定目录 inode，必须就地覆盖 frontend-dist，不能重命名目录切换。
param(
    [switch]$SkipBuild,
    [string]$Server  = '192.168.100.1',
    [string]$RemoteDir = '/mnt/mmc0-4/jsherp',
    [string]$HostKey = 'SHA256:R2PbBdYWF37HyDs9OpcQrxw12YQUanbsS4s5gG8MxZo',
    [int]$AppPort = 9998
)

$ErrorActionPreference = 'Stop'
$root       = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolsDir   = Join-Path $root 'tools'
$webDir     = Join-Path $root 'jshERP-web'
$distDir    = Join-Path $webDir 'dist'
$nodeDir    = Join-Path $toolsDir 'node16-build'
$plink      = Join-Path $toolsDir 'plink.exe'
$pscp       = Join-Path $toolsDir 'pscp.exe'
$pwFile     = Join-Path $toolsDir 'ssh\password'
$zipPath    = Join-Path $env:TEMP 'frontend-dist.zip'
$scriptPath = Join-Path $env:TEMP 'deploy-nas-remote.sh'

foreach ($p in @($plink, $pscp, $pwFile)) {
    if (-not (Test-Path $p)) { throw "缺少文件: $p" }
}
$pw = (Get-Content $pwFile -Raw).Trim()

# 1. 构建
if (-not $SkipBuild) {
    if (-not (Test-Path (Join-Path $nodeDir 'npm.cmd'))) { throw "缺少 Node 构建环境: $nodeDir" }
    Write-Host '[1/5] 构建前端 ...'
    Push-Location $webDir
    try {
        $env:Path = $nodeDir + ';' + $env:Path
        & (Join-Path $nodeDir 'npm.cmd') run build
        if ($LASTEXITCODE -ne 0) { throw "前端构建失败，退出码 $LASTEXITCODE" }
    } finally { Pop-Location }
} else {
    Write-Host '[1/5] 跳过构建，使用现有 dist'
}
$indexLocal = Join-Path $distDir 'index.html'
if (-not (Test-Path $indexLocal)) { throw "找不到构建产物: $indexLocal" }
$expectedMd5 = (Get-FileHash $indexLocal -Algorithm MD5).Hash.ToLower()
$appJs = [regex]::Match((Get-Content $indexLocal -Raw), '/js/app[^"''\s>]*\.js').Value
if (-not $appJs) { throw '无法从 index.html 解析入口 JS' }

# 2. 打包（条目统一用 / 分隔）
Write-Host '[2/5] 打包 dist ...'
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    Get-ChildItem $distDir -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($distDir.Length + 1).Replace('\', '/')
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $rel, [System.IO.Compression.CompressionLevel]::Optimal)
    }
} finally { $zip.Dispose() }

# 3. 上传
Write-Host '[3/5] 上传到设备 ...'
& $pscp -pw $pw -batch -hostkey $HostKey $zipPath "root@${Server}:/tmp/frontend-dist.zip"
if ($LASTEXITCODE -ne 0) { throw '上传失败' }

# 4. 远端就地覆盖 + 校验 + 回滚
$remote = @'
set -e
REMOTE_DIR="__REMOTE_DIR__"
EXPECTED="__EXPECTED__"
APPJS="__APPJS__"
PORT="__PORT__"
cd "$REMOTE_DIR"
rm -rf /tmp/fd-new
mkdir -p /tmp/fd-new
unzip -q -o /tmp/frontend-dist.zip -d /tmp/fd-new
if [ ! -f /tmp/fd-new/index.html ]; then echo "NO_INDEX"; exit 2; fi
TS=$(date +%Y%m%d-%H%M%S)
cp -a frontend-dist "frontend-dist-bak-$TS"
# 就地覆盖而非清空目录：保留旧哈希资源，避免在线用户懒加载 chunk 时 404 导致点击无响应
cp -a /tmp/fd-new/. frontend-dist/
# 仅清理 7 天前的历史哈希资源
find frontend-dist/js frontend-dist/css frontend-dist/img -type f -mtime +7 -delete 2>/dev/null || true
NEWMD5=$(md5sum frontend-dist/index.html | cut -d' ' -f1)
if [ "$NEWMD5" != "$EXPECTED" ]; then
  echo "MD5_MISMATCH $NEWMD5"
  rm -rf frontend-dist/* 2>/dev/null || true
  cp -a "frontend-dist-bak-$TS"/. frontend-dist/
  exit 3
fi
ls -1dt frontend-dist-bak-* 2>/dev/null | sed '1,3d' | while read f; do rm -rf "$f"; done
rm -rf /tmp/fd-new /tmp/frontend-dist.zip
SERVED=$(curl -s "http://127.0.0.1:$PORT/" | md5sum | cut -d' ' -f1)
echo "SERVED_MD5 $SERVED"
if [ -f "frontend-dist$APPJS" ]; then echo "APP_JS_OK"; else echo "APP_JS_MISSING"; fi
echo "SYNC_OK $TS"
'@
$remote = $remote.Replace('__REMOTE_DIR__', $RemoteDir).Replace('__EXPECTED__', $expectedMd5)
$remote = $remote.Replace('__APPJS__', $appJs).Replace('__PORT__', "$AppPort")
Set-Content -Path $scriptPath -Value $remote -Encoding ASCII -NoNewline

Write-Host '[4/5] 就地同步 frontend-dist ...'
& $pscp -pw $pw -batch -hostkey $HostKey $scriptPath "root@${Server}:/tmp/deploy-nas.sh"
if ($LASTEXITCODE -ne 0) { throw '上传部署脚本失败' }
$out = & $plink -ssh "root@$Server" -pw $pw -batch -hostkey $HostKey 'sh /tmp/deploy-nas.sh'
$out | ForEach-Object { Write-Host "    $_" }
if ($LASTEXITCODE -ne 0) { throw "远端同步失败，退出码 $LASTEXITCODE" }

# 5. 校验线上实际响应
Write-Host '[5/5] 校验线上入口 ...'
$servedLine = $out | Where-Object { $_ -like 'SERVED_MD5 *' } | Select-Object -First 1
$servedMd5 = if ($servedLine) { ($servedLine -split ' ')[1] } else { '' }
if ($servedMd5 -ne $expectedMd5) { throw "线上入口 MD5 不一致: $servedMd5 != $expectedMd5" }
if (-not ($out -contains 'APP_JS_OK')) { throw "线上入口资源缺失: $appJs" }

& $plink -ssh "root@$Server" -pw $pw -batch -hostkey $HostKey 'rm -f /tmp/deploy-nas.sh' | Out-Null

Write-Host ''
Write-Host "部署完成: http://${Server}:$AppPort/  ($appJs, index md5 $expectedMd5)"
