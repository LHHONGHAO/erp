/**
 * 商品信息展示工具（图片地址、规格显示）
 * 商品信息页、库存页、单据明细等所有引用商品字段的表格统一使用这里的方法，避免各处重复实现
 */

import { MATERIAL_COLUMNS } from '@/views/material/config/materialColumns'

/**
 * 拼接 SKU 图片完整地址
 * 说明：不使用 getFileAccessHttpUrl，因其会过滤含 '[' 等特殊字符的路径
 */
export function getSkuImgUrl(imgUrl) {
  if (!imgUrl) return ''
  if (imgUrl.startsWith('http')) return imgUrl
  const base = (window._CONFIG && window._CONFIG['domianURL']) ? window._CONFIG['domianURL'] : ''
  // 后端图片预览接口为 /systemConfig/static/**，imgUrl 存储的是相对路径
  if (imgUrl.indexOf('systemConfig/static/') === -1) {
    return base + '/systemConfig/static/' + imgUrl
  }
  return base + '/' + imgUrl
}

/**
 * 行图片地址：优先 extend.img_url（imgName），为空时从旧规格值结构中匹配规格图
 */
export function getRowImgUrl(record) {
  if (!record) return ''
  if (record.imgName) return getSkuImgUrl(record.imgName)
  if (record.attribute && record.specValues) {
    try {
      const attrObj = JSON.parse(record.attribute)
      const modules = attrObj.specModules || []
      const specVals = JSON.parse(record.specValues)
      for (let i = 0; i < specVals.length; i++) {
        const text = specVals[i].valueText
        for (let j = 0; j < modules.length; j++) {
          const values = modules[j].values || []
          for (let k = 0; k < values.length; k++) {
            if (values[k].text === text && values[k].image) {
              return getSkuImgUrl(values[k].image)
            }
          }
        }
      }
    } catch (e) { /* 旧数据格式异常时忽略 */ }
  }
  return ''
}

/**
 * 规格显示：每个子规格一行，格式「子规格名：子规格值」，多行左对齐
 * 例：颜色：蘑菇
 *     被套：220*230cm
 * 取值优先级：spec_values（含子规格明细）> sku.spec_json > sku.spec_summary
 */
export function getSpecDisplay(record) {
  if (!record) return ''
  // 1. spec_values 里保存了每个规格大类的子规格名与子规格值，可精确还原
  if (record.specValues) {
    try {
      const arr = JSON.parse(record.specValues)
      if (Array.isArray(arr) && arr.length > 0) {
        const lines = []
        arr.forEach(function(item) {
          const name = item.attributeName || item.name || ''
          const childNames = item.childNames || []
          const subValues = item.subValues || {}
          if (childNames.length > 0) {
            // 组合规格：每个子规格单独一行
            childNames.forEach(function(childName) {
              const value = subValues[childName]
              if (value != null && String(value).trim() !== '') {
                lines.push(childName + '：' + String(value).trim())
              }
            })
          } else {
            const value = item.valueText || item.value || ''
            if (value !== '') {
              lines.push(name ? (name + '：' + value) : value)
            }
          }
        })
        if (lines.length > 0) return lines.join('\n')
      }
    } catch (e) { /* 解析失败继续走兜底 */ }
  }
  if (record.specJson) {
    try {
      const arr = JSON.parse(record.specJson)
      if (Array.isArray(arr) && arr.length > 0) {
        return arr.map(function(item) {
          const name = item.name || item.attributeName || ''
          const value = item.value || item.valueText || ''
          return name ? (name + '：' + value) : value
        }).filter(function(t) { return t !== '' }).join('\n')
      }
    } catch (e) { /* 解析失败继续走兜底 */ }
  }
  if (record.specSummary) {
    return record.specSummary.split(';').map(function(part) {
      return part.replace(':', '：')
    }).filter(function(t) { return t !== '' }).join('\n')
  }
  return ''
}

/**
 * 规格值合并为一列，用 / 分割，如 "蓝色/200*230/250*230"
 */
export function getSpecValueMerged(sku) {
  if (!sku || !sku.specValues) return ''
  try {
    const arr = JSON.parse(sku.specValues)
    if (!Array.isArray(arr)) return sku.specValues || ''
    return arr.map(function(item) { return item.valueText || '' }).filter(function(v) { return v !== '' }).join('/')
  } catch (e) {
    return sku.specValues || ''
  }
}

/**
 * 标签可选属性：商品信息表格的全部列（商品基础信息 + SKU 信息 + 库存）
 * 标签编辑页与打印标签预览页共用，保证两处属性、取值完全一致
 * 取值 SKU 优先、商品行其次；列值为空也保留，避免模板里的属性在打印页丢失
 * 末尾追加表格之外的旧属性键，兼容历史标签模板
 */
export function buildLabelAttrs(record, sku) {
  var row = record || {}
  var skuRow = sku || {}
  var list = []
  var exists = {}
  var resolve = function(field) {
    var skuVal = skuRow[field]
    if (skuVal != null && skuVal !== '') return skuVal
    return row[field]
  }
  MATERIAL_COLUMNS.forEach(function(col) {
    if (!col.field || col.field === 'action' || col.field === 'imgName' || exists[col.field]) return
    var value = col.field === 'specDisplay'
      ? getSpecDisplay(Object.assign({}, row, skuRow))
      : resolve(col.field)
    exists[col.field] = true
    list.push({ key: col.field, label: col.title, value: value == null ? '' : String(value), isCustom: false })
  })
  var legacy = [
    { key: 'standard', label: '规格型号', value: row.standard },
    { key: 'model', label: '型号', value: row.model },
    { key: 'color', label: '颜色', value: row.color },
    { key: 'commodityUnit', label: '单位', value: row.commodityUnit },
    { key: 'otherField1', label: '扩展1', value: row.otherField1 },
    { key: 'otherField2', label: '扩展2', value: row.otherField2 },
    { key: 'otherField3', label: '扩展3', value: row.otherField3 },
    { key: 'specValues', label: '规格值', value: skuRow.specValuesText },
    { key: 'wholesaleDecimal', label: '销售价', value: skuRow.wholesaleDecimal },
    { key: 'lowDecimal', label: '最低售价', value: skuRow.lowDecimal }
  ]
  legacy.forEach(function(attr) {
    if (exists[attr.key]) return
    exists[attr.key] = true
    list.push({ key: attr.key, label: attr.label, value: attr.value == null ? '' : String(attr.value), isCustom: false })
  })
  return list
}
