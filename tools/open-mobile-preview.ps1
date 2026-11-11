﻿﻿﻿﻿﻿param(
    [string]$Path = '/dashboard/analysis',
    [ValidateSet('iPhoneSE', 'iPhone12', 'iPhone14ProMax', 'Android360', 'iPadMini')]
    [string]$Device = 'iPhone12',
    [ValidateSet('portrait', 'landscape')]
    [string]$Orientation = 'portrait',
    [string]$BaseUrl = 'http://localhost:3000'
)

$ErrorActionPreference = 'Stop'

# 机型尺寸表（单位：CSS 像素）
$devices = @{
    iPhoneSE       = @{ Width = 375; Height = 667;  Label = 'iPhone SE 375x667' }
    iPhone12       = @{ Width = 390; Height = 844;  Label = 'iPhone 12 390x844' }
    iPhone14ProMax = @{ Width = 430; Height = 932;  Label = 'iPhone 14 Pro Max 430x932' }
    Android360     = @{ Width = 360; Height = 800;  Label = 'Android 360x800' }
    iPadMini       = @{ Width = 768; Height = 1024; Label = 'iPad mini 768x1024' }
}

$selected = $devices[$Device]
if ($Orientation -eq 'landscape') {
    $screenWidth = $selected.Height
    $screenHeight = $selected.Width
} else {
    $screenWidth = $selected.Width
    $screenHeight = $selected.Height
}

if ($Path -notmatch '^/') { $Path = '/' + $Path }
$url = "$BaseUrl$Path"

# 检查开发服务器是否已启动
try {
    Invoke-WebRequest -Uri $BaseUrl -UseBasicParsing -TimeoutSec 3 | Out-Null
} catch {
    Write-Host "[WARN] $BaseUrl 未响应，请先在 jshERP-web 目录执行 npm run serve" -ForegroundColor Yellow
}

# 查找 Chrome 或 Edge
$browser = $null
$candidates = @()
if ($env:ProgramFiles) { $candidates += Join-Path $env:ProgramFiles 'Google\Chrome\Application\chrome.exe' }
if (${env:ProgramFiles(x86)}) { $candidates += Join-Path ${env:ProgramFiles(x86)} 'Google\Chrome\Application\chrome.exe' }
if ($env:LOCALAPPDATA) { $candidates += Join-Path $env:LOCALAPPDATA 'Google\Chrome\Application\chrome.exe' }
if ($env:ProgramFiles) { $candidates += Join-Path $env:ProgramFiles 'Microsoft\Edge\Application\msedge.exe' }
if (${env:ProgramFiles(x86)}) { $candidates += Join-Path ${env:ProgramFiles(x86)} 'Microsoft\Edge\Application\msedge.exe' }

foreach ($candidate in $candidates) {
    if (Test-Path $candidate) {
        $browser = $candidate
        break
    }
}

if (-not $browser) {
    Write-Host '[ERROR] 未找到 Chrome 或 Edge，请手动用 --app 模式打开目标地址' -ForegroundColor Red
    exit 1
}

# 独立浏览器配置目录：保证 --window-size 生效，避免复用已运行实例时被忽略
$profileDir = Join-Path $PSScriptRoot '..\.cache\mobile-preview-profile'
if (-not (Test-Path $profileDir)) {
    New-Item -ItemType Directory -Path $profileDir -Force | Out-Null
}

# --app 模式无地址栏，窗口高度补 32px 抵消系统标题栏，使内容区接近设备高度
$windowSize = "$screenWidth,$($screenHeight + 32)"

$browserArgs = @(
    "--app=$url",
    "--window-size=$windowSize",
    "--user-data-dir=$profileDir",
    '--no-first-run',
    '--no-default-browser-check'
)

Start-Process -FilePath $browser -ArgumentList $browserArgs

Write-Host ("已启动 {0} | {1} | {2} | {3}x{4}" -f (Split-Path $browser -Leaf), $url, $selected.Label, $screenWidth, $screenHeight) -ForegroundColor Green
Write-Host '提示：首次使用该独立配置需登录一次，登录态会保存在 .cache\mobile-preview-profile' -ForegroundColor DarkGray
