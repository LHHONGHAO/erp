/**
 * 标签模板完整流程（唯一路径）：元素数据组装 → 模板快照 → 按类型读取 / 保存 / 删除。
 * 编辑页（LabelEditMobile）与打印页（LabelPrintModal）都从这里读写模板，
 * 不再各自实现一套存储分发与快照组装。
 */
import { getAction, postAction } from '@/api/manage'
import { editorStateToV2, isLabelTemplateV2, v2ToSchemaView } from './labelTemplateV2'
import { replaceReceiptGoodsWithItems, isReceiptItemKey, isReceiptItemHidden } from './goodsLabelLayout'
import { fetchLabelTemplates, saveLabelTemplate, deleteLabelTemplate } from './labelTemplateStore'
import { fetchDeliveryTemplate, saveDeliveryTemplate } from './deliveryNoteTemplateStore'
import { normalizeEditorData } from './templateEditDataModel'
import { normalizeSpecBlocks, parseSpecItemKey } from './goodsSpecBlocks'
import { flattenSpecMapping } from './categorySpec'

/** 元素数据（属性面板结构）：保存模板、还原面板共用同一份结构 */
function buildEditorData(attrs, title, specBlocks) {
  return normalizeEditorData({ fields: attrs, title: title, specBlocks: specBlocks })
}

/** 编辑态规格块：映射表优先，其次由属性清单还原，最后回退编辑态 specBlocks */
function collectSpecBlocks(vm) {
  var mapped = normalizeSpecBlocks(flattenSpecMapping(vm.specMapping || []))
  if (mapped.length || vm.specMappingReady === true) return mapped
  var blocks = {}
  ;(vm.allAttrs || []).forEach(function(attr) {
    var parsed = parseSpecItemKey(attr.key)
    if (!parsed || parsed.rowNo !== 1) return
    var id = String(parsed.categoryId || '') + ':' + parsed.blockNo
    var block = blocks[id]
    if (!block) {
      block = blocks[id] = { name: '', values: [], blockNo: parsed.blockNo }
      if (parsed.categoryId != null) block.categoryId = String(parsed.categoryId)
      if (attr.categoryName) block.categoryName = attr.categoryName
      if (Array.isArray(attr.categoryMemberIds)) block.categoryMemberIds = attr.categoryMemberIds.slice()
      if (attr.specSourceName) block.sourceName = attr.specSourceName
      if (Array.isArray(attr.specSourceValues)) block.sourceValues = attr.specSourceValues.slice()
    }
    var text = attr.value == null ? '' : String(attr.value)
    if (parsed.kind === 'name') block.name = text.replace(/：$/, '')
    else block.values[parsed.valueNo - 1] = text
  })
  var collected = normalizeSpecBlocks(Object.keys(blocks).map(function(key) { return blocks[key] }))
  if (collected.length) return collected
  return normalizeSpecBlocks(vm.specBlocks)
}

/**
 * 小票明细元素统一链路（唯一入口）：编辑页占位数据、预览/打印真实数据都走这里。
 * 明细拆分（replaceReceiptGoodsWithItems）、隐藏项过滤、schema 重建全部收敛在本函数，
 * 编辑页与预览页只传数据来源，不再各自拼装。
 *
 * @param {Object} state {
 *   rows 明细数据行（编辑页传占位行，预览/打印传真实明细）,
 *   specBlocks 规格块, useSpecBlockPreset 编辑占位预置开关,
 *   compactHidden 按模板保存的可见项压缩, hiddenKeys 关闭的明细项,
 *   selectedAttrs, attrStyles, attrLabels, allAttrs 当前编辑态,
 *   paperWidth 纸宽, labelHeight/overflowMode schema 重建参数,
 *   template 预览/打印传入当前模板（带 schema 时同步重建 schema 视图）
 * }
 * @returns {Object} { selectedAttrs, attrStyles, attrLabels, allAttrs, itemKeys, schema? }
 */
