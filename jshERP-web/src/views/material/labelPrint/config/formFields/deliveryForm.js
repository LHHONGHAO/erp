/**
 * 发货单表单字段配置（唯一字段白名单）
 * key 与发货单业务值 / 模板中保存的字段完全一致，顺序与分组决定元素面板展示顺序。
 * 含商品信息：末尾并入商品基础属性 + SKU 属性字段。
 */
import { buildProductFormAttrs, mergeProductAttrValues } from '@/views/material/labelPrint/utils/productFormFields'
import { appendCategorySpecAttrs } from '@/views/material/labelPrint/utils/categorySpec'

const DELIVERY_FORM_TITLE = '发货单'

const DELIVERY_STATUS_MAP = { '0': '未审核', '1': '已审核', '2': '已完成', '3': '已关闭' }

function toNum(v) { var n = Number(v); return isNaN(n) ? 0 : n }
function toMoney(v) { return toNum(v).toFixed(2) }

const DELIVERY_FIELDS = [
  { key: 'dn_title', label: '发货单标题', group: '单据头' },
  { key: 'dn_no', label: '单号', group: '单据头' },
  { key: 'dn_date', label: '单据日期', group: '单据头' },
  { key: 'dn_member', label: '会员', group: '单据头' },
  { key: 'dn_receiver', label: '收货人', group: '单据头' },
  { key: 'dn_phone', label: '联系电话', group: '单据头' },
  { key: 'dn_address', label: '收货地址', group: '单据头' },
  { key: 'dn_depot', label: '仓库', group: '单据头' },
  { key: 'dn_paytype', label: '收款类型', group: '单据头' },
  { key: 'dn_account', label: '收款账户', group: '单据头' },
  { key: 'dn_operator', label: '操作员', group: '单据头' },
  { key: 'dn_salesman', label: '业务员', group: '单据头' },
  { key: 'dn_remark', label: '备注', group: '单据头' },
  { key: 'dn_attach', label: '附件', group: '单据头' },
  { key: 'dn_table', label: '商品明细表', group: '商品明细' },
  { key: 'dn_col_name', label: '商品名（逐行）', group: '商品明细' },
  { key: 'dn_col_spec', label: '规格（逐行）', group: '商品明细' },
  { key: 'dn_col_qty', label: '数量（逐行）', group: '商品明细' },
  { key: 'dn_col_price', label: '单价（逐行）', group: '商品明细' },
  { key: 'dn_col_amount', label: '金额（逐行）', group: '商品明细' },
  { key: 'dn_qty_total', label: '数量合计', group: '金额合计' },
  { key: 'dn_amount_total', label: '金额合计', group: '金额合计' },
  { key: 'dn_discount', label: '优惠折扣', group: '金额合计' },
  { key: 'dn_discount_amount', label: '优惠金额', group: '金额合计' },
  { key: 'dn_real_amount', label: '应收金额', group: '金额合计' },
  { key: 'dn_paid_total', label: '收款金额', group: '金额合计' },
  { key: 'dn_back_amount', label: '找零', group: '金额合计' },
  { key: 'dn_status', label: '单据状态', group: '其它' },
  { key: 'dn_footer', label: '落款', group: '其它' }
]

export default {
  type: 'delivery',
  title: DELIVERY_FORM_TITLE,
  buildAttrs(context) {
    var record = (context && context.record) || {}
    var list = DELIVERY_FIELDS.map(function(def) {
      var value = record[def.key]
      return {
        key: def.key,
        label: def.label,
        value: value == null ? '' : String(value),
        isCustom: false,
        group: def.group
      }
    })
    var exists = {}
    list.forEach(function(attr) { exists[attr.key] = true })
    buildProductFormAttrs(record.goodsRows).forEach(function(attr) {
      if (exists[attr.key]) return
      exists[attr.key] = true
      list.push(attr)
    })
    // 类别 / 规格：与标签、地址打印共用同一套规格映射器
    return appendCategorySpecAttrs(list)
  }
}

