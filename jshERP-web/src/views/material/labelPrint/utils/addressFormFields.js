import { GOODS_TABLE_KEY } from './goodsLabelLayout'
import { buildProductFormAttrs, mergeProductAttrValues } from './productFormFields'

/** 地址打印表单字段（唯一数据源）：key 与地址识别页面表单字段一致 */
export const ADDRESS_FIELD_DEFS = [
  { key: 'contacts', label: '联系人', type: 'input' },
  { key: 'telephone', label: '电话', type: 'textarea' },
  { key: 'account', label: '账号', type: 'input' },
  { key: 'platform', label: '平台', type: 'input' },
  { key: 'remark', label: '备注', type: 'input' },
  { key: 'address', label: '地址', type: 'textarea' },
  { key: 'goods', label: '商品信息', type: 'input' },
  { key: GOODS_TABLE_KEY, label: '商品明细表', type: 'table' }
]

/** 地址打印表单标题：编辑页元素与模板签名均以此为准 */
export const ADDRESS_FORM_TITLE = '地址打印'

/** 地址识别面板展示的字段（平台由面板单独渲染，不在行字段里） */
const PANEL_KEYS = ['contacts', 'telephone', 'account', 'remark', 'address']
export const ADDRESS_PANEL_FIELDS = ADDRESS_FIELD_DEFS
  .filter(function(field) { return PANEL_KEYS.indexOf(field.key) > -1 })
  .map(function(field) { return { field: field.key, label: field.label, type: field.type } })

/** 地址打印属性清单：编辑页与打印预览共用；商品信息固定并入商品基础属性/SKU 属性 */
export function buildAddressAttrs(record) {
  var r = record || {}
  var list = ADDRESS_FIELD_DEFS.map(function(field) {
    var isTable = field.key === GOODS_TABLE_KEY
    return {
      key: field.key,
      label: field.label,
      value: isTable ? '' : (r[field.key] == null ? '' : String(r[field.key])),
      isCustom: false,
      group: '地址信息'
    }
  })
  var exists = {}
  list.forEach(function(attr) { exists[attr.key] = true })
  var rows = Array.isArray(r.goodsRows) ? r.goodsRows : []
  buildProductFormAttrs(rows).forEach(function(attr) {
    if (exists[attr.key]) return
    exists[attr.key] = true
    list.push(attr)
  })
  return list
}

/**
 * 从地址识别页面数据提取打印字段值（唯一提取入口，快照与预览共用）
 *
 * @param {Object} source 页面数据
 *   form 表单输入（contacts/telephone/account/remark/address），
 *   goods 商品信息文案，goodsRows 商品多行数据，platform 平台
 * @returns {Object} 地址打印字段值（含 goodsRows 明细行）
 */
export function extractAddressValues(source) {
  var s = source || {}
  var form = s.form || {}
  var values = {}
  ADDRESS_PANEL_FIELDS.forEach(function(field) {
    values[field.field] = form[field.field] || ''
  })
  values.goods = s.goods == null ? '' : String(s.goods)
  values.goodsRows = Array.isArray(s.goodsRows) ? s.goodsRows : []
  values.platform = s.platform || ''
  // 商品字段统一约定：快照 values 必须并入商品基础属性 / SKU 属性真实值
  return mergeProductAttrValues(values, values.goodsRows)
}
