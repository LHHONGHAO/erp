/**
 * 地址打印：商品明细的表格 / 组合排版与动态高度
 *
 * 规则：
 * 1. 表格元素（goods_table）高度 = 表头 + 各商品行高度，行高由字号、行距和折行行数决定；
 * 2. 组合元素（goods）每个商品一行，内容由模板勾选的「商品名称 + 规格值1 + 规格值2 + 规格值3」组成（默认全部）；
 * 3. 表格/组合元素实际变高后，位于其下方的其他元素整体顺移，保持原有相对间距；
 * 4. 只做运行时重排，不回写模板里保存的坐标。
 */
export const GOODS_TABLE_KEY = 'goods_table'
export const GOODS_KEY = 'goods'

/** 表格列名与列宽权重（商品名称更宽） */
export const GOODS_TABLE_COLUMNS = ['商品名称', '规格值1', '规格值2', '规格值3']
/** 商品组合（商品信息）可选的组成字段，顺序即显示顺序 */
export const GOODS_FIELD_OPTIONS = [
  { key: 'name', label: '商品名称' },
  { key: 'spec1', label: '规格值1' },
  { key: 'spec2', label: '规格值2' },
  { key: 'spec3', label: '规格值3' }
]
const GOODS_FIELD_KEYS = GOODS_FIELD_OPTIONS.map(function(f) { return f.key })
const COL_WEIGHTS = [3, 2, 2, 2]
const CELL_PAD = 2

/**
 * 归一化组合字段：未配置（旧模板）时默认显示全部四项；
 * 已配置时按固定顺序取勾选项（允许为空，表示不显示值）
 */
export function normalizeGoodsFields(fields) {
  if (!Array.isArray(fields)) return GOODS_FIELD_KEYS.slice()
  return GOODS_FIELD_KEYS.filter(function(key) { return fields.indexOf(key) > -1 })
}

let measureCanvas = null
function measureCtx() {
  if (measureCanvas) return measureCanvas
  if (typeof document === 'undefined') return null
  measureCanvas = document.createElement('canvas').getContext('2d')
  return measureCanvas
}

/**
 * 文本在指定宽度下折行后的行数（至少 1 行）
 */
export function measureTextLines(text, width, fontSize, bold) {
  const value = text == null ? '' : String(text)
  if (!value) return 1
  const lines = value.split('\n')
  if (!width || width <= 0) return lines.length
  const ctx = measureCtx()
  if (!ctx) return lines.length
  ctx.font = (bold ? 'bold ' : '') + (fontSize || 12) + 'px sans-serif'
  let total = 0
  lines.forEach(function(line) {
    if (!line) {
      total += 1
      return
    }
    let count = 1
    let current = ''
    for (let i = 0; i < line.length; i++) {
      const ch = line.charAt(i)
      if (current && ctx.measureText(current + ch).width > width) {
        count++
        current = ch
      } else {
        current += ch
      }
    }
    total += count
  })
  return Math.max(1, total)
}

/** 单个商品的组合文案；fields 为空数组时返回空串，未传时默认全部字段 */
export function goodsLine(row, fields) {
  if (!row) return ''
  const keys = normalizeGoodsFields(fields)
  return keys
    .map(function(k) { return row[k] == null ? '' : String(row[k]).trim() })
    .filter(function(v) { return v !== '' })
    .join(' ')
}

/**
 * 统一商品行数据：优先 values.goodsRows（地址识别页多行商品），
 * 兼容旧数据里 goods 为「名称(规格)、名称(规格)」或按行拼接的字符串
 */
export function normalizeGoodsRows(values) {
  const list = values && Array.isArray(values.goodsRows) ? values.goodsRows : []
  const rows = list.map(function(g) {
    return {
      name: g && g.name != null ? String(g.name) : '',
      spec1: g && g.spec1 != null ? String(g.spec1) : '',
      spec2: g && g.spec2 != null ? String(g.spec2) : '',
      spec3: g && g.spec3 != null ? String(g.spec3) : ''
    }
  }).filter(function(r) { return r.name || r.spec1 || r.spec2 || r.spec3 })
  if (rows.length) return rows
  const text = values && values.goods != null ? String(values.goods) : ''
  return text.split(/[\n、]+/).map(function(part) {
    const line = part.trim()
    if (!line) return null
    // 旧格式「名称(规格)」→ 名称 + 规格值1
    const m = line.match(/^(.+?)[(（](.*)[)）]$/)
    if (m) return { name: m[1].trim(), spec1: m[2].trim(), spec2: '', spec3: '' }
    return { name: line, spec1: '', spec2: '', spec3: '' }
  }).filter(function(r) { return !!r })
}

