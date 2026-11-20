import { normalizeSpecBlocks, specNameKey, specValueKey } from './goodsSpecBlocks'
import { buildSpecBlockItems, resolveSpecBlockLines } from './specBlockLayout'
import { buildLabelAttrs } from '@/views/material/utils/materialDisplay'

function firstValue(source, keys) {
  if (!source) return null
  for (let i = 0; i < keys.length; i++) {
    const value = source[keys[i]]
    if (value != null && value !== '') return value
  }
  return null
}

function categoryOnlyRow(row) {
  const source = row && row.raw ? row.raw : row
  return {
    categoryId: firstValue(source, ['categoryId', 'materialCategoryId', 'category_id'])
  }
}

function hasProductData(row) {
  if (!row) return false
  const product = row.material || row.product || row
  const hasIdentifier = ['materialId', 'material_id', 'materialName', 'productName', 'skuId', 'skuCode', 'specValues', 'specDisplay']
    .some(function(key) { return product[key] != null && product[key] !== '' })
  if (hasIdentifier) return true
  const hasCategory = firstValue(product, ['categoryId', 'materialCategoryId', 'category_id']) != null
  const hasName = firstValue(product, ['name', 'title']) != null
  return hasCategory && hasName
}

function isProductField(field) {
  return field && (field.group === '商品基础属性' || field.group === 'SKU属性')
}

/** 编辑器字段清单只存结构；value 与业务记录、SKU、单据值一律不进入快照。 */
export function normalizeEditorData(data) {
  const source = data || {}
  const fields = Array.isArray(source.fields) ? source.fields : []
  return {
    version: 1,
    title: source.title == null ? '' : String(source.title),
    fields: fields.filter(function(field) { return field && field.key != null && field.key !== '' }).map(function(field) {
      const item = {
        key: String(field.key),
        label: field.label == null ? String(field.key) : String(field.label),
        type: field.type || 'text',
        isCustom: !!field.isCustom,
        group: field.group || '',
        isPage: !!field.isPage
      }
      if (field.comment) item.comment = String(field.comment)
      if (field.categoryId != null) item.categoryId = String(field.categoryId)
      if (field.categoryName) item.categoryName = String(field.categoryName)
      if (Array.isArray(field.categoryMemberIds) && field.categoryMemberIds.length) {
        item.categoryMemberIds = field.categoryMemberIds.map(function(id) { return String(id) })
      }
      return item
    }),
    specBlocks: normalizeSpecBlocks(source.specBlocks)
  }
}

export function buildEditorData(fields, options) {
  const opts = options || {}
  return normalizeEditorData({ fields: fields, title: opts.title, specBlocks: opts.specBlocks })
}

/** Keep only structure metadata when opening a template editor from a business preview. */
export function buildTemplateEditContext(options) {
  const opts = options || {}
  const record = opts.record || {}
  const currentFields = opts.fields || (opts.editorData && opts.editorData.fields) || []
  const fields = currentFields.slice()
  const knownFields = {}
  fields.forEach(function(field) { if (field && field.key != null) knownFields[String(field.key)] = true })
  const appendProductFields = function(list) {
    ;(list || []).forEach(function(field) {
      if (!isProductField(field) || field.key == null || knownFields[String(field.key)]) return
      fields.push(field)
      knownFields[String(field.key)] = true
    })
  }
  if (opts.includeProductAttributes) {
    appendProductFields(opts.productFields)
    ;[opts.productRows, opts.rows, record.goodsRows, record.detailRows, record.rows].forEach(function(list) {
      ;(Array.isArray(list) ? list : []).forEach(function(row) {
        const source = row && row.raw ? row.raw : row
        const product = source && (source.material || source.product) ? (source.material || source.product) : source
        if (!hasProductData(product)) return
        appendProductFields(buildLabelAttrs(product, (source && source.sku) || (row && row.sku) || product))
      })
    })
  }
  const categoryRows = []
  ;[opts.rows, record.goodsRows, record.detailRows, record.rows].forEach(function(rows) {
    ;(Array.isArray(rows) ? rows : []).forEach(function(row) {
      categoryRows.push(categoryOnlyRow(row))
    })
  })
  return {
    record: {
      categoryId: firstValue(record, ['categoryId', 'materialCategoryId', 'category_id']),
      goodsRows: categoryRows
    },
    sku: {},
    editorData: buildEditorData(fields, {
      title: opts.title || (opts.editorData && opts.editorData.title),
      specBlocks: opts.specBlocks || (opts.editorData && opts.editorData.specBlocks)
    })
  }
}

