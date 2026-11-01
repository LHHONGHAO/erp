# 生成 JSH_ERP 局域网自签名 HTTPS 证书（不写入系统证书库，纯 .NET 生成）
# 输出：
#   D:\erp\data\config\jsh-https.p12  后端 HTTPS 连接器 / 本地代理使用的 PKCS12（密码 changeit）
#   D:\erp\data\config\jsh-erp.cer     供手机/电脑安装的 DER 证书（含 CA 标志）
param(
    [string]$DataDir = 'D:\erp\data',
    [string]$LanIp = '192.168.100.157',
    [string]$Passphrase = 'changeit',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security

$certDir = Join-Path $DataDir 'config'
$p12 = Join-Path $certDir 'jsh-https.p12'
$cer = Join-Path $certDir 'jsh-erp.cer'
if ((Test-Path $p12) -and (Test-Path $cer) -and (-not $Force)) {
    Write-Host "证书已存在，跳过生成（如需重新生成请加 -Force）：$p12"
    exit 0
}
if (-not (Test-Path $certDir)) { New-Item -ItemType Directory -Path $certDir -Force | Out-Null }

$rsa = [System.Security.Cryptography.RSA]::Create(2048)
$dn = [System.Security.Cryptography.X509Certificates.X500DistinguishedName]::new('CN=jsh-erp.local')
$req = [System.Security.Cryptography.X509Certificates.CertificateRequest]::new(
    $dn,
    $rsa,
    [System.Security.Cryptography.HashAlgorithmName]::SHA256,
    [System.Security.Cryptography.RSASignaturePadding]::Pkcs1
)

# 作为根证书使用：CA=true，可用于校验服务器证书
$basic = [System.Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new($true, $false, 10, $true)
$req.CertificateExtensions.Add($basic)

$keyUsage = [System.Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new(
    [System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]'DigitalSignature,KeyEncipherment,KeyCertSign',
    $true)
$req.CertificateExtensions.Add($keyUsage)

$oids = New-Object System.Security.Cryptography.OidCollection
[void]$oids.Add([System.Security.Cryptography.Oid]::new('1.3.6.1.5.5.7.3.1'))
$req.CertificateExtensions.Add(
    [System.Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]::new($oids, $false))

$san = [System.Security.Cryptography.X509Certificates.SubjectAlternativeNameBuilder]::new()
$san.AddDnsName('localhost')
$san.AddDnsName('jsh-erp.local')
$san.AddIpAddress([System.Net.IPAddress]'127.0.0.1')
$san.AddIpAddress([System.Net.IPAddress]$LanIp)
$req.CertificateExtensions.Add($san.Build())

$cert = $req.CreateSelfSigned([DateTimeOffset]::Now.AddDays(-1), [DateTimeOffset]::Now.AddYears(10))

[System.IO.File]::WriteAllBytes($p12, $cert.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Pfx, $Passphrase))
[System.IO.File]::WriteAllBytes($cer, $cert.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert))
$cert.Dispose()
$rsa.Dispose()

Write-Host "证书已生成："
Write-Host "  PKCS12 : $p12"
Write-Host "  证书   : $cer"
Write-Host "  SAN    : localhost / jsh-erp.local / 127.0.0.1 / $LanIp"
Write-Host "  有效期 : 10 年"
