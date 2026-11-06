/**
 * 标签模板 v3 结构（elements[]）与编辑器内部结构互转
 *
 * 约定：
 *   - 模板坐标/尺寸统一用毫米（x、y、width、height）
 *   - 字号沿用像素（编辑与打印一致），不参与毫米换算
 *   - 模板只保存数据源标识、字段绑定与布局，不保存业务数据、不写 SQL
 */

export var PX_PER_MM = 3.779528
export var SCHEMA_VERSION = 1
export var CODE_KEY_PREFIX = 'code__'

function round2(value) {
  return Math.round((Number(value) || 0) * 100) / 100
}

export function pxToMm(px) {
  return round2((Number(px) || 0) / PX_PER_MM)
}

export function mmToPx(mm) {
  return Math.round((Number(mm) || 0) * PX_PER_MM)
}

/** 新建空模板 */
export function createSchema(options) {
  var opts = options || {}
  return {
    name: opts.name || '未命名标签',
    version: SCHEMA_VERSION,
    businessType: opts.businessType || opts.provider || '',
    size: {
      width: opts.width || 40,
      height: opts.height || 30,
      unit: 'mm'
    },
    dataSource: {
      provider: opts.provider || '',
      fields: []
    },
    elements: [],
    meta: {}
  }
}

/**
 * 编辑器内部结构 → v3 模板
 *
 * @param {Object} tpl { id,name,type,materialId,categoryId,categoryName,
 *                       selectedAttrs,attrStyles,attrLabels,customLabels,labelWidth,labelHeight,provider }
 */
export function legacyToSchema(tpl) {
  var source = tpl || {}
  var styles = source.attrStyles || {}
  var labels = source.attrLabels || {}
  var isDelivery = source.templateType === 'delivery'
  var elements = []
  var usedFields = []
  var customMap = {}
  ;(source.customLabels || []).forEach(function(item) {
    customMap[item.key] = item
  })

  ;(source.selectedAttrs || []).forEach(function(key, index) {
    var style = styles[key] || {}
    var custom = customMap[key]
    var element = {
      id: 'el_' + (index + 1),
      type: 'text',
      field: custom ? null : key,
      label: Object.prototype.hasOwnProperty.call(labels, key)
        ? labels[key]
        : (custom ? custom.label : ''),
      x: pxToMm(style.x),
      y: pxToMm(style.y),
      fontSize: style.fontSize || 14,
      fontWeight: style.fontWeight || 'normal',
      letterSpacing: style.letterSpacing || 0,
      lineHeight: style.lineHeight || 1.3
    }
    if (style.width) element.width = pxToMm(style.width)
    if (style.height) element.height = pxToMm(style.height)
    if (custom) {
      element.text = custom.value
      element.custom = true
    }
    elements.push(element)
    if (!custom) usedFields.push(key)

    var codeStyle = styles[CODE_KEY_PREFIX + key]
    if (codeStyle && !custom) {
      var isQr = codeStyle.codeType === 'qrcode'
      elements.push({
        id: 'el_' + (index + 1) + '_code',
        type: isQr ? 'qrcode' : 'barcode',
        field: key,
        x: pxToMm(codeStyle.x),
        y: pxToMm(codeStyle.y),
        width: pxToMm(codeStyle.width || (isQr ? 80 : 160)),
        height: pxToMm(codeStyle.height || (isQr ? 80 : 50))
      })
    }
  })

  return {
    name: source.name || '未命名标签',
    version: SCHEMA_VERSION,
    businessType: source.businessType || source.provider || (isDelivery ? 'shipping.delivery' : ''),
    size: {
      width: pxToMm(source.labelWidth || 320),
      height: pxToMm(source.labelHeight || 240),
      unit: 'mm'
    },
    dataSource: {
      provider: source.provider || '',
      fields: usedFields
    },
    elements: elements,
    meta: {
      type: source.type || null,
      materialId: source.materialId || null,
      categoryId: source.categoryId || null,
      categoryName: source.categoryName || null,
      legacyId: typeof source.id === 'string' ? source.id : null
    }
  }
}

/**
 * v3 模板 → 编辑器内部结构
 * 同时兼容旧版（selectedAttrs/attrStyles）模板对象
 */
export function schemaToLegacy(schema) {
  var source = schema || {}
  if (!source.elements) {
    return source
  }
  var size = source.size || {}
  var result = {
    selectedAttrs: [],
    attrStyles: {},
    attrLabels: {},
    customLabels: [],
    labelWidth: mmToPx(size.width || 40),
    labelHeight: mmToPx(size.height || 30),
    provider: (source.dataSource && source.dataSource.provider) || ''
  }
  var meta = source.meta || {}
  result.type = meta.type || null
  result.materialId = meta.materialId || null
  result.categoryId = meta.categoryId || null
  result.categoryName = meta.categoryName || null

  ;(source.elements || []).forEach(function(element, index) {
    if (!element) return
    if (element.type === 'barcode' || element.type === 'qrcode') {
      if (!element.field) return
      result.attrStyles[CODE_KEY_PREFIX + element.field] = {
        x: mmToPx(element.x),
        y: mmToPx(element.y),
        width: mmToPx(element.width || 0),
        height: mmToPx(element.height || 0),
        codeType: element.type
      }
      return
    }
    if (element.type && element.type !== 'text' && element.type !== 'table') return
    var key = element.field || element.id || ('custom_' + index)
    result.selectedAttrs.push(key)
    var style = {
      x: mmToPx(element.x),
      y: mmToPx(element.y),
      fontSize: element.fontSize || 14,
      fontWeight: element.fontWeight || 'normal',
      letterSpacing: element.letterSpacing || 0,
      lineHeight: element.lineHeight || 1.3
    }
    if (element.width) style.width = mmToPx(element.width)
    if (element.height) style.height = mmToPx(element.height)
    result.attrStyles[key] = style
    result.attrLabels[key] = element.label == null ? '' : element.label
    if (element.custom || (!element.field && element.text != null)) {
      result.customLabels.push({
        key: key,
        label: element.label || '',
        value: element.text == null ? '' : element.text,
        isCustom: true
      })
    }
  })
  return result
}

/** 校验模板结构，返回错误信息数组（空数组表示通过） */
export function validateSchema(schema) {
  var errors = []
  if (!schema || typeof schema !== 'object') {
    return ['模板结构为空']
  }
  if (!schema.name) errors.push('模板名称不能为空')
  if (!schema.size || !schema.size.width || !schema.size.height) {
    errors.push('缺少标签尺寸')
  }
  var provider = schema.dataSource && schema.dataSource.provider
  if (!provider) errors.push('未指定业务数据源')
  var fields = (schema.dataSource && schema.dataSource.fields) || []
  var ids = {}
  ;(schema.elements || []).forEach(function(element, index) {
    if (!element.id) {
      errors.push('第 ' + (index + 1) + ' 个元素缺少唯一 ID')
    } else if (ids[element.id]) {
      errors.push('元素 ID 重复：' + element.id)
    } else {
      ids[element.id] = true
    }
    if (element.field && fields.indexOf(element.field) === -1) {
      errors.push('元素 ' + (element.id || index) + ' 的字段不在数据源字段内：' + element.field)
    }
  })
  return errors
}
