<template>
  <div ref="container">
    <a-modal
      title="标签编辑"
      :width="1100"
      :visible="visible"
      :getContainer="() => $refs.container"
      :maskClosable="false"
      :footer="null"
      @cancel="handleCancel"
      style="top:5%;height:90%;">
      <a-row :gutter="12">
        <!-- 左栏：属性勾选 + 自定义标签 + 模板管理 -->
        <a-col :span="6">
          <div class="label-edit-panel">
            <div class="label-edit-section-title">选择属性</div>
            <a-checkbox-group v-model="selectedAttrs" @change="onSelectedChange" style="width:100%">
              <a-row v-for="attr in allAttrs" :key="attr.key">
                <a-checkbox :value="attr.key">
                  {{ attr.label }}
                  <a-icon v-if="attr.isCustom" type="close" @click.prevent.stop="removeCustomLabel(attr.key)" style="margin-left:4px;color:#ff4d4f;cursor:pointer;font-size:11px" />
                </a-checkbox>
              </a-row>
            </a-checkbox-group>
            <a-button size="small" type="dashed" block @click="addCustomLabel" style="margin-top:8px">
              <a-icon type="plus" />添加自定义标签
            </a-button>
          </div>

          <!-- 模板管理：分组卡片 -->
          <div class="label-edit-panel" style="margin-top:10px">
            <div class="label-edit-section-title">模板管理</div>
            <!-- 普通模板 -->
            <div class="template-group">
              <div class="template-group-title">普通模板（当前商品）</div>
              <div v-if="normalTemplates.length === 0" class="template-empty">暂无</div>
              <div class="template-cards">
                <div
                  v-for="tpl in normalTemplates"
                  :key="tpl.id"
                  class="template-card"
                  :class="{ active: selectedTemplateId === tpl.id }"
                  @click="onTemplateChange(tpl.id)"
                >{{ tpl.name }}</div>
              </div>
            </div>
            <!-- 默认模板 -->
            <div class="template-group">
              <div class="template-group-title">默认模板（品类）</div>
              <div v-if="defaultTemplates.length === 0" class="template-empty">暂无</div>
              <div class="template-cards">
                <div
                  v-for="tpl in defaultTemplates"
                  :key="tpl.id"
                  class="template-card"
                  :class="{ active: selectedTemplateId === tpl.id }"
                  @click="onTemplateChange(tpl.id)"
                >{{ tpl.name }}<span class="template-cat-name">（{{ tpl.categoryName }}）</span></div>
              </div>
            </div>
            <!-- 保存模板 -->
            <div style="margin-top:10px;border-top:1px solid #e8e8e8;padding-top:8px">
              <a-input v-model="newTemplateName" placeholder="模板名称" style="margin-bottom:8px" />
              <a-radio-group v-model="templateType" style="margin-bottom:8px">
                <a-radio value="normal">普通模板</a-radio>
                <a-radio value="default">默认模板</a-radio>
              </a-radio-group>
              <a-select
                v-if="templateType === 'default'"
                v-model="templateCategoryId"
                style="width:100%;margin-bottom:8px"
                placeholder="选择品类"
                showSearch
                :filterOption="filterCategory"
              >
                <a-select-option v-for="cat in categoryList" :key="cat.value" :value="cat.value">{{ cat.label }}</a-select-option>
              </a-select>
              <a-button type="primary" size="small" @click="saveTemplate" :disabled="!newTemplateName || (templateType === 'default' && !templateCategoryId)">保存模板</a-button>
              <a-button size="small" @click="deleteTemplate" style="margin-left:8px" :disabled="!selectedTemplateId">删除</a-button>
            </div>
          </div>
        </a-col>

        <!-- 中栏：标签预览 -->
        <a-col :span="10">
          <div class="label-edit-panel">
            <div class="label-edit-section-title">
              标签预览（{{ labelWidthMm }}mm × {{ labelHeightMm }}mm）
              <span style="font-weight:normal;font-size:12px;color:#999;margin-left:8px">点击属性编辑样式，拖拽移动位置，拖拽右下角调节尺寸，双击自定义标签编辑内容</span>
            </div>
            <div
              class="label-preview"
              :style="{ width: labelWidth + 'px', height: labelHeight + 'px' }"
              ref="labelPreview"
              @mousemove="onPreviewMouseMove"
              @mouseup="onPreviewMouseUp"
              @mouseleave="onPreviewMouseUp"
            >
              <div v-if="selectedAttrList.length === 0" class="label-preview-empty">请先勾选属性</div>
              <div
                v-for="attr in selectedAttrList"
                :key="attr.key"
                class="label-preview-item"
                :class="{ active: currentAttrKey === attr.key }"
                :style="getItemStyle(attr.key)"
                @mousedown="onItemMouseDown($event, attr.key)"
                @dblclick="onItemDoubleClick($event, attr)"
              >
                <template v-if="attrLabels[attr.key]">{{ attrLabels[attr.key] }}: </template>
                <input
                  v-if="editingKey === attr.key"
                  :value="attr.value"
                  @input="updateCustomValue(attr.key, $event.target.value)"
                  @blur="editingKey = null"
                  @keyup.enter="editingKey = null"
                  @mousedown.stop
                  class="label-edit-input"
                />
                <template v-else>{{ attr.value }}</template>
                <!-- resize 手柄 -->
                <div
                  v-if="currentAttrKey === attr.key && editingKey !== attr.key"
                  class="resize-handle"
                  @mousedown.stop.prevent="onResizeMouseDown($event, attr.key)"
                ></div>
              </div>
            </div>
            <div style="margin-top:12px">
              <a-button type="primary" @click="handlePrint" :disabled="selectedAttrList.length === 0">打印</a-button>
              <a-button @click="handleReset" style="margin-left:8px">重置位置</a-button>
            </div>
          </div>
        </a-col>

        <!-- 右栏：标签尺寸 + 样式编辑 -->
        <a-col :span="8">
          <div class="label-edit-panel">
            <!-- 标签宽高（毫米，固定最上端） -->
            <div class="label-size-editor">
              <div class="label-edit-section-title">标签尺寸（毫米）</div>
              <a-row :gutter="8">
                <a-col :span="12">
                  <span class="style-label">宽</span>
                  <a-input-number v-model="labelWidthMm" :min="10" :max="200" size="small" style="width:70px" />
                  <span class="style-label" style="margin-left:2px">mm</span>
                </a-col>
                <a-col :span="12">
                  <span class="style-label">高</span>
                  <a-input-number v-model="labelHeightMm" :min="10" :max="200" size="small" style="width:70px" />
                  <span class="style-label" style="margin-left:2px">mm</span>
                </a-col>
              </a-row>
            </div>

            <!-- 当前选中属性的样式编辑 -->
            <div class="label-edit-section-title" style="margin-top:12px">样式编辑</div>
            <div v-if="!currentAttrKey" class="label-edit-empty">请点击预览中的属性</div>
            <div v-else>
              <div class="attr-style-name">
                {{ getCurrentAttrLabel }}
                <a-tag v-if="getCurrentAttr && getCurrentAttr.isCustom" color="blue" style="font-size:11px;margin-left:4px">自定义</a-tag>
              </div>

              <!-- 属性名：下拉选择 + 编辑框 -->
              <div style="margin-bottom:8px">
                <span class="style-label">属性名</span>
                <a-select
                  v-model="presetLabelSelect"
                  style="width:100%"
                  placeholder="快速选择属性名"
                  @change="onPresetLabelSelect"
                >
                  <a-select-option v-for="name in presetLabelNames" :key="name" :value="name">{{ name }}</a-select-option>
                </a-select>
                <a-input
                  v-model="attrLabels[currentAttrKey]"
                  placeholder="编辑属性名（留空则只显示值）"
                  style="margin-top:4px"
                  allowClear
                />
              </div>

              <!-- 字号 + 粗细 -->
              <a-row :gutter="4" style="margin-top:8px">
                <a-col :span="12">
                  <span class="style-label">字号</span>
                  <a-input-number v-model="attrStyles[currentAttrKey].fontSize" :min="6" :max="72" size="small" style="width:60px" />
                </a-col>
                <a-col :span="12">
                  <span class="style-label">粗细</span>
                  <a-select v-model="attrStyles[currentAttrKey].fontWeight" size="small" style="width:70px">
                    <a-select-option value="normal">正常</a-select-option>
                    <a-select-option value="bold">粗体</a-select-option>
                  </a-select>
                </a-col>
              </a-row>

              <!-- X + Y -->
              <a-row :gutter="4" style="margin-top:4px">
                <a-col :span="12">
                  <span class="style-label">X</span>
                  <a-input-number v-model="attrStyles[currentAttrKey].x" :min="0" :max="labelWidth" size="small" style="width:60px" />
                </a-col>
                <a-col :span="12">
                  <span class="style-label">Y</span>
                  <a-input-number v-model="attrStyles[currentAttrKey].y" :min="0" :max="labelHeight" size="small" style="width:60px" />
                </a-col>
              </a-row>

              <!-- 标签尺寸（px） -->
              <a-row :gutter="4" style="margin-top:4px">
                <a-col :span="12">
                  <span class="style-label">宽</span>
                  <a-input-number v-model="attrStyles[currentAttrKey].width" :min="20" :max="500" size="small" style="width:60px" />
                </a-col>
                <a-col :span="12">
                  <span class="style-label">高</span>
                  <a-input-number v-model="attrStyles[currentAttrKey].height" :min="10" :max="200" size="small" style="width:60px" />
                </a-col>
              </a-row>
            </div>
          </div>
        </a-col>
      </a-row>
    </a-modal>
  </div>
