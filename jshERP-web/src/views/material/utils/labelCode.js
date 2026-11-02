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

// 条码/二维码作为独立元素存放时的 key 前缀：code__<属性key>
export var CODE_KEY_PREFIX = 'code__'

// 是否为独立的编码元素
export function isCodeKey(key) {
  return String(key == null ? '' : key).indexOf(CODE_KEY_PREFIX) === 0
}

// 编码元素对应的属性 key
export function codeSourceKey(key) {
  return isCodeKey(key) ? String(key).slice(CODE_KEY_PREFIX.length) : ''
}

// 编码元素显示的属性名 + 属性值（属性名留空时只显示值）
export function codeTextOf(attr, labels) {
  if (!attr) return ''
  var label = labels && Object.prototype.hasOwnProperty.call(labels, attr.key) ? labels[attr.key] : (attr.label || '')
  var value = attr.value == null ? '' : String(attr.value)
  return label ? (label + ': ' + value) : value
}

/**
 * 旧模板迁移：把「文字与条码合在一个元素里」的样式拆成两个独立元素
 *
 * 旧格式：attrStyles[属性key] = { x, y, width, height, codeType, fontSize, ... }
 * 新格式：attrStyles[属性key] 只保留文字；attrStyles['code__'+属性key] = { x, y, width, height, codeType }
 *
 * @param {Object} styles   属性样式表
 * @param {Array}  allAttrs 当前全部属性（用于按属性值估算文字高度）
 * @param {Object} labels   属性名表
 * @returns {Object} 迁移后的样式表（不修改入参）
 */
export function migrateCodeStyles(styles, allAttrs, labels) {
  var next = {}
  Object.keys(styles || {}).forEach(function(key) {
    next[key] = JSON.parse(JSON.stringify(styles[key]))
  })
  Object.keys(next).forEach(function(key) {
    var s = next[key]
    if (!s || !s.codeType || isCodeKey(key)) return
    var attr = (allAttrs || []).find(function(a) { return a.key === key })
    var fontSize = s.fontSize || 14
    var lineHeight = s.lineHeight || 1.3
    var text = codeTextOf(attr, labels)
    var lines = text ? String(text).split('\n').length : 0
    var textBoxHeight = lines ? Math.round(lines * fontSize) + 2 : 0
    var textGap = lines ? Math.round((lines * fontSize * (lineHeight - 1)) / 3) : 0
    var textHeight = textBoxHeight + textGap
    var isQr = s.codeType === 'qrcode'
    var boxWidth = s.width || (isQr ? DEFAULT_QR_SIZE : DEFAULT_BARCODE_WIDTH)
    var imgHeight = s.height ? Math.max(20, s.height - textHeight) : (isQr ? DEFAULT_QR_SIZE : DEFAULT_BARCODE_HEIGHT)
    next[CODE_KEY_PREFIX + key] = {
      x: s.x || 0,
      y: (s.y || 0) + textHeight,
      width: boxWidth,
      height: imgHeight,
      codeType: s.codeType
    }
    delete next[key].codeType
    delete next[key].width
    delete next[key].height
  })
  return next
}

// 标签打印机 203dpi（8 点/mm），96dpi 像素与打印点的换算比例，需与后端 LabelPrinterService 保持一致
export var PX_TO_DOT = (8 * 25.4) / 96
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
    // cell 一并返回，后端直接使用，避免后端用另一套模块数重算导致差 1 个点
    return { width: side, height: side, cell: cell }
  }
  if (codeType === 'barcode') {
    var barCount = barcodeModules(value)
    if (!barCount) return { width: w, height: h }
    var dots = (w || DEFAULT_BARCODE_WIDTH) * PX_TO_DOT
    var narrow = Math.max(1, Math.min(BARCODE_MAX_NARROW, Math.floor(dots / barCount)))
    // narrow 一并返回，后端直接使用，避免后端重算取整后宽度成倍变小
    return { width: (barCount * narrow) / PX_TO_DOT, height: h, narrow: narrow }
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
    // 位图宽高比与显示框一致，避免 CSS contain 缩放后出现留白（预览宽度与实际打印不符）
    var modules = barcodeModules(value)
    var scale = 4
    var bitmapWidth = modules ? modules * scale : Math.round((width || DEFAULT_BARCODE_WIDTH) * scale)
    var bitmapHeight = Math.max(40, Math.round(bitmapWidth * ((height || DEFAULT_BARCODE_HEIGHT) / (width || DEFAULT_BARCODE_WIDTH))))
    return new Promise(function(resolve) {
      try {
        var canvas = document.createElement('canvas')
        JsBarcode(canvas, value, {
          format: 'CODE128',
          displayValue: false,
          width: scale,
          height: bitmapHeight,
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
