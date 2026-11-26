/**
 * 小票表单字段配置（唯一字段白名单）
 * 字段定义与取值统一由「销售开单表单数据提取模块」提供，编辑页只显示属性名，
 * 业务值由预览/打印阶段注入。
 */
import { buildRetailSaleAttrs } from '@/views/material/labelPrint/utils/retailSaleFormFields'

const RECEIPT_FORM_TITLE = '小票'

export default {
  type: 'receipt',
  title: RECEIPT_FORM_TITLE,
  buildAttrs(context) {
    var record = (context && context.record) || {}
    return buildRetailSaleAttrs(record)
  }
}
