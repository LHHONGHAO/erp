/**
 * 商品表单字段提取模块（有商品信息的表单共用）
 *
 * 提取「商品基础属性 + SKU 属性」两类字段，供表单数据的元素面板使用：
 * 1. 先取商品列的完整字段结构（值可空），保证面板字段齐全；
 * 2. 再按业务行回填真实值，同 key 以首个非空值为准。
 *
 * 使用方：地址打印、发货单、销售开单（小票）等含商品信息的表单。
 */

import { buildLabelAttrs } from '@/views/material/utils/materialDisplay'

const PRODUCT_GROUPS = ['商品基础属性', 'SKU属性']

/** 规格值等字段可能是 JSON 字符串，只接受对象形式的 SKU 行 */
function normalizeSku(sku) {
  if (!sku || typeof sku !== 'object' || Array.isArray(sku)) return {}
  return sku
}

/** SKU 兼容解析：对象直接用，JSON 字符串尝试解析，其余按空 SKU 处理 */
function normalizeSkuDeep(sku) {
  if (sku && typeof sku === 'object' && !Array.isArray(sku)) return sku
  if (typeof sku === 'string') {
    var text = sku.trim()
    if (text.charAt(0) === '{' || text.charAt(0) === '[') {
      try {
        var parsed = JSON.parse(text)
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed
      } catch (e) { /* 非 JSON 字符串按空 SKU 处理 */ }
    }
  }
  return {}
}

/**
 * 从业务明细行提取商品字段值（有商品数据的表单统一约定）
 *
 * 约定：所有带商品数据的表单，生成打印快照 values 时必须并入商品基础属性 +
 * SKU 属性的真实值（多行取首个非空值），预览端 applySnapshotValues 按 key 回填元素。
 *
 * @param {Array} rows 业务明细行（depotItem / 购物车行均可），自动解析行内 sku JSON
 * @returns {Object} { key: value } 仅含非空值
 */
export function extractProductAttrValues(rows) {
  var values = {}
  var normalized = (Array.isArray(rows) ? rows : []).map(function(row) {
    if (!row) return null
    var source = row.raw || row
    // 明细行字段归一：depotItem 行商品名为 materialName，统一映射到商品列 name
    var item = Object.assign({}, source)
    if (item.name == null && item.materialName != null) item.name = item.materialName
    return { raw: item, sku: normalizeSkuDeep(row.sku != null ? row.sku : source.sku) }
  })
  buildProductFormAttrs(normalized).forEach(function(attr) {
    if (attr && attr.key && attr.value !== '' && attr.value != null) values[attr.key] = attr.value
  })
  return values
}

/**
 * 把商品字段快照值并入既有 values（有商品数据的表单统一入口）
 *
 * 约定：表单字段优先，商品字段只补尚未定义的 key，不覆盖业务值。
 * 发货单、销售开单、地址打印等表单的 extract*Values 末尾统一调用本函数。
 *
 * @param {Object} values 表单字段值
 * @param {Array} rows 业务明细行
 * @returns {Object} 合并后的 values（原地修改并返回）
 */
export function mergeProductAttrValues(values, rows) {
  var target = values || {}
  var productValues = extractProductAttrValues(rows)
  Object.keys(productValues).forEach(function(key) {
    if (target[key] === undefined) target[key] = productValues[key]
  })
  return target
}

/**
 * 构建商品基本信息 + SKU 信息字段
 *
 * @param {Array} rows 业务明细行，支持 { raw, sku } 或直接传商品记录
 * @returns {Array} [{ key, label, value, isCustom, group }]
 */
export function buildProductFormAttrs(rows) {
  var list = []
  var byKey = {}
  var add = function(attr) {
    if (!attr || !attr.key || byKey[attr.key]) return
    if (PRODUCT_GROUPS.indexOf(attr.group) === -1) return
    byKey[attr.key] = attr
    list.push(attr)
  }
  buildLabelAttrs({}, {}).forEach(add)
  ;(Array.isArray(rows) ? rows : []).forEach(function(row) {
    if (!row) return
    var source = row.raw || row
    var sku = normalizeSku(row.sku || row.skuRow || source.sku)
    buildLabelAttrs(source, sku).forEach(function(attr) {
      if (byKey[attr.key]) {
        if (attr.value !== '' && byKey[attr.key].value === '') byKey[attr.key].value = attr.value
        return
      }
      add(attr)
    })
  })
  return list
}
