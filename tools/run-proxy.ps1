# Start local root proxy (invoked by scheduled task JSHERP-Proxy)
# 代理端口/目标统一从项目根目录 deploy.config.json 读取（见 local-proxy.js）
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

function Find-Node {
    $cmd = Get-Command node.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        "$env:LOCALAPPDATA\Programs\node16\node-v16.20.2-win-x64\node.exe",
        "$env:ProgramFiles\nodejs\node.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    throw '未找到 node.exe，请先安装 Node.js'
}

$nodeExe = Find-Node
$proxyJs = Join-Path $Root 'tools\local-proxy.js'
$logFile = Join-Path $Root 'tools\local-proxy.log'
& $nodeExe $proxyJs *> $logFile
