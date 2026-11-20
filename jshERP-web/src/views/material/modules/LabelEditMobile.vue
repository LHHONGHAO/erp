<template>
  <mobile-modal-page
    :visible="true"
    :title="currentTitle"
    @close="handleBack">
    <!-- 顶栏左右两列：左列页面标题单独一行；右列上为标签名称、下为标签尺寸 -->
    <template #title-extra>
      <div class="lem-header-sub">
        <div class="lem-header-name">{{ headerTemplateName }}</div>
        <div class="lem-header-size">{{ sizeLabel }}</div>
      </div>
    </template>
    <template #actions>
      <a-icon type="edit" class="mmp-icon lem-icon-sm" title="编辑标签信息" @click="openEditInfo" />
      <a-icon type="setting" class="mmp-icon" title="设置" @click="openSettings" />
    </template>
    <div class="lem-page">

    <!-- 对齐排列工具条（固定在顶栏下方，左侧提示标签状态） -->
    <div class="lem-align-bar">
      <div class="lem-align-hint">
        <span class="lem-align-hint__state" :class="{ 'is-multi': multiSelectMode }">{{ labelStateText }}</span>
        <span class="lem-align-hint__tip">{{ multiSelectMode ? '单击空白单选' : '长按空白进入多选' }}</span>
      </div>
      <div class="lem-align-btns">
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
      </div>
    </div>

    <!-- 画布 + 右侧属性栏 -->
    <div class="lem-content">
    <!-- 预览区 -->
    <div class="lem-preview-wrap" ref="previewWrap" :class="wrapScrolling ? 'lem-preview-wrap--scrolling' : 'lem-preview-wrap--fitting'"
         @pointerdown="startAreaPress">
      <div class="lem-scroll-spacer" :style="{ width: (scrollSpacer + sidePanelReserve) + 'px' }"></div>
      <div
        class="lem-label"
        ref="labelCanvas"
        :style="[labelStyle, { touchAction: multiSelectMode ? 'none' : 'auto' }]"
        @pointerdown.stop.prevent="startMarquee"
      >
        <LabelTextFrame
          v-for="(key, idx) in plainAttrKeys"
          :key="key"
          :drag-key="key"
          :element-key="key"
          :frame-style="getElementStyle(key)"
          :zoom="zoom"
          :active="selectedKeys.indexOf(key) !== -1"
          :show-frame="true"
          :show-handle="currentAttrKey === key"
          :show-delete="currentAttrKey === key && !isCode(key)"
          :extra-class="isCode(key) ? 'lem-elem-code' : ''"
          @drag-start="startPointerDrag"
          @delete="removeAttr(key)"
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
              <div class="lem-mini-row" v-for="n in 1" :key="n">
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
              <div class="lem-col-line" v-for="n in 1" :key="n">{{ getDetailSample(key, n) }}</div>
            </div>
          </template>
          <template v-else>
            <span class="lem-elem-label" v-if="displayParts[key] && displayParts[key].label && key !== 'page_no'">{{ displayParts[key].label }}</span>
            <span class="lem-elem-value" v-if="displayParts[key] && displayParts[key].value">{{ displayParts[key].value }}</span>
            <span class="lem-elem-placeholder" v-else-if="displayParts[key] && displayParts[key].placeholder">{{ displayParts[key].placeholder }}</span>
          </template>
        </LabelTextFrame>
        <!-- 规格块：与普通文字共用同一个外框组件和样式，块内文字按行渲染在边框内 -->
        <LabelTextFrame
          v-for="view in specBlockViews"
          :key="'spec-frame-' + view.id"
          :drag-key="view.anchorKey"
          :element-key="view.anchorKey"
          :frame-style="view.frameStyle"
          :zoom="zoom"
          :active="view.active"
          :show-frame="true"
          :show-handle="view.active"
          :show-delete="view.active"
          extra-class="lem-spec-block"
          @drag-start="startPointerDrag"
          @delete="removeSpecBlock(view)"
        >
          <div
            v-for="(line, li) in view.layout.lines"
            :key="li"
            class="lem-spec-line"
            :style="{ height: view.layout.lineHeightPx + 'px', lineHeight: view.layout.lineHeightPx + 'px', textAlign: view.frameStyle.textAlign }"
          >
            <template v-for="(item, ii) in line.items">
              <span v-if="ii > 0" :key="'gap' + ii" class="lem-spec-gap"> </span>
              <span :key="ii" class="lem-spec-item" :class="{ 'is-placeholder': item.placeholder }">{{ item.text }}</span>
            </template>
          </div>
        </LabelTextFrame>
        <!-- 框选矩形 -->
        <div v-if="marquee.visible" class="lem-marquee" :style="marqueeStyle"></div>
        <!-- 拖动吸附时的对齐线 -->
        <div v-if="guides.v !== null" class="lem-guide lem-guide-v" :style="{ left: guides.v + 'px', width: hairlineWidth }"></div>
        <div v-if="guides.h !== null" class="lem-guide lem-guide-h" :style="{ top: guides.h + 'px', height: hairlineWidth }"></div>
      </div>
      <div class="lem-scroll-spacer" :style="{ width: (scrollSpacer + sidePanelReserve) + 'px' }"></div>
    </div>

    <!-- 属性面板：覆盖在画布右侧，不压缩画布；与元素面板互斥 -->
    <div class="lem-side" v-if="sidePanel === 'attr'" ref="sidePanel">
      <div class="lem-side-head">
        <span class="lem-side-title">文字属性</span>
        <a class="lem-side-close" @click="closeAll">全部关闭</a>
      </div>
      <div class="lem-side-body">
          <div class="lem-panel-multi-tip" v-if="selectedKeys.length > 1 && !currentSpecBlock">已选 {{ selectedKeys.length }} 项，样式修改应用到全部选中元素</div>
          <!-- 品类规格：类别、规格为独立元素，属性面板只保留这两项 -->
          <template v-if="currentSpecBlock">
            <template v-if="isCategorySpecBlock">
              <div class="lem-field-stack">
                <div class="lem-field-head">
                  <span class="lem-style-label">类别</span>
                </div>
                <a-input
                  :value="currentCategoryLabel"
                  placeholder="类别"
                  class="lem-text-input"
                  @input="updateCategoryLabel"
                />
              </div>
              <div class="lem-field-stack">
                <div class="lem-field-head">
                  <span class="lem-style-label">规格</span>
                </div>
                <a-input
                  :value="normalizedSpecLabel"
                  placeholder="规格"
                  class="lem-text-input"
                  @input="updateNormalizedSpecLabel"
                />
              </div>
            </template>
            <template v-else>
              <div class="lem-field-stack" v-if="currentSpecBlock.nameKey">
                <div class="lem-field-head">
                  <span class="lem-style-label">属性名</span>
                  <a-switch
                    size="small"
                    class="lem-field-switch"
                    :checked="isAttrSelected(currentSpecBlock.nameKey)"
                    @change="onSpecVisibleChange(currentSpecBlock.nameKey, $event)"
                  />
                </div>
                <a-input
                  :value="specNameText"
                  :disabled="!isAttrSelected(currentSpecBlock.nameKey)"
                  placeholder="规格名"
                  class="lem-text-input"
                  @input="updateSpecName"
                />
              </div>
              <!-- 规格块整体排版：折行与文本对齐都在同一个块几何上 -->
              <div class="lem-field-stack">
                <div class="lem-field-head">
                  <span class="lem-style-label">排版</span>
                  <a-checkbox :checked="specBlockWrap" @change="onSpecWrapChange">折行</a-checkbox>
                </div>
                <a-radio-group :value="specBlockAlign" size="small" @change="onSpecAlignChange">
                  <a-radio-button value="left">左齐</a-radio-button>
                  <a-radio-button value="center">居中</a-radio-button>
                  <a-radio-button value="right">右齐</a-radio-button>
                </a-radio-group>
              </div>
              <div class="lem-field-stack">
                <div class="lem-field-head">
                  <span class="lem-style-label">属性值</span>
                  <a-button size="small" class="lem-ocr-btn">OCR</a-button>
                </div>
                <div class="lem-value-list">
                  <div class="lem-value-row" v-for="valueKey in currentSpecBlock.valueKeys" :key="valueKey">
                    <a-input
                      :value="specValueTextOf(valueKey)"
                      :disabled="!isAttrSelected(valueKey)"
                      placeholder="规格值"
                      class="lem-text-input"
                      @input="updateSpecValueAt(valueKey, $event)"
                    />
                    <a-switch
                      size="small"
                      class="lem-field-switch"
                      :checked="isAttrSelected(valueKey)"
                      @change="onSpecVisibleChange(valueKey, $event)"
                    />
                  </div>
                </div>
              </div>
            </template>
          </template>
          <div class="lem-field-stack" v-else-if="selectedKeys.length === 1 && showFontFields && selectedKeys[0] !== 'page_no'">
            <div class="lem-field-head">
              <span class="lem-style-label">属性名</span>
            </div>
            <a-input
              :value="currentLabel"
              :placeholder="isCustomAttr(selectedKeys[0]) ? '自定义文本内容' : '属性名（留空不显示）'"
              class="lem-text-input"
              @input="updateCurrentLabel"
            />
          </div>
          <div class="lem-field-stack" v-else-if="selectedKeys.length === 1 && selectedKeys[0] === 'page_no'">
            <div class="lem-field-head">
              <span class="lem-style-label">文本内容</span>
            </div>
            <a-input :value="pageLabelText()" disabled class="lem-text-input" />
          </div>
          <div class="lem-field-stack" v-else-if="selectedKeys.length === 1 && isGoodsKey(selectedKeys[0])">
            <div class="lem-field-head">
              <span class="lem-style-label">组合字段</span>
            </div>
            <a-checkbox-group :value="goodsFields" class="lem-goods-field-group" @change="onGoodsFieldsChange">
              <a-checkbox v-for="f in goodsFieldOptions" :key="f.key" :value="f.key">{{ f.label }}</a-checkbox>
            </a-checkbox-group>
          </div>
          <!-- 样式编辑：多选时不一致的项显示为空，修改后应用到全部 -->
          <div class="lem-style-grid">
            <div class="lem-style-item" v-if="showFontFields">
              <span class="lem-style-label">字号</span>
              <a-input-number :value="getMultiStyle('fontSize')" @change="onStyleChange('fontSize', $event)" :min="6" :max="72" size="small" class="lem-style-control" placeholder="多值" />
            </div>
            <div class="lem-style-item" v-if="showFontFields">
              <span class="lem-style-label">粗细</span>
              <a-select :value="getMultiStyle('fontWeight')" @change="onStyleChange('fontWeight', $event)" size="small" class="lem-style-control" placeholder="多值">
                <a-select-option value="normal">正常</a-select-option>
                <a-select-option value="bold">粗体</a-select-option>
              </a-select>
            </div>
            <div class="lem-style-item" v-if="showFontFields">
              <span class="lem-style-label">字距</span>
              <a-input-number :value="getMultiStyle('letterSpacing')" @change="onStyleChange('letterSpacing', $event)" :min="0" :max="30" :step="0.5" size="small" class="lem-style-control" placeholder="多值" />
            </div>
            <div class="lem-style-item" v-if="showFontFields">
              <span class="lem-style-label">行距</span>
              <a-input-number :value="getMultiStyle('lineHeight')" @change="onStyleChange('lineHeight', $event)" :min="0.8" :max="4" :step="0.1" size="small" class="lem-style-control" placeholder="多值" />
            </div>
            <div class="lem-style-item">
              <span class="lem-style-label">X</span>
              <a-input-number :value="getMultiStyle('x')" @change="onStyleChange('x', $event)" size="small" class="lem-style-control" />
            </div>
            <div class="lem-style-item">
              <span class="lem-style-label">Y</span>
              <a-input-number :value="getMultiStyle('y')" @change="onStyleChange('y', $event)" size="small" class="lem-style-control" />
            </div>
            <div class="lem-style-item">
              <span class="lem-style-label">宽</span>
              <a-input-number :value="getMultiStyle('width')" @change="onStyleChange('width', $event)" :min="10" :max="2000" size="small" class="lem-style-control" placeholder="自动" />
            </div>
            <div class="lem-style-item">
              <span class="lem-style-label">高</span>
              <a-input-number :value="getMultiStyle('height')" @change="onStyleChange('height', $event)" :min="10" :max="1000" size="small" class="lem-style-control" placeholder="自动" />
            </div>
          </div>
      </div>
    </div>

    <!-- 元素面板：与属性面板同款，贴附右侧；两者互斥 -->
    <div class="lem-side" v-if="sidePanel === 'element'">
      <div class="lem-side-head">
        <span class="lem-side-title">元素</span>
      </div>
      <LabelElementPanel
        :providers="providerList"
        :provider="provider"
        :groups="attrGroups"
        :selected-attrs="selectedAttrs"
        @provider-change="onProviderChange"
        @toggle-item="toggleElemItem"
        @add-custom="addCustomLabel"
      />
      <div class="lem-side-foot">
        <a-button type="primary" size="small" block icon="profile" @click="openFormDataSheet">表单数据</a-button>
      </div>
    </div>

    <!-- 悬浮切换卡片：位于屏幕下方，两个都关闭时贴屏幕右边缘 -->
    <div class="lem-side-tabs" :class="{ 'lem-side-tabs--attached': sidePanel }">
      <div
        class="lem-side-tab"
        :class="{ 'lem-side-tab--disabled': !selectedKeys.length }"
        @click="toggleSidePanel('attr')"
      >
        <a-icon :type="sidePanel === 'attr' ? 'right' : 'left'" />
        <span>属性</span>
      </div>
      <div class="lem-side-tab" @click="toggleSidePanel('element')">
        <a-icon :type="sidePanel === 'element' ? 'right' : 'left'" />
        <span>元素</span>
      </div>
    </div>
    </div>

    <!-- 工具栏：固定在屏幕底边 -->
    <div class="lem-toolbar">
      <div class="lem-toolbar-scroll">
        <div class="lem-tool-btn" @click="zoomIn">
          <a-icon type="zoom-in" />
          <span>放大</span>
        </div>
        <div class="lem-tool-btn" @click="zoomOut">
          <a-icon type="zoom-out" />
          <span>缩小</span>
        </div>
        <div class="lem-tool-btn" :class="{ 'is-disabled': historyIndex <= 0 }" @click="undo">
          <a-icon type="undo" />
          <span>撤销</span>
        </div>
        <div class="lem-tool-btn" :class="{ 'is-disabled': historyIndex >= history.length - 1 }" @click="redo">
          <a-icon type="redo" />
          <span>恢复</span>
        </div>
        <div class="lem-tool-btn" @click="handlePrint">
          <a-icon type="printer" />
          <span>打印</span>
        </div>
        <div class="lem-tool-btn" @click="saveTemplate">
          <a-icon type="save" />
          <span>保存</span>
        </div>
      </div>
    </div>

    <!-- 保存 / 打印设置融合弹层：顶栏「保存」与「设置」共用，差异只在保存后的去向 -->
    <a-modal
      :title="saveSheetEntry === 'settings' ? '打印设置' : '保存模板'"
      :visible="saveSheetVisible"
      :width="'92%'"
      :maskClosable="false"
      :zIndex="3020"
      class="lem-save-sheet"
      wrapClassName="lem-save-sheet-wrap"
      ok-text="保存"
      cancel-text="取消"
      @ok="confirmSaveTemplate"
      @cancel="closeSaveSheet"
    >
      <div class="lem-save-field">
        <div class="lem-save-label">模板名称</div>
        <a-input v-model="newTemplateName" placeholder="请输入模板名称" allowClear @pressEnter="confirmSaveTemplate" />
      </div>
      <div class="lem-save-field">
        <div class="lem-save-label">模板对象</div>
        <div class="lem-save-object-row">
          <a-select
            v-model="templateObject"
            style="flex:1"
            :disabled="templateType !== 'label'"
            @change="onTemplateObjectChange"
          >
            <a-select-option v-for="opt in templateObjectOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
          <a-select
            v-if="templateObject === 'product'"
            v-model="templateCategoryId"
            style="flex:1"
            placeholder="全部类型"
            allowClear
          >
            <a-select-option v-for="cat in categoryList" :key="cat.value" :value="cat.value">{{ cat.label }}</a-select-option>
          </a-select>
        </div>
      </div>
      <div class="lem-save-field">
        <div class="lem-save-label-row">
          <span class="lem-save-label">单行标签超出宽度自动折行</span>
          <a-switch v-model="overflowWrapEnabled" />
        </div>
      </div>
      <div class="lem-save-field">
        <div class="lem-save-label-row">
          <span class="lem-save-option">单页打印（打印在一张页面）</span>
          <a-switch
            :checked="pageMode === 'none' && flowElementKeys.length === 0"
            :disabled="flowElementKeys.length > 0"
            @change="val => val && onPageModeChange('none')"
          />
        </div>
      </div>
      <div class="lem-save-field">
        <div class="lem-save-label-row">
          <span class="lem-save-option">自动分页（按标签高度自动分页打印）</span>
          <a-switch
            :checked="pageMode === 'fit' && flowElementKeys.length === 0"
            :disabled="flowElementKeys.length > 0"
            @change="val => val && onPageModeChange('fit')"
          />
        </div>
      </div>
      <div class="lem-save-field" v-if="selectedAttrs.length">
        <div class="lem-save-label-row">
          <span class="lem-save-label">分页打印</span>
          <span class="lem-save-switch-value">{{ flowElementKeys.length ? '商品分页 · 分页数量 ' + flowElementKeys.length : '未启用' }}</span>
        </div>
        <div class="lem-flow-cards">
          <div
            v-for="key in selectedAttrs"
            :key="key"
            class="lem-flow-row"
            :class="{ 'is-on': flowElementKeys.indexOf(key) > -1 }"
          >
            <span class="lem-flow-name">{{ flowElementLabel(key) }}</span>
            <a-switch
              :checked="flowElementKeys.indexOf(key) > -1"
              @change="toggleFlowElement(key)"
            />
          </div>
        </div>
      </div>
      <template slot="footer">
        <div class="lem-save-bottom">
          <div class="lem-save-bottom-label">
            <span>显示分页标签</span>
            <a-switch
              :checked="pageLabelEnabled"
              :disabled="effectivePageMode === 'none'"
              @change="onPageLabelToggle"
            />
          </div>
          <div class="lem-save-footer-btns">
            <a-button v-if="saveAsEnabled" @click="openSaveAs">另存为</a-button>
            <a-button @click="closeSaveSheet">取消</a-button>
            <a-button type="primary" @click="confirmSaveTemplate">保存</a-button>
          </div>
        </div>
      </template>
    </a-modal>
    <!-- 另存为：输入新模板名称后保存为新模板 -->
    <a-modal
      title="另存为"
      :visible="saveAsVisible"
      :width="'80%'"
      :zIndex="3020"
      ok-text="确定"
      cancel-text="取消"
      @ok="confirmSaveAs"
      @cancel="saveAsVisible = false"
    >
      <a-input
        v-model="saveAsName"
        placeholder="请输入新模板名称"
        allowClear
        @pressEnter="confirmSaveAs"
      />
    </a-modal>
    <!-- 顶栏唯一编辑按钮：弹层编辑标题、副标题与标签尺寸 -->
    <a-modal
      title="编辑标签信息"
      :visible="renameVisible"
      :width="'86%'"
      :zIndex="3030"
      ok-text="确定"
      cancel-text="取消"
      @ok="confirmEditInfo"
      @cancel="renameVisible = false"
    >
      <div class="lem-size-row">
        <span class="lem-size-label">标题</span>
        <a-input
          v-model="renameValue"
          placeholder="请输入标题"
          :maxLength="40"
          allowClear
        />
      </div>
      <div class="lem-size-row">
        <span class="lem-size-label">副标题</span>
        <a-input
          v-model="subtitleValue"
          placeholder="请输入副标题"
          :maxLength="60"
          allowClear
        />
      </div>
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
      <div class="lem-save-hint">标题保存时作为模板名称；副标题仅调整当前页面顶栏显示</div>
    </a-modal>
    <!-- 表单数据：标准数据表组件（上方规格选择、下方数据字段选择） -->
    <label-form-data-sheet
      :visible="formDataVisible"
      :keyword="formDataKeyword"
      :category-groups="formDataCategoryGroups"
      :field-groups="formDataGroups"
      @update:keyword="formDataKeyword = $event"
      @toggle-category="toggleFormDataCategory"
      @toggle-category-expand="toggleFormDataCategoryExpand"
      @toggle-item="toggleFormDataItem"
      @toggle-key="toggleFormDataKey"
      @toggle-group="toggleFormDataGroup"
      @confirm="confirmFormDataSheet"
      @cancel="closeFormDataSheet"
    />
    <!-- 打印标签弹层（不再跳转打印页面） -->
    <label-print-modal ref="labelPrintModal" />
    </div>
  </mobile-modal-page>
