<template>
  <div ref="container">
    <a-modal
      :title="templateType === 'delivery' ? '发货模版编辑' : '标签编辑'"
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
          <!-- 业务数据源：字段由适配器提供，编辑器不直接访问业务数据库 -->
          <div v-if="templateType !== 'delivery'" class="label-edit-panel" style="margin-bottom:10px">
            <div class="label-edit-section-title">业务数据源</div>
            <a-select
              v-model="provider"
              style="width:100%"
              placeholder="选择业务数据源"
              allowClear
              @change="onProviderChange"
            >
              <a-select-option v-for="p in providerList" :key="p.provider" :value="p.provider">{{ p.name }}</a-select-option>
            </a-select>
            <div style="margin-top:6px;font-size:12px;color:#999">选择后自动加载可用字段，模板只绑定字段标识</div>
          </div>
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

          <!-- 模板管理：分组卡片（仅商品标签模版显示） -->
          <div v-if="templateType !== 'delivery'" class="label-edit-panel" style="margin-top:10px">
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
            <!-- 缩放工具栏（置顶） -->
            <div class="label-zoom-bar">
              <a-button-group size="small">
                <a-button icon="undo" title="撤销" :disabled="!canUndo" @click="undo" />
                <a-button icon="redo" title="恢复" :disabled="!canRedo" @click="redo" />
              </a-button-group>
              <span class="label-zoom-divider"></span>
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
              <a-button-group size="small" style="margin-left:6px">
                <a-button title="左右均分" :disabled="selectedKeys.length < 3" @click="distributeSelected('h')">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="square">
                    <rect x="1" y="7" width="4" height="10"/>
                    <rect x="10" y="7" width="4" height="10"/>
                    <rect x="19" y="7" width="4" height="10"/>
                  </svg>
                </a-button>
                <a-button title="上下均分" :disabled="selectedKeys.length < 3" @click="distributeSelected('v')">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="square">
                    <rect x="7" y="1" width="10" height="4"/>
                    <rect x="7" y="10" width="10" height="4"/>
                    <rect x="7" y="19" width="10" height="4"/>
                  </svg>
                </a-button>
              </a-button-group>
              <span class="label-zoom-divider"></span>
              <a-button size="small" icon="zoom-out" title="缩小" @click="changeZoom(-0.1)" />
              <span class="label-zoom-value">{{ Math.round(zoom * 100) }}%</span>
              <a-button size="small" icon="zoom-in" title="放大" @click="changeZoom(0.1)" />
              <a-button size="small" @click="resetZoom">100%</a-button>
              <a-button size="small" @click="zoomToFit">适应窗口</a-button>
              <span class="label-zoom-divider"></span>
              <a-button v-if="templateType !== 'delivery'" size="small" @click="saveAsTemplate">另存为</a-button>
              <a-button size="small" type="primary" @click="saveTemplate">保存</a-button>
            </div>
            <div class="label-edit-section-title">
              标签预览（{{ labelWidthMm }}mm × {{ labelHeightMm }}mm）
              <span style="font-weight:normal;font-size:12px;color:#999;margin-left:8px">点击选择属性，再次点击取消；长按全选拖动；拖拽移动位置；双击自定义标签编辑内容</span>
            </div>
            <div class="label-preview-scroll" ref="previewScroll" :class="wrapScrolling ? 'label-preview-scroll--scrolling' : 'label-preview-scroll--fitting'" @mousedown.self="clearAttrSelection">
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
                    v-for="item in renderItems"
                    :key="item.key"
                    :data-key="item.key"
                    class="label-preview-item"
                    :class="{ active: selectedKeys.indexOf(item.key) > -1, 'is-multiline': !item.isCode && !item.attr.type && isMultilineValue(item.attr.value), 'is-code': item.isCode, 'is-table': item.attr.type === 'table' }"
                    :style="getItemStyle(item.key)"
                    @mousedown="onItemMouseDown($event, item.key)"
                    @dblclick="!item.isCode && onItemDoubleClick($event, item.attr)"
                  >
                    <!-- 商品明细表（type=table）：按商品名称分行显示 商品名称/数量/规格 -->
                    <template v-if="!item.isCode && item.attr.type === 'table'">
                      <div class="le-table">
                        <div class="le-table-head">
                          <div class="le-col-name">商品名称</div>
                          <div class="le-col-qty">数量</div>
                          <div class="le-col-spec">规格</div>
                        </div>
                        <div v-for="(mi, miIdx) in deliveryMockItems" :key="miIdx" class="le-table-row">
                          <div class="le-col-name">{{ mi.materialName }}</div>
                          <div class="le-col-qty">{{ mi.operNumber }}</div>
                          <div class="le-col-spec">{{ mi.specDisplay }}</div>
                        </div>
                      </div>
                    </template>
                    <template v-else-if="!item.isCode">
                      <template v-if="attrLabels[item.attr.key]">{{ attrLabels[item.attr.key] }}: </template>
                      <input
                        v-if="editingKey === item.key"
                        :value="item.attr.value"
                        @input="updateCustomValue(item.attr.key, $event.target.value)"
                        @blur="editingKey = null"
                        @keyup.enter="editingKey = null"
                        @mousedown.stop
                        class="label-edit-input"
                      />
                      <template v-else>{{ item.attr.value }}</template>
                    </template>
                    <!-- 条码/二维码：独立元素，可单独选中、拖动、调整宽高 -->
                    <template v-else>
                      <img v-if="codeImages[item.key]" :src="codeImages[item.key]" class="label-code-img" :style="codeImgStyle(item.key)" />
                      <span v-else class="label-code-fallback">{{ item.attr.value }}</span>
                    </template>
                    <!-- resize 手柄 -->
                    <div
                      v-if="selectedKeys.length === 1 && currentAttrKey === item.key && editingKey !== item.key"
                      class="resize-handle"
                      @mousedown.stop.prevent="onResizeMouseDown($event, item.key)"
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
            <!-- 编辑模式：当前模板标识（通用模板显示「类型/模板名」，普通模板只显示模板名） -->
            <div v-if="currentTemplateTitle" class="label-current-template">
              <span class="label-current-template-text">{{ currentTemplateTitle }}</span>
              <a class="label-current-template-del" @click="handleDeleteCurrentTemplate">删除</a>
            </div>
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
            </div>
            <div v-if="!selectedKeys.length" class="label-edit-empty">请点击预览中的属性</div>
            <div v-else>
              <!-- 属性名编辑：可输入可快速选择，留空则不显示属性名（编码元素不适用） -->
              <div style="margin-bottom:8px" v-if="!currentIsCode">
                <span class="style-label">属性名编辑</span>
                <a-auto-complete
                  v-model="currentLabelEdit"
                  :dataSource="presetLabelNames"
                  placeholder="选择或输入属性名（留空则不显示属性名）"
                  style="width:100%"
                />
              </div>
              <!-- 条码 / 二维码：作为独立元素添加到标签中，可单独拖动和调整宽高 -->
              <div style="margin-bottom:8px">
                <span class="style-label">编码</span>
                <a-checkbox :checked="currentCodeType === 'barcode'" @change="e => setCodeType('barcode', e.target.checked)">条码</a-checkbox>
                <a-checkbox :checked="currentCodeType === 'qrcode'" @change="e => setCodeType('qrcode', e.target.checked)">二维码</a-checkbox>
                <div v-if="currentIsCode" style="font-size:12px;color:#999;margin-top:2px">编码为独立元素，可单独拖动和调整宽高</div>
              </div>

              <!-- 字体属性：单列排列，数值项带数字调节按钮；多选时不一致的项显示为空（编码元素不适用） -->
              <div class="style-field" v-if="!currentIsCode">
                <span class="style-label">字号</span>
                <a-input-number :value="getMultiStyle('fontSize')" @change="v => setMultiStyle('fontSize', v)" :min="6" :max="72" size="small" style="width:100%" placeholder="多个值" />
              </div>
              <div class="style-field" v-if="!currentIsCode">
                <span class="style-label">粗细</span>
                <a-select :value="getMultiStyle('fontWeight')" @change="v => setMultiStyle('fontWeight', v)" size="small" style="width:100%" placeholder="多个值">
                  <a-select-option value="normal">正常</a-select-option>
                  <a-select-option value="bold">粗体</a-select-option>
                </a-select>
              </div>
              <div class="style-field" v-if="!currentIsCode">
                <span class="style-label">字间距(px)</span>
                <a-input-number :value="getMultiStyle('letterSpacing')" @change="v => setMultiStyle('letterSpacing', v)" :min="0" :max="30" :step="0.5" size="small" style="width:100%" placeholder="多个值" />
              </div>
              <div class="style-field" v-if="!currentIsCode">
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
        <div v-if="templateType !== 'delivery'">
          <div class="label-edit-section-title" style="margin-bottom:6px">商品类型</div>
          <a-select
            v-model="templateCategoryId"
            style="width:100%"
            placeholder="不选择则保存为普通模板"
            allowClear
            showSearch
            :filterOption="filterCategory"
            :getPopupContainer="getBodyContainer"
            :dropdownStyle="{ zIndex: 3200 }"
          >
            <a-select-option v-for="cat in categoryList" :key="cat.value" :value="cat.value">{{ cat.label }}</a-select-option>
          </a-select>
          <div style="margin-top:6px;font-size:12px;color:#999">仅二级类目可作为模板类型（一级类目不可保存模板）；一个类目只保留一个模板</div>
          <div v-if="conflictTemplate" style="margin-top:4px;font-size:12px;color:#fa8c16">
            该类型已有模板「{{ conflictTemplate.name }}」，保存后将覆盖
          </div>
          <div style="margin-top:4px;font-size:12px;color:#999">不选类型保存为普通模板（仅当前商品可用）；选择类型保存为该类型的通用模板</div>
        </div>
    </a-modal>
  </div>
