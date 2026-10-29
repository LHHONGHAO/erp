<template>
  <div ref="container">
    <a-modal
      title="标签编辑"
      :width="1440"
      :visible="visible"
      :getContainer="() => $refs.container"
      :maskClosable="false"
      :footer="null"
      :bodyStyle="{ height: 'calc(96vh - 118px)', overflow: 'hidden' }"
      @cancel="handleCancel"
      style="top:2%;height:96%;max-width:98vw;">
      <a-row type="flex" :gutter="12" class="le-layout">
        <!-- 左栏：属性勾选 + 自定义标签 + 模板管理（固定宽度） -->
        <a-col class="le-side" style="width:300px;flex:none">
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
          </div>
        </a-col>

        <!-- 中栏：标签预览（自适应宽度） -->
        <a-col class="le-center" style="flex:1;min-width:0">
          <div class="label-edit-panel le-panel-center">
            <div class="label-edit-section-title">
              标签预览（{{ labelWidthMm }}mm × {{ labelHeightMm }}mm）
              <span style="font-weight:normal;font-size:12px;color:#999;margin-left:8px">点击属性编辑样式，拖拽移动位置，拖拽右下角调节尺寸，双击自定义标签编辑内容</span>
            </div>
            <!-- 缩放工具栏 -->
            <div class="label-zoom-bar">
              <a-button-group size="small">
                <a-button icon="align-left" title="左对齐" :disabled="selectedKeys.length < 2" @click="alignSelected('left')" />
                <a-button icon="align-center" title="水平居中" :disabled="selectedKeys.length < 2" @click="alignSelected('center')" />
                <a-button icon="align-right" title="右对齐" :disabled="selectedKeys.length < 2" @click="alignSelected('right')" />
              </a-button-group>
              <a-button-group size="small" style="margin-left:6px">
                <a-button icon="vertical-align-top" title="顶部对齐" :disabled="selectedKeys.length < 2" @click="alignSelected('top')" />
                <a-button icon="vertical-align-middle" title="垂直居中" :disabled="selectedKeys.length < 2" @click="alignSelected('middle')" />
                <a-button icon="vertical-align-bottom" title="底部对齐" :disabled="selectedKeys.length < 2" @click="alignSelected('bottom')" />
              </a-button-group>
              <a-button size="small" style="margin-left:6px" :disabled="selectedKeys.length < 3" @click="distributeSelected('h')">左右均分</a-button>
              <a-button size="small" style="margin-left:6px" :disabled="selectedKeys.length < 3" @click="distributeSelected('v')">上下均分</a-button>
              <span class="label-zoom-divider"></span>
              <a-button size="small" icon="zoom-out" title="缩小" @click="changeZoom(-0.1)"></a-button>
              <span class="label-zoom-value">{{ Math.round(zoom * 100) }}%</span>
              <a-button size="small" icon="zoom-in" title="放大" @click="changeZoom(0.1)"></a-button>
              <a-button size="small" @click="resetZoom">100%</a-button>
              <a-button size="small" @click="zoomToFit">适应窗口</a-button>
              <span class="label-zoom-divider"></span>
              <a-button size="small" @click="saveAsTemplate">另存为</a-button>
              <a-button size="small" type="primary" @click="saveTemplate">保存</a-button>
            </div>
            <div class="label-preview-scroll" ref="previewScroll" @mousedown.self="clearAttrSelection">
              <div
                class="label-preview-wrap"
                :style="{ width: (labelWidth * zoom) + 'px', height: (labelHeight * zoom) + 'px' }"
                @mousedown.self="clearAttrSelection"
              >
                <!-- 刻度尺：横向 -->
                <div class="label-ruler-h" :style="{ width: (labelWidth * zoom) + 'px' }">
                  <span
                    v-for="t in rulerHTicks"
                    :key="'rh' + t.mm"
                    class="ruler-tick"
                    :class="{ major: t.major }"
                    :style="{ left: t.pos + 'px' }"
                  ><em v-if="t.major">{{ t.mm }}</em></span>
                </div>
                <!-- 刻度尺：纵向 -->
                <div class="label-ruler-v" :style="{ height: (labelHeight * zoom) + 'px' }">
                  <span
                    v-for="t in rulerVTicks"
                    :key="'rv' + t.mm"
                    class="ruler-tick-v"
                    :class="{ major: t.major }"
                    :style="{ top: t.pos + 'px' }"
                  ><em v-if="t.major">{{ t.mm }}</em></span>
                </div>
                <div
                  class="label-preview"
                  :style="{ width: labelWidth + 'px', height: labelHeight + 'px', transform: 'scale(' + zoom + ')' }"
                  ref="labelPreview"
                  @mousedown.self="onMarqueeStart"
                >
                  <!-- 框选矩形 -->
                  <div
                    v-if="marquee.active"
                    class="label-marquee"
                    :style="{ left: marquee.left + 'px', top: marquee.top + 'px', width: marquee.width + 'px', height: marquee.height + 'px' }"
                  ></div>
                  <div v-if="selectedAttrList.length === 0" class="label-preview-empty">请先勾选属性</div>
                  <div
                    v-for="attr in selectedAttrList"
                    :key="attr.key"
                    :data-key="attr.key"
                    class="label-preview-item"
                    :class="{ active: selectedKeys.indexOf(attr.key) > -1, 'is-multiline': isMultilineValue(attr.value), 'is-code': isCodeItem(attr.key) }"
                    :style="getItemStyle(attr.key)"
                    @mousedown="onItemMouseDown($event, attr.key)"
                    @dblclick="onItemDoubleClick($event, attr)"
                  >
                    <template v-if="!isCodeItem(attr.key)">
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
                    </template>
                    <!-- 条码/二维码：文字（属性名+属性值）照常显示，下方生成图形 -->
                    <template v-else>
                      <div class="label-code-box">
                        <div class="label-code-text" :style="codeTextStyle(attr)">{{ codeText(attr) }}</div>
                        <img v-if="codeImages[attr.key]" :src="codeImages[attr.key]" class="label-code-img" :style="codeImgStyle(attr)" />
                        <span v-else class="label-code-fallback">{{ attr.value }}</span>
                      </div>
                    </template>
                    <!-- resize 手柄 -->
                    <div
                      v-if="selectedKeys.length === 1 && currentAttrKey === attr.key && editingKey !== attr.key"
                      class="resize-handle"
                      @mousedown.stop.prevent="onResizeMouseDown($event, attr.key)"
                    ></div>
                  </div>
                  <!-- 拖拽对齐参考线 -->
                  <div v-for="(gx, gi) in guideLines.v" :key="'gv' + gi" class="snap-guide snap-guide-v" :style="{ left: gx + 'px' }"></div>
                  <div v-for="(gy, gj) in guideLines.h" :key="'gh' + gj" class="snap-guide snap-guide-h" :style="{ top: gy + 'px' }"></div>
                </div>
              </div>
            </div>
            <div style="margin-top:12px">
              <a-button type="primary" @click="handlePrint" :disabled="selectedAttrList.length === 0">打印</a-button>
              <a-button @click="handleReset" style="margin-left:8px">重置位置</a-button>
            </div>
          </div>
        </a-col>

        <!-- 右栏：标签尺寸 + 样式编辑（固定宽度） -->
        <a-col class="le-side" style="width:340px;flex:none">
          <div class="label-edit-panel">
            <!-- 标签宽高（毫米）：固定最顶端 -->
            <div class="label-size-editor" style="margin-bottom:10px">
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
            <!-- 标签文字：只读展示当前标签里的全部文字 -->
            <div class="label-text-box">
              <div class="label-edit-section-title">标签文字</div>
              <a-textarea
                :value="labelTextContent"
                :rows="5"
                readonly
                placeholder="勾选属性后，这里显示标签中的全部文字"
              />
            </div>
            <!-- 当前选中属性（支持 Shift 多选）的样式编辑 -->
            <div class="label-edit-section-title" style="margin-top:12px">
              样式编辑
              <span v-if="selectedKeys.length > 1" style="font-weight:normal;font-size:12px;color:#1890ff;margin-left:6px">已选 {{ selectedKeys.length }} 项</span>
            </div>
            <div v-if="!selectedKeys.length" class="label-edit-empty">请点击预览中的属性（Shift + 点击可多选）</div>
            <div v-else>
              <!-- 属性名编辑：可输入可快速选择，留空则不显示属性名 -->
              <div style="margin-bottom:8px">
                <span class="style-label">属性名编辑</span>
                <a-auto-complete
                  v-model="currentLabelEdit"
                  :dataSource="presetLabelNames"
                  placeholder="选择或输入属性名（留空则不显示属性名）"
                  style="width:100%"
                />
              </div>
              <!-- 条码 / 二维码：勾选后按该属性的值生成图形 -->
              <div style="margin-bottom:8px">
                <span class="style-label">编码</span>
                <a-checkbox :checked="currentCodeType === 'barcode'" @change="e => setCodeType('barcode', e.target.checked)">条码</a-checkbox>
                <a-checkbox :checked="currentCodeType === 'qrcode'" @change="e => setCodeType('qrcode', e.target.checked)">二维码</a-checkbox>
              </div>

              <!-- 字体属性：单列排列，数值项带数字调节按钮；多选时不一致的项显示为空 -->
              <div class="style-field">
                <span class="style-label">字号</span>
                <a-input-number :value="getMultiStyle('fontSize')" @change="v => setMultiStyle('fontSize', v)" :min="6" :max="72" size="small" style="width:100%" placeholder="多个值" />
              </div>
              <div class="style-field">
                <span class="style-label">粗细</span>
                <a-select :value="getMultiStyle('fontWeight')" @change="v => setMultiStyle('fontWeight', v)" size="small" style="width:100%" placeholder="多个值">
                  <a-select-option value="normal">正常</a-select-option>
                  <a-select-option value="bold">粗体</a-select-option>
                </a-select>
              </div>
              <div class="style-field">
                <span class="style-label">字间距(px)</span>
                <a-input-number :value="getMultiStyle('letterSpacing')" @change="v => setMultiStyle('letterSpacing', v)" :min="0" :max="30" :step="0.5" size="small" style="width:100%" placeholder="多个值" />
              </div>
              <div class="style-field">
                <span class="style-label">行距(倍)</span>
                <a-input-number :value="getMultiStyle('lineHeight')" @change="v => setMultiStyle('lineHeight', v)" :min="0.8" :max="4" :step="0.1" size="small" style="width:100%" placeholder="多个值" />
              </div>
              <div class="style-field">
                <span class="style-label">X</span>
                <a-input-number :value="getMultiStyle('x')" @change="v => setMultiStyle('x', v)" :min="-999" :max="9999" size="small" style="width:100%" placeholder="多个值" />
              </div>
              <div class="style-field">
                <span class="style-label">Y</span>
                <a-input-number :value="getMultiStyle('y')" @change="v => setMultiStyle('y', v)" :min="-999" :max="9999" size="small" style="width:100%" placeholder="多个值" />
              </div>
              <div class="style-field">
                <span class="style-label">宽</span>
                <a-input-number :value="getMultiStyle('width')" @change="v => setMultiStyle('width', v)" :min="10" :max="2000" size="small" style="width:100%" placeholder="自动" />
              </div>
              <div class="style-field">
                <span class="style-label">高</span>
                <a-input-number :value="getMultiStyle('height')" @change="v => setMultiStyle('height', v)" :min="10" :max="1000" size="small" style="width:100%" placeholder="自动" />
              </div>
            </div>
          </div>
        </a-col>
      </a-row>
    </a-modal>

    <!-- 打印标签页面（打印按钮打开） -->
    <label-print-modal ref="labelPrintModal"></label-print-modal>

    <!-- 模板保存弹层：新建模板时弹出 -->
    <a-modal
      title="保存模板"
      :width="380"
      :visible="saveModalVisible"
      :zIndex="3000"
      okText="保存"
      cancelText="取消"
      @ok="confirmSaveTemplate"
      @cancel="saveModalVisible = false"
    >
      <div style="margin-bottom:12px">
        <div class="label-edit-section-title" style="margin-bottom:6px">模板名称</div>
        <a-input v-model="newTemplateName" placeholder="请输入模板名称" allowClear @pressEnter="confirmSaveTemplate" />
      </div>
      <div>
        <div class="label-edit-section-title" style="margin-bottom:6px">商品类型</div>
        <a-select
          v-model="templateCategoryId"
          style="width:100%"
          placeholder="不选择则保存为普通模板"
          allowClear
          showSearch
          :filterOption="filterCategory"
        >
          <a-select-option v-for="cat in categoryList" :key="cat.value" :value="cat.value">{{ cat.label }}</a-select-option>
        </a-select>
        <div style="margin-top:6px;font-size:12px;color:#999">不选类型保存为普通模板（仅当前商品可用）；选择类型保存为该类型的通用模板</div>
      </div>
    </a-modal>
  </div>
