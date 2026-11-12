<template>
  <div class="lem-page" :class="{ 'lem-page--embedded': embedded }">
    <!-- 标准顶栏 -->
    <mobile-top-bar @back="handleBack">
      <template #title>
        <div class="lem-title-area">
          <div class="lem-title">{{ currentTitle }}</div>
          <div class="lem-subtitle" @click="openSizeEditor">
            {{ labelWidthMm }}mm × {{ labelHeightMm }}mm
            <a-icon type="edit" class="lem-size-edit-icon" />
          </div>
        </div>
      </template>
      <template #actions>
        <a-icon type="fullscreen" class="lem-top-icon" @click="toggleZoom" />
        <a-icon type="more" class="lem-top-icon" @click="showMore = !showMore" />
      </template>
    </mobile-top-bar>

    <!-- 对齐排列工具条（固定在顶栏下方，全部图标按钮） -->
    <div class="lem-align-bar">
      <div class="lem-align-btns">
        <a-button size="small" icon="undo" title="撤销" :disabled="historyIndex <= 0" @click="undo" />
        <a-button size="small" icon="redo" title="恢复" :disabled="historyIndex >= history.length - 1" @click="redo" />
        <span class="lem-align-divider"></span>
        <a-button size="small" icon="align-left" title="左对齐" :disabled="selectedKeys.length < 2" @click="alignSelected('left')" />
        <a-button size="small" icon="align-center" title="水平居中" :disabled="selectedKeys.length < 2" @click="alignSelected('center')" />
        <a-button size="small" icon="align-right" title="右对齐" :disabled="selectedKeys.length < 2" @click="alignSelected('right')" />
        <a-button size="small" icon="vertical-align-top" title="顶对齐" :disabled="selectedKeys.length < 2" @click="alignSelected('top')" />
        <a-button size="small" icon="vertical-align-middle" title="垂直居中" :disabled="selectedKeys.length < 2" @click="alignSelected('middle')" />
        <a-button size="small" icon="vertical-align-bottom" title="底对齐" :disabled="selectedKeys.length < 2" @click="alignSelected('bottom')" />
        <span class="lem-align-divider"></span>
        <a-button size="small" title="左右均分" :disabled="selectedKeys.length < 3" @click="distributeSelected('h')">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
            <rect x="1" y="4" width="3" height="8"/>
            <rect x="6.5" y="4" width="3" height="8"/>
            <rect x="12" y="4" width="3" height="8"/>
          </svg>
        </a-button>
        <a-button size="small" title="上下均分" :disabled="selectedKeys.length < 3" @click="distributeSelected('v')">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
            <rect x="4" y="1" width="8" height="3"/>
            <rect x="4" y="6.5" width="8" height="3"/>
            <rect x="4" y="12" width="8" height="3"/>
          </svg>
        </a-button>
        <a-button size="small" icon="close" title="取消选择" :disabled="!selectedKeys.length" @click="clearSelection" />
      </div>
    </div>

    <!-- 预览区 -->
    <div class="lem-preview-wrap" ref="previewWrap" :class="wrapScrolling ? 'lem-preview-wrap--scrolling' : 'lem-preview-wrap--fitting'">
      <div class="lem-scroll-spacer" :style="{ width: scrollSpacer + 'px' }"></div>
      <div
        class="lem-label"
        ref="labelCanvas"
        :style="[labelStyle, { touchAction: multiSelectMode ? 'none' : 'auto' }]"
        @pointerdown.self.prevent="startMarquee"
      >
        <div
          v-for="(key, idx) in selectedAttrs"
          :key="key"
          class="lem-elem"
          :data-key="key"
          :class="{ 'lem-elem-selected': isSelected(key), 'lem-elem-code': isCode(key) }"
          :style="getElementStyle(key)"
          @pointerdown.stop.prevent="startPointerDrag(key, $event, 'move')"
        >
          <template v-if="isCode(key)">
            <img v-if="codeImages[key]" :src="codeImages[key]" class="lem-code-img" />
            <span v-else class="lem-code-placeholder">编码</span>
          </template>
          <template v-else-if="key === 'dn_table'">
            <div class="lem-mini-table" :style="{ width: getTablePreviewWidth(key) + 'px' }">
              <div class="lem-mini-row lem-mini-head">
                <span class="lem-mini-col-name">商品名称</span>
                <span class="lem-mini-col-qty">数量</span>
                <span class="lem-mini-col-spec">规格</span>
              </div>
              <div class="lem-mini-row" v-for="n in 3" :key="n">
                <span class="lem-mini-col-name">{{ '示例商品' + n }}</span>
                <span class="lem-mini-col-qty">{{ n }}</span>
                <span class="lem-mini-col-spec">白色 / XL</span>
              </div>
            </div>
          </template>
          <template v-else-if="isGoodsTable(key)">
            <div class="lem-mini-table lem-goods-table" :style="goodsTableStyle">
              <div class="lem-goods-row lem-mini-head" :style="{ height: (goodsTableData ? goodsTableData.headHeight : 0) + 'px' }">
                <span v-for="(col, ci) in goodsTableColumns" :key="ci" class="lem-goods-cell"
                      :style="{ width: getGoodsColumnWidth(ci) + 'px' }">{{ col }}</span>
              </div>
              <div class="lem-goods-row" v-for="(row, ri) in goodsTablePreviewRows" :key="ri"
                   :style="{ height: getGoodsRowHeight(ri) + 'px' }">
                <span v-for="(cell, ci) in row" :key="ci" class="lem-goods-cell"
                      :style="{ width: getGoodsColumnWidth(ci) + 'px' }">{{ cell }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="isDetailColumn(key)">
            <div class="lem-col-stack">
              <div class="lem-col-line" v-for="n in 2" :key="n">{{ getDetailSample(key, n) }}</div>
            </div>
          </template>
          <template v-else>
            <span class="lem-elem-label" v-if="attrLabels[key]">{{ attrLabels[key] }}：</span>
            <span class="lem-elem-value">{{ getAttrValue(key) }}</span>
          </template>
          <span
            v-if="currentAttrKey === key"
            class="lem-resize-handle"
            :style="handleAntiScaleStyle"
            @pointerdown.stop.prevent="startPointerDrag(key, $event, 'resize')"
          ></span>
          <span
            v-if="currentAttrKey === key && !isCode(key)"
            class="lem-delete-btn"
            :style="deleteBtnAntiScaleStyle"
            @click.stop="removeAttr(currentAttrKey)"
          >×</span>
        </div>
        <!-- 框选矩形 -->
        <div v-if="marquee.visible" class="lem-marquee" :style="marqueeStyle"></div>
      </div>
      <div class="lem-scroll-spacer" :style="{ width: scrollSpacer + 'px' }"></div>
    </div>

    <!-- 工具栏：始终固定在屏幕底边，按钮左右滑动，下滑/上滑按钮固定在右侧 -->
    <div class="lem-toolbar">
      <div class="lem-toolbar-scroll">
        <div class="lem-tool-btn" @click="openElementPanel">
          <a-icon type="appstore" />
          <span>元素</span>
        </div>
        <div class="lem-tool-btn" @click="deleteSelected">
          <a-icon type="delete" />
          <span>删除</span>
        </div>
        <div class="lem-tool-btn" @click="zoomIn">
          <a-icon type="zoom-in" />
          <span>放大</span>
        </div>
        <div class="lem-tool-btn" @click="zoomOut">
          <a-icon type="zoom-out" />
          <span>缩小</span>
        </div>
        <div class="lem-tool-btn" @click="saveTemplate">
          <a-icon type="save" />
          <span>保存</span>
        </div>
        <div class="lem-tool-btn" @click="handlePrint">
          <a-icon type="printer" />
          <span>打印</span>
        </div>
        <div class="lem-tool-btn" @click="openSettings">
          <a-icon type="setting" />
          <span>设置</span>
        </div>
      </div>
      <div class="lem-toolbar-move" :class="{ 'lem-toolbar-move-locked': panelDown }" @click="togglePanelMode">
        <a-icon :type="panelDown ? 'up' : 'down'" />
        <span>{{ panelDown ? '上滑' : '下滑' }}</span>
      </div>
    </div>

    <!-- 属性编辑面板：上滑状态为向上浮出的弹层；下滑状态不弹层 -->
    <div class="lem-panel" v-if="selectedKeys.length && !panelDown">
      <div class="lem-panel-multi-tip" v-if="selectedKeys.length > 1">已选 {{ selectedKeys.length }} 项，样式修改应用到全部选中元素</div>
      <!-- 属性名 / 文本内容（单选时显示） -->
      <div class="lem-text-input-row" v-if="selectedKeys.length === 1 && showFontFields">
        <a-input
          :value="currentLabel"
          :placeholder="isCustomAttr(selectedKeys[0]) ? '自定义文本内容' : '属性名（留空不显示）'"
          class="lem-text-input"
          @input="updateCurrentLabel"
        />
      </div>
      <div class="lem-text-input-row" v-if="selectedKeys.length === 1 && showFontFields && !isCustomAttr(selectedKeys[0]) && !isGoodsKey(selectedKeys[0])">
        <a-input
          :value="currentValue"
          placeholder="文本内容"
          class="lem-text-input"
          @input="updateCurrentValue"
        />
        <a-button size="small" class="lem-ocr-btn">OCR</a-button>
      </div>
      <!-- 商品信息：组合值由勾选的字段拼接（如只勾商品名称+规格值2） -->
      <div class="lem-goods-field-row" v-if="selectedKeys.length === 1 && isGoodsKey(selectedKeys[0])">
        <span class="lem-style-label">组合字段</span>
        <a-checkbox-group :value="goodsFields" class="lem-goods-field-group" @change="onGoodsFieldsChange">
          <a-checkbox v-for="f in goodsFieldOptions" :key="f.key" :value="f.key">{{ f.label }}</a-checkbox>
        </a-checkbox-group>
      </div>
      <!-- 样式编辑：多选时不一致的项显示为空，修改后应用到全部 -->
      <div class="lem-style-grid">
        <div class="lem-style-item" v-if="showFontFields">
          <span class="lem-style-label">字号</span>
          <a-input-number :value="getMultiStyle('fontSize')" @change="v => setMultiStyle('fontSize', v)" :min="6" :max="72" size="small" style="width:70px" placeholder="多值" />
        </div>
        <div class="lem-style-item" v-if="showFontFields">
          <span class="lem-style-label">粗细</span>
          <a-select :value="getMultiStyle('fontWeight')" @change="v => setMultiStyle('fontWeight', v)" size="small" style="width:70px" placeholder="多值">
            <a-select-option value="normal">正常</a-select-option>
            <a-select-option value="bold">粗体</a-select-option>
          </a-select>
        </div>
        <div class="lem-style-item" v-if="showFontFields">
          <span class="lem-style-label">字距</span>
          <a-input-number :value="getMultiStyle('letterSpacing')" @change="v => setMultiStyle('letterSpacing', v)" :min="0" :max="30" :step="0.5" size="small" style="width:70px" placeholder="多值" />
        </div>
        <div class="lem-style-item" v-if="showFontFields">
          <span class="lem-style-label">行距</span>
          <a-input-number :value="getMultiStyle('lineHeight')" @change="v => setMultiStyle('lineHeight', v)" :min="0.8" :max="4" :step="0.1" size="small" style="width:70px" placeholder="多值" />
        </div>
        <div class="lem-style-item">
          <span class="lem-style-label">X</span>
          <a-input-number :value="getMultiStyle('x')" @change="v => setMultiStyle('x', v)" size="small" style="width:70px" />
        </div>
        <div class="lem-style-item">
          <span class="lem-style-label">Y</span>
          <a-input-number :value="getMultiStyle('y')" @change="v => setMultiStyle('y', v)" size="small" style="width:70px" />
        </div>
        <div class="lem-style-item">
          <span class="lem-style-label">宽</span>
          <a-input-number :value="getMultiStyle('width')" @change="v => setMultiStyle('width', v)" :min="10" :max="2000" size="small" style="width:70px" placeholder="自动" />
        </div>
        <div class="lem-style-item">
          <span class="lem-style-label">高</span>
          <a-input-number :value="getMultiStyle('height')" @change="v => setMultiStyle('height', v)" :min="10" :max="1000" size="small" style="width:70px" placeholder="自动" />
        </div>
      </div>
    </div>

    <!-- 元素选择弹层：全宽，从底边栏上沿向上弹出，受弹出锁定按钮控制 -->
    <div v-if="elementPanelVisible && !panelDown" class="lem-sheet-mask" @click="elementPanelVisible = false">
      <div class="lem-sheet" @click.stop>
        <div class="lem-sheet-head">
          <span class="lem-sheet-title">元素</span>
        </div>
        <div class="lem-sheet-body">
          <!-- 数据表选择：字段随所选表加载，逻辑与 Web 端「业务数据源」一致 -->
          <div v-if="providerList.length" class="lem-provider">
            <span class="lem-provider-label">数据表</span>
            <a-select
              :value="provider"
              size="small"
              style="flex:1"
              placeholder="选择数据表"
              @change="onProviderChange"
            >
              <a-select-option v-for="p in providerList" :key="p.provider" :value="p.provider">{{ p.name }}</a-select-option>
            </a-select>
          </div>
          <div v-for="group in attrGroups" :key="group.name" class="lem-elem-group">
            <div class="lem-elem-group-title">{{ group.name }}</div>
            <div
              v-for="attr in group.attrs"
              :key="attr.key"
              class="lem-elem-item"
              :class="{ 'lem-elem-item-active': selectedAttrs.indexOf(attr.key) > -1 }"
              @click="toggleAttr(attr.key)"
            >
              <span class="lem-elem-key">{{ attr.key }}</span>
              <span class="lem-elem-cn">{{ attr.comment || attr.label }}</span>
              <a-icon
                :type="selectedAttrs.indexOf(attr.key) > -1 ? 'check-circle' : 'plus-circle'"
                :theme="selectedAttrs.indexOf(attr.key) > -1 ? 'filled' : 'outlined'"
                :style="{ color: selectedAttrs.indexOf(attr.key) > -1 ? '#1890ff' : '#bfbfbf' }"
              />
            </div>
          </div>
          <a-button type="dashed" block class="lem-add-custom" @click="addCustomLabel">
            <a-icon type="plus" />添加自定义标签
          </a-button>
        </div>
      </div>
    </div>

    <!-- 尺寸编辑弹窗 -->
    <a-modal
      :visible="sizeEditorVisible"
      title="标签尺寸"
      :zIndex="3010"
      @ok="confirmSizeEdit"
      @cancel="sizeEditorVisible = false"
      ok-text="确定"
      cancel-text="取消"
    >
      <div class="lem-size-editor">
        <div class="lem-size-row">
          <span class="lem-size-label">宽度</span>
          <a-input-number
            v-model="editWidthMm"
            :min="10"
            :max="500"
            style="width: 120px"
            addon-after="mm"
          />
        </div>
        <div class="lem-size-row">
          <span class="lem-size-label">高度</span>
          <a-input-number
            v-model="editHeightMm"
            :min="10"
            :max="800"
            style="width: 120px"
            addon-after="mm"
          />
        </div>
        <div class="lem-size-presets">
          <div class="lem-size-preset-item" @click="applyPreset(60, 40)">60×40</div>
          <div class="lem-size-preset-item" @click="applyPreset(40, 30)">40×30</div>
          <div class="lem-size-preset-item" @click="applyPreset(80, 50)">80×50</div>
          <div class="lem-size-preset-item" @click="applyPreset(100, 60)">100×60</div>
          <div class="lem-size-preset-item" @click="applyPreset(148, 212)">148×212</div>
          <div class="lem-size-preset-item" @click="applyPreset(210, 297)">A4</div>
        </div>
      </div>
    </a-modal>

    <!-- 保存模板弹窗：与 PC 端一致（模板名称 + 商品类型） -->
    <a-modal
      title="保存模板"
      :visible="saveModalVisible"
      :width="'92%'"
      :maskClosable="false"
      :zIndex="3020"
      class="lem-save-sheet"
      wrapClassName="lem-save-sheet-wrap"
      ok-text="保存"
      cancel-text="取消"
      @ok="confirmSaveTemplate"
      @cancel="saveModalVisible = false"
    >
      <div class="lem-save-field">
        <div class="lem-save-label">模板名称</div>
        <a-input v-model="newTemplateName" placeholder="请输入模板名称" allowClear @pressEnter="confirmSaveTemplate" />
      </div>
      <div class="lem-save-field">
        <div class="lem-save-label-row">
          <span class="lem-save-label">超长文本</span>
          <span class="lem-save-switch-value">{{ overflowWrapEnabled ? '自动折行' : '省略号裁切' }}</span>
          <a-switch v-model="overflowWrapEnabled" />
        </div>
        <div class="lem-save-hint">{{ overflowWrapEnabled ? '文字超出标签宽度自动折行，下方内容依次下移' : '文字超出标签宽度保持单行，末尾补省略号后裁切' }}</div>
      </div>
      <div v-if="templateType === 'label'" class="lem-save-field">
        <div class="lem-save-label">商品类型</div>
        <a-select v-model="templateCategoryId" style="width:100%" placeholder="不选择则保存为普通模板" allowClear>
          <a-select-option v-for="cat in categoryList" :key="cat.value" :value="cat.value">{{ cat.label }}</a-select-option>
        </a-select>
        <div class="lem-save-hint">仅二级类目可作为模板类型（一级类目不可保存模板）；一个类目只保留一个模板</div>
        <div v-if="conflictTemplate" class="lem-save-conflict">该类型已有模板「{{ conflictTemplate.name }}」，保存后将覆盖</div>
        <div class="lem-save-hint">不选类型保存为普通模板（仅当前商品可用）；选择类型保存为该类型的通用模板</div>
      </div>
    </a-modal>
    <!-- 打印标签弹层（不再跳转打印页面） -->
    <label-print-modal ref="labelPrintModal" />
  </div>
</template>

<script>
  import MobileTopBar from '@/components/mobile/MobileTopBar'
  import LabelPrintModal from './LabelPrintModal'
  import { buildLabelAttrs } from '@/views/material/utils/materialDisplay'
  import { isCodeKey, codeSourceKey, renderCodeDataUrl, CODE_KEY_PREFIX } from '@/views/material/utils/labelCode'
  import { fetchLabelTemplates, saveLabelTemplate } from '@/views/material/utils/labelTemplateStore'
  import { fetchDeliveryTemplate, saveDeliveryTemplate as persistDeliveryTemplate } from '@/views/bill/utils/deliveryNoteTemplateStore'
  import { fetchProviders, fetchProviderFields, fetchLabelData } from '@/views/material/utils/labelProviderApi'
  import { getAction, postAction } from '@/api/manage'
  import { queryMaterialCategoryTreeList } from '@/api/api'
  import { computeSpacer, centerScroll, setupLabelScroll, computeFitScale } from '@/utils/label-scroll'
  import { GOODS_TABLE_KEY, GOODS_KEY, GOODS_TABLE_COLUMNS, GOODS_FIELD_OPTIONS, resolveGoodsLayout } from '@/views/material/utils/goodsLabelLayout'

  const PX_PER_MM = 3.779528
  // 标签默认缩放：预览区四周留白（与设计稿一致，左右各 20px）
  const LABEL_FIT_PADDING = 20
  // 默认缩放上限与放大按钮上限保持一致
  const LABEL_MAX_ZOOM = 3
  const LABEL_MIN_ZOOM = 0.3

  /**
   * 模板类型配置表
   * 所有类型差异集中在此，新增类型只需加一行（如以后的 receipt/小票）
   *
   * storage: 'localTemplates' → 用 fetchLabelTemplates / saveLabelTemplate（多模板列表）
   *           'platformConfig' → 用 getUiConfig / saveUiConfig（单 key 覆盖写）
   */
  const TYPE_CONFIG = {
    label: {
      title: '标签编辑',
      defaultWidth: 320,
      defaultHeight: 240,
      attrsBuilder: 'label',
      storage: 'localTemplates',
      defaultKeys: ['name', 'spec', 'price', 'barcode'], // 仅 applyDefaultLayout 使用
      successMsg: '标签模版已保存'
    },
    delivery: {
      title: '发货模版编辑',
      defaultWidth: 560,
      defaultHeight: 800,
      attrsBuilder: 'delivery',
      storage: 'deliveryTemplate',
      defaultKeys: ['dn_title', 'dn_no', 'dn_date', 'dn_member', 'dn_phone', 'dn_table', 'dn_qty_total', 'dn_amount_total', 'dn_remark', 'dn_footer'],
      successMsg: '发货模版已保存'
    },
    receipt: {
      title: '小票编辑',
      defaultWidth: 380,
      defaultHeight: 600,
      attrsBuilder: 'receipt',
      storage: 'platformConfig',
      platformConfigKey: 'receipt_layout',
      defaultKeys: [],
      successMsg: '小票模版已保存'
    },
    member: {
      title: '会员标签编辑',
      defaultWidth: 320,
      defaultHeight: 240,
      attrsBuilder: 'member',
      storage: 'platformConfig',
      platformConfigKey: 'member_layout',
      defaultKeys: ['contacts', 'telephone', 'address'],
      successMsg: '会员标签模版已保存'
    },
    address: {
      title: '地址打印',
      defaultWidth: 320,
      defaultHeight: 240,
      attrsBuilder: 'address',
      storage: 'platformConfig',
      platformConfigKey: 'address_layout',
      defaultKeys: ['contacts', 'telephone', 'address'],
      successMsg: '地址打印模版已保存'
    }
  }

  export default {
    name: 'LabelEditMobile',
    components: { MobileTopBar, LabelPrintModal },
    props: {
      // 以标准弹层方式内嵌在打印页时使用，不再依赖路由跳转
      embedded: { type: Boolean, default: false },
      initialType: { type: String, default: '' },
      initialBusinessType: { type: String, default: '' },
      initialTemplateId: { type: [String, Number], default: null }
    },
    data() {
      return {
        templateType: 'label',
        record: {},
        sku: {},
        // 业务数据表（数据源）：与 Web 端一致，字段由后端适配器/数据表提供
        providerList: [],
        provider: '',
        businessType: '',
        allAttrs: [],
        selectedAttrs: [],
        attrStyles: {},
        attrLabels: {},
        // 商品组合（商品信息）显示哪些字段：默认全部，可在属性面板按需勾选
        goodsFields: ['name', 'spec1', 'spec2', 'spec3'],
        currentAttrKey: null,
        labelWidth: 320,
        labelHeight: 240,
        // 超长文本处理：wrap=自动折行并下移下方内容，ellipsis=单行省略号裁切
        overflowMode: 'wrap',
        zoom: 1,
        showMore: false,
        elementPanelVisible: false,
        customCounter: 0,
        // true = 下滑状态：点击元素不向上弹层；false = 上滑状态：面板向上浮出
        panelDown: false,
        templates: [],
        selectedTemplateId: null,
        // 保存模板弹窗（与 PC 端一致：模板名称 + 商品类型）
        saveModalVisible: false,
        newTemplateName: '',
        templateCategoryId: null,
        categoryTree: [],
        // 发货单模板（统一模板表记录）
        deliveryTemplate: null,
        codeImages: {},
        history: [],
        historyIndex: -1,
        dragging: { active: false, mode: '', key: '', startX: 0, startY: 0, origX: 0, origY: 0, origW: 0, origH: 0, moved: false },
        selectedKeys: [],
        multiSelectMode: false,
        suppressClick: false,
        marquee: { active: false, visible: false, startX: 0, startY: 0, x: 0, y: 0, w: 0, h: 0 },
        sizeEditorVisible: false,
        editWidthMm: 148,
        editHeightMm: 212,
        // wrap 是否溢出需要滚动：true → flex-start + scrollLeft 居中；false → center 直接居中
        wrapScrolling: false
      }
    },
    computed: {
      currentTitle() {
        var cfg = TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
        return cfg.title
      },
      // 超长文本处理开关：开=自动折行，关=省略号裁切
      overflowWrapEnabled: {
        get: function() { return this.overflowMode !== 'ellipsis' },
        set: function(val) { this.overflowMode = val ? 'wrap' : 'ellipsis' }
      },
      // 仅二级类目可作为模板类型（一级类目不可保存模板）
      categoryList() {
        return this.flattenCategoryTree(this.categoryTree, 2)
      },
      // 全部类目（仅用于类目名称回显，包含一级类目）
      allCategoryList() {
        return this.flattenCategoryTree(this.categoryTree)
      },
      // 所选类目下已存在的其它模板：一个类目只保留一个模板，保存时覆盖
      conflictTemplate() {
        var id = this.templateCategoryId
        if (!id) return null
        var self = this
        return this.templates.find(function(t) {
          return t.type === 'default' && String(t.categoryId) === String(id) && t.id !== self.selectedTemplateId
        }) || null
      },
      labelWidthMm() {
        return Math.round(this.labelWidth / PX_PER_MM)
      },
      labelHeightMm() {
        return Math.round(this.labelHeight / PX_PER_MM)
      },
      labelStyle() {
        return {
          width: this.labelWidth + 'px',
          height: this.labelHeight + 'px',
          transform: 'scale(' + this.zoom + ')',
          transformOrigin: 'center top'
        }
      },
      // 标签居中 + 对称滑动：从 label-scroll 模块计算单侧 spacer 宽度
      scrollSpacer() {
        return computeSpacer(this.labelWidth, this.zoom)
      },
      // 商品明细行数据（地址识别页带回的多行商品）
      goodsRowsData() {
        return resolveGoodsLayout({
          selectedAttrs: this.selectedAttrs,
          attrStyles: this.attrStyles,
          values: this.record || {},
          paperWidth: this.labelWidth,
          goodsLabel: this.attrLabels.goods || '',
          goodsFields: this.goodsFields
        })
      },
      // 商品组合可勾选的字段（商品名称 / 规格值1-3）
      goodsFieldOptions() {
        return GOODS_FIELD_OPTIONS
      },
      // 商品明细表：列宽/行高/总高度（按商品行数动态计算）
      goodsTableData() {
        return this.goodsRowsData.table
      },
      goodsTableColumns() {
        return GOODS_TABLE_COLUMNS
      },
      // 表格预览行：没有商品数据时给一行占位，便于调整样式
      goodsTablePreviewRows() {
        var table = this.goodsTableData
        if (table && table.rows.length) return table.rows.map(function(row) { return row.cells })
        return [['商品名称', '规格值1', '规格值2', '规格值3']]
      },
      goodsTableStyle() {
        var style = this.attrStyles[GOODS_TABLE_KEY] || {}
        return {
          width: (this.goodsTableData ? this.goodsTableData.width : 0) + 'px',
          fontSize: (style.fontSize || 12) + 'px',
          lineHeight: style.lineHeight || 1.2
        }
      },
      // handle / delete 反缩放：同时抵消尺寸 AND 偏移量
      // 外层 label scale(zoom) 会把子元素的尺寸和偏移都放大 zoom 倍
      // 所以 layout 层的 right/bottom/top/left 也要除以 zoom
      handleAntiScaleStyle() {
        var inv = 1 / this.zoom
        return {
          right: (-10 * inv) + 'px',
          bottom: (-10 * inv) + 'px',
          transform: 'scale(' + inv + ')',
          transformOrigin: 'bottom right'
        }
      },
      deleteBtnAntiScaleStyle() {
        var inv = 1 / this.zoom
        return {
          top: (-10 * inv) + 'px',
          left: (-10 * inv) + 'px',
          transform: 'scale(' + inv + ')',
          transformOrigin: 'top left'
        }
      },
      // 按 group 分组展示全部字段（已选中/未选中都列出，勾选式切换）
      attrGroups() {
        var groups = []
        var map = {}
        this.allAttrs.forEach(function(a) {
          var name = a.group || '可用字段'
          if (!map[name]) {
            map[name] = { name: name, attrs: [] }
            groups.push(map[name])
          }
          map[name].attrs.push(a)
        })
        return groups
      },
      currentValue() {
        if (!this.currentAttrKey) return ''
        var attr = this.allAttrs.find(a => a.key === this.currentAttrKey)
        return attr ? attr.value : ''
      },
      currentLabel() {
        if (!this.currentAttrKey) return ''
        return this.attrLabels[this.currentAttrKey] || ''
      },
      // 字体类字段是否显示：只要选中项中含非编码元素就显示
      showFontFields() {
        if (!this.selectedKeys.length) return false
        return !this.selectedKeys.every(k => this.isCode(k))
      },
      marqueeStyle() {
        return {
          left: this.marquee.x + 'px',
          top: this.marquee.y + 'px',
          width: this.marquee.w + 'px',
          height: this.marquee.h + 'px'
        }
      }
    },
    mounted() {
      this.initFromRoute()
      // 标签居中 + 对称滑动：用 label-scroll 模块
      setupLabelScroll(this, 'previewWrap', 'labelWidth', 'zoom', 'wrapScrolling')
    },
    beforeDestroy() {
      this.unbindPointerEvents()
      this.unbindMarqueeEvents()
      if (this._labelScrollResize && typeof window !== 'undefined') {
        window.removeEventListener('resize', this._labelScrollResize)
        this._labelScrollResize = null
      }
    },
    methods: {
      initFromRoute() {
        var type = this.initialType || this.$route.query.type || 'label'
        var cfg = TYPE_CONFIG[type] || TYPE_CONFIG.label
        // 从打印预览页点「编辑」返回时，恢复当时的商品数据和模板，避免编辑页字段全空
        var context = null
        try { context = JSON.parse(sessionStorage.getItem('jsh.labelEdit.context') || 'null') } catch (e) { context = null }
        if (context) {
          try { sessionStorage.removeItem('jsh.labelEdit.context') } catch (e) { /* 忽略 */ }
          if (context.record) this.record = context.record
          if (context.sku) this.sku = context.sku
        }
        this.templateType = type
        // 业务标识：来源页优先，其次按类型兜底（商品/发货单/会员）
        this.businessType = this.initialBusinessType || this.$route.query.businessType || (context && context.businessType) ||
          (type === 'delivery' ? 'delivery' : (type === 'label' ? 'product' : (type === 'member' ? 'member' : '')))
        this.labelWidth = cfg.defaultWidth
        this.labelHeight = cfg.defaultHeight
        this.deliveryTemplate = null
        this.allAttrs = this.buildAttrs(cfg.attrsBuilder)
        var loaded
        if (context && context.template && context.template.selectedAttrs) {
          this.applyTemplateSnapshot(context.template, cfg)
          this.selectedTemplateId = context.template.id || null
          // 列表仍要加载，供后续切换/保存，但不再用列表里的模板覆盖刚恢复的布局
          loaded = this.loadTemplate(cfg, true)
        } else {
          loaded = this.loadTemplate(cfg)
        }
        // 模板布局恢复后再拉数据表，避免数据源字段覆盖模板里的已选字段
        var self = this
        Promise.resolve(loaded).then(function() {
          self.initProvider(context)
        })
      },
      /** 数据表下拉：加载列表，并按来源业务自动选中对应表 */
      initProvider(context) {
        var self = this
        this.providerList = []
        if (!this.businessType) return
        fetchProviders(this.businessType).then(function(list) {
          self.providerList = list || []
          if (!self.providerList.length) return
          var preferred = (context && context.provider) ||
            (context && context.template && context.template.provider) ||
            (self.templateType === 'delivery' ? 'sales.delivery'
              : (self.templateType === 'member' ? 'table.jsh_supplier' : 'product.label'))
          var matched = self.providerList.filter(function(p) { return p.provider === preferred })[0]
          self.provider = matched ? matched.provider : self.providerList[0].provider
          self.applyProviderData(self.provider, false)
        })
      },
      /** 手动切换数据表：按新表重建字段，并清理新表中不存在的已选字段 */
      onProviderChange(provider) {
        this.provider = provider || ''
        this.applyProviderData(this.provider, true)
      },
      /**
       * 按数据表加载字段清单（有单据 id 时同时取真实值）
       * rebuild=true 表示用户主动切换：清理新表里不存在的已选字段（自定义标签始终保留）
       */
      applyProviderData(provider, rebuild) {
        var self = this
        if (!provider) {
          this.allAttrs = this.buildAttrs((TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label).attrsBuilder)
          return Promise.resolve()
        }
        var documentId = this.record && this.record.id
        return Promise.all([
          fetchProviderFields(provider),
          fetchLabelData(provider, documentId, null, this.businessType)
        ]).then(function(result) {
          if (self.provider !== provider) return
          var fields = result[0] || []
          var values = result[1] || {}
          if (!fields.length) return
          var list = fields.filter(function(field) {
            // 数组明细交给模板的循环渲染，不作为文本字段
            return field.type !== 'array'
          }).map(function(field) {
            return {
              key: field.key,
              label: field.label || field.key,
              comment: field.comment || '',
              value: values[field.key] == null ? '' : String(values[field.key]),
              type: field.type || 'text',
              isCustom: false,
              group: self.templateType === 'delivery' ? '销售出库'
                : (self.templateType === 'member' ? '会员信息' : '可用字段')
            }
          })
          var exists = {}
          list.forEach(function(attr) { exists[attr.key] = true })
          // 自定义标签、以及模板中已选但新表没有的字段保留，避免存量模板字段被清空
          self.allAttrs.forEach(function(attr) {
            if (exists[attr.key]) return
            if (attr.isCustom || self.selectedAttrs.indexOf(attr.key) > -1) list.push(attr)
          })
          self.allAttrs = list
          fields.forEach(function(field) {
            if (self.attrLabels[field.key] === undefined) {
              self.$set(self.attrLabels, field.key, field.label || field.key)
            }
          })
          if (rebuild) {
            var keys = list.map(function(attr) { return attr.key })
            self.selectedAttrs = self.selectedAttrs.filter(function(key) { return keys.indexOf(key) > -1 })
          }
        })
      },
      /** 根据 attrsBuilder 名称构建字段列表，新增类型只需在此加分支 */
      buildAttrs(builder) {
        switch (builder) {
          case 'delivery': return this.buildDeliveryAttrs()
          case 'receipt': return this.buildReceiptAttrs()
          case 'member': return this.buildMemberAttrs()
          case 'address': return this.buildAddressAttrs()
          case 'label':
          default: return buildLabelAttrs(this.record, this.sku)
        }
      },
      /** 会员标签默认字段（key 与会员信息表 jsh_supplier 列名保持一致） */
      buildMemberAttrs() {
        var m = this.record || {}
        var mk = function(key, label, value) {
          return {
            key: key,
            label: label,
            value: value == null ? '' : String(value),
            isCustom: false,
            group: '会员信息'
          }
        }
        return [
          mk('contacts', '联系人', m.contacts),
          mk('telephone', '电话', m.telephone),
          mk('phone_num', '手机', m.phoneNum),
          mk('address', '联系地址', m.address),
          mk('supplier', '会员卡号', m.supplier),
          mk('email', '电子邮箱', m.email),
          mk('wechat_account', '微信账户', m.wechatAccount),
          mk('douyin_account', '抖音账户', m.douyinAccount),
          mk('alipay_account', '支付宝账户', m.alipayAccount),
          mk('other_account', '其它账户', m.otherAccount),
          mk('advance_in', '预付款', m.advanceIn),
          mk('description', '备注', m.description),
          mk('enabled', '状态', m.enabled === false ? '禁用' : '启用')
        ]
      },
      /** 地址打印字段：只取地址识别页面输入框的数据（联系人/电话/账号/平台/备注/地址/商品） */
      buildAddressAttrs() {
        var m = this.record || {}
        var mk = function(key, label, value) {
          return {
            key: key,
            label: label,
            value: value == null ? '' : String(value),
            isCustom: false,
            group: '地址信息'
          }
        }
        return [
          mk('contacts', '联系人', m.contacts),
          mk('telephone', '电话', m.telephone),
          mk('account', '账号', m.account),
          mk('platform', '平台', m.platform),
          mk('remark', '备注', m.remark),
          mk('address', '地址', m.address),
          // 商品信息：每个商品一行（名称 + 规格值1/2/3）
          mk('goods', '商品信息', m.goods),
          // 商品明细表：按商品行数动态排版
          {
            key: GOODS_TABLE_KEY,
            label: '商品明细表',
            value: '',
            isCustom: false,
            group: '地址信息'
          }
        ]
      },
      /** 统一的模板加载入口，根据 storage 类型分发 */
      loadTemplate(cfg, skipApply) {
        var self = this
        if (cfg.storage === 'localTemplates') {
          var templateId = this.initialTemplateId || this.$route.query.templateId
          return fetchLabelTemplates().then(function(templates) {
            self.templates = templates || []
            if (skipApply) return
            var target = templateId ? templates.find(t => t.id === templateId) : (templates[0] || null)
            if (target && target.selectedAttrs) {
              self.applyTemplateSnapshot(target, cfg)
              self.selectedTemplateId = target.id
            }
          })
        } else if (cfg.storage === 'deliveryTemplate') {
          // 发货单模板：统一模板表适配器（兼容旧 platformConfig）
          return fetchDeliveryTemplate().then(function(tpl) {
            if (skipApply) {
              if (tpl && tpl.selectedAttrs && tpl.selectedAttrs.length) self.deliveryTemplate = tpl
              return
            }
            if (tpl && tpl.selectedAttrs && tpl.selectedAttrs.length) {
              self.deliveryTemplate = tpl
              self.applyTemplateSnapshot(tpl, cfg)
            } else {
              self.applyDefaultLayout(cfg)
            }
          }).catch(function() {
            if (skipApply) return
            self.applyDefaultLayout(cfg)
          })
        } else if (cfg.storage === 'platformConfig') {
          var tryLoad = function(key, isFallback) {
            return getAction('/platformConfig/getUiConfig', { key: key }).then(function(res) {
              if (skipApply) return
              var data = (res && res.code === 200) ? res.data : null
              var snap = self.parseConfigData(data)
              if (snap && snap.selectedAttrs && snap.selectedAttrs.length) {
                self.applyTemplateSnapshot(snap, cfg)
                // 从旧 key 迁移过来的话，顺便写一份到新 key
                if (isFallback && cfg.platformConfigKey && cfg.platformConfigKey !== key) {
                  self.postSnapshot(cfg.platformConfigKey, snap)
                }
              } else if (!isFallback && cfg.legacyPlatformConfigKey) {
                return tryLoad(cfg.legacyPlatformConfigKey, true)
              } else {
                self.applyDefaultLayout(cfg)
              }
            }).catch(function() {
              if (!isFallback && cfg.legacyPlatformConfigKey) {
                return tryLoad(cfg.legacyPlatformConfigKey, true)
              }
              self.applyDefaultLayout(cfg)
            })
          }
          return tryLoad(cfg.platformConfigKey, false)
        }
      },
      /** 把持久化快照应用到当前编辑状态 */
      applyTemplateSnapshot(snap, cfg) {
        this.selectedAttrs = JSON.parse(JSON.stringify(snap.selectedAttrs))
        this.attrStyles = JSON.parse(JSON.stringify(snap.attrStyles || {}))
        this.attrLabels = JSON.parse(JSON.stringify(snap.attrLabels || {}))
        // 旧模板没有 goodsFields：保留当前默认（全部字段）
        if (Array.isArray(snap.goodsFields)) this.goodsFields = snap.goodsFields.slice()
        if (snap.width) this.labelWidth = snap.width
        if (snap.height) this.labelHeight = snap.height
        this.overflowMode = snap.overflowMode === 'ellipsis' ? 'ellipsis' : 'wrap'
        this.restoreMissingAttrs(snap.selectedAttrs)
        this.fitLabelAfterLoad()
      },
      /** getUiConfig 返回值可能是字符串或已解析对象，统一处理 */
      parseConfigData(data) {
        var cfg = data
        if (typeof data === 'string') {
          try { cfg = JSON.parse(data) } catch (e) { cfg = null }
        }
        if (cfg && typeof cfg === 'object' && cfg.config != null) cfg = cfg.config
        return cfg
      },
      /** 通用 platformConfig 写入 */
      postSnapshot(key, snap) {
        return postAction('/platformConfig/saveUiConfig', {
          key: key,
          value: JSON.stringify(snap)
        })
      },
      // 零售出库单据全部可显示字段，group 仅用于「元素」弹窗分组展示
      buildDeliveryAttrs() {
        return [
          { key: 'dn_title', label: '发货单标题', value: '发货单', group: '单据头' },
          { key: 'dn_no', label: '单号', value: 'LS261105001', group: '单据头' },
          { key: 'dn_date', label: '单据日期', value: '2026-11-05 10:30', group: '单据头' },
          { key: 'dn_member', label: '会员', value: '张三', group: '单据头' },
          { key: 'dn_receiver', label: '收货人', value: '张三', group: '单据头' },
          { key: 'dn_phone', label: '联系电话', value: '13800138000', group: '单据头' },
          { key: 'dn_address', label: '收货地址', value: '北京市朝阳区xx路xx号', group: '单据头' },
          { key: 'dn_depot', label: '仓库', value: '主仓库', group: '单据头' },
          { key: 'dn_paytype', label: '收款类型', value: '现金', group: '单据头' },
          { key: 'dn_account', label: '收款账户', value: '现金账户', group: '单据头' },
          { key: 'dn_operator', label: '操作员', value: 'admin', group: '单据头' },
          { key: 'dn_salesman', label: '业务员', value: '李四', group: '单据头' },
          { key: 'dn_remark', label: '备注', value: '请尽快发货', group: '单据头' },
          { key: 'dn_attach', label: '附件', value: '无', group: '单据头' },
          { key: 'dn_table', label: '商品明细表', value: '商品明细表', group: '商品明细' },
          { key: 'dn_col_name', label: '商品名（逐行）', value: '示例商品', group: '商品明细' },
          { key: 'dn_col_spec', label: '规格（逐行）', value: '白色/XL', group: '商品明细' },
          { key: 'dn_col_qty', label: '数量（逐行）', value: '2', group: '商品明细' },
          { key: 'dn_col_price', label: '单价（逐行）', value: '280.00', group: '商品明细' },
          { key: 'dn_col_amount', label: '金额（逐行）', value: '280.00', group: '商品明细' },
          { key: 'dn_qty_total', label: '数量合计', value: '1', group: '金额合计' },
          { key: 'dn_amount_total', label: '金额合计', value: '¥280.00', group: '金额合计' },
          { key: 'dn_discount', label: '优惠折扣', value: '10', group: '金额合计' },
          { key: 'dn_discount_amount', label: '优惠金额', value: '28.00', group: '金额合计' },
          { key: 'dn_real_amount', label: '应收金额', value: '252.00', group: '金额合计' },
          { key: 'dn_paid_total', label: '收款金额', value: '252.00', group: '金额合计' },
          { key: 'dn_back_amount', label: '找零', value: '0.00', group: '金额合计' },
          { key: 'dn_status', label: '单据状态', value: '已审核', group: '其它' },
          { key: 'dn_footer', label: '落款', value: '华夏家纺', group: '其它' }
        ]
      },
      /** 小票字段占位（后续按业务需求补充） */
      buildReceiptAttrs() {
        return [
          { key: 'rc_title', label: '小票标题', value: '零售小票', group: '单据头' },
          { key: 'rc_no', label: '单号', value: 'LS261105001', group: '单据头' },
          { key: 'rc_date', label: '日期', value: '2026-11-05 10:30', group: '单据头' },
          { key: 'rc_member', label: '会员', value: '张三', group: '单据头' },
          { key: 'rc_table', label: '商品明细表', value: '商品明细表', group: '商品明细' },
          { key: 'rc_qty_total', label: '数量合计', value: '1', group: '金额合计' },
          { key: 'rc_amount_total', label: '金额合计', value: '¥280.00', group: '金额合计' },
          { key: 'rc_remark', label: '备注', value: '谢谢惠顾', group: '其它' },
          { key: 'rc_footer', label: '落款', value: '华夏家纺', group: '其它' }
        ]
      },
      /** 通用默认布局：从 cfg.defaultKeys 挑字段，从上到下依次排 */
      applyDefaultLayout(cfg) {
        cfg = cfg || TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
        var DEFAULT_KEYS = cfg.defaultKeys || []
        var styles = {}
        var labels = {}
        var selected = []
        var y = 10
        var self = this
        this.allAttrs.forEach(function(a) {
          if (DEFAULT_KEYS.indexOf(a.key) === -1) return
          selected.push(a.key)
          var isTitle = /title$/.test(a.key)
          var isTable = /table$/.test(a.key)
          styles[a.key] = {
            x: 20, y: y, width: 0, height: 0,
            fontSize: isTitle ? 18 : 12,
            fontWeight: isTitle ? 'bold' : 'normal',
            letterSpacing: 0, lineHeight: 1.2
          }
          labels[a.key] = a.label
          if (isTable) y += 160
          else if (isTitle) y += 32
          else y += 24
        })
        // 小票没配 defaultKeys 时，至少选标题+单号
        if (!selected.length && self.templateType === 'receipt') {
          self.allAttrs.forEach(function(a) {
            if (a.key === 'rc_title' || a.key === 'rc_no') {
              selected.push(a.key)
              styles[a.key] = { x: 20, y: y, width: 0, height: 0, fontSize: 14, fontWeight: 'normal', letterSpacing: 0, lineHeight: 1.2 }
              labels[a.key] = a.label
              y += 28
            }
          })
        }
        this.selectedAttrs = selected
        this.attrStyles = styles
        this.attrLabels = labels
        this.fitLabelAfterLoad()
      },
      // 模板里的自定义标签不在固定字段清单中，需补回 allAttrs 才能在编辑器预览/编辑
      restoreMissingAttrs(keys) {
        var self = this
        var exists = {}
        this.allAttrs.forEach(function(a) { exists[a.key] = true })
        ;(keys || []).forEach(function(key) {
          if (exists[key]) return
          var label = self.attrLabels[key] || '自定义'
          self.allAttrs.push({ key: key, label: label, value: label, isCustom: true, group: '自定义' })
          exists[key] = true
        })
      },
      getAttrValue(key) {
        // 商品信息按勾选的组合字段拼接显示（与打印一致）
        if (key === GOODS_KEY && this.goodsRowsData.goodsText) return this.goodsRowsData.goodsText
        var attr = this.allAttrs.find(function(a) { return a.key === key })
        return attr ? attr.value : ''
      },
      getElementStyle(key) {
        // 商品明细表/商品组合会按商品行数变高，使用运行时重排后的坐标与高度
        var resolved = this.goodsRowsData.styles[key]
        var style = resolved || this.attrStyles[key] || {}
        var result = {
          left: (style.x || 0) + 'px',
          top: (style.y || 0) + 'px',
          fontSize: (style.fontSize || 12) + 'px',
          fontWeight: style.fontWeight || 'normal',
          letterSpacing: (style.letterSpacing || 0) + 'px',
          lineHeight: style.lineHeight || 1.2
        }
        if (key === GOODS_TABLE_KEY) {
          var table = this.goodsTableData
          result.width = ((table && table.width) || style.width || 0) + 'px'
          result.padding = '0'
          result.whiteSpace = 'normal'
          return result
        }
        // 指定宽度时文字按框内宽度自动换行（与打印一致）
        if (style.width) {
          result.width = style.width + 'px'
          result.whiteSpace = 'pre-wrap'
          result.wordBreak = 'break-all'
          result.overflowWrap = 'anywhere'
          // 块级排版：属性名与属性值连续折行，而不是各自成块
          result.display = 'block'
          result.padding = '0'
        }
        // 指定高度时超出部分裁掉（与打印一致：只打印框内完整行）
        if (style.height) {
          result.height = style.height + 'px'
          result.overflow = 'hidden'
        }
        return result
      },
      isCode(key) {
        return isCodeKey(key)
      },
      isGoodsTable(key) {
        return key === GOODS_TABLE_KEY
      },
      isGoodsKey(key) {
        return key === GOODS_KEY
      },
      // 勾选/取消商品组合字段：顺序固定为 商品名称 → 规格值1 → 规格值2 → 规格值3
      onGoodsFieldsChange(values) {
        var list = Array.isArray(values) ? values : []
        this.goodsFields = GOODS_FIELD_OPTIONS
          .map(function(f) { return f.key })
          .filter(function(key) { return list.indexOf(key) > -1 })
      },
      getGoodsColumnWidth(index) {
        var table = this.goodsTableData
        return table && table.widths[index] ? table.widths[index] : 0
      },
      getGoodsRowHeight(index) {
        var table = this.goodsTableData
        if (table && table.rows[index]) return table.rows[index].height
        var style = this.attrStyles[GOODS_TABLE_KEY] || {}
        return Math.round((style.fontSize || 12) * (style.lineHeight || 1.2)) + 4
      },
      isSelected(key) {
        return this.selectedKeys.indexOf(key) > -1
      },
      selectElement(key) {
        // 拖动结束后紧随的 click 不应改变选择状态
        if (this.suppressClick) {
          this.suppressClick = false
          return
        }
        // 点击一次选中、再次点击取消（toggle）
        var idx = this.selectedKeys.indexOf(key)
        if (idx > -1) {
          this.selectedKeys.splice(idx, 1)
          if (this.currentAttrKey === key) {
            this.currentAttrKey = this.selectedKeys.length ? this.selectedKeys[this.selectedKeys.length - 1] : null
          }
        } else {
          // 如果之前已多选且点在另一个未选中项上 → 开始多选（保留已选中）
          if (this.selectedKeys.length > 0) {
            this.selectedKeys.push(key)
          } else {
            this.selectedKeys = [key]
          }
          this.currentAttrKey = key
        }
      },
      clearSelection() {
        this.selectedKeys = []
        this.currentAttrKey = null
      },
      addAttr(key) {
        if (this.selectedAttrs.indexOf(key) !== -1) return
        this.selectedAttrs.push(key)
        var idx = this.selectedAttrs.length
        this.$set(this.attrStyles, key, { x: 10, y: 10 + idx * 30, width: 0, height: 0, fontSize: 12, fontWeight: 'normal', letterSpacing: 0, lineHeight: 1.2 })
        var attr = this.allAttrs.find(function(a) { return a.key === key })
        if (attr) this.$set(this.attrLabels, key, attr.label)
        this.selectedKeys = [key]
        this.currentAttrKey = key
      },
      // 勾选式切换：已选中则移除，未选中则添加（弹窗保持打开，可连续操作）
      toggleAttr(key) {
        if (this.selectedAttrs.indexOf(key) > -1) {
          this.removeAttr(key)
        } else {
          this.addAttr(key)
        }
      },
      // 自定义标签：新增一个可自由填写文案的元素
      addCustomLabel() {
        this.customCounter = (this.customCounter || 0) + 1
        var key = 'custom_' + this.customCounter + '_' + Date.now()
        var attr = { key: key, label: '自定义', value: '自定义文本', isCustom: true, group: '自定义' }
        this.allAttrs.push(attr)
        this.addAttr(key)
      },
      isDetailColumn(key) {
        return ['dn_col_name', 'dn_col_spec', 'dn_col_qty', 'dn_col_price', 'dn_col_amount'].indexOf(key) > -1
      },
      isCustomAttr(key) {
        var attr = this.allAttrs.find(function(a) { return a.key === key })
        return !!(attr && attr.isCustom)
      },
      // 明细列在编辑器中的两行示例值，与渲染页行高保持一致
      getDetailSample(key, n) {
        var samples = {
          dn_col_name: n === 1 ? '示例商品一' : '示例商品二',
          dn_col_spec: '白色/XL',
          dn_col_qty: '2',
          dn_col_price: '280.00',
          dn_col_amount: '280.00'
        }
        return samples[key] || ''
      },
      removeAttr(key) {
        var idx = this.selectedAttrs.indexOf(key)
        if (idx !== -1) this.selectedAttrs.splice(idx, 1)
        this.$delete(this.attrStyles, key)
        this.$delete(this.attrLabels, key)
        var sIdx = this.selectedKeys.indexOf(key)
        if (sIdx !== -1) this.selectedKeys.splice(sIdx, 1)
        if (this.currentAttrKey === key) {
          this.currentAttrKey = this.selectedKeys.length ? this.selectedKeys[this.selectedKeys.length - 1] : null
        }
      },
      updateCurrentLabel(e) {
        var val = typeof e === 'string' ? e : (e && e.target ? e.target.value : e)
        if (this.currentAttrKey) this.$set(this.attrLabels, this.currentAttrKey, val)
      },
      // 多选批量读取：全部一致返回值，否则 null（输入框显示为空）
      getMultiStyle(field) {
        if (!this.selectedKeys.length) return null
        var first = this.attrStyles[this.selectedKeys[0]]
        var val = first ? first[field] : undefined
        for (var i = 1; i < this.selectedKeys.length; i++) {
          var s = this.attrStyles[this.selectedKeys[i]]
          if ((s ? s[field] : undefined) !== val) return null
        }
        return val === undefined ? null : val
      },
      // 多选批量写入：应用到全部选中元素（跳过空值，避免破坏定位样式）
      setMultiStyle(field, val) {
        if (val === null || val === undefined || val === '') return
        var self = this
        this.selectedKeys.forEach(function(key) {
          if (!self.attrStyles[key]) self.$set(self.attrStyles, key, {})
          self.$set(self.attrStyles[key], field, val)
        })
        this.pushSnapshot()
      },
      // 读取元素实际渲染尺寸（offsetWidth/Height 不受 transform 缩放影响）
      getItemSize(key) {
        var el = document.querySelector('.lem-elem[data-key="' + key + '"]')
        return { w: el ? el.offsetWidth : 0, h: el ? el.offsetHeight : 0 }
      },
      // 对齐：以选中对象外接矩形为基准 left/center/right/top/middle/bottom
      alignSelected(mode) {
        var self = this
        var keys = this.selectedKeys
        if (keys.length < 2) return
        var rects = keys.map(function(key) {
          var s = self.attrStyles[key] || {}
          var size = self.getItemSize(key)
          return { key: key, x: s.x || 0, y: s.y || 0, w: size.w, h: size.h }
        })
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
      // 均分：h 左右均分，v 上下均分（首尾不动，中间等间距）
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
      updateCurrentValue(e) {
        var val = typeof e === 'string' ? e : (e && e.target ? e.target.value : e)
        var attr = this.allAttrs.find(a => a.key === this.currentAttrKey)
        if (attr) attr.value = val
      },
      openElementPanel() {
        if (this.panelDown) {
          this.$message.warning('已锁定弹出层，先点右下角「上滑」解锁')
          return
        }
        // 再点一次「元素」即关闭
        this.elementPanelVisible = !this.elementPanelVisible
      },
      deleteSelected() {
        var keys = this.selectedKeys.slice()
        if (!keys.length && this.currentAttrKey) keys = [this.currentAttrKey]
        if (!keys.length) return
        var self = this
        keys.forEach(function(k) { self.removeAttr(k) })
      },
      toggleMultiSelect() {
        this.multiSelectMode = !this.multiSelectMode
        // 退出多选时收敛为单选；进入多选保留当前选中项作为起点
        if (!this.multiSelectMode) {
          this.selectedKeys = this.currentAttrKey ? [this.currentAttrKey] : []
        }
      },
      // 下滑/上滑：只控制属性面板是否向上弹层，工具栏始终固定在底边
      togglePanelMode() {
        this.panelDown = !this.panelDown
      },
      copyElement() {
        if (!this.currentAttrKey) return
        var srcKey = this.currentAttrKey
        var newKey = srcKey + '_copy_' + Date.now()
        var srcAttr = this.allAttrs.find(function(a) { return a.key === srcKey })
        if (srcAttr) {
          this.allAttrs.push({ key: newKey, label: srcAttr.label, value: srcAttr.value, isCustom: true })
        }
        this.selectedAttrs.push(newKey)
        this.$set(this.attrStyles, newKey, JSON.parse(JSON.stringify(this.attrStyles[srcKey] || {})))
        if (this.attrStyles[newKey]) this.attrStyles[newKey].x += 10
        this.$set(this.attrLabels, newKey, this.attrLabels[srcKey] || '')
        this.selectedKeys = [newKey]
        this.currentAttrKey = newKey
        this.pushSnapshot()
      },
      pushSnapshot() {
        var snap = JSON.stringify({
          attrStyles: this.attrStyles,
          attrLabels: this.attrLabels,
          labelWidth: this.labelWidth,
          labelHeight: this.labelHeight
        })
        if (this.historyIndex >= 0 && this.history[this.historyIndex] === snap) return
        // 丢弃 redo 分支
        if (this.historyIndex < this.history.length - 1) {
          this.history = this.history.slice(0, this.historyIndex + 1)
        }
        if (this.history.length > 50) this.history.shift()
        this.historyIndex = this.history.push(snap) - 1
      },
      undo() {
        if (this.historyIndex <= 0) { this.$message.info('没有可撤销的操作'); return }
        this.historyIndex--
        var snap = JSON.parse(this.history[this.historyIndex])
        this.attrStyles = JSON.parse(JSON.stringify(snap.attrStyles || {}))
        this.attrLabels = JSON.parse(JSON.stringify(snap.attrLabels || {}))
        this.labelWidth = snap.labelWidth != null ? snap.labelWidth : this.labelWidth
        this.labelHeight = snap.labelHeight != null ? snap.labelHeight : this.labelHeight
      },
      redo() {
        if (this.historyIndex >= this.history.length - 1) { this.$message.info('没有可恢复的操作'); return }
        this.historyIndex++
        var snap = JSON.parse(this.history[this.historyIndex])
        this.attrStyles = JSON.parse(JSON.stringify(snap.attrStyles || {}))
        this.attrLabels = JSON.parse(JSON.stringify(snap.attrLabels || {}))
        this.labelWidth = snap.labelWidth != null ? snap.labelWidth : this.labelWidth
        this.labelHeight = snap.labelHeight != null ? snap.labelHeight : this.labelHeight
      },
      zoomIn() {
        this.zoom = Math.min(LABEL_MAX_ZOOM, this.zoom + 0.1)
      },
      zoomOut() {
        this.zoom = Math.max(LABEL_MIN_ZOOM, this.zoom - 0.1)
      },
      // 全屏图标：1:1 与「按窗口适配」之间切换
      toggleZoom() {
        if (Math.abs(this.zoom - 1) < 0.01) this.fitLabelToView()
        else this.zoom = 1
      },
      /**
       * 按预览区尺寸等比缩放标签（复用 label-scroll 的 computeFitScale）
       * 默认放大到适应屏幕宽度，四周留白 20px；超高标签按高度收敛，避免超出可视区
       * 缩放后由 setupLabelScroll 负责居中/滑动
       */
      fitLabelToView() {
        var wrap = this.$refs.previewWrap
        if (!wrap || !this.labelWidth || !this.labelHeight) return
        var wrapW = wrap.clientWidth || window.innerWidth
        var wrapH = wrap.clientHeight || window.innerHeight
        var scale = computeFitScale(this.labelWidth, this.labelHeight, wrapW, wrapH, LABEL_FIT_PADDING, LABEL_MAX_ZOOM)
        this.zoom = Math.min(LABEL_MAX_ZOOM, Math.max(LABEL_MIN_ZOOM, Math.round(scale * 100) / 100))
      },
      // 模板加载完成后等一帧再适配，确保预览区已完成布局
      fitLabelAfterLoad() {
        var self = this
        this.$nextTick(function() {
          self.fitLabelToView()
        })
      },
      // 拖拽移动/缩放：统一用 Pointer Events + setPointerCapture，
      // 手指移出元素后事件仍持续派发到捕获元素，不依赖 touch passive 机制
      // 选中逻辑也在这里完成（彻底移除 click 事件避免时序冲突）
      startPointerDrag(key, e, mode) {
        var self = this
        // 记录 pointerdown 瞬间的快照，留到 pointerup 做 toggle 判断
        this._pointerDownKey = key
        this._selectedBeforeDown = this.selectedKeys.slice()
        this._prevFocus = this.currentAttrKey  // ← 记录 pointerdown 前的焦点
        // ⚠️ 不在这里改 currentAttrKey！焦点只能在 pointerup toggle 后决定
        var willMultiDrag = mode === 'move' && this.selectedKeys.length > 1
        // 长按 600ms：全选所有元素并整体拖动
        if (mode === 'move') {
          this._longPressTimer = setTimeout(function() {
            self._longPressTimer = null
            var allKeys = self.selectedAttrs.slice()
            self.selectedKeys = allKeys
            self.currentAttrKey = allKeys[0] || null
            self.startActualDrag(key, e, 'move', true)
          }, 600)
        }
        this.startActualDrag(key, e, mode, willMultiDrag)
      },
      // 测量元素文字本身的自然尺寸（不换行、不裁剪），用于限制缩放边框的最小值
      measureContentSize(key) {
        var el = document.querySelector('.lem-label .lem-elem[data-key="' + key + '"]')
        if (!el) return { width: 0, height: 0 }
        var clone = el.cloneNode(true)
        clone.style.position = 'absolute'
        clone.style.visibility = 'hidden'
        clone.style.left = '0'
        clone.style.top = '0'
        clone.style.width = 'auto'
        clone.style.height = 'auto'
        clone.style.whiteSpace = 'nowrap'
        clone.style.overflow = 'visible'
        clone.style.outline = 'none'
        var handles = clone.querySelectorAll('.lem-resize-handle, .lem-delete-btn')
        Array.prototype.forEach.call(handles, function(node) {
          if (node.parentNode) node.parentNode.removeChild(node)
        })
        el.parentNode.appendChild(clone)
        var size = { width: clone.offsetWidth, height: clone.offsetHeight }
        el.parentNode.removeChild(clone)
        return size
      },
      startActualDrag(key, e, mode, multiDrag) {
        var self = this
        var style = this.attrStyles[key] || {}
        // resize 模式下如果没有显式 width/height，从 DOM 实际尺寸除以 zoom 得到
        var origW = style.width || 0
        var origH = style.height || 0
        if ((mode === 'resize') && (!origW || !origH)) {
          var el = document.querySelector('.lem-elem[data-key="' + key + '"]')
          if (el) {
            var r = el.getBoundingClientRect()
            if (!origW) origW = Math.round(r.width / this.zoom)
            if (!origH) origH = Math.round(r.height / this.zoom)
          }
        }
        // 记录整体拖拽的所有元素起点
        var allKeys = multiDrag ? this.selectedKeys.slice() : [key]
        var dragOrigins = {}
        allKeys.forEach(function(k) {
          var s = self.attrStyles[k] || {}
          dragOrigins[k] = { x: s.x || 0, y: s.y || 0, width: s.width || 0, height: s.height || 0 }
        })
        // 缩放边框时的最小尺寸：不得小于文字本身占用的尺寸
        var minW = 12
        var minH = 12
        if (mode === 'resize') {
          var natural = this.measureContentSize(key)
          if (natural.width > 0) minW = natural.width
          if (natural.height > 0) minH = natural.height
        }
        this.dragging = {
          active: true,
          mode: mode,
          key: key,
          startX: e.clientX,
          startY: e.clientY,
          origX: style.x || 0,
          origY: style.y || 0,
          origW: origW,
          origH: origH,
          minW: minW,
          minH: minH,
          moved: false,
          multiDrag: multiDrag,
          dragOrigins: dragOrigins
        }
        try {
          if (e.currentTarget.setPointerCapture) {
            e.currentTarget.setPointerCapture(e.pointerId)
          }
        } catch (err) { /* 忽略捕获失败，仍有 document 级监听兜底 */ }
        document.addEventListener('pointermove', this.onPointerMove)
        document.addEventListener('pointerup', this.onPointerUp)
        document.addEventListener('pointercancel', this.onPointerUp)
      },
      unbindPointerEvents() {
        document.removeEventListener('pointermove', this.onPointerMove)
        document.removeEventListener('pointerup', this.onPointerUp)
        document.removeEventListener('pointercancel', this.onPointerUp)
      },
      onPointerMove(e) {
        // 有长按定时器 → 手指一动立即取消长按，视为普通拖动
        if (this._longPressTimer) {
          clearTimeout(this._longPressTimer)
          this._longPressTimer = null
        }
        if (!this.dragging.active) return
        var d = this.dragging
        var dx = e.clientX - d.startX
        var dy = e.clientY - d.startY
        if (!d.moved && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
          d.moved = true
        }
        if (d.mode === 'move') {
          if (d.multiDrag && d.dragOrigins) {
            // 多选整体移动（不限制边界，可拖到屏幕任意可视位置）
            var self2 = this
            Object.keys(d.dragOrigins).forEach(function(k) {
              var origin = d.dragOrigins[k]
              var newX = Math.round((origin.x || 0) + dx / self2.zoom)
              var newY = Math.round((origin.y || 0) + dy / self2.zoom)
              self2.$set(self2.attrStyles[k], 'x', newX)
              self2.$set(self2.attrStyles[k], 'y', newY)
            })
          } else {
            // 单个元素移动（不限制边界，文字标签超出屏幕时可拖到任意可视位置）
            var newX = d.origX + dx / this.zoom
            var newY = d.origY + dy / this.zoom
            this.$set(this.attrStyles[d.key], 'x', Math.round(newX))
            this.$set(this.attrStyles[d.key], 'y', Math.round(newY))
          }
        } else if (d.mode === 'resize') {
          // 调整文字框尺寸：不得小于文字本身大小，避免文字被压缩重叠
          var minW = d.minW || 12
          var minH = d.minH || 12
          var newW = Math.max(minW, Math.round(d.origW + dx / this.zoom))
          var newH = Math.max(minH, Math.round(d.origH + dy / this.zoom))
          this.$set(this.attrStyles[d.key], 'width', newW)
          this.$set(this.attrStyles[d.key], 'height', newH)
        }
      },
      onPointerUp() {
        if (this._longPressTimer) {
          clearTimeout(this._longPressTimer)
          this._longPressTimer = null
        }
        var prevKeys = this._selectedBeforeDown || []
        var prevFocus = this._prevFocus
        var pKey = this._pointerDownKey
        var didMove = this.dragging.moved
        if (this.dragging.active) {
          this.dragging.active = false
          this.dragging.moved = false
          this.pushSnapshot()
        }
        // 没移动 + 不是 resize 手柄操作 → 点击意图，执行 toggle
        if (!didMove && pKey && (!this.dragging.mode || this.dragging.mode === 'move')) {
          var idx = prevKeys.indexOf(pKey)
          if (idx > -1) {
            // 之前已选中 → 移除（点谁踢谁）
            var next = prevKeys.slice()
            next.splice(idx, 1)
            this.selectedKeys = next
            // 焦点逻辑：
            //   移除的是 prevFocus → 无焦点（不自动转给剩余元素）
            //   移除的是非焦点  → prevFocus 保持不变
            if (pKey === prevFocus) {
              this.currentAttrKey = null
            } else {
              this.currentAttrKey = prevFocus
            }
          } else {
            // 之前未选中 → 单选，不再累加到已有选中项
            this.selectedKeys = [pKey]
            this.currentAttrKey = pKey
          }
        }
        // 移动过 → 拖拽意图，恢复 pointerdown 前的焦点（如果有的话）
        // 注意 pointerdown 没改 currentAttrKey，所以这里不需要恢复
        this._pointerDownKey = null
        this._selectedBeforeDown = null
        this._prevFocus = null
        this.unbindPointerEvents()
      },
      // 空白处按住滑动框选：Pointer Events + setPointerCapture，与元素拖动同一套机制
      startMarquee(e) {
        var rect = this.$refs.labelCanvas.getBoundingClientRect()
        this.marquee = {
          active: true,
          visible: false,
          startX: (e.clientX - rect.left) / this.zoom,
          startY: (e.clientY - rect.top) / this.zoom,
          x: 0, y: 0, w: 0, h: 0
        }
        try {
          if (e.currentTarget.setPointerCapture) {
            e.currentTarget.setPointerCapture(e.pointerId)
          }
        } catch (err) { /* 忽略捕获失败，仍有 document 级监听兜底 */ }
        document.addEventListener('pointermove', this.onMarqueeMove)
        document.addEventListener('pointerup', this.onMarqueeUp)
        document.addEventListener('pointercancel', this.onMarqueeUp)
      },
      onMarqueeMove(e) {
        var m = this.marquee
        if (!m.active) return
        var rect = this.$refs.labelCanvas.getBoundingClientRect()
        var cx = (e.clientX - rect.left) / this.zoom
        var cy = (e.clientY - rect.top) / this.zoom
        var w = Math.abs(cx - m.startX)
        var h = Math.abs(cy - m.startY)
        if (!m.visible && (w > 6 || h > 6)) m.visible = true
        if (m.visible) {
          m.x = Math.round(Math.min(cx, m.startX))
          m.y = Math.round(Math.min(cy, m.startY))
          m.w = Math.round(w)
          m.h = Math.round(h)
        }
      },
      onMarqueeUp() {
        var m = this.marquee
        if (m.active) {
          m.active = false
          if (m.visible) {
            // 相交命中：与框选矩形有重叠的元素全部选中
            var hit = []
            var self = this
            this.selectedAttrs.forEach(function(key) {
              var s = self.attrStyles[key] || {}
              var size = self.getItemSize(key)
              var ex = s.x || 0
              var ey = s.y || 0
              var ew = s.width > 0 ? s.width : size.w
              var eh = s.height > 0 ? s.height : size.h
              if (ex < m.x + m.w && ex + ew > m.x && ey < m.y + m.h && ey + eh > m.y) {
                hit.push(key)
              }
            })
            this.selectedKeys = hit
            this.currentAttrKey = hit.length ? hit[hit.length - 1] : null
          } else {
            // 未形成框选 = 点击空白，清空选择
            this.selectedKeys = []
            this.currentAttrKey = null
          }
          m.visible = false
        }
        this.unbindMarqueeEvents()
      },
      unbindMarqueeEvents() {
        document.removeEventListener('pointermove', this.onMarqueeMove)
        document.removeEventListener('pointerup', this.onMarqueeUp)
        document.removeEventListener('pointercancel', this.onMarqueeUp)
      },
      // 明细表预览宽度：与渲染页规则一致（未手动调整宽度时占满画布剩余宽度）
      getTablePreviewWidth(key) {
        var style = this.attrStyles[key] || {}
        if (style.width > 0) return style.width
        return Math.max(60, this.labelWidth - (style.x || 0) - 10)
      },
      saveTemplate() {
        var cfg = TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
        var self = this
        // 统一保存逻辑：所有类型先弹「保存模板」，再按类型走各自的模板数据模块保存
        if (cfg.storage === 'localTemplates') {
          var exist = this.selectedTemplateId
            ? this.templates.find(function(t) { return t.id === self.selectedTemplateId })
            : null
          this.loadCategoryTree().then(function() {
            self.newTemplateName = exist ? (exist.name || '') : ''
            var catId = exist && exist.type === 'default' ? exist.categoryId : null
            var valid = catId && self.categoryList.some(function(c) { return String(c.value) === String(catId) })
            self.templateCategoryId = valid ? catId : null
            self.saveModalVisible = true
          })
          return
        }
        if (cfg.storage === 'deliveryTemplate') {
          this.newTemplateName = (this.deliveryTemplate && this.deliveryTemplate.name) || '发货单模板'
        } else {
          this.newTemplateName = cfg.title.replace('编辑', '') + '模板'
        }
        this.templateCategoryId = null
        this.saveModalVisible = true
      },
      /** 发货单/小票：使用统一模板数据保存（不涉及商品类型） */
      persistUnifiedTemplate(cfg, name) {
        var self = this
        var payload = {
          name: name,
          selectedAttrs: JSON.parse(JSON.stringify(this.selectedAttrs)),
          attrStyles: JSON.parse(JSON.stringify(this.attrStyles)),
          attrLabels: JSON.parse(JSON.stringify(this.attrLabels)),
          goodsFields: this.goodsFields.slice(),
          width: this.labelWidth,
          height: this.labelHeight,
          overflowMode: this.overflowMode
        }
        if (cfg.storage === 'deliveryTemplate') {
          var base = this.deliveryTemplate || {}
          var tpl = Object.assign({}, base, {
            name: name,
            businessType: 'shipping.delivery',
            provider: this.provider || 'shipping.delivery',
            selectedAttrs: payload.selectedAttrs,
            attrStyles: payload.attrStyles,
            attrLabels: payload.attrLabels,
            labelWidth: payload.width,
            labelHeight: payload.height,
            overflowMode: payload.overflowMode
          })
          persistDeliveryTemplate(tpl).then(function(res) {
            if (res && res.code === 200 && res.data != null) {
              tpl.serverId = res.data
              tpl.id = res.data
              self.deliveryTemplate = tpl
            }
            self.showSaveResult(res, cfg)
          }).catch(function() {
            self.$message.error(cfg.title + '保存失败，请检查网络')
          })
        } else if (cfg.storage === 'platformConfig') {
          // 单 key 覆盖写模式
          this.postSnapshot(cfg.platformConfigKey, payload).then(function(res) {
            self.showSaveResult(res, cfg)
          }).catch(function() {
            self.$message.error(cfg.title + '保存失败，请检查网络')
          })
        }
      },
      /** 加载商品类目树（保存模板弹窗的「商品类型」选项，仅二级类目可选） */
      loadCategoryTree() {
        var self = this
        if (this.categoryTree.length) return Promise.resolve(this.categoryTree)
        return queryMaterialCategoryTreeList({ id: '' }).then(function(res) {
          self.categoryTree = Array.isArray(res) ? res : []
          return self.categoryTree
        }).catch(function() {
          return []
        })
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
      getCategoryName(categoryId) {
        if (!categoryId) return ''
        var cat = this.allCategoryList.find(function(c) { return String(c.value) === String(categoryId) })
        return cat ? cat.label : ''
      },
      /** 收集当前编辑内容，组装模板数据对象（字段与 PC 端 buildTplPayload 一致） */
      buildTplPayload(tpl) {
        var self = this
        var customLabels = []
        this.allAttrs.forEach(function(attr) {
          if (attr.isCustom) {
            customLabels.push(JSON.parse(JSON.stringify(attr)))
          }
        })
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
          // 数据源取「数据表」下拉当前选中项，未选择时按商品标签兜底
          provider: this.provider || (tpl && tpl.provider) || 'product.label',
          businessType: this.provider || (tpl && tpl.businessType) || 'product.label',
          selectedAttrs: JSON.parse(JSON.stringify(this.selectedAttrs)),
          attrStyles: cleanStyles,
          attrLabels: cleanLabels,
          goodsFields: this.goodsFields.slice(),
          customLabels: customLabels,
          labelWidth: this.labelWidth,
          labelHeight: this.labelHeight,
          overflowMode: this.overflowMode
        })
      },
      /** 保存弹窗确认：不选商品类型=普通模板，选了=该类型的通用模板（同类型只保留一个） */
      confirmSaveTemplate() {
        var name = (this.newTemplateName || '').trim()
        if (!name) {
          this.$message.warning('请输入模板名称')
          return
        }
        var cfg = TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
        // 发货单/小票：同一套保存入口，按各自模板数据模块落地
        if (cfg.storage !== 'localTemplates') {
          this.saveModalVisible = false
          this.persistUnifiedTemplate(cfg, name)
          return
        }
        var self = this
        var isCategoryTpl = !!this.templateCategoryId
        var exist = this.selectedTemplateId
          ? this.templates.find(function(t) { return t.id === self.selectedTemplateId })
          : null
        var tpl = this.buildTplPayload({
          id: exist ? exist.id : 'tpl_' + Date.now(),
          serverId: exist ? exist.serverId : null,
          name: name,
          type: isCategoryTpl ? 'default' : 'normal',
          materialId: isCategoryTpl ? null : (exist ? exist.materialId : null),
          categoryId: isCategoryTpl ? this.templateCategoryId : null,
          categoryName: isCategoryTpl ? this.getCategoryName(this.templateCategoryId) : null
        })
        // 类型通用模板：同类型只保留一个，其余移除
        if (isCategoryTpl) {
          this.templates = this.templates.filter(function(t) {
            if (t.id === tpl.id) return true
            return !(t.type === 'default' && String(t.categoryId) === String(self.templateCategoryId))
          })
        }
        // 覆盖当前模板，或新增一条
        var idx = this.templates.findIndex(function(t) { return t.id === tpl.id })
        if (idx > -1) this.templates.splice(idx, 1, tpl)
        else this.templates.push(tpl)
        this.selectedTemplateId = tpl.id
        this.saveModalVisible = false
        this.persistTemplate(tpl, '模板已保存')
      },
      /** 保存单个模板到统一模板表，成功后从服务端刷新列表，拿到权威 id/version */
      persistTemplate(tpl, successMsg) {
        var self = this
        return saveLabelTemplate(tpl).then(function(res) {
          if (!res || res.code !== 200) {
            self.$message.warning('模板已保存到本机，云端保存失败，其他设备暂无法同步')
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
            return saved
          })
        }).catch(function() {
          self.$message.warning('模板已保存到本机，云端保存失败，其他设备暂无法同步')
          return null
        })
      },
      showSaveResult(res, cfg) {
        if (res && res.code === 200) {
          this.$message.success(cfg.successMsg)
        } else {
          this.$message.error((res && res.data) || (cfg.title + '保存失败'))
        }
      },
      handlePrint() {
        // 把当前编辑中的模板快照写入 sessionStorage，跳转打印页后恢复
        var snapshotValues = {}
        this.allAttrs.forEach(function(attr) {
          if (attr.key) snapshotValues[attr.key] = attr.value == null ? '' : attr.value
        })
        try {
          sessionStorage.setItem('jsh.labelEdit.snapshot', JSON.stringify({
            labelWidth: this.labelWidth,
            labelHeight: this.labelHeight,
            selectedAttrs: this.selectedAttrs,
            attrStyles: this.attrStyles,
            attrLabels: this.attrLabels,
            overflowMode: this.overflowMode,
            values: snapshotValues
          }))
        } catch (e) { /* 忽略 */ }
        // 标签模板可能有多个：带上当前模板标识，打印弹层从统一模板存储读取对应模板
        var templateId = ''
        var templateName = ''
        if (this.templateType === 'label') {
          var curId = this.selectedTemplateId
          var cur = curId
            ? this.templates.find(function(t) { return t.id === curId })
            : null
          templateId = cur ? (cur.serverId || cur.id) : ''
          templateName = cur ? cur.name : ''
        }
        // 当前页直接弹出打印层；编辑按钮不再跳转（已在编辑页）
        this.$refs.labelPrintModal.openByType(this.templateType || 'label', {
          templateId: templateId,
          name: templateName,
          directEntry: false
        })
      },
      openSettings() {
        this.$message.info('设置功能开发中')
      },
      handleBack() {
        // 弹层内嵌时关闭弹层，独立路由页时保持原来的返回行为
        if (this.embedded) {
          this.$emit('close')
          return
        }
        this.$router.back()
      },
      openSizeEditor() {
        this.editWidthMm = this.labelWidthMm
        this.editHeightMm = this.labelHeightMm
        this.sizeEditorVisible = true
      },
      confirmSizeEdit() {
        this.labelWidth = Math.round(this.editWidthMm * PX_PER_MM)
        this.labelHeight = Math.round(this.editHeightMm * PX_PER_MM)
        this.sizeEditorVisible = false
        // 尺寸变化后重新按窗口适配
        this.fitLabelAfterLoad()
      },
      applyPreset(wMm, hMm) {
        this.editWidthMm = wMm
        this.editHeightMm = hMm
      }
    }
  }