</template>

<script>
  import MobileModalPage from '@/components/mobile/MobileModalPage'
  import LabelPrintModal from '@/views/material/labelPrint/LabelPrintModal'
  import LabelTextFrame from '@/views/material/labelPrint/edit/LabelTextFrame'
  import LabelElementPanel from '@/views/material/labelPrint/edit/LabelElementPanel'
  import LabelFormDataSheet from '@/views/material/labelPrint/edit/LabelFormDataSheet'
  import { createLabelElementDrag } from '@/views/material/labelPrint/edit/labelElementDrag'
  import { isCodeKey } from '@/views/material/labelPrint/utils/labelCode'
  import { buildTemplateSnapshot, saveTemplateSnapshot, resolveTemplate, deleteTemplate } from '@/views/material/labelPrint/utils/labelTemplateFlow'
  import { v2ToEditorState, TEMPLATE_RENDER_VERSION } from '@/views/material/labelPrint/utils/labelTemplateV2'
  import { fetchProviders, fetchProviderFields } from '@/views/material/labelPrint/utils/labelProviderApi'
  import { queryMaterialCategoryTreeList } from '@/api/api'
  import { computeSpacer, centerScroll, setupLabelScroll, computeFitScale } from '@/utils/label-scroll'
  import { GOODS_TABLE_KEY, GOODS_KEY, GOODS_TABLE_COLUMNS, GOODS_FIELD_OPTIONS, hasGoodsValue, resolveLabelLayout, isGoodsTableKey, isReceiptItemKey, isReceiptItemHidden, replaceReceiptGoodsWithItems } from '@/views/material/labelPrint/utils/goodsLabelLayout'
  import { parseSpecItemKey, specNameKey, isSpecItemKey, firstRowForEdit, normalizeSpecBlocks, resolveEditSpecBlocks, buildSpecBlocksFromCategory } from '@/views/material/labelPrint/utils/goodsSpecBlocks'
  import { normalizeEditorData } from '@/views/material/labelPrint/utils/templateEditDataModel'
  import { buildSpecBlockItems, resolveSpecBlockLines, SPEC_BLOCK_PAD_X, SPEC_BLOCK_GAP } from '@/views/material/labelPrint/utils/specBlockLayout'
  import { LABEL_CATEGORY_KEY, LABEL_SPEC_KEY, buildCategorySpecAttrs, normalizeSpecMapping, flattenSpecMapping, specMappingHasSelection } from '@/views/material/labelPrint/utils/categorySpec'
  import { buildFormFieldAttrs } from '@/views/material/labelPrint/config/formFields'

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
   * storage: 'localTemplates' → 标签多模板列表
   *           'platformConfig' → 单 key 覆盖写
   * 读写统一由 utils/labelTemplateFlow 处理
   */
  const TYPE_CONFIG = {
    label: {
      title: '标签编辑',
      defaultWidth: 320,
      defaultHeight: 240,
      storage: 'localTemplates',
      defaultKeys: ['name', 'spec', 'price', 'barcode'], // 仅 applyDefaultLayout 使用
      successMsg: '标签模版已保存'
    },
    delivery: {
      title: '发货模版编辑',
      defaultWidth: 560,
      defaultHeight: 800,
      storage: 'deliveryTemplate',
      defaultKeys: ['dn_title', 'dn_no', 'dn_date', 'dn_member', 'dn_phone', 'dn_table', 'dn_qty_total', 'dn_amount_total', 'dn_remark', 'dn_footer'],
      successMsg: '发货模版已保存'
    },
    receipt: {
      title: '小票编辑',
      // 80mm 热敏小票纸（96dpi → 302px）
      defaultWidth: 302,
      defaultHeight: 600,
      storage: 'platformConfig',
      platformConfigKey: 'receipt_layout',
      defaultKeys: [],
      successMsg: '小票模版已保存'
    },
    member: {
      title: '会员标签编辑',
      defaultWidth: 320,
      defaultHeight: 240,
      storage: 'platformConfig',
      platformConfigKey: 'member_layout',
      defaultKeys: ['contacts', 'telephone', 'address'],
      successMsg: '会员标签模版已保存'
    },
    address: {
      title: '地址打印',
      defaultWidth: 320,
      defaultHeight: 240,
      storage: 'platformConfig',
      platformConfigKey: 'address_layout',
      defaultKeys: ['contacts', 'telephone', 'account', 'platform', 'remark', 'address', 'goods', 'goods_table'],
      successMsg: '地址打印模版已保存'
    }
  }

  export default {
    name: 'LabelEditMobile',
    components: { MobileModalPage, LabelPrintModal, LabelTextFrame, LabelElementPanel, LabelFormDataSheet },
    props: {
      // 由标准复用弹层页传入的业务上下文
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
        editorDataFromForm: false,
        editorDataRestored: false,
        editorFormTitle: '',
        selectedAttrs: [],
        // 小票单品元素中通过开关关闭（不显示）的 key，打印快照时一并带给预览页
        hiddenItemKeys: [],
        // 规格块结构（规格名 + 属性值名）：编辑态只显示属性名，由 specMapping 扁平派生
        specBlocks: [],
        // 规格数据映射表（唯一权威）：该标签从哪个类别读取哪个规格的哪些值
        specMapping: [],
        // 模板是否已建立规格数据映射表：为 true 时打印/预览只加载映射表选中的规格
        specMappingReady: false,
        attrStyles: {},
        attrLabels: {},
        // 商品组合（商品信息）显示哪些字段：默认全部，可在属性面板按需勾选
        goodsFields: ['name', 'spec1', 'spec2', 'spec3'],
        currentAttrKey: null,
        labelWidth: 320,
        labelHeight: 240,
        // 超长文本处理：wrap=自动折行并下移下方内容，ellipsis=单行省略号裁切
        overflowMode: 'wrap',
        // 多商品分页：none=多行连续，fit=按高度续页，each=每件一张标签（随模板保存）
        pageMode: 'none',
        // fit 模式下每页最多商品行数（0 = 只按标签高度续页）
        maxRowsPerPage: 0,
        // 续页时重复商品表头
        repeatHeader: true,
        // 参与多行/分页打印的元素（打印设置里勾选）
        flowElementKeys: [],
        // 小票按单品生成的动态元素（只用于当前单据，不写入模板）
        dynamicItemKeys: [],
        zoom: 1,
        customCounter: 0,
        templates: [],
        selectedTemplateId: null,
        // 「保存 / 打印设置」融合弹层
        saveSheetVisible: false,
        // 「表单数据」底部弹层：勾选状态为草稿，确定后一次性写回
        formDataVisible: false,
        formDataKeyword: '',
        formDataDraft: {},
        // 「表单数据」品类折叠状态：key=品类 ID，true=展开
        formDataExpanded: {},
        // 「表单数据」已勾选的项（元素面板只列这些；画布删除不影响）
        formDataCheckedKeys: {},
        // 入口：'save' 顶栏保存按钮（保存后关闭编辑页）；'settings' 设置按钮（保存后返回编辑页）
        saveSheetEntry: 'save',
        newTemplateName: '',
        // 另存为弹层与名称
        saveAsVisible: false,
        saveAsName: '',
        // 模板对象：商品模板 / 地址模板
        templateObject: 'product',
        // 顶栏模板名称（与页面标题并列显示）
        currentTemplateName: '',
        // 顶栏副标题自定义文案；为空时回退为业务名称
        subtitleText: '',
        // 顶栏编辑弹层：标题、副标题与标签尺寸
        renameVisible: false,
        renameValue: '',
        subtitleValue: '',
        templateCategoryId: null,
        categoryTree: [],
        // 发货单模板（统一模板表记录）
        deliveryTemplate: null,
        codeImages: {},
        history: [],
        historyIndex: -1,
        selectedKeys: [],
        // 右侧面板：'' 都关闭；'attr' 文字属性；'element' 元素（互斥，同时只显示一个）
        sidePanel: '',
        multiSelectMode: false,
        marquee: { active: false, visible: false, startX: 0, startY: 0, x: 0, y: 0, w: 0, h: 0 },
        // 拖动吸附时的对齐线位置（标签坐标系），null 表示不显示
        guides: { v: null, h: null },
        editWidthMm: 148,
        editHeightMm: 212,
        // wrap 是否溢出需要滚动：true → flex-start + scrollLeft 居中；false → center 直接居中
        wrapScrolling: false      }
    },
    computed: {
      // 顶栏左侧提示：当前标签选中状态
      labelStateText() {
        if (this.multiSelectMode) return '已选 ' + this.selectedKeys.length + ' 项 · 多选'
        if (!this.selectedKeys.length) return '未选中标签'
        return '已选 1 项'
      },
      currentTitle() {
        var cfg = TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
        return cfg.title
      },
      // 超长文本处理开关：开=自动折行，关=省略号裁切
      overflowWrapEnabled: {
        get: function() { return this.overflowMode !== 'ellipsis' },
        set: function(val) { this.overflowMode = val ? 'wrap' : 'ellipsis' }
      },
      // 分页打印对象有勾选时即为商品分页
      effectivePageMode() {
        return this.flowElementKeys.length > 0 ? 'each' : this.pageMode
      },
      // 仅本地模板仓库支持另存为（发货单/小票/会员/地址走各自保存入口）
      saveAsEnabled() {
        var cfg = TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
        return cfg.storage === 'localTemplates'
      },
      // 模板对象选项：商品标签可切换商品/地址，其他模板类型固定为自身对象
      templateObjectOptions() {
        var map = {
          label: [{ value: 'product', label: '商品模板' }, { value: 'address', label: '地址模板' }],
          delivery: [{ value: 'delivery', label: '发货单模板' }],
          receipt: [{ value: 'receipt', label: '小票模板' }],
          member: [{ value: 'member', label: '会员标签模板' }],
          address: [{ value: 'address', label: '地址打印模板' }]
        }
        return map[this.templateType] || map.label
      },
      // 商品模板按一级类目绑定：下拉可选的就是商品一级类目
      categoryList() {
        return this.flattenCategoryTree(this.categoryTree, 1)
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
      // 标准弹层顶栏副标题：标签尺寸
      sizeLabel() {
        return this.labelWidthMm + 'mm × ' + this.labelHeightMm + 'mm'
      },
      // 顶栏模板名称：未保存/未加载时显示默认名称
      headerTemplateName() {
        if (this.currentTemplateName) return this.currentTemplateName
        var cfg = TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
        return cfg.title.replace('编辑', '') + '模板'
      },
      labelStyle() {
        return {
          width: this.labelWidth + 'px',
          height: this.labelHeight + 'px',
          transform: 'scale(' + this.zoom + ')',
          transformOrigin: 'center top'
        }
      },
      // 1px 在缩放后仍保持 1px：虚线框与对齐线都用这个宽度
      hairlineWidth() {
        return (1 / this.zoom) + 'px'
      },
      // 打印设置里「分页标签」的勾选状态，直接以画布是否含该元素为准
      pageLabelEnabled() {
        return this.selectedAttrs.indexOf('page_no') > -1
      },
      // 标签居中 + 对称滑动：从 label-scroll 模块计算单侧 spacer 宽度
      scrollSpacer() {
        return computeSpacer(this.labelWidth, this.zoom)
      },
      // 属性面板打开时，左右各补一段可滚动余量，让画布可左移，手柄不被面板盖住
      sidePanelReserve() {
        if (this.sidePanel !== 'attr') return 0
        var narrow = typeof window !== 'undefined' && window.innerWidth <= 360
        return (narrow ? 164 : 180) + 20
      },
      // 编辑区与打印分页保持一致：重复出现的商品属性在编辑页只保留第一组
      // 具体打印时按打印规则展开全部商品行
      editorGoodsRows() {
        // 小票编辑的是模板：只用属性名占位，不带入单据业务值与尺寸
        if (this.templateType === 'receipt') return [this.editGoodsRow()]
        var rows = (this.record && this.record.goodsRows) || []
        return firstRowForEdit(rows, hasGoodsValue)
      },
      // 标签最终布局（与打印预览、打印数据共用同一函数）
      labelLayout() {
        // 编辑页按占位排版：普通属性只放属性名，规格项保留规格名/规格值本身
        var values = {}
        this.allAttrs.forEach(function(attr) {
          values[attr.key] = isSpecItemKey(attr.key) && attr.value != null ? String(attr.value) : ''
        })
        // 分页标签：文案由当前页/总页数决定，不参与普通文本编辑
        values['page_no'] = this.pageLabelText()
        return resolveLabelLayout({
          selectedAttrs: this.selectedAttrs,
          attrStyles: this.attrStyles,
          values: values,
          labels: this.attrLabels,
          goodsRows: this.editorGoodsRows,
          paperWidth: this.labelWidth,
          goodsLabel: this.attrLabels[GOODS_KEY] || '',
          goodsFields: this.goodsFields,
          mode: this.overflowMode,
          editorMode: true
        })
      },
      // 兼容原有调用：商品明细行数据
      goodsRowsData() {
        return this.labelLayout
      },
      // 元素 key → 编辑页显示文本
      // 编辑页只显示属性名（含表单标题），不展示业务值；规格块显示规格名与规格值本身
      displayParts() {
        var self = this
        var map = {}
        this.selectedAttrs.forEach(function(key) {
          var attr = self.allAttrs.find(function(a) { return a.key === key })
          // 规格名/规格值：规格块自身内容，按真实数据展示
          if (isSpecItemKey(key)) {
            map[key] = { label: '', value: attr && attr.value != null ? String(attr.value) : '' }
            return
          }
          // 分页标签：编辑页直接显示当前页码占位
          if (key === 'page_no') {
            map[key] = { label: '', value: self.pageLabelText() }
            return
          }
          // 明细表 / 编码由各自模板渲染，不走文本占位
          if (isGoodsTableKey(key) || key === 'dn_table' || self.isCode(key)) {
            map[key] = { label: '', value: '' }
            return
          }
          // 其余属性：只显示属性名，业务值在打印时填充
          // 属性名有文本正常显示；属性名留空时用字段默认名称浅灰占位，便于在编辑页识别元素
          var hasLabelProp = Object.prototype.hasOwnProperty.call(self.attrLabels, key)
          var label = hasLabelProp ? String(self.attrLabels[key] == null ? '' : self.attrLabels[key]) : ''
          if (!label && attr && attr.isCustom) {
            // 自定义标签没有业务名，直接显示自身文案
            map[key] = { label: '', value: attr.value == null ? '' : String(attr.value), placeholder: '' }
          } else if (label) {
            map[key] = { label: '', value: label, placeholder: '' }
          } else {
            map[key] = { label: '', value: '', placeholder: attr ? String(attr.label || '') : '' }
          }
        })
        return map
      },
      // 商品组合可勾选的字段（商品名称 / 规格值1-3）
      goodsFieldOptions() {
        return GOODS_FIELD_OPTIONS
      },
      // 商品明细表样式的实际元素 key（小票为 rc_table，其余为 goods_table）
      goodsTableKey() {
        return this.selectedAttrs.filter(isGoodsTableKey)[0] || GOODS_TABLE_KEY
      },
      // 商品明细表：列宽/行高/总高度（按商品行数动态计算）
      goodsTableData() {
        return this.goodsRowsData.table
      },
      goodsTableColumns() {
        return GOODS_TABLE_COLUMNS
      },
      // 表格预览行：编辑页只保留一行属性名占位，真实商品行在打印时按规则填充
      goodsTablePreviewRows() {
        return [GOODS_TABLE_COLUMNS]
      },
      goodsTableStyle() {
        var style = this.attrStyles[this.goodsTableKey] || {}
        return {
          width: (this.goodsTableData ? this.goodsTableData.width : 0) + 'px',
          fontSize: (style.fontSize || 12) + 'px',
          lineHeight: style.lineHeight || 1.2
        }
      },
      // 按 group 分组展示全部字段（已选中/未选中都列出，勾选式切换）
      // 规格名 + 其下全部规格值聚合为一个「规格块」条目，一个块只占一项
      attrGroups() {
        var self = this
        var groups = []
        var map = {}
        var blocks = {}
        // 元素面板只显示「表单数据」里已勾选的项：勾选后一直列出，画布删除不隐藏
        var selected = {}
        this.selectedAttrs.forEach(function(key) { selected[key] = true })
        var checkedKeys = this.formDataCheckedKeys || {}
        Object.keys(checkedKeys).forEach(function(key) {
          if (checkedKeys[key]) selected[key] = true
        })
        var pushAttr = function(name, attr) {
          if (!map[name]) {
            map[name] = { name: name, attrs: [] }
            groups.push(map[name])
          }
          map[name].attrs.push(attr)
        }
        this.allAttrs.forEach(function(a) {
          // 分页标签用面板底部的专用按钮添加，不在字段列表里重复出现
          if (a.key === 'page_no') return
          var name = a.group || '可用字段'
          var item = parseSpecItemKey(a.key)
          if (!item) {
            if (!selected[a.key]) return
            pushAttr(name, a)
            return
          }
          if (!selected[a.key]) return
          var isCategorySpec = item.categoryId != null && item.categoryId !== ''
          // 品类规格拆为两个独立元素：类别、规格
          var blockId = isCategorySpec
            ? (item.kind === 'value' ? 'cat-spec' : 'cat-name')
            : item.rowNo + '-' + (item.blockIdentity || item.blockNo)
          var block = blocks[blockId]
          if (!block) {
            var nameKey = specNameKey(item.rowNo, item.blockNo, item.categoryId, item.blockIdentity)
            var nameAttr = self.allAttrs.find(function(x) { return x.key === nameKey })
            var label = nameAttr && nameAttr.value ? String(nameAttr.value).replace(/：$/, '') : '规格块'
            if (isCategorySpec) {
              var labelKey = item.kind === 'value' ? 'catvalue' : 'catlabel'
              var defaultLabel = item.kind === 'value' ? '规格' : '类别'
              var savedLabel = Object.prototype.hasOwnProperty.call(self.attrLabels, labelKey) ? self.attrLabels[labelKey] : null
              label = savedLabel != null && savedLabel !== '' ? String(savedLabel) : defaultLabel
            }
            block = {
              key: 'specblock-' + blockId,
              blockId: blockId,
              isSpecBlock: true,
              blockKeys: [],
              label: label,
              specText: '',
              valueCount: 0
            }
            blocks[blockId] = block
            pushAttr(name, block)
          }
          block.blockKeys.push(a.key)
          if (item.kind === 'value') block.valueCount++
        })
        return groups.filter(function(group) { return group.attrs.length })
      },
      currentLabel() {
        if (!this.currentAttrKey) return ''
        return this.attrLabels[this.currentAttrKey] || ''
      },
      // 「表单数据」全域字段：当前字段清单为主，配置字段补差；规格项按块聚合、不展开为普通字段
      formDataFieldList() {
        var self = this
        var list = []
        var exists = {}
        var push = function(attr) {
          if (!attr || !attr.key || exists[attr.key] || attr.key === 'page_no') return
          // 类别/规格是归一化元素：品类规格被选中后自动回填元素面板，不作为可选数据字段
          if (attr.key === LABEL_CATEGORY_KEY || attr.key === LABEL_SPEC_KEY) return
          exists[attr.key] = true
          var label = attr.label
          if (Object.prototype.hasOwnProperty.call(self.attrLabels, attr.key) && self.attrLabels[attr.key] !== '') {
            label = self.attrLabels[attr.key]
          }
          list.push({
            key: attr.key,
            label: label || attr.key,
            value: attr.value,
            group: attr.group || '可用字段',
            isSpecBlock: false,
            blockKeys: []
          })
        }
        this.allAttrs.forEach(function(attr) {
          // 自定义标签不是表单字段，不进入「表单数据」
          if (attr.isCustom && /^custom_/.test(attr.key)) return
          if (parseSpecItemKey(attr.key)) return
          push(attr)
        })
        buildFormFieldAttrs(this.templateType, { record: this.record, sku: this.sku }).forEach(push)

        // 规格映射项：按全部已保存品类的规格配置列出「品类 → 规格名 → 规格值」，
        // 勾选结果写入当前标签独立的数据映射表（specMapping），未勾选的不加载业务数据
        var mappedBlocks = flattenSpecMapping(this.specMapping)
        this.categorySpecBlocksForRows().forEach(function(block) {
          var cid = block.categoryId == null ? '' : String(block.categoryId)
          var name = block.name || '规格'
          var blockNo = block.blockNo || 1
          var mapPrefix = '$map:' + cid + ':' + blockNo
          var saved = mappedBlocks.filter(function(item) {
            return String(item.categoryId || '') === cid && (item.name || '规格') === name
          })[0]
          var savedValues = saved && Array.isArray(saved.values) ? saved.values.map(String) : []
          var sourceValues = (block.sourceValues || block.values || []).map(String)
          var valueItems = sourceValues.map(function(value, valueIndex) {
            return { key: mapPrefix + ':v' + (valueIndex + 1), label: value }
          })
          list.push({
            key: 'mapblock-' + cid + '|' + blockNo,
            label: name,
            value: '',
            group: '品类规格',
            isSpecBlock: true,
            isMapping: true,
            mapped: savedValues.length > 0,
            categoryId: block.categoryId == null ? null : cid,
            categoryName: block.categoryName || '',
            categoryMemberIds: Array.isArray(block.categoryMemberIds) ? block.categoryMemberIds.slice() : [],
            blockNo: blockNo,
            blockKeys: [mapPrefix + ':name'].concat(valueItems.map(function(item) { return item.key })),
            valueItems: valueItems,
            sourceName: block.sourceName || name,
            sourceValues: sourceValues,
            savedValues: savedValues
          })
        })
        return list
      },
      // 「表单数据」分组：普通字段按 group 分组；品类规格由 formDataCategoryGroups 单独渲染
      formDataGroups() {
        var self = this
        var keyword = String(this.formDataKeyword || '').trim().toLowerCase()
        var groups = []
        var map = {}
        this.formDataFieldList.forEach(function(field) {
          if (field.isSpecBlock) return
          if (keyword) {
            var label = String(field.label || '').toLowerCase()
            var key = String(field.key || '').toLowerCase()
            if (label.indexOf(keyword) === -1 && key.indexOf(keyword) === -1) return
          }
          var name = field.group || '可用字段'
          if (!map[name]) {
            map[name] = { name: name, fields: [] }
            groups.push(map[name])
          }
          map[name].fields.push(field)
        })
        return groups.map(function(group) {
          var selectedCount = group.fields.filter(function(field) { return self.isFormDataChecked(field) }).length
          return {
            name: group.name,
            items: group.fields.map(function(field) {
              return {
                key: field.key,
                label: field.label,
                valueText: self.formDataValueText(field),
                isSpecBlock: false,
                blockKeys: field.blockKeys,
                checked: self.isFormDataChecked(field)
              }
            }),
            count: group.fields.length,
            selectedCount: selectedCount,
            checked: selectedCount === group.fields.length,
            indeterminate: selectedCount > 0 && selectedCount < group.fields.length
          }
        })
      },
      // 「表单数据」品类规格：品类一段 → 规格名一行 → 规格值 chips
      formDataCategoryGroups() {
        var self = this
        var keyword = String(this.formDataKeyword || '').trim().toLowerCase()
        var groups = []
        var map = {}
        this.formDataFieldList.forEach(function(field) {
          if (!field.isSpecBlock) return
          var categoryName = String(field.categoryName || '未分类品类')
          var specName = String(field.label || '')
          var values = (field.valueItems || []).filter(function(item) {
            if (!keyword) return true
            if (categoryName.toLowerCase().indexOf(keyword) > -1) return true
            if (specName.toLowerCase().indexOf(keyword) > -1) return true
            return String(item.label || '').toLowerCase().indexOf(keyword) > -1
          })
          var categoryMatched = !keyword || categoryName.toLowerCase().indexOf(keyword) > -1 ||
            specName.toLowerCase().indexOf(keyword) > -1
          if (!values.length && !categoryMatched) return
          var categoryKey = String(field.categoryId || '') || 'uncategorized'
          if (!map[categoryKey]) {
            map[categoryKey] = { key: categoryKey, name: categoryName, keys: [], items: [] }
            groups.push(map[categoryKey])
          }
          var group = map[categoryKey]
          group.keys = group.keys.concat((field.valueItems || []).map(function(item) { return item.key }))
          var chipItems = values.map(function(item) {
            return {
              key: item.key,
              label: item.label,
              checked: !!self.formDataDraft[item.key]
            }
          })
          var blockChecked = (field.valueItems || []).length > 0 &&
            (field.valueItems || []).every(function(item) { return !!self.formDataDraft[item.key] })
          var selectedValues = (field.valueItems || []).filter(function(item) { return !!self.formDataDraft[item.key] }).length
          group.items.push({
            key: field.key,
            label: specName,
            blockNo: field.blockNo,
            isSpecBlock: true,
            blockKeys: field.blockKeys,
            valueItems: chipItems,
            checked: blockChecked,
            selectedCount: selectedValues,
            valueCount: (field.valueItems || []).length
          })
        })
        return groups.map(function(group) {
          var selectedCount = group.keys.filter(function(key) { return !!self.formDataDraft[key] }).length
          return {
            key: group.key,
            name: group.name,
            items: group.items,
            count: group.keys.length,
            selectedCount: selectedCount,
            // 折叠列表：搜索时自动展开命中的品类，手动折叠优先
            expanded: self.formDataExpanded[group.key] === undefined ? !!keyword : !!self.formDataExpanded[group.key],
            checked: group.keys.length > 0 && selectedCount === group.keys.length,
            indeterminate: selectedCount > 0 && selectedCount < group.keys.length
          }
        })
      },
      // 小票规格块：规格名 + N 个规格值，选中块内任一项都按整块展示
      currentSpecBlock() {
        var key = this.currentAttrKey
        if (!key || !parseSpecItemKey(key)) {
          key = this.selectedKeys.find(function(k) { return !!parseSpecItemKey(k) })
        }
        if (!key) return null
        var parsed = parseSpecItemKey(key)
        if (!parsed) return null
        var rowNo = parsed.rowNo
        var blockNo = parsed.blockNo
        var categoryId = parsed.categoryId
        var nameKey = specNameKey(rowNo, blockNo, categoryId, parsed.blockIdentity)
        var hasName = this.allAttrs.some(a => a.key === nameKey)
        var valueKeys = this.allAttrs.filter(function(attr) {
          var item = parseSpecItemKey(attr.key)
          return !!item && item.kind === 'value' && item.rowNo === rowNo && item.blockNo === blockNo && String(item.categoryId || '') === String(categoryId || '') && String(item.blockIdentity || '') === String(parsed.blockIdentity || '')
        }).map(function(attr) { return attr.key })
        return { nameKey: hasName ? nameKey : '', valueKeys: valueKeys }
      },
      // 品类规格统一使用同一个「类别」标签 key
      currentCategoryLabelKey() {
        var block = this.currentSpecBlock
        if (!block) return ''
        var parsed = parseSpecItemKey(block.nameKey || block.valueKeys[0])
        if (!parsed || parsed.categoryId == null || parsed.categoryId === '') return ''
        return 'catlabel'
      },
      currentCategoryLabel() {
        var key = this.currentCategoryLabelKey
        if (!key) return ''
        var saved = this.attrLabels[key]
        return saved != null && saved !== '' ? String(saved) : '类别'
      },
      // 当前选中的是否品类规格元素（类别 / 规格）
      isCategorySpecBlock() {
        var block = this.currentSpecBlock
        if (!block) return false
        var parsed = parseSpecItemKey(block.nameKey || block.valueKeys[0])
        return !!(parsed && parsed.categoryId != null && parsed.categoryId !== '')
      },
      normalizedSpecLabel() {
        var saved = this.attrLabels.catvalue
        return saved != null && saved !== '' ? String(saved) : '规格'
      },
      // 画布普通文字元素：规格元素统一由 specBlockViews 以整块渲染
      plainAttrKeys() {
        return this.selectedAttrs.filter(function(key) { return !isSpecItemKey(key) })
      },
      // 「表单数据」品类规格：按品类聚合已选规格值（画布 / 元素面板共用）
      categorySpecGroups() {
        var self = this
        var keys = this.selectedAttrs.slice()
        var order = []
        var map = {}
        keys.forEach(function(key) {
          var item = parseSpecItemKey(key)
          if (!item || item.categoryId == null || item.categoryId === '') return
          var id = String(item.categoryId)
          if (!map[id]) {
            map[id] = { id: id, keys: [], nameKeys: [], valueKeys: [] }
            order.push(id)
          }
          map[id].keys.push(key)
          if (item.kind === 'name') map[id].nameKeys.push(key)
          else map[id].valueKeys.push(key)
        })
        return order.map(function(id) {
          var group = map[id]
          var labelKey = 'catlabel'
          var saved = Object.prototype.hasOwnProperty.call(self.attrLabels, labelKey) ? self.attrLabels[labelKey] : null
          group.labelKey = labelKey
          // 四件套 / 三件套等全部品类归一化为通用「类别」，可在属性面板改写
          group.label = saved != null && saved !== '' ? String(saved) : '类别'
          group.anchorKey = group.nameKeys[0] || group.keys[0]
          return group
        })
      },
      // 归一化规格块：所有品类合成一组，画布与元素面板只显示「类别」「规格」
      normalizedCategorySpecGroup() {
        var groups = this.categorySpecGroups
        if (!groups.length) return null
        var keys = []
        var nameKeys = []
        var valueKeys = []
        groups.forEach(function(group) {
          keys = keys.concat(group.keys)
          nameKeys = nameKeys.concat(group.nameKeys)
          valueKeys = valueKeys.concat(group.valueKeys)
        })
        var savedValueLabel = Object.prototype.hasOwnProperty.call(this.attrLabels, 'catvalue') ? this.attrLabels.catvalue : null
        return {
          id: 'all',
          keys: keys,
          nameKeys: nameKeys,
          valueKeys: valueKeys,
          anchorKey: groups[0].anchorKey,
          labelKey: 'catlabel',
          label: groups[0].label,
          valueLabel: savedValueLabel != null && savedValueLabel !== '' ? String(savedValueLabel) : '规格'
        }
      },
      // 规格块视图：品类规格归一化为一块（类别 + 规格），旧规格块保持原渲染
      specBlockViews() {
        var self = this
        var labelWidth = this.labelWidth || 320
        var lastRight = 0
        var lastBottom = 0
        // 小票不展示品类名
        var showCategory = this.templateType !== 'receipt'
        var views = []
        var pushView = function(anchorKey, nameKey, keys, items, flow) {
          var anchorStyle = self.attrStyles[anchorKey] || {}
          var layout = resolveSpecBlockLines({
            items: items,
            // 只有用户手动拖宽过的块才沿用保存宽度；其余按自然宽度排列
            width: anchorStyle.specWidthLocked && anchorStyle.width > 0 ? anchorStyle.width : 0,
            fontSize: anchorStyle.fontSize || 12,
            fontWeight: anchorStyle.fontWeight || 'normal',
            letterSpacing: anchorStyle.letterSpacing || 0,
            lineHeight: anchorStyle.lineHeight || 1.3,
            textAlign: anchorStyle.textAlign || 'left'
          })
          // 锚点有几何时按保存位置还原；无几何时依次向下顺排，永不回到 (0,0)
          var hasGeom = anchorStyle.x != null && anchorStyle.y != null &&
            isFinite(anchorStyle.x) && isFinite(anchorStyle.y)
          var x, y
          if (hasGeom) {
            x = Number(anchorStyle.x)
            y = Number(anchorStyle.y)
          } else if (flow) {
            x = lastRight > 0 ? lastRight + SPEC_BLOCK_GAP : 20
            y = lastBottom > 0 ? lastBottom + 4 : 10
            if (x + layout.width > labelWidth - 10) {
              x = 20
              y = lastBottom > 0 ? lastBottom + 4 : 10
            }
          } else {
            x = 20
            y = lastBottom > 0 ? lastBottom + 6 : 10
          }
          lastRight = Math.max(lastRight, x + layout.width)
          lastBottom = Math.max(lastBottom, y + layout.height)
          views.push({
            id: 'view-' + anchorKey,
            anchorKey: anchorKey,
            nameKey: nameKey,
            keys: keys,
            active: keys.some(function(key) { return self.selectedKeys.indexOf(key) > -1 }),
            items: items,
            layout: layout,
            frameStyle: {
              left: x + 'px',
              top: y + 'px',
              width: layout.width + 'px',
              height: layout.height + 'px',
              fontSize: (anchorStyle.fontSize || 12) + 'px',
              fontWeight: anchorStyle.fontWeight || 'normal',
              letterSpacing: (anchorStyle.letterSpacing || 0) + 'px',
              lineHeight: String(anchorStyle.lineHeight || 1.3),
              textAlign: layout.textAlign,
              padding: '0 ' + SPEC_BLOCK_PAD_X + 'px',
              boxSizing: 'border-box',
              whiteSpace: 'nowrap',
              display: 'block'
            }
          })
        }
        // 归一化：类别、规格拆成两个独立元素，可分别选中与定位
        var normalized = this.normalizedCategorySpecGroup
        if (normalized) {
          if (showCategory) {
            var categoryItems = buildSpecBlockItems({
              categoryText: normalized.label,
              nameText: '',
              namePlaceholder: '',
              nameWrap: false,
              values: []
            })
            pushView(normalized.nameKeys[0] || '', normalized.nameKeys[0] || '', normalized.nameKeys, categoryItems, false)
          }
          var specItems = buildSpecBlockItems({
            categoryText: '',
            nameText: '',
            namePlaceholder: '',
            nameWrap: false,
            values: [{ key: 'normalized', text: normalized.valueLabel }]
          })
          pushView(normalized.valueKeys[0] || normalized.anchorKey, normalized.valueKeys[0] || '', normalized.valueKeys, specItems, false)
        }
        // 旧模板中无品类的规格块：沿用原「属性名 + 值」渲染
        var blocks = {}
        var order = []
        this.selectedAttrs.forEach(function(key) {
          var item = parseSpecItemKey(key)
          if (!item || (item.categoryId != null && item.categoryId !== '')) return
          var id = item.rowNo + '-' + (item.blockIdentity || item.blockNo)
          if (!blocks[id]) {
            blocks[id] = { id: id, keys: [] }
            order.push(id)
          }
          blocks[id].keys.push(key)
        })
        order.forEach(function(id) {
          var block = blocks[id]
          var parsed = parseSpecItemKey(block.keys[0])
          var nameKey = specNameKey(parsed.rowNo, parsed.blockNo)
          var nameAttr = self.allAttrs.find(function(a) { return a.key === nameKey })
          var hasName = block.keys.indexOf(nameKey) > -1
          var nameText = ''
          if (nameAttr && hasName) {
            var savedName = Object.prototype.hasOwnProperty.call(self.attrLabels, nameKey) ? self.attrLabels[nameKey] : null
            nameText = String(savedName != null && savedName !== ''
              ? savedName
              : (nameAttr.value || nameAttr.label || '')).replace(/：$/, '').trim()
          }
          var namePlaceholder = ''
          if (!nameText && hasName) {
            namePlaceholder = String((nameAttr && (nameAttr.specSourceName || nameAttr.label)) || '').trim() || '规格'
          }
          var anchorKey = nameAttr ? nameKey : block.keys[0]
          var anchorStyle = self.attrStyles[anchorKey] || {}
          var values = []
          block.keys.forEach(function(key) {
            if (key === nameKey) return
            var attr = self.allAttrs.find(function(a) { return a.key === key })
            values.push({ key: key, text: attr ? attr.value : '' })
          })
          var items = buildSpecBlockItems({
            nameKey: nameKey,
            nameText: nameText ? nameText + '：' : '',
            namePlaceholder: namePlaceholder,
            nameWrap: !!anchorStyle.specWrap,
            values: values
          })
          pushView(anchorKey, nameKey, block.keys, items, true)
        })
        return views
      },
      // 规格块几何所在的锚点：优先属性名（名称关闭时位置仍由名称锚点保存）
      specBlockStyleKey() {
        var block = this.currentSpecBlock
        if (!block) return ''
        if (block.nameKey && this.allAttrs.some(a => a.key === block.nameKey)) return block.nameKey
        return block.valueKeys.find(key => !!this.attrStyles[key]) || ''
      },
      // 锚点还没有样式时，用块内首个已有样式兜底，保证写样式不丢位置
      specBlockFallbackStyle() {
        var block = this.currentSpecBlock
        if (!block) return null
        var self = this
        var found = null
        block.valueKeys.some(function(key) {
          if (self.attrStyles[key]) {
            found = self.attrStyles[key]
            return true
          }
          return false
        })
        return found
      },
      specBlockWrap() {
        var key = this.specBlockStyleKey
        return !!(key && this.attrStyles[key] && this.attrStyles[key].specWrap)
      },
      specBlockAlign() {
        var key = this.specBlockStyleKey
        var align = key && this.attrStyles[key] ? this.attrStyles[key].textAlign : ''
        return align === 'center' || align === 'right' ? align : 'left'
      },
      specNameText() {
        var block = this.currentSpecBlock
        if (!block || !block.nameKey) return ''
        // 面板输入以已保存的属性名为准，空值即空（界面按浅灰占位显示）
        if (Object.prototype.hasOwnProperty.call(this.attrLabels, block.nameKey)) {
          return String(this.attrLabels[block.nameKey] == null ? '' : this.attrLabels[block.nameKey]).replace(/：$/, '')
        }
        var attr = this.allAttrs.find(a => a.key === block.nameKey)
        return attr ? String(attr.value || attr.label || '').replace(/：$/, '') : ''
      },
      // 规格块内已显示的元素（属性名 + 各规格值），字体样式同时作用于它们
      specBlockKeys() {
        var block = this.currentSpecBlock
        if (!block) return []
        var keys = []
        if (block.nameKey && this.isAttrSelected(block.nameKey)) keys.push(block.nameKey)
        block.valueKeys.forEach(key => {
          if (this.isAttrSelected(key)) keys.push(key)
        })
        return keys
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
    watch: {
      // 取消选中后自动收起属性面板
      selectedKeys(val) {
        var self = this
        if (!val.length && this.sidePanel === 'attr') {
          this.sidePanel = ''
          this.$nextTick(function() {
            centerScroll(self.$refs.previewWrap, self, 'wrapScrolling')
          })
          return
        }
        if (this.sidePanel === 'attr') this.panSelectionIntoView()
      },
      currentAttrKey() {
        if (this.sidePanel === 'attr') this.panSelectionIntoView()
      },
      zoom() {
        if (this.sidePanel !== 'attr') return
        var self = this
        setTimeout(function() { self.centerAndPan() }, 80)
      }
    },
    created() {
      // 拖动 / 缩放 / 选中切换的唯一入口
      this._labelDrag = createLabelElementDrag(this)
    },
    mounted() {
      this.initEditor()
      // 标签居中 + 对称滑动：用 label-scroll 模块
      setupLabelScroll(this, 'previewWrap', 'labelWidth', 'zoom', 'wrapScrolling')
    },
    beforeDestroy() {
      this._labelDrag.destroy()
      this.unbindMarqueeEvents()
      if (this._labelScrollResize && typeof window !== 'undefined') {
        window.removeEventListener('resize', this._labelScrollResize)
        this._labelScrollResize = null
      }
    },
    methods: {
      initEditor() {
        var type = this.initialType || 'label'
        var cfg = TYPE_CONFIG[type] || TYPE_CONFIG.label
        // 从打印预览页点「编辑」返回时，恢复当时的商品数据和模板，避免编辑页字段全空
        var context = null
        try { context = JSON.parse(sessionStorage.getItem('jsh.labelEdit.context') || 'null') } catch (e) { context = null }
        if (context) {
          try { sessionStorage.removeItem('jsh.labelEdit.context') } catch (e) { /* 忽略 */ }
          // 编辑态仅消费字段结构所需的上下文；小票入口只传 categoryId，不含单据业务值。
          if (context.record) this.record = context.record
          if (context.sku) this.sku = context.sku
        }
        this.templateType = type
        // 业务标识：来源页优先，其次按类型兜底（商品/发货单/会员）
        this.businessType = this.initialBusinessType || (context && context.businessType) ||
          (type === 'delivery' ? 'delivery' : (type === 'label' ? 'product' : (type === 'member' ? 'member' : '')))
        this.labelWidth = cfg.defaultWidth
        this.labelHeight = cfg.defaultHeight
        this.deliveryTemplate = null
        var liveEditorData = context && context.editorData && Array.isArray(context.editorData.fields) && context.editorData.fields.length
          ? normalizeEditorData(context.editorData)
          : null
        this.editorDataFromForm = !!liveEditorData
        this.editorDataRestored = false
        this.editorFormTitle = liveEditorData ? liveEditorData.title : ''
        this.allAttrs = liveEditorData
          ? this.appendPageAttr(liveEditorData.fields.map(function(field) { return Object.assign({ value: '' }, field) }))
          : this.buildAttrs().map(function(attr) { return Object.assign({}, attr, { value: attr.isCustom ? attr.value : '' }) })
        if (liveEditorData && liveEditorData.specBlocks.length) this.specBlocks = liveEditorData.specBlocks
        var loaded
        // 只接受当前渲染代次的模板快照，旧格式（含打印页传入的旧态）一律按旧模板提示删除
        var hasContextTemplate = !!(context && context.template && context.template.selectedAttrs &&
          Number(context.template.renderVersion) === TEMPLATE_RENDER_VERSION)
        if (hasContextTemplate) {
          // 以打印预览当前使用的模板（已补全字段与样式）为准，保证编辑框与预览一致
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
          // 小票规格块来自商品品类设置：先取到品类配置，再按规格块拆分商品元素
          if ((self.templateType === 'receipt' || self.templateType === 'label' || self.templateType === 'address') && !self.categoryTree.length) {
            self.loadCategoryTree().then(function() {
              self.applyCategoryEditElements()
            }, function() {
              self.applyCategoryEditElements()
            })
            return
          }
          self.applyCategoryEditElements()
        })
      },
      /**
       * 小票：把商品数据拆成「每件商品 × 每个字段」的独立元素
       * 明细表/商品组合/遗留商品字段由 replaceReceiptGoodsWithItems 统一移除
       */
      applyReceiptItemElements() {
        if (this.templateType !== 'receipt') return
        // 编辑的是模板：已建立映射表时只按映射表生成规格块，否则按商品品类设置；
        // 不使用单据明细推导，单据值只在预览/打印阶段注入
        var mappingBlocks = this.specMappingReady ? flattenSpecMapping(this.specMapping) : []
        var blocks = mappingBlocks.length ? mappingBlocks : this.categorySpecBlocksForRows()
        if (!blocks.length && !this.specMappingReady) blocks = resolveEditSpecBlocks(this.specBlocks, [])
        this.specBlocks = blocks
        // 商品名称/数量/单价/金额仍按属性名占位，不带业务值
        var rows = [this.editGoodsRow()]
        var fallbackY = 0
        var headerKeys = ['rc_title', 'rc_no', 'rc_date', 'rc_member']
        var self = this
        headerKeys.forEach(function(key) {
          var style = self.attrStyles[key]
          if (style) fallbackY = Math.max(fallbackY, (style.y || 0) + (style.height || 20))
        })
        var next = replaceReceiptGoodsWithItems({
          rows: rows,
          specBlocks: blocks,
          useSpecBlockPreset: true,
          selectedAttrs: this.selectedAttrs,
          attrStyles: this.attrStyles,
          attrLabels: this.attrLabels,
          paperWidth: this.labelWidth,
          fallbackY: fallbackY ? fallbackY + 8 : 100
        })
        this.allAttrs = this.allAttrs.filter(function(attr) { return !isReceiptItemKey(attr.key) })
        next.attrs.forEach(function(attr) { self.allAttrs.push(attr) })
        this.selectedAttrs = next.selectedAttrs
        this.attrStyles = next.attrStyles
        this.attrLabels = next.attrLabels
        this.dynamicItemKeys = next.itemKeys
        // 模板里保存了关闭的规格名/规格值：重建后仍保持隐藏（按属性维度跨商品行生效）
        if (this.hiddenItemKeys && this.hiddenItemKeys.length) {
          this.selectedAttrs = this.selectedAttrs.filter(function(key) {
            return !isReceiptItemHidden(self.hiddenItemKeys, key)
          })
        }
      },
      /** 商品类表单与小票共用类别规格块；商品标签只扩充规格属性，不带入当前商品值。 */
      applyCategoryEditElements() {
        if (this.templateType === 'receipt') {
          this.applyReceiptItemElements()
          // 小票同样使用通用「类别 / 规格」元素：规格数据由规格映射器在预览/打印阶段注入
          var receiptBlocks = this.specMappingReady
            ? normalizeSpecBlocks(flattenSpecMapping(this.specMapping))
            : normalizeSpecBlocks(this.specBlocks)
          if (!receiptBlocks.length) this.removeCategorySpecElements()
          else this.applyCategorySpecLabels(receiptBlocks)
          this.fitLabelAfterLoad()
          return
        }
        if (this.templateType !== 'label' && this.templateType !== 'address') return
        // 类别 / 规格为标准表单元素：品类没有规格配置时也进入字段清单与元素面板
        // 已建立数据映射表时以映射表为准（只加载已选规格）；未建立时按品类配置展示
        if (this.specMappingReady) {
          var mappedBlocks = normalizeSpecBlocks(flattenSpecMapping(this.specMapping))
          this.specBlocks = mappedBlocks
          if (!mappedBlocks.length) {
            // 未选择任何规格：不生成类别/规格元素
            this.removeCategorySpecElements()
            return
          }
          this.applyCategorySpecLabels(mappedBlocks)
          return
        }
        var blocks = this.categorySpecBlocksForRows()
        if (!blocks.length) blocks = resolveEditSpecBlocks(this.specBlocks, [])
        this.specBlocks = blocks
        if (!blocks.length) {
          // 未选择任何规格：不生成类别/规格元素
          this.removeCategorySpecElements()
          return
        }
        this.applyCategorySpecLabels()
      },
      /**
       * 把品类规格块归一化为标准「类别 / 规格」两个元素（各表单共用）
       * 移除第 1 行规格元素，按原规格块位置插入 label_category / label_spec
       */
      applyCategorySpecLabels(blocks) {
        var fallbackBlocks = Array.isArray(blocks) ? blocks : []
        var legacySpecKeys = ['spec', 'specDisplay']
        var anchorKey = legacySpecKeys.filter((key) => this.selectedAttrs.indexOf(key) > -1)[0]
        if (!anchorKey) {
          anchorKey = this.selectedAttrs.filter(function(key) {
            var parsed = parseSpecItemKey(key)
            return parsed && parsed.rowNo === 1
          })[0]
        }
        if (!anchorKey && fallbackBlocks.length) {
          anchorKey = this.selectedAttrs.filter(function(key) {
            var parsed = parseSpecItemKey(key)
            return !!parsed
          })[0]
        }
        var anchor = anchorKey && this.attrStyles[anchorKey]
        var startY = anchor ? (anchor.y || 0) : this.selectedAttrs.reduce((max, key) => {
          var style = this.attrStyles[key] || {}
          return Math.max(max, (style.y || 0) + (style.height || 20) + 6)
        }, 10)
        // 品类规格归一化为两个独立标签：类别、规格，共用基础样式，各自保存位置
        var baseStyle = {
          x: anchor ? (anchor.x || 20) : 20,
          y: startY,
          width: Math.max(90, this.labelWidth - 40),
          fontSize: anchor ? (anchor.fontSize || 12) : 12,
          fontWeight: anchor ? (anchor.fontWeight || 'normal') : 'normal',
          letterSpacing: anchor ? (anchor.letterSpacing || 0) : 0,
          lineHeight: anchor ? (anchor.lineHeight || 1.3) : 1.3,
          textAlign: 'left'
        }
        var lineHeightPx = Math.max(1, Math.round(baseStyle.fontSize * baseStyle.lineHeight))
        var categoryKey = LABEL_CATEGORY_KEY
        var specKey = LABEL_SPEC_KEY
        var availableWidth = Math.max(60, this.labelWidth - baseStyle.x - 10)
        var categoryStyle = Object.assign({}, baseStyle, { width: availableWidth, height: lineHeightPx })
        var specStyle = Object.assign({}, baseStyle, {
          y: baseStyle.y + lineHeightPx + 2,
          width: availableWidth,
          height: lineHeightPx
        })
        var plainAttrs = buildCategorySpecAttrs()
        var self = this
        // 移除旧的品类规格属性（rowNo=1），改为两个独立标签
        this.allAttrs = this.allAttrs.filter(function(attr) {
          if (attr.key === categoryKey || attr.key === specKey) return false
          var parsed = parseSpecItemKey(attr.key)
          return !parsed || parsed.rowNo !== 1
        })
        plainAttrs.forEach(function(attr) {
          self.allAttrs.push(attr)
          if (!self.attrStyles[attr.key]) {
            self.$set(self.attrStyles, attr.key, attr.key === categoryKey ? categoryStyle : specStyle)
          }
          if (self.attrLabels[attr.key] === undefined) self.$set(self.attrLabels, attr.key, attr.value)
        })
        var oldSpecKeys = this.selectedAttrs.filter(function(key) {
          var parsed = parseSpecItemKey(key)
          return parsed && parsed.rowNo === 1
        })
        var insertIndex = oldSpecKeys.length ? this.selectedAttrs.indexOf(oldSpecKeys[0]) : this.selectedAttrs.length
        if (insertIndex < 0) insertIndex = this.selectedAttrs.length
        this.selectedAttrs = this.selectedAttrs.filter(function(key) {
          return key !== categoryKey && key !== specKey && oldSpecKeys.indexOf(key) === -1
        })
        this.selectedAttrs.splice(insertIndex, 0, categoryKey, specKey)
        var dynamic = (this.dynamicItemKeys || []).filter(function(key) {
          return key !== categoryKey && key !== specKey &&
            self.allAttrs.some(function(attr) { return attr.key === key })
        })
        dynamic.push(categoryKey, specKey)
        this.dynamicItemKeys = dynamic
        if (this.hiddenItemKeys && this.hiddenItemKeys.length) {
          this.selectedAttrs = this.selectedAttrs.filter(function(key) {
            return !isReceiptItemHidden(self.hiddenItemKeys, key)
          })
        }
        this.fitLabelAfterLoad()
      },
      /** 未选择任何规格：移除归一化的类别 / 规格元素（保留样式，重新选择时沿用） */
      removeCategorySpecElements() {
        var self = this
        var keys = [LABEL_CATEGORY_KEY, LABEL_SPEC_KEY]
        this.allAttrs = this.allAttrs.filter(function(attr) { return keys.indexOf(attr.key) === -1 })
        this.selectedAttrs = this.selectedAttrs.filter(function(key) { return keys.indexOf(key) === -1 })
        this.hiddenItemKeys = this.hiddenItemKeys.filter(function(key) { return keys.indexOf(key) === -1 })
        this.dynamicItemKeys = (this.dynamicItemKeys || []).filter(function(key) { return keys.indexOf(key) === -1 })
        keys.forEach(function(key) { self.$delete(self.formDataCheckedKeys, key) })
        if (keys.indexOf(this.currentAttrKey) > -1) this.currentAttrKey = null
      },
      /**
       * 表单数据确定后归一化回填元素面板：
       * 数据映射表有条目 → 「类别」「规格」两个元素进入面板；映射表为空 → 移除
       */
      applySpecPanelSelection() {
        var self = this
        var keys = [LABEL_CATEGORY_KEY, LABEL_SPEC_KEY]
        if (!specMappingHasSelection(this.specMapping)) {
          this.removeCategorySpecElements()
          return
        }
        buildCategorySpecAttrs().forEach(function(def) {
          var exist = self.allAttrs.filter(function(attr) { return attr.key === def.key })[0]
          if (!exist) self.allAttrs.push(def)
          if (self.selectedAttrs.indexOf(def.key) === -1) self.selectedAttrs.push(def.key)
          self.hiddenItemKeys = self.hiddenItemKeys.filter(function(key) { return key !== def.key })
          self.$set(self.formDataCheckedKeys, def.key, true)
          if (self.attrLabels[def.key] === undefined) self.$set(self.attrLabels, def.key, def.value)
          if (!self.attrStyles[def.key]) {
            var y = 10
            self.selectedAttrs.forEach(function(key) {
              if (keys.indexOf(key) > -1) return
              var style = self.attrStyles[key] || {}
              y = Math.max(y, (style.y || 0) + (style.height || 20) + 6)
            })
            self.$set(self.attrStyles, def.key, {
              x: 20, y: y, width: Math.max(90, self.labelWidth - 40), height: 0,
              fontSize: 12, fontWeight: 'normal', letterSpacing: 0, lineHeight: 1.3, textAlign: 'left'
            })
          }
        })
        var dynamic = (this.dynamicItemKeys || []).slice()
        keys.forEach(function(key) {
          if (dynamic.indexOf(key) === -1) dynamic.push(key)
        })
        this.dynamicItemKeys = dynamic
        this.fitLabelAfterLoad()
      },
      editGoodsRow() {
        return { name: '商品名称', qty: '数量', price: '单价', amount: '金额' }
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
          if (!self.editorDataFromForm && !self.editorDataRestored && !(context && context.template && context.template.editorData && context.template.editorData.fields && context.template.editorData.fields.length)) {
            self.applyProviderData(self.provider, false)
          }
        })
      },
      /** 手动切换数据表：按新表重建字段，并清理新表中不存在的已选字段 */
      onProviderChange(provider) {
        this.provider = provider || ''
        this.applyProviderData(this.provider, true)
      },
      /**
       * 按数据表加载字段清单；编辑模板只读取字段结构，不读取单据业务值。
       * rebuild=true 表示用户主动切换：清理新表里不存在的已选字段（自定义标签始终保留）
       */
      applyProviderData(provider, rebuild) {
        var self = this
        if (!rebuild && (this.editorDataFromForm || this.editorDataRestored)) return Promise.resolve()
        if (!provider) {
          this.allAttrs = this.buildAttrs()
          return Promise.resolve()
        }
        return fetchProviderFields(provider).then(function(result) {
          if (self.provider !== provider) return
          var fields = result || []
          if (!fields.length) return
          var list = fields.filter(function(field) {
            // 数组明细交给模板的循环渲染，不作为文本字段
            return field.type !== 'array'
          }).map(function(field) {
            return {
              key: field.key,
              label: field.label || field.key,
              comment: field.comment || '',
              value: '',
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
      /** 从表单字段配置取全域字段（与打印预览页同源），再补分页标签元素 */
      buildAttrs() {
        var list = buildFormFieldAttrs(this.templateType, { record: this.record, sku: this.sku })
        return this.appendPageAttr(list)
      },
      // 分页标签：作为模板元素常驻字段清单（未勾选时不在画布上显示）
      appendPageAttr(list) {
        var arr = list || []
        var exists = arr.some(function(a) { return a.key === 'page_no' })
        if (!exists) {
          arr.push({ key: 'page_no', label: '分页标签', value: '', isCustom: true, isPage: true, group: '模板元素' })
        }
        return arr
      },
      // 分页标签的示例文案：每件一张时按商品行数估算页数，其余场景按 1 页
      pageLabelText() {
        var rows = (this.record && this.record.goodsRows) || []
        var total = this.effectivePageMode === 'each'
          ? Math.max(1, rows.filter(function(r) { return hasGoodsValue(r) }).length)
          : 1
        return '1/' + total
      },
      // 打印设置：分页 / 每件一张默认带页码，多行连续则移除页码标签
      onPageModeChange(e) {
        // 已勾选分页打印对象时固定为商品分页
        if (this.flowElementKeys.length > 0) return
        var val = e && e.target ? e.target.value : e
        this.pageMode = val === 'fit' ? 'fit' : 'none'
        this.setPageLabel(this.pageMode !== 'none')
      },
      // 分页打印对象：点击卡片切换是否参与商品分页
      toggleFlowElement(key) {
        var wasEmpty = this.flowElementKeys.length === 0
        var idx = this.flowElementKeys.indexOf(key)
        if (idx > -1) this.flowElementKeys.splice(idx, 1)
        else this.flowElementKeys.push(key)
        // 首次勾选进入商品分页时，默认带上分页标签
        if (wasEmpty && this.flowElementKeys.length) this.setPageLabel(true)
      },
      onPageLabelToggle(e) {
        var checked = e && e.target ? e.target.checked : !!e
        this.setPageLabel(checked)
      },
      // 添加/移除分页标签元素；silent 用于模板初始化，不额外产生撤销记录
      setPageLabel(on, silent) {
        var key = 'page_no'
        var exists = this.selectedAttrs.indexOf(key) > -1
        if (on) {
          if (exists) return
          this.addAttr(key)
          // 只显示页码本身，不带“属性名:”前缀
          this.$set(this.attrLabels, key, '')
          var style = this.attrStyles[key]
          if (style) {
            this.$set(style, 'x', Math.max(2, this.labelWidth - 78))
            this.$set(style, 'y', Math.max(2, this.labelHeight - 18))
            this.$set(style, 'width', 70)
            this.$set(style, 'fontSize', 10)
          }
          if (!silent) this.pushSnapshot()
          return
        }
        if (!exists) return
        this.removeAttr(key)
        if (!silent) this.pushSnapshot()
      },
      /** 统一的模板加载入口：按 storage 类型从统一模板流程读取（只支持 v2） */
      loadTemplate(cfg, skipApply) {
        var self = this
        return resolveTemplate(cfg, { templateId: this.initialTemplateId }).then(function(res) {
          if (Array.isArray(res.list)) self.templates = res.list
          if (skipApply) {
            if (cfg.storage === 'deliveryTemplate' && res.template) self.deliveryTemplate = res.template
            return
          }
          if (res.template) {
            // 统一格式为 v2：编辑页按同一入口还原成编辑态
            self.applyTemplateSnapshot(v2ToEditorState(res.template), cfg)
            if (cfg.storage === 'localTemplates') self.selectedTemplateId = res.template.id
            if (cfg.storage === 'deliveryTemplate') self.deliveryTemplate = res.template
            return
          }
          if (res.outdated && res.outdated.length) {
            if (cfg.storage === 'localTemplates') self.selectedTemplateId = null
            if (cfg.storage === 'deliveryTemplate') self.deliveryTemplate = null
            self.promptOutdatedTemplate(res.outdated[0], cfg)
            return
          }
          // 标签多模板：没有可用模板时保留当前默认元素，其余类型回到默认布局
          if (cfg.storage === 'localTemplates') return
          self.applyDefaultLayout(cfg)
        })
      },
      /**
       * 旧模板不再兼容：提示删除后重建
       * platformKey 表示模板存在 platformConfig（无删除接口，写空值即视为删除）
       */
      promptOutdatedTemplate(tpl, cfg) {
        var self = this
        var name = (tpl && tpl.name) || '该模板'
        var done = function() {
          self.$message.success('旧模板已删除')
          self.applyDefaultLayout(cfg || TYPE_CONFIG[self.templateType] || TYPE_CONFIG.label)
        }
        this.$confirm({
          title: '有旧模板',
          content: '模板「' + name + '」为旧结构，已不支持，是否删除？',
          okText: '删除',
          cancelText: '取消',
          onOk: function() {
            var task = deleteTemplate(cfg, tpl)
            return Promise.resolve(task).then(done).catch(function() {
              self.$message.error('删除失败，请稍后重试')
            })
          }
        })
      },
      /** 把持久化快照应用到当前编辑状态 */
      applyTemplateSnapshot(snap, cfg) {
        var self = this
        // 无表单实时结构时，模板保存的编辑数据负责还原属性面板；有表单上下文则以当前字段结构为准。
        if (!this.editorDataFromForm && snap.editorData && Array.isArray(snap.editorData.fields) && snap.editorData.fields.length) {
          var savedEditorData = normalizeEditorData(snap.editorData)
          this.allAttrs = this.appendPageAttr(savedEditorData.fields.map(function(field) { return Object.assign({ value: '' }, field) }))
          if (savedEditorData.specBlocks.length) this.specBlocks = savedEditorData.specBlocks
          this.editorFormTitle = savedEditorData.title
          this.editorDataRestored = true
        }
        this.selectedAttrs = JSON.parse(JSON.stringify(snap.selectedAttrs))
        this.attrStyles = JSON.parse(JSON.stringify(snap.attrStyles || {}))
        this.attrLabels = JSON.parse(JSON.stringify(snap.attrLabels || {}))
        this.hiddenItemKeys = Array.isArray(snap.hiddenItemKeys) ? snap.hiddenItemKeys.slice() : []
        // 「表单数据」勾选项：旧模板没有该字段时按当前画布元素还原
        var checkedKeys = Array.isArray(snap.formDataCheckedKeys) && snap.formDataCheckedKeys.length
          ? snap.formDataCheckedKeys
          : snap.selectedAttrs
        var checkedMap = {}
        ;(Array.isArray(checkedKeys) ? checkedKeys : []).forEach(function(key) { checkedMap[key] = true })
        this.formDataCheckedKeys = checkedMap
        // 规格映射表为唯一权威：有映射时 specBlocks 由映射表扁平派生；
        // 模板没有映射表（旧的 specBlocks 结构）时沿用已保存的规格块，按旧路径取值
        this.specMapping = normalizeSpecMapping(snap.specMapping)
        this.specBlocks = this.specMapping.length
          ? normalizeSpecBlocks(flattenSpecMapping(this.specMapping))
          : normalizeSpecBlocks(snap.specBlocks)
        this.specMappingReady = snap.specMappingReady === true && this.specMapping.length > 0
        // 自定义文本的值随模板保存，加载时合并回属性列表
        if (Array.isArray(snap.customLabels)) {
          snap.customLabels.forEach(function(custom) {
            var exist = self.allAttrs.find(function(a) { return a.key === custom.key })
            if (exist) {
              exist.value = custom.value
              exist.label = custom.label || exist.label
            } else {
              self.allAttrs.push(JSON.parse(JSON.stringify(custom)))
            }
          })
        }
        // 旧模板没有 goodsFields：保留当前默认（全部字段）
        if (Array.isArray(snap.goodsFields)) this.goodsFields = snap.goodsFields.slice()
        var width = snap.width || snap.labelWidth
        var height = snap.height || snap.labelHeight
        if (width) this.labelWidth = width
        if (height) this.labelHeight = height
        // 顶栏展示当前模板名称
        if (snap.name) this.currentTemplateName = snap.name
        this.overflowMode = snap.overflowMode === 'ellipsis' ? 'ellipsis' : 'wrap'
        // 商品分页由「分页打印」勾选对象数量决定，这里只还原单页/自动分页
        this.pageMode = snap.pageMode === 'fit' ? 'fit' : 'none'
        this.maxRowsPerPage = Math.max(0, parseInt(snap.maxRowsPerPage, 10) || 0)
        this.repeatHeader = snap.repeatHeader !== false
        // 「分页打印」勾选对象只服务于商品分页：旧模板按原打印方式还原，其余方式清空
        var flow = Array.isArray(snap.flowElementKeys)
          ? snap.flowElementKeys.filter(function(key) { return self.selectedAttrs.indexOf(key) >= 0 })
          : []
        if (snap.pageMode === 'each' && !flow.length) {
          flow = this.selectedAttrs.filter(function(key) { return key === GOODS_KEY || isGoodsTableKey(key) })
        }
        this.flowElementKeys = snap.pageMode === 'each' ? flow : []
        this.restoreMissingAttrs(snap.selectedAttrs)
        this.fitLabelAfterLoad()
        // 打印方式为分页时，分页标签默认选中（旧模板缺该元素则自动补上）
        if (this.effectivePageMode !== 'none' && this.selectedAttrs.indexOf('page_no') === -1) {
          this.setPageLabel(true, true)
        }
      },
      /** 通用默认布局：从 cfg.defaultKeys 挑字段，从上到下依次排 */
      applyDefaultLayout(cfg) {
        cfg = cfg || TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
        this.hiddenItemKeys = []
        this.specBlocks = []
        this.specMapping = []
        this.specMappingReady = false
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
        // 默认布局对应「表单数据」勾选状态即画布元素
        var checkedMap = {}
        selected.forEach(function(key) { checkedMap[key] = true })
        this.formDataCheckedKeys = checkedMap
        // 默认不勾选分页打印对象；勾选任意对象即进入商品分页
        this.flowElementKeys = []
        // 默认布局没有已保存模板，顶栏使用默认模板名称
        this.currentTemplateName = cfg.title.replace('编辑', '') + '模板'
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
      getElementStyle(key) {
        // 使用统一布局结果：商品明细 / 文字折行 / 上下归位后的实际坐标与高度
        var resolved = isSpecItemKey(key) ? null : this.labelLayout.styles[key]
        var style = resolved || this.attrStyles[key] || {}
        var result = {
          left: (style.x || 0) + 'px',
          top: (style.y || 0) + 'px',
          fontSize: (style.fontSize || 12) + 'px',
          fontWeight: style.fontWeight || 'normal',
          letterSpacing: (style.letterSpacing || 0) + 'px',
          lineHeight: String(style.lineHeight || 1.3)
        }
        if (isGoodsTableKey(key)) {
          var table = this.goodsTableData
          result.width = ((table && table.width) || style.width || 0) + 'px'
          result.padding = '0'
          result.whiteSpace = 'normal'
          return result
        }
        // 与打印预览保持一致：未设置宽度时按「标签宽 - 左边距」折行
        var wrapWidth = style.width > 0 ? style.width : Math.max(20, this.labelWidth - (style.x || 0))
        result.width = wrapWidth + 'px'
        result.whiteSpace = 'pre-wrap'
        result.wordBreak = 'break-all'
        result.overflowWrap = 'anywhere'
        result.display = 'block'
        result.padding = '0'
        // 指定高度时由内容层裁切，元素本身保持 overflow:visible，避免裁掉手柄
        if (style.height) {
          result.height = style.height + 'px'
        }
        return result
      },
      isCode(key) {
        return isCodeKey(key)
      },
      isGoodsTable(key) {
        return isGoodsTableKey(key)
      },
      // 文字类元素：宽度可拖到文字自然宽度以下触发折行，高度由内容自动撑开
      isTextElem(key) {
        return !this.isCode(key) && !this.isGoodsTable(key) && key !== 'dn_table'
      },
      isGoodsKey(key) {
        return key === GOODS_KEY
      },
      /** 打印设置里展示的元素名称 */
      flowElementLabel(key) {
        if (this.attrLabels && this.attrLabels[key]) return this.attrLabels[key]
        var attr = this.allAttrs.find(function(a) { return a.key === key })
        return attr && attr.label ? attr.label : key
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
        var style = this.attrStyles[this.goodsTableKey] || {}
        return Math.round((style.fontSize || 12) * (style.lineHeight || 1.2)) + 4
      },
      clearSelection() {
        this.selectedKeys = []
        this.currentAttrKey = null
        this.multiSelectMode = false
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
        this.pushSnapshot()
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
        this.pushSnapshot()
      },
      // 规格块整块删除：移除属性名 + 全部规格值，与单项删除行为一致
      removeSpecBlock(frame) {
        var self = this
        var keys = (frame && frame.keys) || []
        keys.forEach(function(key) {
          var idx = self.selectedAttrs.indexOf(key)
          if (idx !== -1) self.selectedAttrs.splice(idx, 1)
          self.$delete(self.attrStyles, key)
          self.$delete(self.attrLabels, key)
          var sIdx = self.selectedKeys.indexOf(key)
          if (sIdx !== -1) self.selectedKeys.splice(sIdx, 1)
        })
        if (keys.indexOf(this.currentAttrKey) > -1) {
          this.currentAttrKey = this.selectedKeys.length ? this.selectedKeys[this.selectedKeys.length - 1] : null
        }
        this.pushSnapshot()
      },
      updateCurrentLabel(e) {
        var val = typeof e === 'string' ? e : (e && e.target ? e.target.value : e)
        if (this.currentAttrKey) this.$set(this.attrLabels, this.currentAttrKey, val)
      },
      isAttrSelected(key) {
        return !!key && this.selectedAttrs.indexOf(key) > -1
      },
      // 是否为规格块内的元素（属性名或规格值）
      isSpecAttrKey(key) {
        return !!parseSpecItemKey(key)
      },
      // 取规格块的全部元素 key：属性名在前，规格值随后
      specBlockKeysOf(key) {
        var parsed = parseSpecItemKey(key)
        if (!parsed) return []
        var rowNo = parsed.rowNo
        var blockNo = parsed.blockNo
        var categoryId = parsed.categoryId
        var keys = []
        var nameKey = specNameKey(rowNo, blockNo, categoryId, parsed.blockIdentity)
        if (this.allAttrs.some(function(a) { return a.key === nameKey })) keys.push(nameKey)
        this.allAttrs.forEach(function(a) {
          var item = parseSpecItemKey(a.key)
          if (item && item.kind === 'value' && item.rowNo === rowNo && item.blockNo === blockNo && String(item.categoryId || '') === String(categoryId || '') && String(item.blockIdentity || '') === String(parsed.blockIdentity || '')) {
            keys.push(a.key)
          }
        })
        return keys
      },
      // 元素面板条目是否已加载（规格块要求块内全部元素都在）
      isElemItemOn(attr) {
        var keys = attr && attr.isSpecBlock ? attr.blockKeys : [attr && attr.key]
        if (!keys || !keys.length) return false
        var self = this
        return keys.every(function(key) { return self.selectedAttrs.indexOf(key) > -1 })
      },
      // 元素面板点击：规格块按整块开关，普通属性按单项开关
      toggleElemItem(attr) {
        if (!attr) return
        if (!attr.isSpecBlock) {
          this.toggleAttr(attr.key)
          return
        }
        var self = this
        var visible = !this.isElemItemOn(attr)
        attr.blockKeys.forEach(function(key) {
          if (self.isAttrSelected(key) !== visible) self.setAttrVisible(key, visible)
        })
      },
      // 规格名/规格值开关：只控制是否参与标签渲染，保留原位置与字号，关闭后可再次打开
      onSpecVisibleChange(key, checked) {
        this.setAttrVisible(key, !!checked)
      },
      setAttrVisible(key, visible) {
        if (!key) return
        if (visible) {
          if (this.selectedAttrs.indexOf(key) === -1) this.selectedAttrs.push(key)
          this.hiddenItemKeys = this.hiddenItemKeys.filter(function(k) { return k !== key })
          if (!this.attrStyles[key]) {
            this.$set(this.attrStyles, key, {
              x: 8, y: 10, width: 0, height: 0,
              fontSize: 12, fontWeight: 'normal', letterSpacing: 0, lineHeight: 1.2
            })
          }
          if (this.attrLabels[key] === undefined) {
            var attr = this.allAttrs.find(a => a.key === key)
            this.$set(this.attrLabels, key, attr ? attr.label : '')
          }
        } else {
          var aIdx = this.selectedAttrs.indexOf(key)
          if (aIdx > -1) this.selectedAttrs.splice(aIdx, 1)
          if (this.hiddenItemKeys.indexOf(key) === -1) this.hiddenItemKeys.push(key)
          // 保留选中态：面板不关闭，可再次打开开关
        }
        this.pushSnapshot()
      },
      // ===== 表单数据弹层 =====
      // 勾选状态以草稿为准，确定后才写回画布
      isFormDataChecked(field) {
        if (!field) return false
        var keys = field.isSpecBlock ? (field.blockKeys || []) : [field.key]
        if (!keys.length) return false
        var self = this
        return keys.every(function(key) { return !!self.formDataDraft[key] })
      },
      formDataValueText(field) {
        if (field.isSpecBlock) return (field.blockKeys ? field.blockKeys.length : 0) + ' 项'
        return field.value == null ? '' : String(field.value)
      },
      openFormDataSheet() {
        var self = this
        var draft = {}
        var checkedKeys = this.formDataCheckedKeys || {}
        this.formDataFieldList.forEach(function(field) {
          var keys = field.isSpecBlock ? (field.blockKeys || []) : [field.key]
          keys.forEach(function(key) {
            // 以「表单数据」勾选态为准，画布上删除过的项仍保持勾选
            self.$set(draft, key, !!checkedKeys[key] || self.selectedAttrs.indexOf(key) > -1)
          })
        })
        // 映射表规格：规格名按是否已建立映射勾选，规格值按映射表逐项勾选
        this.formDataFieldList.forEach(function(field) {
          if (!field.isMapping) return
          var keys = field.blockKeys || []
          if (keys.length) self.$set(draft, keys[0], !!field.mapped)
          var savedValues = Array.isArray(field.savedValues) ? field.savedValues : []
          ;(field.valueItems || []).forEach(function(item) {
            self.$set(draft, item.key, savedValues.indexOf(String(item.label)) > -1)
          })
        })
        this.formDataDraft = draft
        this.formDataKeyword = ''
        // 折叠列表默认展开已选规格的品类，其余收起
        var expanded = {}
        this.formDataFieldList.forEach(function(field) {
          if (!field.isMapping || !field.mapped) return
          expanded[String(field.categoryId || '') || 'uncategorized'] = true
        })
        this.formDataExpanded = expanded
        this.formDataVisible = true
      },
      // 品类折叠 / 展开
      toggleFormDataCategoryExpand(key) {
        if (!key) return
        var expanded = this.formDataExpanded[key] === undefined ? !!this.formDataKeyword : !!this.formDataExpanded[key]
        this.$set(this.formDataExpanded, key, !expanded)
      },
      closeFormDataSheet() {
        this.formDataVisible = false
        this.formDataDraft = {}
        this.formDataKeyword = ''
      },
      toggleFormDataItem(item) {
        if (!item) return
        var next = !this.isFormDataChecked(item)
        var keys = item.isSpecBlock ? (item.blockKeys || []) : [item.key]
        var self = this
        keys.forEach(function(key) { self.$set(self.formDataDraft, key, next) })
      },
      toggleFormDataGroup(group) {
        if (!group) return
        var next = !group.checked
        var self = this
        group.items.forEach(function(item) {
          var keys = item.isSpecBlock ? (item.blockKeys || []) : [item.key]
          keys.forEach(function(key) { self.$set(self.formDataDraft, key, next) })
        })
      },
      // 品类整段勾选/取消（规格名 + 全部规格值）
      toggleFormDataCategory(group) {
        if (!group) return
        var next = !group.checked
        var self = this
        group.items.forEach(function(item) {
          ;(item.blockKeys || []).forEach(function(key) { self.$set(self.formDataDraft, key, next) })
        })
      },
      // 单个规格值 chips 勾选/取消
      toggleFormDataKey(key) {
        if (!key) return
        this.$set(this.formDataDraft, key, !this.formDataDraft[key])
      },
      // 确定：普通字段按「新增默认位置 / 移除并清理样式」写回，规格块整块显示/隐藏且保留样式
      confirmFormDataSheet() {
        var self = this
        var changed = false
        var addPlain = function(key) {
          if (self.selectedAttrs.indexOf(key) > -1) return
          self.selectedAttrs.push(key)
          if (!self.attrStyles[key]) {
            var idx = self.selectedAttrs.length
            self.$set(self.attrStyles, key, {
              x: 10, y: 10 + idx * 30, width: 0, height: 0,
              fontSize: 12, fontWeight: 'normal', letterSpacing: 0, lineHeight: 1.2
            })
          }
          var attr = self.allAttrs.find(function(a) { return a.key === key })
          // 地址打印等表单补充的商品基础属性/SKU 属性可能不在当前数据表字段里，按表单定义补进字段清单
          if (!attr) {
            var field = self.formDataFieldList.filter(function(item) { return item.key === key })[0]
            if (field) {
              attr = {
                key: field.key,
                label: field.label,
                value: field.value == null ? '' : String(field.value),
                isCustom: false,
                group: field.group
              }
              self.allAttrs.push(attr)
            }
          }
          if (self.attrLabels[key] === undefined) {
            self.$set(self.attrLabels, key, attr ? attr.label : '')
          }
        }
        var removePlain = function(key) {
          var idx = self.selectedAttrs.indexOf(key)
          if (idx !== -1) self.selectedAttrs.splice(idx, 1)
          self.$delete(self.attrStyles, key)
          self.$delete(self.attrLabels, key)
          var sIdx = self.selectedKeys.indexOf(key)
          if (sIdx !== -1) self.selectedKeys.splice(sIdx, 1)
          if (self.currentAttrKey === key) {
            self.currentAttrKey = self.selectedKeys.length ? self.selectedKeys[self.selectedKeys.length - 1] : null
          }
        }
        this.formDataFieldList.forEach(function(field) {
          // 映射表规格只更新数据映射表，不写画布元素
          if (field.isMapping) return
          var checked = self.isFormDataChecked(field)
          var selected = self.selectedAttrs.indexOf(field.key) > -1
          if (checked === selected) return
          changed = true
          if (checked) addPlain(field.key)
          else removePlain(field.key)
        })
        // 「表单数据」勾选态固化：元素面板按它列出，画布删除不影响
        this.formDataFieldList.forEach(function(field) {
          // 规格映射项只写入映射表，不进入元素面板勾选键
          if (field.isMapping) return
          var keys = field.isSpecBlock ? (field.blockKeys || []) : [field.key]
          keys.forEach(function(key) {
            if (self.formDataDraft[key]) self.$set(self.formDataCheckedKeys, key, true)
            else self.$delete(self.formDataCheckedKeys, key)
          })
        })
        // 规格选择结果写入数据映射表（品类 / 规格名 / 规格值），预览与打印统一按映射表取值
        var prevMapping = JSON.stringify(this.specMapping)
        this.syncSpecMappingFromSheet()
        if (JSON.stringify(this.specMapping) !== prevMapping) changed = true
        this.specMappingReady = true
        // 确定后按选择回填元素面板：所有规格只归一化为「类别」「规格」两个元素
        this.applySpecPanelSelection()
        if (changed) this.pushSnapshot()
        this.closeFormDataSheet()
      },
      /**
       * 把「表单数据」勾选的规格值写入当前标签的数据映射表（specMapping）
       * 结构：按类别分组，记录该标签从哪个类别读取哪个规格的哪些值；
       * specBlocks 仅由映射表扁平派生，不再单独维护。
       */
      syncSpecMappingFromSheet() {
        var self = this
        var mapping = []
        this.formDataFieldList.forEach(function(field) {
          if (!field.isMapping) return
          var values = (field.valueItems || [])
            .filter(function(value) { return !!self.formDataDraft[value.key] })
            .map(function(value) { return value.label })
          if (!values.length) return
          mapping.push({
            categoryId: field.categoryId,
            categoryName: field.categoryName,
            memberIds: field.categoryMemberIds || [],
            specs: [{
              // 规格名统一使用品类配置名，便于打印时按商品行的规格名精确取值
              name: field.sourceName || field.label,
              values: values,
              sourceName: field.sourceName || field.label,
              sourceValues: field.sourceValues || values
            }]
          })
        })
        this.specMapping = normalizeSpecMapping(mapping)
        this.specBlocks = normalizeSpecBlocks(flattenSpecMapping(this.specMapping))
      },
      updateSpecName(e) {
        var block = this.currentSpecBlock
        if (!block || !block.nameKey) return
        var val = typeof e === 'string' ? e : (e && e.target ? e.target.value : e)
        var clean = String(val || '').replace(/：$/, '')
        var attr = this.allAttrs.find(a => a.key === block.nameKey)
        if (attr) attr.value = clean ? clean + '：' : ''
        // 属性名同时写入标签映射：重新打开模板时按它还原显示名，空值按浅灰占位显示
        this.$set(this.attrLabels, block.nameKey, clean)
        this.pushSnapshot()
      },
      // 类别：品类规格统一显示为通用「类别」，此处可改写
      updateCategoryLabel(e) {
        var key = this.currentCategoryLabelKey
        if (!key) return
        var val = typeof e === 'string' ? e : (e && e.target ? e.target.value : e)
        this.$set(this.attrLabels, key, String(val == null ? '' : val))
        this.pushSnapshot()
      },
      // 规格：品类规格统一显示为通用「规格」，此处可改写
      updateNormalizedSpecLabel(e) {
        var val = typeof e === 'string' ? e : (e && e.target ? e.target.value : e)
        this.$set(this.attrLabels, 'catvalue', String(val == null ? '' : val))
        this.pushSnapshot()
      },
      // 折行：勾选后该属性名后面的属性值整体换到下一行
      onSpecWrapChange(e) {
        var checked = e && e.target ? !!e.target.checked : !!e
        this.setSpecBlockStyle('specWrap', checked)
      },
      // 对齐：整块文本按左齐/居中/右齐显示在边框范围内
      onSpecAlignChange(e) {
        var val = e && e.target ? e.target.value : e
        this.setSpecBlockStyle('textAlign', val === 'center' || val === 'right' ? val : 'left')
      },
      setSpecBlockStyle(field, value) {
        var key = this.specBlockStyleKey
        if (!key) return
        if (!this.attrStyles[key]) {
          // 锚点样式缺失：沿用块内已有位置，避免写排版属性时把块带回左上角
          var fallback = this.specBlockFallbackStyle()
          this.$set(this.attrStyles, key, fallback ? Object.assign({}, fallback) : {})
        }
        this.$set(this.attrStyles[key], field, value)
        this.pushSnapshot()
      },
      // 规格块最小宽度：块内全部属性名排开所需的宽度
      specBlockMinWidth(key) {
        var view = this.specBlockViews.find(function(v) {
          return v.anchorKey === key || v.keys.indexOf(key) > -1
        })
        return view ? Math.max(12, view.layout.minWidth) : 12
      },
      specValueTextOf(key) {
        var attr = this.allAttrs.find(a => a.key === key)
        return attr ? attr.value : ''
      },
      updateSpecValueAt(key, e) {
        if (!key) return
        var val = typeof e === 'string' ? e : (e && e.target ? e.target.value : e)
        var attr = this.allAttrs.find(a => a.key === key)
        if (attr) attr.value = val
      },
      // 字体类字段作用于规格块的属性名 + 全部规格值；位置/尺寸只作用于当前元素
      styleTargets(field) {
        var fontFields = { fontSize: 1, fontWeight: 1, letterSpacing: 1, lineHeight: 1 }
        if (fontFields[field] && this.specBlockKeys.length > 1) return this.specBlockKeys
        return this.selectedKeys
      },
      // 多选批量读取：全部一致返回值，否则 null（输入框显示为空）
      getMultiStyle(field) {
        var keys = this.styleTargets(field)
        if (!keys.length) return null
        var first = this.attrStyles[keys[0]]
        var val = first ? first[field] : undefined
        for (var i = 1; i < keys.length; i++) {
          var s = this.attrStyles[keys[i]]
          if ((s ? s[field] : undefined) !== val) return null
        }
        return val === undefined ? null : val
      },
      // 模板事件桥接：避免模板内联箭头函数导致 Vue 2 编译报错
      onStyleChange(field, val) {
        this.setMultiStyle(field, val)
      },
      // 多选批量写入：应用到全部选中元素（跳过空值，避免破坏定位样式）
      setMultiStyle(field, val) {
        if (val === null || val === undefined || val === '') return
        var self = this
        this.styleTargets(field).forEach(function(key) {
          if (!self.attrStyles[key]) self.$set(self.attrStyles, key, {})
          self.$set(self.attrStyles[key], field, val)
          // 文字类改宽度后高度跟随内容：清掉固定高度，避免折行被裁切
          if (field === 'width' && self.isTextElem(key) && self.attrStyles[key].height) {
            self.$delete(self.attrStyles[key], 'height')
          }
        })
        this.pushSnapshot()
      },
      // 读取元素实际渲染尺寸（offsetWidth/Height 不受 transform 缩放影响）
      getItemSize(key) {
        var el = document.querySelector('.lem-elem[data-key="' + key + '"]')
        return { w: el ? el.offsetWidth : 0, h: el ? el.offsetHeight : 0 }
      },
      // 元素边框矩形（标签坐标系，直接取渲染后的虚线外框，不含手柄、与字号推算无关）
      getItemRect(key) {
        var canvas = this.$refs.labelCanvas
        var el = document.querySelector('.lem-elem[data-key="' + key + '"]')
        if (!canvas || !el) {
          var s = this.attrStyles[key] || {}
          var size = this.getItemSize(key)
          return { key: key, x: s.x || 0, y: s.y || 0, w: size.w, h: size.h }
        }
        var cr = canvas.getBoundingClientRect()
        var er = el.getBoundingClientRect()
        var z = this.zoom || 1
        return {
          key: key,
          x: (er.left - cr.left) / z,
          y: (er.top - cr.top) / z,
          w: er.width / z,
          h: er.height / z
        }
      },
      // 对齐：以选中对象外接矩形为基准 left/center/right/top/middle/bottom
      alignSelected(mode) {
        var self = this
        var keys = this.selectedKeys
        if (keys.length < 2) return
        var rects = keys.map(function(key) {
          return self.getItemRect(key)
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
          return self.getItemRect(key)
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
      // 属性面板打开后，把选中元素和手柄平移到面板左侧
      panSelectionIntoView() {
        var self = this
        this.$nextTick(function() {
          self.ensureSelectionVisible()
        })
      },
      ensureSelectionVisible() {
        var wrap = this.$refs.previewWrap
        var panel = this.$refs.sidePanel
        var key = this.currentAttrKey || this.selectedKeys[this.selectedKeys.length - 1]
        if (!wrap || !panel || !key) return
        var el = wrap.querySelector('.lem-elem[data-key="' + key + '"]')
        if (!el) return
        var elRect = el.getBoundingClientRect()
        var wrapRect = wrap.getBoundingClientRect()
        var visibleRight = panel.getBoundingClientRect().left - 8
        var visibleLeft = wrapRect.left + 8
        if (elRect.left < visibleLeft) {
          var maxLeft = wrap.scrollWidth - wrap.clientWidth
          wrap.scrollLeft = Math.max(0, Math.min(maxLeft, wrap.scrollLeft - (visibleLeft - elRect.left)))
        }
        var overflow = (elRect.right + 12) - visibleRight
        if (overflow > 0) {
          var max = wrap.scrollWidth - wrap.clientWidth
          wrap.scrollLeft = Math.min(max, wrap.scrollLeft + overflow)
        }
      },
      centerAndPan() {
        var self = this
        this.$nextTick(function() {
          if (self.sidePanel !== 'attr') return
          var wrap = self.$refs.previewWrap
          if (!wrap) return
          // 面板打开时左右都补了滑动余量，固定用 flex-start 再按 max/2 居中
          self.wrapScrolling = true
          self.$nextTick(function() {
            var max2 = wrap.scrollWidth - wrap.clientWidth
            wrap.scrollLeft = max2 > 0 ? Math.round(max2 / 2) : 0
            self.ensureSelectionVisible()
          })
        })
      },
      closeAll() {
        // 全部关闭：清空所有选中元素并收起侧边属性面板
        var self = this
        this.clearSelection()
        this.sidePanel = ''
        this.$nextTick(function() {
          centerScroll(self.$refs.previewWrap, self, 'wrapScrolling')
        })
      },
      toggleSidePanel(panel) {
        // 互斥：同一时间只显示一个面板；再点当前面板即全部关闭
        if (panel === 'attr' && !this.selectedKeys.length) return
        var was = this.sidePanel
        this.sidePanel = this.sidePanel === panel ? '' : panel
        var self = this
        this.$nextTick(function() {
          if (self.sidePanel === 'attr') {
            self.centerAndPan()
          } else if (was === 'attr') {
            centerScroll(self.$refs.previewWrap, self, 'wrapScrolling')
          }
        })
      },
      pushSnapshot() {
        var snap = JSON.stringify({
          attrStyles: this.attrStyles,
          attrLabels: this.attrLabels,
          labelWidth: this.labelWidth,
          labelHeight: this.labelHeight,
          // 选择状态也纳入历史，撤销/恢复可还原选择与取消
          selectedAttrs: this.selectedAttrs,
          selectedKeys: this.selectedKeys,
          currentAttrKey: this.currentAttrKey,
          multiSelectMode: this.multiSelectMode
        })
        if (this.historyIndex >= 0 && this.history[this.historyIndex] === snap) return
        // 丢弃 redo 分支
        if (this.historyIndex < this.history.length - 1) {
          this.history = this.history.slice(0, this.historyIndex + 1)
        }
        if (this.history.length > 50) this.history.shift()
        this.historyIndex = this.history.push(snap) - 1
      },
      applySnapshot(snap) {
        this.attrStyles = JSON.parse(JSON.stringify(snap.attrStyles || {}))
        this.attrLabels = JSON.parse(JSON.stringify(snap.attrLabels || {}))
        this.labelWidth = snap.labelWidth != null ? snap.labelWidth : this.labelWidth
        this.labelHeight = snap.labelHeight != null ? snap.labelHeight : this.labelHeight
        if (snap.selectedAttrs) this.selectedAttrs = snap.selectedAttrs.slice()
        this.selectedKeys = (snap.selectedKeys || []).slice()
        this.currentAttrKey = snap.currentAttrKey || null
        this.multiSelectMode = !!snap.multiSelectMode
      },
      undo() {
        if (this.historyIndex <= 0) { this.$message.info('没有可撤销的操作'); return }
        this.historyIndex--
        this.applySnapshot(JSON.parse(this.history[this.historyIndex]))
      },
      redo() {
        if (this.historyIndex >= this.history.length - 1) { this.$message.info('没有可恢复的操作'); return }
        this.historyIndex++
        this.applySnapshot(JSON.parse(this.history[this.historyIndex]))
      },
      zoomIn() {
        this.zoom = Math.min(LABEL_MAX_ZOOM, this.zoom + 0.1)
      },
      zoomOut() {
        this.zoom = Math.max(LABEL_MIN_ZOOM, this.zoom - 0.1)
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
      // 拖动 / 缩放 / 吸附 / 点击选中切换统一由 labelElementDrag 控制器处理
      startPointerDrag(key, e, mode) {
        this._labelDrag.startPointerDrag(key, e, mode)
      },
      // 编辑区空白（含画布外的灰色区域）：长按进入多选并保留当前选中，单击空白退出多选
      startAreaPress(e) {
        var self = this
        if (this.$refs.labelCanvas && this.$refs.labelCanvas.contains(e.target)) return
        var startX = e.clientX
        var startY = e.clientY
        var timer = null
        var cleanup = function() {
          if (timer) {
            clearTimeout(timer)
            timer = null
          }
          document.removeEventListener('pointermove', onMove)
          document.removeEventListener('pointerup', onUp)
          document.removeEventListener('pointercancel', onCancel)
        }
        var onMove = function(ev) {
          // 移动超过阈值视为滚动，取消本次长按/单击判定
          if (Math.abs(ev.clientX - startX) > 8 || Math.abs(ev.clientY - startY) > 8) cleanup()
        }
        var onUp = function() {
          if (timer) {
            // 未达长按时长：按单击空白处理，清空选择并退出多选
            cleanup()
            self.selectedKeys = []
            self.currentAttrKey = null
            self.multiSelectMode = false
            self.pushSnapshot()
            return
          }
          cleanup()
        }
        var onCancel = function() {
          cleanup()
        }
        timer = setTimeout(function() {
          timer = null
          // 只切换多选状态，以当前已选元素为基础，不再自动全选
          self.multiSelectMode = true
          self.pushSnapshot()
          if (navigator.vibrate) navigator.vibrate(10)
        }, 600)
        document.addEventListener('pointermove', onMove)
        document.addEventListener('pointerup', onUp)
        document.addEventListener('pointercancel', onCancel)
      },
      // 空白处按住滑动框选：Pointer Events + setPointerCapture，与元素拖动同一套机制
      startMarquee(e) {
        if (e.target !== e.currentTarget) return
        var self = this
        var rect = this.$refs.labelCanvas.getBoundingClientRect()
        this.marquee = {
          active: true,
          visible: false,
          startX: (e.clientX - rect.left) / this.zoom,
          startY: (e.clientY - rect.top) / this.zoom,
          x: 0, y: 0, w: 0, h: 0
        }
        // 长按画布空白：进入多选状态并保留当前选中，不再自动全选
        this._marqueeLongPressed = false
        this._marqueeTimer = setTimeout(function() {
          self._marqueeTimer = null
          self._marqueeLongPressed = true
          // 放弃框选，只切换多选状态
          self.marquee.active = false
          self.marquee.visible = false
          self.multiSelectMode = true
          self.unbindMarqueeEvents()
          self.pushSnapshot()
          if (navigator.vibrate) navigator.vibrate(10)
        }, 600)
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
        if (!m.visible && (w > 6 || h > 6)) {
          m.visible = true
          // 已经开始框选，取消长按全选
          if (this._marqueeTimer) {
            clearTimeout(this._marqueeTimer)
            this._marqueeTimer = null
          }
        }
        if (m.visible) {
          m.x = Math.round(Math.min(cx, m.startX))
          m.y = Math.round(Math.min(cy, m.startY))
          m.w = Math.round(w)
          m.h = Math.round(h)
        }
      },
      onMarqueeUp() {
        if (this._marqueeTimer) {
          clearTimeout(this._marqueeTimer)
          this._marqueeTimer = null
        }
        var longPressed = this._marqueeLongPressed
        this._marqueeLongPressed = false
        var m = this.marquee
        if (longPressed) {
          // 长按已全选，抬手不再按“点击空白”清空
          m.visible = false
          this.unbindMarqueeEvents()
          return
        }
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
            // 未形成框选 = 点击空白，清空选择并退出多选
            this.selectedKeys = []
            this.currentAttrKey = null
            this.multiSelectMode = false
          }
          m.visible = false
          this.pushSnapshot()
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
      /** 顶栏「保存」：打开融合弹层，保存后关闭编辑页 */
      saveTemplate() {
        this.openSaveSheet('save')
      },
      /** 顶栏「设置」：打开同一个融合弹层，保存后返回编辑页 */
      openSettings() {
        this.openSaveSheet('settings')
      },
      /** 打开「保存 / 打印设置」融合弹层，按入口决定保存后的去向 */
      openSaveSheet(entry) {
        var cfg = TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
        var self = this
        this.saveSheetEntry = entry || 'save'
        var show = function() {
          if (cfg.storage === 'localTemplates') {
            var exist = self.selectedTemplateId
              ? self.templates.find(function(t) { return t.id === self.selectedTemplateId })
              : null
            self.newTemplateName = self.currentTemplateName || (exist ? (exist.name || '') : '')
            self.templateObject = (exist && exist.objectType) || 'product'
            if (self.templateObject !== 'product') self.templateCategoryId = null
            var catId = exist && exist.type === 'default' ? exist.categoryId : null
            var valid = catId && self.categoryList.some(function(c) { return String(c.value) === String(catId) })
            self.templateCategoryId = valid ? catId : null
          } else if (cfg.storage === 'deliveryTemplate') {
            self.newTemplateName = self.currentTemplateName ||
              ((self.deliveryTemplate && self.deliveryTemplate.name) || '发货单模板')
            self.templateObject = 'delivery'
            self.templateCategoryId = null
          } else {
            self.newTemplateName = self.headerTemplateName
            self.templateObject = self.templateObjectOptions[0].value
            self.templateCategoryId = null
          }
          self.saveSheetVisible = true
        }
        if (cfg.storage === 'localTemplates') this.loadCategoryTree().then(show)
        else show()
      },
      closeSaveSheet() {
        this.saveSheetVisible = false
      },
      /** 切换模板对象：地址模板不绑定商品类型 */
      onTemplateObjectChange(val) {
        if (val !== 'product') this.templateCategoryId = null
      },
      /** 顶栏唯一编辑按钮：统一编辑标题、副标题与标签尺寸 */
      openEditInfo() {
        this.renameValue = this.headerTemplateName
        this.subtitleValue = this.subtitleText || this.currentTitle
        this.editWidthMm = this.labelWidthMm
        this.editHeightMm = this.labelHeightMm
        this.renameVisible = true
      },
      confirmEditInfo() {
        var name = (this.renameValue || '').trim()
        if (!name) {
          this.$message.warning('请输入标题')
          return
        }
        this.currentTemplateName = name
        this.newTemplateName = name
        this.subtitleText = (this.subtitleValue || '').trim()
        if (this.editWidthMm > 0 && this.editHeightMm > 0) {
          this.labelWidth = Math.round(this.editWidthMm * PX_PER_MM)
          this.labelHeight = Math.round(this.editHeightMm * PX_PER_MM)
          // 尺寸变化后重新按窗口适配
          this.fitLabelAfterLoad()
        }
        this.renameVisible = false
      },
      /** 当前面板的规格块结构（规格名 + 属性值名，含已关闭项），随模板保存 */
      collectSpecBlocks() {
        // 数据映射表为唯一权威：specBlocks 只由映射表扁平派生
        var mapped = normalizeSpecBlocks(flattenSpecMapping(this.specMapping))
        if (mapped.length || this.specMappingReady) return mapped
        var blocks = {}
        this.allAttrs.forEach(function(attr) {
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
          if (parsed.kind === 'name') {
            block.name = text.replace(/：$/, '')
          } else {
            block.values[parsed.valueNo - 1] = text
          }
        })
        var collected = normalizeSpecBlocks(Object.keys(blocks).map(function(key) { return blocks[key] }))
        if (collected.length) return collected
        // 类别/规格已归一为 label_category / label_spec 后，属性清单里不再有 specname/specvalue 键，
        // 回退到编辑态规格结构，避免模板保存成空 specBlocks 导致打印时丢失品类映射
        return normalizeSpecBlocks(this.specBlocks)
      },
      /** 加载商品类目树（保存模板弹窗的「商品类型」下拉，取一级类目） */
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
      /** 小票规格块对应的商品品类：单据或明细行上带的 categoryId */
      /** 表单明细品类 ID（兼容调用）；规格并集以数据库内所有已配置规格的品类为准。 */
      receiptCategoryIds() {
        var record = this.record || {}
        var rows = []
        ;[record.goodsRows, record.detailRows, record.rows].forEach(function(list) {
          if (Array.isArray(list)) rows = rows.concat(list)
        })
        var ids = []
        rows.forEach(function(row) {
          var id = row && (row.categoryId != null ? row.categoryId : (row.materialCategoryId != null ? row.materialCategoryId : row.category_id))
          if (id != null && id !== '' && ids.map(String).indexOf(String(id)) < 0) ids.push(id)
        })
        if (!ids.length) {
          var recordId = record.categoryId != null ? record.categoryId : (record.materialCategoryId != null ? record.materialCategoryId : record.category_id)
          if (recordId != null && recordId !== '') ids.push(recordId)
        }
        return ids
      },
      /** 按 id 在类目树里找节点（保留 specConfig） */
      findCategoryNode(categoryId) {
        var target = String(categoryId)
        function walk(nodes) {
          if (!nodes) return null
          for (var i = 0; i < nodes.length; i++) {
            var node = nodes[i]
            if (!node) continue
            if (String(node.id) === target || String(node.value) === target) return node
            var found = walk(node.children)
            if (found) return found
          }
          return null
        }
        return walk(this.categoryTree)
      },
      /** 品类树索引：类目 id → { node, path }，path 从一级类开始 */
      categoryIndex() {
        var index = {}
        function walk(nodes, path) {
          if (!Array.isArray(nodes)) return
          nodes.forEach(function(node) {
            if (!node) return
            var id = node.id != null ? node.id : node.value
            var nextPath = path.concat([node])
            if (id != null && id !== '') index[String(id)] = { node: node, path: nextPath }
            walk(node.children, nextPath)
          })
        }
        walk(this.categoryTree, [])
        return index
      },
      /**
       * 编辑态品类规格定义：只认一级类目，一级类目上配置的规格被它下面所有二级类目共用。
       * 明细行指向二级类目时归到对应的一级类目；一级类目没配规格时兼容旧数据，
       * 取其下第一个配了规格的二级类目定义。只读品类定义，不读商品业务值。
       */
      categoryDefinitions() {
        var groups = []
        var groupMap = {}
        var collectMembers = function(node) {
          var ids = []
          ;(function walk(current) {
            if (!current) return
            var id = current.id != null ? current.id : current.value
            if (id != null && id !== '') ids.push(String(id))
            ;(Array.isArray(current.children) ? current.children : []).forEach(walk)
          })(node)
          return ids
        }
        var blocksOf = function(node) {
          return buildSpecBlocksFromCategory(node && node.specConfig)
        }
        // 一级类目自身的规格定义；没有时向下找第一个配置了规格的类目（旧数据兼容）
        var inheritedBlocks = function(node) {
          var own = blocksOf(node)
          if (own.length) return own
          var children = Array.isArray(node && node.children) ? node.children : []
          for (var i = 0; i < children.length; i++) {
            var childBlocks = inheritedBlocks(children[i])
            if (childBlocks.length) return childBlocks
          }
          return []
        }
        var addGroup = function(firstLevel) {
          if (!firstLevel) return
          var id = firstLevel.id != null ? firstLevel.id : firstLevel.value
          if (id == null || id === '') return
          var key = String(id)
          if (groupMap[key]) return
          var blocks = inheritedBlocks(firstLevel)
          if (!blocks.length) return
          groupMap[key] = {
            id: key,
            name: firstLevel.title || firstLevel.label || firstLevel.name || '',
            memberIds: collectMembers(firstLevel),
            blocks: blocks
          }
          groups.push(groupMap[key])
        }
        // 「表单数据」加载所有已保存的商品品类，不按当前单据商品所属品类过滤
        ;(Array.isArray(this.categoryTree) ? this.categoryTree : []).forEach(addGroup)
        return groups
      },
      /** 品类规格定义 → 一级类目一组规格块（同组二级类目共用这一份定义） */
      categorySpecBlocksForRows() {
        var definitions = this.categoryDefinitions()
        if (!definitions.length) return this.savedSpecBlockSlots()
        var saved = normalizeSpecBlocks(this.specBlocks)
        var result = []
        definitions.forEach(function(group) {
          group.blocks.forEach(function(block, index) {
            var blockNo = index + 1
            var savedBlock = saved.filter(function(item) {
              return String(item.categoryId || '') === group.id && (Number(item.blockNo) || 1) === blockNo
            })[0]
            result.push({
              categoryId: group.id,
              categoryName: group.name,
              categoryMemberIds: group.memberIds,
              blockNo: blockNo,
              name: savedBlock && savedBlock.name ? savedBlock.name : block.name,
              values: savedBlock && savedBlock.values && savedBlock.values.length ? savedBlock.values.slice() : block.values.slice(),
              sourceName: block.name,
              sourceValues: block.values.slice()
            })
          })
        })
        return result
      },
      /** 数据库没有品类规格定义时，退回模板保存的规格块（旧模板结构） */
      savedSpecBlockSlots() {
        return normalizeSpecBlocks(this.specBlocks).map(function(block, index) {
          var item = {
            name: block.name,
            values: block.values.slice(),
            blockNo: Number(block.blockNo) || index + 1,
            sourceName: block.sourceName || block.name,
            sourceValues: (block.sourceValues || block.values).slice()
          }
          if (block.categoryId != null) item.categoryId = String(block.categoryId)
          if (block.categoryName) item.categoryName = block.categoryName
          if (block.categoryMemberIds) item.categoryMemberIds = block.categoryMemberIds.slice()
          return item
        })
      },
      // onlyLevel 指定时只保留该级类目（1=一级，2=二级），未返回 categoryLevel 时按树深度判断
      flattenCategoryTree(tree, onlyLevel) {        var result = []
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
      /** 融合弹层确认：不选商品类型=普通模板，选了=该类型的通用模板（同类型只保留一个） */
      confirmSaveTemplate() {
        var name = (this.newTemplateName || '').trim()
        if (!name) {
          this.$message.warning('请输入模板名称')
          return
        }
        var cfg = TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
        var self = this
        // 保存成功后的去向：设置入口返回编辑页，保存入口关闭编辑页
        var finish = function(ok) {
          if (!ok) return
          self.currentTemplateName = name
          self.saveSheetVisible = false
          if (self.saveSheetEntry === 'save') self.handleBack()
        }
        // 发货单/小票/会员/地址：同一套保存入口，按各自模板类型落地
        if (cfg.storage !== 'localTemplates') {
          saveTemplateSnapshot(this, cfg, name, null).then(finish)
          return
        }
        var isCategoryTpl = this.templateObject === 'product' && !!this.templateCategoryId
        var exist = this.selectedTemplateId
          ? this.templates.find(function(t) { return t.id === self.selectedTemplateId })
          : null
        // 商品模板：选了类目=该类目通用模板；没选类目=绑定当前商品；两者都不成立时才是「全部类型」
        var materialId = null
        if (this.templateObject === 'product' && !isCategoryTpl) {
          materialId = (this.record && this.record.id) || (exist && exist.materialId) || null
        }
        var tplType = isCategoryTpl ? 'default'
          : (this.templateObject === 'product' && !materialId ? 'default' : 'normal')
        saveTemplateSnapshot(this, cfg, name, {
          id: exist ? exist.id : 'tpl_' + Date.now(),
          serverId: exist ? exist.serverId : null,
          objectType: this.templateObject,
          type: tplType,
          materialId: materialId,
          categoryId: isCategoryTpl ? this.templateCategoryId : null,
          categoryName: isCategoryTpl ? this.getCategoryName(this.templateCategoryId) : null
        }, '模板已保存').then(function() { finish(true) })
      },
      /** 另存为：以新名称保存为独立模板，不覆盖当前模板 */
      openSaveAs() {
        var base = (this.newTemplateName || this.currentTemplateName || '标签模板').trim()
        this.saveAsName = base ? base + ' 副本' : ''
        this.saveAsVisible = true
      },
      confirmSaveAs() {
        var name = (this.saveAsName || '').trim()
        if (!name) {
          this.$message.warning('请输入模板名称')
          return
        }
        var exist = this.templates.some(function(t) { return t.name === name })
        if (exist) {
          this.$message.warning('模板名称已存在，请修改')
          return
        }
        var isCategoryTpl = this.templateObject === 'product' && !!this.templateCategoryId
        var materialId = (this.templateObject === 'product' && !isCategoryTpl && this.record && this.record.id) || null
        var cfg = TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
        this.saveAsVisible = false
        var self = this
        saveTemplateSnapshot(this, cfg, name, {
          id: 'tpl_' + Date.now(),
          serverId: null,
          objectType: this.templateObject,
          type: isCategoryTpl ? 'default' : (this.templateObject === 'product' && !materialId ? 'default' : 'normal'),
          materialId: materialId,
          categoryId: isCategoryTpl ? this.templateCategoryId : null,
          categoryName: isCategoryTpl ? this.getCategoryName(this.templateCategoryId) : null
        }, '模板已另存为').then(function() {
          self.newTemplateName = name
          self.currentTemplateName = name
          self.saveSheetVisible = false
        })
      },
      handlePrint() {
        // 把当前编辑中的模板快照写入 sessionStorage，跳转打印页后恢复
        var snapshotValues = {}
        this.allAttrs.forEach(function(attr) {
          if (attr.key) snapshotValues[attr.key] = attr.value == null ? '' : attr.value
        })
        try {
          var cfgInfo = TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
          var currentId = this.selectedTemplateId
          var current = currentId
            ? this.templates.find(function(t) { return t.id === currentId })
            : null
          // 快照与保存模板使用同一 v2 结构，预览按同一套规则还原，样式保持一致
          var snapshot = buildTemplateSnapshot(this, cfgInfo, (current && current.name) || cfgInfo.title.replace('编辑', '') + '模板')
          snapshot.values = snapshotValues
          // 小票：明细行跟着快照走，预览/打印时按同一规则重新拆成单品元素
          if (this.templateType === 'receipt' && this.record && Array.isArray(this.record.goodsRows)) {
            snapshot.rows = this.record.goodsRows
          }
          sessionStorage.setItem('jsh.labelEdit.snapshot', JSON.stringify(snapshot))
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
      handleBack() {
        this.$emit('close')
      },
      applyPreset(wMm, hMm) {
        this.editWidthMm = wMm
        this.editHeightMm = hMm
      }
    }
  }
</script>

<style lang="scss" scoped>
/* 顶栏右列：标签名称在上、标签尺寸在下，与左侧页面标题并排 */
.lem-header-sub {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 1px;
  min-width: 0;
}

/* 顶栏右列：标签名称 / 标签尺寸 */
.lem-header-name {
  overflow: hidden;
  max-width: 100%;
  font-size: 12px;
  line-height: 1.2;
  color: rgba(255, 255, 255, 0.85);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.lem-header-size {
  overflow: hidden;
  max-width: 100%;
  font-size: 11px;
  line-height: 1.2;
  color: rgba(255, 255, 255, 0.68);
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 顶栏编辑按钮缩小 */
.mmp-tools .mmp-icon.lem-icon-sm {
  width: 34px;
  font-size: 18px;
}

.lem-page {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  background: #f5f5f5;
  padding-bottom: calc(58px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 画布 + 右侧面板：面板覆盖在画布上，不压缩画布 */
.lem-content {
  position: relative;
  --lem-side-w: 180px;
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: stretch;
  overflow: hidden;
}

.lem-preview-wrap {
  flex: 1 1 auto;
  min-width: 0;
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

/* 拖动吸附时的对齐线 */
.lem-guide {
  position: absolute;
  background: #ff4d4f;
  pointer-events: none;
  z-index: 20;
}

.lem-guide-v {
  top: 0;
  bottom: 0;
}

.lem-guide-h {
  left: 0;
  right: 0;
}

.lem-elem-label {
  color: #333;
}

.lem-elem-value {
  color: #333;
}

/* 属性名为空时的浅灰占位：只用于编辑页识别元素，不打印、不写库 */
.lem-elem-placeholder {
  color: #bfbfbf;
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
  justify-content: space-between;
  gap: 8px;
  padding: 6px 12px;
  background: #fff;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
  overflow: hidden;
  position: sticky;
  top: 0;
  z-index: 20;
}

/* 左侧提示：标签选中状态 + 空白处操作说明 */
.lem-align-hint {
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  gap: 2px;
  max-width: 46%;
}

.lem-align-hint__state {
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  color: #262626;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.lem-align-hint__state.is-multi {
  color: #1890ff;
}

.lem-align-hint__tip {
  overflow: hidden;
  font-size: 10px;
  color: #999;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.lem-align-bar::-webkit-scrollbar {
  display: none;
}

.lem-align-btns {
  display: flex;
  gap: 4px;
  align-items: center;
  flex-wrap: nowrap;
  flex: 0 1 auto;
  min-width: 0;
  margin-left: auto;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.lem-align-btns::-webkit-scrollbar {
  display: none;
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

/* 保存模板弹窗 */
.lem-save-option {
  font-size: 13px;
  color: #333;
}
.lem-save-field {
  margin-bottom: 8px;
}

.lem-save-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 3px;
  color: #333;
}

.lem-save-object-row {
  display: flex;
  gap: 8px;
}

.lem-save-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 30px;
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

/* 分页打印对象：单列开关行 */
.lem-flow-cards {
  display: flex;
  flex-direction: column;
}

.lem-flow-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  height: 30px;
  font-size: 13px;
  line-height: 18px;
  color: #333;
}

.lem-flow-row.is-on .lem-flow-name {
  color: #1677ff;
  font-weight: 500;
}

/* 弹层底部：显示分页标签与取消/保存同一行 */
.lem-save-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.lem-save-bottom-label {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  font-size: 13px;
}

.lem-save-footer-btns {
  display: flex;
  justify-content: flex-end;
}

.lem-save-footer-btns .ant-btn + .ant-btn {
  margin-left: 8px;
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

/* 按钮区：均分底栏宽度 */
.lem-toolbar-scroll {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: space-around;
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

/* 撤销/恢复不可用态 */
.lem-tool-btn.is-disabled {
  color: #c9c9c9;
}

/* 面板：覆盖在画布右侧（不压缩画布），属性/元素互斥 */
.lem-side {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: var(--lem-side-w);
  z-index: 30;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-left: 1px solid #eee;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  animation: lem-side-in 0.18s ease-out;
}

@keyframes lem-side-in {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}

.lem-side-head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 34px;
  padding: 0 8px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.lem-side-close {
  flex: none;
  font-size: 12px;
  line-height: 1;
  color: #1890ff;
  padding: 6px 2px;
  white-space: nowrap;
}

.lem-side-title {
  font-size: 12px;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
}

.lem-side-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 8px;
}

/* 表单数据按钮：固定在元素面板底部，不随字段列表滚动 */
.lem-side-foot {
  flex: none;
  padding: 8px;
  background: #fff;
  border-top: 1px solid #f0f0f0;
}

/* 悬浮切换卡片：固定在屏幕下方，贴面板左侧或屏幕右边缘 */
.lem-side-tabs {
  position: absolute;
  right: 0;
  bottom: 16px;
  z-index: 40;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.lem-side-tabs--attached {
  right: var(--lem-side-w);
}

.lem-side-tab {
  width: 34px;
  height: 64px;
  background: #1890ff;
  color: #fff;
  border-radius: 8px 0 0 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  box-shadow: -2px 2px 8px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  user-select: none;
}

.lem-side-tab .anticon {
  font-size: 12px;
  line-height: 12px;
}

.lem-side-tab > span {
  writing-mode: vertical-rl;
  letter-spacing: 2px;
  font-size: 11px;
  line-height: 11px;
}

/* 未选中元素时属性卡片置灰（点击无反应） */
.lem-side-tab--disabled {
  opacity: 0.45;
}

/* 窄屏收窄面板宽度 */
@media (max-width: 360px) {
  .lem-content {
    --lem-side-w: 164px;
  }
}

.lem-panel-multi-tip {
  font-size: 11px;
  line-height: 1.4;
  color: #1890ff;
  margin-bottom: 6px;
}

/* 规格块：属性名/属性值逐项开关 */

/* 属性名 / 属性值：标题一行，输入框换行整行显示 */
.lem-field-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.lem-field-head {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 20px;
}

.lem-field-head > .lem-style-label {
  flex: 0 0 auto;
  width: auto;
}

.lem-field-head > .lem-ocr-btn {
  height: 20px;
  padding: 0 6px;
  font-size: 12px;
  line-height: 18px;
}

.lem-field-switch {
  margin-left: auto;
  flex: none;
}

.lem-text-input {
  width: 100%;
  min-width: 0;
}

/* 属性面板控件：恢复可见外框与紧凑字号（移动端全局样式默认去掉输入框边框） */
.lem-side-body ::v-deep .ant-input,
.lem-side-body ::v-deep .ant-input-number,
.lem-side-body ::v-deep .ant-select-selection {
  height: 26px !important;
  box-sizing: border-box;
  border: 1px solid #d9d9d9 !important;
  border-radius: 4px !important;
  background: #fff !important;
  box-shadow: none !important;
  font-size: 12px !important;
  color: #333 !important;
}

.lem-side-body ::v-deep .ant-input {
  padding: 0 7px;
  line-height: 24px !important;
}

.lem-side-body ::v-deep .ant-input-number {
  width: 100%;
}

.lem-side-body ::v-deep .ant-input-number-input {
  height: 24px !important;
  padding: 0 7px;
  font-size: 12px !important;
  line-height: 24px !important;
  text-align: left !important;
}

.lem-side-body ::v-deep .ant-input-number-handler-wrap {
  display: none !important;
}

.lem-side-body ::v-deep .ant-select-selection__rendered {
  margin: 0 7px;
  font-size: 12px !important;
  line-height: 24px !important;
}

.lem-side-body ::v-deep .ant-select-arrow {
  right: 7px;
}

.lem-side-body ::v-deep .ant-input:hover,
.lem-side-body ::v-deep .ant-input:focus,
.lem-side-body ::v-deep .ant-input-number:hover,
.lem-side-body ::v-deep .ant-input-number-focused,
.lem-side-body ::v-deep .ant-select-selection:hover,
.lem-side-body ::v-deep .ant-select-focused .ant-select-selection {
  border-color: #40a9ff !important;
  box-shadow: none !important;
}

.lem-side-body ::v-deep .ant-input[disabled] {
  background: #f5f5f5 !important;
  color: #bfbfbf !important;
}

/* 规格值列表：一个规格块可挂多个规格值，每个值独立开关 */
.lem-value-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.lem-value-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.lem-value-row > .lem-text-input {
  flex: 1 1 0;
  width: auto;
  min-width: 0;
}

.lem-value-row > .lem-field-switch {
  margin-left: 0;
}

/* 规格块内文字行：与打印预览同款（等行高、不换行、按对齐方式显示） */
.lem-spec-line {
  white-space: pre;
  overflow: hidden;
}

/* 规格块内的浅灰占位（属性名空白时显示），仅编辑页可见 */
.lem-spec-item.is-placeholder {
  color: #bfbfbf;
}

/* 商品信息组合字段勾选 */
.lem-goods-field-group {
  flex: 1 1 0;
  width: 100%;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 2px 8px;
  line-height: 20px;
}

.lem-goods-field-group ::v-deep .ant-checkbox-wrapper {
  margin-left: 0;
  font-size: 12px;
}

.lem-ocr-btn {
  flex: none;
  margin-top: 0;
}

.lem-style-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.lem-style-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

/* 控件占满属性栏剩余宽度 */
.lem-style-control {
  flex: 1 1 0;
  width: auto !important;
  min-width: 0;
}

.lem-style-label {
  flex: 0 0 34px;
  width: 34px;
  font-size: 12px;
  color: #999;
  white-space: nowrap;
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
  padding: 10px 16px 2px;
}

.lem-save-sheet-wrap .ant-modal-title {
  text-align: center;
  font-size: 15px;
}

.lem-save-sheet-wrap .ant-modal-body {
  padding: 0 20px 4px;
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}

.lem-save-sheet-wrap .ant-modal-footer {
  padding: 8px 20px 10px;
  border-top: 1px solid #f0f0f0;
}

.lem-save-sheet-wrap .ant-modal-footer .ant-btn {
  height: 28px;
  padding: 0 14px;
  font-size: 13px;
  border-radius: 6px;
}

/* 弹层控件尺寸与紧凑布局对齐 */
.lem-save-sheet-wrap .ant-input,
.lem-save-sheet-wrap .ant-select-selection {
  height: 30px;
  font-size: 13px;
}

.lem-save-sheet-wrap .ant-input {
  padding: 0 9px;
  line-height: 30px;
}

.lem-save-sheet-wrap .ant-select-selection__rendered {
  line-height: 30px;
  margin-left: 9px;
  margin-right: 9px;
}

.lem-save-sheet-wrap .ant-switch {
  min-width: 34px;
  height: 19px;
  line-height: 19px;
}

.lem-save-sheet-wrap .ant-switch::after {
  width: 15px;
  height: 15px;
  top: 2px;
  left: 2px;
}

.lem-save-sheet-wrap .ant-switch-checked::after {
  left: 100%;
  margin-left: 0;
  transform: translateX(calc(-100% - 2px));
}

/* 表单数据：底部标准弹层（宽 100%、底部贴合、上圆角、内容滚动、底部固定确定） */
.lem-form-sheet-wrap {
  display: flex;
  align-items: flex-end;
}

.lem-form-sheet-wrap .ant-modal {
  position: relative;
  top: auto;
  bottom: 0;
  width: 100% !important;
  max-width: 100%;
  margin: 0;
  padding-bottom: 0;
}

.lem-form-sheet-wrap .ant-modal-content {
  border-radius: 16px 16px 0 0;
  padding-bottom: env(safe-area-inset-bottom);
  overflow: hidden;
}

.lem-form-sheet-wrap .ant-modal-header {
  border-radius: 16px 16px 0 0;
  border-bottom: none;
  padding: 10px 16px 2px;
}

.lem-form-sheet-wrap .ant-modal-title {
  text-align: center;
  font-size: 15px;
}

.lem-form-sheet-wrap .ant-modal-body {
  padding: 4px 16px;
  max-height: 62vh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.lem-form-sheet-wrap .ant-modal-footer {
  padding: 8px 16px 10px;
  border-top: 1px solid #f0f0f0;
}

.lem-form-sheet-wrap .ant-modal-footer .ant-btn {
  width: 100%;
  height: 40px;
  border-radius: 20px;
}

.lem-form-search {
  margin-bottom: 8px;
}

.lem-form-group {
  margin-bottom: 10px;
}

.lem-form-group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 2px;
  border-bottom: 1px solid #f0f0f0;
}

.lem-form-group-name {
  flex: 1 1 auto;
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.lem-form-group-count {
  flex: none;
  font-size: 11px;
  color: #999;
}

.lem-form-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 2px;
}

.lem-form-row-label {
  flex: none;
  max-width: 56%;
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lem-form-row-value {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 12px;
  color: #999;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lem-form-cat .lem-form-group-name {
  color: #1f6feb;
}

.lem-form-spec {
  padding: 4px 2px 6px;
  border-bottom: 1px dashed #f0f0f0;
}

.lem-form-spec:last-child {
  border-bottom: none;
}

.lem-form-spec-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
}

.lem-form-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 2px 0 0 26px;
}

.lem-form-chip {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  border: 1px solid #d9d9d9;
  border-radius: 13px;
  font-size: 12px;
  color: #666;
  background: #fff;
}

.lem-form-chip.is-on {
  border-color: #1f6feb;
  color: #1f6feb;
  background: #eaf2ff;
}

.lem-form-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #999;
}
</style>
