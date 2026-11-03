<#
.SYNOPSIS
  生成局域网 HTTPS 使用的「根证书 + 服务端证书」，客户端安装一次根证书即可长期信任。
.DESCRIPTION
  - 根证书 CA 有效 10 年，私钥保存在数据目录 config/jsh-ca.p12，续期服务端证书时复用，客户端无需重装
  - 服务端证书有效 5 年，SAN 自动包含 localhost、127.0.0.1、本机所有局域网 IPv4 与计算机名
  - 输出：
      config/jsh-ca.p12        根证书私钥（务必保留，丢失后所有客户端需重新安装）
      config/jsh-root-ca.crt   可发给客户端安装的根证书（授权文件）
      config/jsh-https.p12     Tomcat 使用的服务端密钥库（密码 changeit）
.PARAMETER DataDir
  数据目录，默认 e:\huaxia\jshERP-data
.PARAMETER JavaHome
  JDK 路径，不传则自动探测
.PARAMETER RenewServer
  重新签发服务端证书（IP 变化或证书到期时使用），根证书保持不变
.PARAMETER RenewRoot
  重新生成根证书，所有客户端都需要重新安装根证书
.EXAMPLE
  .\https-cert.ps1
  .\https-cert.ps1 -RenewServer
#>
param(
    [string]$DataDir = '',
    [string]$JavaHome = '',
    # 额外写入证书的域名（如远程访问域名 www.innas.xyz）
    [string[]]$Dns = @(),
    # 额外写入证书的 IP（如服务器公网 IP）
    [string[]]$Ip = @(),
    [switch]$RenewServer,
    [switch]$RenewRoot
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $DataDir) { $DataDir = Join-Path (Split-Path -Parent $Root) 'jshERP-data' }
$CfgDir = Join-Path $DataDir 'config'
New-Item -ItemType Directory -Path $CfgDir -Force | Out-Null

$p12File   = Join-Path $CfgDir 'jsh-https.p12'
$caP12File = Join-Path $CfgDir 'jsh-ca.p12'
$rootCrt   = Join-Path $CfgDir 'jsh-root-ca.crt'
$storePass = 'changeit'
$caValidDays     = 3650
$serverValidDays = 1825

function Find-Keytool([string]$Hint) {
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
        if ($resolved) {
            $exe = Join-Path $resolved 'bin\keytool.exe'
            if (Test-Path $exe) { return $exe }
        }
    }
    if (Get-Command keytool -ErrorAction SilentlyContinue) { return 'keytool' }
    throw '未找到 keytool.exe，请通过 -JavaHome 指定 JDK 目录'
}

function Invoke-Keytool([string[]]$CmdArgs, [string]$Desc) {
    & $keytool @CmdArgs | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "$Desc 失败（keytool 退出码 $LASTEXITCODE）" }
}

function Get-SanList {
    $parts = @('dns:localhost', 'ip:127.0.0.1')
    $ips = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
        Select-Object -ExpandProperty IPAddress
    foreach ($ip in $ips) { $parts += "ip:$ip" }
    if ($env:COMPUTERNAME) { $parts += "dns:$($env:COMPUTERNAME)" }
    foreach ($d in $Dns) { if ($d) { $parts += "dns:$d" } }
    foreach ($i in $Ip) { if ($i) { $parts += "ip:$i" } }
    # 去重，避免重复扩展项导致签发失败
    return (($parts | Select-Object -Unique) -join ',')
}

$keytool = Find-Keytool -Hint $JavaHome
Write-Host "keytool : $keytool" -ForegroundColor DarkGray

$needRoot   = (-not (Test-Path $caP12File)) -or $RenewRoot
$needServer = $RenewServer -or $needRoot -or (-not (Test-Path $p12File)) -or (-not (Test-Path $rootCrt))

