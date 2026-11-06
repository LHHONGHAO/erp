/**
 * 标签模板存储（统一 print_template 表）
 *
 * 一个模板一行，config 保存完整 v3 JSON；旧版单行 JSON 存储首次访问时自动迁移。
 * 对外仍返回编辑器内部结构（selectedAttrs/attrStyles/...），编辑器交互不变。
 * 本地 localStorage 仅作离线缓存兜底。
 */
import { getAction } from '@/api/manage'
import { listPrintTemplates, savePrintTemplate, deletePrintTemplate } from './labelProviderApi'
import { legacyToSchema, schemaToLegacy } from './labelSchema'

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

/** 服务端行 → 编辑器内部模板结构 */
function fromServer(row) {
  var cfg = row.config
  if (typeof cfg === 'string') {
    try {
      cfg = JSON.parse(cfg)
    } catch (e) {
      cfg = {}
    }
  }
  cfg = cfg && typeof cfg === 'object' ? cfg : {}
  var legacy = cfg.elements ? schemaToLegacy(cfg) : JSON.parse(JSON.stringify(cfg))
  legacy.id = row.id
  legacy.serverId = row.id
  legacy.name = row.name || cfg.name || '未命名标签'
  legacy.businessType = row.businessType || cfg.businessType || ''
  legacy.provider = (cfg.dataSource && cfg.dataSource.provider) || legacy.provider || ''
  legacy.version = row.version || 1
  // 保留 v3 原始结构，供预览模块按 elements 直接渲染
  legacy.schema = cfg.elements ? cfg : null
  return legacy
}

/** 旧版单行 JSON 模板一次性迁移到 print_template */
function migrateLegacyTemplates() {
  return getAction('/labelTemplate/list').then(function(res) {
    var content = res && res.code === 200 ? (res.data || '') : ''
    if (!content) return Promise.resolve()
    var list = []
    try {
      list = JSON.parse(content)
    } catch (e) {
      list = []
    }
    if (!Array.isArray(list) || !list.length) return Promise.resolve()
    var tasks = list.map(function(tpl) {
      // 旧模板没有数据源，迁移时保持为空，继续按本地商品属性渲染
      var schema = legacyToSchema(Object.assign({}, tpl, {
        businessType: tpl.businessType || 'product.label',
        provider: tpl.provider || ''
      }))
      return savePrintTemplate({
        name: tpl.name || schema.name,
        businessType: schema.businessType || 'product.label',
        width: schema.size.width,
        height: schema.size.height,
        config: schema
      })
    })
    return Promise.all(tasks).catch(function() { return [] })
  }).catch(function() {})
}

/** 发货单模板由发货单适配器管理，不进入标签模板列表 */
function isDeliveryRow(row) {
  var cfg = row && row.config
  if (typeof cfg === 'string') {
    try {
      cfg = JSON.parse(cfg)
    } catch (e) {
      cfg = null
    }
  }
  return row.businessType === 'shipping.delivery' || (cfg && cfg.meta && cfg.meta.kind === 'delivery-note')
}

function loadFromServer() {
  return listPrintTemplates().then(function(res) {
    var rows = res && res.code === 200 && Array.isArray(res.data) ? res.data : null
    if (rows === null) {
      throw new Error('模板服务不可用')
    }
    rows = rows.filter(function(row) { return !isDeliveryRow(row) })
    if (rows.length) {
      return rows.map(fromServer)
    }
    return migrateLegacyTemplates().then(function() {
      return listPrintTemplates().then(function(res2) {
        var rows2 = res2 && res2.code === 200 && Array.isArray(res2.data) ? res2.data : []
        return rows2.filter(function(row) { return !isDeliveryRow(row) }).map(fromServer)
      })
    })
  })
}

/**
 * 拉取模板列表（PC/手机共用同一份数据），服务端不可用时回退本地缓存
 */
export function fetchLabelTemplates() {
  return loadFromServer().then(function(list) {
    cacheList(list)
    return list
  }).catch(function() {
    return localTemplates()
  })
}

/** 保存单个模板：有 serverId 更新，无则新增；返回后端响应 */
export function saveLabelTemplate(tpl) {
  var schema = legacyToSchema(tpl)
  // 模板必须绑定业务数据源：businessType 与 dataSource.provider 保持一致
  var provider = tpl.provider || schema.dataSource.provider || ''
  if (!provider && schema.businessType && schema.businessType !== 'shipping.delivery') {
    provider = schema.businessType
  }
  if (!provider) provider = 'product.label'
  schema.dataSource.provider = provider
  schema.businessType = provider
  var payload = {
    id: tpl.serverId || null,
    name: tpl.name || schema.name,
    businessType: schema.businessType,
    width: schema.size.width,
    height: schema.size.height,
    config: schema
  }
  return savePrintTemplate(payload).then(function(res) {
    if (res && res.code === 200 && res.data && !tpl.serverId) {
      tpl.serverId = res.data
    }
    return res
  })
}

/** 保存整份模板列表（移动端等批量保存场景） */
export function saveLabelTemplates(list) {
  cacheList(list)
  var tasks = (list || []).map(function(tpl) {
    return saveLabelTemplate(tpl).catch(function() {
      return { code: 500 }
    })
  })
  return Promise.all(tasks).then(function(results) {
    var failed = results.filter(function(item) { return !item || item.code !== 200 })
    return failed.length ? { code: 500, data: '部分模板保存失败' } : { code: 200, data: 'ok' }
  }).catch(function() {
    return { code: 500, data: '保存失败' }
  })
}

/** 删除模板（后端置为停用） */
export function deleteLabelTemplate(tpl) {
  if (!tpl) return Promise.resolve({ code: 200 })
  var id = tpl.serverId || (typeof tpl.id === 'number' ? tpl.id : null)
  if (!id) return Promise.resolve({ code: 200 })
  return deletePrintTemplate(id)
}

/** 已拉取过的缓存，供同步读取场景使用（未拉取过返回 []） */
export function getCachedLabelTemplates() {
  return cache || []
}
