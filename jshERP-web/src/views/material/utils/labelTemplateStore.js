/**
 * 标签模板存储（唯一数据源：print_template 表）
 *
 * 一个模板一行，config 保存完整 v2 JSON；对外返回编辑器内部结构
 * （selectedAttrs/attrStyles/...），编辑器交互不变。
 *
 * 只支持 v2：加载到旧结构时返回 outdated=true，由调用方提示删除后重建。
 */
import { listPrintTemplates, savePrintTemplate, deletePrintTemplate } from './labelProviderApi'
import {
  editorStateToV2,
  v2ToEditorState,
  v2ToSchemaView,
  isLabelTemplateV2,
  parseTemplateRaw
} from './labelTemplateV2'

/** 服务端行 → 编辑器内部模板结构 */
function fromServer(row) {
  var cfg = parseTemplateRaw(row.config)
  var base = {
    id: row.id,
    serverId: row.id,
    name: row.name || (cfg && cfg.name) || '未命名标签',
    businessType: row.businessType || (cfg && cfg.businessType) || '',
    version: row.version || 1,
    outdated: !isLabelTemplateV2(cfg),
    selectedAttrs: []
  }
  if (base.outdated) return base
  var state = v2ToEditorState(cfg)
  Object.assign(base, state)
  base.name = row.name || state.name || '未命名标签'
  base.businessType = row.businessType || state.businessType || ''
  // 模板分类信息（商品类型通用模板）
  var meta = state.meta || {}
  base.type = meta.type || 'normal'
  base.categoryId = meta.categoryId || null
  base.categoryName = meta.categoryName || null
  base.materialId = meta.materialId || null
  // v2 原始结构 + 渲染视图，供预览模块按 elements 直接渲染
  base.v2 = cfg
  base.schema = v2ToSchemaView(cfg)
  return base
}

/** 发货单模板由发货单适配器管理，不进入标签模板列表 */
function isDeliveryRow(row) {
  var cfg = parseTemplateRaw(row.config) || {}
  var meta = cfg.meta || {}
  return row.businessType === 'shipping.delivery' || meta.kind === 'delivery-note'
}

/** 拉取模板列表（PC/手机共用同一份数据） */
export function fetchLabelTemplates() {
  return listPrintTemplates().then(function(res) {
    var rows = res && res.code === 200 && Array.isArray(res.data) ? res.data : []
    return rows.filter(function(row) { return !isDeliveryRow(row) }).map(fromServer)
  })
}

/** 编辑器结构（或已是 v2）→ v2 */
export function toTemplateV2(tpl) {
  if (isLabelTemplateV2(tpl)) return tpl
  var provider = tpl.provider || tpl.businessType || 'product.label'
  return editorStateToV2({
    name: tpl.name,
    provider: provider,
    businessType: provider,
    selectedAttrs: tpl.selectedAttrs,
    attrStyles: tpl.attrStyles,
    attrLabels: tpl.attrLabels,
    allAttrs: tpl.allAttrs || tpl.customLabels || [],
    goodsFields: tpl.goodsFields,
    labelWidth: tpl.labelWidth || tpl.width || 320,
    labelHeight: tpl.labelHeight || tpl.height || 240,
    overflowMode: tpl.overflowMode,
    pageMode: tpl.pageMode,
    maxRowsPerPage: tpl.maxRowsPerPage,
    repeatHeader: tpl.repeatHeader
  }, {
    type: tpl.type,
    materialId: tpl.materialId,
    categoryId: tpl.categoryId,
    categoryName: tpl.categoryName,
    kind: tpl.meta && tpl.meta.kind
  })
}

/** 保存单个模板：有 serverId 更新，无则新增；返回后端响应 */
export function saveLabelTemplate(tpl) {
  var schema = toTemplateV2(tpl)
  // 模板必须绑定业务数据源：businessType 与 dataSource.provider 保持一致
  var provider = schema.dataSource.provider || tpl.provider || schema.businessType
  if (!provider) provider = 'product.label'
  schema.dataSource.provider = provider
  schema.businessType = provider
  var payload = {
    id: tpl.serverId || null,
    name: tpl.name || schema.name,
    businessType: provider,
    width: schema.paper.width,
    height: schema.paper.height,
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
