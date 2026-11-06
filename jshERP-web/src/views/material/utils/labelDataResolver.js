/**
 * 标签数据解析：预览与打印共用的唯一数据入口
 *
 * 有业务数据源时走向后端适配器（统一 JSON），
 * 没有数据源（旧模板/本地商品标签）时回退商品属性，保证历史模板继续可用。
 */
import { buildLabelAttrs } from './materialDisplay'
import { fetchLabelData, fetchProviderFields } from './labelProviderApi'

function toText(value) {
  if (value == null) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

/** 数组明细行统一键名（name/quantity/spec/price/amount） */
export function normalizeRows(items) {
  if (!items || !items.length) return []
  return items.map(function(item) {
    var row = item || {}
    return {
      name: toText(row.name || row.materialName || row.productName),
      spec: toText(row.spec || row.specDisplay),
      qty: toText(row.quantity != null ? row.quantity : row.qty),
      price: toText(row.price),
      amount: toText(row.amount),
      raw: row
    }
  })
}

/**
 * 解析模板所需数据
 *
 * @param {Object} options
 *   provider    数据源标识（模板 dataSource.provider）
 *   fields      数据源字段元数据（可选，未传时按 provider 拉取）
 *   documentId  业务单据 ID（商品 ID / 单据 ID）
 *   subId       子 ID（如 SKU ID，可选）
 *   record/sku  无数据源时的本地属性来源
 * @returns {Promise<{attrs, values, rows, fields}>}
 */
export function resolveTemplateData(options) {
  var opts = options || {}
  var provider = opts.provider
  var documentId = opts.documentId

  if (provider && documentId != null && documentId !== '') {
    var fieldTask = opts.fields && opts.fields.length
      ? Promise.resolve(opts.fields)
      : fetchProviderFields(provider)
    return Promise.all([fieldTask, fetchLabelData(provider, documentId, opts.subId)]).then(function(result) {
      var fields = result[0] || []
      var data = result[1] || {}
      var values = {}
      var attrs = fields.map(function(field) {
        values[field.key] = toText(data[field.key])
        return {
          key: field.key,
          label: field.label || field.key,
          value: values[field.key],
          type: field.type || 'text',
          isCustom: false
        }
      })
      // 适配器返回但未登记元数据的字段也补上，避免数据丢失
      Object.keys(data).forEach(function(key) {
        if (key === 'items' || values[key] != null) return
        values[key] = toText(data[key])
        attrs.push({ key: key, label: key, value: values[key], type: 'text', isCustom: false })
      })
      return {
        attrs: attrs,
        values: values,
        rows: normalizeRows(data.items),
        fields: fields
      }
    })
  }

  var localAttrs = buildLabelAttrs(opts.record, opts.sku)
  var localValues = {}
  localAttrs.forEach(function(attr) {
    localValues[attr.key] = attr.value == null ? '' : String(attr.value)
  })
  return Promise.resolve({
    attrs: localAttrs,
    values: localValues,
    rows: normalizeRows(opts.rows),
    fields: []
  })
}
