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
