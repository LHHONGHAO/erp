/**
 * 商品信息展示工具（图片地址、规格显示）
 * 商品信息页、库存页、单据明细等所有引用商品字段的表格统一使用这里的方法，避免各处重复实现
 */

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
