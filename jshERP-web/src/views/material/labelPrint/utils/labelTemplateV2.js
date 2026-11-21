/**
 * 标签模板 v2 结构（唯一持久化格式）
 *
 * 一致性约定：
 *   - 编辑器保存、打印预览、打印数据都只认 v2；三者都通过 v2ToEditorState 还原成同一份
 *     编辑器内部结构（px），再交给 resolveLabelLayout 计算，保证显示样式一致。
 *   - 打印方向、打印份数、打印设备属于打印运行时参数，不写入模板。
 *   - 不兼容 v1/v3 旧模板：加载到旧结构时由调用方提示「旧模板已不支持，请删除后重新创建」。
 *
 * v2 结构（坐标/尺寸为毫米）：
 * {
 *   schemaVersion: 2,
 *   name, businessType,
 *   paper: { width, height, unit: 'mm' },
 *   dataSource: {
 *     provider, providerVersion,
 *     // 记录集：本次要打印多少条数据由运行时 records.length 决定，模板不写死条数
 *     records: { source, identity, labelPerRecord },
 *     // scope: record=每条记录画一次，items=记录内明细逐行，once=整份只画一次
 *     // path: 取值路径；明细集合用 path 指向记录里的数组（如 goodsRows）
 *     fields: [{ key, label, type, scope, path, items: [{ key, path }] }]
 *   },
 *   layout: { overflowMode: 'wrap'|'ellipsis', elements: [{
 *     id, key, type: 'text'|'barcode'|'qrcode', label, text,
 *     x, y, width, height, fontSize, fontWeight, letterSpacing, lineHeight,
 *     overflow, printMode, prefixLabel, itemFields
 *   }] },
 *   print: { pageMode: 'single'|'fit'|'each', recordMode: 'oneLabelPerRecord'|'continuous',
 *            recordsPerPage,
 *            list: { source, path, mode: 'multiline'|'paginate'|'perItem', maxRowsPerPage, repeatHeader } },
 *   meta: { type, materialId, categoryId, categoryName, kind }
 * }
 */
import { pxToMm, mmToPx, CODE_KEY_PREFIX, fillSeparatorText } from './labelSchema'
import { GOODS_KEY, GOODS_TABLE_KEY } from './goodsLabelLayout'
import { normalizeSpecBlocks, isSpecNameKey } from './goodsSpecBlocks'
import { normalizeSpecMapping, flattenSpecMapping } from './categorySpec'
import { normalizeEditorData } from './templateEditDataModel'

export var TEMPLATE_SCHEMA_VERSION = 2
// 渲染代次：规格块「名称锚点 + 占位」改版后升到 3；低于该值的模板一律按旧模板处理（提示删除，不加载）
export var TEMPLATE_RENDER_VERSION = 3
export var TEMPLATE_VERSION_FIELD = 'schemaVersion'
// 明细集合在业务记录对象里的默认路径（地址/发货单均把商品行放在 goodsRows）
export var DEFAULT_ITEM_PATH = 'goodsRows'

var PAGE_MODE_TO_PRINT = { none: 'single', fit: 'fit', each: 'each' }
var PRINT_TO_PAGE_MODE = { single: 'none', fit: 'fit', each: 'each' }
var PAGE_MODE_TO_LIST = { none: 'multiline', fit: 'paginate', each: 'perItem' }
var LIST_TO_PAGE_MODE = { multiline: 'none', paginate: 'fit', perItem: 'each' }

/** 解析存储值：字符串 / { config } 包装统一取出对象 */
export function parseTemplateRaw(raw) {
  var cfg = raw
  if (typeof cfg === 'string') {
    try { cfg = JSON.parse(cfg) } catch (e) { return null }
  }
  if (cfg && typeof cfg === 'object' && cfg.config != null && !cfg.schemaVersion && !cfg.layout && !cfg.elements && !cfg.selectedAttrs) {
    cfg = cfg.config
    if (typeof cfg === 'string') {
      try { cfg = JSON.parse(cfg) } catch (e) { return null }
    }
  }
  return cfg && typeof cfg === 'object' ? cfg : null
}