export function applyReceiptRowsToState(state) {
  var s = state || {}
  var styles = s.attrStyles || {}
  // 明细起点：单据头（标题/单号/日期/会员）最低元素的底部 + 8
  var fallbackY = 0
  var headerKeys = ['rc_title', 'rc_no', 'rc_date', 'rc_member']
  headerKeys.forEach(function(key) {
    var style = styles[key]
    if (style) fallbackY = Math.max(fallbackY, (style.y || 0) + (style.height || 20))
  })
  var hiddenKeys = s.hiddenKeys || []
  var next = replaceReceiptGoodsWithItems({
    rows: s.rows,
    specBlocks: s.specBlocks,
    specMappingReady: s.specMappingReady === true,
    useSpecBlockPreset: !!s.useSpecBlockPreset,
    compactHidden: !!s.compactHidden,
    hiddenKeys: hiddenKeys,
    selectedAttrs: s.selectedAttrs,
    attrStyles: s.attrStyles,
    attrLabels: s.attrLabels,
    paperWidth: s.paperWidth,
    fallbackY: fallbackY ? fallbackY + 8 : 100
  })
  // 商品块转换后返回的有效隐藏清单：并入数据块的静态字段对应动态列隐藏项已被剔除
  var effectiveHidden = next.hiddenKeys || hiddenKeys
  var allAttrs = (s.allAttrs || []).filter(function(attr) {
    return !isReceiptItemKey(attr.key)
  }).concat(next.attrs)
  var selectedAttrs = next.selectedAttrs
  if (effectiveHidden.length) {
    selectedAttrs = selectedAttrs.filter(function(key) {
      return !isReceiptItemHidden(effectiveHidden, key)
    })
  }
  var result = {
    selectedAttrs: selectedAttrs,
    attrStyles: next.attrStyles,
    attrLabels: next.attrLabels,
    allAttrs: allAttrs,
    itemKeys: next.itemKeys
  }
  var tpl = s.template
  if (tpl && tpl.schema) {
    var v2 = editorStateToV2({
      name: tpl.name,
      provider: tpl.provider,
      businessType: tpl.businessType,
      selectedAttrs: selectedAttrs,
      attrStyles: next.attrStyles,
      attrLabels: next.attrLabels,
      allAttrs: allAttrs,
      goodsFields: tpl.goodsFields,
      labelWidth: s.paperWidth,
      labelHeight: s.labelHeight,
      overflowMode: s.overflowMode,
      flowElementKeys: tpl.flowElementKeys
    })
    result.schema = v2ToSchemaView(v2)
  }
  return result
}

/** getUiConfig 返回值可能是字符串或已解析对象，统一处理 */
function parseConfigData(data) {
  var cfg = data
  if (typeof data === 'string') {
    try { cfg = JSON.parse(data) } catch (e) { cfg = null }
  }
  if (cfg && typeof cfg === 'object' && cfg.config != null) cfg = cfg.config
  return cfg
}

/** 请求异常转成可读原因 */
function formatRequestError(err) {
  if (err && err.response) {
    var data = err.response.data
    var text = ''
    if (typeof data === 'string') {
      text = data
    } else if (data && typeof data === 'object') {
      text = data.message || data.msg || ''
    }
    return 'HTTP ' + err.response.status + (text ? ' ' + text : '')
  }
  return (err && err.message) ? err.message : '请求未发出'
}

/**
 * 当前编辑状态 → v2 模板快照
 * 模板保存、打印前快照、元素数据保存都走这一处组装。
 * meta: { id, serverId, type, objectType, materialId, categoryId, categoryName }
 */
export function buildTemplateSnapshot(vm, cfg, name, meta, options) {
  var info = meta || {}
  var o = options || {}
  var isDelivery = cfg && cfg.storage === 'deliveryTemplate'
  var specBlocks = collectSpecBlocks(vm)
  // 编辑页关闭的规格名/规格值随模板保存，重开编辑页/打印时保持不显示
  var hiddenItemKeys
  if (!o.skipHidden) {
    hiddenItemKeys = (vm.dynamicItemKeys || []).filter(function(key) {
      return vm.selectedAttrs.indexOf(key) === -1
    })
  }
  return editorStateToV2({
    name: name,
    provider: vm.provider || vm.businessType || '',
    businessType: vm.provider || vm.businessType || '',
    selectedAttrs: vm.selectedAttrs.slice(),
    attrStyles: Object.assign({}, vm.attrStyles),
    attrLabels: Object.assign({}, vm.attrLabels),
    allAttrs: vm.allAttrs,
    goodsFields: vm.goodsFields,
    specBlocks: specBlocks,
    // 规格数据映射表（唯一权威，按类别分组；specBlocks 由它扁平派生）
    specMapping: vm.specMapping || [],
    // 模板是否已建立规格数据映射表（决定打印/预览是否只加载已选规格）
    specMappingReady: vm.specMappingReady === true,
    editorData: buildEditorData(vm.allAttrs, vm.editorFormTitle, specBlocks),
    hiddenItemKeys: hiddenItemKeys,
    // 「表单数据」勾选项随模板保存，重开时元素面板仍完整列出
    formDataCheckedKeys: Object.keys(vm.formDataCheckedKeys || {}),
    labelWidth: vm.labelWidth,
    labelHeight: vm.labelHeight,
    overflowMode: vm.overflowMode,
    pageMode: vm.effectivePageMode,
    maxRowsPerPage: 0,
    repeatHeader: vm.repeatHeader,
    // 分页标签固定每页打印，不参与勾选，不写入勾选列表
    flowElementKeys: vm.flowElementKeys.filter(function(key) { return key !== 'page_no' })
  }, {
    type: info.type || (isDelivery ? 'delivery' : vm.templateType),
    kind: isDelivery ? 'delivery-note' : null,
    objectType: info.objectType || null,
    materialId: info.materialId || null,
    categoryId: info.categoryId || null,
    categoryName: info.categoryName || null
  })
}

