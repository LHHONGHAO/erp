/**
 * 地址打印表单字段配置（唯一字段白名单）
 * 字段定义统一维护在 addressFormFields，含商品行带原始记录时并入的商品属性。
 */
import { ADDRESS_FORM_TITLE, buildAddressAttrs } from '@/views/material/labelPrint/utils/addressFormFields'
import { appendCategorySpecAttrs } from '@/views/material/labelPrint/utils/categorySpec'

export { ADDRESS_FORM_TITLE }

export default {
  type: 'address',
  title: ADDRESS_FORM_TITLE,
  buildAttrs(context) {
    var record = (context && context.record) || {}
    return appendCategorySpecAttrs(buildAddressAttrs(record))
  }
}
