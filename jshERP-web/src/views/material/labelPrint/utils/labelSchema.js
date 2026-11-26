/**
 * 标签模板公共常量与毫米/像素换算
 *
 * 约定：
 *   - 模板坐标/尺寸统一用毫米（x、y、width、height）
 *   - 字号沿用像素（编辑与打印一致），不参与毫米换算
 *   - 模板只保存数据源标识、字段绑定与布局，不保存业务数据、不写 SQL
 */

export var PX_PER_MM = 3.779528
export var CODE_KEY_PREFIX = 'code__'

function round2(value) {
  return Math.round((Number(value) || 0) * 100) / 100
}

export function pxToMm(px) {
  return round2((Number(px) || 0) / PX_PER_MM)
}

export function mmToPx(mm) {
  return Math.round((Number(mm) || 0) * PX_PER_MM)
}

/** 估算文本像素宽：全角字符按 1em，半角按 0.5em */
export function estimateTextWidth(text, fontSize) {
  var fs = Number(fontSize) || 12
  var width = 0
  var textStr = text == null ? '' : String(text)
  for (var i = 0; i < textStr.length; i++) {
    var code = textStr.charCodeAt(i)
    var fullWidth = code >= 0x2e80 || (code >= 0x2010 && code <= 0x203a)
    width += fullWidth ? fs : fs * 0.5
  }
  return width
}

/**
 * 间隔符输出宽度：固定铺满整个标签宽度（左右边框到边框）
 * 编辑器、预览、打印共用同一规则，保证所见即所得
 * @param labelWidth 标签宽度（px）
 */
export function resolveSeparatorWidth(labelWidth) {
  return Math.max(20, Number(labelWidth) || 320)
}

// 间隔符字宽实测上下文：与打印 canvas 相同字体（sans-serif），保证铺满精度
var separatorMeasureCtx = null
function measureSymbolWidth(sym, fontSize, fontWeight, letterSpacing) {
  var fs = Number(fontSize) || 12
  var ls = Number(letterSpacing) || 0
  if (separatorMeasureCtx === null) {
    try {
      separatorMeasureCtx = typeof document !== 'undefined' && document.createElement
        ? document.createElement('canvas').getContext('2d')
        : false
    } catch (e) {
      separatorMeasureCtx = false
    }
  }
  if (separatorMeasureCtx) {
    separatorMeasureCtx.font = (fontWeight && fontWeight !== 'normal' ? fontWeight + ' ' : '') + fs + 'px sans-serif'
    var measured = separatorMeasureCtx.measureText(sym).width
    if (measured > 0) return measured + ls * sym.length
  }
  // 无 canvas 环境兜底：按全角/半角估算并放大 3%
  return (estimateTextWidth(sym, fs) + ls * sym.length) * 1.03
}

/**
 * 间隔符填充：把符号按元素宽度重复铺满（长度随宽自动调节，字号随样式）
 * @param symbol 间隔符符号（可为多字符，如 '- -'）
 * @param widthPx 元素宽度（px）
 * @param fontSize 字号（px）
 * @param letterSpacing 字间距（px）
 * @param fontWeight 字重（实测字宽时使用）
 */
export function fillSeparatorText(symbol, widthPx, fontSize, letterSpacing, fontWeight) {
  var sym = symbol == null ? '' : String(symbol)
  if (!sym) return ''
  var width = Number(widthPx) || 0
  if (width <= 0) return sym
  var symWidth = measureSymbolWidth(sym, fontSize, fontWeight, letterSpacing)
  if (symWidth <= 0) return sym
  // 多排一个符号：预览/打印对超宽部分裁剪，确保虚线触及右边框
  var count = Math.floor(width / symWidth) + 1
  if (count < 1) count = 1
  if (count > 500) count = 500
  var out = ''
  for (var i = 0; i < count; i++) out += sym
  return out
}

