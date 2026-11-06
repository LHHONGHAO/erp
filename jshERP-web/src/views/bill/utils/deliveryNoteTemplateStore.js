/**
 * 发货单模板存储适配器
 *
 * 把发货单模板（编辑器结构）适配为统一 print_template 记录：
 *   businessType = shipping.delivery
 *   config.meta.kind = delivery-note
 * 模板只保存布局与字段绑定，不保存业务数据。
 * 旧版 platformConfig（delivery_note_layout / delivery_note_template）仅作为读取兜底。
 */
import { getAction } from '@/api/manage'
import { listPrintTemplates, savePrintTemplate } from '@/views/material/utils/labelProviderApi'
import { legacyToSchema, schemaToLegacy } from '@/views/material/utils/labelSchema'

export const DELIVERY_BUSINESS_TYPE = 'shipping.delivery'
export const DELIVERY_TEMPLATE_NAME = '发货单模板'

const META_KIND = 'delivery-note'
const LEGACY_LAYOUT_KEY = 'delivery_note_layout'
const LEGACY_FORM_KEY = 'delivery_note_template'

/** getUiConfig 返回值可能是字符串、对象或 { config } 包装，统一取出对象 */
function parseConfig(value) {
  var cfg = value
  if (typeof cfg === 'string') {
    try {
      cfg = JSON.parse(cfg)
    } catch (e) {
      return null
    }
  }
  if (cfg && typeof cfg === 'object' && cfg.config != null && !cfg.selectedAttrs && !cfg.elements) {
    cfg = cfg.config
  }
  return cfg && typeof cfg === 'object' ? cfg : null
}

function isDeliveryRow(row) {
  var cfg = parseConfig(row.config) || {}
  var meta = cfg.meta || {}
  return row.businessType === DELIVERY_BUSINESS_TYPE || meta.kind === META_KIND
}

/** 统一表记录 → 编辑器模板结构 */
function fromServer(row) {
  var cfg = parseConfig(row.config) || {}
  var legacy = cfg.elements ? schemaToLegacy(cfg) : JSON.parse(JSON.stringify(cfg))
  legacy.id = row.id
  legacy.serverId = row.id
  legacy.name = row.name || cfg.name || DELIVERY_TEMPLATE_NAME
  legacy.businessType = row.businessType || cfg.businessType || DELIVERY_BUSINESS_TYPE
  legacy.version = row.version || 1
  legacy.labelWidth = legacy.labelWidth || legacy.width || 560
  legacy.labelHeight = legacy.labelHeight || legacy.height || 800
  legacy.width = legacy.labelWidth
  legacy.height = legacy.labelHeight
  legacy.schema = cfg.elements ? cfg : null
  if (cfg.meta && cfg.meta.deliveryNote) legacy.deliveryNote = cfg.meta.deliveryNote
  return legacy
}

/** 旧版布局快照 → 编辑器模板结构 */
function fromLegacyLayout(snapshot) {
  var snap = parseConfig(snapshot)
  if (!snap || !snap.selectedAttrs || !snap.selectedAttrs.length) return null
  var tpl = {
    id: null,
    serverId: null,
    name: DELIVERY_TEMPLATE_NAME,
    businessType: DELIVERY_BUSINESS_TYPE,
    selectedAttrs: JSON.parse(JSON.stringify(snap.selectedAttrs)),
    attrStyles: JSON.parse(JSON.stringify(snap.attrStyles || {})),
    attrLabels: JSON.parse(JSON.stringify(snap.attrLabels || {})),
    labelWidth: snap.width || snap.labelWidth || 560,
    labelHeight: snap.height || snap.labelHeight || 800,
    schema: null
  }
  tpl.width = tpl.labelWidth
  tpl.height = tpl.labelHeight
  return tpl
}

function getLegacyConfig(key) {
  return getAction('/platformConfig/getUiConfig', { key: key }).then(function(res) {
    return res && res.code === 200 ? parseConfig(res.data) : null
  }).catch(function() {
    return null
  })
}

/** 旧平台配置兜底：先读布局，再读旧版简单表单 */
function loadLegacyTemplate() {
  return getLegacyConfig(LEGACY_LAYOUT_KEY).then(function(layout) {
    var tpl = fromLegacyLayout(layout)
    if (tpl) return tpl
    return getLegacyConfig(LEGACY_FORM_KEY).then(function(form) {
      return form && typeof form === 'object' ? form : null
    })
  })
}

/**
 * 读取发货单模板
 * 统一模板表优先；没有记录时回退旧 platformConfig，返回 null 表示尚未配置。
 */
export function fetchDeliveryTemplate() {
  return listPrintTemplates().then(function(res) {
    var rows = res && res.code === 200 && Array.isArray(res.data) ? res.data : []
    var row = rows.filter(isDeliveryRow)[0]
    if (row) return fromServer(row)
    return loadLegacyTemplate()
  }).catch(function() {
    return loadLegacyTemplate()
  })
}

/** 保存发货单模板：写入统一模板表，有 serverId 更新，无则新增 */
export function saveDeliveryTemplate(tpl) {
  var source = Object.assign({}, tpl, {
    templateType: 'delivery',
    businessType: DELIVERY_BUSINESS_TYPE,
    provider: DELIVERY_BUSINESS_TYPE
  })
  var schema = legacyToSchema(source)
  schema.businessType = DELIVERY_BUSINESS_TYPE
  schema.dataSource.provider = DELIVERY_BUSINESS_TYPE
  schema.meta = Object.assign({}, schema.meta, { kind: META_KIND })
  if (tpl && tpl.deliveryNote) schema.meta.deliveryNote = tpl.deliveryNote
  var payload = {
    id: (tpl && tpl.serverId) || null,
    name: (tpl && tpl.name) || DELIVERY_TEMPLATE_NAME,
    businessType: DELIVERY_BUSINESS_TYPE,
    width: schema.size.width,
    height: schema.size.height,
    config: schema
  }
  return savePrintTemplate(payload).then(function(res) {
    if (res && res.code === 200 && res.data != null && tpl && !tpl.serverId) {
      tpl.serverId = res.data
    }
    return res
  })
}
