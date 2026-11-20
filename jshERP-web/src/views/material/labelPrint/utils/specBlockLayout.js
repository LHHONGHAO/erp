/**
 * 规格块行布局（编辑器 / 打印预览 / 打印共用，唯一算法）
 *
 * 一个规格块 = 属性名 + N 个规格值，按 item 顺序流式排布：
 *   花色：被套 床单 枕套
 * 排布规则：
 *   1. item（名称 / 单个值）是原子文本单元，不拆分、不截断；
 *   2. 加入下一项会超出块宽时另起一行；
 *   3. 名称为 wrap=true 时，其后的属性值整体换到下一行；
 *   4. 最小宽度 = 块内全部名称宽度之和（名称不能被压坏），
 *      自然宽度 = 全部 item 单行总宽。
 */

export const SPEC_BLOCK_GAP = 8
export const SPEC_BLOCK_PAD_X = 2

let measureCanvas = null

function getMeasureContext() {
  if (typeof document === 'undefined' || !document.createElement) return null
  if (measureCanvas) return measureCanvas
  const canvas = document.createElement('canvas')
  if (!canvas.getContext) return null
  measureCanvas = canvas.getContext('2d')
  return measureCanvas
}

function fontFamily() {
  const style = typeof document !== 'undefined' && document.body ? window.getComputedStyle(document.body) : null
  return (style && style.fontFamily) || 'sans-serif'
}

/** 文本宽度（px）；无 canvas 时按字宽估算，保证 SSR/测试环境可运行 */
export function measureSpecTextWidth(text, fontSize, fontWeight, letterSpacing) {
  const value = text == null ? '' : String(text)
  if (!value) return 0
  const size = fontSize || 12
  const spacing = letterSpacing || 0
  const ctx = getMeasureContext()
  if (!ctx) return value.length * size * 0.62 + Math.max(0, value.length - 1) * spacing
  ctx.font = (fontWeight === 'bold' ? 'bold ' : '') + size + 'px ' + fontFamily()
  const width = ctx.measureText(value).width + Math.max(0, value.length - 1) * spacing
  return Math.ceil(width)
}

/** 名称与值之间的分隔宽度：按当前字号下的空格宽度取值 */
export function measureSpecGap(fontSize, fontWeight, letterSpacing) {
  return Math.max(3, measureSpecTextWidth(' ', fontSize, fontWeight, letterSpacing))
}

/**
 * 组装块的文本项
 *
 * @param {Object} options {
 *   categoryText, // 品类名：只挂在每个品类的第一个规格块上，独占一行
 *   nameText, nameWrap, nameKey,
 *   namePlaceholder, // 属性名没有文本时的浅灰占位文案（仅编辑页传入，打印不传）
 *   values: [{ key, text }]
 * }
 * @returns {Array} [{ key, kind: 'name'|'value', category, text, wrap, placeholder }]
 */
export function buildSpecBlockItems(options) {
  const opts = options || {}
  const items = []
  const categoryText = opts.categoryText == null ? '' : String(opts.categoryText).trim()
  if (categoryText) {
    items.push({ key: '', kind: 'name', category: true, text: categoryText, wrap: true, placeholder: false })
  }
  const nameText = opts.nameText == null ? '' : String(opts.nameText)
  const namePlaceholder = opts.namePlaceholder == null ? '' : String(opts.namePlaceholder)
  if (nameText) {
    items.push({ key: opts.nameKey || '', kind: 'name', text: nameText, wrap: !!opts.nameWrap, placeholder: false })
  } else if (namePlaceholder) {
    // 名称空白：用可显示的属性名浅灰占位，占位只服务编辑页
    items.push({ key: opts.nameKey || '', kind: 'name', text: namePlaceholder, wrap: !!opts.nameWrap, placeholder: true })
  }
  ;(Array.isArray(opts.values) ? opts.values : []).forEach(function(value) {
    const text = value && value.text != null ? String(value.text) : ''
    if (text === '') return
    items.push({ key: value.key || '', kind: 'value', text: text, wrap: false, placeholder: false })
  })
  return items
}

/**
 * 计算规格块的行布局
 *
 * @param {Object} options {
 *   items, width, fontSize, fontWeight, letterSpacing, lineHeight, textAlign
 * }
 * @returns {Object} { lines: [{ items, text, width }], width, height, minWidth, lineHeightPx, textAlign }
 */
export function resolveSpecBlockLines(options) {
  const opts = options || {}
  const fontSize = opts.fontSize || 12
  const fontWeight = opts.fontWeight || 'normal'
  const letterSpacing = opts.letterSpacing || 0
  const lineHeight = opts.lineHeight || 1.3
  const textAlign = opts.textAlign === 'center' || opts.textAlign === 'right' ? opts.textAlign : 'left'
  const lineHeightPx = Math.max(1, Math.round(fontSize * lineHeight))
  const gap = measureSpecGap(fontSize, fontWeight, letterSpacing)
  const padX = SPEC_BLOCK_PAD_X * 2
  const items = (Array.isArray(opts.items) ? opts.items : []).map(function(item) {
    return {
      key: item.key || '',
      kind: item.kind === 'name' ? 'name' : 'value',
      text: item.text == null ? '' : String(item.text),
      wrap: !!item.wrap,
      placeholder: !!item.placeholder,
      width: measureSpecTextWidth(item.text, fontSize, fontWeight, letterSpacing)
    }
  })
  if (!items.length) {
    return { lines: [], width: 0, height: 0, minWidth: 0, lineHeightPx: lineHeightPx, textAlign: textAlign }
  }
  // 名称最小宽度：全部名称顺序排开所需宽度，边框再窄也不允许压名称
  // 浅灰占位只服务编辑页，不参与最小宽度计算（打印时不存在该文本）
  let nameWidth = 0
  let nameCount = 0
  let naturalWidth = 0
  items.forEach(function(item, index) {
    naturalWidth += item.width + (index > 0 ? gap : 0)
    if (item.kind === 'name' && !item.placeholder) {
      nameWidth += item.width + (nameCount > 0 ? gap : 0)
      nameCount++
    }
  })
  const minWidth = Math.ceil(nameWidth + padX)
  const naturalTotal = Math.ceil(naturalWidth + padX)
  const requested = Number(opts.width)
  const width = Math.max(minWidth, isFinite(requested) && requested > 0 ? Math.ceil(requested) : naturalTotal)
  const contentWidth = Math.max(1, width - padX)
  const lines = []
  let current = { items: [], width: 0 }
  const flush = function() {
    if (!current.items.length) return
    lines.push({
      items: current.items,
      text: current.items.map(function(item) { return item.text }).join(' '),
      width: current.width
    })
    current = { items: [], width: 0 }
  }
  items.forEach(function(item) {
    const add = (current.items.length ? gap : 0) + item.width
    if (current.items.length && current.width + add > contentWidth + 0.5) flush()
    current.items.push(item)
    current.width += (current.items.length > 1 ? gap : 0) + item.width
    if (item.kind === 'name' && item.wrap) flush()
  })
  flush()
  return {
    lines: lines,
    width: width,
    height: lines.length * lineHeightPx,
    minWidth: minWidth,
    lineHeightPx: lineHeightPx,
    textAlign: textAlign
  }
}
