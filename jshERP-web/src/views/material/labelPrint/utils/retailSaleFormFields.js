/**
 * 销售开单表单数据提取模块（唯一数据源）
 *
 * 销售开单页面（RetailSaleOrderList）的字段定义与取值统一走本模块：
 * 1. buildRetailSaleAttrs：编辑页「表单数据」按钮、打印预览共用的字段结构
 * 2. extractRetailSaleValues：结算后写入打印快照的字段值
 *
 * 字段 key 沿用小票（receipt）前缀，保证已有小票模板无需迁移即可识别新增字段。
 */
import { buildProductFormAttrs } from './productFormFields'
import { appendCategorySpecAttrs } from './categorySpec'

/** 销售开单表单字段定义（顺序即面板展示顺序） */
export const RETAIL_SALE_FIELD_DEFS = [
  { key: 'rc_title', label: '小票标题', group: '单据头' },
  { key: 'rc_no', label: '单号', group: '单据头' },
  { key: 'rc_date', label: '日期', group: '单据头' },
  { key: 'rc_member', label: '会员', group: '单据头' },
  { key: 'rc_depot', label: '仓库', group: '单据头' },
  { key: 'rc_pay_type', label: '收款类型', group: '单据头' },
  { key: 'rc_table', label: '商品明细表', group: '商品明细' },
  { key: 'rc_qty_total', label: '数量合计', group: '金额合计' },
  { key: 'rc_goods_amount', label: '商品金额', group: '金额合计' },
  { key: 'rc_discount', label: '折扣', group: '金额合计' },
  { key: 'rc_discount_amount', label: '优惠金额', group: '金额合计' },
  { key: 'rc_amount_total', label: '金额合计', group: '金额合计' },
  { key: 'rc_remark', label: '备注', group: '其它' },
  { key: 'rc_footer', label: '落款', group: '其它' }
]

function toText(value) {
  return value == null ? '' : String(value)
}

function toMoney(value) {
  var text = toText(value).trim()
  if (!text) return ''
  return /^[¥￥]/.test(text) ? text : '¥' + text
}

/** 字段结构：编辑页与打印预览共用，value 由业务数据按需注入；含商品基础属性与 SKU 属性 */
export function buildRetailSaleAttrs(record) {
  var r = record || {}
  var list = RETAIL_SALE_FIELD_DEFS.map(function(def) {
    return {
      key: def.key,
      label: def.label,
      value: toText(r[def.key]),
      isCustom: false,
      group: def.group
    }
  })
  var exists = {}
  list.forEach(function(attr) { exists[attr.key] = true })
  buildProductFormAttrs(r.goodsRows).forEach(function(attr) {
    if (exists[attr.key]) return
    exists[attr.key] = true
    list.push(attr)
  })
  // 类别 / 规格：与标签、地址打印共用同一套规格映射器
  return appendCategorySpecAttrs(list)
}

/**
 * 从销售开单页面数据提取打印字段值
 *
 * @param {Object} source 页面结算数据
 *   number 单号 / date 日期 / memberName 会员 / depotName 仓库 / payType 收款类型
 *   qtyTotal 数量合计 / goodsAmount 商品金额 / discount 折扣 /
 *   discountAmount 优惠金额 / amountTotal 应收金额 / remark 备注 / footer 落款
 * @returns {Object} rc_* 字段值
 */
export function extractRetailSaleValues(source) {
  var s = source || {}
  return {
    rc_title: toText(s.title) || '零售小票',
    rc_no: toText(s.number),
    rc_date: toText(s.date),
    rc_member: toText(s.memberName),
    rc_depot: toText(s.depotName),
    rc_pay_type: toText(s.payType),
    rc_qty_total: toText(s.qtyTotal),
    rc_goods_amount: toMoney(s.goodsAmount),
    rc_discount: toText(s.discount),
    rc_discount_amount: toMoney(s.discountAmount),
    rc_amount_total: toMoney(s.amountTotal),
    rc_remark: toText(s.remark),
    rc_footer: toText(s.footer) || '谢谢惠顾，欢迎再次光临'
  }
}
