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
/** 小票模板里的商品明细表字段（与 goods_table 同义） */
export const RECEIPT_TABLE_KEY = 'rc_table'

/** 是否为商品明细表元素（地址/商品标签用 goods_table，小票用 rc_table） */
export function isGoodsTableKey(key) {
  return key === GOODS_TABLE_KEY || key === RECEIPT_TABLE_KEY
}

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

/** 小票商品明细：面板每种属性只列一次，画布/打印时再按商品行展开 */
export const RECEIPT_ITEM_FIELDS = [
  { key: 'name', label: '商品名称', weight: 0.26 },
  { key: 'spec', label: '规格组合', weight: 0.24 },
  { key: 'qty', label: '数量', weight: 0.08 },
  { key: 'price', label: '单价', weight: 0.14 },
  { key: 'discount', label: '折扣', weight: 0.10 },
  { key: 'amount', label: '金额', weight: 0.18 }
]
export const RECEIPT_RECORD_FIELDS = [
  { key: 'rc_title', label: '小票标题', group: '单据信息' },
  { key: 'rc_no', label: '单号', group: '单据信息' },
  { key: 'rc_date', label: '日期', group: '单据信息' },
  { key: 'rc_shop', label: '店铺', group: '单据信息' },
  { key: 'rc_operator', label: '开单员', group: '单据信息' },
  { key: 'rc_pay_type', label: '收银方式', group: '单据信息' },
  { key: 'rc_member', label: '会员信息', group: '单据信息' },
  { key: 'rc_member_name', label: '会员姓名', group: '单据信息' },
  { key: 'rc_member_phone', label: '会员电话', group: '单据信息' },
  { key: 'rc_member_no', label: '会员卡号', group: '单据信息' },
  { key: 'rc_qty_total', label: '合计数量', group: '金额信息' },
  { key: 'rc_amount_total', label: '合计金额', group: '金额信息' },
  { key: 'rc_discount', label: '折扣', group: '金额信息' },
  { key: 'rc_settle_amount', label: '结算金额', group: '金额信息' },
  { key: 'rc_discount_amount', label: '优惠金额', group: '金额信息' },
  { key: 'rc_get_amount', label: '实收金额', group: '金额信息' },
  { key: 'rc_back_amount', label: '找零', group: '金额信息' },
  { key: 'rc_debt', label: '欠款', group: '金额信息' },
  { key: 'rc_remark', label: '备注', group: '其他信息' },
  { key: 'rc_footer', label: '落款', group: '其他信息' }
]
export const RECEIPT_ITEM_PANEL_PREFIX = 'rc_item_'
const RECEIPT_ITEM_KEY_RE = /^item\d+_(?:name|spec|qty|price|discount|amount)$/
const RECEIPT_ITEM_PANEL_KEY_RE = /^rc_item_(name|spec|qty|price|discount|amount)$/

/** 元素面板中的固定商品属性 key（不包含商品行号） */
export function receiptItemPanelKey(fieldKey) {
  return RECEIPT_ITEM_PANEL_PREFIX + fieldKey
}

export function isReceiptItemPanelKey(key) {
  return RECEIPT_ITEM_PANEL_KEY_RE.test(String(key == null ? '' : key))
}

export function receiptItemFieldFromPanel(key) {
  const matched = String(key == null ? '' : key).match(RECEIPT_ITEM_PANEL_KEY_RE)
  return matched ? matched[1] : ''
}

/** 规格组合的可增减属性项 */
export const RECEIPT_SPEC_PART_OPTIONS = [
  { key: 'specName1', label: '规格名1' },
  { key: 'specValue1', label: '规格值1' },
  { key: 'specName2', label: '规格名2' },
  { key: 'specValue2', label: '规格值2' },
  { key: 'specName3', label: '规格名3' },
  { key: 'specValue3', label: '规格值3' }
]

export function defaultReceiptSpecParts() {
  return RECEIPT_SPEC_PART_OPTIONS.slice(0, 4).map(function(part) {
    return { key: part.key, showLabel: false, displayName: part.label }
  })
}

/** 只保存组合规则；空数组是合法的“规格组合为空”，缺省才使用默认四项 */
export function normalizeReceiptSpecParts(parts) {
  if (!Array.isArray(parts)) return defaultReceiptSpecParts()
  const used = {}
  const normalized = []
  parts.forEach(function(part) {
    const option = RECEIPT_SPEC_PART_OPTIONS.filter(function(item) {
      return part && item.key === part.key
    })[0]
    if (!option || used[option.key]) return
    used[option.key] = true
    normalized.push({
      key: option.key,
      showLabel: part.showLabel === true || part.showName === true,
      displayName: part.displayName != null ? String(part.displayName) : option.label
    })
  })
  return normalized
}
/** 小票里属于商品标签、不参与小票排版的遗留字段 */
const RECEIPT_STRAY_KEYS = ['name', 'spec1', 'spec2', 'spec3', GOODS_KEY, GOODS_TABLE_KEY, RECEIPT_TABLE_KEY]

/** 是否为小票「按单品拆分」生成的动态元素（只参与本次编辑/打印，不写入模板） */
export function isReceiptItemKey(key) {
  return RECEIPT_ITEM_KEY_RE.test(String(key == null ? '' : key))
}

