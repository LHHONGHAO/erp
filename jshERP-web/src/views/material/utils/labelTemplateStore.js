import { getAction, postAction } from '@/api/manage'

const CACHE_KEY = 'labelTemplates'
let cache = null

function localTemplates() {
  try {
    return JSON.parse(localStorage.getItem(CACHE_KEY) || '[]')
  } catch (e) {
    return []
  }
}

function cacheList(list) {
  cache = list
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(list))
  } catch (e) {
    // 存储失败不影响主流程
  }
}

/**
 * 从后端拉取标签模板列表（PC/手机共用同一份数据）。
 * 后端为空时把本机 localStorage 里已有的模板迁移上去；后端不可用时回退本地缓存。
 */
export function fetchLabelTemplates() {
  return getAction('/labelTemplate/list').then(function(res) {
    if (!res || res.code !== 200) {
      return localTemplates()
    }
    var content = res.data || ''
    var list = []
    if (content) {
      try { list = JSON.parse(content) } catch (e) { list = [] }
    }
    if (!list.length) {
      var local = localTemplates()
      if (local.length) {
        list = local
        saveLabelTemplates(list)
      }
    }
    cacheList(list)
    return list
  }).catch(function() {
    return localTemplates()
  })
}

/**
 * 保存模板列表：先更新本地缓存，再提交后端（返回 Promise 便于失败提示）
 */
export function saveLabelTemplates(list) {
  cacheList(list)
  return postAction('/labelTemplate/save', { templates: list })
}

/**
 * 已拉取过的缓存，供同步读取场景使用（未拉取过返回 []）
 */
export function getCachedLabelTemplates() {
  return cache || []
}