/** 表格各列宽度 */
function columnWidths(totalWidth) {
  const sum = COL_WEIGHTS.reduce(function(a, b) { return a + b }, 0)
  const widths = COL_WEIGHTS.map(function(w) { return Math.floor(totalWidth * w / sum) })
  widths[widths.length - 1] += totalWidth - widths.reduce(function(a, b) { return a + b }, 0)
  return widths
}

/**
 * 商品明细表布局：列宽、表头高、每行高度与总高度
 */
export function buildGoodsTable(rows, style, paperWidth) {
  const s = style || {}
  const fontSize = s.fontSize || 12
  const lineHeight = fontSize * (s.lineHeight || 1.2)
  const x = s.x || 0
  const width = s.width > 0 ? s.width : Math.max(60, paperWidth - x - 10)
  const widths = columnWidths(width)
  const bold = s.fontWeight === 'bold'
  const headHeight = Math.round(lineHeight) + CELL_PAD * 2
  const body = rows.map(function(row) {
    const cells = [row.name, row.spec1, row.spec2, row.spec3]
    let maxLines = 1
    cells.forEach(function(cell, i) {
      maxLines = Math.max(maxLines, measureTextLines(cell, widths[i] - CELL_PAD * 2, fontSize, bold))
    })
    return {
      cells: cells,
      height: Math.round(maxLines * lineHeight) + CELL_PAD * 2
    }
  })
  return {
    x: x,
    y: s.y || 0,
    width: width,
    widths: widths,
    columns: GOODS_TABLE_COLUMNS.slice(),
    headHeight: headHeight,
    rows: body,
    height: headHeight + body.reduce(function(sum, r) { return sum + r.height }, 0),
    fontSize: fontSize,
    lineHeight: lineHeight,
    bold: bold
  }
}

/**
 * 解析地址打印的动态排版
 * @param {Object} options
 * @param {Array}  options.selectedAttrs 已选元素 key
 * @param {Object} options.attrStyles    模板样式（含 x/y/width/height/fontSize/lineHeight）
 * @param {Object} options.values        业务值（goodsRows / goods）
 * @param {number} options.paperWidth    标签宽度（px）
 * @param {Array}  options.goodsFields   商品组合的组成字段（缺省=全部）
 * @returns {{styles:Object, goodsRows:Array, goodsText:string, table:Object|null}}
 */
export function resolveGoodsLayout(options) {
  const opts = options || {}
  const attrStyles = opts.attrStyles || {}
  const selected = opts.selectedAttrs || []
  const values = opts.values || {}
  const paperWidth = opts.paperWidth || 320
  const goodsRows = normalizeGoodsRows(values)
  const goodsText = goodsRows.map(function(row) { return goodsLine(row, opts.goodsFields) }).join('\n')

  const styles = {}
  Object.keys(attrStyles).forEach(function(key) {
    styles[key] = Object.assign({}, attrStyles[key])
  })

  const specialKeys = []
  if (selected.indexOf(GOODS_TABLE_KEY) > -1 && attrStyles[GOODS_TABLE_KEY]) specialKeys.push(GOODS_TABLE_KEY)
  if (selected.indexOf(GOODS_KEY) > -1 && attrStyles[GOODS_KEY]) specialKeys.push(GOODS_KEY)
  specialKeys.sort(function(a, b) { return (attrStyles[a].y || 0) - (attrStyles[b].y || 0) })

  let table = null
  let shift = 0
  specialKeys.forEach(function(key) {
    const style = attrStyles[key]
    const baseY = style.y || 0
    const fontSize = style.fontSize || 12
    const lineHeight = fontSize * (style.lineHeight || 1.2)
    const originalHeight = style.height > 0 ? style.height : lineHeight
    let actualHeight = originalHeight
    if (key === GOODS_TABLE_KEY) {
      table = buildGoodsTable(goodsRows, style, paperWidth)
      actualHeight = table.height
    } else {
      const width = style.width > 0 ? style.width : Math.max(20, paperWidth - (style.x || 0))
      // 组合元素前面有「属性名：」前缀，测量行数时一并计入
      const prefix = opts.goodsLabel ? opts.goodsLabel + '：' : ''
      actualHeight = Math.max(lineHeight, measureTextLines(prefix + goodsText, width, fontSize, style.fontWeight === 'bold') * lineHeight)
    }
    const delta = Math.max(0, Math.ceil(actualHeight - originalHeight))
    const sourceBottom = baseY + originalHeight
    Object.keys(styles).forEach(function(other) {
      if (other === key) return
      const otherY = attrStyles[other].y || 0
      if (otherY >= sourceBottom - 0.5) styles[other].y = otherY + shift + delta
    })
    styles[key] = Object.assign({}, style, {
      y: baseY + shift,
      height: Math.round(actualHeight)
    })
    shift += delta
  })

  return {
    styles: styles,
    goodsRows: goodsRows,
    goodsText: goodsText,
    table: table
  }
}

