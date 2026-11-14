/**
 * 发货单模板存储适配器
 *
 * 把发货单模板（编辑器结构）适配为统一 print_template 记录：
 *   businessType = shipping.delivery
 *   config.meta.kind = delivery-note
 *
 * 模板只保存布局与字段绑定，不保存业务数据。
 * 只支持 v2：加载到旧结构时返回 outdated=true，由调用方提示删除后重建。
 */
import { listPrintTemplates, savePrintTemplate } from '@/views/material/utils/labelProviderApi'
import {
  editorStateToV2,
  v2ToEditorState,
  v2ToSchemaView,
  isLabelTemplateV2,
  parseTemplateRaw
} from '@/views/material/utils/labelTemplateV2'

export const DELIVERY_BUSINESS_TYPE = 'shipping.delivery'
export const DELIVERY_TEMPLATE_NAME = '发货单模板'

const META_KIND = 'delivery-note'

function isDeliveryRow(row) {
  var cfg = parseTemplateRaw(row.config) || {}
  var meta = cfg.meta || {}
  return row.businessType === DELIVERY_BUSINESS_TYPE || meta.kind === META_KIND
}

/** 统一表记录 → 编辑器模板结构 */
function fromServer(row) {
  var cfg = parseTemplateRaw(row.config)
  var base = {
    id: row.id,
    serverId: row.id,
    name: row.name || (cfg && cfg.name) || DELIVERY_TEMPLATE_NAME,
    businessType: DELIVERY_BUSINESS_TYPE,
    version: row.version || 1,
    outdated: !isLabelTemplateV2(cfg),
    selectedAttrs: []
  }
  if (base.outdated) return base
  var state = v2ToEditorState(cfg)
  Object.assign(base, state)
  base.name = row.name || state.name || DELIVERY_TEMPLATE_NAME
  base.businessType = DELIVERY_BUSINESS_TYPE
  base.labelWidth = state.labelWidth || 560
  base.labelHeight = state.labelHeight || 800
  base.width = base.labelWidth
  base.height = base.labelHeight
  base.v2 = cfg
  base.schema = v2ToSchemaView(cfg)
  if (cfg.meta && cfg.meta.deliveryNote) base.deliveryNote = cfg.meta.deliveryNote
  return base
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

/** 保存发货单模板：写入统一模板表，有 serverId 更新，无则新增 */
export function saveDeliveryTemplate(tpl) {
  var source = tpl || {}
  var schema = isLabelTemplateV2(source)
    ? source
    : editorStateToV2({
      name: source.name,
      provider: DELIVERY_BUSINESS_TYPE,
      businessType: DELIVERY_BUSINESS_TYPE,
      selectedAttrs: source.selectedAttrs,
      attrStyles: source.attrStyles,
      attrLabels: source.attrLabels,
      allAttrs: source.allAttrs || source.customLabels || [],
      goodsFields: source.goodsFields,
      labelWidth: source.labelWidth || source.width || 560,
      labelHeight: source.labelHeight || source.height || 800,
      overflowMode: source.overflowMode,
      pageMode: source.pageMode,
      maxRowsPerPage: source.maxRowsPerPage,
      repeatHeader: source.repeatHeader
    }, { kind: META_KIND, type: 'delivery' })
  schema.businessType = DELIVERY_BUSINESS_TYPE
  schema.dataSource.provider = DELIVERY_BUSINESS_TYPE
  schema.meta = Object.assign({}, schema.meta, { kind: META_KIND })
  if (source.deliveryNote) schema.meta.deliveryNote = source.deliveryNote
  var payload = {
    id: source.serverId || null,
    name: source.name || DELIVERY_TEMPLATE_NAME,
    businessType: DELIVERY_BUSINESS_TYPE,
    width: schema.paper.width,
    height: schema.paper.height,
    config: schema
  }
  return savePrintTemplate(payload).then(function(res) {
    if (res && res.code === 200 && res.data != null && tpl && !tpl.serverId) {
      tpl.serverId = res.data
    }
    return res
  })
}
