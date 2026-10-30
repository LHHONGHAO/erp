/**
 * 打印机搜索与连接工具（手机浏览器端）
 *
 * 支持四种打印机类型：
 *   1. bluetooth  蓝牙打印机（Web Bluetooth API）
 *   2. wifi       Wi-Fi 打印机（IPP 协议探测，HTTP fetch）
 *   3. network    网络共享打印机（SMB/IPP，同网段探测）
 *   4. cloud      云打印机（浏览器打印对话框 / 云打印服务）
 *
 * 统一打印机对象：
 *   { id, name, type, address, status, capabilities }
 */

/** 打印机类型定义 */
export const PRINTER_TYPES = {
  bluetooth: { key: 'bluetooth', label: '蓝牙打印机', icon: 'bluetooth', color: '#1890ff' },
  wifi: { key: 'wifi', label: 'Wi-Fi 打印机', icon: 'wifi', color: '#52c41a' },
  network: { key: 'network', label: '网络共享打印机', icon: 'share-alt', color: '#faad14' },
  cloud: { key: 'cloud', label: '云打印机', icon: 'cloud', color: '#722ed1' }
}

/** 蓝牙打印机相关 Service UUID（常见标签打印机使用的 GATT 服务） */
const BT_PRINTER_SERVICES = [
  '000018f0-0000-1000-8000-00805f9b34fb', // 通用打印服务
  'e7810a71-73ae-499d-8c15-faa9aef0c3f2', // ESC/POS 打印服务
  '0000ae30-0000-1000-8000-00805f9b34fb'  // 厂商自定义打印服务
]

/** IPP 默认端口 */
const IPP_PORT = 631
/** 原始打印端口（JetDirect / Port 9100） */
const RAW_PORT = 9100

/**
 * 检测当前浏览器是否支持 Web Bluetooth
 */
export function isBluetoothSupported () {
  return !!(navigator.bluetooth && typeof navigator.bluetooth.requestDevice === 'function')
}

/**
 * 检测当前浏览器是否支持网络打印（IPP via fetch）
 */
export function isNetworkPrintSupported () {
  return typeof fetch === 'function'
}

/**
 * 生成统一的打印机 ID
 */
function genPrinterId (type, address) {
  return type + '_' + String(address).replace(/[^a-zA-Z0-9]/g, '_')
}

/* ============================================================
 *  1. 蓝牙打印机搜索
 * ============================================================ */

/**
 * 搜索蓝牙打印机
 * 注意：Web Bluetooth 必须由用户手势触发，会弹出系统设备选择对话框
 * @returns {Promise<Object>} 统一打印机对象
 */
export function searchBluetoothPrinter () {
  return new Promise((resolve, reject) => {
    if (!isBluetoothSupported()) {
      reject(new Error('当前浏览器不支持蓝牙，请使用 Chrome for Android'))
      return
    }
    const options = {
      acceptAllDevices: true,
      optionalServices: BT_PRINTER_SERVICES
    }
    navigator.bluetooth.requestDevice(options).then(device => {
      resolve({
        id: genPrinterId('bluetooth', device.id),
        name: device.name || '未知蓝牙打印机',
        type: 'bluetooth',
        address: device.id,
        status: 'available',
        device: device,
        capabilities: { protocol: 'ble', services: BT_PRINTER_SERVICES }
      })
    }).catch(err => {
      if (err && err.name === 'NotFoundError') {
        reject(new Error('已取消蓝牙设备选择'))
      } else {
        reject(err)
      }
    })
  })
}

/* ============================================================
 *  2. Wi-Fi 打印机搜索（IPP 协议）
 * ============================================================ */

/**
 * 探测指定 IP 是否为支持 IPP 的 Wi-Fi 打印机
 * @param {string} ip 打印机 IP 地址
 * @returns {Promise<Object|null>} 打印机对象或 null
 */