</template>

<script>
  // 商品列定义：标签可选属性直接取自商品信息表格的列，保证两处字段一致
  import { buildLabelAttrs } from '@/views/material/utils/materialDisplay'
  import { fitCodeSize, renderCodeDataUrl, isCodeKey, codeSourceKey, migrateCodeStyles, CODE_KEY_PREFIX } from '@/views/material/utils/labelCode'
  import { fetchLabelTemplates, saveLabelTemplate, deleteLabelTemplate } from '@/views/material/utils/labelTemplateStore'
  import { fetchProviders } from '@/views/material/utils/labelProviderApi'
  import { fetchDeliveryTemplate, saveDeliveryTemplate as persistDeliveryTemplate, DELIVERY_BUSINESS_TYPE } from '@/views/bill/utils/deliveryNoteTemplateStore'
  import { resolveTemplateData } from '@/views/material/utils/labelDataResolver'
  import { legacyToSchema } from '@/views/material/utils/labelSchema'
  import LabelPrintModal from './LabelPrintModal'
  import { computeSpacer, setupLabelScroll } from '@/utils/label-scroll'

  // 像素与毫米转换常量（96dpi: 1mm ≈ 3.779528px）
  var PX_PER_MM = 3.779528

  export default {
    name: "LabelEditModal",
    components: { LabelPrintModal },
    data() {
      return {
        visible: false,
        templateType: 'label',
        record: {},
        sku: {},
        categoryTree: [],
        allAttrs: [],
        // 业务数据源（适配器标识）与其字段/数据
        providerList: [],
        provider: '',
        providerFields: [],
        providerData: {},
        businessType: '',
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
        // 从打印预览进入时要加载的模板 id（优先于品类默认模板）
        pendingTemplateId: null,
        // 发货单模板（统一模板表记录，保存时复用 serverId 更新）
        deliveryTemplate: null,
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
        // wrap 是否溢出需要滚动：true → flex-start + scrollLeft 居中；false → center 直接居中
        wrapScrolling: false,
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
        customCounter: 0,
        // undo/redo 历史
        history: [],
        historyIdx: -1,
        // 发货模板预览用的商品明细 mock 数据
        deliveryMockItems: [
          { materialName: '记忆棉枕芯', operNumber: 2, specDisplay: '50×30cm 白色' },
          { materialName: '纯棉四件套', operNumber: 1, specDisplay: '1.8m床 蓝色' },
          { materialName: '蚕丝被', operNumber: 1, specDisplay: '200×230cm 双人' }
        ]
      }
    },
    computed: {
      selectedAttrList() {
        return this.allAttrs.filter(attr => this.selectedAttrs.indexOf(attr.key) > -1)
      },
      // 当前正在编辑的模板（未选中模板时为 null）
      currentTemplate() {
        var id = this.selectedTemplateId
        if (!id) return null
        return this.templates.find(function(t) { return t.id === id }) || null
      },
      // 编辑模式下的模板标识：通用模板显示「类型/模板名」，普通模板只显示模板名
      currentTemplateTitle() {
        var tpl = this.currentTemplate
        if (!tpl) return ''
        if (tpl.type === 'default') {
          var typeName = tpl.categoryName || this.getCategoryName(tpl.categoryId) || '通用模板'
          return typeName + ' / ' + tpl.name
        }
        return tpl.name
      },
      // 渲染条目：属性文字元素 + 独立的条码/二维码元素
      renderItems() {
        var items = []
        var styles = this.attrStyles
        this.selectedAttrList.forEach(function(attr) {
          items.push({ key: attr.key, attr: attr, isCode: false })
          if (styles[CODE_KEY_PREFIX + attr.key]) {
            items.push({ key: CODE_KEY_PREFIX + attr.key, attr: attr, isCode: true })
          }
        })
        return items
      },
      // 当前选中的是否为独立编码元素
      currentIsCode() {
        return isCodeKey(this.currentAttrKey)
      },
      // 当前选中项关联的编码类型（条码/二维码），未关联时返回空
      currentCodeType() {
        var key = this.currentAttrKey
        if (!key) return ''
        if (isCodeKey(key)) {
          var s = this.attrStyles[key]
          return (s && s.codeType) || ''
        }
        var codeStyle = this.attrStyles[CODE_KEY_PREFIX + key]
        return (codeStyle && codeStyle.codeType) || ''
      },
      // 编码内容签名：属性值、编码类型、尺寸变化时重新生成图片
      codeSignature() {
        var self = this
        return this.renderItems.filter(function(item) { return item.isCode }).map(function(item) {
          var s = self.attrStyles[item.key] || {}
          return [item.key, s.codeType || '', item.attr.value == null ? '' : item.attr.value, s.width || '', s.height || ''].join('|')
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
        // 仅二级类目可作为模板类型：一级类目不允许保存模板
        return this.flattenCategoryTree(this.categoryTree, 2)
      },
      // 全部类目（仅用于类目名称回显，包含一级类目）
      allCategoryList() {
        return this.flattenCategoryTree(this.categoryTree)
      },
      // 所选类目下已存在的模板：一个类目只保留一个模板，保存时覆盖
      conflictTemplate() {
        var id = this.templateCategoryId
        if (!id) return null
        return this.templates.find(function(t) {
          return t.type === 'default' && String(t.categoryId) === String(id)
        }) || null
      },
      normalTemplates() {
        var mid = this.record.id
        return this.templates.filter(function(t) { return t.type === 'normal' && t.materialId === mid })
      },
      defaultTemplates() {
        return this.templates.filter(function(t) { return t.type === 'default' })
      },
      canUndo() {
        return this.historyIdx > 0
      },
      canRedo() {
        return this.historyIdx < this.history.length - 1
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
      this.loadProviders()
      // 标签居中 + 对称滑动：用 label-scroll 模块
      setupLabelScroll(this, 'previewScroll', 'labelWidth', 'zoom', 'wrapScrolling')
    },
    methods: {
      open(record, sku, categoryTree, templateId, templateType, businessType) {
        this.record = record || {}
        this.sku = sku || {}
        this.categoryTree = categoryTree || []
        this.templateType = templateType || 'label'
        // 业务标识（如 product），决定下拉框自动加载哪些业务数据表
        this.businessType = businessType || ''
        // 从打印预览进入时带上的模板 id：优先加载这套模板（即预览里看到的那套标签数据）
        this.pendingTemplateId = templateId || null
        this.allAttrs = this.buildAttrList()
        this.selectedAttrs = []
        this.attrStyles = {}
        this.attrLabels = {}
        this.provider = ''
        this.providerFields = []
        this.providerData = {}
        this.currentAttrKey = null
        this.selectedKeys = []
        this.editingKey = null
        this.selectedTemplateId = null
        this.newTemplateName = ''
        this.templateCategoryId = null
        this.deliveryTemplate = null
        this.saveModalVisible = false
        this.closeAfterSave = false
        this.customCounter = 0
        // 发货模版默认尺寸更大（A5 左右），标签模版保持原尺寸
        if (this.templateType === 'delivery') {
          this.labelWidth = 560
          this.labelHeight = 800
        } else {
          this.labelWidth = 320
          this.labelHeight = 240
        }
        this.zoom = 1
        this.guideLines = { v: [], h: [] }
        // 按业务类型加载数据源（含该业务相关的数据表）
        this.loadProviders(this.businessType)
        this.visible = true
        if (this.templateType === 'delivery') {
          this.loadDeliveryTemplate()
        } else {
          this.loadCategoryDefaultTemplate()
        }
        var self = this
        this.$nextTick(function() { self.markClean() })
        this.resetHistory()
      },
      // 记录当前编辑状态快照（保存/打开模板后调用），用于关闭时判断是否有改动
      snapshotState() {
        return JSON.stringify({
          attrs: this.selectedAttrs,
          styles: this.attrStyles,
          labels: this.attrLabels,
          w: this.labelWidth,
          h: this.labelHeight,
          provider: this.provider
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
        if (this.templateType === 'delivery') {
          return this.buildDeliveryAttrs()
        }
        // 与打印标签预览页共用同一套属性来源（商品信息表格全部列 + 旧模板兼容键）
        return buildLabelAttrs(this.record, this.sku)
      },
      // 发货模版属性：单据头、公司信息、明细表、合计、备注、落款
      buildDeliveryAttrs() {
        return [
          { key: 'dn_title', label: '发货单标题', value: '发货单', preset: '发货单' },
          { key: 'dn_no', label: '单号', value: 'LS261104001' },
          { key: 'dn_date', label: '日期', value: '2026-11-04' },
          { key: 'dn_member', label: '会员', value: '张三' },
          { key: 'dn_phone', label: '电话', value: '13800138000' },
          { key: 'dn_address', label: '地址', value: '北京市朝阳区xx路xx号' },
          { key: 'dn_table', label: '商品明细表', value: '商品明细表', type: 'table' },
          { key: 'dn_qty_total', label: '合计数量', value: '3' },
          { key: 'dn_amount_total', label: '合计金额', value: '199.00' },
          { key: 'dn_remark', label: '备注', value: '请尽快发货' },
          { key: 'dn_footer', label: '落款', value: '华夏家纺' }
        ]
      },
      // onlyLevel 指定时只保留该级类目（1=一级，2=二级），未返回 categoryLevel 时按树深度判断
      flattenCategoryTree(tree, onlyLevel) {
        var result = []
        function traverse(nodes, prefix, depth) {
          if (!nodes) return
          nodes.forEach(function(node) {
            var label = prefix ? prefix + ' / ' + (node.title || node.label) : (node.title || node.label)
            var level = node.categoryLevel != null ? Number(node.categoryLevel) : depth
            if (!onlyLevel || level === onlyLevel) {
              result.push({ value: node.value || node.id, label: label })
            }
            if (node.children) traverse(node.children, label, depth + 1)
          })
        }
        traverse(tree, '', 1)
        return result
      },
      filterCategory(input, option) {
        return option.componentOptions.children[0].text.indexOf(input) >= 0
      },
      // 下拉挂到 body：避免被「保存模板」弹窗裁剪/遮挡，且不受弹窗定位影响
      getBodyContainer() {
        return document.body
      },
      getCategoryName(categoryId) {
        if (!categoryId) return ''
        var cat = this.allCategoryList.find(function(c) { return String(c.value) === String(categoryId) })
        return cat ? cat.label : ''
      },
      getItemStyle(key) {
        var s = this.attrStyles[key]
        if (!s) return {}
        // 独立编码元素：宽高即图形实际可打印尺寸
        if (isCodeKey(key)) {
          var codeSize = this.codeLayout(key)
          return {
            left: (s.x || 0) + 'px',
            top: (s.y || 0) + 'px',
            width: codeSize.imgWidth + 'px',
            height: Math.round(codeSize.imgHeight) + 'px'
          }
        }
        var style = {
          left: s.x + 'px',
          top: s.y + 'px',
          fontSize: s.fontSize + 'px',
          fontWeight: s.fontWeight,
          letterSpacing: (s.letterSpacing || 0) + 'px',
          lineHeight: String(s.lineHeight || 1.3)
        }
        // 指定宽度时文字按框内宽度自动换行（与打印一致）
        if (s.width) {
          style.width = s.width + 'px'
          style.whiteSpace = 'pre-wrap'
          style.wordBreak = 'break-all'
          style.overflowWrap = 'anywhere'
        }
        if (s.height) style.height = s.height + 'px'
        return style
      },
      // 是否为独立编码元素
      isCodeItem(key) {
        return isCodeKey(key)
      },
      // 编码元素的图形布局：按属性值生成图形，尺寸取元素自身宽高
      codeLayout(codeKey) {
        var s = this.attrStyles[codeKey] || {}
        var isQr = s.codeType === 'qrcode'
        var sourceKey = codeSourceKey(codeKey)
        var attr = this.allAttrs.find(function(a) { return a.key === sourceKey })
        var value = attr && attr.value != null ? attr.value : ''
        var boxWidth = s.width || (isQr ? 80 : 160)
        var boxHeight = s.height || (isQr ? 80 : 50)
        // 按打印机硬件能力换算实际可打印尺寸，预览与打印保持一致
        var fitted = fitCodeSize(s.codeType, value, boxWidth, boxHeight)
        return {
          value: value,
          imgWidth: fitted.width,
          imgHeight: fitted.height
        }
      },
      // 编码图片尺寸
      codeImgStyle(codeKey) {
        var layout = this.codeLayout(codeKey)
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
        // 已取消勾选的属性：同时清理其独立编码元素与多选状态
        this.selectedKeys = this.selectedKeys.filter(function(k) {
          var sourceKey = isCodeKey(k) ? codeSourceKey(k) : k
          return self.selectedAttrs.indexOf(sourceKey) > -1
        })
        Object.keys(this.attrStyles).forEach(function(k) {
          if (!isCodeKey(k)) return
          if (self.selectedAttrs.indexOf(codeSourceKey(k)) === -1) {
            self.$delete(self.attrStyles, k)
            self.$delete(self.codeImages, k)
          }
        })
        if (this.currentAttrKey && this.selectedKeys.indexOf(this.currentAttrKey) === -1) {
          this.currentAttrKey = null
        }
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
        // 同步删除该属性对应的独立编码元素
        this.$delete(this.attrStyles, CODE_KEY_PREFIX + key)
        this.$delete(this.codeImages, CODE_KEY_PREFIX + key)
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
      // 切换编码：选中文字元素时为该属性添加/移除独立编码元素；选中编码元素时直接切换类型
      setCodeType(type, checked) {
        var key = this.currentAttrKey
        if (!key) return
        if (isCodeKey(key)) {
          var codeStyle = this.attrStyles[key]
          if (!codeStyle) return
          if (checked) {
            this.$set(codeStyle, 'codeType', type)
          } else if (codeStyle.codeType === type) {
            this.removeCodeItem(key)
          }
          return
        }
        var codeKey = CODE_KEY_PREFIX + key
        if (checked) {
          if (this.attrStyles[codeKey]) {
            this.$set(this.attrStyles[codeKey], 'codeType', type)
          } else {
            this.createCodeItem(key, type)
          }
        } else if (this.attrStyles[codeKey] && this.attrStyles[codeKey].codeType === type) {
          this.removeCodeItem(codeKey)
        }
      },
      // 在属性文字下方创建独立的条码/二维码元素
      createCodeItem(attrKey, type) {
        var textStyle = this.attrStyles[attrKey] || {}
        var el = document.querySelector('.label-preview-item[data-key="' + attrKey + '"]')
        var textHeight = el ? el.offsetHeight : 18
        var isQr = type === 'qrcode'
        var codeKey = CODE_KEY_PREFIX + attrKey
        this.$set(this.attrStyles, codeKey, {
          x: textStyle.x || 0,
          y: (textStyle.y || 0) + textHeight + 4,
          width: isQr ? 80 : 160,
          height: isQr ? 80 : 50,
          codeType: type
        })
        this.selectedKeys = [codeKey]
        this.currentAttrKey = codeKey
      },
      // 删除独立编码元素
      removeCodeItem(codeKey) {
        if (this.currentAttrKey === codeKey) this.currentAttrKey = null
        var idx = this.selectedKeys.indexOf(codeKey)
        if (idx > -1) this.selectedKeys.splice(idx, 1)
        this.$delete(this.codeImages, codeKey)
        this.$delete(this.attrStyles, codeKey)
      },
      // 为所有独立编码元素生成图片
      refreshCodeImages() {
        var self = this
        var alive = {}
        this.renderItems.forEach(function(item) {
          if (!item.isCode) return
          var s = self.attrStyles[item.key]
          if (!s) return
          alive[item.key] = true
          var layout = self.codeLayout(item.key)
          renderCodeDataUrl(s.codeType, layout.value, layout.imgWidth, layout.imgHeight).then(function(url) {
            self.$set(self.codeImages, item.key, url)
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
        this.pushSnapshot()
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
        this.pushSnapshot()
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
        this.pushSnapshot()
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
      // 点击选中/取消（toggle），长按 600ms 全选所有属性并整体拖动
      onItemMouseDown(e, key) {
        if (this.editingKey === key) return
        e.preventDefault()
        e.stopPropagation()
        var self = this
        // 若当前已多选，点在已选中项上直接整体拖动
        if (this.selectedKeys.indexOf(key) > -1 && this.selectedKeys.length > 1) {
          this.startDrag(e, key)
          return
        }
        this._pressTimer = setTimeout(function() {
          // 长按：全选所有可渲染属性并整体拖动
          self._pressTimer = null
          self._mousedownInfo = null
          document.removeEventListener('mousemove', self._onItemMouseMove, true)
          document.removeEventListener('mouseup', self._onItemMouseUp, true)
          var allKeys = self.renderItems.map(function(item) { return item.key })
          self.selectedKeys = allKeys
          self.currentAttrKey = key
          self.startDrag(e, key)
        }, 600)
        this._mousedownInfo = { key: key, clientX: e.clientX, clientY: e.clientY, moved: false }
        document.addEventListener('mousemove', this._onItemMouseMove, true)
        document.addEventListener('mouseup', this._onItemMouseUp, true)
      },
      _onItemMouseMove(e) {
        var info = this._mousedownInfo
        if (!info) return
        var dx = e.clientX - info.clientX
        var dy = e.clientY - info.clientY
        if (Math.abs(dx) + Math.abs(dy) > 3) {
          info.moved = true
          // 位移超过阈值：取消 toggle/长按，保留当前状态并开始拖拽
          if (this._pressTimer) {
            clearTimeout(this._pressTimer)
            this._pressTimer = null
          }
          this._mousedownInfo = null
          document.removeEventListener('mousemove', this._onItemMouseMove, true)
          document.removeEventListener('mouseup', this._onItemMouseUp, true)
          // 点在未选中项上：先选中它再开始拖拽
          if (this.selectedKeys.indexOf(info.key) === -1) {
            this.selectedKeys = [info.key]
          }
          this.startDrag(e, info.key)
        }
      },
      _onItemMouseUp(e) {
        document.removeEventListener('mousemove', this._onItemMouseMove, true)
        document.removeEventListener('mouseup', this._onItemMouseUp, true)
        var info = this._mousedownInfo
        this._mousedownInfo = null
        if (this._pressTimer) {
          clearTimeout(this._pressTimer)
          this._pressTimer = null
          // 点击 toggle
          var idx = this.selectedKeys.indexOf(info.key)
          if (idx > -1) {
            this.selectedKeys.splice(idx, 1)
            if (this.currentAttrKey === info.key) this.currentAttrKey = this.selectedKeys.length ? this.selectedKeys[this.selectedKeys.length - 1] : null
          } else {
            // 点击未选中项：单选，不再累加到已有选中项
            this.selectedKeys = [info.key]
            this.currentAttrKey = info.key
          }
        }
      },
      startDrag(e, key) {
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
        // 拖拽/resize 结束后保存快照
        this.pushSnapshot()
      },
      // undo/redo 历史快照：仅保存样式相关（位置/字号/粗细/宽高）
      pushSnapshot() {
        var snap = JSON.stringify({
          attrStyles: this.attrStyles,
          attrLabels: this.attrLabels,
          labelWidth: this.labelWidth,
          labelHeight: this.labelHeight
        })
        // 和上一个快照相同就不重复存
        if (this.historyIdx >= 0 && this.history[this.historyIdx] === snap) return
        // 丢弃 redo 分支
        if (this.historyIdx < this.history.length - 1) {
          this.history = this.history.slice(0, this.historyIdx + 1)
        }
        // 限制历史长度
        if (this.history.length > 50) {
          this.history.shift()
        }
        this.historyIdx = this.history.push(snap) - 1
      },
      undo() {
        if (!this.canUndo) return
        this.historyIdx--
        var snap = JSON.parse(this.history[this.historyIdx])
        this.restoreSnapshot(snap)
      },
      redo() {
        if (!this.canRedo) return
        this.historyIdx++
        var snap = JSON.parse(this.history[this.historyIdx])
        this.restoreSnapshot(snap)
      },
      restoreSnapshot(snap) {
        // 整体替换保证 Vue 2 响应式
        this.attrStyles = JSON.parse(JSON.stringify(snap.attrStyles || {}))
        this.attrLabels = JSON.parse(JSON.stringify(snap.attrLabels || {}))
        this.labelWidth = snap.labelWidth != null ? snap.labelWidth : this.labelWidth
        this.labelHeight = snap.labelHeight != null ? snap.labelHeight : this.labelHeight
      },
      // 调用这个方法来清空历史（打开新模板时）
      resetHistory() {
        this.history = []
        this.historyIdx = -1
        this.$nextTick(function() { this.pushSnapshot() })
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
        this.pushSnapshot()
      },
      // 模板管理：云端读取，本地仅作缓存
      loadTemplates() {
        var self = this
        fetchLabelTemplates().then(function(list) {
          self.templates = list
          // 弹层已打开但模板晚于打开到达时，补一次品类默认模板匹配。
          // 但若用户已主动选择了业务数据源（provider 非空），则不再用默认模板覆盖其选择。
          if (self.visible && !self.selectedTemplateId && !self.provider) {
            self.loadCategoryDefaultTemplate()
          }
        })
      },
      // 业务数据源列表（后端适配器注册）
      // businessType 为业务标识（如 product），传入时后端会追加该业务相关的数据表
      loadProviders(businessType) {
        var self = this
        this.businessType = businessType || ''
        var expectedBusinessType = this.businessType
        fetchProviders(businessType).then(function(list) {
          // 防止更早的请求（如 mounted 中无 businessType 的调用）覆盖更新的结果
          if (self.businessType !== expectedBusinessType) return
          self.providerList = list || []
        })
      },
      // 切换数据源：按适配器字段重建可选属性
      onProviderChange(provider) {
        this.provider = provider || ''
        this.applyProviderData(this.provider)
      },
      /**
       * 按数据源加载字段与预览数据
       * 只调用统一数据接口，不直接访问业务数据库
       */
      applyProviderData(provider) {
        var self = this
        if (!provider) {
          this.providerFields = []
          this.providerData = {}
          this.allAttrs = this.buildAttrList()
          return Promise.resolve()
        }
        return resolveTemplateData({
          provider: provider,
          documentId: this.record.id,
          sku: this.sku
        }).then(function(result) {
          if (self.provider !== provider) return
          self.providerFields = result.fields || []
          self.providerData = result.values || {}
          self.allAttrs = self.buildProviderAttrs(result.attrs)
          // 模板未显式设置标签名的字段，按数据源字段名补上
          ;(result.attrs || []).forEach(function(attr) {
            if (self.attrLabels[attr.key] === undefined) {
              self.$set(self.attrLabels, attr.key, attr.label)
            }
          })
          // 数据源里已不存在的字段从模板中移除，避免绑定失效（自定义标签保留）
          if (result.attrs && result.attrs.length) {
            var keys = self.allAttrs.map(function(attr) { return attr.key })
            self.selectedAttrs = self.selectedAttrs.filter(function(key) { return keys.indexOf(key) > -1 })
            self.onSelectedChange()
          }
          self.$nextTick(function() { self.markClean() })
        })
      },
      // 数据源属性 + 模板自定义标签合并
      buildProviderAttrs(attrs) {
        var list = (attrs || []).filter(function(attr) {
          // 数组明细不直接作为文本绑定，留给模板的循环渲染能力使用
          return attr.type !== 'array'
        }).map(function(attr) {
          return {
            key: attr.key,
            label: attr.label,
            value: attr.value,
            type: attr.type,
            isCustom: false
          }
        })
        var exists = {}
        list.forEach(function(attr) { exists[attr.key] = true })
        this.allAttrs.forEach(function(attr) {
          if (attr.isCustom && !exists[attr.key]) list.push(attr)
        })
        return list
      },
      // 发货模版：通过统一模板存储适配器读取（无记录时适配器回退旧配置）
      loadDeliveryTemplate() {
        var self = this
        fetchDeliveryTemplate().then(function(tpl) {
          if (tpl && tpl.selectedAttrs && tpl.selectedAttrs.length) {
            self.deliveryTemplate = tpl
            self.selectedAttrs = JSON.parse(JSON.stringify(tpl.selectedAttrs))
            self.attrStyles = JSON.parse(JSON.stringify(tpl.attrStyles || {}))
            self.attrLabels = JSON.parse(JSON.stringify(tpl.attrLabels || {}))
            self.labelWidth = tpl.labelWidth || tpl.width || 560
            self.labelHeight = tpl.labelHeight || tpl.height || 800
            self.resetHistory()
          } else {
            self.applyDefaultDeliveryLayout()
          }
          self.$nextTick(function() { self.markClean() })
        }).catch(function() {
          self.applyDefaultDeliveryLayout()
          self.$nextTick(function() { self.markClean() })
        })
      },
      applyDefaultDeliveryLayout() {
        var attrs = [
          { key: 'dn_title', label: '发货单标题', value: '发货单' },
          { key: 'dn_no', label: '单号', value: 'LS261104001' },
          { key: 'dn_date', label: '日期', value: '2026-11-04' },
          { key: 'dn_member', label: '会员', value: '张三' },
          { key: 'dn_phone', label: '电话', value: '13800138000' },
          { key: 'dn_address', label: '地址', value: '北京市朝阳区xx路xx号' },
          { key: 'dn_table', label: '商品明细表', value: '商品明细表' },
          { key: 'dn_qty_total', label: '合计数量', value: '3' },
          { key: 'dn_amount_total', label: '合计金额', value: '199.00' },
          { key: 'dn_remark', label: '备注', value: '请尽快发货' },
          { key: 'dn_footer', label: '落款', value: '华夏家纺' }
        ]
        var self = this
        // 关键：先组装普通对象再整体赋值，保证 Vue 2 响应式（逐 key 新增属性不响应）
        var styles = {}
        var labels = {}
        var selected = []
        var y = 10
        attrs.forEach(function(a, idx) {
          selected.push(a.key)
          styles[a.key] = { x: 20, y: y, width: 0, height: 0, fontSize: a.key === 'dn_title' ? 18 : 12, fontWeight: a.key === 'dn_title' ? 'bold' : 'normal', letterSpacing: 0, lineHeight: 1.2 }
          labels[a.key] = a.label
          if (a.key === 'dn_table') {
            y += 160
          } else if (a.key === 'dn_title') {
            y += 32
          } else {
            y += 24
          }
        })
        this.selectedAttrs = selected
        this.attrStyles = styles
        this.attrLabels = labels
        this.resetHistory()
      },
      loadCategoryDefaultTemplate() {
        var self = this
        var tpl = null
        // 记录调度时的 provider，若用户在此期间主动选择了数据源则不再覆盖
        var providerAtSchedule = this.provider
        // 从打印预览进入：先加载预览里正在用的那套模板
        if (this.pendingTemplateId) {
          tpl = this.templates.find(function(t) { return t.id === self.pendingTemplateId }) || null
          if (tpl) {
            this.selectedTemplateId = tpl.id
            this.$nextTick(function() {
              if (self.provider !== providerAtSchedule) return
              self.onTemplateChange(tpl.id)
              // 打开时的模板加载不算修改，避免关闭时误弹保存确认
              self.markClean()
            })
            return
          }
        }
        var categoryId = this.record.categoryId
        if (!categoryId) return
        tpl = this.templates.find(function(t) {
          return t.type === 'default' && String(t.categoryId) === String(categoryId)
        })
        if (tpl) {
          this.selectedTemplateId = tpl.id
          this.$nextTick(function() {
            if (self.provider !== providerAtSchedule) return
            self.onTemplateChange(tpl.id)
          })
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
          // 独立编码元素（条码/二维码）样式一并保存
          var codeKey = CODE_KEY_PREFIX + key
          if (self.attrStyles[codeKey]) {
            cleanStyles[codeKey] = JSON.parse(JSON.stringify(self.attrStyles[codeKey]))
          }
          // 属性名允许留空（留空表示只显示规格值），空字符串也要保存
          if (Object.prototype.hasOwnProperty.call(self.attrLabels, key)) {
            cleanLabels[key] = self.attrLabels[key]
          }
        })
        return Object.assign(tpl || {}, {
          provider: this.provider || (tpl && tpl.provider) || '',
          businessType: this.provider || (tpl && tpl.businessType) || '',
          selectedAttrs: JSON.parse(JSON.stringify(this.selectedAttrs)),
          attrStyles: cleanStyles,
          attrLabels: cleanLabels,
          customLabels: customLabels,
          labelWidth: this.labelWidth,
          labelHeight: this.labelHeight
        })
      },
      // 保存单个模板到统一模板表（config 内为 v3 结构）
      // 保存前校验业务数据源；保存成功后从服务端刷新列表，拿到权威 id/version
      persistTemplate(tpl, successMsg) {
        var self = this
        if (!this.provider) {
          this.$message.warning('请先选择业务数据源')
          this.closeAfterSave = false
          return Promise.resolve(null)
        }
        return saveLabelTemplate(tpl).then(function(res) {
          if (!res || res.code !== 200) {
            self.$message.warning('模板已保存到本机，云端保存失败，其他设备暂无法同步')
            self.closeAfterSave = false
            return null
          }
          var serverId = res.data
          return fetchLabelTemplates().then(function(list) {
            self.templates = list
            var saved = null
            if (serverId != null && serverId !== '') {
              saved = list.find(function(t) { return String(t.serverId) === String(serverId) })
            }
            if (!saved) {
              saved = list.find(function(t) { return t.name === tpl.name })
            }
            if (saved) self.selectedTemplateId = saved.id
            return saved
          }).catch(function() {
            // 刷新失败时保留本地乐观数据，不阻塞保存结果
            return null
          }).then(function(saved) {
            self.$message.success(successMsg)
            self.markClean()
            if (self.closeAfterSave) {
              self.closeAfterSave = false
              self.visible = false
            }
            return saved
          })
        }).catch(function() {
          self.$message.warning('模板已保存到本机，云端保存失败，其他设备暂无法同步')
          self.closeAfterSave = false
          return null
        })
      },
      // 保存按钮：已有模板（编辑打开）直接覆盖；新建弹保存弹层
      saveTemplate() {
        var self = this
        // 发货模版：同样先弹「保存模板」（只需模板名称），确认后走统一模板适配器保存
        if (this.templateType === 'delivery') {
          if (!this.selectedAttrs.length) {
            this.$message.warning('请先选择要显示的内容')
            return
          }
          this.newTemplateName = (this.deliveryTemplate && this.deliveryTemplate.name) || '发货单模板'
          this.templateCategoryId = null
          this.saveModalVisible = true
          return
        }
        // 标签模板必须绑定业务数据源，否则模板无法解析数据
        if (!this.provider) {
          this.$message.warning('请先选择业务数据源')
          return
        }
        var exist = this.selectedTemplateId ? this.templates.find(function(t) { return t.id === self.selectedTemplateId }) : null
        if (exist) {
          // 覆盖原有模板：保留 id/名称/类型/品类归属，仅更新内容
          var idx = this.templates.indexOf(exist)
          var saved = this.buildTplPayload(exist)
          this.templates.splice(idx, 1, saved)
          this.persistTemplate(saved, '模板已覆盖保存')
          return
        }
        // 新建模板：弹保存弹层
        this.newTemplateName = ''
        this.templateCategoryId = null
        this.saveModalVisible = true
      },
      // 发货模版保存：通过适配器写入统一模板表（businessType=shipping.delivery）
      saveDeliveryTemplate(name) {
        var self = this
        if (!this.selectedAttrs.length) {
          this.$message.warning('请先选择要显示的内容')
          return
        }
        var base = this.deliveryTemplate || {}
        var tpl = Object.assign({}, base, {
          name: name || base.name || '发货单模板',
          businessType: DELIVERY_BUSINESS_TYPE,
          provider: DELIVERY_BUSINESS_TYPE,
          selectedAttrs: JSON.parse(JSON.stringify(this.selectedAttrs)),
          attrStyles: JSON.parse(JSON.stringify(this.attrStyles)),
          attrLabels: JSON.parse(JSON.stringify(this.attrLabels)),
          labelWidth: this.labelWidth,
          labelHeight: this.labelHeight
        })
        persistDeliveryTemplate(tpl).then(function(res) {
          if (res && res.code === 200) {
            if (res.data != null) {
              tpl.serverId = res.data
              tpl.id = res.data
            }
            self.deliveryTemplate = tpl
            self.$message.success('发货模版已保存')
            self.markClean()
            if (self.closeAfterSave) {
              self.closeAfterSave = false
              self.visible = false
            }
          } else {
            self.$message.error((res && res.data) || '发货模版保存失败')
          }
        }).catch(function() {
          self.$message.error('发货模版保存失败，请检查网络')
        })
      },
      // 另存为：无论是否已有模板，都弹保存弹层存为新模板
      saveAsTemplate() {
        // 类目回填：沿用当前模板的类目，但仅限二级类目（旧模板可能是一级类目）
        var self = this
        var tpl = this.currentTemplate
        var catId = tpl && tpl.type === 'default' ? tpl.categoryId : null
        var valid = catId && this.categoryList.some(function(c) { return String(c.value) === String(catId) })
        this.templateCategoryId = valid ? catId : null
        this.saveModalVisible = true
      },
      // 保存弹层确认：不选商品类型=普通模板，选了=该类型的通用模板
      confirmSaveTemplate() {
        if (!this.newTemplateName) {
          this.$message.warning('请输入模板名称')
          return
        }
        // 发货模版：走统一的保存入口，仅使用模板名称
        if (this.templateType === 'delivery') {
          this.saveModalVisible = false
          this.saveDeliveryTemplate(this.newTemplateName)
          return
        }
        if (!this.provider) {
          this.$message.warning('请先选择业务数据源')
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
        this.persistTemplate(tpl, '模板已保存')
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
            // 恢复该属性对应的独立编码元素
            var codeKey = CODE_KEY_PREFIX + key
            if (tpl.attrStyles[codeKey]) {
              self.$set(self.attrStyles, codeKey, JSON.parse(JSON.stringify(tpl.attrStyles[codeKey])))
            }
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
        // 旧模板兼容：文字与条码合并在同一个元素里的，拆成独立的编码元素
        this.attrStyles = migrateCodeStyles(this.attrStyles, this.allAttrs, this.attrLabels)
        if (tpl.labelWidth) this.labelWidth = tpl.labelWidth
        if (tpl.labelHeight) this.labelHeight = tpl.labelHeight
        this.currentAttrKey = null
        // 恢复模板绑定的数据源，并按适配器加载预览数据（不访问业务数据库）
        this.provider = tpl.provider || ''
        var provider = this.provider
        var that = this
        if (provider) {
          this.applyProviderData(provider).then(function() {
            that.$nextTick(function() { that.markClean() })
          })
        }
        // 打开模板后视为干净状态，关闭时不再提示保存
        this.$nextTick(function() { that.markClean() })
        this.resetHistory()
      },
      // 删除当前正在编辑的模板（需确认，避免误删）
      handleDeleteCurrentTemplate() {
        var self = this
        var tpl = this.currentTemplate
        if (!tpl) return
        this.$confirm({
          title: '删除模板',
          content: '确定删除模板「' + tpl.name + '」吗？删除后不可恢复。',
          okText: '删除',
          okType: 'danger',
          cancelText: '取消',
          zIndex: 3100,
          onOk: function() {
            self.deleteTemplate()
          }
        })
      },
      deleteTemplate() {
        if (!this.selectedTemplateId) return
        var self = this
        var tpl = this.currentTemplate
        this.templates = this.templates.filter(function(t) { return t.id !== self.selectedTemplateId })
        deleteLabelTemplate(tpl).then(function(res) {
          if (!res || res.code !== 200) {
            self.$message.warning('模板已从本机移除，云端删除失败，其他设备可能仍可见')
          }
        }).catch(function() {
          self.$message.warning('模板已从本机移除，云端删除失败，其他设备可能仍可见')
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
        // 附带新版 v3 结构，预览模块按 elements 直接渲染
        tpl.schema = legacyToSchema(tpl)
        this.$refs.labelPrintModal.open(this.record, this.sku, this.categoryTree, tpl)
      }
    },
    beforeDestroy() {
      this.unbindDragEvents()
      this.unbindMarqueeEvents()
      if (this._labelScrollResize && typeof window !== 'undefined') {
        window.removeEventListener('resize', this._labelScrollResize)
        this._labelScrollResize = null
      }
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
  /* 左栏纵向滚动，面板按内容高度排列；否则多个 height:100% 的面板会互相挤压，后面的面板被裁掉 */
  .le-side {
    overflow-y: auto;
  }
  .le-side > .label-edit-panel {
    height: auto;
    overflow: visible;
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
  .label-current-template {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 2px;
    margin-bottom: 8px;
    color: #333;
    font-size: 13px;
    font-weight: bold;
  }
  .label-current-template-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .label-current-template-del {
    flex: none;
    margin-left: 8px;
    font-weight: normal;
    color: #ff4d4f;
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
    margin: -12px -12px 8px -12px;
    padding: 6px 12px;
    background: #fafafa;
    border-bottom: 1px solid #e8e8e8;
    position: sticky;
    top: 0;
    z-index: 10;
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
    padding: 32px 32px 24px 32px;
    background: #f0f2f5;
    border: 1px solid #e8e8e8;
    border-radius: 4px;
    display: flex;
    align-items: flex-start;
  }
  /* 无溢出时 flex 直接居中 */
  .label-preview-scroll--fitting {
    justify-content: center;
  }
  /* 有溢出时用 flex-start + scrollLeft 居中（配合 label-scroll 模块） */
  .label-preview-scroll--scrolling {
    justify-content: flex-start;
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
  /* 条码 / 二维码：独立元素，只渲染图形 */
  .label-preview-item.is-code {
    align-items: flex-start;
    overflow: visible;
    white-space: normal;
    padding: 0;
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
  /* 商品明细表：按商品名称分行显示 */
  .label-preview-item.is-table {
    white-space: normal;
    overflow: visible;
    padding: 0;
    line-height: 1.4;
    display: block;
  }
  .le-table {
    width: 100%;
    font-size: 12px;
    white-space: nowrap;
  }
  .le-table-head {
    display: flex;
    font-weight: bold;
    border-bottom: 1px solid #333;
    padding-bottom: 2px;
    margin-bottom: 2px;
  }
  .le-table-row {
    display: flex;
    padding: 2px 0;
  }
  .le-col-name {
    flex: 1;
    min-width: 100px;
  }
  .le-col-qty {
    width: 50px;
    text-align: center;
    flex: none;
  }
  .le-col-spec {
    width: 140px;
    flex: none;
    text-align: right;
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
