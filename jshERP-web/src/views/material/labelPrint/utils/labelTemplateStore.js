/**
 * 标签模板存储（唯一数据源：print_template 表）
 *
 * 对外统一返回 v2 模板（config 原文 + 记录元信息），
 * 编辑器 / 打印预览各自用 v2ToEditorState、v2ToSchemaView 还原视图。
 *
 * 只支持 v2：读取到旧结构时返回 outdated=true，不转换、不替换，
 * 由调用方提示「有旧模板，是否删除」。
 */
import { listPrintTemplates, savePrintTemplate, deletePrintTemplate } from './labelProviderApi'
import { isLabelTemplateV2, parseTemplateRaw } from './labelTemplateV2'

/** 服务端行 → v2 模板（追加列表展示与模板匹配所需元信息） */
function fromServer(row) {
  var cfg = parseTemplateRaw(row.config)
  if (!isLabelTemplateV2(cfg)) {
    return {
      id: row.id,
      serverId: row.id,
      name: row.name || '未命名标签',
      businessType: row.businessType || '',
      version: row.version || 1,
      outdated: true
    }
  }
  var meta = cfg.meta || {}
  return Object.assign({}, cfg, {
    id: row.id,
    serverId: row.id,
    name: row.name || cfg.name || '未命名标签',
    businessType: row.businessType || cfg.businessType || '',
    version: row.version || 1,
    // 模板匹配用的记录元信息（只在记录外层，不写回 config）
    type: meta.type || 'normal',
    objectType: meta.objectType || (String(row.businessType || '').indexOf('address') === 0 ? 'address' : 'product'),
    categoryId: meta.categoryId || null,
    categoryName: meta.categoryName || null,
    materialId: meta.materialId || null,
    outdated: false
  })
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

/** 保存单个模板：入参必须是 v2 模板；有 serverId 更新，无则新增 */
export function saveLabelTemplate(schema) {
  // 模板必须绑定业务数据源：businessType 与 dataSource.provider 保持一致
  var provider = (schema.dataSource && schema.dataSource.provider) || schema.businessType || 'product.label'
  schema.dataSource = schema.dataSource || {}
  schema.dataSource.provider = provider
  schema.businessType = provider
  var payload = {
    id: schema.serverId || null,
    name: schema.name || '未命名标签',
    businessType: provider,
    width: schema.paper.width,
    height: schema.paper.height,
    config: schema
  }
  return savePrintTemplate(payload).then(function(res) {
    if (res && res.code === 200 && res.data && !schema.serverId) {
      schema.serverId = res.data
    }
    return res
  })
}

/** 删除模板（后端置为停用） */
export function deleteLabelTemplate(tpl) {
  if (!tpl) return Promise.resolve({ code: 200 })
  var id = tpl.serverId || (typeof tpl.id === 'number' ? tpl.id : null)
  if (!id) return Promise.resolve({ code: 200 })
  return deletePrintTemplate(id)
}
