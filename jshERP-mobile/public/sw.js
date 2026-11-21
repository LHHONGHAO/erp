// 手机端 Service Worker：仅满足 PWA 安装条件，不缓存资源、不拦截请求，
// 避免把网络层失败（如证书未受信任）包装成 respondWith 错误。
// SW_VER 20261120c：内容变更触发线上旧 SW 更新，旧图标缓存随新 SW 失效。
const SW_VERSION = '20261120c';

self.addEventListener('install', function () {
  self.skipWaiting();
});

self.addEventListener('activate', function (event) {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', function () {
});