</template>

<script>
  // 像素与毫米转换常量（96dpi: 1mm ≈ 3.779528px）
  var PX_PER_MM = 3.779528

  export default {
    name: "LabelEditModal",
    data() {
      return {
        visible: false,
        record: {},
        sku: {},
        categoryTree: [],
        allAttrs: [],
        selectedAttrs: [],
        attrStyles: {},
        attrLabels: {},
        currentAttrKey: null,
        editingKey: null,
        templates: [],
        selectedTemplateId: null,
        newTemplateName: '',
        templateType: 'normal',
        templateCategoryId: null,
        labelWidth: 320,
        labelHeight: 240,
        // 拖拽移动
        dragKey: null,
        dragStartX: 0,
        dragStartY: 0,
        dragOrigX: 0,
        dragOrigY: 0,
        // 拖拽resize
        resizeKey: null,
        resizeStartX: 0,
        resizeStartY: 0,
        resizeOrigW: 0,
        resizeOrigH: 0,
        // 属性名下拉
        presetLabelSelect: undefined,
        presetLabelNames: [
          '品名', '商品名称', '规格', '条码', '货号', '价格', '零售价',
          '颜色', '尺码', '尺寸', '材质', '产地', '等级', '重量',
          '品牌', '型号', '批次', '生产日期', '保质期', 'SKU码'
        ],
        customCounter: 0
      }
    },
    computed: {
      selectedAttrList() {
        return this.allAttrs.filter(attr => this.selectedAttrs.indexOf(attr.key) > -1)
      },
      getCurrentAttr() {
        return this.allAttrs.find(a => a.key === this.currentAttrKey)
      },
      getCurrentAttrLabel() {
        var attr = this.getCurrentAttr
        return attr ? attr.label : ''
      },
      labelWidthMm: {
        get() { return Math.round(this.labelWidth / PX_PER_MM) },
        set(val) { this.labelWidth = Math.round(val * PX_PER_MM) }
      },
      labelHeightMm: {
        get() { return Math.round(this.labelHeight / PX_PER_MM) },
        set(val) { this.labelHeight = Math.round(val * PX_PER_MM) }
      },
      categoryList() {
        return this.flattenCategoryTree(this.categoryTree)
      },
      normalTemplates() {
        var mid = this.record.id
        return this.templates.filter(function(t) { return t.type === 'normal' && t.materialId === mid })
      },
      defaultTemplates() {
        return this.templates.filter(function(t) { return t.type === 'default' })
      }
    },
    mounted() {
      this.loadTemplates()
    },
    methods: {
      open(record, sku, categoryTree) {
        this.record = record || {}
        this.sku = sku || {}
        this.categoryTree = categoryTree || []
        this.allAttrs = this.buildAttrList()
        this.selectedAttrs = []
        this.attrStyles = {}
        this.attrLabels = {}
        this.currentAttrKey = null
        this.editingKey = null
        this.selectedTemplateId = null
        this.newTemplateName = ''
        this.templateType = 'normal'
        this.templateCategoryId = this.record.categoryId || null
        this.presetLabelSelect = undefined
        this.customCounter = 0
        this.labelWidth = 320
        this.labelHeight = 240
        this.visible = true
        this.loadCategoryDefaultTemplate()
      },
      handleCancel() {
        this.visible = false
      },
      buildAttrList() {
        var record = this.record
        var sku = this.sku
        return [
          { key: 'name', label: '商品名称', value: record.name || '', isCustom: false },
          { key: 'standard', label: '规格型号', value: record.standard || '', isCustom: false },
          { key: 'model', label: '型号', value: record.model || '', isCustom: false },
          { key: 'color', label: '颜色', value: record.color || '', isCustom: false },
          { key: 'brand', label: '品牌', value: record.brand || '', isCustom: false },
          { key: 'mnemonic', label: '助记码', value: record.mnemonic || '', isCustom: false },
          { key: 'weight', label: '重量', value: record.weight != null ? String(record.weight) : '', isCustom: false },
          { key: 'position', label: '仓位货架', value: record.position || '', isCustom: false },
          { key: 'mfrs', label: '制造商', value: record.mfrs || '', isCustom: false },
          { key: 'mBarCode', label: '条码', value: record.mBarCode || '', isCustom: false },
          { key: 'commodityUnit', label: '单位', value: record.commodityUnit || '', isCustom: false },
          { key: 'otherField1', label: '扩展1', value: record.otherField1 || '', isCustom: false },
          { key: 'otherField2', label: '扩展2', value: record.otherField2 || '', isCustom: false },
          { key: 'otherField3', label: '扩展3', value: record.otherField3 || '', isCustom: false },
          { key: 'specValues', label: '规格值', value: sku.specValuesText || '', isCustom: false },
          { key: 'skuCode', label: 'SKU码', value: sku.skuCode || '', isCustom: false },
          { key: 'purchaseDecimal', label: '采购价', value: sku.purchaseDecimal || '', isCustom: false },
          { key: 'commodityDecimal', label: '零售价', value: sku.commodityDecimal || '', isCustom: false },
          { key: 'wholesaleDecimal', label: '销售价', value: sku.wholesaleDecimal || '', isCustom: false },
          { key: 'lowDecimal', label: '最低售价', value: sku.lowDecimal || '', isCustom: false },
          { key: 'currentStock', label: '库存', value: sku.currentStock != null ? String(sku.currentStock) : '', isCustom: false }
        ].filter(function(attr) { return attr.value })
      },
      flattenCategoryTree(tree) {
        var result = []
        function traverse(nodes, prefix) {
          if (!nodes) return
          nodes.forEach(function(node) {
            var label = prefix ? prefix + ' / ' + (node.title || node.label) : (node.title || node.label)
            result.push({ value: node.value || node.id, label: label })
            if (node.children) traverse(node.children, label)
          })
        }
        traverse(tree, '')
        return result
      },
      filterCategory(input, option) {
        return option.componentOptions.children[0].text.indexOf(input) >= 0
      },
      getCategoryName(categoryId) {
        if (!categoryId) return ''
        var cat = this.categoryList.find(function(c) { return String(c.value) === String(categoryId) })
        return cat ? cat.label : ''
      },
      getItemStyle(key) {
        var s = this.attrStyles[key]
        if (!s) return {}
        return {
          left: s.x + 'px',
          top: s.y + 'px',
          fontSize: s.fontSize + 'px',
          fontWeight: s.fontWeight,
          width: (s.width || 120) + 'px',
          height: (s.height || 24) + 'px'
        }
      },
      onSelectedChange() {
        var self = this
        this.selectedAttrs.forEach(function(key) {
          if (!self.attrStyles[key]) {
            var idx = self.selectedAttrs.indexOf(key)
            self.$set(self.attrStyles, key, {
              fontSize: 14, x: 10, y: 10 + idx * 30, fontWeight: 'normal',
              width: 120, height: 24
            })
          }
          if (!self.attrLabels[key]) {
            var attr = self.allAttrs.find(function(a) { return a.key === key })
            self.$set(self.attrLabels, key, attr ? attr.label : '')
          }
        })
        if (this.currentAttrKey && this.selectedAttrs.indexOf(this.currentAttrKey) === -1) {
          this.currentAttrKey = null
        }
      },
      // 自定义标签
      addCustomLabel() {
        this.customCounter++
        var key = 'custom_' + Date.now() + '_' + this.customCounter
        var customAttr = {
          key: key,
          label: '自定义' + this.customCounter,
          value: '双击编辑内容',
          isCustom: true
        }
        this.allAttrs.push(customAttr)
        this.selectedAttrs.push(key)
        var idx = this.selectedAttrs.length - 1
        this.$set(this.attrStyles, key, {
          fontSize: 14, x: 10, y: 10 + idx * 30, fontWeight: 'normal',
          width: 120, height: 24
        })
        this.$set(this.attrLabels, key, '')
      },
      removeCustomLabel(key) {
        var idx = this.allAttrs.findIndex(function(a) { return a.key === key })
        if (idx > -1) this.allAttrs.splice(idx, 1)
        var sIdx = this.selectedAttrs.indexOf(key)
        if (sIdx > -1) this.selectedAttrs.splice(sIdx, 1)
        if (this.currentAttrKey === key) this.currentAttrKey = null
        delete this.attrStyles[key]
        delete this.attrLabels[key]
      },
      updateCustomValue(key, value) {
        var attr = this.allAttrs.find(function(a) { return a.key === key })
        if (attr) attr.value = value
      },
      // 属性名下拉
      onPresetLabelSelect(value) {
        if (this.currentAttrKey && value) {
          this.$set(this.attrLabels, this.currentAttrKey, value)
        }
        this.presetLabelSelect = undefined
      },
      // 拖拽移动
      onItemMouseDown(e, key) {
        if (this.editingKey === key) return
        e.preventDefault()
        e.stopPropagation()
        this.dragKey = key
        this.dragStartX = e.clientX
        this.dragStartY = e.clientY
        this.dragOrigX = this.attrStyles[key] ? this.attrStyles[key].x : 0
        this.dragOrigY = this.attrStyles[key] ? this.attrStyles[key].y : 0
        this.currentAttrKey = key
      },
      // 拖拽resize
      onResizeMouseDown(e, key) {
        this.resizeKey = key
        this.resizeStartX = e.clientX
        this.resizeStartY = e.clientY
        this.resizeOrigW = (this.attrStyles[key] && this.attrStyles[key].width) || 120
        this.resizeOrigH = (this.attrStyles[key] && this.attrStyles[key].height) || 24
      },
      onPreviewMouseMove(e) {
        // 拖拽移动
        if (this.dragKey) {
          var dx = e.clientX - this.dragStartX
          var dy = e.clientY - this.dragStartY
          var newX = Math.max(0, Math.min(this.labelWidth - 10, this.dragOrigX + dx))
          var newY = Math.max(0, Math.min(this.labelHeight - 10, this.dragOrigY + dy))
          if (this.attrStyles[this.dragKey]) {
            this.$set(this.attrStyles[this.dragKey], 'x', Math.round(newX))
            this.$set(this.attrStyles[this.dragKey], 'y', Math.round(newY))
          }
        }
        // 拖拽resize
        if (this.resizeKey) {
          var rdx = e.clientX - this.resizeStartX
          var rdy = e.clientY - this.resizeStartY
          var newW = Math.max(30, this.resizeOrigW + rdx)
          var newH = Math.max(12, this.resizeOrigH + rdy)
          if (this.attrStyles[this.resizeKey]) {
            this.$set(this.attrStyles[this.resizeKey], 'width', Math.round(newW))
            this.$set(this.attrStyles[this.resizeKey], 'height', Math.round(newH))
            // 字体大小随高度自动调整
            this.$set(this.attrStyles[this.resizeKey], 'fontSize', Math.max(6, Math.round(newH * 0.6)))
          }
        }
      },
      onPreviewMouseUp() {
        this.dragKey = null
        this.resizeKey = null
      },
      // 双击编辑（仅自定义标签）
      onItemDoubleClick(e, attr) {
        if (!attr.isCustom) return
        this.editingKey = attr.key
        this.currentAttrKey = attr.key
        var self = this
        this.$nextTick(function() {
          var input = self.$refs.labelPreview.querySelector('.label-edit-input')
          if (input) {
            input.focus()
            input.select()
          }
        })
      },
      handleReset() {
        var self = this
        this.selectedAttrs.forEach(function(key, idx) {
          if (self.attrStyles[key]) {
            self.$set(self.attrStyles[key], 'x', 10)
            self.$set(self.attrStyles[key], 'y', 10 + idx * 30)
          }
        })
      },
      // 模板管理
      loadTemplates() {
        try {
          var json = localStorage.getItem('labelTemplates')
          this.templates = json ? JSON.parse(json) : []
        } catch (e) {
          this.templates = []
        }
      },
      loadCategoryDefaultTemplate() {
        var categoryId = this.record.categoryId
        if (!categoryId) return
        var self = this
        var tpl = this.templates.find(function(t) {
          return t.type === 'default' && String(t.categoryId) === String(categoryId)
        })
        if (tpl) {
          this.selectedTemplateId = tpl.id
          this.$nextTick(function() { self.onTemplateChange(tpl.id) })
        }
      },
      saveTemplate() {
        if (!this.newTemplateName) {
          this.$message.warning('请输入模板名称')
          return
        }
        if (this.templateType === 'default' && !this.templateCategoryId) {
          this.$message.warning('请选择品类')
          return
        }
        var self = this
        // 收集自定义标签
        var customLabels = []
        this.allAttrs.forEach(function(attr) {
          if (attr.isCustom) {
            customLabels.push(JSON.parse(JSON.stringify(attr)))
          }
        })
        // 清理样式和标签名
        var cleanStyles = {}
        var cleanLabels = {}
        this.selectedAttrs.forEach(function(key) {
          if (self.attrStyles[key]) {
            cleanStyles[key] = JSON.parse(JSON.stringify(self.attrStyles[key]))
          }
          if (self.attrLabels[key]) {
            cleanLabels[key] = self.attrLabels[key]
          }
        })
        var tpl = {
          id: 'tpl_' + Date.now(),
          name: this.newTemplateName,
          type: this.templateType,
          materialId: this.templateType === 'normal' ? (this.record.id || null) : null,
          categoryId: this.templateType === 'default' ? this.templateCategoryId : null,
          categoryName: this.templateType === 'default' ? this.getCategoryName(this.templateCategoryId) : null,
          selectedAttrs: JSON.parse(JSON.stringify(this.selectedAttrs)),
          attrStyles: cleanStyles,
          attrLabels: cleanLabels,
          customLabels: customLabels,
          labelWidth: this.labelWidth,
          labelHeight: this.labelHeight
        }
        // 默认模板：同品类只保留一个
        if (this.templateType === 'default' && this.templateCategoryId) {
          this.templates = this.templates.filter(function(t) {
            return !(t.type === 'default' && String(t.categoryId) === String(self.templateCategoryId))
          })
        }
        this.templates.push(tpl)
        localStorage.setItem('labelTemplates', JSON.stringify(this.templates))
        this.selectedTemplateId = tpl.id
        this.$message.success('模板已保存')
      },
      onTemplateChange(tplId) {
        var self = this
        var tpl = this.templates.find(function(t) { return t.id === tplId })
        if (!tpl) return
        // 重建基础属性
        this.allAttrs = this.buildAttrList()
        // 恢复自定义标签
        if (tpl.customLabels && tpl.customLabels.length > 0) {
          tpl.customLabels.forEach(function(custom) {
            self.allAttrs.push(JSON.parse(JSON.stringify(custom)))
          })
          this.customCounter = tpl.customLabels.length
        }
        // 应用选中属性和样式
        this.selectedAttrs = JSON.parse(JSON.stringify(tpl.selectedAttrs))
        this.attrStyles = {}
        this.attrLabels = {}
        tpl.selectedAttrs.forEach(function(key) {
          if (tpl.attrStyles && tpl.attrStyles[key]) {
            self.$set(self.attrStyles, key, JSON.parse(JSON.stringify(tpl.attrStyles[key])))
          } else {
            var idx = tpl.selectedAttrs.indexOf(key)
            self.$set(self.attrStyles, key, { fontSize: 14, x: 10, y: 10 + idx * 30, fontWeight: 'normal', width: 120, height: 24 })
          }
          if (tpl.attrLabels && tpl.attrLabels[key]) {
            self.$set(self.attrLabels, key, tpl.attrLabels[key])
          } else {
            var attr = self.allAttrs.find(function(a) { return a.key === key })
            self.$set(self.attrLabels, key, attr ? attr.label : '')
          }
        })
        if (tpl.labelWidth) this.labelWidth = tpl.labelWidth
        if (tpl.labelHeight) this.labelHeight = tpl.labelHeight
        this.currentAttrKey = null
      },
      deleteTemplate() {
        if (!this.selectedTemplateId) return
        var self = this
        this.templates = this.templates.filter(function(t) { return t.id !== self.selectedTemplateId })
        localStorage.setItem('labelTemplates', JSON.stringify(this.templates))
        this.selectedTemplateId = null
        this.$message.success('模板已删除')
      },
      // 打印
      handlePrint() {
        var self = this
        var printWin = window.open('', '_blank', 'width=500,height=600')
        if (!printWin) {
          this.$message.warning('请允许弹出窗口以打印标签')
          return
        }
        var itemsHtml = ''
        this.selectedAttrList.forEach(function(attr) {
          var style = self.attrStyles[attr.key]
          if (!style) return
          var labelText = self.attrLabels[attr.key] || ''
          var displayText = labelText ? labelText + ': ' + attr.value : attr.value
          itemsHtml += '<div style="position:absolute;' +
            'left:' + style.x + 'px;' +
            'top:' + style.y + 'px;' +
            'width:' + (style.width || 120) + 'px;' +
            'height:' + (style.height || 24) + 'px;' +
            'font-size:' + style.fontSize + 'px;' +
            'font-weight:' + style.fontWeight + ';' +
            'overflow:hidden;' +
            '">' + displayText + '</div>'
        })
        printWin.document.write(
          '<html><head><title>打印标签</title>' +
          '<style>' +
          '@page{margin:0}' +
          'body{margin:0;padding:0;font-family:Arial,"Microsoft YaHei",sans-serif}' +
          '.label{position:relative;width:' + this.labelWidth + 'px;height:' + this.labelHeight + 'px;overflow:hidden;page-break-after:always}' +
          '</style></head><body>' +
          '<div class="label">' + itemsHtml + '</div>' +
          '</body></html>'
        )
        printWin.document.close()
        printWin.focus()
        setTimeout(function() {
          printWin.print()
          printWin.close()
        }, 300)
      }
    }
  }
