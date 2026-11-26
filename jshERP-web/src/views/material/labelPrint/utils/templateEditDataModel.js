import { normalizeSpecBlocks } from './goodsSpecBlocks'
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

function buildEditorData(fields, options) {
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
