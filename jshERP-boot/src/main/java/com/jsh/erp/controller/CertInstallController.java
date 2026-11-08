package com.jsh.erp.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * HTTPS 根证书安装入口。
 * 局域网自建根证书签发服务端证书，客户端只需安装一次根证书（授权文件）即可长期信任，
 * 之后访问 HTTPS 不再提示“不安全”，手机也能正常使用摄像头扫码等安全上下文功能。
 * 本页面走 HTTP 端口访问，未登录也可打开（已在 LogCostFilter 白名单中放行）。
 */
@RestController
@RequestMapping("/cert")
public class CertInstallController {

    private static final String ROOT_CA_FILE = "jsh-root-ca.crt";

    @Value("${jsh.data.dir:}")
    private String dataDir;

    @Value("${server.port:9998}")
    private int httpPort;

    @Value("${jsh.https.port:9999}")
    private int httpsPort;

    private File rootCaFile() {
        String dir = (dataDir == null || dataDir.trim().isEmpty() ? "config" : dataDir.trim() + "/config");
        return new File(dir, ROOT_CA_FILE);
    }

    private String httpBase(HttpServletRequest request) {
        return "http://" + request.getServerName() + ":" + httpPort;
    }

    @GetMapping({"", "/", "/index.html"})
    public ResponseEntity<String> guide(HttpServletRequest request) {
        String base = httpBase(request);
        String httpsUrl = "https://" + request.getServerName() + ":" + httpsPort;
        String exist = rootCaFile().exists() ? "" : "<p class=\"warn\">服务端尚未生成根证书，请先在服务器执行 https-cert.ps1。</p>";
        String html = "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>HTTPS 根证书安装</title><style>"
                + "body{font-family:-apple-system,'Microsoft YaHei',sans-serif;margin:0;background:#f5f6f8;color:#333}"
                + ".wrap{max-width:760px;margin:0 auto;padding:24px 16px 48px}"
                + "h1{font-size:20px;margin:0 0 8px}p{line-height:1.8;font-size:14px}"
                + ".card{background:#fff;border-radius:8px;padding:20px;margin:16px 0;box-shadow:0 1px 4px rgba(0,0,0,.06)}"
                + ".btn{display:inline-block;background:#1890ff;color:#fff;text-decoration:none;padding:10px 18px;"
                + "border-radius:6px;font-size:14px;margin:6px 8px 6px 0}"
                + ".btn.ghost{background:#fff;color:#1890ff;border:1px solid #1890ff}"
                + "h2{font-size:16px;margin:0 0 10px}li{line-height:1.9;font-size:14px}"
                + ".warn{color:#d4380d}code{background:#f0f2f5;padding:2px 6px;border-radius:4px}"
                + "</style></head><body><div class=\"wrap\">"
                + "<h1>HTTPS 根证书安装</h1>"
                + "<p>本系统的 HTTPS 证书由服务器自建根证书签发。每台设备只需安装一次根证书（授权文件），"
                + "之后访问 <code>" + httpsUrl + "</code> 将不再提示“不安全”，无需重复操作。</p>"
                + exist
                + "<div class=\"card\"><h2>1. 下载根证书</h2>"
                + "<a class=\"btn\" href=\"" + base + "/cert/" + ROOT_CA_FILE + "\">下载根证书 jsh-root-ca.crt</a>"
                + "<a class=\"btn ghost\" href=\"" + base + "/cert/install-windows.bat\">Windows 一键安装（需管理员）</a>"
                + "</div>"
                + "<div class=\"card\"><h2>2. Windows 电脑安装</h2><ul>"
                + "<li>下载 <code>jsh-root-ca.crt</code> 后双击，点“安装证书”</li>"
                + "<li>存储位置选“当前用户”或“本地计算机”，再选“将所有的证书都放入下列存储”</li>"
                + "<li>浏览并选择“受信任的根证书颁发机构”，完成后重启浏览器</li>"
                + "<li>或直接运行下载的 <code>install-windows.bat</code>，按提示允许管理员权限</li>"
                + "</ul></div>"
                + "<div class=\"card\"><h2>3. 安卓手机安装</h2><ul>"
                + "<li>用手机浏览器打开本页面，下载 <code>jsh-root-ca.crt</code></li>"
                + "<li>设置 → 安全（或“密码与安全”）→ 加密与凭据 → 安装证书 → CA 证书</li>"
                + "<li>选择刚下载的文件，按提示确认（部分机型需先设置锁屏密码）</li>"
                + "</ul></div>"
                + "<div class=\"card\"><h2>4. iPhone / iPad 安装</h2><ul>"
                + "<li>用 Safari 打开本页面，下载根证书并允许下载描述文件</li>"
                + "<li>设置 → 已下载描述文件 → 安装</li>"
                + "<li>设置 → 通用 → 关于本机 → 证书信任设置 → 对“JSH ERP Root CA”开启完全信任</li>"
                + "</ul></div>"
                + "<div class=\"card\"><h2>5. 安装完成后</h2>"
                + "<p>重启浏览器，访问 <code>" + httpsUrl + "</code> 验证不再出现证书警告。"
                + "若服务器 IP 发生变化，管理员在服务器执行 <code>.\\https-cert.ps1 -RenewServer</code> 重新签发即可，客户端无需重装。</p>"
                + "</div></div></body></html>";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/html;charset=UTF-8"));
        return new ResponseEntity<>(html, headers, HttpStatus.OK);
    }

    @GetMapping("/" + ROOT_CA_FILE)
    public ResponseEntity<byte[]> downloadRootCa() throws IOException {
        File file = rootCaFile();
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        byte[] data = Files.readAllBytes(file.toPath());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-x509-ca-cert"));
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + ROOT_CA_FILE + "\"");
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    @GetMapping("/install-windows.bat")
    public ResponseEntity<String> downloadWindowsInstaller(HttpServletRequest request) {
        String url = httpBase(request) + "/cert/" + ROOT_CA_FILE;
        String bat = "@echo off\r\n"
                + "setlocal\r\n"
                + "echo Downloading root certificate ...\r\n"
                + "powershell -NoProfile -Command \"Invoke-WebRequest -UseBasicParsing -Uri '" + url + "' -OutFile $env:TEMP\\jsh-root-ca.crt\"\r\n"
                + "if not exist \"%TEMP%\\jsh-root-ca.crt\" (echo Download failed & pause & exit /b 1)\r\n"
                + "echo Installing to Trusted Root Certification Authorities (needs administrator) ...\r\n"
                + "powershell -NoProfile -Command \"Start-Process -FilePath certutil -ArgumentList '-addstore','Root','%TEMP%\\jsh-root-ca.crt' -Verb RunAs -Wait\"\r\n"
                + "echo Done. Please restart your browser.\r\n"
                + "pause\r\n";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/octet-stream"));
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"install-windows.bat\"");
        return new ResponseEntity<>(bat, headers, HttpStatus.OK);
    }
}