/** 是否可加载的 v2 模板：结构为 v2 且渲染代次匹配 */
export function isLabelTemplateV2(raw) {
  var cfg = parseTemplateRaw(raw)
  if (!cfg) return false
  return Number(cfg[TEMPLATE_VERSION_FIELD]) === TEMPLATE_SCHEMA_VERSION &&
    Number(cfg.renderVersion) === TEMPLATE_RENDER_VERSION &&
    !!cfg.layout && Array.isArray(cfg.layout.elements)
}

function isListKey(key) {
  return key === GOODS_KEY || key === GOODS_TABLE_KEY || key === 'dn_table'
}

function findAttr(attrs, key) {
  var list = attrs || []
  for (var i = 0; i < list.length; i++) {
    if (list[i] && list[i].key === key) return list[i]
  }
  return null
}

/**
 * 编辑器内部结构 → v2 模板
 *
 * @param {Object} state { name, provider, businessType, selectedAttrs, attrStyles, attrLabels,
 *                         allAttrs, goodsFields, labelWidth, labelHeight, overflowMode,
 *                         pageMode(none/fit/each), maxRowsPerPage, repeatHeader,
 *                         specBlocks, hiddenItemKeys }
 * @param {Object} meta  { id, type, materialId, categoryId, categoryName, kind }
 */
