import Vue from 'vue'
import { getMaterialByBarCode } from '@/api/api'
import { getMpListShort } from '@/utils/util'

function extractRows (response) {
  if (response && response.code === 200 && Array.isArray(response.data)) {
    return response.data
  }
  return []
}

function requestByParam (params) {
  return getMaterialByBarCode(params).then(function (response) {
    return extractRows(response)
  }).catch(function () {
    return []
  })
}

/**
 * 按识别内容调用现有商品搜索接口。
 * 识别内容不截断、不转换、不删除前导零；先按条码查询，再按 SKU 码查询。
 * 不按固定长度或纯数字规则判断内容类型。
 *
 * @param {Object} payload content / format / type / organId / prefixNo / mpList
 * @returns {Promise<{success, rows, matchedBy, content, format, type}>}
 */
export async function searchContentByCode (payload) {
  const content = payload.content
  const format = payload.format || 'UNKNOWN'
  const type = payload.type || 'UNKNOWN'
  if (!content) {
    return { success: false, rows: [], matchedBy: '', content: content, format: format, type: type }
  }
  const propertyList = Vue.ls.get('materialPropertyList') || []
  const baseParams = {
    organId: payload.organId,
    prefixNo: payload.prefixNo,
    mpList: payload.mpList !== undefined ? payload.mpList : getMpListShort(propertyList)
  }
  const barRows = await requestByParam(Object.assign({}, baseParams, { barCode: content }))
  if (barRows.length > 0) {
    return {
      success: true,
      rows: barRows,
      matchedBy: 'barCode',
      content: content,
      format: format,
      type: type
    }
  }
  const skuRows = await requestByParam(Object.assign({}, baseParams, { skuCode: content }))
  return {
    success: skuRows.length > 0,
    rows: skuRows,
    matchedBy: skuRows.length > 0 ? 'skuCode' : '',
    content: content,
    format: format,
    type: type
  }
}
