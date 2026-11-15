/**
 * 前端版本检查
 *
 * 服务器更新前端后，浏览器（尤其 iOS 添加到主屏幕的 PWA）可能继续使用缓存里的旧入口页，
 * 导致一直运行旧代码。这里在应用启动和登录成功时主动拉取一次最新的 index.html，
 * 比对当前运行的前端包文件名；不一致说明服务器已更新，用带版本参数的地址重新加载，
 * 从而绕开旧入口页的缓存。
 */
const TARGET_KEY = 'jsh.appVersion.target'
const LAST_CHECK_KEY = 'jsh.appVersion.lastCheck'
const CHECK_INTERVAL = 60 * 1000

function readSessionValue(key) {
  try { return window.sessionStorage.getItem(key) } catch (e) { return null }
}

function writeSessionValue(key, value) {
  try { window.sessionStorage.setItem(key, value) } catch (e) { /* Safari private/restricted contexts */ }
}

function currentVersionParam() {
  const match = /(?:^|&)v=([^&]*)/.exec(window.location.search.replace(/^\?/, ''))
  if (!match) return ''
  try { return decodeURIComponent(match[1]) } catch (e) { return match[1] }
}

function versionedCurrentUrl(version) {
  const search = window.location.search
  const encoded = encodeURIComponent(version)
  const nextSearch = /(?:^|[?&])v=/.test(search)
    ? search.replace(/([?&])v=[^&]*/, '$1v=' + encoded)
    : search + (search ? '&' : '?') + 'v=' + encoded
  return window.location.pathname + nextSearch + window.location.hash
}

/** 当前正在运行的入口脚本，如 js/app.537da6bb.js */
function currentBundle() {
  const script = document.querySelector('script[src*="/js/app."]')
  if (!script) return ''
  const matched = /js\/app\.[a-z0-9]+\.js/i.exec(script.src)
  return matched ? matched[0] : ''
}

/** 最新入口脚本：直接取服务器上 index.html 引用的那个 */
function latestBundle(html) {
  const all = html.match(/js\/app\.[a-z0-9]+\.js/gi)
  return all && all.length ? all[all.length - 1] : ''
}

export default function checkAppVersion() {
  const running = currentBundle()
  if (!running) return Promise.resolve(false)

  const now = Date.now()
  const last = Number(readSessionValue(LAST_CHECK_KEY) || 0)
  if (now - last < CHECK_INTERVAL) return Promise.resolve(false)
  writeSessionValue(LAST_CHECK_KEY, String(now))

  const base = process.env.BASE_URL || '/'
  return fetch(base + 'index.html?__v=' + now, { cache: 'no-store' })
    .then(res => (res.ok ? res.text() : ''))
    .then(html => {
      const latest = latestBundle(html)
      if (!latest || latest === running) return false
      // 已因该版本重载过却仍是旧包，说明拿到的还是缓存页面，停止重载避免死循环
      const version = latest.replace(/^.*app\./, '').replace(/\.js$/, '')
      if (readSessionValue(TARGET_KEY) === latest || currentVersionParam() === version) return false
      writeSessionValue(TARGET_KEY, latest)
      location.replace(versionedCurrentUrl(version))
      return true
    })
    .catch(() => false)
}
