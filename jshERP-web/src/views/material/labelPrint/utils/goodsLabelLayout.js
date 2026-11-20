/**
 * 地址打印：商品明细的表格 / 组合排版与动态高度
 *
 * 规则：
 * 1. 表格元素（goods_table）高度 = 表头 + 各商品行高度，行高由字号、行距和折行行数决定；
 * 2. 组合元素（goods）每个商品一行，内容由模板勾选的「商品名称 + 规格值1 + 规格值2 + 规格值3」组成（默认全部）；
 * 3. 表格/组合元素实际变高后，位于其下方的其他元素整体顺移，保持原有相对间距；
 * 4. 只做运行时重排，不回写模板里保存的坐标。
 */
import { parseGoodsSpecBlocks, specBlocksFromRow, parseSpecItemKey, isSpecItemKey, specNameKey, specValueKey, formatSpecName } from './goodsSpecBlocks'
import { pickMappedSpecValues, resolveCategorySpecText, pickSpecValuesFromDefs, resolveSpecTextFromDefs, LABEL_CATEGORY_KEY, LABEL_SPEC_KEY } from './categorySpec'

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

/** 小票商品明细：每件商品拆成独立元素，字段顺序即显示顺序 */
export const RECEIPT_ITEM_FIELDS = [
  { key: 'name', label: '商品名称', weight: 0.32 },
  { key: 'spec', label: '规格', weight: 0.26 },
  { key: 'qty', label: '数量', weight: 0.10 },
  { key: 'price', label: '单价', weight: 0.16 },
  { key: 'amount', label: '金额', weight: 0.16 }
]
const RECEIPT_ITEM_KEY_RE = /^item\d+_(?:name|spec|specname\d+|specvalue\d+(?:_\d+)?|specname_c.+_b\d+(?:_n.*)?|specvalue_c.+_b\d+(?:_n.*?)?_v\d+(?:_i.*)?|qty|price|amount)$/
/** 小票里属于商品标签、不参与小票排版的遗留字段 */
const RECEIPT_STRAY_KEYS = ['name', 'spec1', 'spec2', 'spec3', GOODS_KEY, GOODS_TABLE_KEY, RECEIPT_TABLE_KEY]

/** 是否为小票「按单品拆分」生成的动态元素（只参与本次编辑/打印，不写入模板） */
export function isReceiptItemKey(key) {
  const text = String(key == null ? '' : key)
  return RECEIPT_ITEM_KEY_RE.test(text) || isSpecItemKey(text)
}

/** 把旧模板的品类规格 key 映射到新模板统一的块/值槽位 key。 */
function canonicalReceiptItemKey(key) {
  const text = String(key == null ? '' : key)
  const parsed = parseSpecItemKey(text)
  if (parsed) {
    return parsed.kind === 'name'
      ? specNameKey(1, parsed.blockNo)
      : specValueKey(1, parsed.blockNo, parsed.valueNo)
  }
  return text.replace(/^item\d+_/, 'item1_')
}

/**
 * 编辑页关闭的明细项是否命中当前元素
 *
 * 编辑页只展示一组商品明细，开关按属性维度保存：
 * 规格名/规格值按「块序号 + 值序号」跨商品行共享，名称/数量等字段按字段名跨行共享，
 * 打印展开全部商品行时沿用同一套开关。
 */
export function isReceiptItemHidden(hiddenKeys, key) {
  const hidden = hiddenKeys || []
  if (!hidden.length) return false
  const text = String(key == null ? '' : key)
  if (hidden.indexOf(text) > -1) return true
  const parsed = parseSpecItemKey(text)
  if (parsed) {
    return hidden.some(function(item) {
      const other = parseSpecItemKey(item)
      return !!other && canonicalReceiptItemKey(item) === canonicalReceiptItemKey(text)
    })
  }
  const matched = /^item\d+_(name|qty|price|amount)$/.exec(text)
  if (!matched) return false
  return hidden.some(function(item) {
    const other = /^item\d+_(name|qty|price|amount)$/.exec(String(item))
    return !!other && other[1] === matched[1]
  })
}

