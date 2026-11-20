/**
 * 表单字段统一入口
 *
 * 编辑页（LabelEditMobile）与打印预览页（LabelPrintModal）都从这里取字段清单，
 * 保证两页字段、分组、取值规则完全一致。
 * page_no（分页标签）属于模板元素，由调用方按需追加，不在配置内。
 */
import labelForm from './labelForm'
import deliveryForm from './deliveryForm'
import receiptForm from './receiptForm'
import memberForm from './memberForm'
import addressForm from './addressForm'

const FORM_CONFIGS = {
  label: labelForm,
  delivery: deliveryForm,
  receipt: receiptForm,
  member: memberForm,
  address: addressForm
}

/** 按模板类型取表单配置，未知类型回退商品标签 */
export function getFormFieldConfig(type) {
  return FORM_CONFIGS[type] || FORM_CONFIGS.label
}

/**
 * 构建表单全域字段清单
 * @param {string} type 模板类型
 * @param {Object} context { record, sku }
 * @returns {Array} [{ key, label, value, isCustom, group }]
 */
export function buildFormFieldAttrs(type, context) {
  var config = getFormFieldConfig(type)
  var list = config.buildAttrs(context || {}) || []
  return list.map(function(attr) { return Object.assign({}, attr) })
}

/** 字段结构签名：字段集合或显示名变化时签名变化，供后续「重建字段」比对使用 */
export function buildFormFieldSignature(type, context) {
  return buildFormFieldAttrs(type, context)
    .map(function(attr) { return attr.key + ':' + attr.label })
    .join('|')
}