function parseLegacySpec(text) {
  const value = text == null ? '' : String(text).trim()
  const index = value.search(/[：:]/)
  if (index < 0) return { name: '', value: value }
  return {
    name: value.slice(0, index).trim(),
    value: value.slice(index + 1).trim()
  }
}

function receiptSpecPartValue(row, key) {
  if (row && row[key] != null) return String(row[key]).trim()
  const matched = key.match(/^spec(Name|Value)(\d)$/)
  if (!matched || !row) return ''
  const index = Number(matched[2])
  let text = row['spec' + index]
  if ((text == null || text === '') && index === 1 && row.spec != null) text = row.spec
  const segments = String(text == null ? '' : text).split('　').filter(function(part) { return part.trim() })
  const parsed = segments.map(parseLegacySpec)
  return parsed.map(function(part) {
    return matched[1] === 'Name' ? part.name : part.value
  }).filter(function(value) { return value !== '' }).join(' ')
}

/** 按模板规则输出规格名/规格值；showLabel 控制是否额外显示可编辑的属性名 */
export function receiptSpecText(row, parts) {
  return normalizeReceiptSpecParts(parts).map(function(part) {
    const value = receiptSpecPartValue(row, part.key)
    if (!value) return ''
    return part.showLabel && part.displayName ? part.displayName + '：' + value : value
  }).filter(function(value) { return value !== '' }).join(' ')
}

/** 单个商品字段值；显式传入组合规则时使用规则，否则兼容旧快照 */
export function receiptItemValue(row, key, specParts) {
  if (!row) return ''
  if (key === 'spec') {
    if (Array.isArray(specParts)) return receiptSpecText(row, specParts)
    const spec = row.spec != null ? String(row.spec) : ''
    if (spec) return spec
    return ['spec1', 'spec2', 'spec3']
      .map(function(k) { return row[k] == null ? '' : String(row[k]).trim() })
      .filter(function(v) { return v !== '' })
      .join(' ')
  }
  return row[key] == null ? '' : String(row[key])
}

/**
 * 小票商品明细 → 逐件、逐字段的独立元素
 * 每件商品占一行，5 个字段按列坐标摆放，均可在编辑页单独选中修改
 *
 * @param {Object} options rows / paperWidth / x / y / fontSize / lineHeight / gap
 * @returns {{attrs:Array, styles:Object, keys:Array, height:number}}
 */
export function buildReceiptItemElements(options) {
  const opts = options || {}
  const rows = (opts.rows || []).filter(hasGoodsValue)
  const enabledFields = Array.isArray(opts.fields)
    ? RECEIPT_ITEM_FIELDS.filter(function(field) { return opts.fields.indexOf(field.key) > -1 })
    : RECEIPT_ITEM_FIELDS
  if (!rows.length || !enabledFields.length) {
    return { attrs: [], styles: {}, keys: [], height: 0 }
  }
  const paperWidth = opts.paperWidth > 0 ? opts.paperWidth : 320
  const fontSize = opts.fontSize || 12
  const lineHeight = opts.lineHeight || 1.3
  const x = opts.x != null ? opts.x : 8
  const startY = opts.y != null ? opts.y : 0
  const gap = opts.gap != null ? opts.gap : 4
  const width = Math.max(120, paperWidth - x * 2)
  const linePx = fontSize * lineHeight
  // 列坐标：按权重分配宽度，最后一列补齐余量
  const cols = []
  let offset = 0
  enabledFields.forEach(function(field, index) {
    const last = index === enabledFields.length - 1
    const colWidth = last ? width - offset : Math.floor(width * field.weight)
    cols.push({ x: offset, width: colWidth })
    offset += colWidth
  })
  const attrs = []
  const styles = {}
  let cursor = startY
  rows.forEach(function(row, rowIndex) {
    // 每行按各列折行后的最大行数确定行高，避免同一行各列各自把下方元素重复下推
    const fields = enabledFields.map(function(field, colIndex) {
      const value = receiptItemValue(row, field.key, opts.specParts)
      return {
        key: 'item' + (rowIndex + 1) + '_' + field.key,
        field: field,
        value: value,
        colIndex: colIndex,
        lines: measureTextLines(value, cols[colIndex].width, fontSize, false, 0)
      }
    })
    let maxLines = 1
    fields.forEach(function(item) { if (item.lines > maxLines) maxLines = item.lines })
    const rowHeight = Math.round(Math.max(linePx, maxLines * linePx))
    fields.forEach(function(item) {
      attrs.push({
        key: item.key,
        label: item.field.label,
        value: item.value,
        isCustom: false,
        isReceiptItem: true,
        group: '商品明细'
      })
      styles[item.key] = {
        x: x + cols[item.colIndex].x,
        y: cursor,
        width: cols[item.colIndex].width,
        height: rowHeight,
        fontSize: fontSize,
        fontWeight: 'normal',
        letterSpacing: 0,
        lineHeight: lineHeight
      }
    })
    cursor += rowHeight + gap
  })
  return {
    attrs: attrs,
    styles: styles,
    keys: attrs.map(function(a) { return a.key }),
    height: rows.length ? cursor - startY - gap : 0
  }
}