/** Convert category spec blocks into placeholder attributes for the shared editor canvas. */
export function buildCategorySpecEditAttrs(specBlocks, options) {
  const opts = options || {}
  const blocks = normalizeSpecBlocks(specBlocks)
  const rowNo = opts.rowNo || 1
  const startY = opts.startY || 0
  const width = opts.width || 240
  const fontSize = opts.fontSize || 12
  const fontWeight = opts.fontWeight || 'normal'
  const letterSpacing = opts.letterSpacing || 0
  const lineHeight = opts.lineHeight || 1.3
  const x = opts.x == null ? 20 : opts.x
  const attrs = []
  const styles = {}
  const lineHeightPx = Math.max(1, Math.round(fontSize * lineHeight))
  // 多品类规格：每个品类一段（品类名一行），段内每个规格块独占一行，品类之间空一行
  let y = startY
  let currentCategory = null

  blocks.forEach(function(block, blockIndex) {
    const categoryId = block.categoryId
    const categoryName = block.categoryName || ''
    const categoryKey = categoryId == null ? '' : String(categoryId)
    const firstOfCategory = categoryKey !== currentCategory
    if (firstOfCategory && currentCategory !== null) y += lineHeightPx
    currentCategory = categoryKey
    const blockNo = Number(block.blockNo) || blockIndex + 1
    const blockIdentity = block.sourceName || block.name
    const nameKey = specNameKey(rowNo, blockNo, categoryId, categoryId != null ? blockIdentity : null)
    const specMeta = {
      categoryId: categoryId == null ? null : String(categoryId),
      categoryName: categoryName,
      // 一级类目下的全部类目 id：业务明细行指向二级类时也能匹配到本段规格
      categoryMemberIds: Array.isArray(block.categoryMemberIds) ? block.categoryMemberIds.map(function(id) { return String(id) }) : [],
      specBlockNo: blockNo,
      specSourceName: block.sourceName || block.name,
      specSourceValues: Array.isArray(block.sourceValues) ? block.sourceValues.slice() : block.values.slice()
    }
    const items = buildSpecBlockItems({
      nameKey: nameKey,
      categoryText: firstOfCategory ? categoryName : '',
      nameText: block.name ? block.name + '：' : '',
      // 名称空白时用可显示的属性名占位（编辑页浅灰显示，不打印）
      namePlaceholder: block.name ? '' : (block.sourceName || categoryName || '规格'),
      nameWrap: false,
      values: block.values.map(function(value, index) {
        return { key: specValueKey(rowNo, blockNo, index + 1, categoryId, categoryId != null ? blockIdentity : null, categoryId != null ? ((block.sourceValues && block.sourceValues[index]) || value) : null), text: value }
      })
    })
    // 块宽先按自然宽度排版；超过可用宽度时按块宽内部折行
    const natural = resolveSpecBlockLines({ items: items, fontSize: fontSize, fontWeight: fontWeight, letterSpacing: letterSpacing, lineHeight: lineHeight })
    const blockWidth = Math.min(Math.max(natural.minWidth, natural.width), width)
    const layout = resolveSpecBlockLines({ items: items, width: blockWidth, fontSize: fontSize, fontWeight: fontWeight, letterSpacing: letterSpacing, lineHeight: lineHeight })
    const blockX = x
    const blockY = y
    attrs.push(Object.assign({ key: nameKey, label: block.name, value: block.name, isCustom: false, group: '商品规格' }, specMeta))
    // 块几何只挂在属性名（锚点）上：位置、块宽、对齐、折行
    styles[nameKey] = {
      x: blockX,
      y: blockY,
      width: layout.width,
      height: layout.height,
      fontSize: fontSize,
      fontWeight: fontWeight,
      letterSpacing: letterSpacing,
      lineHeight: lineHeight,
      textAlign: 'left',
      specWrap: false
    }
    block.values.forEach(function(value, valueIndex) {
      const valueIdentity = (block.sourceValues && block.sourceValues[valueIndex]) || value
      const valueKey = specValueKey(rowNo, blockNo, valueIndex + 1, categoryId, categoryId != null ? blockIdentity : null, categoryId != null ? valueIdentity : null)
      attrs.push(Object.assign({ key: valueKey, label: value, value: value, isCustom: false, group: '商品规格' }, specMeta))
      // 规格值不单独定位/定宽，全部由锚点块几何派生
      styles[valueKey] = {
        x: blockX,
        y: blockY,
        width: 0,
        height: 0,
        fontSize: fontSize,
        fontWeight: fontWeight,
        letterSpacing: letterSpacing,
        lineHeight: lineHeight
      }
    })
    y += layout.height
  })
  return { attrs: attrs, styles: styles, keys: attrs.map(function(attr) { return attr.key }), height: blocks.length ? (y - startY) : 0 }
}
