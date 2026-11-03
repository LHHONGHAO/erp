/**
 * 本地打印助手通信
 *
 * 浏览器不允许网页枚举本机打印机，需在电脑上运行 tools/local-print-agent 的打印助手。
 * 助手监听 http://127.0.0.1:17890，提供：
 *   GET  /printers  返回本机打印机名称列表
 *   POST /print     接收标签图片并直接打印（无系统对话框）
 */
export const LOCAL_AGENT_BASE = 'http://127.0.0.1:17890'
export const LOCAL_PRINTER_PREFIX = 'local::'

/** 选择的值是否为本地打印机 */
export function isLocalPrinter(value) {
  return typeof value === 'string' && value.indexOf(LOCAL_PRINTER_PREFIX) === 0
}

/** 取本地打印机的真实名称 */
export function localPrinterName(value) {
  return isLocalPrinter(value) ? value.slice(LOCAL_PRINTER_PREFIX.length) : value
}

function request(path, options, timeout) {
  const ms = timeout || 2500
  const controller = typeof AbortController !== 'undefined' ? new AbortController() : null
  const timer = controller ? setTimeout(() => controller.abort(), ms) : null
  const opt = Object.assign({}, options, controller ? { signal: controller.signal } : {})
  return fetch(LOCAL_AGENT_BASE + path, opt).then(res => res.json()).finally(() => {
    if (timer) clearTimeout(timer)
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