export function editorStateToV2(state, meta) {
  var src = state || {}
  var info = meta || {}
  var styles = src.attrStyles || {}
  var labels = src.attrLabels || {}
  var overflow = src.overflowMode === 'ellipsis' ? 'ellipsis' : 'wrap'
  var elements = []
  var fields = []
  var seenField = {}
  // 参与多行/分页打印的元素：显式传入时以传入为准（允许空数组=都不参与）
  var hasFlowConfig = Array.isArray(src.flowElementKeys)
  var flowKeys = hasFlowConfig ? src.flowElementKeys.filter(function(key) { return !!key }) : []

  ;(src.selectedAttrs || []).forEach(function(key, index) {
    if (!key) return
    var style = styles[key] || {}
    var attr = findAttr(src.allAttrs, key)
    var custom = !!(attr && attr.isCustom) || String(key).indexOf('custom_') === 0
    var label = Object.prototype.hasOwnProperty.call(labels, key)
      ? labels[key]
      : (attr ? attr.label : '')
    var element = {
      id: 'e' + (index + 1),
      key: key,
      type: 'text',
      label: label == null ? '' : label,
      x: pxToMm(style.x || 0),
      y: pxToMm(style.y || 0),
      fontSize: style.fontSize || 12,
      fontWeight: style.fontWeight || 'normal',
      letterSpacing: style.letterSpacing || 0,
      lineHeight: style.lineHeight || 1.3,
      overflow: overflow,
      printMode: isListKey(key) ? 'auto' : 'inline',
      prefixLabel: !!(label == null ? '' : label)
    }
    if (style.width) element.width = pxToMm(style.width)
    if (style.height) element.height = pxToMm(style.height)
    // 文字对齐随模板保存，编辑器/打印共用
    element.textAlign = style.textAlign === 'center' || style.textAlign === 'right' ? style.textAlign : 'left'
    // 规格块几何与排版只挂在属性名（锚点）上：折行随模板保存，编辑器/打印共用
    if (isSpecNameKey(key)) {
      element.specWrap = !!style.specWrap
      // 仅用户手动拖宽过的规格块才保存固定块宽，其余按内容自然并列
      if (style.specWidthLocked && style.width > 0) element.specWidth = pxToMm(style.width)
    }
    if (custom) {
      element.custom = true
      // 分页标签：文案由运行时页码决定，绑定字段而不是固定文本
      if (key === 'page_no') {
        element.field = 'page_no'
        element.prefixLabel = false
      } else if (attr && attr.isSeparator) {
        // 间隔符：保存原始符号，导出文本按元素宽度与字号自动铺满
        element.separator = true
        element.symbol = attr.value == null ? '' : String(attr.value)
        var sepStyle = styles[key] || {}
        element.text = fillSeparatorText(element.symbol, Number(sepStyle.width) || 40, sepStyle.fontSize || 12)
      } else if (attr && attr.isCodeElem) {
        // 自定义码元素：类型（barcode/qrcode）与内容都挂在元素自身，不绑定业务字段
        element.type = attr.codeType === 'barcode' ? 'barcode' : 'qrcode'
        element.customCode = true
        element.text = attr.value == null ? '' : String(attr.value)
        element.prefixLabel = false
      } else {
        element.text = attr && attr.value != null ? String(attr.value) : ''
      }
    }
    if (key === GOODS_KEY && Array.isArray(src.goodsFields)) {
      element.itemFields = src.goodsFields.slice()
    }
    // 文本元素附加码类型：属性面板选中「二维码/条码」后，打印/预览按元素文本或字段值自动生成码。
    // 导出即转为标准码元素（type=barcode/qrcode），打印链路对 textCodeFrom 元素完全复用既有码渲染；
    // textCodeFrom=text 标记导入时还原为原文本元素（而不是独立码元素）
    if (attr && (attr.textCodeType === 'qrcode' || attr.textCodeType === 'barcode') && key !== 'page_no') {
      element.type = attr.textCodeType
      element.textCodeFrom = 'text'
      if (custom) {
        element.customCode = true
        element.text = attr.value == null ? '' : String(attr.value)
        element.prefixLabel = false
      }
    }
    if (key === GOODS_TABLE_KEY) element.table = true
    // 元素声明自己属于哪一层数据：明细元素逐条循环，其余每条记录画一次
    element.repeat = isListKey(key) ? 'perItem' : 'record'
    if (isListKey(key)) element.collection = key
    // 参与多行/分页打印的标记（打印设置里勾选）
    element.flow = hasFlowConfig ? flowKeys.indexOf(key) >= 0 : isListKey(key)
    elements.push(element)

    if (!custom && !seenField[key]) {
      seenField[key] = true
      var isList = isListKey(key)
      var field = {
        key: key,
        label: label == null ? '' : label,
        type: isList ? 'list' : 'text',
        scope: isList ? 'items' : 'record',
        // 明细集合指向业务记录里的数组；普通字段直接用 key 取值
        path: isList ? DEFAULT_ITEM_PATH : key
      }
      if (isList) {
        field.items = (Array.isArray(src.goodsFields) && src.goodsFields.length
          ? src.goodsFields
          : ['name', 'spec1']).map(function(itemKey) {
          var itemAttr = findAttr(src.allAttrs, itemKey)
          return { key: itemKey, path: itemKey, label: itemAttr ? itemAttr.label : itemKey }
        })
      }
      fields.push(field)
    }

    // 独立条码 / 二维码元素
    var codeStyle = styles[CODE_KEY_PREFIX + key]
    if (codeStyle && !custom) {
      elements.push({
        id: 'e' + (index + 1) + 'c',
        key: CODE_KEY_PREFIX + key,
        sourceKey: key,
        type: codeStyle.codeType === 'qrcode' ? 'qrcode' : 'barcode',
        x: pxToMm(codeStyle.x || 0),
        y: pxToMm(codeStyle.y || 0),
        width: pxToMm(codeStyle.width || 0),
        height: pxToMm(codeStyle.height || 0)
      })
    }
  })

  var pageMode = PAGE_MODE_TO_PRINT[src.pageMode] || 'single'
  var v2 = {
    schemaVersion: TEMPLATE_SCHEMA_VERSION,
    renderVersion: TEMPLATE_RENDER_VERSION,
    name: src.name || '未命名标签',
    businessType: src.businessType || src.provider || '',
    paper: {
      width: pxToMm(src.labelWidth || 320),
      height: pxToMm(src.labelHeight || 240),
      unit: 'mm'
    },
    dataSource: {
      provider: src.provider || '',
      providerVersion: 1,
      // 记录集：条数由运行时选中数据决定，模板只约定来源与出标签方式
      records: {
        source: (src.provider || 'record') + '.selected',
        identity: (src.provider || 'record') + '.id',
        labelPerRecord: true
      },
      fields: fields
    },
    layout: {
      overflowMode: overflow,
      elements: elements
    },
    print: {
      pageMode: pageMode,
      // 多条记录默认一条记录一张标签；记录内明细按 list.mode 分页
      recordMode: 'oneLabelPerRecord',
      recordsPerPage: 1,
      // 参与多行/分页打印的元素（打印设置里勾选）
      flowKeys: hasFlowConfig
        ? flowKeys
        : elements.filter(function(el) { return !!el.flow }).map(function(el) { return el.key }),
      list: {
        source: GOODS_KEY,
        path: DEFAULT_ITEM_PATH,
        mode: PAGE_MODE_TO_LIST[src.pageMode] || 'multiline',
        maxRowsPerPage: Math.max(0, parseInt(src.maxRowsPerPage, 10) || 0),
        repeatHeader: src.repeatHeader !== false
      }
    },
    meta: {
      type: info.type || null,
      objectType: info.objectType || null,
      materialId: info.materialId || null,
      categoryId: info.categoryId || null,
      categoryName: info.categoryName || null,
      kind: info.kind || null
    }
  }
  // 规格数据映射表（按类别分组）：模板唯一权威结构，记录读取哪个类别的哪个规格值
  var specMapping = normalizeSpecMapping(src.specMapping)
  v2.specMapping = specMapping
  // 只有建立了映射表才走「仅加载已选规格」；否则沿用编辑器当前规格块（旧路径）
  v2.specMappingReady = specMapping.length > 0
  // 渲染内部结构：有映射表时由映射表派生，逐类别保留 blockNo / memberIds
  var specBlocks = specMapping.length
    ? normalizeSpecBlocks(flattenSpecMapping(specMapping))
    : normalizeSpecBlocks(src.specBlocks)
  if (specBlocks.length) v2.specBlocks = specBlocks
  // 仅供编辑属性面板恢复的结构快照；严格排除 value 等业务数据，不参与预览/打印。
  v2.editorData = normalizeEditorData(src.editorData || {
    fields: src.allAttrs,
    title: src.formTitle,
    specBlocks: specBlocks
  })
  // 编辑页关闭的规格名/规格值随模板保存，跨端打开仍保持隐藏
  var hiddenItemKeys = (Array.isArray(src.hiddenItemKeys) ? src.hiddenItemKeys : [])
    .filter(function(key) { return !!key })
  if (hiddenItemKeys.length) v2.hiddenItemKeys = hiddenItemKeys
  // 「表单数据」里勾选的项：元素面板始终列出这些项，与画布上是否已删除无关
  var formDataKeys = (Array.isArray(src.formDataCheckedKeys) ? src.formDataCheckedKeys : [])
    .filter(function(key) { return !!key })
  if (formDataKeys.length) v2.formDataKeys = formDataKeys
  return v2
}