</script>
<style scoped>
  .label-edit-panel {
    background: #fafafa;
    border-radius: 4px;
    padding: 12px;
    max-height: 580px;
    overflow-y: auto;
  }
  .label-edit-section-title {
    font-weight: bold;
    margin-bottom: 8px;
    padding-bottom: 4px;
    border-bottom: 1px solid #e8e8e8;
  }
  .label-edit-empty {
    color: #999;
    text-align: center;
    padding: 20px 0;
  }
  .label-size-editor {
    background: #fff;
    border: 1px solid #e8e8e8;
    border-radius: 4px;
    padding: 8px;
    margin-bottom: 8px;
  }
  .attr-style-name {
    font-weight: bold;
    margin-bottom: 8px;
    font-size: 13px;
    color: #1890ff;
  }
  .style-label {
    font-size: 12px;
    color: #666;
    margin-right: 4px;
  }
  .label-preview {
    position: relative;
    border: 2px dashed #d9d9d9;
    background: #fff;
    margin: 0 auto;
    overflow: hidden;
    user-select: none;
  }
  .label-preview-empty {
    color: #ccc;
    text-align: center;
    line-height: 200px;
  }
  .label-preview-item {
    position: absolute;
    cursor: move;
    line-height: 1.2;
    padding: 1px 2px;
    overflow: hidden;
    white-space: nowrap;
    display: flex;
    align-items: center;
  }
  .label-preview-item:hover {
    outline: 1px dashed #1890ff;
  }
  .label-preview-item.active {
    border: 2px solid #1890ff;
    background: rgba(24, 144, 255, 0.05);
  }
  .label-edit-input {
    border: none;
    background: transparent;
    width: 100%;
    outline: none;
    font-size: inherit;
    font-family: inherit;
    padding: 0;
  }
  .resize-handle {
    position: absolute;
    right: -5px;
    bottom: -5px;
    width: 10px;
    height: 10px;
    background: #1890ff;
    cursor: nwse-resize;
    border-radius: 50%;
    border: 1px solid #fff;
    z-index: 20;
  }
  /* 模板分组卡片 */
  .template-group {
    margin-bottom: 10px;
  }
  .template-group-title {
    font-size: 12px;
    color: #666;
    margin-bottom: 6px;
    font-weight: bold;
  }
  .template-empty {
    font-size: 12px;
    color: #ccc;
    padding: 4px 0;
  }
  .template-cards {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
  .template-card {
    padding: 4px 10px;
    border: 1px solid #d9d9d9;
    border-radius: 4px;
    cursor: pointer;
    font-size: 12px;
    background: #fff;
    transition: all 0.2s;
  }
  .template-card:hover {
    border-color: #1890ff;
    color: #1890ff;
  }
  .template-card.active {
    border-color: #1890ff;
    background: #1890ff;
    color: #fff;
  }
  .template-cat-name {
    font-size: 11px;
    opacity: 0.8;
  }
</style>
