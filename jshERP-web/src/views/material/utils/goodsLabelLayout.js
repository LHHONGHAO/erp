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
export function measureTextLines(text, width, fontSize, bold, letterSpacing) {
  const value = text == null ? '' : String(text)
  if (!value) return 1
  const lines = value.split('\n')
  if (!width || width <= 0) return lines.length
  const ctx = measureCtx()
  if (!ctx) return lines.length
  ctx.font = (bold ? 'bold ' : '') + (fontSize || 12) + 'px sans-serif'
  const spacing = letterSpacing || 0
  const measure = function(txt) {
    return ctx.measureText(txt).width + txt.length * spacing
  }
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
      if (current && measure(current + ch) > width) {
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
  // 未设置宽度时默认「标签宽 - 左边距」，按宽度折行，不再横向溢出
  const width = s.width > 0 ? s.width : Math.max(20, paperWidth - x)
  const widths = columnWidths(width)
  const bold = s.fontWeight === 'bold'
  const headHeight = Math.round(lineHeight) + CELL_PAD * 2
  const body = rows.map(function(row) {
    const cells = [row.name, row.spec1, row.spec2, row.spec3]
    let maxLines = 1
    cells.forEach(function(cell, i) {
      maxLines = Math.max(maxLines, measureTextLines(cell, widths[i] - CELL_PAD * 2, fontSize, bold, s.letterSpacing || 0))
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
 * 商品行是否有内容（空行不参与分行/分页）
 * @param {Object} row
 * @returns {Boolean}
 */
export function hasGoodsValue(row) {
  if (!row || typeof row !== 'object') return false
  if (row.name || row.spec1 || row.spec2 || row.spec3) return true
  // 数据源明细行：除内部 key 外任一字段有值即视为有效
  return Object.keys(row).some(function(key) {
    return key !== 'key' && row[key] != null && row[key] !== ''
  })
}

/**
 * 多个商品按标签可用高度拆页
 *
 * @param {Object} options
 * @param {Array}  options.rows        商品行（空行会被忽略）
 * @param {Object} options.style       商品元素样式（x/y/width/fontSize/lineHeight）
 * @param {Object} options.table       表格模式下的表格数据（buildGoodsTable 的返回值）
 * @param {Number} options.paperWidth  标签宽度（px）
 * @param {Number} options.paperHeight 标签高度（px）
 * @param {Array}  options.goodsFields 商品组合字段
 * @param {String} options.goodsLabel  商品组合前缀（属性名）
 * @param {String} options.mode        none | fit | each
 * @param {Number} options.maxRowsPerPage fit 模式下每页最多商品行数（0 或空 = 只按高度）
 * @returns {{pages: Array<{rows: Array, rowIndexes: number[]}>, count: number}}
 */
export function paginateGoodsRows(options) {
  const opts = options || {}
  const rows = (opts.rows || []).filter(hasGoodsValue)
  const indexOfAll = rows.map(function(_, i) { return i })
  if (!rows.length) return { pages: [{ rows: [], rowIndexes: [] }], count: 1 }
  if (opts.mode === 'each') {
    // 每个商品按数量拆分：数量 2 的商品输出 2 张，每张只记 1 件
    const eachPages = []
    rows.forEach(function(row, i) {
      const qty = Math.min(99, Math.max(1, parseInt(row && row.qty, 10) || 1))
      for (let n = 0; n < qty; n++) eachPages.push({ rows: [row], rowIndexes: [i] })
    })
    return { pages: eachPages, count: eachPages.length }
  }
  if (opts.mode !== 'fit') {
    return { pages: [{ rows: rows, rowIndexes: indexOfAll }], count: 1 }
  }

  const style = opts.style || {}
  const table = opts.table
  const paperHeight = opts.paperHeight > 0 ? opts.paperHeight : 0
  const top = style.y || 0
  const fontSize = style.fontSize || 12
  const lineHeight = fontSize * (style.lineHeight || 1.3)
  const bold = style.fontWeight === 'bold'
  const width = style.width > 0 ? style.width : Math.max(20, (opts.paperWidth || 0) - (style.x || 0))
  const prefix = opts.goodsLabel ? opts.goodsLabel + ': ' : ''
  // 可用高度 = 标签底边 - 商品元素顶边（表格模式再减去重复表头）
  let pageHeight = paperHeight > top ? paperHeight - top : 0
  if (table) pageHeight = Math.max(0, pageHeight - (table.headHeight || 0))
  // 尺寸未知时保持单页，避免误拆
  if (!pageHeight) {
    return { pages: [{ rows: rows, rowIndexes: indexOfAll }], count: 1 }
  }

  const heights = rows.map(function(row, i) {
    if (table && table.rows && table.rows[i]) return table.rows[i].height || lineHeight
    const lines = measureTextLines(
      prefix + goodsLine(row, opts.goodsFields),
      width,
      fontSize,
      bold,
      style.letterSpacing || 0
    )
    return Math.max(lineHeight, lines * lineHeight)
  })

  // 每页商品行上限：0 表示只按高度分页
  const maxRows = Math.max(0, parseInt(opts.maxRowsPerPage, 10) || 0)
  const pages = []
  let current = { rows: [], rowIndexes: [] }
  let used = 0
  rows.forEach(function(row, i) {
    const height = heights[i]
    const fullByRows = maxRows > 0 && current.rows.length >= maxRows
    // 当前页已有内容且（放不下本行 或 已达行数上限）时换页；单行超高时独占一页
    if (current.rows.length && (fullByRows || used + height > pageHeight + 0.5)) {
      pages.push(current)
      current = { rows: [], rowIndexes: [] }
      used = 0
    }
    current.rows.push(row)
    current.rowIndexes.push(i)
    used += height
  })
  if (current.rows.length) pages.push(current)
  if (!pages.length) pages.push({ rows: [], rowIndexes: [] })
  return { pages: pages, count: pages.length }
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
  const goodsLines = goodsRows.map(function(row) { return goodsLine(row, opts.goodsFields) })
  const goodsText = goodsLines.join('\n')
  // 每个商品值占一行，且每行都带「属性名: 」前缀（与单行元素的字号、宽度一致）
  const goodsPrefix = opts.goodsLabel ? opts.goodsLabel + ': ' : ''
  const goodsDisplay = goodsPrefix
    ? goodsLines.map(function(line) { return goodsPrefix + line }).join('\n')
    : goodsText

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
      // 逐行带前缀后测量行数，前缀可能额外占行
      actualHeight = Math.max(lineHeight, measureTextLines(goodsDisplay, width, fontSize, style.fontWeight === 'bold', style.letterSpacing || 0) * lineHeight)
    }
    // 商品表 / 商品组合同样按实际高度排版：多行下移，少行回缩上移
    const delta = Math.round(actualHeight - originalHeight)
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
    goodsDisplay: goodsDisplay,
    table: table
  }
}

/** 单行文本超出宽度时裁切，末尾补省略号 */
export function truncateText(text, width, fontSize, bold, letterSpacing) {
  const value = text == null ? '' : String(text)
  if (!value || !width || width <= 0) return value
  const ctx = measureCtx()
  if (!ctx) return value
  ctx.font = (bold ? 'bold ' : '') + (fontSize || 12) + 'px sans-serif'
  const spacing = letterSpacing || 0
  const measure = function(txt) {
    return ctx.measureText(txt).width + txt.length * spacing
  }
  if (measure(value) <= width) return value
  const ellipsis = '…'
  if (measure(ellipsis) > width) return ''
  let low = 0
  let high = value.length
  while (low < high) {
    const mid = Math.ceil((low + high) / 2)
    if (measure(value.slice(0, mid) + ellipsis) <= width) low = mid
    else high = mid - 1
  }
  return value.slice(0, low) + ellipsis
}

/**
 * 文本超宽处理（预览与打印共用）
 *
 * mode = 'wrap'     ：按元素宽度自动折行，元素高度取实际行数；
 *                     折行多出的高度让下方元素整体下移，行数少于模板预留高度时上方元素逐行上移；
 * mode = 'ellipsis' ：保持单行，超出宽度末尾补省略号裁切（显式换行的每行分别处理）。
 *
 * @param {Object} options.styles      已解析样式（含 x/y/width/height/fontSize/lineHeight）
 * @param {Array}  options.keys        需要处理的文本元素 key
 * @param {Object} options.texts       元素 key → 显示文本（含「属性名: 」前缀）
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
    const fontSize = style.fontSize || 12
    const bold = style.fontWeight === 'bold'
    const lineHeight = fontSize * (style.lineHeight || 1.3)
    if (mode === 'ellipsis') {
      if (!width) return
      resultTexts[key] = raw.split('\n').map(function(line) {
        return truncateText(line, width, fontSize, bold, style.letterSpacing || 0)
      }).join('\n')
      return
    }
    // 按实际内容行数排版：多行增高下移，少行回缩上移
    const originalHeight = style.height > 0 ? style.height : lineHeight
    const lines = width
      ? measureTextLines(raw, width, fontSize, bold, style.letterSpacing || 0)
      : raw.split('\n').length
    const actualHeight = Math.max(lineHeight, lines * lineHeight)
    const delta = Math.round(actualHeight - originalHeight)
    if (delta !== 0) {
      const bottom = (style.y || 0) + originalHeight
      Object.keys(styles).forEach(function(other) {
        if (other === key) return
        if ((styles[other].y || 0) >= bottom - 0.5) styles[other].y = (styles[other].y || 0) + delta
      })
    }
    style.height = Math.round(actualHeight)
  })

  return { styles: styles, texts: resultTexts }
}

/**
 * 标签最终布局（编辑预览 / 打印预览 / 打印数据共用）
 *
 * 先按商品明细计算动态高度并推动下方元素，再按文字实际行数折行 / 上下归位。
 *
 * @param {Object} options.selectedAttrs 已选元素 key
 * @param {Object} options.attrStyles    模板保存样式
 * @param {Object} options.values        业务值（key → 值）
 * @param {Object} options.labels        元素 key → 属性名
 * @param {Array}  options.goodsRows     商品明细行
 * @param {Array}  options.goodsFields   商品组合字段
 * @param {Number} options.paperWidth    标签宽度（px）
 * @param {String} options.mode          wrap | ellipsis
 * @returns {{styles:Object, texts:Object, table:Object|null, goodsRows:Array, goodsText:String}}
 */
export function resolveLabelLayout(options) {
  const opts = options || {}
  const labels = opts.labels || {}
  const values = Object.assign({}, opts.values || {}, {
    goodsRows: opts.goodsRows || (opts.values && opts.values.goodsRows) || []
  })
  const allKeys = opts.selectedAttrs || Object.keys(opts.attrStyles || {})
  // 明细表由表格自身排版，不参与文字折行
  const textKeys = allKeys.filter(function(key) {
    return key !== GOODS_TABLE_KEY && key !== 'dn_table' && key !== 'dn_table_total' && key.indexOf('code__') !== 0
  })
  const goods = resolveGoodsLayout({
    selectedAttrs: allKeys,
    attrStyles: opts.attrStyles,
    values: values,
    paperWidth: opts.paperWidth,
    goodsLabel: opts.goodsLabel || labels[GOODS_KEY] || '',
    goodsFields: opts.goodsFields
  })
  const texts = {}
  textKeys.forEach(function(key) {
    // 商品组合已按行带好属性名前缀，直接使用，避免重复拼接
    if (key === GOODS_KEY) {
      texts[key] = goods.goodsDisplay
      return
    }
    const value = values[key] == null ? '' : values[key]
    texts[key] = labels[key] ? labels[key] + ': ' + value : value
  })
  const overflow = applyTextOverflow({
    mode: opts.mode,
    styles: goods.styles,
    keys: textKeys,
    texts: texts
  })
  return {
    styles: overflow.styles,
    texts: overflow.texts,
    table: goods.table,
    goodsRows: goods.goodsRows,
    goodsText: goods.goodsText,
    goodsDisplay: goods.goodsDisplay
  }
}
