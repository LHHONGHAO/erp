﻿﻿﻿﻿﻿<#
.SYNOPSIS
  构建 JSH 通用打印组件（print-agent.exe）并同步到前端静态目录
.DESCRIPTION
  1) 用系统自带 csc.exe 编译 src\PrintAgent.cs（.NET Framework 4.x，无需额外依赖）
  2) 产物输出到 dist\：exe + 使用说明.txt + print-agent.zip
  3) 复制 exe / zip 到 jshERP-web\public\print-agent\，随前端构建进入 dist，
     部署后即可通过 http(s)://<域名>:9999/print-agent/print-agent.exe 下载
.EXAMPLE
  .\build.ps1
  .\build.ps1 -NoSync
#>
param(
    [switch]$NoSync
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}

$root      = Split-Path -Parent $MyInvocation.MyCommand.Path
$src       = Join-Path $root 'src\PrintAgent.cs'
$dist      = Join-Path $root 'dist'
$publicDir = Join-Path $root '..\..\jshERP-web\public\print-agent'

if (-not (Test-Path $src)) { throw "未找到源码：$src" }

# 1) 定位 csc.exe（.NET Framework 4.x 自带，无需安装 SDK）
$csc = @(
    'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe',
    'C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe'
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $csc) { throw '未找到 .NET Framework 4.x 编译器 csc.exe' }

New-Item -ItemType Directory -Force -Path $dist | Out-Null

# 2) 源码转成带 BOM 的 UTF-8，避免 csc 按 ANSI 读取导致中文乱码
$tmpSrc = Join-Path $env:TEMP 'jsh-print-agent-build.cs'
$text   = [IO.File]::ReadAllText($src, [Text.Encoding]::UTF8)
[IO.File]::WriteAllText($tmpSrc, $text, (New-Object Text.UTF8Encoding($true)))

Write-Host "==> 编译 print-agent.exe ..."
$exe = Join-Path $dist 'print-agent.exe'
& $csc /nologo /target:winexe /platform:anycpu /optimize+ `
    /r:System.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll `
    /out:"$exe" "$tmpSrc"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $exe)) { throw '编译失败' }

# 3) 使用说明
$readme = Join-Path $dist '使用说明.txt'
$lines = @(
    'JSH 通用打印组件（Windows）',
    '',
    '【安装】',
    '  1. 双击 print-agent.exe 即可。程序会自动安装到：',
    '     %LOCALAPPDATA%\JshPrintAgent\print-agent.exe',
    '  2. 安装后常驻任务栏右下角托盘（图标为默认应用程序图标）。',
    '  3. 默认开机自动启动，可在托盘右键菜单里取消。',
    '',
    '【使用】',
    '  服务地址：http://127.0.0.1:17890（被占用时自动顺延 17891 / 17892）',
    '  浏览器打开该地址可查看运行状态与本机打印机列表。',
    '',
    '【接口】',
    '  GET  /ping       健康检查',
    '  GET  /printers   本机打印机名称列表',
    '  POST /print      { printer, image(base64), widthMm, heightMm, copies }',
    '  POST /raw        { printer, data | dataBase64, encoding }',
    '',
    '【排障】',
    '  - 托盘图标右键 → 打开日志文件，可查看运行与打印记录。',
    '  - 若双击后无任何界面，请确认托盘区是否有图标；日志中会有启动记录。',
    '  - 退出：托盘右键 → 退出打印组件。',
    '  - 卸载：托盘退出后，删除目录 %LOCALAPPDATA%\JshPrintAgent 即可。'
)
[IO.File]::WriteAllText($readme, ($lines -join "`r`n"), (New-Object Text.UTF8Encoding($true)))

# 4) 打包 zip（便于离线分发）
$zip = Join-Path $dist 'print-agent.zip'
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path @($exe, $readme) -DestinationPath $zip -Force

# 5) 同步到前端 public，构建时自动进入 dist 静态目录
if (-not $NoSync) {
    New-Item -ItemType Directory -Force -Path $publicDir | Out-Null
    Copy-Item $exe  (Join-Path $publicDir 'print-agent.exe') -Force
    Copy-Item $zip  (Join-Path $publicDir 'print-agent.zip') -Force
    Copy-Item $readme (Join-Path $publicDir '使用说明.txt') -Force
    Write-Host "==> 已同步到 $publicDir"
}

$size = (Get-Item $exe).Length
$hash = (Get-FileHash $exe -Algorithm SHA256).Hash
Write-Host ''
Write-Host ("完成：{0}  ({1:N1} KB)" -f $exe, ($size / 1024))
Write-Host ("SHA256: {0}" -f $hash)
