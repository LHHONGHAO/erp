/**
 * 打印机工具（手机浏览器端）
 *
 * 蓝牙打印机：Web Bluetooth 直接连接（仅 Chrome for Android）
 * Wi-Fi 打印机：手机浏览器不能打开原始打印端口（9100），也探测不到局域网设备，
 *   因此按「手动添加：名称 + IP + 端口」的方式配置，配置保存在浏览器本地；
 *   打印时由后端用 Socket 把 TSPL 指令直接发到 ip:port（/print/network）。
 * 云打印机：调用浏览器打印对话框。
 */
import { postAction } from '@/api/manage'

/** 打印机类型定义 */
export const PRINTER_TYPES = {
  bluetooth: { key: 'bluetooth', label: '蓝牙打印机', icon: 'bluetooth', color: '#1890ff' },
  wifi: { key: 'wifi', label: 'Wi-Fi 打印机', icon: 'wifi', color: '#52c41a' },
  cloud: { key: 'cloud', label: '云打印机', icon: 'cloud', color: '#722ed1' }
}

/** 蓝牙打印机相关 Service UUID（常见标签打印机使用的 GATT 服务） */
const BT_PRINTER_SERVICES = [
  '000018f0-0000-1000-8000-00805f9b34fb', // 通用打印服务
  'e7810a71-73ae-499d-8c15-faa9aef0c3f2', // ESC/POS 打印服务
  '0000ae30-0000-1000-8000-00805f9b34fb'  // 厂商自定义打印服务
]

/** 已配置的 Wi-Fi 打印机在 localStorage 中的键 */
const WIFI_PRINTER_KEY = 'jsh_wifi_printers'
/** 网络打印机默认端口（原始打印 / JetDirect） */
export const DEFAULT_PRINTER_PORT = 9100

/**
 * 检测当前浏览器是否支持 Web Bluetooth
 */
export function isBluetoothSupported () {
  return !!(navigator.bluetooth && typeof navigator.bluetooth.requestDevice === 'function')
}

/**
 * 搜索蓝牙打印机
 * 注意：Web Bluetooth 必须由用户手势触发，会弹出系统设备选择对话框
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
        id: 'bluetooth_' + device.id,
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
 *  Wi-Fi / 网络打印机：本地配置存储
 * ============================================================ */

/**
 * 已保存的 Wi-Fi 打印机列表
 * @returns {Array<{id,name,ip,port}>}
 */
export function getWifiPrinters () {
  try {
    const raw = localStorage.getItem(WIFI_PRINTER_KEY)
    const list = raw ? JSON.parse(raw) : []
    return Array.isArray(list) ? list : []
  } catch (e) {
    return []
  }
}

/** 生成打印机 ID（IP + 端口唯一） */
function genWifiId (ip, port) {
  return 'wifi_' + ip + '_' + port
}

/**
 * 保存（新增或更新）一台 Wi-Fi 打印机
 * @param {Object} printer { name, ip, port }
 * @returns {Array} 保存后的完整列表
 */
export function saveWifiPrinter (printer) {
  const ip = String(printer.ip || '').trim()
  const port = Number(printer.port) || DEFAULT_PRINTER_PORT
  const id = genWifiId(ip, port)
  const list = getWifiPrinters()
  const item = {
    id: id,
    name: String(printer.name || '').trim() || ip,
    ip: ip,
    port: port,
    type: 'wifi'
  }
  const idx = list.findIndex(p => p.id === id)
  if (idx >= 0) {
    list[idx] = item
  } else {
    list.push(item)
  }
  localStorage.setItem(WIFI_PRINTER_KEY, JSON.stringify(list))
  return list
}

/**
 * 删除一台 Wi-Fi 打印机
 * @param {string} id 打印机 ID
 * @returns {Array} 删除后的完整列表
 */
export function removeWifiPrinter (id) {
  const list = getWifiPrinters().filter(p => p.id !== id)
  localStorage.setItem(WIFI_PRINTER_KEY, JSON.stringify(list))
  return list
}

/**
 * 测试网络打印机连通性：由后端尝试 TCP 连接 ip:port
 * @returns {Promise<{connected:boolean,message:string}>}
 */
export function testWifiPrinter (ip, port) {
  return postAction('/print/networkTest', {
    ip: ip,
    port: Number(port) || DEFAULT_PRINTER_PORT
  }).then(res => ({
    connected: !!(res && res.code === 200),
    message: (res && res.data) || '连接失败'
  }))
}

/**
 * 打印到网络打印机：后端把 TSPL 指令通过 Socket 发送到 ip:port
 * @param {Object} printer { ip, port }
 * @param {Object} payload { copies, width, height, items }
 */
export function printToWifiPrinter (printer, payload) {
  return postAction('/print/network', Object.assign({
    ip: printer.ip,
    port: Number(printer.port) || DEFAULT_PRINTER_PORT
  }, payload))
}

/* ============================================================
 *  云打印机
 * ============================================================ */

/**
 * 检测云打印能力
 * 浏览器本身无法枚举云打印机，云打印机通过系统打印对话框选择
 */
export function getCloudPrinterInfo () {
  const hasPrint = typeof window !== 'undefined' && typeof window.print === 'function'
  return {
    id: 'cloud_system',
    name: '云打印机 / 系统打印机',
    type: 'cloud',
    address: 'system',
    status: hasPrint ? 'available' : 'unavailable',
    capabilities: { protocol: 'browser', note: '通过浏览器打印对话框选择已配置的云打印机' }
  }
}

/**
 * 检测各类型在当前浏览器的可用性
 */
export function getPrinterCapabilities () {
  return {
    bluetooth: isBluetoothSupported(),
    // Wi-Fi 打印机由后端 Socket 下发，浏览器端恒可用
    wifi: true,
    cloud: typeof window !== 'undefined' && typeof window.print === 'function'
  }
}
