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
 * 间隔符填充：把符号按元素宽度重复铺满（长度随宽自动调节，字号随样式）
 * @param symbol 间隔符符号（可为多字符，如 '- -'）
 * @param widthPx 元素宽度（px）
 * @param fontSize 字号（px）
 */
export function fillSeparatorText(symbol, widthPx, fontSize) {
  var sym = symbol == null ? '' : String(symbol)
  if (!sym) return ''
  var width = Number(widthPx) || 0
  if (width <= 0) return sym
  var symWidth = estimateTextWidth(sym, fontSize)
  if (symWidth <= 0) return sym
  var count = Math.floor(width / symWidth)
  if (count < 1) count = 1
  if (count > 500) count = 500
  var out = ''
  for (var i = 0; i < count; i++) out += sym
  return out
}

