// PWA 最小 Service Worker：仅用于满足「添加到主屏幕」的安装条件。
// 不缓存任何资源，也不拦截请求；否则网络层失败（如证书未受信任）会被包装成
// “FetchEvent.respondWith received an error”，掩盖真实错误。
self.addEventListener('install', function () {
  self.skipWaiting();
});

self.addEventListener('activate', function (event) {
  event.waitUntil(self.clients.claim());
});

// 注册一个空的 fetch 监听即可满足安装条件，不调用 respondWith，交给浏览器原生处理
self.addEventListener('fetch', function () {
});