</script>

<style lang="scss" scoped>
.lem-page {
  display: flex;
  flex-direction: column;
  position: relative;
  height: 100vh;
  height: 100dvh;
  background: #f5f5f5;
  padding-bottom: calc(58px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 内嵌到打印弹层时以标准弹层形式覆盖全屏 */
.lem-page--embedded {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 3000;
}

.lem-title-area {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  text-align: center;
  line-height: 1.2;
}

.lem-title {
  font-size: 16px;
  font-weight: 400;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lem-subtitle {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 2px;
  white-space: nowrap;
}

.lem-top-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 49px;
  font-size: 20px;
  color: #fff;
}

.lem-top-icon:active {
  background: rgba(255, 255, 255, 0.2);
}

.lem-preview-wrap {
  flex: 1;
  overflow: auto;
  /* 与默认适配缩放的四周边距保持一致（20px） */
  padding: 20px 0;
  display: flex;
  align-items: flex-start;
}

/* 有溢出（需要滚动）时用 flex-start + spacer 配合 scrollLeft=max/2 居中 */
.lem-preview-wrap--scrolling {
  justify-content: flex-start;
}

/* 无溢出（fit）时直接用 center 让标签在视口中居中 */
.lem-preview-wrap--fitting {
  justify-content: center;
}

.lem-scroll-spacer {
  flex-shrink: 0;
  height: 1px;
}

.lem-label {
  position: relative;
  flex-shrink: 0;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  border-radius: 4px;
}

.lem-elem {
  position: absolute;
  cursor: move;
  /* 左右不留内边距：文字始终从元素 x 起始，与指定宽度的元素、打印位置保持一致 */
  padding: 2px 0;
  border: 1px solid transparent;
  white-space: nowrap;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
  z-index: 1;
}

.lem-elem-selected {
  outline: 1px solid #1890ff;
  outline-offset: 0;
  background: rgba(24, 144, 255, 0.05);
}

.lem-elem-label {
  color: #333;
}

.lem-elem-value {
  color: #333;
}

.lem-code-img {
  max-width: 100%;
  max-height: 100%;
}

.lem-code-placeholder {
  color: #999;
}

/* 明细表迷你预览：与渲染页结构/列宽比例一致，保证所见即所得 */
.lem-mini-table {
  font-size: 12px;
  line-height: 1.2;
  white-space: normal;
}

/* 商品明细表：列宽/行高由商品行数计算，行内折行不裁切 */
.lem-goods-row {
  display: flex;
  align-items: flex-start;
  padding: 0;
  border-top: 1px solid #eee;
  box-sizing: border-box;
  overflow: hidden;
}

.lem-goods-cell {
  flex: 0 0 auto;
  padding: 2px;
  box-sizing: border-box;
  word-break: break-all;
  overflow: hidden;
}

.lem-mini-row {
  display: flex;
  align-items: flex-start;
  padding: 5px 0;
  border-top: 1px solid #eee;
}

.lem-mini-head {
  border-top: none;
  color: #999;
}

.lem-mini-col-name {
  flex: 1 1 auto;
  min-width: 0;
  word-break: break-all;
  color: #333;
}

.lem-mini-col-qty {
  flex: 0 0 17%;
  color: #333;
  text-align: center;
}

.lem-mini-col-spec {
  flex: 0 0 30%;
  color: #333;
  text-align: right;
}

.lem-resize-handle {
  position: absolute;
  width: 20px;
  height: 20px;
  background: #1890ff;
  border-radius: 50%;
  cursor: nwse-resize;
  touch-action: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.lem-resize-handle::after {
  content: '';
  width: 7px;
  height: 7px;
  border-right: 1.5px solid #fff;
  border-bottom: 1.5px solid #fff;
  margin-top: -1px;
  margin-left: -1px;
}

.lem-delete-btn {
  position: absolute;
  width: 20px;
  height: 20px;
  background: #ff4d4f;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}

/* 框选矩形 */
.lem-marquee {
  position: absolute;
  border: 1px dashed #1890ff;
  background: rgba(24, 144, 255, 0.12);
  z-index: 30;
  pointer-events: none;
}

/* 对齐排列工具条（固定在顶栏下方，始终显示） */
.lem-align-bar {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  background: #fff;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  position: sticky;
  top: 0;
  z-index: 20;
}

.lem-align-bar::-webkit-scrollbar {
  display: none;
}

.lem-align-btns {
  display: flex;
  gap: 4px;
  align-items: center;
  flex-wrap: nowrap;
}

.lem-align-divider {
  width: 1px;
  height: 18px;
  background: #e8e8e8;
  margin: 0 6px;
  flex: none;
}

.lem-align-btns .ant-btn {
  flex: none;
}

/* 多选按钮激活态 */
.lem-tool-active,
.lem-tool-active span {
  color: #1890ff;
}

/* 保存模板弹窗 */
.lem-save-field {
  margin-bottom: 14px;
}

.lem-save-label {
  font-weight: 600;
  margin-bottom: 6px;
  color: #333;
}

.lem-save-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.lem-save-label-row .lem-save-label {
  margin-bottom: 0;
}

.lem-save-switch-value {
  flex: 1;
  text-align: right;
  margin-right: 10px;
  font-size: 13px;
  color: #666;
}

.lem-save-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: #999;
}

.lem-save-conflict {
  margin-top: 4px;
  font-size: 12px;
  color: #fa8c16;
}

.lem-toolbar {
  /* 始终固定在屏幕底边，点击元素不会改变其位置 */
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  flex-wrap: nowrap;
  align-items: stretch;
  background: #fff;
  border-top: 1px solid #eee;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.08);
  padding-bottom: env(safe-area-inset-bottom);
}

/* 可横向滑动的按钮区 */
.lem-toolbar-scroll {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 4px;
  padding: 8px 10px;
  overflow-x: auto;
  overflow-y: hidden;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.lem-toolbar-scroll::-webkit-scrollbar {
  display: none;
}

/* 上下移动按钮：固定在右侧，不随按钮栏滑动 */
.lem-toolbar-move {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  font-size: 10px;
  color: #1890ff;
  padding: 4px 12px;
  border-left: 1px solid #eee;
  background: #fff;
  box-shadow: -2px 0 6px rgba(0, 0, 0, 0.05);
  z-index: 2;
}

/* 弹出锁定状态：按钮高亮提示当前已锁定，点击元素不弹卡片 */
.lem-toolbar-move-locked {
  color: #fff;
  background: #1890ff;
}

.lem-toolbar-move-locked span {
  color: #fff;
}

.lem-tool-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 10px;
  color: #666;
  padding: 4px 8px;
  flex: 0 0 auto;
  min-width: 44px;
}

.lem-tool-btn .anticon {
  font-size: 18px;
}

/* 属性面板：宽度与页面一致，从底边栏上沿向上弹出；下滑（锁定）状态不弹 */
.lem-panel {
  position: absolute;
  left: 0;
  right: 0;
  width: 100%;
  box-sizing: border-box;
  bottom: calc(58px + env(safe-area-inset-bottom));
  z-index: 90;
  background: #fff;
  border-top: 1px solid #eee;
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.12);
  padding: 12px 16px;
  max-height: 46vh;
  overflow-y: auto;
  animation: lem-panel-up 0.18s ease-out;
}

@keyframes lem-panel-up {
  from { transform: translateY(100%); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

/* 元素选择弹层：全宽，从底边栏上沿向上弹出，遮罩不覆盖底边栏 */
.lem-sheet-mask {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: calc(58px + env(safe-area-inset-bottom));
  background: rgba(0, 0, 0, 0.35);
  z-index: 200;
}

.lem-sheet {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  box-sizing: border-box;
  max-height: 46vh;
  background: #fff;
  border-radius: 12px 12px 0 0;
  display: flex;
  flex-direction: column;
  animation: lem-panel-up 0.18s ease-out;
}

.lem-sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 10px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.lem-sheet-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.lem-sheet-body {
  flex: 1 1 auto;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 12px 16px 16px;
}

.lem-provider {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.lem-provider-label {
  flex: none;
  font-size: 13px;
  color: #666;
}

.lem-panel-multi-tip {
  font-size: 12px;
  color: #1890ff;
  margin-bottom: 8px;
}

.lem-text-input-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.lem-text-input {
  flex: 1;
}

/* 商品信息组合字段勾选 */
.lem-goods-field-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 10px;
}

.lem-goods-field-row .lem-style-label {
  flex-shrink: 0;
  line-height: 24px;
}

.lem-goods-field-group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  line-height: 24px;
}

.lem-ocr-btn {
  flex-shrink: 0;
}

.lem-style-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 12px;
}

.lem-style-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.lem-style-label {
  font-size: 12px;
  color: #999;
}

.lem-elem-list {
  max-height: 400px;
  overflow-y: auto;
}

.lem-elem-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 8px;
  border-bottom: 1px solid #f5f5f5;
  font-size: 14px;
  color: #333;
}

.lem-elem-item .anticon {
  color: #1890ff;
  flex: 0 0 auto;
  margin-left: 8px;
}

/* 字段名（左）+ 中文名（中）+ 选择按钮（右） */
.lem-elem-key {
  flex: 0 0 46%;
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lem-elem-cn {
  flex: 1 1 auto;
  min-width: 0;
  margin-left: 8px;
  font-size: 13px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 勾选式元素列表：分组标题 + 已选中高亮 */
.lem-elem-group-title {
  font-size: 12px;
  color: #999;
  padding: 8px 8px 4px;
  background: #fafafa;
}

.lem-elem-item-active {
  background: #e6f7ff;
  color: #1890ff;
}

.lem-add-custom {
  margin-top: 10px;
}

/* 明细列元素：逐行重复，行高固定 20px，与发货单渲染页保持一致 */
.lem-col-stack {
  display: flex;
  flex-direction: column;
}

.lem-col-line {
  height: 20px;
  line-height: 20px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lem-subtitle {
  cursor: pointer;
}

.lem-size-edit-icon {
  margin-left: 4px;
  font-size: 12px;
  opacity: 0.6;
}

.lem-size-editor {
  padding: 8px 0;
}

.lem-size-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.lem-size-label {
  font-size: 14px;
  color: #333;
}

.lem-size-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.lem-size-preset-item {
  padding: 6px 14px;
  background: #f5f5f5;
  border-radius: 4px;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.lem-size-preset-item:active {
  background: #e6f7ff;
  color: #1890ff;
}
</style>

<style lang="scss">
/* 保存模板弹层：移动端标准底部弹层样式（圆角顶部、贴底、安全区留白） */
.lem-save-sheet-wrap {
  display: flex;
  align-items: flex-end;
}

.lem-save-sheet-wrap .ant-modal {
  position: relative;
  top: auto;
  bottom: 0;
  width: 100% !important;
  max-width: 100%;
  margin: 0;
  padding-bottom: 0;
}

.lem-save-sheet-wrap .ant-modal-content {
  border-radius: 16px 16px 0 0;
  padding-bottom: env(safe-area-inset-bottom);
  overflow: hidden;
}

.lem-save-sheet-wrap .ant-modal-header {
  border-radius: 16px 16px 0 0;
  border-bottom: none;
  padding: 16px 16px 8px;
}

.lem-save-sheet-wrap .ant-modal-title {
  text-align: center;
  font-size: 16px;
}

.lem-save-sheet-wrap .ant-modal-body {
  padding: 4px 16px 8px;
  max-height: 60vh;
  overflow-y: auto;
}

.lem-save-sheet-wrap .ant-modal-footer {
  padding: 8px 16px 16px;
  border-top: 1px solid #f0f0f0;
}

.lem-save-sheet-wrap .ant-modal-footer .ant-btn {
  height: 40px;
}
</style>