if (-not $needServer) {
    Write-Host "证书已存在，无需重新生成：" -ForegroundColor Green
    Write-Host "  服务端: $p12File"
    Write-Host "  根证书: $rootCrt"
    Write-Host "如需在 IP 变化后重新签发，请执行: .\https-cert.ps1 -RenewServer" -ForegroundColor DarkGray
    exit 0
}

$work = Join-Path $env:TEMP ("jsh-cert-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work -Force | Out-Null
$serverP12  = Join-Path $work 'server.p12'
$serverCsr  = Join-Path $work 'server.csr'
$serverCrtP = Join-Path $work 'server.crt'

try {
    if ($needRoot) {
        Write-Host "==> 生成根证书（有效期 $caValidDays 天）" -ForegroundColor Yellow
        Invoke-Keytool @(
            '-genkeypair', '-alias', 'jsh-ca', '-keyalg', 'RSA', '-keysize', '2048',
            '-sigalg', 'SHA256withRSA', '-validity', "$caValidDays",
            '-dname', 'CN=JSH ERP Root CA,OU=LAN,O=JSH ERP,C=CN', '-ext', 'bc:c',
            '-keystore', $caP12File, '-storetype', 'PKCS12',
            '-storepass', $storePass, '-keypass', $storePass
        ) '生成根证书'
        Invoke-Keytool @(
            '-exportcert', '-alias', 'jsh-ca', '-keystore', $caP12File,
            '-storepass', $storePass, '-rfc', '-file', $rootCrt
        ) '导出根证书'
        Write-Host "    根证书已生成: $rootCrt" -ForegroundColor Green
    } else {
        Write-Host "==> 复用已有根证书，仅重新签发服务端证书" -ForegroundColor Yellow
        Invoke-Keytool @(
            '-exportcert', '-alias', 'jsh-ca', '-keystore', $caP12File,
            '-storepass', $storePass, '-rfc', '-file', $rootCrt
        ) '导出根证书'
    }

    $san = Get-SanList
    Write-Host "==> 生成服务端证书（有效期 $serverValidDays 天）" -ForegroundColor Yellow
    Write-Host "    SAN: $san" -ForegroundColor DarkGray
    Invoke-Keytool @(
        '-genkeypair', '-alias', 'jsh-https', '-keyalg', 'RSA', '-keysize', '2048',
        '-sigalg', 'SHA256withRSA', '-validity', "$serverValidDays",
        '-dname', 'CN=JSH ERP,OU=LAN,O=JSH ERP,C=CN',
        '-keystore', $serverP12, '-storetype', 'PKCS12',
        '-storepass', $storePass, '-keypass', $storePass
    ) '生成服务端密钥'
    Invoke-Keytool @(
        '-certreq', '-alias', 'jsh-https', '-keystore', $serverP12,
        '-storepass', $storePass, '-file', $serverCsr
    ) '生成证书请求'
    Invoke-Keytool @(
        '-gencert', '-alias', 'jsh-ca', '-keystore', $caP12File, '-storepass', $storePass,
        '-infile', $serverCsr, '-outfile', $serverCrtP, '-rfc',
        '-validity', "$serverValidDays", '-ext', "SAN=$san", '-ext', 'EKU=serverAuth'
    ) '签发服务端证书'
    Invoke-Keytool @(
        '-importcert', '-alias', 'jsh-ca', '-keystore', $serverP12,
        '-storepass', $storePass, '-file', $rootCrt, '-noprompt'
    ) '导入根证书到密钥库'
    Invoke-Keytool @(
        '-importcert', '-alias', 'jsh-https', '-keystore', $serverP12,
        '-storepass', $storePass, '-file', $serverCrtP, '-noprompt'
    ) '导入服务端证书链'

    Copy-Item $serverP12 $p12File -Force
    Write-Host "==> 完成" -ForegroundColor Green
    Write-Host "    服务端密钥库: $p12File"
    Write-Host "    客户端授权文件: $rootCrt"
    Write-Host "    客户端访问 http://<本机IP>:9999/cert 下载安装根证书后，HTTPS 将不再提示不安全" -ForegroundColor Green
} finally {
    Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
}