/** 模板列表（标签多模板） */
export function fetchTemplateList() {
  return fetchLabelTemplates()
}

/**
 * 按模板类型读取：统一返回 { list, template, outdated, error }
 * - localTemplates：多模板列表，按 templateId 定位，缺省取第一条
 * - deliveryTemplate：发货单模板（单条）
 * - platformConfig：小票/会员/地址等单 key 覆盖写
 */
export function resolveTemplate(cfg, opts) {
  var o = opts || {}
  if (!cfg) return Promise.resolve({ list: null, template: null, outdated: [] })
  if (cfg.storage === 'localTemplates') {
    return fetchLabelTemplates().then(function(list) {
      var templates = list || []
      var target = o.templateId
        ? templates.find(function(t) {
          return String(t.id) === String(o.templateId) || String(t.serverId) === String(o.templateId)
        })
        : (templates[0] || null)
      var outdated = target && target.outdated ? [target] : []
      return {
        list: templates,
        template: target && !target.outdated ? target : null,
        outdated: outdated
      }
    })
  }
  if (cfg.storage === 'deliveryTemplate') {
    return fetchDeliveryTemplate().then(function(tpl) {
      var outdated = tpl && tpl.outdated ? [tpl] : []
      return { list: null, template: tpl && !tpl.outdated ? tpl : null, outdated: outdated }
    }).catch(function() {
      return { list: null, template: null, outdated: [] }
    })
  }
  return getAction('/platformConfig/getUiConfig', { key: cfg.platformConfigKey, _t: Date.now() }).then(function(res) {
    if (!res || res.code !== 200) {
      return { list: null, template: null, outdated: [], error: '接口返回异常' }
    }
    var raw = parseConfigData(res.data)
    if (!raw) {
      // 服务器尚未保存过模板走空状态；有内容但无法解析则提示错误
      var empty = res.data == null || res.data === ''
      return { list: null, template: null, outdated: [], error: empty ? '' : '模板数据为空或格式无法解析' }
    }
    if (typeof raw === 'object' && !Object.keys(raw).length) {
      return { list: null, template: null, outdated: [] }
    }
    // 成功读取后写本地缓存，供弱网/离线时兜底
    try {
      localStorage.setItem('jsh.uiConfig.' + cfg.platformConfigKey, typeof res.data === 'string' ? res.data : JSON.stringify(raw))
    } catch (e) { /* 忽略 */ }
    if (!isLabelTemplateV2(raw)) {
      return {
        list: null,
        template: null,
        outdated: [{ name: (cfg.title || '标签') + '模板', platformKey: cfg.platformConfigKey }]
      }
    }
    // 没有任何可渲染元素 → 视为无模板
    var elements = (raw.layout && raw.layout.elements) || []
    if (!elements.length) return { list: null, template: null, outdated: [] }
    return { list: null, template: raw, outdated: [] }
  }).catch(function(err) {
    var result = {
      list: null,
      template: null,
      outdated: [],
      error: (err && (err.message || err.msg)) || '网络异常'
    }
    if (o.useCache) {
      try {
        var cached = parseConfigData(localStorage.getItem('jsh.uiConfig.' + cfg.platformConfigKey))
        if (cached && isLabelTemplateV2(cached)) result.template = cached
      } catch (e) { /* 忽略 */ }
    }
    return result
  })
}