</template>

<script>
  // 商品列定义：标签可选属性直接取自商品信息表格的列，保证两处字段一致
  import { buildLabelAttrs } from '@/views/material/utils/materialDisplay'
  import { fitCodeSize, renderCodeDataUrl } from '@/views/material/utils/labelCode'
  import { fetchLabelTemplates, saveLabelTemplates } from '@/views/material/utils/labelTemplateStore'
  import LabelPrintModal from './LabelPrintModal'

  // 像素与毫米转换常量（96dpi: 1mm ≈ 3.779528px）
  var PX_PER_MM = 3.779528

  export default {
    name: "LabelEditModal",
    components: { LabelPrintModal },
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
        // 多选：Shift + 点击可同时选中多个文本标签
        selectedKeys: [],
        // 条码/二维码图片缓存（key → dataURL）
        codeImages: {},
        editingKey: null,
        templates: [],
        selectedTemplateId: null,
        newTemplateName: '',
        templateCategoryId: null,
        // 模板保存弹层
        saveModalVisible: false,
        // 关闭确认保存后是否继续关闭编辑器
        closeAfterSave: false,
        // 干净状态快照：用于判断关闭时是否有未保存修改
        cleanSnapshot: '',
        labelWidth: 320,
        labelHeight: 240,
        // 预览缩放比例与对齐参考线
        zoom: 1,
        guideLines: { v: [], h: [] },
        // 拖拽移动（dragOrigins 记录各选中项的拖动起点，多选时整体移动）
        dragKey: null,
        dragStartX: 0,
        dragStartY: 0,
        dragOrigins: {},
        // 左键拖动框选
        marquee: { active: false, left: 0, top: 0, width: 0, height: 0 },
        marqueeStartX: 0,
        marqueeStartY: 0,
        // 拖拽resize
        resizeKey: null,
        resizeStartX: 0,
        resizeStartY: 0,
        resizeOrigW: 0,
        resizeOrigH: 0,
        resizeOrigFont: 14,
        // 属性名快速选择
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
      // 选中项的编码类型（条码/二维码），多选时不一致返回空
      currentCodeType() {
        return this.getMultiStyle('codeType') || ''
      },
      // 编码内容签名：属性值、编码类型、尺寸变化时重新生成图片
      codeSignature() {
        var self = this
        return this.selectedAttrList.map(function(attr) {
          var s = self.attrStyles[attr.key] || {}
          return [attr.key, s.codeType || '', attr.value == null ? '' : attr.value, s.width || '', s.height || ''].join('|')
        }).join(';')
      },
      // 标签中所有文字，按预览顺序逐行输出「属性名: 值」，属性名留空时只输出值
      labelTextContent() {
        var self = this
        return this.selectedAttrList.map(function(attr) {
          var label = Object.prototype.hasOwnProperty.call(self.attrLabels, attr.key)
            ? self.attrLabels[attr.key]
            : (attr.label || '')
          var value = attr.value == null ? '' : String(attr.value)
          return label ? (label + ': ' + value) : value
        }).join('\n')
      },
      // 当前选中的属性
      getCurrentAttr() {
        return this.allAttrs.find(a => a.key === this.currentAttrKey)
      },
      getCurrentAttrLabel() {
        var attr = this.getCurrentAttr
        return attr ? attr.label : ''
      },
      // 属性名编辑：显示当前属性名，可直接输入修改，留空表示不显示属性名
      // 多选时：属性名一致显示该名称，不一致显示为空
      currentLabelEdit: {
        get() {
          var self = this
          if (!this.selectedKeys.length) return ''
          var first = null
          for (var i = 0; i < this.selectedKeys.length; i++) {
            var key = this.selectedKeys[i]
            var val = Object.prototype.hasOwnProperty.call(this.attrLabels, key)
              ? this.attrLabels[key]
              : self.getAttrDefaultLabel(key)
            if (i === 0) {
              first = val
            } else if (val !== first) {
              return ''
            }
          }
          return first == null ? '' : first
        },
        set(val) {
          var self = this
          this.selectedKeys.forEach(function(key) {
            self.$set(self.attrLabels, key, val == null ? '' : val)
          })
        }
      },
      // 预览刻度尺
      rulerHTicks() {
        return this.buildRulerTicks(this.labelWidthMm)
      },
      rulerVTicks() {
        return this.buildRulerTicks(this.labelHeightMm)
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
    watch: {
      // 属性内容、编码类型或尺寸变化时重新生成条码/二维码图片
      codeSignature() {
        this.refreshCodeImages()
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
        this.selectedKeys = []
        this.editingKey = null
        this.selectedTemplateId = null
        this.newTemplateName = ''
        this.templateCategoryId = null
        this.saveModalVisible = false
        this.closeAfterSave = false
        this.customCounter = 0
        this.labelWidth = 320
        this.labelHeight = 240
        this.zoom = 1
        this.guideLines = { v: [], h: [] }
        this.visible = true
        this.loadCategoryDefaultTemplate()
        var self = this
        this.$nextTick(function() { self.markClean() })
      },
      // 记录当前编辑状态快照（保存/打开模板后调用），用于关闭时判断是否有改动
      snapshotState() {
        return JSON.stringify({
          attrs: this.selectedAttrs,
          styles: this.attrStyles,
          labels: this.attrLabels,
          w: this.labelWidth,
          h: this.labelHeight
        })
      },
      markClean() {
        this.cleanSnapshot = this.snapshotState()
      },
      isDirty() {
        return this.snapshotState() !== this.cleanSnapshot
      },
      handleCancel() {
        var self = this
        // 模板被修改过或存在未保存内容时，提示是否保存
        if (this.isDirty()) {
          this.$confirm({
            title: '模板内容已修改',
            content: '关闭前是否保存当前标签模板？',
            okText: '保存',
            cancelText: '不保存',
            zIndex: 3100,
            onOk: function() {
              self.closeAfterSave = true
              self.saveTemplate()
            },
            onCancel: function() {
              self.visible = false
            }
          })
          return
        }
        this.visible = false
      },
      buildAttrList() {
        // 与打印标签预览页共用同一套属性来源（商品信息表格全部列 + 旧模板兼容键）
        return buildLabelAttrs(this.record, this.sku)
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
        var style = {
          left: s.x + 'px',
          top: s.y + 'px',
          fontSize: s.fontSize + 'px',
          fontWeight: s.fontWeight,
          letterSpacing: (s.letterSpacing || 0) + 'px',
          lineHeight: String(s.lineHeight || 1.3)
        }
        // 未手动指定宽高时边框随文字自适应，字号/行距/字距变化时自动跟随
        if (s.width) style.width = s.width + 'px'
        if (s.height) style.height = s.height + 'px'
        // 条码/二维码：宽度用设定值或默认 80/160，总高 = 文字高度 + 图形高度
        if (s.codeType) {
          var codeAttr = this.selectedAttrList.find(function(a) { return a.key === key })
          if (codeAttr) {
            var layout = this.codeLayout(codeAttr)
            style.width = layout.boxWidth + 'px'
            style.height = Math.round(layout.textHeight + layout.imgHeight) + 'px'
          }
        }
        return style
      },
      // 该属性是否渲染为条码/二维码
      isCodeItem(key) {
        var s = this.attrStyles[key]
        return !!(s && s.codeType)
      },
      // 条码/二维码上方的文字：属性名 + 属性值（属性名留空时只显示值）
      codeText(attr) {
        var label = Object.prototype.hasOwnProperty.call(this.attrLabels, attr.key)
          ? this.attrLabels[attr.key]
          : (attr.label || '')
        var value = attr.value == null ? '' : String(attr.value)
        return label ? (label + ': ' + value) : value
      },
      // 编码元素布局：文字高度、盒宽、图形宽高
      codeLayout(attr) {
        var s = this.attrStyles[attr.key] || {}
        var isQr = s.codeType === 'qrcode'
        var text = this.codeText(attr)
        var lines = text ? String(text).split('\n').length : 0
        var fontSize = s.fontSize || 14
        var lineHeight = s.lineHeight || 1.3
        // 文字框固定为文本实际长高 + 2px
        var textBoxHeight = lines ? Math.round(lines * fontSize) + 2 : 0
        // 文字与图形之间的间距取原行距留白的三分之一，避免文字和条码之间出现大空隙
        var textGap = lines ? Math.round((lines * fontSize * (lineHeight - 1)) / 3) : 0
        var textHeight = textBoxHeight + textGap
        var boxWidth = s.width || (isQr ? 80 : 160)
        var imgHeight = s.height ? Math.max(20, s.height - textHeight) : (isQr ? 80 : 50)
        // 按打印机硬件能力换算实际可打印尺寸，预览与打印保持一致
        var fitted = fitCodeSize(s.codeType, attr.value, boxWidth, imgHeight)
        return {
          text: text,
          textHeight: textHeight,
          textBoxHeight: textBoxHeight,
          boxWidth: boxWidth,
          imgWidth: fitted.width,
          imgHeight: fitted.height
        }
      },
      // 编码元素上方的文字框：严格贴合文字高度
      codeTextStyle(attr) {
        var layout = this.codeLayout(attr)
        return { height: layout.textBoxHeight + 'px', lineHeight: '1' }
      },
      // 编码图片尺寸
      codeImgStyle(attr) {
        var layout = this.codeLayout(attr)
        return {
          width: layout.imgWidth + 'px',
          height: Math.round(layout.imgHeight) + 'px'
        }
      },
      onSelectedChange() {
        var self = this
        this.selectedAttrs.forEach(function(key) {
          if (!self.attrStyles[key]) {
            var idx = self.selectedAttrs.indexOf(key)
            // 不预设宽高，默认由文字内容撑开，字号/行距/字距调整时边框自动跟随
            self.$set(self.attrStyles, key, {
              fontSize: 14, x: 10, y: 10 + idx * 30, fontWeight: 'normal',
              letterSpacing: 0, lineHeight: 1.3
            })
          }
          if (!Object.prototype.hasOwnProperty.call(self.attrLabels, key)) {
            var attrItem = self.allAttrs.find(function(a) { return a.key === key })
            self.$set(self.attrLabels, key, attrItem ? attrItem.label : '')
          }
        })
        if (this.currentAttrKey && this.selectedAttrs.indexOf(this.currentAttrKey) === -1) {
          this.currentAttrKey = null
          this.selectedKeys = []
        }
        // 已取消勾选的属性同时移出多选集合
        var selfKeys = this
        this.selectedKeys = this.selectedKeys.filter(function(k) {
          return selfKeys.selectedAttrs.indexOf(k) > -1
        })
      },
      // 值是否为多行（规格：子规格名：子规格值 逐行）
      isMultilineValue(value) {
        return value != null && String(value).indexOf('\n') > -1
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
          letterSpacing: 0, lineHeight: 1.3
        })
        this.$set(this.attrLabels, key, '')
      },
      removeCustomLabel(key) {
        var idx = this.allAttrs.findIndex(function(a) { return a.key === key })
        if (idx > -1) this.allAttrs.splice(idx, 1)
        var sIdx = this.selectedAttrs.indexOf(key)
        if (sIdx > -1) this.selectedAttrs.splice(sIdx, 1)
        if (this.currentAttrKey === key) this.currentAttrKey = null
        var kIdx = this.selectedKeys.indexOf(key)
        if (kIdx > -1) this.selectedKeys.splice(kIdx, 1)
        delete this.attrStyles[key]
        delete this.attrLabels[key]
      },
      updateCustomValue(key, value) {
        var attr = this.allAttrs.find(function(a) { return a.key === key })
        if (attr) attr.value = value
      },
      // 点击预览空白处取消选中
      clearAttrSelection() {
        this.currentAttrKey = null
        this.selectedKeys = []
      },
      // 左键拖动框选：在标签画布空白处按下并拖拽，选中与选框相交的文本标签
      onMarqueeStart(e) {
        if (e.button !== 0) return
        e.preventDefault()
        var local = this.getLabelLocalPoint(e)
        this.marqueeStartX = local.x
        this.marqueeStartY = local.y
        this.marquee = { active: true, left: local.x, top: local.y, width: 0, height: 0 }
        this.currentAttrKey = null
        this.selectedKeys = []
        document.addEventListener('mousemove', this.onMarqueeMove)
        document.addEventListener('mouseup', this.onMarqueeEnd)
      },
      onMarqueeMove(e) {
        if (!this.marquee.active) return
        var local = this.getLabelLocalPoint(e)
        var left = Math.min(this.marqueeStartX, local.x)
        var top = Math.min(this.marqueeStartY, local.y)
        var width = Math.abs(local.x - this.marqueeStartX)
        var height = Math.abs(local.y - this.marqueeStartY)
        this.marquee = { active: true, left: left, top: top, width: width, height: height }
        var self = this
        this.selectedKeys = this.selectedAttrList.filter(function(attr) {
          var s = self.attrStyles[attr.key]
          if (!s) return false
          var size = self.getItemSize(attr.key)
          if (!size.w && !size.h) return false
          return s.x < left + width && s.x + size.w > left && s.y < top + height && s.y + size.h > top
        }).map(function(attr) { return attr.key })
      },
      onMarqueeEnd() {
        var keys = this.selectedKeys
        this.marquee = { active: false, left: 0, top: 0, width: 0, height: 0 }
        this.currentAttrKey = keys.length === 1 ? keys[0] : null
        document.removeEventListener('mousemove', this.onMarqueeMove)
        document.removeEventListener('mouseup', this.onMarqueeEnd)
      },
      unbindMarqueeEvents() {
        document.removeEventListener('mousemove', this.onMarqueeMove)
        document.removeEventListener('mouseup', this.onMarqueeEnd)
      },
      // 鼠标坐标换算为标签画布内坐标（缩放已取消）
      getLabelLocalPoint(e) {
        var el = this.$refs.labelPreview
        if (!el) return { x: 0, y: 0 }
        var rect = el.getBoundingClientRect()
        return {
          x: (e.clientX - rect.left) / this.zoom,
          y: (e.clientY - rect.top) / this.zoom
        }
      },
      // 切换编码类型：'barcode' 条码 / 'qrcode' 二维码 / '' 文本（两种编码互斥）
      setCodeType(type, checked) {
        var self = this
        this.selectedKeys.forEach(function(key) {
          var s = self.attrStyles[key]
          if (!s) return
          if (checked) {
            self.$set(s, 'codeType', type)
          } else if (s.codeType === type) {
            self.$delete(s, 'codeType')
          }
        })
      },
      // 为所有设置为条码/二维码的属性生成图片
      refreshCodeImages() {
        var self = this
        var alive = {}
        this.selectedAttrList.forEach(function(attr) {
          var s = self.attrStyles[attr.key]
          if (!s || !s.codeType) return
          alive[attr.key] = true
          var layout = self.codeLayout(attr)
          renderCodeDataUrl(s.codeType, attr.value, layout.imgWidth, layout.imgHeight).then(function(url) {
            self.$set(self.codeImages, attr.key, url)
          })
        })
        Object.keys(this.codeImages).forEach(function(key) {
          if (!alive[key]) self.$delete(self.codeImages, key)
        })
      },
      // 属性默认标题（未自定义属性名时使用）
      getAttrDefaultLabel(key) {
        var attr = this.allAttrs.find(function(a) { return a.key === key })
        return attr ? attr.label : ''
      },
      // 多选样式读取：一致返回该值，不一致返回空（输入框显示为空）
      getMultiStyle(field) {
        if (!this.selectedKeys.length) return undefined
        var first = this.attrStyles[this.selectedKeys[0]]
        if (!first) return undefined
        var val = first[field]
        for (var i = 1; i < this.selectedKeys.length; i++) {
          var s = this.attrStyles[this.selectedKeys[i]]
          if (!s || s[field] !== val) return undefined
        }
        return val
      },
      // 多选样式写入：统一应用到所有选中项
      setMultiStyle(field, val) {
        var self = this
        if (val == null && field !== 'width' && field !== 'height') return
        this.selectedKeys.forEach(function(key) {
          var s = self.attrStyles[key]
          if (!s) return
          if (val == null) {
            self.$delete(s, field)
          } else {
            self.$set(s, field, val)
          }
        })
      },
      // 读取预览元素实际渲染尺寸（不受缩放影响）
      getItemSize(key) {
        var el = document.querySelector('.label-preview-item[data-key="' + key + '"]')
        return { w: el ? el.offsetWidth : 0, h: el ? el.offsetHeight : 0 }
      },
      // 对齐：以选中对象的外接矩形为基准 left/center/right/top/middle/bottom
      alignSelected(mode) {
        var self = this
        var keys = this.selectedKeys
        if (keys.length < 2) return
        var rects = keys.map(function(key) {
          var s = self.attrStyles[key] || {}
          var size = self.getItemSize(key)
          return { key: key, x: s.x || 0, y: s.y || 0, w: size.w, h: size.h }
        })
        // 选中对象整体的左/右/上/下边界
        var minX = Math.min.apply(null, rects.map(function(r) { return r.x }))
        var maxX = Math.max.apply(null, rects.map(function(r) { return r.x + r.w }))
        var minY = Math.min.apply(null, rects.map(function(r) { return r.y }))
        var maxY = Math.max.apply(null, rects.map(function(r) { return r.y + r.h }))
        rects.forEach(function(r) {
          var s = self.attrStyles[r.key]
          if (!s) return
          if (mode === 'left') self.$set(s, 'x', Math.round(minX))
          if (mode === 'right') self.$set(s, 'x', Math.round(maxX - r.w))
          if (mode === 'center') self.$set(s, 'x', Math.round((minX + maxX - r.w) / 2))
          if (mode === 'top') self.$set(s, 'y', Math.round(minY))
          if (mode === 'bottom') self.$set(s, 'y', Math.round(maxY - r.h))
          if (mode === 'middle') self.$set(s, 'y', Math.round((minY + maxY - r.h) / 2))
        })
      },
      // 均分：h 左右均分，v 上下均分（首尾保持不动，中间等间距）
      distributeSelected(dir) {
        var self = this
        var keys = this.selectedKeys
        if (keys.length < 3) return
        var rects = keys.map(function(key) {
          var s = self.attrStyles[key] || {}
          var size = self.getItemSize(key)
          return { key: key, x: s.x || 0, y: s.y || 0, w: size.w, h: size.h }
        })
        if (dir === 'h') {
          rects.sort(function(a, b) { return a.x - b.x })
          var totalW = rects.reduce(function(sum, r) { return sum + r.w }, 0)
          var firstH = rects[0]
          var lastH = rects[rects.length - 1]
          var gapH = ((lastH.x + lastH.w) - firstH.x - totalW) / (rects.length - 1)
          var cursorH = firstH.x
          rects.forEach(function(r) {
            self.$set(self.attrStyles[r.key], 'x', Math.round(cursorH))
            cursorH += r.w + gapH
          })
        } else {
          rects.sort(function(a, b) { return a.y - b.y })
          var totalH = rects.reduce(function(sum, r) { return sum + r.h }, 0)
          var firstV = rects[0]
          var lastV = rects[rects.length - 1]
          var gapV = ((lastV.y + lastV.h) - firstV.y - totalH) / (rects.length - 1)
          var cursorV = firstV.y
          rects.forEach(function(r) {
            self.$set(self.attrStyles[r.key], 'y', Math.round(cursorV))
            cursorV += r.h + gapV
          })
        }
      },
      // 生成刻度尺刻度：每 2mm 一格，每 10mm 标注
      buildRulerTicks(totalMm) {
        var ticks = []
        var perMm = this.zoom * PX_PER_MM
        for (var mm = 0; mm <= totalMm; mm += 2) {
          ticks.push({ mm: mm, major: mm % 10 === 0, pos: Math.round(mm * perMm) })
        }
        return ticks
      },
      // 缩放
      changeZoom(delta) {
        var next = Math.round((this.zoom + delta) * 10) / 10
        this.zoom = Math.min(3, Math.max(0.3, next))
      },
      resetZoom() {
        this.zoom = 1
      },
      zoomToFit() {
        var el = this.$refs.previewScroll
        if (!el) return
        var sw = el.clientWidth - 24
        var sh = el.clientHeight - 24
        if (sw <= 0 || sh <= 0) return
        var fit = Math.min(sw / this.labelWidth, sh / this.labelHeight)
        this.zoom = Math.max(0.3, Math.min(3, Math.round(fit * 10) / 10))
      },
      // 拖拽移动（Shift + 点击为多选）
      onItemMouseDown(e, key) {
        if (this.editingKey === key) return
        if (e.shiftKey) {
          e.preventDefault()
          e.stopPropagation()
          var idx = this.selectedKeys.indexOf(key)
          if (idx > -1) {
            this.selectedKeys.splice(idx, 1)
          } else {
            this.selectedKeys.push(key)
          }
          this.currentAttrKey = this.selectedKeys.length ? this.selectedKeys[this.selectedKeys.length - 1] : null
          return
        }
        e.preventDefault()
        e.stopPropagation()
        // 点在已选中的多选项上时保留多选，实现整体拖动
        if (this.selectedKeys.indexOf(key) === -1) {
          this.selectedKeys = [key]
        }
        this.dragKey = key
        this.dragStartX = e.clientX
        this.dragStartY = e.clientY
        this.dragOrigins = {}
        var self = this
        this.selectedKeys.forEach(function(k) {
          var s = self.attrStyles[k]
          if (s) self.dragOrigins[k] = { x: s.x, y: s.y }
        })
        this.currentAttrKey = key
        this.bindDragEvents()
      },
      // 拖拽resize
      onResizeMouseDown(e, key) {
        this.resizeKey = key
        this.resizeStartX = e.clientX
        this.resizeStartY = e.clientY
        // 未指定固定宽高时，以当前实际渲染尺寸作为拖拽起点
        var el = document.querySelector('.label-preview-item[data-key="' + key + '"]')
        this.resizeOrigW = (this.attrStyles[key] && this.attrStyles[key].width) || (el ? el.offsetWidth : 120)
        this.resizeOrigH = (this.attrStyles[key] && this.attrStyles[key].height) || (el ? el.offsetHeight : 24)
        this.resizeOrigFont = (this.attrStyles[key] && this.attrStyles[key].fontSize) || 14
        this.bindDragEvents()
      },
      // 拖拽过程绑定到 document，缩放后移出预览区也能继续拖拽
      bindDragEvents() {
        document.addEventListener('mousemove', this.onPreviewMouseMove)
        document.addEventListener('mouseup', this.onPreviewMouseUp)
      },
      unbindDragEvents() {
        document.removeEventListener('mousemove', this.onPreviewMouseMove)
        document.removeEventListener('mouseup', this.onPreviewMouseUp)
      },
      onPreviewMouseMove(e) {
        // 拖拽移动（鼠标位移按缩放比例换算为标签坐标，多选时整体移动）
        if (this.dragKey) {
          var dx = (e.clientX - this.dragStartX) / this.zoom
          var dy = (e.clientY - this.dragStartY) / this.zoom
          var self2 = this
          var dragKeys = Object.keys(this.dragOrigins)
          if (dragKeys.length > 1) {
            dragKeys.forEach(function(k) {
              var st = self2.attrStyles[k]
              var og = self2.dragOrigins[k]
              if (!st || !og) return
              self2.$set(st, 'x', Math.round(Math.max(-self2.labelWidth, Math.min(self2.labelWidth * 2, og.x + dx))))
              self2.$set(st, 'y', Math.round(Math.max(-self2.labelHeight, Math.min(self2.labelHeight * 2, og.y + dy))))
            })
            this.guideLines = { v: [], h: [] }
          } else {
            var style = this.attrStyles[this.dragKey]
            var origin = this.dragOrigins[this.dragKey]
            if (style && origin) {
              var w = style.width || 120
              var h = style.height || 24
              var snap = this.computeSnap(origin.x + dx, origin.y + dy, w, h, this.dragKey)
              this.$set(style, 'x', Math.round(Math.max(-this.labelWidth, Math.min(this.labelWidth * 2, snap.x))))
              this.$set(style, 'y', Math.round(Math.max(-this.labelHeight, Math.min(this.labelHeight * 2, snap.y))))
              this.guideLines = snap.guides
            }
          }
        }
        // 拖拽resize
        if (this.resizeKey) {
          var rdx = (e.clientX - this.resizeStartX) / this.zoom
          var rdy = (e.clientY - this.resizeStartY) / this.zoom
          var newW = Math.max(30, this.resizeOrigW + rdx)
          var newH = Math.max(12, this.resizeOrigH + rdy)
          if (this.attrStyles[this.resizeKey]) {
            // 外框与字号等比缩放：字体随外框放大/缩小
            var ratio = newH / this.resizeOrigH
            this.$set(this.attrStyles[this.resizeKey], 'width', Math.round(newW))
            this.$set(this.attrStyles[this.resizeKey], 'height', Math.round(newH))
            this.$set(this.attrStyles[this.resizeKey], 'fontSize', Math.max(6, Math.round(this.resizeOrigFont * ratio)))
          }
        }
      },
      onPreviewMouseUp() {
        this.dragKey = null
        this.dragOrigins = {}
        this.resizeKey = null
        this.guideLines = { v: [], h: [] }
        this.unbindDragEvents()
      },
      /**
       * 对齐吸附：把当前属性的左/中/右、上/中/下与其他属性及标签边界比较，
       * 距离小于阈值（按缩放换算，屏幕上约 6px）时吸附，并返回参考线位置
       */
      computeSnap(rawX, rawY, width, height, selfKey) {
        var threshold = 6 / this.zoom
        var targetsX = [0, this.labelWidth / 2, this.labelWidth]
        var targetsY = [0, this.labelHeight / 2, this.labelHeight]
        var list = this.selectedAttrList
        for (var i = 0; i < list.length; i++) {
          if (list[i].key === selfKey) continue
          var s = this.attrStyles[list[i].key]
          if (!s) continue
          var ow = s.width || 120
          var oh = s.height || 24
          targetsX.push(s.x, s.x + ow / 2, s.x + ow)
          targetsY.push(s.y, s.y + oh / 2, s.y + oh)
        }
        var snapX = this.findSnap([rawX, rawX + width / 2, rawX + width], targetsX, threshold)
        var snapY = this.findSnap([rawY, rawY + height / 2, rawY + height], targetsY, threshold)
        return {
          x: rawX + (snapX ? snapX.delta : 0),
          y: rawY + (snapY ? snapY.delta : 0),
          guides: { v: snapX ? [snapX.line] : [], h: snapY ? [snapY.line] : [] }
        }
      },
      // 在候选锚点中找距离最近且在阈值内的吸附目标
      findSnap(mine, targets, threshold) {
        var best = null
        for (var i = 0; i < mine.length; i++) {
          for (var j = 0; j < targets.length; j++) {
            var delta = targets[j] - mine[i]
            if (Math.abs(delta) > threshold) continue
            if (!best || Math.abs(delta) < Math.abs(best.delta)) {
              best = { delta: delta, line: targets[j] }
            }
          }
        }
        return best
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
      // 模板管理：云端读取，本地仅作缓存
      loadTemplates() {
        var self = this
        fetchLabelTemplates().then(function(list) {
          self.templates = list
          // 弹层已打开但模板晚于打开到达时，补一次品类默认模板匹配
          if (self.visible && !self.selectedTemplateId) {
            self.loadCategoryDefaultTemplate()
          }
        })
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
      // 收集当前编辑内容，组装模板数据对象
      buildTplPayload(tpl) {
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
          // 属性名允许留空（留空表示只显示规格值），空字符串也要保存
          if (Object.prototype.hasOwnProperty.call(self.attrLabels, key)) {
            cleanLabels[key] = self.attrLabels[key]
          }
        })
        return Object.assign(tpl || {}, {
          selectedAttrs: JSON.parse(JSON.stringify(this.selectedAttrs)),
          attrStyles: cleanStyles,
          attrLabels: cleanLabels,
          customLabels: customLabels,
          labelWidth: this.labelWidth,
          labelHeight: this.labelHeight
        })
      },
      persistTemplates(successMsg) {
        var self = this
        saveLabelTemplates(this.templates).then(function(res) {
          if (!res || res.code !== 200) {
            self.$message.warning('模板已保存到本机，云端保存失败，其他设备暂无法同步')
          }
        })
        this.markClean()
        this.$message.success(successMsg)
        // 从关闭确认流程进入的保存：保存完成后关闭编辑器
        if (this.closeAfterSave) {
          this.closeAfterSave = false
          this.visible = false
        }
      },
      // 保存按钮：已有模板（编辑打开）直接覆盖；新建弹保存弹层
      saveTemplate() {
        var self = this
        var exist = this.selectedTemplateId ? this.templates.find(function(t) { return t.id === self.selectedTemplateId }) : null
        if (exist) {
          // 覆盖原有模板：保留 id/名称/类型/品类归属，仅更新内容
          var idx = this.templates.indexOf(exist)
          this.templates.splice(idx, 1, this.buildTplPayload(exist))
          this.persistTemplates('模板已覆盖保存')
          return
        }
        // 新建模板：弹保存弹层
        this.newTemplateName = ''
        this.templateCategoryId = null
        this.saveModalVisible = true
      },
      // 另存为：无论是否已有模板，都弹保存弹层存为新模板
      saveAsTemplate() {
        this.saveModalVisible = true
      },
      // 保存弹层确认：不选商品类型=普通模板，选了=该类型的通用模板
      confirmSaveTemplate() {
        if (!this.newTemplateName) {
          this.$message.warning('请输入模板名称')
          return
        }
        var self = this
        var isCategoryTpl = !!this.templateCategoryId
        var tpl = this.buildTplPayload({
          id: 'tpl_' + Date.now(),
          name: this.newTemplateName,
          type: isCategoryTpl ? 'default' : 'normal',
          materialId: isCategoryTpl ? null : (this.record.id || null),
          categoryId: isCategoryTpl ? this.templateCategoryId : null,
          categoryName: isCategoryTpl ? this.getCategoryName(this.templateCategoryId) : null
        })
        // 类型通用模板：同类型只保留一个
        if (isCategoryTpl) {
          this.templates = this.templates.filter(function(t) {
            return !(t.type === 'default' && String(t.categoryId) === String(self.templateCategoryId))
          })
        }
        this.templates.push(tpl)
        this.selectedTemplateId = tpl.id
        this.saveModalVisible = false
        this.persistTemplates('模板已保存')
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
            var style = JSON.parse(JSON.stringify(tpl.attrStyles[key]))
            // 旧模板没有字间距/行距，补默认值
            if (style.letterSpacing == null) style.letterSpacing = 0
            if (style.lineHeight == null) style.lineHeight = 1.3
            // 旧模板默认写死的宽高视为自动，边框继续跟随字号自适应
            if (style.width === 120 && style.height === 24) {
              delete style.width
              delete style.height
            }
            self.$set(self.attrStyles, key, style)
          } else {
            var idx = tpl.selectedAttrs.indexOf(key)
            self.$set(self.attrStyles, key, { fontSize: 14, x: 10, y: 10 + idx * 30, fontWeight: 'normal', letterSpacing: 0, lineHeight: 1.3 })
          }
          // 模板里存在该属性名的记录就按记录恢复（含空字符串），只有没记录时才用列标题兜底
          if (tpl.attrLabels && Object.prototype.hasOwnProperty.call(tpl.attrLabels, key)) {
            self.$set(self.attrLabels, key, tpl.attrLabels[key])
          } else {
            var attr = self.allAttrs.find(function(a) { return a.key === key })
            self.$set(self.attrLabels, key, attr ? attr.label : '')
          }
        })
        if (tpl.labelWidth) this.labelWidth = tpl.labelWidth
        if (tpl.labelHeight) this.labelHeight = tpl.labelHeight
        this.currentAttrKey = null
        // 打开模板后视为干净状态，关闭时不再提示保存
        var that = this
        this.$nextTick(function() { that.markClean() })
      },
      deleteTemplate() {
        if (!this.selectedTemplateId) return
        var self = this
        this.templates = this.templates.filter(function(t) { return t.id !== self.selectedTemplateId })
        saveLabelTemplates(this.templates).then(function(res) {
          if (!res || res.code !== 200) {
            self.$message.warning('模板已保存到本机，云端保存失败，其他设备暂无法同步')
          }
        })
        this.selectedTemplateId = null
        this.$message.success('模板已删除')
      },
      // 打印：直接打开打印标签页面，并带上当前编辑内容（所见即所得）
      handlePrint() {
        var self = this
        var exist = this.selectedTemplateId ? this.templates.find(function(t) { return t.id === self.selectedTemplateId }) : null
        var base = exist ? JSON.parse(JSON.stringify(exist)) : { id: 'unsaved', name: '', type: 'normal' }
        var tpl = this.buildTplPayload(base)
        this.$refs.labelPrintModal.open(this.record, this.sku, this.categoryTree, tpl)
      }
    },
    beforeDestroy() {
      this.unbindDragEvents()
      this.unbindMarqueeEvents()
    }
  }