/** 单行文本超出宽度时裁切，末尾补省略号 */
export function truncateText(text, width, fontSize, bold) {
  const value = text == null ? '' : String(text)
  if (!value || !width || width <= 0) return value
  const ctx = measureCtx()
  if (!ctx) return value
  ctx.font = (bold ? 'bold ' : '') + (fontSize || 12) + 'px sans-serif'
  if (ctx.measureText(value).width <= width) return value
  const ellipsis = '…'
  if (ctx.measureText(ellipsis).width > width) return ''
  let low = 0
  let high = value.length
  while (low < high) {
    const mid = Math.ceil((low + high) / 2)
    if (ctx.measureText(value.slice(0, mid) + ellipsis).width <= width) low = mid
    else high = mid - 1
  }
  return value.slice(0, low) + ellipsis
}

/**
 * 文本超宽处理（预览与打印共用）
 *
 * mode = 'wrap'     ：按元素宽度自动折行，折行多出的高度让下方元素整体下移；
 * mode = 'ellipsis' ：保持单行，超出宽度末尾补省略号裁切（显式换行的每行分别处理）。
 *
 * @param {Object} options.styles      已解析样式（含 x/y/width/height/fontSize/lineHeight）
 * @param {Array}  options.keys        需要处理的文本元素 key
 * @param {Object} options.texts       元素 key → 显示文本（含「属性名：」前缀）
 * @param {string} options.mode        wrap | ellipsis
 * @returns {{styles:Object, texts:Object}}
 */
export function applyTextOverflow(options) {
  const opts = options || {}
  const mode = opts.mode === 'ellipsis' ? 'ellipsis' : 'wrap'
  const source = opts.styles || {}
  const texts = opts.texts || {}
  const styles = {}
  Object.keys(source).forEach(function(key) {
    styles[key] = Object.assign({}, source[key])
  })
  const resultTexts = {}
  const ordered = (opts.keys || Object.keys(styles))
    .filter(function(key) { return styles[key] && key.indexOf('code__') !== 0 })
    .sort(function(a, b) { return (styles[a].y || 0) - (styles[b].y || 0) })

  ordered.forEach(function(key) {
    const style = styles[key]
    const raw = texts[key] == null ? '' : String(texts[key])
    resultTexts[key] = raw
    const width = style.width > 0 ? style.width : 0
    if (!width) return
    const fontSize = style.fontSize || 12
    const bold = style.fontWeight === 'bold'
    const lineHeight = fontSize * (style.lineHeight || 1.3)
    if (mode === 'ellipsis') {
      resultTexts[key] = raw.split('\n').map(function(line) {
        return truncateText(line, width, fontSize, bold)
      }).join('\n')
      return
    }
    const originalHeight = style.height > 0 ? style.height : lineHeight
    const actualHeight = Math.max(originalHeight, measureTextLines(raw, width, fontSize, bold) * lineHeight)
    const delta = Math.ceil(actualHeight - originalHeight)
    if (delta > 0) {
      const bottom = (style.y || 0) + originalHeight
      Object.keys(styles).forEach(function(other) {
        if (other === key) return
        if ((styles[other].y || 0) >= bottom - 0.5) styles[other].y = (styles[other].y || 0) + delta
      })
      style.height = Math.round(actualHeight)
    }
  })

  return { styles: styles, texts: resultTexts }
}
