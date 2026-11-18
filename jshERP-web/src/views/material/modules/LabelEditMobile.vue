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
        <div
          v-for="(key, idx) in selectedAttrs"
          :key="key"
          class="lem-elem"
          :data-key="key"
          :class="{ 'lem-elem-code': isCode(key), 'lem-elem--active': selectedKeys.indexOf(key) !== -1 }"
          :style="getElementStyle(key)"
          @pointerdown.stop.prevent="startPointerDrag(key, $event, 'move')"
        >
          <!-- 内容层单独裁切，手柄放在内容层之外，避免被 overflow 裁掉 -->
          <div class="lem-elem-inner">
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
            <span class="lem-elem-value">{{ displayParts[key] ? displayParts[key].value : '' }}</span>
          </template>
          </div>
          <span v-if="!isSpecAttrKey(key)" class="lem-elem-frame" aria-hidden="true"></span>
          <span
            v-if="currentAttrKey === key && !isSpecAttrKey(key)"
            class="lem-resize-handle"
            :style="handleAntiScaleStyle"
            @pointerdown.stop.prevent="startPointerDrag(key, $event, 'resize')"
          ></span>
          <span
            v-if="currentAttrKey === key && !isCode(key) && !isSpecAttrKey(key)"
            class="lem-delete-btn"
            :style="deleteBtnAntiScaleStyle"
            @pointerdown.stop.prevent="noop"
            @click.stop="removeAttr(key)"
          >×</span>
        </div>
        <!-- 规格块：属性名 + 全部规格值合并为一个整块，外框/拖动/手柄与普通文字标签一致 -->
        <div
          v-for="frame in specBlockFrames"
          :key="'spec-frame-' + frame.id"
          class="lem-elem lem-spec-block"
          :class="{ 'lem-elem--active': frame.active }"
          :style="frame.style"
          @pointerdown.stop.prevent="startPointerDrag(frame.anchorKey, $event, 'move')"
        >
          <span class="lem-elem-frame" aria-hidden="true"></span>
          <span
            v-if="frame.active"
            class="lem-resize-handle"
            :style="handleAntiScaleStyle"
            @pointerdown.stop.prevent="startPointerDrag(frame.anchorKey, $event, 'resize')"
          ></span>
          <span
            v-if="frame.active"
            class="lem-delete-btn"
            :style="deleteBtnAntiScaleStyle"
            @pointerdown.stop.prevent="noop"
            @click.stop="removeSpecBlock(frame)"
          >×</span>
        </div>
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
          <!-- 规格块：属性名 + N 个规格值，整块选中；每项右侧开关控制是否显示 -->
          <template v-if="currentSpecBlock">
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
      <div class="lem-side-body">
        <!-- 数据表选择：字段随所选表加载，逻辑与 Web 端「业务数据源」一致 -->
        <div v-if="providerList.length" class="lem-provider">
          <span class="lem-provider-label">数据表</span>
          <a-select
            :value="provider"
            size="small"
            class="lem-provider-select"
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
            :class="{ 'lem-elem-item-active': isElemItemOn(attr) }"
            @click="toggleElemItem(attr)"
          >
            <div class="lem-elem-text">
              <span class="lem-elem-cn">{{ attr.isSpecBlock ? attr.label : (attr.comment || attr.label) }}</span>
              <span v-if="attr.isSpecBlock" class="lem-elem-key">规格块 · {{ attr.valueCount }} 个值</span>
              <span v-else class="lem-elem-key">{{ attr.key }}</span>
            </div>
            <a-icon
              :type="isElemItemOn(attr) ? 'check-circle' : 'plus-circle'"
              :theme="isElemItemOn(attr) ? 'filled' : 'outlined'"
              :class="{ 'lem-elem-icon--on': isElemItemOn(attr) }"
            />
          </div>
        </div>
        <a-button type="dashed" block size="small" class="lem-add-custom" @click="addCustomLabel">
          <a-icon type="plus" />自定义标签
        </a-button>
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
    <!-- 打印标签弹层（不再跳转打印页面） -->
    <label-print-modal ref="labelPrintModal" />
    </div>
  </mobile-modal-page>
</template>

