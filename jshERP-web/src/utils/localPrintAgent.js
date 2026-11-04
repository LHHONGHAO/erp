/**
 * 本地打印助手通信
 *
 * 浏览器不允许网页枚举本机打印机，需在电脑上运行 tools/local-print-agent 的打印助手。
 * 助手监听 http://127.0.0.1:17890（被占用时自动顺延到 17891/17892），提供：
 *   GET  /ping      健康检查，返回版本与实际端口
 *   GET  /printers  返回本机打印机名称列表
 *   POST /print     接收标签图片并直接打印（无系统对话框）
 */
export const LOCAL_AGENT_PORT_RANGE = [17890, 17891, 17892]
export const LOCAL_PRINTER_PREFIX = 'local::'

/** 助手安装包下载地址（部署在远程主机静态目录） */
export const LOCAL_AGENT_DOWNLOAD_URL =
  (location.protocol === 'https:' ? 'https://' : 'http://') +
  location.hostname + (location.port ? ':' + location.port : '') +
  '/print-agent/print-agent.exe'

/** 探测成功后缓存的实际端口，避免每次都扫一遍 */
let resolvedBase = null

/** 选择的值是否为本地打印机 */
export function isLocalPrinter(value) {
  return typeof value === 'string' && value.indexOf(LOCAL_PRINTER_PREFIX) === 0
}

/** 取本地打印机的真实名称 */
export function localPrinterName(value) {
  return isLocalPrinter(value) ? value.slice(LOCAL_PRINTER_PREFIX.length) : value
}

function once(base, path, options, ms) {
  const controller = typeof AbortController !== 'undefined' ? new AbortController() : null
  const timer = controller ? setTimeout(() => controller.abort(), ms) : null
  const opt = Object.assign({}, options, controller ? { signal: controller.signal } : {})
  return fetch(base + path, opt).then(res => res.json()).finally(() => {
    if (timer) clearTimeout(timer)
  })
}

/**
 * 依次探测端口，返回第一个可用的助手地址。
 * 全部不可用时返回 null。
 */
export function detectLocalAgent(timeout) {
  const ms = timeout || 1500
  if (resolvedBase) {
    return once(resolvedBase, '/ping', { method: 'GET' }, ms)
      .then(res => (res && res.code === 200 && res.app === 'jsh-print-agent') ? resolvedBase : null)
      .catch(() => { resolvedBase = null; return null })
  }
  const tryPort = (idx) => {
    if (idx >= LOCAL_AGENT_PORT_RANGE.length) return Promise.resolve(null)
    const base = 'http://127.0.0.1:' + LOCAL_AGENT_PORT_RANGE[idx]
    return once(base, '/ping', { method: 'GET' }, ms)
      .then(res => {
        if (res && res.code === 200 && res.app === 'jsh-print-agent') {
          resolvedBase = base
          return base
        }
        return tryPort(idx + 1)
      })
      .catch(() => tryPort(idx + 1))
  }
  return tryPort(0)
}

function request(path, options, timeout) {
  return detectLocalAgent().then(base => {
    if (!base) throw new Error('本地打印助手未运行')
    return once(base, path, options, timeout || 2500)
  })
}

/** 探测助手并获取本机打印机列表；未启动助手时返回空数组 */
export function fetchLocalPrinters() {
  return request('/printers', { method: 'GET' })
    .then(res => (res && res.code === 200 && Array.isArray(res.data)) ? res.data : [])
    .catch(() => [])
}

/** 发送标签图片到本地打印机 */
export function printToLocalPrinter(payload) {
  return request('/print', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  }, 20000)
}
