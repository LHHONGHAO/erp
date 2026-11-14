# 构建脚本（Windows / PowerShell）
# 用法:
#   powershell -ExecutionPolicy Bypass -File scripts\build.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\build.ps1 -Version 0.1.0 -Arch aarch64_cortex-a53 -Local

param(
    [string]$Version = "0.1.0",
    [string]$Arch = "aarch64_cortex-a53",
    [switch]$Local
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $root 'build'
$stageDir = Join-Path $buildDir 'stage'

function Resolve-Go {
    $cmd = Get-Command go -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $fallback = 'C:\Users\Administrator\.local\share\go1.27.3\go\bin\go.exe'
    if (Test-Path $fallback) { return $fallback }
    throw '未找到 Go 工具链，请先安装 Go 1.22+ 或加入 PATH'
}

$go = Resolve-Go
Write-Host "使用 Go: $go"

$savedGoos = $env:GOOS
$savedGoarch = $env:GOARCH
$savedCgo = $env:CGO_ENABLED
try {
    Push-Location $root

    New-Item -ItemType Directory -Force -Path $buildDir | Out-Null

    Write-Host "== 构建 linux/arm64 二进制 =="
    $env:CGO_ENABLED = '0'
    $env:GOOS = 'linux'
    $env:GOARCH = 'arm64'
    & $go build -trimpath -ldflags "-s -w -X main.version=$Version" -o (Join-Path $buildDir 'erp-print-agent') ./cmd/erp-print-agent
    if ($LASTEXITCODE -ne 0) { throw '构建失败' }

    $bin = Get-Item (Join-Path $buildDir 'erp-print-agent')
    Write-Host ("二进制大小: {0:N2} MB" -f ($bin.Length / 1MB))

    if ($Local) {
        Write-Host "== 构建 Windows 调试产物 =="
        $winDir = Join-Path $buildDir 'win'
        New-Item -ItemType Directory -Force -Path $winDir | Out-Null
        $env:GOOS = 'windows'
        $env:GOARCH = 'amd64'
        & $go build -o (Join-Path $winDir 'erp-print-agent.exe') ./cmd/erp-print-agent
        if ($LASTEXITCODE -ne 0) { throw '构建 Windows 代理失败' }
        & $go build -o (Join-Path $winDir 'mock-server.exe') ./cmd/mock-server
        if ($LASTEXITCODE -ne 0) { throw '构建 mock-server 失败' }
    }

    Write-Host "== 准备 staging 目录 =="
    if (Test-Path $stageDir) { Remove-Item -Recurse -Force $stageDir }
    New-Item -ItemType Directory -Force -Path (Join-Path $stageDir 'usr\bin') | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $stageDir 'etc\init.d') | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $stageDir 'etc\erp-print-agent') | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $stageDir 'usr\lib\lua\luci\controller') | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $stageDir 'usr\lib\lua\luci\view\erp_print_agent') | Out-Null

    Copy-Item (Join-Path $buildDir 'erp-print-agent') (Join-Path $stageDir 'usr\bin\erp-print-agent')
    Copy-Item (Join-Path $root 'packaging\openwrt\files\erp-print-agent.init') (Join-Path $stageDir 'etc\init.d\erp-print-agent')
    Copy-Item (Join-Path $root 'packaging\openwrt\files\config.json') (Join-Path $stageDir 'etc\erp-print-agent\config.json')
    Copy-Item (Join-Path $root 'packaging\openwrt\luci\controller\erp_print_agent.lua') (Join-Path $stageDir 'usr\lib\lua\luci\controller\erp_print_agent.lua')
    Copy-Item (Join-Path $root 'packaging\openwrt\luci\view\erp_print_agent\index.htm') (Join-Path $stageDir 'usr\lib\lua\luci\view\erp_print_agent\index.htm')
    # LuCI 模板解析器不接受 CRLF（会报 unfinished string），暂存时统一转成 LF
    $stagedView = Join-Path $stageDir 'usr\lib\lua\luci\view\erp_print_agent\index.htm'
    $viewText = [IO.File]::ReadAllText($stagedView) -replace "`r`n", "`n"
    [IO.File]::WriteAllText($stagedView, $viewText, (New-Object System.Text.UTF8Encoding($false)))
    $caFile = Join-Path $root 'packaging\openwrt\files\ca.pem'
    if (Test-Path $caFile) {
        Copy-Item $caFile (Join-Path $stageDir 'etc\erp-print-agent\ca.pem')
    } else {
        Write-Warning '未找到 packaging\openwrt\files\ca.pem，IPK 内不含 CA 证书'
    }

    Write-Host "== 生成 IPK =="
    $env:GOOS = 'windows'
    $env:GOARCH = 'amd64'
    $ipkName = "erp-print-agent_${Version}_${Arch}.ipk"
    & $go run ./cmd/mk-ipk -stage $stageDir -out (Join-Path $buildDir $ipkName) `
        -version $Version -arch $Arch -conffiles /etc/erp-print-agent/config.json `
        -depends "libc,luci-base"
    if ($LASTEXITCODE -ne 0) { throw '生成 IPK 失败' }

    $ipk = Get-Item (Join-Path $buildDir $ipkName)
    Write-Host ("IPK: {0} ({1:N0} 字节)" -f $ipk.FullName, $ipk.Length)
}
finally {
    Pop-Location
    $env:GOOS = $savedGoos
    $env:GOARCH = $savedGoarch
    $env:CGO_ENABLED = $savedCgo
}
