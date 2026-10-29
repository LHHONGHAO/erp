/**
 * 标签条码/二维码生成
 *
 * 统一输出 dataURL：预览 与 浏览器兜底打印 使用同一份图像；
 * 后端打印不走图片，直接下发 TSPL 的 BARCODE / QRCODE 硬件指令。
 */
import QRCode from 'qrcode'
import JsBarcode from 'jsbarcode'

var DEFAULT_QR_SIZE = 80
var DEFAULT_BARCODE_WIDTH = 160
var DEFAULT_BARCODE_HEIGHT = 50

// 标签打印机 203dpi（8 点/mm），96dpi 像素与打印点的换算比例，需与后端 LabelPrinterService 保持一致
var PX_TO_DOT = (8 * 25.4) / 96
var BARCODE_MAX_NARROW = 4
var QR_MAX_CELL = 10

// 模块数缓存，避免每次布局重复编码
var moduleCache = {}

function barcodeModules(value) {
  var key = 'barcode:' + value
  if (moduleCache[key] != null) return moduleCache[key]
  var modules = 0
  try {
    var canvas = document.createElement('canvas')
    JsBarcode(canvas, value, { format: 'CODE128', displayValue: false, width: 1, height: 10, margin: 0 })
    modules = canvas.width || 0
  } catch (e) {
    modules = 0
  }
  moduleCache[key] = modules
  return modules
}

function qrModules(value) {
  var key = 'qr:' + value
  if (moduleCache[key] != null) return moduleCache[key]
  var modules = 0
  try {
    modules = QRCode.create(value, { errorCorrectionLevel: 'M' }).modules.size || 0
  } catch (e) {
    modules = 0
  }
  moduleCache[key] = modules
  return modules
}

/**
 * 按打印机硬件能力换算实际可打印尺寸，保证预览与打印完全一致
 *
 * - 条码：硬件只能按「整数个模块」输出，宽度 = 模块数 × 窄条宽度
 * - 二维码：硬件只能输出正方形，边长 = 模块数 × 单元宽度
 *
 * @returns {{width: Number, height: Number}} 可打印尺寸（px）
 */
export function fitCodeSize(codeType, text, width, height) {
  var value = text == null ? '' : String(text)
  var w = width || 0
  var h = height || 0
  if (!value) return { width: w, height: h }
  if (codeType === 'qrcode') {
    var qrCount = qrModules(value)
    if (!qrCount) return { width: w, height: h }
    var sideDots = Math.min(w || DEFAULT_QR_SIZE, h || DEFAULT_QR_SIZE) * PX_TO_DOT
    var cell = Math.max(1, Math.min(QR_MAX_CELL, Math.floor(sideDots / qrCount)))
    var side = (cell * qrCount) / PX_TO_DOT
    return { width: side, height: side }
  }
  if (codeType === 'barcode') {
    var barCount = barcodeModules(value)
    if (!barCount) return { width: w, height: h }
    var dots = (w || DEFAULT_BARCODE_WIDTH) * PX_TO_DOT
    var narrow = Math.max(1, Math.min(BARCODE_MAX_NARROW, Math.floor(dots / barCount)))
    return { width: (barCount * narrow) / PX_TO_DOT, height: h }
  }
  return { width: w, height: h }
}

/**
 * 生成条码/二维码图片
 *
 * @param {String} codeType 'barcode' | 'qrcode'
 * @param {String} text     编码内容
 * @param {Number} width    显示宽度（px）
 * @param {Number} height   显示高度（px）
 * @returns {Promise<String>} dataURL，无法编码时返回空串
 */
export function renderCodeDataUrl(codeType, text, width, height) {
  var value = text == null ? '' : String(text)
  if (!value) return Promise.resolve('')
  if (codeType === 'qrcode') {
    // 按显示尺寸放大 4 倍生成，保证打印清晰
    var side = Math.max(80, Math.round(Math.min(width || DEFAULT_QR_SIZE, height || DEFAULT_QR_SIZE) * 4))
    return QRCode.toDataURL(value, { margin: 0, width: side, errorCorrectionLevel: 'M' })
      .catch(function() { return '' })
  }
  if (codeType === 'barcode') {
    return new Promise(function(resolve) {
      try {
        var canvas = document.createElement('canvas')
        JsBarcode(canvas, value, {
          format: 'CODE128',
          displayValue: false,
          width: 3,
          height: Math.max(60, Math.round((height || DEFAULT_BARCODE_HEIGHT) * 3)),
          margin: 0
        })
        resolve(canvas.toDataURL('image/png'))
      } catch (e) {
        // CODE128 只支持 ASCII，含中文等字符时无法编码
        resolve('')
      }
    })
  }
  return Promise.resolve('')
}