/**
 * v2 模板 → 编辑器内部结构（px），编辑器与打印预览共用
 */
export function v2ToEditorState(raw) {
  var v2 = parseTemplateRaw(raw) || {}
  var paper = v2.paper || {}
  var layout = v2.layout || {}
  var print = v2.print || {}
  var list = print.list || {}
  var elements = Array.isArray(layout.elements) ? layout.elements : []
  var selectedAttrs = []
  var attrStyles = {}
  var attrLabels = {}
  var customLabels = []
  var goodsFields = null
  // 文本转码元素（textCodeFrom=text）：key → 码类型，导入时还原为文本元素上的码类型标记
  var textCodeTypes = {}

  elements.forEach(function(element) {
    if (!element) return
    if ((element.type === 'barcode' || element.type === 'qrcode') && element.textCodeFrom === 'text' && element.key) {
      textCodeTypes[element.key] = element.type
    }
    // 文本转码元素（textCodeFrom=text）不走独立码还原，落到下方文本还原路径，
    // 由 textCodeTypes 把码类型标记还原回文本元素
    if ((element.type === 'barcode' || element.type === 'qrcode') && element.textCodeFrom !== 'text') {
      // 自定义码元素：类型与内容挂在元素自身，还原为自定义属性
      if (element.customCode) {
        var codeKey = element.key || ('code_' + customLabels.length + '_' + Date.now())
        selectedAttrs.push(codeKey)
        attrStyles[codeKey] = {
          x: mmToPx(element.x),
          y: mmToPx(element.y),
          width: mmToPx(element.width || 0),
          height: mmToPx(element.height || 0)
        }
        attrLabels[codeKey] = element.label == null ? '' : element.label
        customLabels.push({
          key: codeKey,
          label: element.label || (element.type === 'qrcode' ? '二维码' : '条码'),
          value: element.text == null ? '' : String(element.text),
          isCustom: true,
          isCodeElem: true,
          codeType: element.type,
          group: '自定义'
        })
        return
      }
      var source = element.sourceKey || String(element.key || '').replace(CODE_KEY_PREFIX, '')
      if (!source) return
      attrStyles[CODE_KEY_PREFIX + source] = {
        x: mmToPx(element.x),
        y: mmToPx(element.y),
        width: mmToPx(element.width || 0),
        height: mmToPx(element.height || 0),
        codeType: element.type
      }
      return
    }
    var key = element.key || element.field
    if (!key) return
    selectedAttrs.push(key)
    var style = {
      x: mmToPx(element.x),
      y: mmToPx(element.y),
      fontSize: element.fontSize || 12,
      fontWeight: element.fontWeight || 'normal',
      letterSpacing: element.letterSpacing || 0,
      lineHeight: element.lineHeight || 1.3
    }
    if (element.width) style.width = mmToPx(element.width)
    if (element.height) style.height = mmToPx(element.height)
    // 文字对齐所有文本元素通用还原
    style.textAlign = element.textAlign === 'center' || element.textAlign === 'right' ? element.textAlign : 'left'
    if (isSpecNameKey(key)) {
      style.specWrap = !!element.specWrap
      if (element.specWidth > 0) {
        style.width = mmToPx(element.specWidth)
        style.specWidthLocked = true
      }
    }
    attrStyles[key] = style
    attrLabels[key] = element.label == null ? '' : element.label
    if (key === GOODS_KEY && Array.isArray(element.itemFields)) {
      goodsFields = element.itemFields.slice()
    }
    if (element.custom) {
      var isSeparator = !!element.separator
      customLabels.push({
        key: key,
        label: isSeparator ? (element.label || '间隔符') : (element.label || ''),
        // 间隔符还原为原始符号（填充串在导出/打印时按宽度重新生成）
        value: isSeparator && element.symbol != null ? String(element.symbol) : (element.text == null ? '' : element.text),
        isCustom: true,
        isSeparator: isSeparator,
        group: '自定义'
      })
    }
  })

  var pageMode = PRINT_TO_PAGE_MODE[print.pageMode] || LIST_TO_PAGE_MODE[list.mode] || 'none'
  // 参与多行/分页的元素：优先读 print.flowKeys，兼容旧模板的 element.flow
  var flowElementKeys = Array.isArray(print.flowKeys)
    ? print.flowKeys.slice()
    : elements.filter(function(el) { return el && el.flow }).map(function(el) { return el.key })
  flowElementKeys = flowElementKeys.filter(function(key) {
    return key && key.indexOf(CODE_KEY_PREFIX) !== 0 && selectedAttrs.indexOf(key) >= 0
  })
  return {
    name: v2.name || '',
    renderVersion: TEMPLATE_RENDER_VERSION,
    provider: (v2.dataSource && v2.dataSource.provider) || '',
    businessType: v2.businessType || '',
    selectedAttrs: selectedAttrs,
    attrStyles: attrStyles,
    attrLabels: attrLabels,
    // 文本转码元素还原用：key → qrcode/barcode
    textCodeTypes: textCodeTypes,
    customLabels: customLabels,
    goodsFields: goodsFields,
    // 规格映射表为唯一权威；无映射表的模板沿用已保存的规格块（旧路径取值）
    specBlocks: normalizeSpecMapping(v2.specMapping).length
      ? normalizeSpecBlocks(flattenSpecMapping(v2.specMapping))
      : normalizeSpecBlocks(v2.specBlocks),
    specMapping: normalizeSpecMapping(v2.specMapping),
    specMappingReady: v2.specMappingReady === true && normalizeSpecMapping(v2.specMapping).length > 0,
    editorData: normalizeEditorData(v2.editorData),
    // 编辑页关闭的规格名/规格值，重新打开时保持不显示
    hiddenItemKeys: Array.isArray(v2.hiddenItemKeys) ? v2.hiddenItemKeys.slice() : [],
    // 「表单数据」勾选项：元素面板始终显示，画布删除不影响
    formDataCheckedKeys: Array.isArray(v2.formDataKeys) ? v2.formDataKeys.slice() : [],
    labelWidth: mmToPx(paper.width || 40),
    labelHeight: mmToPx(paper.height || 30),
    overflowMode: layout.overflowMode === 'ellipsis' ? 'ellipsis' : 'wrap',
    pageMode: pageMode,
    printListMode: list.mode || PAGE_MODE_TO_LIST[pageMode] || 'multiline',
    // 明细集合在记录对象里的取值路径，预览按它读取真实条数
    collectionPath: list.path || getCollectionPath(v2),
    flowElementKeys: flowElementKeys,
    recordMode: print.recordMode || 'oneLabelPerRecord',
    recordsPerPage: Math.max(1, parseInt(print.recordsPerPage, 10) || 1),
    maxRowsPerPage: Math.max(0, parseInt(list.maxRowsPerPage, 10) || 0),
    repeatHeader: list.repeatHeader !== false,
    meta: v2.meta || {}
  }
}