/** 单个商品的字段值：规格优先用合并后的 spec，其次拼接 spec1/2/3 */
export function receiptItemValue(row, key) {
  if (!row) return ''
  if (key === 'spec') {
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
 * 每件商品占一行，字段按列坐标摆放，均可在编辑页单独选中修改；
 * 规格按「规格名 + N 个规格值」的规格块拆分，名称与每个值各自控制显隐
 *
 * @param {Object} options rows / paperWidth / x / y / fontSize / lineHeight / gap / specBlocks
 * @returns {{attrs:Array, styles:Object, keys:Array, height:number}}
 */
export function buildReceiptItemElements(options) {
  const opts = options || {}
  const rows = (opts.rows || []).filter(hasGoodsValue)
  // 编辑态传入模板保存的规格块（只含属性名），仅用于单行编辑占位；
  // 打印路径不传，多行数据仍按各自单据规格拆分
  const presetBlocks = opts.useSpecBlockPreset && Array.isArray(opts.specBlocks) && opts.specBlocks.length && rows.length === 1
    ? opts.specBlocks
    : null
  const paperWidth = opts.paperWidth > 0 ? opts.paperWidth : 320
  const fontSize = opts.fontSize || 12
  const lineHeight = opts.lineHeight || 1.3
  const x = opts.x != null ? opts.x : 8
  const startY = opts.y != null ? opts.y : 0
  const gap = opts.gap != null ? opts.gap : 4
  const visibleKeys = Array.isArray(opts.visibleKeys) ? opts.visibleKeys.reduce(function(map, key) { map[canonicalReceiptItemKey(key)] = true; return map }, {}) : null
  const hiddenKeys = Array.isArray(opts.hiddenKeys) ? opts.hiddenKeys.reduce(function(map, key) { map[canonicalReceiptItemKey(key)] = true; return map }, {}) : {}
  const styleOverrides = opts.styleOverrides || {}
  const keyVisible = function(key) {
    const canonical = canonicalReceiptItemKey(key)
    const visible = !visibleKeys || !!visibleKeys[canonical]
    return visible && !hiddenKeys[canonical]
  }
  const overrideFor = function(key) { return styleOverrides[canonicalReceiptItemKey(key)] || {} }
  const width = Math.max(120, paperWidth - x * 2)
  const linePx = fontSize * lineHeight
  // 列坐标：按权重分配宽度，最后一列补齐余量
  const cols = []
  let offset = 0
  RECEIPT_ITEM_FIELDS.forEach(function(field, index) {
    const last = index === RECEIPT_ITEM_FIELDS.length - 1
    const colWidth = last ? width - offset : Math.floor(width * field.weight)
    cols.push({ x: offset, width: colWidth })
    offset += colWidth
  })
  const attrs = []
  const styles = {}
  let cursor = startY
  rows.forEach(function(row, rowIndex) {
    const index = rowIndex + 1
    // 规格块在商品行内占整行宽度，与编辑器的规格块画布宽度一致。
    // 若仍放在窄的 spec 列测量、再套用模板里较宽的规格元素，预览会按窄列算出多行高度，
    // 实际却按宽框显示成单行，导致商品行之间出现大段空白。
    const specCol = { x: 12, width: Math.max(90, paperWidth - 40) }
    // 规格块：1 个规格名 + N 个规格值，每个规格值独占一行，均可独立控制显隐
    let specOffset = 0
    const categoryOffsets = {}
    let rowSpecBlocks = presetBlocks || null
    const rowCategoryId = row && (row.categoryId != null ? row.categoryId : row.materialCategoryId)
    if (!rowSpecBlocks && rowCategoryId != null && Array.isArray(opts.specBlocks) && opts.specBlocks.length) {
      const categoryDefinitions = opts.specBlocks.filter(function(block) { return String(block.categoryId) === String(rowCategoryId) })
      if (categoryDefinitions.length) {
        const actualBlocks = parseGoodsSpecBlocks(row)
        const actualValues = {}
        actualBlocks.forEach(function(block) {
          actualValues[block.name] = (actualValues[block.name] || []).concat(block.values || [])
        })
        rowSpecBlocks = categoryDefinitions.map(function(definition) {
        const children = Array.isArray(definition.sourceValues) ? definition.sourceValues : (Array.isArray(definition.values) ? definition.values : [])
          const values = children.length
            ? children.map(function(child) { return (actualValues[child] || [''])[0] || '' })
            : (actualValues[definition.name] || [])
          return Object.assign({}, definition, { values: values, valueNames: definition.values || children, sourceValues: children })
        })
      }
    }
    if (!rowSpecBlocks) {
      // 模板规格是按最大形状定义的通用槽位，不按商品品类生成另一套元素 key；
      // 当前商品只提供自己实际存在的块和值，缺少的槽位自然不生成，也不占高度。
      rowSpecBlocks = parseGoodsSpecBlocks(row).map(function(block, blockIndex) {
        return Object.assign({}, block, { blockNo: blockIndex + 1 })
      })
    }
    const specLayout = (rowSpecBlocks || parseGoodsSpecBlocks(row)).map(function(block, blockIndex) {
      const blockNo = Number(block.blockNo) || blockIndex + 1
      const categoryId = block.categoryId
      const blockIdentity = block.sourceName || block.name
      // 分隔符跟随规格名：不要名称时只剩值，不会留下孤立的冒号
      const nameText = formatSpecName(block.name)
      // 与编辑器 buildCategorySpecEditAttrs 使用相同的 32% / 68% 规格块列宽。
      const nameKey = specNameKey(index, blockNo, categoryId, categoryId != null ? blockIdentity : null)
      const nameOverride = overrideFor(nameKey)
      const labelWidth = nameText
        ? (nameOverride.width > 0 ? nameOverride.width : Math.max(28, Math.round(specCol.width * 0.32)))
        : 0
      const defaultValueWidth = Math.max(8, specCol.width - labelWidth)
      const values = Array.isArray(block.values) ? block.values : []
      // 编辑态多品类规格是互斥的替代布局：每个品类从同一行起点排版，不能把品类 A 的块高累加到品类 B。
      const offsetKey = '__shared__'
      let top = categoryOffsets[offsetKey] || 0
      const blockStart = top
      const valueLayouts = []
      if (!values.length) {
        const height = Math.round(Math.max(linePx, measureTextLines(nameText, labelWidth, fontSize, false, 0) * linePx))
        valueLayouts.push({ value: '', top: top, height: height, visible: keyVisible(nameKey) })
        if (keyVisible(nameKey)) top += height
      } else {
        values.forEach(function(value, valueIndex) {
          const valueIdentity = (block.sourceValues && block.sourceValues[valueIndex]) || (block.valueNames && block.valueNames[valueIndex]) || values[valueIndex]
          const valueKey = specValueKey(index, blockNo, valueIndex + 1, categoryId, categoryId != null ? blockIdentity : null, categoryId != null ? valueIdentity : null)
          const visible = keyVisible(valueKey) && value !== ''
          const override = overrideFor(valueKey)
          const valueWidth = override.width > 0 ? override.width : defaultValueWidth
          const lines = Math.max(
            valueIndex === 0 && nameText && keyVisible(nameKey) ? measureTextLines(nameText, labelWidth, fontSize, false, 0) : 1,
            measureTextLines(value, valueWidth, fontSize, false, 0)
          )
          const height = Math.round(Math.max(linePx, lines * linePx))
          valueLayouts.push({ value: value, top: top, height: height, visible: visible })
          if (visible) top += height
        })
      }
      const nameVisible = keyVisible(nameKey) && (values.length === 0 || values.some(function(value, valueIndex) {
        const valueIdentity = (block.sourceValues && block.sourceValues[valueIndex]) || (block.valueNames && block.valueNames[valueIndex]) || value
        return value !== '' && keyVisible(specValueKey(index, blockNo, valueIndex + 1, categoryId, categoryId != null ? blockIdentity : null, categoryId != null ? valueIdentity : null))
      }))
      const layout = {
        name: block.name,
        nameText: nameText,
        categoryId: categoryId,
        categoryName: block.categoryName,
        blockNo: blockNo,
        sourceName: block.sourceName || block.name,
        sourceValues: block.sourceValues || block.values,
        valueNames: block.valueNames || block.values,
        labelWidth: labelWidth,
        valueWidth: defaultValueWidth,
        top: blockStart,
        height: Math.max(0, top - blockStart),
        nameVisible: nameVisible,
        values: valueLayouts
      }
      categoryOffsets[offsetKey] = top
      specOffset = Math.max(specOffset, top)
      return layout
    })
    // 每行按各列折行后的最大行数确定行高，避免同一行各列各自把下方元素重复下推
    const fields = [
      { key: 'name', label: '商品名称', value: receiptItemValue(row, 'name'), colIndex: 0 },
      { key: 'qty', label: '数量', value: receiptItemValue(row, 'qty'), colIndex: 2 },
      { key: 'price', label: '单价', value: receiptItemValue(row, 'price'), colIndex: 3 },
      { key: 'amount', label: '金额', value: receiptItemValue(row, 'amount'), colIndex: 4 }
    ]
    let hasVisibleItem = specLayout.some(function(block) {
      return block.nameVisible || block.values.some(function(value) { return value.visible })
    })
    let rowHeight = specOffset
    fields.forEach(function(item) {
      item.visible = keyVisible('item' + index + '_' + item.key)
      const override = overrideFor('item' + index + '_' + item.key)
      const itemWidth = override.width > 0 ? override.width : cols[item.colIndex].width
      item.lines = item.visible ? measureTextLines(item.value, itemWidth, fontSize, false, 0) : 0
      if (item.visible) {
        hasVisibleItem = true
        rowHeight = Math.max(rowHeight, item.lines * linePx)
      }
    })
    if (hasVisibleItem) rowHeight = Math.max(rowHeight, linePx)
    rowHeight = Math.round(rowHeight)
    const pushElement = function(key, label, value, style, specMeta) {
      const attr = {
        key: key,
        label: label,
        value: value,
        isCustom: false,
        isReceiptItem: true,
        group: '商品明细'
      }
      if (specMeta && specMeta.categoryId != null) {
        attr.categoryId = String(specMeta.categoryId)
        attr.categoryName = specMeta.categoryName || ''
        attr.specBlockNo = specMeta.blockNo
        attr.specSourceName = specMeta.sourceName || ''
        attr.specSourceValues = specMeta.sourceValues || []
      }
      attrs.push(attr)
      styles[key] = style
    }
    const cellStyle = function(colIndex, height, offsetY) {
      return {
        x: x + cols[colIndex].x,
        y: cursor + (offsetY || 0),
        width: cols[colIndex].width,
        height: height,
        fontSize: fontSize,
        fontWeight: 'normal',
        letterSpacing: 0,
        lineHeight: lineHeight
      }
    }
    pushElement(
      'item' + index + '_name',
      fields[0].label,
      fields[0].value,
      // 元素框高度按本字段内容计算；整行高度仅用于下一条商品的纵向起点。
      // 否则规格列的多行内容会把商品名称/价格等单行框一起撑高，重开模板后看起来像多行框。
      cellStyle(0, Math.max(linePx, fields[0].lines * linePx), 0)
    )
    specLayout.forEach(function(block, blockIndex) {
      const blockNo = Number(block.blockNo) || blockIndex + 1
      const blockIdentity = block.sourceName || block.name
      if (block.labelWidth > 0) {
        const firstVisibleValue = block.values.filter(function(value) { return value.visible })[0]
        pushElement(
          specNameKey(index, blockNo, block.categoryId, block.categoryId != null ? blockIdentity : null),
          block.name,
          block.nameText,
          {
            x: x + specCol.x,
            y: cursor + block.top,
            width: block.labelWidth,
            height: firstVisibleValue ? firstVisibleValue.height : Math.max(linePx, block.height),
            fontSize: fontSize,
            fontWeight: 'normal',
            letterSpacing: 0,
            lineHeight: lineHeight
          },
          { categoryId: block.categoryId, categoryName: block.categoryName, blockNo: blockNo, sourceName: block.sourceName || block.name, sourceValues: block.sourceValues || block.values }
        )
      }
      block.values.forEach(function(layout, valueIndex) {
        if (!layout.value) return
        pushElement(
          specValueKey(index, blockNo, valueIndex + 1, block.categoryId,
            block.categoryId != null ? blockIdentity : null,
            block.categoryId != null ? ((block.sourceValues && block.sourceValues[valueIndex]) || (block.valueNames && block.valueNames[valueIndex]) || layout.value) : null),
          block.valueNames && block.valueNames[valueIndex]
            ? block.valueNames[valueIndex]
            : ('规格值' + blockNo + (valueIndex > 0 ? '·' + (valueIndex + 1) : '')),
          layout.value,
          {
            x: x + specCol.x + block.labelWidth,
            y: cursor + layout.top,
            width: block.valueWidth,
            height: layout.height,
            fontSize: fontSize,
            fontWeight: 'normal',
            letterSpacing: 0,
            lineHeight: lineHeight
          },
          { categoryId: block.categoryId, categoryName: block.categoryName, blockNo: blockNo, sourceName: block.sourceName || block.name, sourceValues: block.sourceValues || block.values }
        )
      })
    })
    fields.slice(1).forEach(function(item) {
      pushElement(
        'item' + index + '_' + item.key,
        item.label,
        item.value,
        cellStyle(item.colIndex, Math.max(linePx, item.lines * linePx), 0)
      )
    })
    cursor += rowHeight + (hasVisibleItem && rowIndex < rows.length - 1 ? gap : 0)
  })
  return {
    attrs: attrs,
    styles: styles,
    keys: attrs.map(function(a) { return a.key }),
    height: rows.length ? cursor - startY : 0
  }
}

/**
 * 小票：把模板里的明细表/商品组合替换成「逐件 + 逐字段」的独立元素
 * 明细表原位作为起点，下方元素按商品行数整体顺移
 *
 * @param {Object} options rows / selectedAttrs / attrStyles / attrLabels / paperWidth / fallbackY / specBlocks
 * @returns {{selectedAttrs:Array, attrStyles:Object, attrLabels:Object, attrs:Array, itemKeys:Array}}
 */
export function replaceReceiptGoodsWithItems(options) {
  const opts = options || {}
  const styles = Object.assign({}, opts.attrStyles || {})
  const labels = Object.assign({}, opts.attrLabels || {})
  const rows = (opts.rows || []).filter(hasGoodsValue)
  const templateKeys = (opts.selectedAttrs || []).filter(isReceiptItemKey).map(canonicalReceiptItemKey)
  const templateStyleKeys = Object.keys(styles).filter(isReceiptItemKey)
  const templateStyles = {}
  const templateLabels = {}
  templateStyleKeys.forEach(function(key) {
    const canonical = canonicalReceiptItemKey(key)
    if (styles[key]) templateStyles[canonical] = styles[key]
    if (Object.prototype.hasOwnProperty.call(labels, key)) templateLabels[canonical] = labels[key]
  })
  const categoryOrigins = {}
  Object.keys(templateStyles).forEach(function(key) {
    const parsed = parseSpecItemKey(key)
    const style = templateStyles[key]
    if (!parsed || parsed.categoryId == null || !style) return
    const categoryKey = String(parsed.categoryId)
    if (categoryOrigins[categoryKey] == null || Number(style.y || 0) < categoryOrigins[categoryKey]) {
      categoryOrigins[categoryKey] = Number(style.y || 0)
    }
  })
  const hasSavedVisibility = templateKeys.length > 0
  const legacyKeyFor = function(key) {
    const parsed = parseSpecItemKey(key)
    if (!parsed || parsed.categoryId == null) return null
    return parsed.kind === 'name'
      ? specNameKey(1, parsed.blockNo)
      : specValueKey(1, parsed.blockNo, parsed.valueNo)
  }
  const savedStyleFor = function(key) {
    const canonical = canonicalReceiptItemKey(key)
    return templateStyles[canonical] || templateStyles[legacyKeyFor(key)] || null
  }
  const savedLabelFor = function(key) {
    const canonical = canonicalReceiptItemKey(key)
    if (Object.prototype.hasOwnProperty.call(templateLabels, canonical)) return templateLabels[canonical]
    const legacy = legacyKeyFor(key)
    return legacy && Object.prototype.hasOwnProperty.call(templateLabels, legacy) ? templateLabels[legacy] : null
  }
  let anchorStyle = null
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
  if (!rows.length) {
    return { selectedAttrs: kept, attrStyles: styles, attrLabels: labels, attrs: [], itemKeys: [] }
  }
  const startY = anchorStyle ? (anchorStyle.y || 0) : (opts.fallbackY || 0)
  const built = buildReceiptItemElements({
    rows: rows,
    specBlocks: opts.specBlocks,
    useSpecBlockPreset: !!opts.useSpecBlockPreset,
    visibleKeys: opts.compactHidden && templateKeys.length ? templateKeys : null,
    hiddenKeys: opts.compactHidden ? opts.hiddenKeys : null,
    styleOverrides: templateStyles,
    paperWidth: opts.paperWidth,
    x: anchorStyle ? (anchorStyle.x || 0) : 8,
    y: startY,
    fontSize: anchorStyle ? (anchorStyle.fontSize || 12) : 12,
    lineHeight: anchorStyle ? (anchorStyle.lineHeight || 1.3) : 1.3
  })
  built.attrs.forEach(function(attr) {
      const canonical = canonicalReceiptItemKey(attr.key)
    const generatedStyle = built.styles[attr.key]
    const savedStyle = savedStyleFor(attr.key)
    if (savedStyle) {
      const firstGenerated = built.styles[canonical]
      const rowOffset = firstGenerated ? generatedStyle.y - firstGenerated.y : 0
      const parsed = parseSpecItemKey(attr.key)
      const categoryOrigin = parsed && parsed.categoryId != null ? categoryOrigins[String(parsed.categoryId)] : null
      const savedY = categoryOrigin != null && parsed
        ? startY + (savedStyle.y - categoryOrigin) + rowOffset
        : savedStyle.y + rowOffset
      styles[attr.key] = Object.assign({}, generatedStyle, savedStyle, {
        y: savedY,
        // 重复商品行的元素高度由当前行文本计算，禁止沿用编辑态占位文本撑大的旧高度。
        height: generatedStyle.height
      })
    } else {
      styles[attr.key] = generatedStyle
    }
    // 明细只显示字段值，不加「商品名称: 」前缀
    labels[attr.key] = savedLabelFor(attr.key) || ''
  })
  const generatedKeys = hasSavedVisibility
    ? built.keys.filter(function(key) {
      const canonical = canonicalReceiptItemKey(key)
      if (templateKeys.indexOf(canonical) > -1) return true
      const parsed = parseSpecItemKey(key)
      const legacy = legacyKeyFor(key)
      if (legacy && templateKeys.indexOf(legacy) > -1) return true
      if (!parsed || parsed.categoryId == null) return false
      const categoryHasRules = Array.isArray(opts.specBlocks) && opts.specBlocks.some(function(block) {
        return String(block.categoryId) === String(parsed.categoryId)
      })
      return !categoryHasRules
    })
    : built.keys
  // 一件商品是一整个纵向组合块。基础行高按自然规格顺序测算，但模板可以保存单项坐标；
  // 坐标覆盖后必须再按该商品实际显示的所有元素求块底部，再决定下一件商品的起点，
  // 否则第一件较低的规格值会与第二件名称/数量等字段交叉。
  const rowGap = opts.itemGap != null ? opts.itemGap : 4
  const rowAttrs = {}
  built.attrs.forEach(function(attr) {
    const parsed = parseSpecItemKey(attr.key)
    const matched = /^item(\d+)_/.exec(String(attr.key))
    const rowNo = parsed ? parsed.rowNo : (matched ? Number(matched[1]) : 0)
    if (rowNo) {
      if (!rowAttrs[rowNo]) rowAttrs[rowNo] = []
      rowAttrs[rowNo].push(attr.key)
    }
  })
  const rowNumbers = Object.keys(rowAttrs).map(Number).sort(function(a, b) { return a - b })
  let previousVisibleBottom = null
  let finalBottom = startY
  rowNumbers.forEach(function(rowNo) {
    const keys = rowAttrs[rowNo]
    let top = Infinity
    let bottom = -Infinity
    keys.forEach(function(key) {
      const canonical = String(key).replace(/^item\d+_/, 'item1_')
      if (generatedKeys.indexOf(key) < 0 && generatedKeys.indexOf(canonical) < 0) return
      const style = styles[key]
      if (!style) return
      top = Math.min(top, Number(style.y) || 0)
      bottom = Math.max(bottom, (Number(style.y) || 0) + (Number(style.height) || 0))
    })
    if (!isFinite(top) || !isFinite(bottom)) return
    const shift = previousVisibleBottom == null ? 0 : Math.max(0, previousVisibleBottom + rowGap - top)
    if (shift > 0) {
      keys.forEach(function(key) {
        const style = styles[key]
        if (style) styles[key] = Object.assign({}, style, { y: (Number(style.y) || 0) + shift })
      })
      top += shift
      bottom += shift
    }
    previousVisibleBottom = bottom
    finalBottom = Math.max(finalBottom, bottom)
  })
  built.height = Math.max(0, finalBottom - startY)
  // 原明细表占位高度与逐件组合块实际总高度差，下方元素整体顺移。
  const delta = built.height - (anchorStyle ? (anchorStyle.height || 0) : 0)
  if (delta !== 0) {
    kept.forEach(function(key) {
      const style = styles[key]
      if (style && (style.y || 0) >= startY - 0.5) {
        styles[key] = Object.assign({}, style, { y: (style.y || 0) + delta })
      }
    })
  }
  return {
    selectedAttrs: kept.concat(generatedKeys),
    attrStyles: styles,
    attrLabels: labels,
    attrs: built.attrs,
    itemKeys: built.keys
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

/** 文本在指定字号下的渲染宽度（px） */
function measureTextWidth(text, fontSize, bold, letterSpacing) {
  const value = text == null ? '' : String(text)
  if (!value) return 0
  const ctx = measureCtx()
  if (!ctx) return value.length * (fontSize || 12)
  ctx.font = (bold ? 'bold ' : '') + (fontSize || 12) + 'px sans-serif'
  return ctx.measureText(value).width + value.length * (letterSpacing || 0)
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
/** 商品行的规格块：优先 SKU 的 spec_json，其次带名称的 spec 文本，最后 spec1/2/3 */
function productSpecBlocks(row) {
  const blocks = specBlocksFromRow(row)
  if (blocks.some(function(block) { return block.name })) return blocks
  return parseGoodsSpecBlocks(row)
}

/**
 * 行没有类目信息时，按规格名 / 已选规格值判断映射项是否命中该行
 * 用于地址识别等多商品场景（行数据缺少 categoryId 时仍能各取各的规格）
 */
function defMatchesRowSpec(def, blocks) {
  if (!def) return false
  const list = Array.isArray(blocks) ? blocks : []
  const specName = String(def.sourceName || def.name || '').trim()
  const selected = (Array.isArray(def.values) ? def.values : []).map(String)
  return list.some(function(block) {
    if (!block) return false
    const blockName = String(block.name || '').trim()
    if (specName && blockName && blockName === specName) return true
    const values = (block.values || []).map(String)
    return selected.some(function(value) { return values.indexOf(value) > -1 })
  })
}

/**
 * 规格块按位置对应取值：模板第 N 个规格块取商品第 N 个规格块的值。
 * 元素 key 与编辑页 buildCategorySpecEditAttrs 保持一致，供预览与打印直接合并进业务值。
 */
export function buildSpecValuesFromRows(rows, specBlocks, specMappingReady) {
  const texts = {}
  // 多商品：每个商品的类别/规格各占一行，逐行追加（后一行不能覆盖前一行）
  const categoryLines = []
  const specLines = []
  const pushLine = function(lines, text) {
    const value = text == null ? '' : String(text).trim()
    if (value) lines.push(value)
  }
  const defs = Array.isArray(specBlocks) ? specBlocks : []
  ;(Array.isArray(rows) ? rows : []).forEach(function(row, rowIndex) {
    if (!row) return
    const index = rowIndex + 1
    const blocks = productSpecBlocks(row)
    // 空字符串的类目 ID 视为「无类目」，避免命中失败或误配到其它品类
    const rawCategoryId = row.categoryId != null ? row.categoryId : row.materialCategoryId
    const rowCategoryId = rawCategoryId != null && String(rawCategoryId).trim() !== '' ? rawCategoryId : null
    const rowCategoryName = row.categoryName != null ? String(row.categoryName).trim() : ''
    // 品类规格按商品所属品类取值：
    // 映射表模式严格匹配行类别（一级类目 / 二级类目），无法确定类别时不输出；
    // 旧模板（未建立映射表）保留全集兜底。
    const rowDefs = defs.filter(function(def) {
      if (!specMappingReady) {
        if (def.categoryId == null || rowCategoryId == null) return true
        if (String(def.categoryId) === String(rowCategoryId)) return true
        return Array.isArray(def.categoryMemberIds) &&
          def.categoryMemberIds.map(String).indexOf(String(rowCategoryId)) > -1
      }
      if (def.categoryId != null && rowCategoryId != null) {
        if (String(def.categoryId) === String(rowCategoryId)) return true
        return Array.isArray(def.categoryMemberIds) &&
          def.categoryMemberIds.map(String).indexOf(String(rowCategoryId)) > -1
      }
      if (rowCategoryId == null) {
        // 行没有类目属性：先按品类名兜底，再按规格名 / 已选规格值回退命中
        if (rowCategoryName) return String(def.categoryName || '').trim() === rowCategoryName
        return defMatchesRowSpec(def, blocks)
      }
      return false
    })
    // 归一化标签：类别 = 一级类目名，规格 = 映射表配置的规格值（/ 拼接）
    const categoryDef = rowDefs.filter(function(def) { return def.categoryName })[0]
    if (categoryDef) {
      if (specMappingReady) {
        // 已选规格驱动：只有模板选中的规格值才加载业务数据
        pushLine(categoryLines, categoryDef.categoryName)
        pushLine(specLines, resolveSpecTextFromDefs(rowDefs, blocks))
      } else {
        const resolved = resolveCategorySpecText(categoryDef.categoryName, blocks)
        if (resolved) {
          pushLine(categoryLines, resolved.categoryName)
          pushLine(specLines, resolved.specText)
        }
      }
    }
    // 已建立映射表的模板只渲染已选规格；旧模板按商品行规格块顺序兜底
    const slots = specMappingReady ? rowDefs : (rowDefs.length ? rowDefs : blocks.map(function(block, i) {
      return Object.assign({}, block, { blockNo: i + 1 })
    }))
    slots.forEach(function(def, defIndex) {
      const blockNo = Number(def.blockNo) || defIndex + 1
      // 映射表模式按规格名精确匹配商品行的规格块；旧模板按位置对应
      const namedBlock = specMappingReady
        ? blocks.filter(function(item) { return item && item.name === (def.sourceName || def.name) })[0]
        : null
      const block = namedBlock || blocks[blockNo - 1] || blocks[defIndex] || { name: '', values: [] }
      const categoryId = def.categoryId
      const identity = def.sourceName || def.name
      const sourceValues = Array.isArray(def.sourceValues)
        ? def.sourceValues
        : (Array.isArray(def.values) ? def.values : [])
      texts[specNameKey(index, blockNo, categoryId, categoryId != null ? identity : null)] =
        formatSpecName(block.name || def.name)
      // 一级类目映射表：只有选中的规格值才读取业务数据
      const mappedValues = specMappingReady
        ? pickSpecValuesFromDefs([def], blocks)
        : pickMappedSpecValues(def.categoryName, blocks)
      ;(specMappingReady ? mappedValues : (mappedValues || block.values || [])).forEach(function(value, valueIndex) {
        let slot = sourceValues.indexOf(value)
        if (slot < 0) slot = valueIndex
        const valueIdentity = sourceValues[slot] != null ? sourceValues[slot] : value
        const valueKey = specValueKey(index, blockNo, slot + 1, categoryId,
          categoryId != null ? identity : null, categoryId != null ? valueIdentity : null)
        texts[valueKey] = value
      })
    })
  })
  if (categoryLines.length) texts[LABEL_CATEGORY_KEY] = categoryLines.join('\n')
  if (specLines.length) texts[LABEL_SPEC_KEY] = specLines.join('\n')
  return texts
}

/**
 * 商品行涉及的品类 ID（去重）：用于打印时只输出本页商品所属品类的规格块
 */
export function collectRowCategoryIds(rows) {
  const ids = []
  ;(Array.isArray(rows) ? rows : []).forEach(function(row) {
    if (!row) return
    const id = row.categoryId != null ? row.categoryId : row.materialCategoryId
    if (id == null || id === '') return
    if (ids.indexOf(String(id)) === -1) ids.push(String(id))
  })
  return ids
}

export function resolveLabelLayout(options) {
  const opts = options || {}
  const labels = opts.labels || {}
  const sourceRows = opts.goodsRows || (opts.values && opts.values.goodsRows) || []
  const values = Object.assign({}, opts.values || {}, buildSpecValuesFromRows(
    sourceRows,
    opts.specBlocks,
    opts.specMappingReady === true
  ), {
    goodsRows: sourceRows
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
    // 类别 / 规格为归一化标签，直接输出解析值，不再叠加属性名前缀
    const isNormalizedKey = key === LABEL_CATEGORY_KEY || key === LABEL_SPEC_KEY
    // 规格名/子属性名本身就是显示文案，不再叠加 attrLabels 前缀，避免编辑态重复文本撑高元素。
    texts[key] = (opts.editorMode && isSpecItemKey(key)) || isNormalizedKey
      ? value
      : (labels[key] ? labels[key] + ': ' + value : value)
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
    categoryIds: collectRowCategoryIds(sourceRows),
    goodsText: goods.goodsText,
    goodsDisplay: goods.goodsDisplay
  }
}
