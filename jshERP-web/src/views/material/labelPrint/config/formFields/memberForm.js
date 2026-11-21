/**
 * 会员标签表单字段配置（唯一字段白名单）
 * key 与会员信息表 jsh_supplier 的列名保持一致，recordKey 为业务对象上的取值字段。
 */

export const MEMBER_FORM_TITLE = '会员标签'

const MEMBER_FIELDS = [
  { key: 'contacts', label: '联系人', recordKey: 'contacts' },
  { key: 'telephone', label: '电话', recordKey: 'telephone' },
  { key: 'phone_num', label: '手机', recordKey: 'phoneNum' },
  { key: 'address', label: '联系地址', recordKey: 'address' },
  { key: 'supplier', label: '会员卡号', recordKey: 'supplier' },
  { key: 'email', label: '电子邮箱', recordKey: 'email' },
  { key: 'wechat_account', label: '微信账户', recordKey: 'wechatAccount' },
  { key: 'douyin_account', label: '抖音账户', recordKey: 'douyinAccount' },
  { key: 'alipay_account', label: '支付宝账户', recordKey: 'alipayAccount' },
  { key: 'other_account', label: '其它账户', recordKey: 'otherAccount' },
  { key: 'advance_in', label: '预付款', recordKey: 'advanceIn' },
  { key: 'description', label: '备注', recordKey: 'description' },
  { key: 'enabled', label: '状态', recordKey: 'enabled' }
]

export default {
  type: 'member',
  title: MEMBER_FORM_TITLE,
  buildAttrs(context) {
    var record = (context && context.record) || {}
    return MEMBER_FIELDS.map(function(def) {
      var value
      if (def.key === 'enabled') {
        value = record.enabled === false ? '禁用' : '启用'
      } else {
        var raw = record[def.recordKey]
        value = raw == null ? '' : String(raw)
      }
      return {
        key: def.key,
        label: def.label,
        value: value,
        isCustom: false,
        group: '会员信息'
      }
    })
  }
}

/**
 * 从会员信息页展示行提取打印字段值（唯一提取入口）
 *
 * @param {Array} displayRows 页面展示行 [{ key, value }]，'--' 视为未填写
 * @returns {Object} 会员字段值
 */
export function extractMemberValues(displayRows) {
  var values = {}
  ;(Array.isArray(displayRows) ? displayRows : []).forEach(function(row) {
    if (!row || !row.key) return
    values[row.key] = row.value === '--' ? '' : row.value
  })
  return values
}