/**
 * 取明细集合路径：优先 print.list.path，其次 dataSource 里 scope=items 的字段 path
 */
export function getCollectionPath(v2) {
  var cfg = v2 || {}
  var list = (cfg.print && cfg.print.list) || {}
  if (list.path) return list.path
  var fields = (cfg.dataSource && cfg.dataSource.fields) || []
  for (var i = 0; i < fields.length; i++) {
    var field = fields[i] || {}
    if (field.scope === 'items' || field.type === 'list') {
      return field.path || DEFAULT_ITEM_PATH
    }
  }
  return DEFAULT_ITEM_PATH
}

/**
 * 按 path 从记录对象取值，支持 'goodsRows' 与 'a.b' 两种写法
 */
export function resolvePath(source, path) {
  if (!source || !path) return undefined
  if (Object.prototype.hasOwnProperty.call(source, path)) return source[path]
  return String(path).split('.').reduce(function(target, segment) {
    if (target == null) return undefined
    return target[segment]
  }, source)
}

/**
 * v2 → v3 渲染视图（仅供 LabelPaper 直接渲染 elements）
 * 元素 field 使用模板语义 key，保证与布局结果 styles[key] 对应。
 */
export function v2ToSchemaView(raw) {
  var v2 = parseTemplateRaw(raw) || {}
  var paper = v2.paper || {}
  var layout = v2.layout || {}
  var elements = (Array.isArray(layout.elements) ? layout.elements : []).map(function(el, index) {
    if (!el) return null
    if (el.type === 'barcode' || el.type === 'qrcode') {
      return {
        id: el.id || ('code_' + index),
        type: el.type,
        // 自定义码元素：field 指向元素自身 key（分页勾选/顺移按它匹配），文本固定
        field: el.customCode ? String(el.key || '') : (el.sourceKey || String(el.key || '').replace(CODE_KEY_PREFIX, '')),
        text: el.customCode ? (el.text == null ? '' : String(el.text)) : undefined,
        customCode: !!el.customCode,
        x: el.x,
        y: el.y,
        width: el.width,
        height: el.height
      }
    }
    return {
      id: el.id || ('e_' + index),
      type: 'text',
      field: el.key,
      label: el.label || '',
      text: el.text,
      x: el.x,
      y: el.y,
      width: el.width,
      height: el.height,
      fontSize: el.fontSize,
      fontWeight: el.fontWeight,
      letterSpacing: el.letterSpacing,
      lineHeight: el.lineHeight,
      textAlign: el.textAlign
    }
  }).filter(function(el) { return !!el })

  return {
    name: v2.name || '',
    businessType: v2.businessType || '',
    overflowMode: layout.overflowMode === 'ellipsis' ? 'ellipsis' : 'wrap',
    size: {
      width: paper.width,
      height: paper.height,
      unit: paper.unit || 'mm'
    },
    elements: elements
  }
}