<script>
  import MobileModalPage from '@/components/mobile/MobileModalPage'
  import LabelPrintModal from './LabelPrintModal'
  import { buildLabelAttrs } from '@/views/material/utils/materialDisplay'
  import { isCodeKey } from '@/views/material/utils/labelCode'
  import { fetchLabelTemplates, saveLabelTemplate, deleteLabelTemplate } from '@/views/material/utils/labelTemplateStore'
  import { fetchDeliveryTemplate, saveDeliveryTemplate as persistDeliveryTemplate } from '@/views/bill/utils/deliveryNoteTemplateStore'
  import { editorStateToV2, v2ToEditorState, isLabelTemplateV2 } from '@/views/material/utils/labelTemplateV2'
  import { fetchProviders, fetchProviderFields, fetchLabelData } from '@/views/material/utils/labelProviderApi'
  import { getAction, postAction } from '@/api/manage'
  import { queryMaterialCategoryTreeList } from '@/api/api'
  import { computeSpacer, centerScroll, setupLabelScroll, computeFitScale } from '@/utils/label-scroll'
  import { GOODS_TABLE_KEY, GOODS_KEY, GOODS_TABLE_COLUMNS, GOODS_FIELD_OPTIONS, hasGoodsValue, resolveLabelLayout, isGoodsTableKey, isReceiptItemKey, isReceiptItemHidden, replaceReceiptGoodsWithItems } from '@/views/material/utils/goodsLabelLayout'
  import { parseSpecItemKey, specNameKey, isSpecItemKey, editPlaceholderLabel, firstRowForEdit, normalizeSpecBlocks, resolveEditSpecBlocks, buildSpecBlocksFromCategory } from '@/views/material/utils/goodsSpecBlocks'

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
      // 80mm 热敏小票纸（96dpi → 302px）
      defaultWidth: 302,
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
    components: { MobileModalPage, LabelPrintModal },
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
        selectedAttrs: [],
        // 小票单品元素中通过开关关闭（不显示）的 key，打印快照时一并带给预览页
        hiddenItemKeys: [],
        // 规格块结构（规格名 + 属性值名）：编辑态只显示属性名，随模板保存/恢复
        specBlocks: [],
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
        dragging: { active: false, mode: '', key: '', startX: 0, startY: 0, origX: 0, origY: 0, origW: 0, origH: 0, moved: false },
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
          mode: this.overflowMode
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
          // 属性名留空时用字段默认名称占位，便于在编辑页识别元素
          var label = self.attrLabels[key] || (attr ? attr.label : '')
          map[key] = { label: '', value: editPlaceholderLabel(label, attr) }
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
      // 规格名 + 其下全部规格值聚合为一个「规格块」条目，一个块只占一项
      attrGroups() {
        var self = this
        var groups = []
        var map = {}
        var blocks = {}
        this.allAttrs.forEach(function(a) {
          // 分页标签用面板底部的专用按钮添加，不在字段列表里重复出现
          if (a.key === 'page_no') return
          var name = a.group || '可用字段'
          if (!map[name]) {
            map[name] = { name: name, attrs: [] }
            groups.push(map[name])
          }
          var item = parseSpecItemKey(a.key)
          if (!item) {
            map[name].attrs.push(a)
            return
          }
          var blockId = item.rowNo + '-' + item.blockNo
          var block = blocks[blockId]
          if (!block) {
            var nameKey = specNameKey(item.rowNo, item.blockNo)
            var nameAttr = self.allAttrs.find(function(x) { return x.key === nameKey })
            var label = nameAttr && nameAttr.value ? String(nameAttr.value).replace(/：$/, '') : '规格块'
            block = {
              key: 'specblock-' + blockId,
              blockId: blockId,
              isSpecBlock: true,
              blockKeys: [],
              label: label,
              valueCount: 0
            }
            blocks[blockId] = block
            map[name].attrs.push(block)
          }
          block.blockKeys.push(a.key)
          if (item.kind === 'value') block.valueCount++
        })
        return groups
      },
      currentLabel() {
        if (!this.currentAttrKey) return ''
        return this.attrLabels[this.currentAttrKey] || ''
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
        var nameKey = specNameKey(rowNo, blockNo)
        var hasName = this.allAttrs.some(a => a.key === nameKey)
        var valueKeys = this.allAttrs.filter(function(attr) {
          var item = parseSpecItemKey(attr.key)
          return !!item && item.kind === 'value' && item.rowNo === rowNo && item.blockNo === blockNo
        }).map(function(attr) { return attr.key })
        return { nameKey: hasName ? nameKey : '', valueKeys: valueKeys }
      },
      // 规格块外框：属性名 + 全部规格值合并为一个实心整块
      specBlockFrames() {
        var self = this
        var blocks = {}
        this.selectedAttrs.forEach(function(key) {
          var item = parseSpecItemKey(key)
          if (!item) return
          var id = item.rowNo + '-' + item.blockNo
          if (!blocks[id]) blocks[id] = { id: id, keys: [] }
          blocks[id].keys.push(key)
        })
        return Object.keys(blocks).map(function(id) {
          var keys = blocks[id].keys
          var x1 = Infinity
          var y1 = Infinity
          var x2 = -Infinity
          var y2 = -Infinity
          keys.forEach(function(key) {
            var style = self.labelLayout.styles[key] || self.attrStyles[key] || {}
            var size = self.getItemSize(key)
            var w = style.width > 0 ? style.width : size.w
            var h = style.height > 0 ? style.height : size.h
            var x = style.x || 0
            var y = style.y || 0
            x1 = Math.min(x1, x)
            y1 = Math.min(y1, y)
            x2 = Math.max(x2, x + w)
            y2 = Math.max(y2, y + h)
          })
          if (!isFinite(x1)) return null
          var pad = 2
          var nameKey = keys.find(function(key) {
            var item = parseSpecItemKey(key)
            return !!item && item.kind === 'name'
          }) || keys[0]
          return {
            id: id,
            keys: keys,
            anchorKey: nameKey,
            active: keys.some(function(key) { return self.selectedKeys.indexOf(key) > -1 }),
            style: {
              left: (x1 - pad) + 'px',
              top: (y1 - pad) + 'px',
              width: (x2 - x1 + pad * 2) + 'px',
              height: (y2 - y1 + pad * 2) + 'px',
              '--lem-zoom': String(self.zoom || 1)
            }
          }
        }).filter(Boolean)
      },
      specNameText() {
        var block = this.currentSpecBlock
        if (!block || !block.nameKey) return ''
        var attr = this.allAttrs.find(a => a.key === block.nameKey)
        return attr ? String(attr.value || '').replace(/：$/, '') : ''
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
    mounted() {
      this.initEditor()
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
      initEditor() {
        var type = this.initialType || 'label'
        var cfg = TYPE_CONFIG[type] || TYPE_CONFIG.label
        // 从打印预览页点「编辑」返回时，恢复当时的商品数据和模板，避免编辑页字段全空
        var context = null
        try { context = JSON.parse(sessionStorage.getItem('jsh.labelEdit.context') || 'null') } catch (e) { context = null }
        if (context) {
          try { sessionStorage.removeItem('jsh.labelEdit.context') } catch (e) { /* 忽略 */ }
          // 小票编辑的是模板布局，不接收单据业务数据
          if (context.record && type !== 'receipt') this.record = context.record
          if (context.sku) this.sku = context.sku
        }
        this.templateType = type
        // 业务标识：来源页优先，其次按类型兜底（商品/发货单/会员）
        this.businessType = this.initialBusinessType || (context && context.businessType) ||
          (type === 'delivery' ? 'delivery' : (type === 'label' ? 'product' : (type === 'member' ? 'member' : '')))
        this.labelWidth = cfg.defaultWidth
        this.labelHeight = cfg.defaultHeight
        this.deliveryTemplate = null
        this.allAttrs = this.buildAttrs(cfg.attrsBuilder)
        var loaded
        var hasContextTemplate = !!(context && context.template && context.template.selectedAttrs)
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
          if (self.templateType === 'receipt' && self.receiptCategoryId() != null && !self.categoryTree.length) {
            self.loadCategoryTree().then(function() {
              self.applyReceiptItemElements()
            }, function() {
              self.applyReceiptItemElements()
            })
            return
          }
          // 小票按单品拆分商品元素：逐件、逐字段独立可编辑
          self.applyReceiptItemElements()
        })
      },
      /**
       * 小票：把商品数据拆成「每件商品 × 每个字段」的独立元素
       * 明细表/商品组合/遗留商品字段由 replaceReceiptGoodsWithItems 统一移除
       */
      applyReceiptItemElements() {
        if (this.templateType !== 'receipt') return
        // 编辑的是模板：规格块只认模板保存的结构，其次是商品品类设置；
        // 不使用单据明细推导，单据值只在预览/打印阶段注入
        var blocks = resolveEditSpecBlocks(this.specBlocks, this.categorySpecBlocks())
        if (!blocks.length) blocks = [{ name: '规格值1', values: ['规格值'] }]
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
      /** 编辑态商品行：框架字段只用属性名占位，规格内容由 specBlocks 提供 */
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
        var list
        switch (builder) {
          case 'delivery': list = this.buildDeliveryAttrs(); break
          case 'receipt': list = this.buildReceiptAttrs(); break
          case 'member': list = this.buildMemberAttrs(); break
          case 'address': list = this.buildAddressAttrs(); break
          case 'label':
          default: list = buildLabelAttrs(this.record, this.sku)
        }
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
      /** 统一的模板加载入口，根据 storage 类型分发（只支持 v2，旧结构提示删除） */
      loadTemplate(cfg, skipApply) {
        var self = this
        if (cfg.storage === 'localTemplates') {
          var templateId = this.initialTemplateId
          return fetchLabelTemplates().then(function(templates) {
            self.templates = templates || []
            if (skipApply) return
            var target = templateId
              ? templates.find(function(t) {
                return String(t.id) === String(templateId) || String(t.serverId) === String(templateId)
              })
              : (templates[0] || null)
            if (!target) return
            if (target.outdated) {
              self.selectedTemplateId = null
              self.promptOutdatedTemplate(target, cfg)
              return
            }
            self.applyTemplateSnapshot(target, cfg)
            self.selectedTemplateId = target.id
          })
        } else if (cfg.storage === 'deliveryTemplate') {
          return fetchDeliveryTemplate().then(function(tpl) {
            if (skipApply) {
              if (tpl && !tpl.outdated) self.deliveryTemplate = tpl
              return
            }
            if (!tpl) {
              self.applyDefaultLayout(cfg)
              return
            }
            if (tpl.outdated) {
              self.deliveryTemplate = null
              self.promptOutdatedTemplate(tpl, cfg)
              return
            }
            self.deliveryTemplate = tpl
            self.applyTemplateSnapshot(tpl, cfg)
          }).catch(function() {
            if (skipApply) return
            self.applyDefaultLayout(cfg)
          })
        } else if (cfg.storage === 'platformConfig') {
          return getAction('/platformConfig/getUiConfig', { key: cfg.platformConfigKey, _t: Date.now() }).then(function(res) {
            if (skipApply) return
            var raw = self.parseConfigData(res && res.code === 200 ? res.data : null)
            if (!raw || (typeof raw === 'object' && !Object.keys(raw).length)) {
              self.applyDefaultLayout(cfg)
              return
            }
            if (!isLabelTemplateV2(raw)) {
              self.promptOutdatedTemplate({
                name: cfg.title + '模板',
                platformKey: cfg.platformConfigKey
              }, cfg)
              return
            }
            var state = v2ToEditorState(raw)
            if (!state.selectedAttrs.length) {
              self.applyDefaultLayout(cfg)
              return
            }
            self.applyTemplateSnapshot(state, cfg)
          }).catch(function() {
            if (skipApply) return
            self.applyDefaultLayout(cfg)
          })
        }
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
            var task = (tpl && tpl.platformKey)
              ? postAction('/platformConfig/saveUiConfig', { key: tpl.platformKey, value: '' })
              : deleteLabelTemplate(tpl)
            return Promise.resolve(task).then(done).catch(function() {
              self.$message.error('删除失败，请稍后重试')
            })
          }
        })
      },
      /** 把持久化快照应用到当前编辑状态 */
      applyTemplateSnapshot(snap, cfg) {
        var self = this
        this.selectedAttrs = JSON.parse(JSON.stringify(snap.selectedAttrs))
        this.attrStyles = JSON.parse(JSON.stringify(snap.attrStyles || {}))
        this.attrLabels = JSON.parse(JSON.stringify(snap.attrLabels || {}))
        this.hiddenItemKeys = Array.isArray(snap.hiddenItemKeys) ? snap.hiddenItemKeys.slice() : []
        // 规格块结构（属性名 + 属性值名）：旧模板没有该字段时留空，由单品元素重建时兜底推导
        this.specBlocks = normalizeSpecBlocks(snap.specBlocks)
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
      /** getUiConfig 返回值可能是字符串或已解析对象，统一处理 */
      parseConfigData(data) {
        var cfg = data
        if (typeof data === 'string') {
          try { cfg = JSON.parse(data) } catch (e) { cfg = null }
        }
        if (cfg && typeof cfg === 'object' && cfg.config != null) cfg = cfg.config
        return cfg
      },
      /** 请求异常转成可读原因：有响应给状态码+响应体，无响应给底层错误信息 */
      formatRequestError(err) {
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
      },
      /** 通用 platformConfig 写入 */
      postSnapshot(key, snap) {
        var value = JSON.stringify(snap)
        return postAction('/platformConfig/saveUiConfig', {
          key: key,
          value: value
        }).then(function(res) {
          // 保存成功写本地缓存，供打印页弱网/离线兜底
          if (res && res.code === 200) {
            try { localStorage.setItem('jsh.uiConfig.' + key, value) } catch (e) { /* 忽略 */ }
          }
          return res
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
      /** 小票字段：只引用表单页面已有的元素，编辑页只显示属性名，不填充示例值 */
      buildReceiptAttrs() {
        var record = this.record || {}
        var val = function (key) {
          var v = record[key]
          return (v === null || v === undefined) ? '' : String(v)
        }
        return [
          { key: 'rc_title', label: '小票标题', value: val('rc_title'), group: '单据头' },
          { key: 'rc_no', label: '单号', value: val('rc_no'), group: '单据头' },
          { key: 'rc_date', label: '日期', value: val('rc_date'), group: '单据头' },
          { key: 'rc_member', label: '会员', value: val('rc_member'), group: '单据头' },
          { key: 'rc_table', label: '商品明细表', value: '商品明细表', group: '商品明细' },
          { key: 'rc_qty_total', label: '数量合计', value: val('rc_qty_total'), group: '金额合计' },
          { key: 'rc_amount_total', label: '金额合计', value: val('rc_amount_total'), group: '金额合计' },
          { key: 'rc_remark', label: '备注', value: val('rc_remark'), group: '其它' },
          { key: 'rc_footer', label: '落款', value: val('rc_footer'), group: '其它' }
        ]
      },
      /** 通用默认布局：从 cfg.defaultKeys 挑字段，从上到下依次排 */
      applyDefaultLayout(cfg) {
        cfg = cfg || TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
        this.hiddenItemKeys = []
        this.specBlocks = []
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
        var resolved = this.labelLayout.styles[key]
        var style = resolved || this.attrStyles[key] || {}
        var result = {
          left: (style.x || 0) + 'px',
          top: (style.y || 0) + 'px',
          fontSize: (style.fontSize || 12) + 'px',
          fontWeight: style.fontWeight || 'normal',
          letterSpacing: (style.letterSpacing || 0) + 'px',
          lineHeight: String(style.lineHeight || 1.3),
          // 虚线外框由子元素 .lem-elem-frame 绘制，这里只传当前缩放值供其反缩放使用
          '--lem-zoom': String(this.zoom)
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
        var keys = []
        var nameKey = specNameKey(rowNo, blockNo)
        if (this.allAttrs.some(function(a) { return a.key === nameKey })) keys.push(nameKey)
        this.allAttrs.forEach(function(a) {
          var item = parseSpecItemKey(a.key)
          if (item && item.kind === 'value' && item.rowNo === rowNo && item.blockNo === blockNo) {
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
      updateSpecName(e) {
        var block = this.currentSpecBlock
        if (!block || !block.nameKey) return
        var val = typeof e === 'string' ? e : (e && e.target ? e.target.value : e)
        var clean = String(val || '').replace(/：$/, '')
        var attr = this.allAttrs.find(a => a.key === block.nameKey)
        if (attr) attr.value = clean ? clean + '：' : ''
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
      noop() {},
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
      // 拖拽移动/缩放：统一用 Pointer Events + setPointerCapture，
      // 手指移出元素后事件仍持续派发到捕获元素，不依赖 touch passive 机制
      // 选中逻辑也在这里完成（彻底移除 click 事件避免时序冲突）
      startPointerDrag(key, e, mode) {
        // 记录 pointerdown 瞬间的快照，留到 pointerup 做 toggle 判断
        this._pointerDownKey = key
        this._selectedBeforeDown = this.selectedKeys.slice()
        this._prevFocus = this.currentAttrKey  // ← 记录 pointerdown 前的焦点
        // ⚠️ 不在这里改 currentAttrKey！焦点只能在 pointerup toggle 后决定
        // 规格块整体拖动：属性名 + 全部规格值跟随同一个位移
        var blockKeys = this.specBlockKeysOf(key)
        this._blockDragKeys = mode === 'move' && blockKeys.length > 1 ? blockKeys : null
        // 规格块整体缩放：手柄拖动时统一调整块内所有元素宽度
        this._blockResizeKeys = mode === 'resize' && blockKeys.length > 1 ? blockKeys : null
        var willMultiDrag = (mode === 'move' || mode === 'resize') &&
          (this.selectedKeys.length > 1 || !!this._blockDragKeys || !!this._blockResizeKeys)
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
        var inner = clone.querySelector('.lem-elem-inner')
        if (inner) {
          inner.style.width = 'auto'
          inner.style.height = 'auto'
          inner.style.overflow = 'visible'
        }
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
        // 记录整体拖拽的所有元素起点（规格块拖动时包含块内隐藏项，保持布局不散）
        var allKeys = multiDrag ? (this._blockDragKeys || this._blockResizeKeys || this.selectedKeys.slice()) : [key]
        allKeys = allKeys.filter(function(k) { return !!self.attrStyles[k] })
        var dragOrigins = {}
        allKeys.forEach(function(k) {
          var s = self.attrStyles[k] || {}
          dragOrigins[k] = { x: s.x || 0, y: s.y || 0, width: s.width || 0, height: s.height || 0 }
        })
        // 规格块缩放：自动宽度的元素以当前渲染宽度为起点，避免手柄拖动时宽度跳变
        if (mode === 'resize') {
          allKeys.forEach(function(k) {
            if (!dragOrigins[k].width) dragOrigins[k].width = self.getItemSize(k).w
          })
        }
        // 缩放边框时的最小尺寸
        var minW = 12
        var minH = 12
        if (mode === 'resize') {
          if (this.isTextElem(key)) {
            // 文字类允许拖窄到文字自然宽度以下：由文字自动折行，高度随内容增长
            minW = 12
            minH = 12
          } else {
            var natural = this.measureContentSize(key)
            if (natural.width > 0) minW = natural.width
            if (natural.height > 0) minH = natural.height
          }
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
          blockResizeKeys: this._blockResizeKeys,
          dragOrigins: dragOrigins,
          // 吸附基准：未参与本次拖动的元素外接矩形
          snapTargets: this.collectSnapTargets(allKeys),
          keyW: this.getItemSize(key).w,
          keyH: this.getItemSize(key).h
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
      // 收集吸附基准：除本次拖动元素外的所有已加载元素外接矩形
      collectSnapTargets(movingKeys) {
        var self = this
        return this.selectedAttrs.filter(function(key) {
          return movingKeys.indexOf(key) === -1
        }).map(function(key) {
          var s = self.attrStyles[key] || {}
          var size = self.getItemSize(key)
          return {
            key: key,
            x: s.x || 0,
            y: s.y || 0,
            w: s.width > 0 ? s.width : size.w,
            h: s.height > 0 ? s.height : size.h
          }
        })
      },
      // 位置吸附：与最近元素的左/中/右、上/中/下对齐，返回吸附后的坐标与对齐线
      snapPosition(x, y, targets, size) {
        var TH = 6 / this.zoom
        var ownV = [0, size.w / 2, size.w]
        var ownH = [0, size.h / 2, size.h]
        var bestV = null
        var bestH = null
        ;(targets || []).forEach(function(t) {
          var tv = [t.x, t.x + t.w / 2, t.x + t.w]
          var th = [t.y, t.y + t.h / 2, t.y + t.h]
          tv.forEach(function(line) {
            ownV.forEach(function(off) {
              var diff = line - (x + off)
              if (Math.abs(diff) <= TH && (!bestV || Math.abs(diff) < Math.abs(bestV.diff))) {
                bestV = { diff: diff, line: line }
              }
            })
          })
          th.forEach(function(line) {
            ownH.forEach(function(off) {
              var diff = line - (y + off)
              if (Math.abs(diff) <= TH && (!bestH || Math.abs(diff) < Math.abs(bestH.diff))) {
                bestH = { diff: diff, line: line }
              }
            })
          })
        })
        return {
          x: bestV ? x + bestV.diff : x,
          y: bestH ? y + bestH.diff : y,
          guides: { v: bestV ? bestV.line : null, h: bestH ? bestH.line : null }
        }
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
          // 先按指针位移算出目标位置，再吸附到最近元素的对齐线
          var rawX = d.origX + dx / this.zoom
          var rawY = d.origY + dy / this.zoom
          var snapped = this.snapPosition(rawX, rawY, d.snapTargets, { w: d.keyW, h: d.keyH })
          this.guides = snapped.guides
          if (d.multiDrag && d.dragOrigins) {
            // 多选整体移动（不限制边界，可拖到屏幕任意可视位置）
            var offX = snapped.x - d.origX
            var offY = snapped.y - d.origY
            var self2 = this
            Object.keys(d.dragOrigins).forEach(function(k) {
              var origin = d.dragOrigins[k]
              self2.$set(self2.attrStyles[k], 'x', Math.round((origin.x || 0) + offX))
              self2.$set(self2.attrStyles[k], 'y', Math.round((origin.y || 0) + offY))
            })
          } else {
            // 单个元素移动（不限制边界，文字标签超出屏幕时可拖到任意可视位置）
            this.$set(this.attrStyles[d.key], 'x', Math.round(snapped.x))
            this.$set(this.attrStyles[d.key], 'y', Math.round(snapped.y))
          }
        } else if (d.mode === 'resize') {
          var minW = d.minW || 12
          if (d.blockResizeKeys && d.blockResizeKeys.length) {
            // 规格块整体缩放：统一调整块内每个元素的宽度，块内各行仍保持原有对齐
            var self3 = this
            var deltaW = Math.round(dx / this.zoom)
            d.blockResizeKeys.forEach(function(k) {
              var origin = d.dragOrigins[k] || {}
              var nextW = Math.max(minW, Math.round((origin.width || 0) + deltaW))
              self3.$set(self3.attrStyles[k], 'width', nextW)
              if (self3.attrStyles[k].height) self3.$delete(self3.attrStyles[k], 'height')
            })
            return
          }
          if (this.isTextElem(d.key)) {
            // 文字类：只改宽度，清掉固定高度，让文字折行后自动撑高/回缩
            var textW = Math.max(minW, Math.round(d.origW + dx / this.zoom))
            this.$set(this.attrStyles[d.key], 'width', textW)
            if (this.attrStyles[d.key].height) this.$delete(this.attrStyles[d.key], 'height')
          } else {
            // 其他元素（条码/表格等）：保持原宽高联动
            var minH = d.minH || 12
            var newW = Math.max(minW, Math.round(d.origW + dx / this.zoom))
            var newH = Math.max(minH, Math.round(d.origH + dy / this.zoom))
            this.$set(this.attrStyles[d.key], 'width', newW)
            this.$set(this.attrStyles[d.key], 'height', newH)
          }
        }
      },
      onPointerUp() {
        var prevKeys = this._selectedBeforeDown || []
        var prevFocus = this._prevFocus
        var pKey = this._pointerDownKey
        var didMove = this.dragging.moved
        if (this.dragging.active) {
          this.dragging.active = false
          this.dragging.moved = false
          this.guides = { v: null, h: null }
          this.pushSnapshot()
        }
        // 没移动 + 不是 resize 手柄操作 → 点击意图，执行 toggle
        if (!didMove && pKey && (!this.dragging.mode || this.dragging.mode === 'move')) {
          // 规格块内任一元素：点击按整块选中/取消（已关闭的规格值保持关闭），普通元素按单项处理
          var blockKeys = this.specBlockKeysOf(pKey)
          var hiddenKeys = this.hiddenItemKeys || []
          var targets = blockKeys.length
            ? blockKeys.filter(function(k) { return hiddenKeys.indexOf(k) === -1 })
            : [pKey]
          if (!targets.length) targets = [pKey]
          var allSelected = targets.every(function(k) { return prevKeys.indexOf(k) > -1 })
          if (allSelected) {
            // 之前已选中 → 整块移除
            var next = prevKeys.filter(function(k) { return targets.indexOf(k) === -1 })
            this.selectedKeys = next
            // 焦点逻辑：
            //   移除的是 prevFocus → 无焦点（不自动转给剩余元素）
            //   移除的是非焦点  → prevFocus 保持不变
            if (prevFocus && targets.indexOf(prevFocus) > -1) {
              this.currentAttrKey = null
            } else {
              this.currentAttrKey = prevFocus
            }
            // 全部取消后退出多选状态
            if (!next.length) this.multiSelectMode = false
          } else {
            // 之前未选中：规格块整块选中；多选状态下追加，普通单击则单选
            var base = this.multiSelectMode ? prevKeys.slice() : []
            targets.forEach(function(k) {
              if (base.indexOf(k) === -1) base.push(k)
            })
            this.selectedKeys = base
            this.currentAttrKey = pKey
          }
          // 选择/取消选择也计入撤销历史
          this.pushSnapshot()
        }
        this._blockDragKeys = null
        this._blockResizeKeys = null
        // 移动过 → 拖拽意图，恢复 pointerdown 前的焦点（如果有的话）
        // 注意 pointerdown 没改 currentAttrKey，所以这里不需要恢复
        this._pointerDownKey = null
        this._selectedBeforeDown = null
        this._prevFocus = null
        this.unbindPointerEvents()
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
        var blocks = []
        this.allAttrs.forEach(function(attr) {
          var parsed = parseSpecItemKey(attr.key)
          if (!parsed || parsed.rowNo !== 1) return
          var block = blocks[parsed.blockNo - 1]
          if (!block) block = blocks[parsed.blockNo - 1] = { name: '', values: [] }
          var text = attr.value == null ? '' : String(attr.value)
          if (parsed.kind === 'name') {
            block.name = text.replace(/：$/, '')
          } else {
            block.values[parsed.valueNo - 1] = text
          }
        })
        return normalizeSpecBlocks(blocks)
      },
      /** 模板只保存布局与打印设置；方向/份数/设备不写入模板 */
      buildV2(name, cfg) {
        // 小票单品元素按单据逐次生成，不写入模板，避免元素数量越积越多
        var self = this
        var selectedAttrs = this.selectedAttrs.filter(function(key) { return !isReceiptItemKey(key) })
        var attrStyles = {}
        Object.keys(this.attrStyles).forEach(function(key) {
          if (!isReceiptItemKey(key)) attrStyles[key] = self.attrStyles[key]
        })
        var attrLabels = {}
        Object.keys(this.attrLabels).forEach(function(key) {
          if (!isReceiptItemKey(key)) attrLabels[key] = self.attrLabels[key]
        })
        // 小票规格名/规格值开关关闭的 key：随模板保存，重开编辑页/打印时保持不显示
        var hiddenItemKeys = (this.dynamicItemKeys || []).filter(function(key) {
          return self.selectedAttrs.indexOf(key) === -1
        })
        var v2 = editorStateToV2({
          name: name,
          provider: this.provider || this.businessType || '',
          businessType: this.provider || this.businessType || '',
          selectedAttrs: selectedAttrs,
          attrStyles: attrStyles,
          attrLabels: attrLabels,
          allAttrs: this.allAttrs.filter(function(attr) { return !isReceiptItemKey(attr.key) }),
          goodsFields: this.goodsFields,
          specBlocks: this.collectSpecBlocks(),
          hiddenItemKeys: hiddenItemKeys,
          labelWidth: this.labelWidth,
          labelHeight: this.labelHeight,
          overflowMode: this.overflowMode,
          pageMode: this.effectivePageMode,
          maxRowsPerPage: 0,
          repeatHeader: this.repeatHeader,
          flowElementKeys: this.flowElementKeys
        }, {
          type: this.templateType,
          kind: cfg && cfg.storage === 'deliveryTemplate' ? 'delivery-note' : null
        })
        return v2
      },
      /** 发货单/小票/会员/地址：使用统一模板数据保存（不涉及商品类型） */
      persistUnifiedTemplate(cfg, name) {
        var self = this
        var v2 = this.buildV2(name, cfg)
        if (cfg.storage === 'deliveryTemplate') {
          var base = this.deliveryTemplate || {}
          var tpl = Object.assign({}, v2, {
            name: name,
            serverId: base.serverId || null,
            deliveryNote: base.deliveryNote
          })
          return persistDeliveryTemplate(tpl).then(function(res) {
            if (res && res.code === 200 && res.data != null) {
              self.deliveryTemplate = Object.assign({}, base, v2ToEditorState(v2), { serverId: res.data, id: res.data })
            }
            self.showSaveResult(res, cfg)
            return res && res.code === 200
          }).catch(function(err) {
            self.$message.error(cfg.title + '保存失败：' + self.formatRequestError(err))
            return false
          })
        }
        // 单 key 覆盖写模式
        return this.postSnapshot(cfg.platformConfigKey, v2).then(function(res) {
          self.showSaveResult(res, cfg)
          return res && res.code === 200
        }).catch(function(err) {
          self.$message.error(cfg.title + '保存失败：' + self.formatRequestError(err))
          return false
        })
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
      receiptCategoryId() {
        var record = this.record || {}
        if (record.categoryId != null && record.categoryId !== '') return record.categoryId
        var rows = Array.isArray(record.goodsRows) ? record.goodsRows : []
        for (var i = 0; i < rows.length; i++) {
          if (rows[i] && rows[i].categoryId != null && rows[i].categoryId !== '') return rows[i].categoryId
        }
        return null
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
      /** 商品品类设置里的规格块（规格名 + 规格值名） */
      categorySpecBlocks() {
        var categoryId = this.receiptCategoryId()
        if (categoryId == null) return []
        var node = this.findCategoryNode(categoryId)
        return node ? buildSpecBlocksFromCategory(node.specConfig) : []
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
          overflowMode: this.overflowMode,
          pageMode: this.effectivePageMode,
          maxRowsPerPage: 0,
          repeatHeader: this.repeatHeader,
          flowElementKeys: this.flowElementKeys.slice()
        })
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
        // 发货单/小票/会员/地址：同一套保存入口，按各自模板数据模块落地
        if (cfg.storage !== 'localTemplates') {
          this.persistUnifiedTemplate(cfg, name).then(finish)
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
        var tpl = this.buildTplPayload({
          id: exist ? exist.id : 'tpl_' + Date.now(),
          serverId: exist ? exist.serverId : null,
          name: name,
          objectType: this.templateObject,
          type: tplType,
          materialId: materialId,
          categoryId: isCategoryTpl ? this.templateCategoryId : null,
          categoryName: isCategoryTpl ? this.getCategoryName(this.templateCategoryId) : null
        })
        // 覆盖当前模板，或新增一条（同类型允许保存多个模板）
        var idx = this.templates.findIndex(function(t) { return t.id === tpl.id })
        if (idx > -1) this.templates.splice(idx, 1, tpl)
        else this.templates.push(tpl)
        this.selectedTemplateId = tpl.id
        this.persistTemplate(tpl, '模板已保存').then(function() { finish(true) })
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
        var tpl = this.buildTplPayload({
          id: 'tpl_' + Date.now(),
          serverId: null,
          name: name,
          objectType: this.templateObject,
          type: isCategoryTpl ? 'default' : (this.templateObject === 'product' && !materialId ? 'default' : 'normal'),
          materialId: materialId,
          categoryId: isCategoryTpl ? this.templateCategoryId : null,
          categoryName: isCategoryTpl ? this.getCategoryName(this.templateCategoryId) : null
        })
        this.templates.push(tpl)
        this.selectedTemplateId = tpl.id
        this.saveAsVisible = false
        var self = this
        this.persistTemplate(tpl, '模板已另存为').then(function() {
          self.newTemplateName = name
          self.currentTemplateName = name
          self.saveSheetVisible = false
        })
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
          var cfgInfo = TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label
          var currentId = this.selectedTemplateId
          var current = currentId
            ? this.templates.find(function(t) { return t.id === currentId })
            : null
          // 快照与保存模板使用同一 v2 结构，预览按同一套规则还原，样式保持一致
          var snapshot = this.buildV2((current && current.name) || cfgInfo.title.replace('编辑', '') + '模板', cfgInfo)
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

.lem-elem {
  position: absolute;
  cursor: move;
  /* 与打印预览一致：无内边距/边框，文字从元素 x 起始，宽度即文本框宽度 */
  padding: 0;
  border: none;
  box-sizing: border-box;
  white-space: nowrap;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
  z-index: 1;
}

/* 所有已加载元素统一虚线外框（点击后只显示手柄）
   注意：不能使用 outline/小于 1px 的边框线宽——WebKit（iOS Safari）会把小于 1px 的
   边框和 outline 线宽按边丢弃，导致左右边线不显示。这里用 1px 边框的子元素按 1/zoom
   反缩放，缩放后仍是 1px 视觉线宽，且四条边都能稳定绘制 */
.lem-elem-frame {
  position: absolute;
  left: 0;
  top: 0;
  box-sizing: border-box;
  width: calc(100% * var(--lem-zoom, 1));
  height: calc(100% * var(--lem-zoom, 1));
  /* 未选中/未点击：灰色边框 */
  border: 2px dashed #bfbfbf;
  transform: scale(calc(1 / var(--lem-zoom, 1)));
  transform-origin: top left;
  pointer-events: none;
}

/* 被点击选中的元素：沿用原来的蓝色边框 */
.lem-elem--active .lem-elem-frame {
  border-color: rgba(24, 144, 255, 0.85);
}

/* 规格块整块：属性名 + 全部规格值共用一个外框，样式与普通文字标签一致，可整体拖动/缩放 */
.lem-spec-block {
  cursor: move;
  touch-action: none;
  /* 高于块内文字元素，整块接收点击与拖动 */
  z-index: 6;
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

/* 内容层：只裁切内容，不裁选手柄 */
.lem-elem-inner {
  width: 100%;
  height: 100%;
  overflow: hidden;
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
  z-index: 3;
}

/* 视觉仍是 20px 圆点，透明感应区扩到 44×44 */
.lem-resize-handle::before,
.lem-delete-btn::before {
  content: '';
  position: absolute;
  inset: -12px;
  border-radius: 50%;
  background: transparent;
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
  touch-action: none;
  z-index: 4;
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

/* 数据表选择：窄栏内纵向排列 */
.lem-provider {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.lem-provider-label {
  flex: none;
  font-size: 11px;
  color: #999;
}

.lem-provider-select {
  width: 100%;
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

/* 元素列表：窄栏单列，中文名在上、字段 key 在下 */
.lem-elem-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 4px;
  padding: 6px 4px;
  border-bottom: 1px solid #f5f5f5;
}

.lem-elem-text {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.lem-elem-item .anticon {
  color: #bfbfbf;
  flex: 0 0 auto;
}

.lem-elem-icon--on {
  color: #1890ff;
}

.lem-elem-cn {
  font-size: 12px;
  line-height: 16px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lem-elem-key {
  font-size: 11px;
  line-height: 14px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 勾选式元素列表：分组标题 + 已选中高亮 */
.lem-elem-group-title {
  font-size: 11px;
  color: #999;
  padding: 5px 4px;
  background: #fafafa;
  margin-top: 2px;
}

.lem-elem-item-active {
  background: #e6f7ff;
}

.lem-elem-item-active .lem-elem-cn {
  color: #1890ff;
}

.lem-add-custom {
  margin-top: 8px;
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
</style>
