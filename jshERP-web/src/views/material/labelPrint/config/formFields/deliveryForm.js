/**
 * 发货单表单字段配置（唯一字段白名单）
 * key 与发货单业务值 / 模板中保存的字段完全一致，顺序与分组决定元素面板展示顺序。
 * 含商品信息：末尾并入商品基础属性 + SKU 属性字段。
 */
import { buildProductFormAttrs } from '@/views/material/labelPrint/utils/productFormFields'
import { appendCategorySpecAttrs } from '@/views/material/labelPrint/utils/categorySpec'

export const DELIVERY_FORM_TITLE = '发货单'

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
