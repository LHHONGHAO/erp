import { getAction, postAction } from '@/api/manage'

/** 数据源列表（含字段元数据） */
export function fetchProviders(businessType) {
  var params = {}
  if (businessType) params.businessType = businessType
  return getAction('/labelProvider/list', params).then(function(res) {
    return res && res.code === 200 ? (res.data || []) : []
  }).catch(function() {
    return []
  })
}

/** 数据源字段元数据 */
export function fetchProviderFields(provider) {
  if (!provider) return Promise.resolve([])
  return getAction('/labelProvider/fields', { provider: provider }).then(function(res) {
    return res && res.code === 200 ? (res.data || []) : []
  }).catch(function() {
    return []
  })
}

/** 按业务单据 ID 取统一数据（预览与打印共用） */
export function fetchLabelData(provider, documentId, subId) {
  if (!provider || documentId == null || documentId === '') return Promise.resolve({})
  var params = { provider: provider, documentId: documentId }
  if (subId != null && subId !== '') params.subId = subId
  return getAction('/labelProvider/data', params).then(function(res) {
    return res && res.code === 200 ? (res.data || {}) : {}
  }).catch(function() {
    return {}
  })
}

/** 统一模板列表 */
export function listPrintTemplates() {
  return getAction('/printTemplate/list')
}

/** 保存统一模板：有 id 更新，无 id 新增 */
export function savePrintTemplate(payload) {
  return postAction('/printTemplate/save', payload)
}

/** 删除统一模板 */
export function deletePrintTemplate(id) {
  return postAction('/printTemplate/delete', { id: id })
}
