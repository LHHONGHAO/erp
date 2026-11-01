/**
 * 本地根路径访问代理（HTTPS 统一入口）
 * 约定：对外只暴露 HTTPS 入口（https://<ip>:9999），后端仅在本机 127.0.0.1 提供 HTTP。
 *   - 访问 / 或其它路径时自动补 /jshERP-boot 前缀
 *   - 已带 /jshERP-boot 前缀的请求（前端静态资源、API、websocket）原样转发
 *   - /cert/install.html、/cert/jsh-erp.cer、/cert/jsh-erp.pfx 为证书安装说明与下载，由本代理直接返回
 * 配置固化在 D:\erp\deploy.config.json
 */
const https = require('https');
const http = require('http');
const fs = require('fs');
const path = require('path');

const cfg = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'deploy.config.json'), 'utf8'));
const LISTEN_PORT = cfg.proxyPort || 9999;
const TARGET_PORT = cfg.backendPort || 9998;
const PREFIX = cfg.contextPath || '/jshERP-boot';
const TLS = cfg.https || {};
const TLS_PFX = TLS.pfx || 'D:/erp/data/config/jsh-https.p12';
const TLS_PASS = TLS.passphrase || 'changeit';
const CERT_FILE = TLS.cer || 'D:/erp/data/config/jsh-erp.cer';
const INSTALL_PAGE = TLS.installPage || path.join(__dirname, 'cert-install.html');

if (!fs.existsSync(TLS_PFX)) {
  throw new Error('未找到 HTTPS 证书 ' + TLS_PFX + '，请先运行 tools/make-cert.ps1');
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.cer': 'application/x-x509-ca-cert',
  '.p12': 'application/x-pkcs12',
  '.pfx': 'application/x-pkcs12'
};

function sendFile(res, file, downloadName) {
  if (!fs.existsSync(file)) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('文件不存在: ' + path.basename(file));
    return;
  }
  const headers = {
    'Content-Type': MIME[path.extname(file)] || 'application/octet-stream',
    'Cache-Control': 'no-store'
  };
  if (downloadName) {
    headers['Content-Disposition'] = 'attachment; filename="' + downloadName + '"';
  }
  res.writeHead(200, headers);
  fs.createReadStream(file).pipe(res);
}

// 证书相关路由（不走后端）
function serveCertRoute(req, res) {
  if (req.method !== 'GET' && req.method !== 'HEAD') return false;
  const pathname = (req.url || '/').split('?')[0];
  if (pathname === '/cert/install.html' || pathname === '/cert/' || pathname === '/cert') {
    sendFile(res, INSTALL_PAGE);
    return true;
  }
  if (pathname === '/cert/jsh-erp.cer') {
    sendFile(res, CERT_FILE, 'jsh-erp.cer');
    return true;
  }
  if (pathname === '/cert/jsh-erp.pfx' || pathname === '/cert/jsh-erp.p12') {
    sendFile(res, TLS_PFX, 'jsh-erp.pfx');
    return true;
  }
  return false;
}

function normalize(url) {
  const pathname = url || '/';
  if (pathname === PREFIX || pathname.startsWith(PREFIX + '/') || pathname.startsWith(PREFIX + '?')) {
    return pathname;
  }
  return PREFIX + pathname;
}

const server = https.createServer({
  pfx: fs.readFileSync(TLS_PFX),
  passphrase: TLS_PASS
}, (req, res) => {
  if (serveCertRoute(req, res)) return;
  const target = normalize(req.url);
  const options = {
    host: '127.0.0.1',
    port: TARGET_PORT,
    path: target,
    method: req.method,
    headers: Object.assign({}, req.headers, { host: '127.0.0.1:' + TARGET_PORT })
  };
  const proxyReq = http.request(options, proxyRes => {
    const headers = Object.assign({}, proxyRes.headers);
    // HTML 入口禁止缓存，避免手机浏览器一直加载旧版本页面
    const contentType = headers['content-type'] || '';
    if (contentType.indexOf('text/html') !== -1) {
      headers['cache-control'] = 'no-store, no-cache, must-revalidate';
      headers['pragma'] = 'no-cache';
      headers['expires'] = '0';
      delete headers['etag'];
      delete headers['last-modified'];
    }
    res.writeHead(proxyRes.statusCode, headers);
    proxyRes.pipe(res);
  });
  proxyReq.on('error', err => {
    res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('代理请求失败: ' + err.message);
  });
  req.pipe(proxyReq);
});

// websocket 升级请求同样转发
server.on('upgrade', (req, socket, head) => {
  const target = normalize(req.url);
  const proxyReq = http.request({
    host: '127.0.0.1',
    port: TARGET_PORT,
    path: target,
    method: req.method,
    headers: Object.assign({}, req.headers, { host: '127.0.0.1:' + TARGET_PORT })
  });
  proxyReq.on('upgrade', (proxyRes, proxySocket, proxyHead) => {
    let headers = 'HTTP/1.1 101 Switching Protocols\r\n';
    Object.keys(proxyRes.headers).forEach(key => {
      headers += key + ': ' + proxyRes.headers[key] + '\r\n';
    });
    socket.write(headers + '\r\n');
    if (proxyHead && proxyHead.length) proxySocket.unshift(proxyHead);
    if (head && head.length) socket.unshift(head);
    proxySocket.pipe(socket);
    socket.pipe(proxySocket);
  });
  proxyReq.on('error', () => socket.destroy());
  proxyReq.end();
});

server.listen(LISTEN_PORT, '0.0.0.0', () => {
  console.log('https proxy listening on ' + LISTEN_PORT + ' -> http://127.0.0.1:' + TARGET_PORT);
});
