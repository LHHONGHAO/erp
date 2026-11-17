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
