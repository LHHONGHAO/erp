# Start local backend (invoked by scheduled task JSHERP-Backend)
# 所有路径相对本脚本推导，配置固化在项目根目录 deploy.config.json
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$cfg = Get-Content (Join-Path $Root 'deploy.config.json') -Raw -Encoding UTF8 | ConvertFrom-Json

function Find-Java {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return (Join-Path $env:JAVA_HOME 'bin\java.exe')
    }
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        "$env:ProgramFiles\Eclipse Adoptium\jdk-8.0.*",
        "$env:USERPROFILE\.jdks\jdk8*",
        "$env:ProgramFiles\Java\jdk1.8.0_*"
    )
    foreach ($c in $candidates) {
        $r = Resolve-Path $c -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Path
        if ($r -and (Test-Path (Join-Path $r 'bin\java.exe'))) { return (Join-Path $r 'bin\java.exe') }
    }
    throw '未找到 JDK 8，请设置 JAVA_HOME'
}

$javaExe   = Find-Java
$rootPath  = $Root -replace '\\', '/'
$dataDir   = $cfg.dataDir -replace '\\', '/'
$staticDir = '-Dspring.resources.static-locations=file:' + $rootPath + '/jshERP-boot/frontend-dist/'
$dataDirArg = '-Djsh.data.dir=' + $dataDir
$logsHomeArg = '-Dlogs.home=' + $dataDir + '/logs'
# HTTP 端口固定 9998（用于证书安装与兼容），HTTPS 由 HttpsConnectorConfig 按 jsh.https.port 额外开启
# 证书由 https-cert.ps1 生成在数据目录 config 下，无需在此注入 server.ssl
$httpArg   = '-Dserver.port=9998'
$httpsArg  = '-Djsh.https.port=' + $cfg.backendPort
$logFile   = Join-Path $Root 'jshERP-boot\local-backend.log'
$jar       = Join-Path $Root 'jshERP-boot\target\jshERP.jar'

Set-Location (Join-Path $Root 'jshERP-boot')
& $javaExe '-Xms256m' '-Xmx1024m' '-Dspring.profiles.active=dev' $httpArg $httpsArg '-Dfile.encoding=UTF-8' $staticDir $dataDirArg $logsHomeArg '-jar' $jar *> $logFile