/**
 * 从发货单页面数据提取 dn_* 打印字段值（唯一提取入口，打印快照与预览共用）
 *
 * @param {Object} source 页面数据
 *   bill 单据（number/operTimeStr/contacts/telephone/address/depotName/payType/
 *   accountName/userName/salesManStr/remark/fileName/changeAmount/discount/
 *   discountMoney/discountLastMoney/backAmount/status），member 会员信息，
 *   items 商品明细（operNumber/allPrice/unitPrice）
 * @returns {Object} dn_* 字段值
 */
export function extractDeliveryValues(source) {
  var s = source || {}
  var bill = s.bill || {}
  var member = s.member || {}
  var items = Array.isArray(s.items) ? s.items : []
  var totalQty = items.reduce(function(sum, item) { return sum + toNum(item.operNumber) }, 0)
  var totalAmount = items.reduce(function(sum, item) {
    return sum + (toNum(item.allPrice) || toNum(item.unitPrice) * toNum(item.operNumber))
  }, 0)
  var paidAmount = bill.changeAmount == null ? totalAmount : toNum(bill.changeAmount)
  // 商品字段统一约定：有商品数据的表单必须回填商品基础属性 / SKU 属性真实值
  var values = {
    dn_title: '发货单',
    dn_no: bill.number || '--',
    dn_date: bill.operTimeStr || '--',
    dn_member: bill.organName || member.name || '散客',
    dn_receiver: bill.contacts || member.contacts || member.name || '散客',
    dn_phone: bill.telephone || member.telephone || '--',
    dn_address: bill.address || member.address || '--',
    dn_depot: bill.depotName || '--',
    dn_paytype: bill.payType || '--',
    dn_account: bill.accountName || '--',
    dn_operator: bill.userName || '--',
    dn_salesman: bill.salesManStr || '--',
    dn_remark: bill.remark || '',
    dn_attach: bill.fileName || '无',
    dn_qty_total: Number.isInteger(totalQty) ? String(totalQty) : totalQty.toFixed(2),
    dn_amount_total: '¥' + toMoney(totalAmount),
    dn_paid_total: '¥' + toMoney(paidAmount),
    dn_discount: bill.discount == null ? '--' : String(bill.discount),
    dn_discount_amount: bill.discountMoney == null ? '--' : toMoney(bill.discountMoney),
    dn_real_amount: bill.discountLastMoney == null ? toMoney(totalAmount) : toMoney(bill.discountLastMoney),
    dn_back_amount: bill.backAmount == null ? '0.00' : toMoney(bill.backAmount),
    dn_status: DELIVERY_STATUS_MAP[String(bill.status)] || (bill.status == null ? '--' : String(bill.status))
  }
  // 商品字段统一约定：有商品数据的表单必须回填商品基础属性 / SKU 属性真实值
  return mergeProductAttrValues(values, items)
}

/**
 * 从发货单明细行提取明细表行（name/spec/qty/price/amount）
 *
 * @param {Array} items 单据商品明细
 * @param {Object} options 可选：spec(item) 规格文案函数（页面列展示与明细表共用）
 * @returns {Array} 明细行
 */
export function extractDeliveryRows(items, options) {
  var spec = (options && options.spec) || function() { return '' }
  return (Array.isArray(items) ? items : []).map(function(item) {
    var amount = toNum(item.allPrice) || toNum(item.unitPrice) * toNum(item.operNumber)
    return {
      name: item.materialName || item.name || '',
      // 商品品类 id：预览端据此解析一级类目（类别取值读映射表约定）
      categoryId: item.categoryId != null ? item.categoryId : (item.materialCategoryId != null ? item.materialCategoryId : null),
      spec: spec(item),
      qty: Number.isInteger(toNum(item.operNumber)) ? String(toNum(item.operNumber)) : toNum(item.operNumber).toFixed(2),
      price: toMoney(item.unitPrice),
      amount: toMoney(amount)
    }
  })
}