/** 保存结果提示（发货单/小票/会员/地址） */
function showSaveResult(vm, res, cfg) {
  if (res && res.code === 200) {
    vm.$message.success(cfg.successMsg)
  } else {
    vm.$message.error((res && res.data) || (cfg.title + '保存失败'))
  }
}

/**
 * 按模板类型保存：一份快照 + 一个分发入口
 * 返回 Promise<boolean>（标签多模板路径总是 resolve，避免阻塞关闭弹层）
 */
export function saveTemplateSnapshot(vm, cfg, name, meta, successMsg) {
  var info = meta || {}
  var v2 = buildTemplateSnapshot(vm, cfg, name, info, { skipHidden: cfg.storage === 'localTemplates' })
  if (cfg.storage === 'deliveryTemplate') {
    var base = vm.deliveryTemplate || {}
    var deliveryTpl = Object.assign({}, v2, {
      name: name,
      serverId: base.serverId || null,
      deliveryNote: base.deliveryNote
    })
    return saveDeliveryTemplate(deliveryTpl).then(function(res) {
      if (res && res.code === 200 && res.data != null) {
        // 保存后也保持 v2 统一格式
        vm.deliveryTemplate = Object.assign({}, deliveryTpl, {
          serverId: res.data,
          id: res.data,
          deliveryNote: base.deliveryNote
        })
      }
      showSaveResult(vm, res, cfg)
      return !!(res && res.code === 200)
    }).catch(function(err) {
      vm.$message.error(cfg.title + '保存失败：' + formatRequestError(err))
      return false
    })
  }
  if (cfg.storage === 'platformConfig') {
    var value = JSON.stringify(v2)
    return postAction('/platformConfig/saveUiConfig', { key: cfg.platformConfigKey, value: value }).then(function(res) {
      // 保存成功写本地缓存，供打印页弱网/离线兜底
      if (res && res.code === 200) {
        try { localStorage.setItem('jsh.uiConfig.' + cfg.platformConfigKey, value) } catch (e) { /* 忽略 */ }
      }
      showSaveResult(vm, res, cfg)
      return !!(res && res.code === 200)
    }).catch(function(err) {
      vm.$message.error(cfg.title + '保存失败：' + formatRequestError(err))
      return false
    })
  }
  // 标签多模板：覆盖或新增一条，保存后刷新列表拿权威 id
  var tpl = Object.assign({}, v2, {
    id: info.id || ('tpl_' + Date.now()),
    serverId: info.serverId || null,
    name: name,
    objectType: info.objectType || 'product',
    type: info.type || 'normal',
    materialId: info.materialId != null ? info.materialId : null,
    categoryId: info.categoryId != null ? info.categoryId : null,
    categoryName: info.categoryName || null
  })
  var idx = vm.templates.findIndex(function(t) { return t.id === tpl.id })
  if (idx > -1) vm.templates.splice(idx, 1, tpl)
  else vm.templates.push(tpl)
  vm.selectedTemplateId = tpl.id
  return saveLabelTemplate(tpl).then(function(res) {
    if (!res || res.code !== 200) {
      vm.$message.warning('模板已保存到本机，云端保存失败，其他设备暂无法同步')
      return null
    }
    var serverId = res.data
    return fetchLabelTemplates().then(function(list) {
      vm.templates = list
      var saved = null
      if (serverId != null && serverId !== '') {
        saved = list.find(function(t) { return String(t.serverId) === String(serverId) })
      }
      if (!saved) {
        saved = list.find(function(t) { return t.name === tpl.name })
      }
      if (saved) vm.selectedTemplateId = saved.id
      return saved
    }).catch(function() {
      // 刷新失败时保留本地乐观数据，不阻塞保存结果
      return null
    }).then(function(saved) {
      vm.$message.success(successMsg || '模板已保存')
      return saved
    })
  }).catch(function() {
    vm.$message.warning('模板已保存到本机，云端保存失败，其他设备暂无法同步')
    return null
  })
}

/** 删除模板：platformConfig 无删除接口，写空值即视为删除 */
export function deleteTemplate(cfg, tpl) {
  return (tpl && tpl.platformKey)
    ? postAction('/platformConfig/saveUiConfig', { key: tpl.platformKey, value: '' })
    : deleteLabelTemplate(tpl)
}