export function probeWifiPrinter (ip) {
  const url = `http://${ip}:${IPP_PORT}/ipp/print`
  return fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/ipp' },
    body: buildIppGetPrinterAttributes(),
    mode: 'cors'
  }).then(res => {
    if (res.status === 200 || res.status === 401 || res.status === 403) {
      return {
        id: genPrinterId('wifi', ip),
        name: `Wi-Fi 打印机 (${ip})`,
        type: 'wifi',
        address: ip,
        status: 'available',
        capabilities: { protocol: 'ipp', port: IPP_PORT, url }
      }
    }
    return null
  }).catch(() => null)
}

/**
 * 搜索同网段内的 Wi-Fi 打印机
 * 浏览器无法做 UDP 广播发现，这里探测常见网关网段的 .1~.254
 * @param {string} network 网段前缀，如 "192.168.1"
 * @param {number} timeout 单 IP 超时 ms
 * @returns {Promise<Array>} 找到的打印机列表
 */
export function searchWifiPrinters (network, timeout = 1500) {
  const prefix = network || guessNetworkPrefix()
  if (!prefix) return Promise.resolve([])
  const ips = []
  for (let i = 1; i <= 254; i++) ips.push(prefix + '.' + i)
  return Promise.all(
    ips.map(ip => Promise.race([
      probeWifiPrinter(ip),
      new Promise(resolve => setTimeout(() => resolve(null), timeout))
    ]))
  ).then(results => results.filter(r => r !== null))
}

/**
 * 推测本机所在网段（取常见私有网段）
 */
function guessNetworkPrefix () {
  // 浏览器无法获取本机 IP，返回常见网段供用户选择
  return '192.168.1'
}

/* ============================================================
 *  3. 网络共享打印机搜索
 * ============================================================ */

/**
 * 探测网络共享打印机（尝试 IPP + 原始端口）
 * @param {string} ip 共享主机 IP
 * @param {string} shareName 共享名（可选）
 * @returns {Promise<Object|null>}
 */
export function probeNetworkPrinter (ip, shareName) {
  const targets = [
    `http://${ip}:${IPP_PORT}/ipp/print`,
    `http://${ip}:${RAW_PORT}`
  ]
  if (shareName) targets.push(`http://${ip}:${IPP_PORT}/printers/${shareName}`)
  return Promise.all(targets.map(url =>
    fetch(url, { method: 'POST', mode: 'cors' }).then(res => {
      if (res.status === 200 || res.status === 401 || res.status === 403 || res.status === 400) return url
      return null
    }).catch(() => null)
  )).then(results => {
    const ok = results.find(r => r !== null)
    if (!ok) return null
    return {
      id: genPrinterId('network', ip + (shareName || '')),
      name: shareName ? `共享打印机 ${shareName} (${ip})` : `网络打印机 (${ip})`,
      type: 'network',
      address: ip,
      shareName: shareName || '',
      status: 'available',
      capabilities: { protocol: ok.indexOf(String(IPP_PORT)) > -1 ? 'ipp' : 'raw', url: ok }
    }
  })
}

/**
 * 搜索网络共享打印机（同网段探测）
 * @param {string} network 网段前缀
 * @returns {Promise<Array>}
 */
export function searchNetworkPrinters (network) {
  return searchWifiPrinters(network).then(list =>
    list.map(p => Object.assign({}, p, { type: 'network', id: genPrinterId('network', p.address) }))
  )
}

/* ============================================================
 *  4. 云打印机搜索
 * ============================================================ */

/**
 * 检测云打印能力
 * 浏览器本身无法枚举云打印机，云打印机通过系统打印对话框选择
 * @returns {Object} 云打印入口描述
 */
