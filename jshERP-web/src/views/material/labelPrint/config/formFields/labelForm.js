/**
 * 商品标签表单字段配置
 * 字段清单来自 materialColumns（商品基础属性 / SKU 属性 / 库存属性），
 * 这里只做转接，保证编辑页、预览页与商品信息表格使用同一份字段定义。
 */
import { buildLabelAttrs } from '@/views/material/utils/materialDisplay'
import { buildCategorySpecAttrs } from '@/views/material/labelPrint/utils/categorySpec'

const LABEL_FORM_TITLE = '商品标签'

/** 合并商品字段与标准「类别 / 规格」元素，同 key 以商品字段为准 */
function withCategorySpecAttrs(list) {
  var exists = {}
  var result = []
  ;(list || []).concat(buildCategorySpecAttrs()).forEach(function(attr) {
    if (!attr || !attr.key || exists[attr.key]) return
    exists[attr.key] = true
    result.push(attr)
  })
  return result
}

export default {
  type: 'label',
  title: LABEL_FORM_TITLE,
  buildAttrs(context) {
    var ctx = context || {}
    return withCategorySpecAttrs(buildLabelAttrs(ctx.record, ctx.sku))
  }
}