/**
 * 小票：把模板里的明细表/商品组合替换成「逐件 + 逐字段」的独立元素
 * 明细表原位作为起点，下方元素按商品行数整体顺移
 *
 * @param {Object} options rows / selectedAttrs / attrStyles / attrLabels / paperWidth / fallbackY
 * @returns {{selectedAttrs:Array, attrStyles:Object, attrLabels:Object, attrs:Array, itemKeys:Array}}
 */
export function replaceReceiptGoodsWithItems(options) {
  const opts = options || {}
  const styles = Object.assign({}, opts.attrStyles || {})
  const labels = Object.assign({}, opts.attrLabels || {})
  const rows = (opts.rows || []).filter(hasGoodsValue)
  let anchorStyle = opts.anchorStyle ? Object.assign({}, opts.anchorStyle) : null
  const kept = []
  ;(opts.selectedAttrs || []).forEach(function(key) {
    if (!key || isReceiptItemKey(key)) return
    if (RECEIPT_STRAY_KEYS.indexOf(key) > -1) {
      if (isGoodsTableKey(key) && styles[key]) anchorStyle = styles[key]
      delete styles[key]
      delete labels[key]
      return
    }
    kept.push(key)
  })
  Object.keys(styles).forEach(function(key) { if (isReceiptItemKey(key)) delete styles[key] })
  Object.keys(labels).forEach(function(key) { if (isReceiptItemKey(key)) delete labels[key] })
  const startY = anchorStyle ? (anchorStyle.y || 0) : (opts.fallbackY || 0)
  if (!rows.length || (Array.isArray(opts.receiptItemFields) && !opts.receiptItemFields.length)) {
    return {
      selectedAttrs: kept,
      attrStyles: styles,
      attrLabels: labels,
      attrs: [],
      itemKeys: [],
      layout: { startY: startY, delta: 0, anchorStyle: anchorStyle, movedKeys: [] }
    }
  }
  const built = buildReceiptItemElements({
    rows: rows,
    fields: opts.receiptItemFields,
    specParts: opts.receiptSpecParts,
    paperWidth: opts.paperWidth,
    x: anchorStyle ? (anchorStyle.x || 0) : 8,
    y: startY,
    fontSize: anchorStyle ? (anchorStyle.fontSize || 12) : 12,
    lineHeight: anchorStyle ? (anchorStyle.lineHeight || 1.3) : 1.3
  })
  // 原明细表占位高度与逐件元素总高度差，下方元素整体顺移
  const delta = built.height - (anchorStyle ? (anchorStyle.height || 0) : 0)
  const movedKeys = []
  if (delta !== 0) {
    kept.forEach(function(key) {
      const style = styles[key]
      if (style && (style.y || 0) >= startY - 0.5) {
        styles[key] = Object.assign({}, style, { y: (style.y || 0) + delta })
        movedKeys.push(key)
      }
    })
  }
  built.attrs.forEach(function(attr) {
    styles[attr.key] = built.styles[attr.key]
    // 明细只显示字段值，不加「商品名称: 」前缀
    labels[attr.key] = ''
  })
  return {
    selectedAttrs: kept.concat(built.keys),
    attrStyles: styles,
    attrLabels: labels,
    attrs: built.attrs,
    itemKeys: built.keys,
    layout: { startY: startY, delta: delta, anchorStyle: anchorStyle, movedKeys: movedKeys }
  }
}

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
    const spec1 = g && g.spec1 != null ? String(g.spec1) : ''
    // 明细行只有合并后的 spec（如小票快照）时，退到第一列规格显示
    const spec = g && g.spec != null ? String(g.spec) : ''
    return {
      name: g && g.name != null ? String(g.name) : '',
      spec1: spec1 || spec,
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
    // 仅提供合并 spec 的明细行（小票快照）也保证规格列有内容
    const cells = [row.name, row.spec1 || row.spec || '', row.spec2, row.spec3]
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
  const tableKey = selected.filter(isGoodsTableKey)[0]
  if (tableKey && attrStyles[tableKey]) specialKeys.push(tableKey)
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
    if (isGoodsTableKey(key)) {
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
  const paperWidth = opts.paperWidth > 0 ? opts.paperWidth : 0
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
    // 折行/裁切宽度 = min(元素边框宽度, 标签可用宽度)；未设置宽度时用标签可用宽度
    const labelLimit = paperWidth ? Math.max(20, paperWidth - (style.x || 0)) : 0
    const width = style.width > 0
      ? (labelLimit ? Math.min(style.width, labelLimit) : style.width)
      : labelLimit
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
    // 小票明细单元格的高度已按整行最大行数统一分配，不再单列参与上下位移
    if (isReceiptItemKey(key)) {
      style.height = Math.round(Math.max(originalHeight, actualHeight))
      return
    }
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
    return !isGoodsTableKey(key) && key !== 'dn_table' && key !== 'dn_table_total' && key.indexOf('code__') !== 0
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
    texts: texts,
    paperWidth: opts.paperWidth
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