</script>
<style scoped>
  /* 三栏布局：左右固定宽度，中间自适应并撑满编辑区高度 */
  .le-layout {
    height: 100%;
  }
  .le-side,
  .le-center {
    height: 100%;
  }
  .label-edit-panel {
    background: #fafafa;
    border-radius: 4px;
    padding: 12px;
    height: 100%;
    max-height: none;
    overflow-y: auto;
    box-sizing: border-box;
  }
  /* 中栏：预览区占满剩余高度 */
  .le-panel-center {
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  .le-panel-center .label-preview-scroll {
    flex: 1;
    height: auto;
    min-height: 200px;
  }
  /* 样式属性：单列，每行一个控件 */
  .style-field {
    display: flex;
    align-items: center;
    margin-bottom: 6px;
  }
  .style-field > .style-label {
    width: 76px;
    flex: none;
    margin-right: 8px;
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
  .label-text-box {
    background: #fff;
    border: 1px solid #e8e8e8;
    border-radius: 4px;
    padding: 8px;
    margin-bottom: 8px;
  }
  .label-size-editor {
    background: #fff;
    border: 1px solid #e8e8e8;
    border-radius: 4px;
    padding: 8px;
    margin-bottom: 8px;
  }
  .style-label {
    font-size: 12px;
    color: #666;
    margin-right: 4px;
  }
  .label-zoom-bar {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    flex-wrap: wrap;
    margin-bottom: 8px;
  }
  .label-zoom-divider {
    width: 1px;
    height: 16px;
    background: #e8e8e8;
    margin: 0 8px;
  }
  .label-zoom-value {
    display: inline-block;
    min-width: 48px;
    text-align: center;
    font-size: 12px;
    color: #666;
    margin: 0 6px;
  }
  .label-preview-scroll {
    height: 460px;
    overflow: auto;
    padding: 28px 16px 16px 32px;
    background: #f0f2f5;
    border: 1px solid #e8e8e8;
    border-radius: 4px;
    display: flex;
    justify-content: center;
    align-items: flex-start;
  }
  .label-preview-wrap {
    position: relative;
    flex: none;
    box-shadow: 0 1px 6px rgba(0, 0, 0, 0.12);
  }
  .label-preview {
    position: absolute;
    left: 0;
    top: 0;
    transform-origin: 0 0;
    background: #fff;
    /* 超出标签范围的文字框仍要完整显示并可继续拖动 */
    overflow: visible;
    outline: 1px dashed #bfbfbf;
    user-select: none;
  }
  /* 框选矩形 */
  .label-marquee {
    position: absolute;
    z-index: 20;
    border: 1px dashed #1890ff;
    background: rgba(24, 144, 255, 0.08);
    pointer-events: none;
  }
  /* 拖拽对齐参考线 */
  .snap-guide {
    position: absolute;
    z-index: 30;
    pointer-events: none;
  }
  .snap-guide-v {
    top: 0;
    bottom: 0;
    width: 0;
    border-left: 1px dashed #ff4d4f;
  }
  .snap-guide-h {
    left: 0;
    right: 0;
    height: 0;
    border-top: 1px dashed #ff4d4f;
  }
  .label-preview-empty {
    color: #ccc;
    text-align: center;
    line-height: 200px;
  }
  /* 条码 / 二维码：上方文字、下方图形 */
  .label-preview-item.is-code {
    align-items: flex-start;
    overflow: visible;
    white-space: normal;
  }
  .label-code-box {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    width: 100%;
  }
  .label-code-text {
    white-space: pre-line;
    word-break: break-word;
    text-align: left;
  }
  .label-code-img {
    display: block;
    object-fit: contain;
  }
  .label-code-fallback {
    font-size: 11px;
    color: #999;
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
    outline: 1px dashed #d9d9d9;
  }
  .label-preview-item.is-multiline {
    /* 规格等属性按商品信息表格的排版显示：逐行、左对齐 */
    white-space: pre-line;
    text-align: left;
    align-items: flex-start;
    line-height: 1.3;
  }
  .label-preview-item:hover {
    outline: 1px dashed #1890ff;
  }
  .label-preview-item.active {
    outline: 1px dashed #1890ff;
    outline-offset: 1px;
    background: rgba(24, 144, 255, 0.05);
  }
  /* 预览刻度尺 */
  .label-ruler-h {
    position: absolute;
    left: 0;
    top: -22px;
    height: 20px;
    background: #fafafa;
    border-bottom: 1px solid #d9d9d9;
    pointer-events: none;
  }
  .label-ruler-v {
    position: absolute;
    top: 0;
    left: -26px;
    width: 24px;
    background: #fafafa;
    border-right: 1px solid #d9d9d9;
    pointer-events: none;
  }
  .ruler-tick {
    position: absolute;
    bottom: 0;
    width: 1px;
    height: 5px;
    background: #bfbfbf;
  }
  .ruler-tick.major {
    height: 9px;
    background: #8c8c8c;
  }
  .ruler-tick em {
    position: absolute;
    left: 2px;
    top: -10px;
    font-size: 10px;
    font-style: normal;
    line-height: 1;
    color: #8c8c8c;
  }
  .ruler-tick-v {
    position: absolute;
    right: 0;
    height: 1px;
    width: 5px;
    background: #bfbfbf;
  }
  .ruler-tick-v.major {
    width: 9px;
    background: #8c8c8c;
  }
  .ruler-tick-v em {
    position: absolute;
    left: 1px;
    top: -5px;
    font-size: 10px;
    font-style: normal;
    line-height: 1;
    color: #8c8c8c;
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