export function getCloudPrinterInfo () {
  const hasPrint = typeof window !== 'undefined' && typeof window.print === 'function'
  return {
    id: genPrinterId('cloud', 'system'),
    name: '云打印机 / 系统打印机',
    type: 'cloud',
    address: 'system',
    status: hasPrint ? 'available' : 'unavailable',
    capabilities: { protocol: 'browser', note: '通过浏览器打印对话框选择已配置的云打印机' }
  }
}

/**
 * 使用云打印（调用浏览器打印对话框）
 * @param {Function} printFn 实际执行打印的函数（接收 iframe 或 HTML）
 */
export function printViaCloud (printFn) {
  if (typeof printFn === 'function') {
    return printFn()
  }
  window.print()
  return Promise.resolve()
}

/* ============================================================
 *  IPP 报文构造（Get-Printer-Attributes）
 * ============================================================ */

/**
 * 构造 IPP Get-Printer-Attributes 请求体
 * 用于探测打印机是否支持 IPP 协议
 */
function buildIppGetPrinterAttributes () {
  // IPP 1.1 Get-Printer-Attributes 操作
  const version = [0x01, 0x01] // IPP/1.1
  const opId = [0x00, 0x00, 0x00, 0x0b] // Get-Printer-Attributes = 0x000b
  const reqId = [0x00, 0x00, 0x00, 0x01]
  const opAttrTag = [0x01] // operation-attributes-tag
  // charset: 'utf-8'
  const charsetAttr = [
    0x47, // value-tag: charset
    0x00, 0x12, // name length (18)
    0x61, 0x74, 0x74, 0x72, 0x69, 0x62, 0x75, 0x74, 0x65, 0x73, 0x2d, 0x63, 0x68, 0x61, 0x72, 0x73, 0x65, 0x74, // 'attributes-charset'
    0x00, 0x05, // value length (5)
    0x75, 0x74, 0x66, 0x2d, 0x38 // 'utf-8'
  ]
  // natural-language: 'en'
  const langAttr = [
    0x48, // value-tag: naturalLanguage
    0x00, 0x1b, // name length (27)
    0x61, 0x74, 0x74, 0x72, 0x69, 0x62, 0x75, 0x74, 0x65, 0x73, 0x2d, 0x6e, 0x61, 0x74, 0x75, 0x72, 0x61, 0x6c, 0x2d, 0x6c, 0x61, 0x6e, 0x67, 0x75, 0x61, 0x67, 0x65, // 'attributes-natural-language'
    0x00, 0x02, // value length (2)
    0x65, 0x6e // 'en'
  ]
  const endAttr = [0x03] // end-of-attributes-tag
  return new Uint8Array([
    ...version, ...opId, ...reqId,
    ...opAttrTag, ...charsetAttr, ...langAttr, ...endAttr
  ]).buffer
}

/* ============================================================
 *  综合搜索
 * ============================================================ */

/**
 * 搜索所有类型的打印机
 * @param {Object} options { network, includeCloud }
 * @returns {Promise<{bluetooth: Array, wifi: Array, network: Array, cloud: Array}>}
 */
export function searchAllPrinters (options) {
  options = options || {}
  const tasks = {
    wifi: options.network ? searchWifiPrinters(options.network) : Promise.resolve([]),
    network: options.network ? searchNetworkPrinters(options.network) : Promise.resolve([]),
    cloud: Promise.resolve(options.includeCloud === false ? [] : [getCloudPrinterInfo()])
  }
  return Promise.all([tasks.wifi, tasks.network, tasks.cloud]).then(([wifi, network, cloud]) => ({
    bluetooth: [], // 蓝牙需用户手势触发，不在批量搜索中返回
    wifi: wifi,
    network: network,
    cloud: cloud
  }))
}

/**
 * 检测各类型在当前浏览器的可用性
 */
export function getPrinterCapabilities () {
  return {
    bluetooth: isBluetoothSupported(),
    wifi: isNetworkPrintSupported(),
    network: isNetworkPrintSupported(),
    cloud: typeof window !== 'undefined' && typeof window.print === 'function'
  }
}
