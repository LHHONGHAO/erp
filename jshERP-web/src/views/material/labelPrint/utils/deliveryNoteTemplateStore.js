/**
 * 发货单模板存储适配器
 *
 * 把发货单模板适配为统一 print_template 记录：
 *   businessType = shipping.delivery
 *   config.meta.kind = delivery-note
 *
 * 对外统一返回 v2 模板，只保存布局与字段绑定，不保存业务数据。
 * 只支持 v2：加载到旧结构时返回 outdated=true，由调用方提示删除后重建。
 */
import { listPrintTemplates, savePrintTemplate } from './labelProviderApi'
import { isLabelTemplateV2, parseTemplateRaw } from './labelTemplateV2'

export const DELIVERY_BUSINESS_TYPE = 'shipping.delivery'
export const DELIVERY_TEMPLATE_NAME = '发货单模板'

const META_KIND = 'delivery-note'

function isDeliveryRow(row) {
  var cfg = parseTemplateRaw(row.config) || {}
  var meta = cfg.meta || {}
  return row.businessType === DELIVERY_BUSINESS_TYPE || meta.kind === META_KIND
}

/** 统一表记录 → v2 模板（追加记录元信息） */
function fromServer(row) {
  var cfg = parseTemplateRaw(row.config)
  if (!isLabelTemplateV2(cfg)) {
    return {
      id: row.id,
      serverId: row.id,
      name: row.name || DELIVERY_TEMPLATE_NAME,
      businessType: DELIVERY_BUSINESS_TYPE,
      version: row.version || 1,
      outdated: true
    }
  }
  var meta = cfg.meta || {}
  var paper = cfg.paper || {}
  return Object.assign({}, cfg, {
    id: row.id,
    serverId: row.id,
    name: row.name || cfg.name || DELIVERY_TEMPLATE_NAME,
    businessType: DELIVERY_BUSINESS_TYPE,
    version: row.version || 1,
    width: paper.width,
    height: paper.height,
    // 发货单额外配置，保存在 meta 里，读出时提到外层供调用方使用
    deliveryNote: meta.deliveryNote,
    outdated: false
  })
}

/**
 * 读取发货单模板
 * 没有记录时返回 null，表示尚未配置。
 */
export function fetchDeliveryTemplate() {
  return listPrintTemplates().then(function(res) {
    var rows = res && res.code === 200 && Array.isArray(res.data) ? res.data : []
    var row = rows.filter(isDeliveryRow)[0]
    return row ? fromServer(row) : null
  }).catch(function() {
    return null
  })
}

/** 保存发货单模板：入参必须是 v2 模板；有 serverId 更新，无则新增 */
export function saveDeliveryTemplate(schema) {
  schema.businessType = DELIVERY_BUSINESS_TYPE
  schema.dataSource = schema.dataSource || {}
  schema.dataSource.provider = DELIVERY_BUSINESS_TYPE
  schema.meta = Object.assign({}, schema.meta, { kind: META_KIND })
  if (schema.deliveryNote) schema.meta.deliveryNote = schema.deliveryNote
  var payload = {
    id: schema.serverId || null,
    name: schema.name || DELIVERY_TEMPLATE_NAME,
    businessType: DELIVERY_BUSINESS_TYPE,
    width: schema.paper.width,
    height: schema.paper.height,
    config: schema
  }
  return savePrintTemplate(payload).then(function(res) {
    if (res && res.code === 200 && res.data != null && !schema.serverId) {
      schema.serverId = res.data
    }
    return res
  })
}
