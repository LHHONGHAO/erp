<template>
  <component
    :is="inline ? 'div' : 'JModal'"
    v-bind="inline ? {} : modalProps"
    :class="inline ? 'mm-inline-form' : ''"
    @cancel="handleCancel"
    :style="inline ? '' : modalStyle">
    <template v-if="!inline" slot="footer">
      <a-button key="back" @click="handleCancel">取消</a-button>
      <a-button type="primary" v-if="showOkFlag" :loading="confirmLoading" @click="handleOk">保存（Ctrl+S）</a-button>
    </template>
    <a-spin :spinning="confirmLoading">
      <a-form :form="form">
        <a-tabs v-model:activeKey="activeKey" size="small" :class="{ 'mm-tabs-mobile': isMobileView }">
          <a-tab-pane key="1" tab="基本信息" id="materialHeadModal" forceRender>
            <!-- 手机端：顶部三个页签动态条，按页显示标题 -->
            <div v-if="isMobileView" class="mm-page-tabs">
              <div v-for="(t, i) in mobilePageTitles" :key="i" class="mm-page-tab" :class="{ active: mobilePageIndex === i }" @click="goMobilePage(i)">
                <span class="mm-page-tab-text">{{ t }}</span>
                <span class="mm-page-tab-bar"></span>
              </div>
            </div>
            <!-- 手机端：基本信息 / 商品信息 / 价格设置 三页横向滑动切换 -->
            <div class="mm-pages" :class="{ 'is-mobile': isMobileView }" ref="mmPages" @scroll="onPagesScroll">
            <!-- 第 1 页：基本信息 -->
            <div class="mm-page">
            <div v-if="!inline" style="margin-bottom: 8px; text-align: right;">
              <a-button size="small" icon="setting" @click="openPropertyConfig">属性设置</a-button>
            </div>
            <template v-for="(row, rowIndex) in basePropertyRows">
              <a-row class="form-row" :gutter="24" :key="'row'+rowIndex">
                <a-col :md="formColSpan" :sm="24" v-for="prop in row" :key="prop.field">
                  <!-- 名称 -->
                  <a-form-item v-if="prop.field==='name'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'name', validatorRules.name ]" @change="handleNameChange" >
                      <a-icon slot="addonAfter" type="scan" style="cursor:pointer;color:#1890ff;" @click="openOcrModal" title="图片识别" />
                    </a-input>
                  </a-form-item>
                  <!-- 单位 -->
                  <a-form-item v-else-if="prop.field==='unit'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'unit', validatorRules.unit ]" />
                  </a-form-item>
                  <!-- 品牌 -->
                  <a-form-item v-else-if="prop.field==='brand'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'brand' ]" />
                  </a-form-item>
                  <!-- 助记码 -->
                  <a-form-item v-else-if="prop.field==='mnemonic'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'自动生成'+prop.anotherName" v-decorator.trim="[ 'mnemonic' ]" :readOnly="true" />
                  </a-form-item>
                  <!-- 类别 -->
                  <a-form-item v-else-if="prop.field==='categoryId'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-tree-select style="width:100%" :dropdownStyle="{maxHeight:'200px',overflow:'auto'}" allow-clear
                                   :treeData="categoryTreeFiltered" :treeDefaultExpandAll="true"
                                   v-decorator="[ 'categoryId', validatorRules.categoryId ]"
                                   :placeholder="'请选择'+prop.anotherName+'（须选二级类目，一级类目不可选）'" @change="handleCategoryChange">
                    </a-tree-select>
                  </a-form-item>
                  <!-- 填充物重量 -->
                  <a-form-item v-else-if="prop.field==='weight'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input-number style="width: 100%" :placeholder="'请输入'+prop.anotherName+'(kg)'" v-decorator.trim="[ 'weight' ]" />
                  </a-form-item>
                  <!-- 制造商 -->
                  <a-form-item v-else-if="prop.field==='mfrs'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'mfrs' ]" />
                  </a-form-item>
                  <!-- 扩展1 -->
                  <a-form-item v-else-if="prop.field==='otherField1'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'otherField1' ]" />
                  </a-form-item>
                  <!-- 扩展2 -->
                  <a-form-item v-else-if="prop.field==='otherField2'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'otherField2' ]" />
                  </a-form-item>
                  <!-- 扩展3 -->
                  <a-form-item v-else-if="prop.field==='otherField3'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'otherField3' ]" />
                  </a-form-item>
                  <!-- 扩展4 -->
                  <a-form-item v-else-if="prop.field==='otherField4'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'otherField4' ]" />
                  </a-form-item>
                  <!-- 扩展5 -->
                  <a-form-item v-else-if="prop.field==='otherField5'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'otherField5' ]" />
                  </a-form-item>
                  <!-- 扩展6 -->
                  <a-form-item v-else-if="prop.field==='otherField6'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'otherField6' ]" />
                  </a-form-item>
                  <!-- 扩展7 -->
                  <a-form-item v-else-if="prop.field==='otherField7'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'otherField7' ]" />
                  </a-form-item>
                  <!-- 面料 -->
                  <a-form-item v-else-if="prop.field==='fabric'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'fabric' ]" />
                  </a-form-item>
                  <!-- 安全类别 -->
                  <a-form-item v-else-if="prop.field==='safetyCategory'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'safetyCategory' ]" />
                  </a-form-item>
                  <!-- 执行标准 -->
                  <a-form-item v-else-if="prop.field==='executiveStandard'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'executiveStandard' ]" />
                  </a-form-item>
                  <!-- 等级 -->
                  <a-form-item v-else-if="prop.field==='grade'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'grade' ]" />
                  </a-form-item>
                  <!-- 检验 -->
                  <a-form-item v-else-if="prop.field==='inspection'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'inspection' ]" />
                  </a-form-item>
                  <!-- 填充物 -->
                  <a-form-item v-else-if="prop.field==='filler'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName" v-decorator.trim="[ 'filler' ]" />
                  </a-form-item>
                  <!-- 序列号 -->
                  <a-form-item v-else-if="prop.field==='enableSerialNumber'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-tooltip title="如果选择为有，则在采购入库单需要录入该商品的序列号">
                      <a-select :placeholder="'请选择'+prop.anotherName" v-decorator="[ 'enableSerialNumber' ]">
                        <a-select-option value="1">有</a-select-option>
                        <a-select-option value="0">无</a-select-option>
                      </a-select>
                    </a-tooltip>
                  </a-form-item>
                  <!-- 条码 -->
                  <a-form-item v-else-if="prop.field==='barCode'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input :placeholder="'请输入'+prop.anotherName+'（非必填）'" v-decorator="[ 'barCode' ]" />
                  </a-form-item>
                  <!-- 商品ID -->
                  <a-form-item v-else-if="prop.field==='id'" :labelCol="labelCol" :wrapperCol="wrapperCol" :label="prop.anotherName">
                    <a-input-number style="width: 100%" :placeholder="'自动生成'+prop.anotherName" v-decorator="[ 'id' ]" :disabled="true" />
                  </a-form-item>
                </a-col>
              </a-row>
            </template>
            </div>
            <!-- 第 2 页：商品信息（规格） -->
            <div class="mm-page">
            <!-- 商品信息卡片：规格模块 -->
            <a-card :title="'商品信息' + (categorySelected ? '' : ' — 选择种类')" size="small" :bordered="true" style="margin-top: 12px;">
              <div v-if="!categorySelected" style="text-align: center; padding: 40px 0; color: #999;">
                <a-icon type="info-circle" style="font-size: 24px;" />
                <div style="margin-top: 8px;">请先在基本信息中选择类别，规格将自动加载</div>
              </div>
              <template v-else>
              <div v-for="(module, mIndex) in specModules" :key="mIndex" class="spec-module">
                <!-- 头部：规格名称 + 图片开关 + 删除 -->
                <div class="spec-header">
                  <span v-if="specLocked" class="spec-header-name">{{ module.name }}</span>
                  <a-select v-else
                    v-model="module.name"
                    showSearch
                    allowClear
                    placeholder="请选择"
                    style="width: 220px;"
                    optionFilterProp="children"
                    :disabled="specLocked"
                    @change="onSpecNameChange(mIndex)">
                    <a-select-option v-for="attr in materialAttributeList" :key="attr.value" :value="attr.name">
                      {{ attr.name }}
                    </a-select-option>
                  </a-select>
                  <a-switch
                    size="small"
                    :checked="module.enableImage"
                    :disabled="specLocked"
                    @change="onToggleSpecImage(mIndex, $event)"
                    style="margin: 0 8px 0 20px;" />
                  <span class="spec-header-label">添加规格图片</span>
                  <a-button
                    v-if="!specLocked"
                    type="link"
                    @click="removeSpecModule(mIndex)"
                    class="spec-header-delete"
                    style="color: #1890ff; padding: 0;">
                    <a-icon type="delete" style="color: #1890ff;" />
                    <span style="font-size: 14px; color: #1890ff;">删除</span>
                  </a-button>
                </div>
                <!-- 分割线 -->
                <div class="spec-divide"></div>
                <!-- 主内容区：规格值列表 -->
                <div class="spec-main">
                  <div class="spec-values-list">
                    <div v-for="(val, vIndex) in module.values" :key="vIndex" class="spec-value-row">
                      <!-- 普通规格值：输入框 → 图片方块 → 删除 -->
                      <template v-if="!module.childNames || module.childNames.length === 0">
                        <a-input
                          v-model="val.text"
                          placeholder="请输入"
                          style="width: 220px;"
                          @blur="onSpecChange()" />
                        <!-- 图片方块（开启图片时显示）：点击先打开图片文字提取框 -->
                        <div v-if="module.enableImage" class="spec-value-img-upload" @click="openSpecImageExtract(mIndex, vIndex)">
                          <div v-if="val.image" class="spec-value-img-preview">
                            <img :src="getFileAccessHttpUrl(val.image)" />
                          </div>
                          <div v-else class="spec-value-img-trigger">
                            <a-icon type="plus" style="font-size: 16px; color: #999;" />
                          </div>
                        </div>
                        <a-button
                          type="link"
                          size="small"
                          @click="removeSpecValue(mIndex, vIndex)"
                          class="spec-value-delete-btn">
                          <a-icon type="delete" style="color: #1890ff;" />
                        </a-button>
                      </template>
                      <!-- 组合规格值：多个子项合并成一个SKU规格维度 -->
                      <div v-else class="spec-composite-value">
                        <div v-for="(childName, childIndex) in module.childNames" :key="childName" class="spec-composite-field">
                          <span>{{ childName }}</span>
                          <a-input
                            :value="val.subValues && val.subValues[childName]"
                            :placeholder="'请输入' + childName"
                            @input="onSubSpecChange(mIndex, vIndex, childName, $event.target.value)" />
                          <!-- 图片方块：紧跟在第一行子规格输入框右侧 -->
                          <div v-if="childIndex === 0 && module.enableImage" class="spec-value-img-upload" @click="openSpecImageExtract(mIndex, vIndex)">
                            <div v-if="val.image" class="spec-value-img-preview">
                              <img :src="getFileAccessHttpUrl(val.image)" />
                            </div>
                            <div v-else class="spec-value-img-trigger">
                              <a-icon type="plus" style="font-size: 16px; color: #999;" />
                            </div>
                          </div>
                          <!-- 删除按钮：仅在第一行子规格输入框右侧显示，紧邻输入框 -->
                          <a-button
                            v-if="childIndex === 0"
                            type="link"
                            size="small"
                            @click="removeSpecValue(mIndex, vIndex)"
                            class="spec-value-delete-btn">
                            <a-icon type="delete" style="color: #1890ff;" />
                          </a-button>
                        </div>
                        <span class="spec-composite-preview" v-if="val.text">组合值：{{ val.text }}</span>
                      </div>
                    </div>
                  </div>
                </div>
                <!-- 底部：添加规格值 -->
                <div class="spec-bottom">
                  <a-button type="link" @click="addSpecValue(mIndex)" class="spec-add-value-btn">
                    <a-icon type="plus" />
                    <span>添加规格值</span>
                  </a-button>
                  <div class="spec-value-suggestions" v-if="getSpecValueSuggestions(mIndex).length">
                    <span class="spec-suggestions-label">已保存：</span>
                    <span
                      v-for="suggestion in getSpecValueSuggestions(mIndex)"
                      :key="suggestion.id || suggestion.valueText"
                      class="spec-value-suggestion-card">
                      <span class="spec-value-suggestion-text" @click="addSuggestedSpecValue(mIndex, suggestion)">{{ suggestion.valueText }}</span>
                      <a-popconfirm
                        title="删除这个常用规格值卡片？不会修改已保存的商品和SKU。"
                        okText="删除"
                        cancelText="取消"
                        @confirm="removeSpecValueSuggestion(mIndex, suggestion)">
                        <a-icon type="close" class="spec-suggestion-delete" title="删除卡片" @click.stop />
                      </a-popconfirm>
                    </span>
                  </div>
                </div>
              </div>
              <!-- 添加规格按钮 -->
              <a-button
                v-if="!specLocked"
                type="primary"
                @click="addSpecModule"
                style="width: 158px;">
                <a-icon type="plus" />
                添加规格
              </a-button>
              </template>
            </a-card>
            </div>
            <!-- 第 3 页：价格设置 -->
            <div class="mm-page">
            <div style="margin-top:8px;" id="materialDetailModal">
              <div class="price-setting-wrapper" :class="{ 'price-setting-fullscreen': priceTableFullscreen }">
                <!-- 价格设置标题和进度 -->
                <div class="price-setting-top-bar">
                  <div class="price-setting-top-bar-left">
                    <span class="price-setting-title"><span class="required-mark">*</span> 价格设置</span>
                    <span class="progress-label">价格填写进度</span>
                    <a-progress :percent="priceFillProgress" :strokeWidth="6" strokeColor="#16a34a" style="width: 130px;" />
                    <span class="progress-percent">{{ priceFillProgress }}%</span>
                  </div>
                  <div class="price-setting-top-bar-right">
                    <a-button type="link" size="small" @click="togglePriceTableFullscreen">
                      <a-icon :type="priceTableFullscreen ? 'fullscreen-exit' : 'fullscreen'" />
                      {{ priceTableFullscreen ? '退出全屏' : '全屏' }}
                    </a-button>
                  </div>
                </div>
                <!-- 批量设置工具栏 -->
                <div class="price-setting-batch-bar">
                  <div class="price-setting-batch-row">
                  <a-popover trigger="click" placement="bottomLeft" :overlayClassName="'spec-filter-popover'">
                    <template slot="content">
                      <div class="spec-filter-panel">
                        <div class="spec-filter-left">
                          <div v-for="(module, mIndex) in specModules" :key="mIndex"
                            class="spec-filter-left-item"
                            :class="{ active: selectedSpecFilter === mIndex }"
                            @click="selectedSpecFilter = mIndex">
                            <a-checkbox
                              :checked="isSpecAllSelected(module)"
                              :indeterminate="isSpecIndeterminate(module)"
                              @change="toggleAllSpecValues(module, $event)"
                              @click.stop.native />
                            <span class="spec-filter-left-name">{{ module.name || ('规格' + (mIndex + 1)) }}</span>
                            <a-icon type="right" class="spec-filter-left-arrow" />
                          </div>
                          <div v-if="specModules.length === 0" style="color: #999; padding: 12px;">请先添加规格</div>
                        </div>
                        <div class="spec-filter-right" v-if="selectedSpecFilter >= 0 && specModules[selectedSpecFilter]">
                          <div class="spec-filter-right-values">
                            <a-checkbox
                              v-for="(val, vIndex) in specModules[selectedSpecFilter].values"
                              :key="vIndex"
                              :checked="isSpecValueSelected(specModules[selectedSpecFilter].name, val.text)"
                              @change="toggleSpecValue(specModules[selectedSpecFilter].name, val.text, $event)"
                              class="spec-filter-value-item">
                              {{ val.text }}
                            </a-checkbox>
                          </div>
                        </div>
                      </div>
                    </template>
                    <div class="spec-filter-trigger">
                      <div class="spec-filter-tags" v-if="selectedFilterCount > 0">
                        <span v-for="(item, idx) in selectedFilterTagList" :key="idx" class="spec-filter-tag">
                          {{ item.label }}
                          <a-icon type="close" class="spec-filter-tag-close" @click.stop="removeFilterTag(item)" />
                        </span>
                      </div>
                      <span v-else>选择范围</span>
                      <a-icon type="down" />
                    </div>
                  </a-popover>
                  <a-input v-model="batchPurchasePrice" addonBefore="进货价" addonAfter="元" placeholder="请输入" class="price-setting-input" />
                  <a-input v-model="batchCommodityPrice" addonBefore="零售价" addonAfter="元" placeholder="请输入" class="price-setting-input" />
                  <a-input v-model="batchMarketPrice" addonBefore="划线价" addonAfter="元" placeholder="请输入" class="price-setting-input" />
                  <a-input v-model="batchStock" addonBefore="期初库存" placeholder="请输入" class="price-setting-input" />
                  <a-input v-model="batchLowSafeStock" addonBefore="最低库存预警" placeholder="请输入" class="price-setting-input" />
                  <a-input v-model="batchHighSafeStock" addonBefore="最高库存预警" placeholder="请输入" class="price-setting-input" />
                  <a-select v-model="batchDepotId" placeholder="请选择仓库" class="price-setting-input" :options="depotOptions" allowClear showSearch optionFilterProp="children" :disabled="!batchStockPositive" />
                  <a-select v-model="batchStore" placeholder="请选择门店" class="price-setting-input" :options="storeOptions" allowClear showSearch optionFilterProp="children" :disabled="!batchStockPositive" />
                  <a-button type="primary" @click="batchSetColumn">批量设置</a-button>
                </div>
                </div>
                <!-- 表格 -->
                <j-editable-table
                  v-show="!isMobileView"
                  ref="editableMeTable"
                  :loading="meTable.loading"
                  :columns="meTable.columns"
                  :dataSource="meTable.dataSource"
                  :minWidth="1300"
                  :maxHeight="priceTableFullscreen ? 520 : skuTableBodyHeight"
                  :rowNumber="false"
                  :rowSelection="false"
                  :actionButton="false"
                  @valueChange="onValueChange"
                  @added="onAdded"
                  @deleted="onDeleted">
                </j-editable-table>
                <!-- 手机端：SKU 价格按卡片单列显示 -->
                <div v-if="isMobileView" class="mm-sku-cards">
                  <div v-for="(row, rIndex) in meTable.dataSource" :key="rIndex" class="mm-sku-card">
                    <div class="mm-sku-card-head">
                      <span class="mm-sku-card-title">规格块 {{ rIndex + 1 }}</span>
                      <span class="mm-sku-card-spec">{{ getSkuCardSpecText(row) }}</span>
                    </div>
                    <div v-for="col in meTable.columns" :key="col.key" class="mm-sku-card-field">
                      <span class="mm-sku-card-label">{{ col.title }}</span>
                      <div class="mm-sku-card-control">
                        <span v-if="col.readonly" class="mm-sku-card-text">{{ row[col.key] || '-' }}</span>
                        <a-input-number v-else-if="col.type === 'inputNumber'" v-model="row[col.key]"
                          :placeholder="colPlaceholder(col)" style="width:100%" />
                        <a-select v-else-if="col.type === 'sel_search' || col.type === 'select'" v-model="row[col.key]"
                          :options="col.options || []" :placeholder="colPlaceholder(col)"
                          style="width:100%" allowClear showSearch optionFilterProp="children" />
                        <a-input v-else v-model="row[col.key]" :placeholder="colPlaceholder(col)" />
                      </div>
                    </div>
                  </div>
                  <div v-if="meTable.dataSource.length === 0" class="mm-sku-card-empty">暂无规格数据，请先添加规格</div>
                </div>
              </div>
              <!-- 表单区域 -->
              <batch-set-price-modal ref="priceModalForm" @ok="batchSetPriceModalFormOk"></batch-set-price-modal>
            </div>
            <a-row class="form-row" :gutter="24">
              <a-col :lg="24" :md="24" :sm="24">
                <a-form-item :labelCol="labelCol" :wrapperCol="{xs: { span: 24 },sm: { span: 24 }}" label="">
                  <a-textarea :rows="1" placeholder="请输入备注" v-decorator="[ 'remark' ]" style="margin-top:8px;"/>
                </a-form-item>
              </a-col>
            </a-row>
            </div>
            </div>
          </a-tab-pane>
        </a-tabs>
      </a-form>
    </a-spin>
    <a-modal :title="'基础属性设置'" :visible="propertyConfigVisible" :width="500" @ok="savePropertyConfig" @cancel="propertyConfigVisible=false">
      <!-- 每行列数 -->
      <div style="margin-bottom: 16px;">
        <span style="margin-right: 12px;">每行列数：</span>
        <a-select v-model="propertyConfigCols" style="width: 80px;">
          <a-select-option v-for="n in 8" :key="n" :value="n">{{ n }}列</a-select-option>
        </a-select>
      </div>
      <!-- 基础属性勾选（按勾选顺序排列） -->
      <div style="margin-bottom: 16px; max-height: 280px; overflow-y: auto; padding-right: 4px;">
        <div style="margin-bottom: 8px; color: #999; font-size: 12px;">按勾选顺序排列显示（取消勾选可隐藏属性）</div>
        <a-checkbox-group v-model="propertyConfigSelected" style="width: 100%;">
          <a-row v-for="prop in propertyConfigBaseColumns" :key="prop.field" style="margin-bottom: 6px;">
            <a-checkbox :value="prop.field">{{ prop.nativeName }}</a-checkbox>
          </a-row>
        </a-checkbox-group>
      </div>
      <!-- 扩展字段别名配置（仅扩展字段可修改名称） -->
      <div style="border-top: 1px solid #f0f0f0; padding-top: 12px;">
        <div style="margin-bottom: 8px; color: #999; font-size: 12px;">扩展字段别名（仅扩展字段可修改名称）</div>
        <a-row v-for="(alias, aliasField) in propertyConfigAliasList" :key="aliasField" style="margin-bottom: 8px;">
          <a-col :span="24">
            <span style="display:inline-block;width:60px;">{{ alias.label }}：</span>
            <a-input v-model="propertyConfigAlias[aliasField]" :placeholder="alias.label" style="width: calc(100% - 70px);" />
          </a-col>
        </a-row>
      </div>
      <div style="border-top: 1px solid #f0f0f0; padding-top: 12px; text-align: left;">
        <a-button @click="handlePropertyConfigReset" type="link" size="small">恢复默认</a-button>
      </div>
    </a-modal>
    <!-- 图片识别弹窗 -->
    <ocr-recognize-modal :visible="ocrVisible" @ok="handleOcrResult" @cancel="ocrVisible=false" />
    <ocr-recognize-modal
      :visible="specImageOcrVisible"
      :imageFile="specImageOcrFile"
      :extractOnly="true"
      @cancel="handleSpecImageOcrClose" />
  </component>
</template>
<script>
  import pick from 'lodash.pick'
  import BatchSetPriceModal from './BatchSetPriceModal'
  import OcrRecognizeModal from './OcrRecognizeModal'
  import JEditableTable from '@/components/jeecg/JEditableTable'
  import { FormTypes, getRefPromise, VALIDATE_NO_PASSED, validateFormAndTables } from '@/utils/JEditableTableUtil'
  import { changeNameToPinYin, checkMaterial, checkMaterialBarCode, getMaterialAttributeNameList, getMaterialCategorySpecValues, deleteMaterialCategorySpecValue, getMaxBarCode, queryMaterialCategoryTreeList, getDepotList } from '@/api/api'
  import { autoJumpNextInput, handleIntroJs, removeByVal, addBigNumbers } from '@/utils/util'
  import { getAction, httpAction, postAction } from '@/api/manage'
  import JImageUpload from '@/components/jeecg/JImageUpload'
  import JDate from '@/components/jeecg/JDate'
  import JModal from '@/components/jeecg/JModal'
  import Vue from 'vue'

  export default {
    name: "MaterialModal",
    components: {
      BatchSetPriceModal,
      OcrRecognizeModal,
      JImageUpload,
      JDate,
      JModal,
      JEditableTable,
      VNodes: {
        functional: true,
        render: (h, ctx) => ctx.props.vnodes,
      }
    },
    props: {
      // 嵌入模式：不使用弹窗，直接把表单渲染为页面内容
      inline: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        title:"操作",
        width: '1300px',
        visible: false,
        modalStyle: '',
        action: '',
        autoAddSpec: false,
        activeKey: '1',
        // 手机端：基本信息 / 商品信息 / 价格设置 三页横向滑动切换
        isMobileView: window.innerWidth <= 768,
        mobilePageIndex: 0,
        mobilePageTitles: ['基本信息', '商品信息', '价格设置'],
        categoryTree: [],
        fileList:[],
        switchDisabled: false, //开关的启用状态
        barCodeSwitch: false, //生成条码开关
        categorySelected: false, // 是否已选择类别
        specLocked: false, // 规格是否锁定（从类别配置加载后禁止修改规格名称及增删）
        maxBarCodeInfo: '', //最大条码
        meDeleteIdList: [], //删除条码信息的id数组
        prefixNo: 'material',
        materialAttributeList: [],
        specModules: [],
        specValueSuggestionCache: {},
        specImageOcrVisible: false,
        specImageOcrFile: null,
        pendingSpecImage: null,
        meOldDataSource: [],
        batchColumn: undefined,
        batchColumnValue: '',
        batchPurchasePrice: '',
        batchCommodityPrice: '',
        batchStock: '',
        batchLowSafeStock: '',
        batchHighSafeStock: '',
        batchMarketPrice: '',
        batchDepotId: undefined,
        batchStore: undefined,
        // 门店下拉选项：门店主数据暂未接入，先保留空选项（允许为空）
        storeOptions: [],
        priceTableFullscreen: false,
        depotList: [],
        selectedSpecFilter: -1,
        batchSpecFilters: {},
        model: {},
        showOkFlag: true,
        setTimeFlag: null,
        labelCol: {
          xs: { span: 24 },
          sm: { span: 6 },
        },
        wrapperCol: {
          xs: { span: 24 },
          sm: { span: 18 },
        },
        basePropertyList: [
          { nativeName: '名称', field: 'name', visible: true, sort: 0, anotherName: '名称', fixed: true },
          { nativeName: '单位', field: 'unit', visible: true, sort: 1, anotherName: '单位', fixed: true },
          { nativeName: '品牌', field: 'brand', visible: true, sort: 2, anotherName: '品牌', fixed: true },
          { nativeName: '助记码', field: 'mnemonic', visible: true, sort: 3, anotherName: '助记码', fixed: true },
          { nativeName: '类别', field: 'categoryId', visible: true, sort: 4, anotherName: '类别', fixed: true },
          { nativeName: '填充物重量', field: 'weight', visible: true, sort: 5, anotherName: '填充物重量', fixed: true },
          { nativeName: '制造商', field: 'mfrs', visible: true, sort: 6, anotherName: '制造商', fixed: true },
          { nativeName: '序列号', field: 'enableSerialNumber', visible: false, sort: 7, anotherName: '序列号', fixed: true },
          { nativeName: '条码', field: 'barCode', visible: true, sort: 8, anotherName: '条码', fixed: true },
          { nativeName: '商品ID', field: 'id', visible: false, sort: 9, anotherName: '商品ID', fixed: true },
          { nativeName: '面料', field: 'fabric', visible: true, sort: 10, anotherName: '面料', fixed: true },
          { nativeName: '安全类别', field: 'safetyCategory', visible: true, sort: 11, anotherName: '安全类别', fixed: true },
          { nativeName: '执行标准', field: 'executiveStandard', visible: true, sort: 12, anotherName: '执行标准', fixed: true },
          { nativeName: '等级', field: 'grade', visible: true, sort: 13, anotherName: '等级', fixed: true },
          { nativeName: '检验', field: 'inspection', visible: true, sort: 14, anotherName: '检验', fixed: true },
          { nativeName: '填充物', field: 'filler', visible: true, sort: 15, anotherName: '填充物', fixed: true },
          { nativeName: '扩展1', field: 'otherField1', visible: true, sort: 16, anotherName: '扩展1', fixed: false },
          { nativeName: '扩展2', field: 'otherField2', visible: true, sort: 17, anotherName: '扩展2', fixed: false },
          { nativeName: '扩展3', field: 'otherField3', visible: true, sort: 18, anotherName: '扩展3', fixed: false },
          { nativeName: '扩展4', field: 'otherField4', visible: true, sort: 19, anotherName: '扩展4', fixed: false },
          { nativeName: '扩展5', field: 'otherField5', visible: true, sort: 20, anotherName: '扩展5', fixed: false },
          { nativeName: '扩展6', field: 'otherField6', visible: true, sort: 21, anotherName: '扩展6', fixed: false },
          { nativeName: '扩展7', field: 'otherField7', visible: true, sort: 22, anotherName: '扩展7', fixed: false }
        ],
        propertyConfigVisible: false,
        // 商品基础信息属性的 field 列表（用于属性设置弹窗，仅这些可勾选/排序）
        basePropertyFields: ['name','unit','brand','mnemonic','categoryId','weight','mfrs','barCode','id',
          'fabric','safetyCategory','executiveStandard','grade','inspection','filler',
          'otherField1','otherField2','otherField3','otherField4','otherField5','otherField6','otherField7'],
        // 属性设置：每行列数（默认4列）
        propertyConfigCols: 4,
        // 属性设置：勾选的基础属性字段数组（按勾选顺序）
        propertyConfigSelected: [],
        // 扩展字段别名配置项（按扩展1~7 顺序）
        propertyConfigAliasList: {
          otherField1: { label: '扩展1' },
          otherField2: { label: '扩展2' },
          otherField3: { label: '扩展3' },
          otherField4: { label: '扩展4' },
          otherField5: { label: '扩展5' },
          otherField6: { label: '扩展6' },
          otherField7: { label: '扩展7' }
        },
        // 属性设置：扩展字段别名
        propertyConfigAlias: { otherField1: '扩展1', otherField2: '扩展2', otherField3: '扩展3', otherField4: '扩展4', otherField5: '扩展5', otherField6: '扩展6', otherField7: '扩展7' },
        meTable: {
          loading: false,
          dataSource: [],
          columns: [
            {
              title: '进货价', key: 'purchaseDecimal', width: '15%', type: FormTypes.inputNumber, defaultValue: '', placeholder: '请输入${title}'
            },
            {
              title: '零售价', key: 'commodityDecimal', width: '15%', type: FormTypes.inputNumber, defaultValue: '', placeholder: '请输入${title}'
            },
            {
              title: '划线价', key: 'marketPrice', width: '15%', type: FormTypes.inputNumber, defaultValue: '', placeholder: '请输入${title}'
            },
            {
              title: '期初库存', key: 'stock', width: '15%', type: FormTypes.inputNumber, defaultValue: '', placeholder: '请输入${title}'
            },
            {
              title: '最低库存预警', key: 'lowSafeStock', width: '12%', type: FormTypes.inputNumber, defaultValue: '', placeholder: '低于该值预警'
            },
            {
              title: '最高库存预警', key: 'highSafeStock', width: '12%', type: FormTypes.inputNumber, defaultValue: '', placeholder: '高于该值预警'
            },
            {
              title: '仓库', key: 'depotId', width: '20%', type: FormTypes.sel_search, defaultValue: '', placeholder: '请选择${title}',
              options: []
            },
            {
              title: '门店', key: 'store', width: '20%', type: FormTypes.input, defaultValue: '', placeholder: '请输入${title}'
            }
          ]
        },
        confirmLoading: false,
        ocrVisible: false,
        // SKU 规格列结构签名，用于避免输入规格值时反复重建列（null 表示需强制重建）
        skuColumnSignature: null,
        // 编辑回显规格时暂停规格变化监听
        suppressSpecSync: false,
        form: this.$form.createForm(this),
        validatorRules:{
          name:{
            rules: [
              { required: true, message: '请输入名称!' },
              { max: 100, message: '长度请小于100个字符', trigger: 'blur' }
            ]
          },
          unit:{
            rules: [
              { required: true, message: '请输入单位!' }
            ]
          },
          categoryId:{
            rules: [
              { required: true, message: '请选择类别（须选二级类目）!' }
            ]
          }
        },
        url: {
          add: '/material/add',
          edit: '/material/update',
          materialsExtendList: '/materialsExtend/getDetailList'
        }
      }
    },
    computed: {
      // 弹窗模式下透传给 JModal 的属性集合
      modalProps() {
        return {
          title: this.title,
          width: this.width,
          visible: this.visible,
          confirmLoading: this.confirmLoading,
          prefixNo: this.prefixNo,
          switchHelp: true,
          switchFullscreen: true,
          id: this.prefixNo
        }
      },
      // 属性设置弹窗中可选的列：仅商品基础信息属性
      propertyConfigBaseColumns() {
        return this.basePropertyList.filter(p => this.basePropertyFields.includes(p.field))
      },
      // 类别树：一级节点禁用，仅可选二级类目
      categoryTreeFiltered() {
        if (!this.categoryTree || this.categoryTree.length === 0) return []
        return this.disableLevelOneNodes(this.categoryTree)
      },
      // 表单每列的 span（24 / 每行列数）
      formColSpan() {
        const cols = this.propertyConfigCols || 4
        return Math.floor(24 / cols)
      },
      basePropertyRows() {
        // 按 propertyConfigSelected 顺序构建可见属性列表
        let visible = []
        if (this.propertyConfigSelected && this.propertyConfigSelected.length > 0) {
          for (let i = 0; i < this.propertyConfigSelected.length; i++) {
            const prop = this.basePropertyList.find(p => p.field === this.propertyConfigSelected[i])
            if (prop && prop.visible) {
              visible.push(prop)
            }
          }
        } else {
          // 兜底：按 sort 顺序
          visible = this.basePropertyList.filter(p => p.visible && this.basePropertyFields.includes(p.field))
          visible.sort((a, b) => a.sort - b.sort)
        }
        const cols = this.propertyConfigCols || 4
        const rows = []
        for (let i = 0; i < visible.length; i += cols) {
          rows.push(visible.slice(i, i + cols))
        }
        return rows
      },
      skuTableBodyHeight() {
        // JEditableTable 的行高约为54px，按SKU数量展开表格。
        return Math.max((this.meTable.dataSource || []).length * 54, 54)
      },
      priceFillProgress() {
        let rows = this.meTable.dataSource
        if (!rows || rows.length === 0) return 0
        let filled = 0
        for (let i = 0; i < rows.length; i++) {
          if (rows[i].wholesaleDecimal) filled++
        }
        return Math.round(filled / rows.length * 100)
      },
      selectedFilterCount() {
        let total = 0
        let keys = Object.keys(this.batchSpecFilters)
        for (let i = 0; i < keys.length; i++) {
          total += (this.batchSpecFilters[keys[i]] || []).length
        }
        return total
      },
      selectedFilterTagList() {
        let tags = []
        let that = this
        let keys = Object.keys(this.batchSpecFilters)
        for (let i = 0; i < keys.length; i++) {
          let specName = keys[i]
          let values = this.batchSpecFilters[specName] || []
          for (let j = 0; j < values.length; j++) {
            tags.push({ specName: specName, value: values[j], label: values[j] })
          }
        }
        return tags
      },
      depotOptions() {
        let opts = []
        for (let i = 0; i < this.depotList.length; i++) {
          let d = this.depotList[i]
          opts.push({ value: String(d.id), label: d.name })
        }
        return opts
      },
      // 批量设置行：期初库存大于 0 时，仓库/门店才可选择
      batchStockPositive() {
        if (this.batchStock === '' || this.batchStock === null || this.batchStock === undefined) return false
        let num = Number(this.batchStock)
        return !isNaN(num) && num > 0
      }
    },
    watch: {
      // 监听 specModules 深度变化（规格名称、规格值文本、图片、启用状态）
      // 不依赖 a-input 的 @blur 事件（在某些场景下不触发），保证表格实时刷新
      specModules: {
        deep: true,
        handler: function () {
          // 编辑回显阶段由 restoreSpecModulesFromSkuRows 自行同步列，避免重复重建行
          if (this.suppressSpecSync) return
          this.onSpecChange()
        }
      }
    },
    created () {
      this.loadBasePropertyConfig()
      this.loadDepotOptions()
      let realScreenWidth = window.screen.width
      this.width = realScreenWidth<1500?'1200px':'1400px'
    },
    mounted() {
      document.getElementById(this.prefixNo).addEventListener('keydown', this.handleOkKey)
      window.addEventListener('resize', this.onWindowResize)
    },
    beforeDestroy() {
      document.getElementById(this.prefixNo).removeEventListener('keydown', this.handleOkKey)
      window.removeEventListener('resize', this.onWindowResize)
    },
    methods: {
      // 手机端适配：窗口宽度变化时切换分页模式并回到首页
      onWindowResize() {
        const isMobile = window.innerWidth <= 768
        if (isMobile === this.isMobileView) return
        this.isMobileView = isMobile
        this.mobilePageIndex = 0
        this.$nextTick(() => {
          if (this.$refs.mmPages) this.$refs.mmPages.scrollLeft = 0
        })
      },
      // 横向滑动后同步当前页码
      onPagesScroll() {
        const el = this.$refs.mmPages
        if (!el || !this.isMobileView) return
        const width = el.clientWidth || 1
        const index = Math.round(el.scrollLeft / width)
        if (index !== this.mobilePageIndex) this.mobilePageIndex = index
      },
      // 手机端：点击顶部标题切换到对应页
      goMobilePage(index) {
        const el = this.$refs.mmPages
        if (!el) return
        const left = (el.clientWidth || 1) * index
        if (el.scrollTo) {
          el.scrollTo({ left: left, behavior: 'smooth' })
        } else {
          el.scrollLeft = left
        }
        this.mobilePageIndex = index
      },
      // 手机端卡片：规格值组合文本
      getSkuCardSpecText(row) {
        const arr = []
        for (let i = 0; i < this.specModules.length; i++) {
          const text = row['skuField' + (i + 1)]
          if (text) arr.push(text)
        }
        return arr.join(' / ')
      },
      // 手机端卡片：输入提示
      colPlaceholder(col) {
        return (col.placeholder || '').replace('${title}', col.title || '')
      },
      // 快捷键
      handleOkKey(e) {
        const key = window.event.keyCode ? window.event.keyCode : window.event.which
        if (key === 83 && e.ctrlKey) {
          //保存 CTRL+S
          this.handleOk()
          e.preventDefault()
        }
      },
      // 获取所有的editableTable实例
      getAllTable() {
        return Promise.all([
          getRefPromise(this, 'editableMeTable')
        ])
      },
      add () {
        // 默认新增一条数据
        this.getAllTable().then(editableTables => {
          editableTables[0].add()
        })
        this.edit({})
        this.categorySelected = false
        this.suppressSpecSync = true
        this.specModules = []
        // 新增时清空上一次编辑残留的规格列
        this.updateSkuColumns()
        this.suppressSpecSync = false
        this.$nextTick(() => {
          handleIntroJs('material', 11)
        })
      },
      edit (record) {
        let that = this
        this.form.resetFields();
        this.model = Object.assign({}, record);
        // 初始化 specModules
        this.specModules = []
        this.specValueSuggestionCache = {}
        let attribute = record.attribute
        if(attribute) {
          let attrObj = JSON.parse(attribute)
          if(attrObj.specModules && Array.isArray(attrObj.specModules)) {
            // 新格式：specModules
            this.specModules = attrObj.specModules.map(function(m) {
              return {
                name: m.name || '',
                enableImage: !!m.enableImage,
                childNames: Array.isArray(m.childNames) ? m.childNames.slice() : [],
                values: (m.values || []).map(function(v) {
                  return { text: v.text || '', image: v.image || '', subValues: Object.assign({}, v.subValues || {}) }
                })
              }
            })
          } else if(attrObj.manySku) {
            // 旧格式：manySku/skuOne/skuTwo/skuThree（兼容转换）
            this.migrateOldAttributeToSpecModules(attrObj)
          }
        }
        // 编辑时：有类别则标记已选择类别，有规格则锁定
        this.categorySelected = !!(record.categoryId)
        this.specLocked = !!(this.specModules.length > 0 && this.specModules[0].name)
        this.activeKey = '1'
        this.barCodeSwitch = false
        this.maxBarCodeInfo = ''
        this.visible = true
        this.meDeleteIdList = []
        this.modalStyle = 'top: 24px;'
        if(JSON.stringify(record) === '{}') {
          this.fileList = []
        } else {
          if(this.action === 'edit') {
            setTimeout(() => {
              // 图片从 SKU 表的 imgUrl 加载，不再从商品信息表读取
              this.fileList = this.meOldDataSource && this.meOldDataSource.length > 0 ? (this.meOldDataSource[0].imgUrl || this.meOldDataSource[0].image || '') : ''
            }, 5)
          }
        }
        this.$nextTick(() => {
          this.form.setFieldsValue(pick(this.model, 'name', 'unit', 'brand', 'mnemonic',
            'categoryId','enableSerialNumber','barCode','weight','remark','mfrs',
            'otherField1','otherField2','otherField3','otherField4','otherField5','otherField6','otherField7',
            'fabric','safetyCategory','executiveStandard','grade','inspection','filler'))
          autoJumpNextInput('materialHeadModal')
          autoJumpNextInput('materialDetailModal')
          // 从列表页"添加规格"按钮触发：自动添加一个规格模块
          if (this.autoAddSpec) {
            this.autoAddSpec = false
            this.addSpecModule()
          }
        });
        this.initMaterialAttribute()
        this.loadTreeData()
        // 加载子表数据
        if (this.model.id) {
          this.switchDisabled = true
          let params = { materialId: this.model.id }
          this.requestMeTableData(this.url.materialsExtendList, params, this.meTable)
          // 编辑时如果有 specModules，延迟设置表格列
          if(this.specModules.length > 0) {
            setTimeout(function() {
              that.updateSkuColumns()
            }, 1200)
          }
        } else {
          this.switchDisabled = false
        }
      },
      /** 查询条码tab的数据 */
      requestMeTableData(url, params, tab) {
        tab.loading = true
        getAction(url, params).then(res => {
          for (let i = 0; i < res.data.rows.length; i++) {
            let row = res.data.rows[i]
            // 规格列与规格输入框绑定：从 specValues 还原“颜色/规格”组合文本与各规格列
            let specArr = this.parseSpecValuesJson(row.specValues)
            for (let k = 0; k < specArr.length; k++) {
              row['skuField' + (k + 1)] = specArr[k].valueText || ''
            }
            row.sku = specArr.length > 0 ? specArr.map(function(v) { return v.valueText }).join('/') : ''
          }
          tab.dataSource = res.data.rows || []
          this.meOldDataSource = res.data.rows || []
          // 编辑时按已有 SKU 还原规格模块，保证规格卡片与 SKU 表格列一一对应
          if (tab === this.meTable && this.specModules.length === 0) {
            this.restoreSpecModulesFromSkuRows(res.data.rows)
          }
          // 将后端 imgUrl 映射到前端 image 字段
          for (let i = 0; i < this.meOldDataSource.length; i++) {
            if (this.meOldDataSource[i].imgUrl && !this.meOldDataSource[i].image) {
              this.meOldDataSource[i].image = this.meOldDataSource[i].imgUrl
            }
          }
          // 如果任一规格模块启用了图片，从规格值填充组合行图片
          if(this.specModules.some(module => module.enableImage)) {
            this.updateRowImagesBySpec()
          }
          //复制新增商品-清除子表ID，保留规格和价格数据
          if(this.action === 'copyAdd') {
            let meTableData = []
            for (let i = 0; i < tab.dataSource.length; i++) {
              let meInfo = tab.dataSource[i]
              meInfo.id = ''  // 清除ID，作为新增
              meTableData.push(meInfo)
            }
            tab.dataSource = meTableData
          }
        }).finally(() => {
          tab.loading = false
        })
      },
      batchSetColumn() {
        let allRows = this.meTable.dataSource
        if (!allRows || allRows.length === 0) {
          this.$message.warning('暂无数据可设置')
          return
        }
        let rows = this.filterRowsBySpec(allRows)
        let hasValue = false
        for (let i = 0; i < rows.length; i++) {
          if (this.batchPurchasePrice !== '') {
            rows[i].purchaseDecimal = this.batchPurchasePrice
            hasValue = true
          }
          if (this.batchCommodityPrice !== '') {
            rows[i].commodityDecimal = this.batchCommodityPrice
            hasValue = true
          }
          if (this.batchMarketPrice !== '') {
            rows[i].marketPrice = this.batchMarketPrice
            hasValue = true
          }
          if (this.batchStock !== '') {
            rows[i].stock = this.batchStock
            hasValue = true
          }
          if (this.batchLowSafeStock !== '') {
            rows[i].lowSafeStock = this.batchLowSafeStock
            hasValue = true
          }
          if (this.batchHighSafeStock !== '') {
            rows[i].highSafeStock = this.batchHighSafeStock
            hasValue = true
          }
          if (this.batchDepotId !== undefined && this.batchDepotId !== null && this.batchDepotId !== '') {
            rows[i].depotId = this.batchDepotId
            hasValue = true
          }
          if (this.batchStore !== undefined && this.batchStore !== null && this.batchStore !== '') {
            rows[i].store = this.batchStore
            hasValue = true
          }
        }
        for (let i = 0; i < rows.length; i++) {
          if (this.batchColumn) {
            rows[i][this.batchColumn] = this.batchColumnValue || ''
            hasValue = true
          }
        }
        if (!hasValue) {
          this.$message.warning('请输入要批量设置的值')
          return
        }
        this.meTable.dataSource = [...allRows]
        if (hasValue) this.$message.success('批量设置成功，共设置 ' + rows.length + ' 行')
      },
      togglePriceTableFullscreen() {
        this.priceTableFullscreen = !this.priceTableFullscreen
      },
      filterRowsBySpec(rows) {
        let filters = this.batchSpecFilters
        let hasFilter = false
        let keys = Object.keys(filters)
        for (let i = 0; i < keys.length; i++) {
          if (filters[keys[i]] && filters[keys[i]].length > 0) {
            hasFilter = true
            break
          }
        }
        if (!hasFilter) return rows
        let that = this
        return rows.filter(function(row) {
          for (let i = 0; i < that.specModules.length; i++) {
            let module = that.specModules[i]
            let selectedValues = filters[module.name]
            if (selectedValues && selectedValues.length > 0) {
              let rowValue = row['skuField' + (i + 1)]
              if (!selectedValues.includes(rowValue)) {
                return false
              }
            }
          }
          return true
        })
      },
      isSpecValueSelected(specName, value) {
        let arr = this.batchSpecFilters[specName]
        return arr && arr.includes(value)
      },
      removeFilterTag(item) {
        let arr = this.batchSpecFilters[item.specName] ? this.batchSpecFilters[item.specName].slice() : []
        arr = arr.filter(function(v) { return v !== item.value })
        this.$set(this.batchSpecFilters, item.specName, arr)
      },
      isSpecAllSelected(module) {
        if (!module.values || module.values.length === 0) return false
        let arr = this.batchSpecFilters[module.name] || []
        return arr.length === module.values.length
      },
      isSpecIndeterminate(module) {
        if (!module.values || module.values.length === 0) return false
        let arr = this.batchSpecFilters[module.name] || []
        return arr.length > 0 && arr.length < module.values.length
      },
      toggleSpecValue(specName, value, e) {
        let checked = e.target.checked
        let arr = this.batchSpecFilters[specName] ? this.batchSpecFilters[specName].slice() : []
        if (checked) {
          if (!arr.includes(value)) arr.push(value)
        } else {
          arr = arr.filter(function(v) { return v !== value })
        }
        this.$set(this.batchSpecFilters, specName, arr)
      },
      toggleAllSpecValues(module, e) {
        let checked = e.target.checked
        if (checked) {
          let allValues = module.values.map(function(v) { return v.text }).filter(function(t) { return t })
          this.$set(this.batchSpecFilters, module.name, allValues)
        } else {
          this.$set(this.batchSpecFilters, module.name, [])
        }
      },
      clearSpecFilters() {
        this.batchSpecFilters = {}
      },
      selectAllSpecValues() {
        for (let i = 0; i < this.specModules.length; i++) {
          let module = this.specModules[i]
          let allValues = module.values.map(function(v) { return v.text }).filter(function(t) { return t })
          this.$set(this.batchSpecFilters, module.name, allValues)
        }
      },
      batchSetPrice(type) {
        if(this.specModules.length > 0 || this.model.id){
          this.$refs.priceModalForm.add(type);
          this.$refs.priceModalForm.disableSubmit = false;
        } else {
          this.$message.warning('抱歉，只有添加规格后才能进行批量操作！');
        }
      },
      close () {
        this.$emit('close')
        this.visible = false
        this.modalStyle = ''
        this.specModules = []
        this.skuColumnSignature = null
        this.specValueSuggestionCache = {}
        this.getAllTable().then(editableTables => {
          editableTables[0].initialize()
        })
      },
      handleOk () {
        this.validateFields()
      },
      handleCancel () {
        this.close()
      },
      /** 触发表单验证 */
      validateFields() {
        this.getAllTable().then(tables => {
          /** 一次性验证主表和所有的次表 */
          return validateFormAndTables(this.form, tables)
        }).then(allValues => {
          let formData = this.classifyIntoFormData(allValues)
          formData.sortList = [];
          if(formData.unit === undefined) {formData.unit = ''}
          // 发起请求
          return this.requestAddOrEdit(formData)
        }).catch(e => {
          if (e && e.error === VALIDATE_NO_PASSED) {
            // 如果有未通过表单验证的子表，就自动跳转到它所在的tab
            this.activeKey = e.index == null ? this.activeKey : (e.index + 1).toString()
            this.$message.warning('请完善必填信息后再保存！')
          } else {
            console.error(e)
            this.$message.error('保存失败：' + ((e && (e.message || e.msg || e.data)) || '请稍后重试'))
          }
        })
      },
      /** 整理成formData */
      classifyIntoFormData(allValues) {
        let materialMain = Object.assign(this.model, allValues.formValue)
        // 序列化 specModules 到 attribute 字段
        let cleanSpecModules = this.specModules.map(function(m) {
          return {
            name: m.name,
            enableImage: m.enableImage,
            childNames: (m.childNames || []).slice(),
            values: m.values.map(function(v) { return { text: v.text, image: v.image || '', subValues: Object.assign({}, v.subValues || {}) } })
          }
        })
        materialMain.attribute = JSON.stringify({ specModules: cleanSpecModules })
        // 同时发送 specModules 供后端 parseAttributeBySku 使用
        materialMain.specModules = cleanSpecModules
        // 发送 manySku 标志触发 saveDetials 旧条码清理逻辑
        if (cleanSpecModules.length > 0) {
          materialMain.manySku = cleanSpecModules.map(function(m) { return m.name })
        }
        let meList = allValues.tablesValue[0].values || []
        // 隐藏列已去掉，按规格列内容重建 specValues 与 sku 组合文本，确保提交的规格数据与表格一致
        for (let i = 0; i < meList.length; i++) {
          let specArr = this.buildSpecValuesFromRow(meList[i])
          if (specArr.length > 0) {
            meList[i].specValues = JSON.stringify(specArr)
            meList[i].sku = specArr.map(function(v) { return v.valueText }).join('/')
          }
          // 兜底：规格列缺失时从 dataSource 取
          let dsRow = this.meTable.dataSource[i]
          if (dsRow) {
            if (!meList[i].specValues && dsRow.specValues) {
              meList[i].specValues = dsRow.specValues
            }
            if (!meList[i].sku && dsRow.sku) {
              meList[i].sku = dsRow.sku
            }
            if (dsRow.image && !meList[i].image) {
              meList[i].image = dsRow.image
            }
            // 兜底：预警阈值/仓库等字段若表格值丢失则从 dataSource 补齐
            if ((meList[i].lowSafeStock === undefined || meList[i].lowSafeStock === null || meList[i].lowSafeStock === '') && dsRow.lowSafeStock !== undefined) {
              meList[i].lowSafeStock = dsRow.lowSafeStock
            }
            if ((meList[i].highSafeStock === undefined || meList[i].highSafeStock === null || meList[i].highSafeStock === '') && dsRow.highSafeStock !== undefined) {
              meList[i].highSafeStock = dsRow.highSafeStock
            }
            if ((meList[i].depotId === undefined || meList[i].depotId === null || meList[i].depotId === '') && dsRow.depotId !== undefined) {
              meList[i].depotId = dsRow.depotId
            }
          }
        }
        // 库存和仓库保留在各自 SKU 明细中，由后端按 SKU 码写入库存表，避免不同 SKU 汇总丢失关联。
        return {
          ...materialMain,
          meList: meList
        }
      },
      /** 发起新增或修改的请求 */
      requestAddOrEdit(formData) {
        //复制新增商品-初始化id和租户id
        if(this.action === 'copyAdd') {
          this.model.id = ''
          this.model.tenantId = ''
          formData.id = ''
          formData.tenantId = ''
        }
        if(formData.meList.length === 0) {
          this.$message.warning('抱歉，请添加商品规格信息！');
          return;
        }
        //校验商品是否存在，通过校验商品的名称、型号、规格、颜色、单位、制造商等
        let param = {
          id: this.model.id?this.model.id:0,
          name: this.model.name,
          model: this.parseParam(this.model.model),
          color: this.parseParam(this.model.color),
          standard: this.parseParam(this.model.standard),
          mfrs: this.parseParam(this.model.mfrs),
          otherField1: this.parseParam(this.model.otherField1),
          otherField2: this.parseParam(this.model.otherField2),
          otherField3: this.parseParam(this.model.otherField3),
          otherField4: this.parseParam(this.model.otherField4),
          otherField5: this.parseParam(this.model.otherField5),
          otherField6: this.parseParam(this.model.otherField6),
          otherField7: this.parseParam(this.model.otherField7),
          unit: this.parseParam(this.model.unit)
        }
        // 编辑时带上单位ID，新增时单位是名称文本，不传 unitId（后端已改为可选）
        if (this.model.unitId) {
          param.unitId = this.model.unitId
        }
        checkMaterial(param).then((res)=>{
          if(res && res.code===200) {
            if(res.data.status){
              this.$message.warning('抱歉，该商品已存在！');
              return;
            } else {
              //进一步校验
              if(!formData.unit) {
                let tempMsg = []
                if(formData.meList.length >=2){
                  tempMsg.push('单单位的商品条码行数不能超过一行！')
                }
                if(tempMsg.length>0){
                  this.$message.warning(tempMsg.join('<br>'));
                  return;
                }
              }
              let skuCount = 0
              for(let i=0; i<formData.meList.length; i++) {
                if(formData.meList[i].sku) {
                  skuCount++
                }
              }
              //图片校验
              if(this.fileList && this.fileList.length > 0) {
                let fileArr = this.fileList.split(',')
                if(fileArr.length > 4) {
                  this.$message.warning('抱歉，商品图片不能超过4张！');
                  return
                }
              }
              formData.meDeleteIdList = this.meDeleteIdList
              //接口调用
              let url = this.url.add, method = 'post'
              if (this.model.id) {
                url = this.url.edit
                method = 'put'
              }
              const that = this;
              this.confirmLoading = true
              httpAction(url, formData, method).then((res) => {
                if(res.code === 200){
                  that.$message.success('保存成功');
                  that.$emit('ok');
                  that.confirmLoading = false
                  that.close();
                }else{
                  // res.data 可能是字符串或对象，兼容多种后端错误返回结构
                  let errMsg = '保存失败'
                  if (typeof res.data === 'string') {
                    errMsg = res.data
                  } else if (res.data && res.data.message) {
                    errMsg = res.data.message
                  } else if (res.msg) {
                    errMsg = res.msg
                  } else if (res.message) {
                    errMsg = res.message
                  }
                  that.$message.warning(errMsg);
                  that.confirmLoading = false
                }
              }).catch((err) => {
                // 网络错误、会话过期等（axios 拦截器已弹提示）
                that.confirmLoading = false
                console.error('保存商品失败:', err)
              }).finally(() => {
                // 兜底重置，避免 confirmLoading 卡住
                that.confirmLoading = false
              })
            }
          } else {
            // 商品重复校验接口返回异常时给出明确提示，避免点击保存无任何反应
            let msg = '商品校验失败'
            if (typeof res.data === 'string') {
              msg = res.data
            } else if (res.data && res.data.message) {
              msg = res.data.message
            } else if (res.msg) {
              msg = res.msg
            }
            this.$message.error(msg + '，请重试')
          }
        }).catch((err) => {
          console.error('商品重复校验请求失败:', err)
          this.$message.error('商品校验失败，请检查网络或重新登录后重试')
        })
      },
      parseParam(param) {
        return param ? param: ""
      },
      validateBarCode(type, value, row, column, callback, target) {
        let params = {
          barCode: value,
          id: row.id.length >= 20?0: row.id
        };
        checkMaterialBarCode(params).then((res)=>{
          if(res && res.code===200) {
            if(!res.data.status){
              callback(true);
            } else {
              callback(false, '该条码已经存在');
            }
          } else {
            callback(false, res.data);
          }
        });
      },
      loadTreeData(){
        let that = this;
        let params = {};
        params.id='';
        queryMaterialCategoryTreeList(params).then((res)=>{
          if(res){
            that.categoryTree = [];
            for (let i = 0; i < res.length; i++) {
              let temp = res[i];
              that.categoryTree.push(temp);
            }
          }
        })
      },
      // 递归禁用一级类目节点
      disableLevelOneNodes(nodes) {
        const result = []
        for (let i = 0; i < nodes.length; i++) {
          const node = Object.assign({}, nodes[i])
          if (node.categoryLevel === 1) {
            node.disabled = true
          }
          if (node.children && node.children.length > 0) {
            node.children = this.disableLevelOneNodes(node.children)
          }
          result.push(node)
        }
        return result
      },
      // 类别变更：自动加载父级类目规格配置
      handleCategoryChange(value) {
        if (!value) {
          // 清除类别，重置规格
          this.categorySelected = false
          this.specLocked = false
          this.specModules = []
          this.specValueSuggestionCache = {}
          return
        }
        // 在 categoryTree 中查找选中节点
        const selectedNode = this.findNodeById(this.categoryTree, value)
        if (!selectedNode) return
        if (selectedNode.specConfig) {
          let categorySpecs = []
          try {
            categorySpecs = JSON.parse(selectedNode.specConfig)
          } catch (e) {
            categorySpecs = []
          }
          if (categorySpecs.length > 0) {
            // Load category-defined cards, preserving configured values and images.
            this.specModules = categorySpecs.map(function(item) {
              if (typeof item === 'string') {
                return { name: item, enableImage: false, childNames: [], values: [{ text: '', image: '', subValues: {} }] }
              }
              return {
                name: item.name || '',
                enableImage: !!item.enableImage,
                childNames: Array.isArray(item.childNames) ? item.childNames.slice() : [],
                values: [{ text: '', image: '', subValues: {} }]
              }
            })
            this.categorySelected = true
            this.specLocked = true
            this.onSpecChange()
            this.$nextTick(() => this.loadAllCategorySpecSuggestions())
            return
          }
        }
        // 类别未配置规格时保留手动添加规格的方式。
        this.specModules = [{ name: '', enableImage: false, childNames: [], values: [{ text: '', image: '', subValues: {} }] }]
        this.categorySelected = true
        this.specLocked = false
        this.onSpecChange()
        this.$nextTick(() => this.loadAllCategorySpecSuggestions())
      },
      // 递归查找节点
      findNodeById(nodes, id) {
        for (let i = 0; i < nodes.length; i++) {
          if (nodes[i].id === id || nodes[i].key === id || nodes[i].value === id) {
            return nodes[i]
          }
          if (nodes[i].children && nodes[i].children.length > 0) {
            const found = this.findNodeById(nodes[i].children, id)
            if (found) return found
          }
        }
        return null
      },
      // ====== 规格模块方法 ======
      addSpecModule() {
        this.specModules.push({
          name: '',
          enableImage: false,
          childNames: [],
          values: [{ text: '', image: '', subValues: {} }]
        })
      },
      removeSpecModule(index) {
        this.specModules.splice(index, 1)
        this.onSpecChange()
      },
      addSpecValue(mIndex) {
        let module = this.specModules[mIndex]
        if (module.values.length >= 50) {
          this.$message.warning('单个规格最多50个值')
          return
        }
        module.values.push({ text: '', image: '', subValues: {} })
      },
      onSubSpecChange(mIndex, vIndex, childName, value) {
        const item = this.specModules[mIndex].values[vIndex]
        if (!item.subValues) this.$set(item, 'subValues', {})
        this.$set(item.subValues, childName, value)
        const names = this.specModules[mIndex].childNames || []
        item.text = names.map(name => (item.subValues[name] || '').trim()).join('/')
        this.onSpecChange()
      },
      removeSpecValue(mIndex, vIndex) {
        this.specModules[mIndex].values.splice(vIndex, 1)
        this.onSpecChange()
      },
      onSpecNameChange(mIndex) {
        this.updateSkuColumns()
        this.loadSpecValueSuggestions(mIndex)
      },
      onToggleSpecImage(mIndex, checked) {
        // 一个商品只允许一个规格维度配图：开启时互斥关闭其它维度
        let self = this
        this.specModules.forEach(function(m, i) {
          m.enableImage = !!checked && i === mIndex
          if (!m.enableImage) {
            m.values.forEach(function(v) { v.image = '' })
          }
        })
        self.onSpecChange()
      },
      openSpecImageExtract(mIndex, vIndex) {
        // 先打开图片文字提取框，再在提取框内加载图片
        this.pendingSpecImage = { mIndex: mIndex, vIndex: vIndex, path: '' }
        this.specImageOcrFile = null
        this.specImageOcrVisible = true
      },
      handleSpecImageOcrClose(imagePath) {
        this.specImageOcrVisible = false
        if (this.pendingSpecImage) {
          let pending = this.pendingSpecImage
          let path = imagePath || pending.path
          let module = this.specModules[pending.mIndex]
          if (path && module && module.values && module.values[pending.vIndex]) {
            this.$set(module.values[pending.vIndex], 'image', path)
            this.updateRowImagesBySpec()
          }
        }
        this.pendingSpecImage = null
        this.specImageOcrFile = null
      },
      onSpecChange() {
        this.updateSkuColumns()
        // 使用 $nextTick 确保 columns 更新导致的重新渲染完成后再生成 SKU
        this.$nextTick(() => {
          this.autoSkuList()
        })
      },
      getSpecSuggestionCacheKey(mIndex) {
        let module = this.specModules[mIndex]
        let categoryId = this.form.getFieldValue('categoryId') || this.model.categoryId
        let attrItem = module && this.materialAttributeList.find(function(a) { return a.name === module.name })
        return categoryId && attrItem ? String(categoryId) + '_' + attrItem.value : ''
      },
      loadSpecValueSuggestions(mIndex) {
        let key = this.getSpecSuggestionCacheKey(mIndex)
        if (!key || this.specValueSuggestionCache[key]) return
        let categoryId = this.form.getFieldValue('categoryId') || this.model.categoryId
        let attrItem = this.materialAttributeList.find(a => a.name === this.specModules[mIndex].name)
        if (!attrItem) return
        getMaterialCategorySpecValues({ categoryId: categoryId, attributeId: attrItem.value }).then(res => {
          if (Array.isArray(res)) this.$set(this.specValueSuggestionCache, key, res)
        })
      },
      loadAllCategorySpecSuggestions() {
        for (let i = 0; i < this.specModules.length; i++) this.loadSpecValueSuggestions(i)
      },
      getSpecValueSuggestions(mIndex) {
        let key = this.getSpecSuggestionCacheKey(mIndex)
        return key ? (this.specValueSuggestionCache[key] || []) : []
      },
      addSuggestedSpecValue(mIndex, suggestion) {
        let module = this.specModules[mIndex]
        if (!module || !suggestion || !suggestion.valueText) return
        if (module.values.some(value => value.text === suggestion.valueText)) {
          this.$message.info('该规格值已添加')
          return
        }
        let value = { text: suggestion.valueText, image: '', subValues: {} }
        if (suggestion.valueDetail) {
          try { value.subValues = JSON.parse(suggestion.valueDetail) || {} } catch (e) { value.subValues = {} }
        } else if (module.childNames && module.childNames.length) {
          let parts = suggestion.valueText.split('/')
          module.childNames.forEach((name, index) => { value.subValues[name] = parts[index] || '' })
        }
        let blankValue = module.values.find(item => !item.text)
        if (blankValue) Object.assign(blankValue, value)
        else module.values.push(value)
        this.onSpecChange()
      },
      removeSpecValueSuggestion(mIndex, suggestion) {
        if (!suggestion || !suggestion.id) return
        const key = this.getSpecSuggestionCacheKey(mIndex)
        deleteMaterialCategorySpecValue({ id: suggestion.id }).then(res => {
          if (res && res.code === 200) {
            const current = this.specValueSuggestionCache[key] || []
            const canonical = this.normalizeSpecValueKey(suggestion.valueText)
            this.$set(this.specValueSuggestionCache, key, current.filter(item =>
              this.normalizeSpecValueKey(item.valueText) !== canonical
            ))
            this.$message.success('已删除常用规格值卡片')
          } else {
            this.$message.error((res && (res.msg || res.message)) || '删除卡片失败')
          }
        }).catch(() => {
          this.$message.error('删除卡片失败，请稍后重试')
        })
      },
      normalizeSpecValueKey(value) {
        const text = String(value || '').normalize('NFKC').trim()
        const compact = text.replace(/\s+/g, '')
        const numericParts = compact.split(/[xX×*]/)
        if (!numericParts.length || numericParts.some(part => !/^\d+(?:\.\d+)?$/.test(part))) return text
        return numericParts.map(part => {
          const pieces = part.split('.')
          const integer = pieces[0].replace(/^0+(?=\d)/, '') || '0'
          const fraction = (pieces[1] || '').replace(/0+$/, '')
          return fraction ? integer + '.' + fraction : integer
        }).join('x')
      },
      getFileAccessHttpUrl(path) {
        if (!path) return ''
        if (path.indexOf('http') === 0) return path
        return window._CONFIG['domianURL'] + '/systemConfig/static/' + path
      },
      // 笛卡尔积工具方法
      cartesianProduct(arrays) {
        if (!arrays || arrays.length === 0) return [[]]
        let result = [[]]
        for (let i = 0; i < arrays.length; i++) {
          let current = arrays[i]
          let temp = []
          for (let j = 0; j < result.length; j++) {
            for (let k = 0; k < current.length; k++) {
              temp.push(result[j].concat([current[k]]))
            }
          }
          result = temp
        }
        return result
      },
      // 按启用图片的规格值更新组合行图片，优先采用靠前规格的图片
      updateRowImagesBySpec() {
        let rows = this.meTable.dataSource
        for (let i = 0; i < rows.length; i++) {
          for (let m = 0; m < this.specModules.length; m++) {
            let module = this.specModules[m]
            if (!module.enableImage) continue
            let rowValue = rows[i]['skuField' + (m + 1)]
            let matched = module.values.find(function(v) { return v.text === rowValue && v.image })
            if (matched) {
              rows[i].image = matched.image
              break
            }
          }
        }
        this.meTable.dataSource = [...rows]
      },
      // 根据规格模块动态添加/移除表格列：列名与规格输入框的规格名称一一绑定
      updateSkuColumns() {
        // 固定列的宽度权重，用于按规格列数量等比例压缩
        const fixedWeights = {
          purchaseDecimal: 15, commodityDecimal: 15, marketPrice: 15,
          stock: 15, lowSafeStock: 12, highSafeStock: 12, depotId: 20, store: 20
        }
        const specCount = this.specModules.length
        // 每个规格列占 10%，剩余宽度按权重分给固定列（固定列至少保留 60%，超出则横向滚动）
        const fixedTotal = Math.max(100 - 10 * specCount, 60)
        let cols = this.meTable.columns.filter(function(col) {
          return col.key.indexOf('skuField') !== 0
        })
        let weightSum = 0
        cols.forEach(function(col) {
          weightSum += (fixedWeights[col.key] || 0)
        })
        // 固定列宽度按权重重新分配，保证表头与数据列总宽度一致
        cols = cols.map(function(col) {
          let weight = fixedWeights[col.key]
          if (!weight || !weightSum) return col
          return Object.assign({}, col, { width: (fixedTotal * weight / weightSum).toFixed(2) + '%' })
        })
        let newSkuCols = []
        for (let i = 0; i < this.specModules.length; i++) {
          let module = this.specModules[i]
          // 只要存在规格输入框就生成对应列，列内容由规格值回填
          newSkuCols.push({
            title: module.name || ('规格' + (i + 1)),
            key: 'skuField' + (i + 1),
            width: '10%',
            type: FormTypes.input,
            defaultValue: '',
            placeholder: '请输入${title}',
            readonly: true
          })
        }
        // 仅在规格结构（数量/名称）或列宽变化时重建列，避免输入规格值过程中反复重置表格
        let signature = newSkuCols.map(function(col) { return col.key + ':' + col.title }).join('|')
          + '#' + cols.map(function(col) { return col.key + ':' + col.width }).join('|')
        if (this.skuColumnSignature === signature) return
        this.skuColumnSignature = signature
        // 规格列在最前，其后为固定列，表头与数据列使用同一份列定义
        this.meTable.columns = newSkuCols.concat(cols)
      },
      // 按表格中可见的规格列内容重建规格值数组（规格列 key 为 skuField1..N）
      buildSpecValuesFromRow(row) {
        let arr = []
        for (let i = 0; i < this.specModules.length; i++) {
          let module = this.specModules[i]
          let text = row['skuField' + (i + 1)]
          if (!text) continue
          let matched = null
          for (let j = 0; j < (module.values || []).length; j++) {
            if (module.values[j].text === text) { matched = module.values[j]; break }
          }
          arr.push({
            attributeName: module.name || ('规格' + (i + 1)),
            valueText: text,
            valueCode: (matched && matched.valueCode) || '',
            // 同一规格值在本商品内共用一张图：图片随规格值写入，不随 SKU 行变化
            image: (module.enableImage && matched && matched.image) || '',
            childNames: (module.childNames || []).slice(),
            subValues: matched && matched.subValues ? Object.assign({}, matched.subValues) : {}
          })
        }
        return arr
      },
      // 生成 SKU 组合（笛卡尔积）：把规格输入框的内容回填到与规格同名的表格列
      autoSkuList() {
        let activeModules = []
        for (let i = 0; i < this.specModules.length; i++) {
          let m = this.specModules[i]
          if (m.values && m.values.length > 0) {
            // 过滤掉空文本的规格值，只保留有内容的值
            let nonEmptyValues = m.values.filter(function(v) { return v.text && v.text.trim() })
            if (nonEmptyValues.length > 0) {
              // index 记录规格模块的原始顺序，保证列与规格输入框一一对应
              activeModules.push({ index: i, module: m, values: nonEmptyValues })
            }
          }
        }
        if (activeModules.length === 0) {
          // 尚未填写规格值时保留一行空行，固定列仍可录入
          this.resetSkuRowsToSingleBlank()
          return
        }
        let combinations = this.cartesianProduct(activeModules.map(function(a) { return a.values }))
        let meTableData = []
        let that = this
        // 按 SKU 组合缓存当前表格已填写的值，重建表格时保留用户输入（价格、库存、仓库等）
        let currentRowMap = {}
        let existingRows = this.meTable.dataSource || []
        for (let r = 0; r < existingRows.length; r++) {
          if (existingRows[r].sku) {
            currentRowMap[existingRows[r].sku] = existingRows[r]
          }
        }
        for (let i = 0; i < combinations.length; i++) {
          let combo = combinations[i]
          let parts = combo.map(function(v) { return v.text })
          let codes = combo.map(function(v) { return v.valueCode || '' })
          let combined = parts.join('/')
          let rowImage = ''
          for (let m = 0; m < activeModules.length; m++) {
            if (activeModules[m].module.enableImage && combo[m].image) {
              rowImage = combo[m].image
              break
            }
          }
          // 构建规格值JSON（供后端存储到 spec_values 字段）
          let specValueArr = []
          for (let j = 0; j < activeModules.length; j++) {
            specValueArr.push({
              attributeName: activeModules[j].module.name,
              valueText: combo[j].text,
              valueCode: combo[j].valueCode || '',
              childNames: (activeModules[j].module.childNames || []).slice(),
              subValues: Object.assign({}, combo[j].subValues || {})
            })
          }
          let specValues = JSON.stringify(specValueArr)
          // 编辑模式：尝试从旧数据匹配已有行（按 sku 组合文本匹配）
          let currentId = ''
          let purchaseDecimal = ''
          let commodityDecimal = ''
          let wholesaleDecimal = ''
          let lowDecimal = ''
          let marketPrice = ''
          let currentImage = rowImage
          for (let j = 0; j < that.meOldDataSource.length; j++) {
            if (combined === that.meOldDataSource[j].sku) {
              currentId = that.meOldDataSource[j].id
              purchaseDecimal = that.meOldDataSource[j].purchaseDecimal
              commodityDecimal = that.meOldDataSource[j].commodityDecimal
              wholesaleDecimal = that.meOldDataSource[j].wholesaleDecimal
              lowDecimal = that.meOldDataSource[j].lowDecimal
              marketPrice = that.meOldDataSource[j].marketPrice
              if (!currentImage && that.meOldDataSource[j].image) {
                currentImage = that.meOldDataSource[j].image
              }
              break
            }
          }
          // 优先保留当前表格中已填写的值（新增场景下用户先填价格再改规格时不丢失）
          let existRow = currentRowMap[combined]
          if (existRow) {
            if (existRow.id) currentId = existRow.id
            if (existRow.purchaseDecimal !== undefined && existRow.purchaseDecimal !== '') purchaseDecimal = existRow.purchaseDecimal
            if (existRow.commodityDecimal !== undefined && existRow.commodityDecimal !== '') commodityDecimal = existRow.commodityDecimal
            if (existRow.wholesaleDecimal !== undefined && existRow.wholesaleDecimal !== '') wholesaleDecimal = existRow.wholesaleDecimal
            if (existRow.lowDecimal !== undefined && existRow.lowDecimal !== '') lowDecimal = existRow.lowDecimal
            if (existRow.marketPrice !== undefined && existRow.marketPrice !== '') marketPrice = existRow.marketPrice
            if (!currentImage && existRow.image) currentImage = existRow.image
          }
          let row = {
            sku: combined, image: currentImage, specValues: specValues,
            purchaseDecimal: purchaseDecimal, commodityDecimal: commodityDecimal,
            wholesaleDecimal: wholesaleDecimal, lowDecimal: lowDecimal, marketPrice: marketPrice
          }
          // 先清空所有规格列，再按规格模块的原始顺序回填，保证列与规格输入框一一对应
          for (let j = 0; j < this.specModules.length; j++) {
            row['skuField' + (j + 1)] = ''
          }
          for (let j = 0; j < activeModules.length; j++) {
            row['skuField' + (activeModules[j].index + 1)] = parts[j] || ''
          }
          // 保留库存、仓库、门店等用户已填写字段
          if (existRow) {
            if (existRow.stock !== undefined) row.stock = existRow.stock
            if (existRow.depotId !== undefined) row.depotId = existRow.depotId
            if (existRow.store !== undefined) row.store = existRow.store
          }
          if (currentId) { row.id = currentId }
          meTableData.push(row)
        }
        that.meTable.dataSource = meTableData
        // 主动同步到 JEditableTable 内部状态，避免 dataSource watcher 异步执行时机问题导致表格不刷新
        that.$nextTick(() => {
          const table = that.$refs.editableMeTable
          if (table && table._pushByDataSource) {
            table.initialize()
            table._pushByDataSource(meTableData)
            table.forceUpdateFormValues()
          }
        })
      },
      // 安全解析 SKU 的规格值 JSON
      parseSpecValuesJson(specValues) {
        if (!specValues) return []
        try {
          let arr = JSON.parse(specValues)
          return Array.isArray(arr) ? arr : []
        } catch (e) {
          return []
        }
      },
      // 尚未填写规格值时保留一行空行，固定列仍可录入
      resetSkuRowsToSingleBlank() {
        let rows = this.meTable.dataSource || []
        let keep = rows.length > 0 ? rows[0] : {}
        let row = {
          sku: '', specValues: '', image: keep.image || '',
          purchaseDecimal: keep.purchaseDecimal !== undefined ? keep.purchaseDecimal : '',
          commodityDecimal: keep.commodityDecimal !== undefined ? keep.commodityDecimal : '',
          wholesaleDecimal: keep.wholesaleDecimal !== undefined ? keep.wholesaleDecimal : '',
          lowDecimal: keep.lowDecimal !== undefined ? keep.lowDecimal : '',
          stock: keep.stock !== undefined ? keep.stock : '',
          depotId: keep.depotId !== undefined ? keep.depotId : '',
          store: keep.store !== undefined ? keep.store : ''
        }
        for (let i = 0; i < this.specModules.length; i++) {
          row['skuField' + (i + 1)] = ''
        }
        this.meTable.dataSource = [row]
        this.$nextTick(() => {
          const table = this.$refs.editableMeTable
          if (table && table._pushByDataSource) {
            table.initialize()
            table._pushByDataSource([row])
            table.forceUpdateFormValues()
          }
        })
      },
      // 编辑时按已有 SKU 的 specValues 还原规格模块（attribute 列已废弃，不再依赖它）
      restoreSpecModulesFromSkuRows(rows) {
        if (!rows || rows.length === 0) return
        let modules = []
        let moduleMap = {}
        for (let i = 0; i < rows.length; i++) {
          let specArr = this.parseSpecValuesJson(rows[i].specValues)
          for (let j = 0; j < specArr.length; j++) {
            let name = specArr[j].attributeName || ('规格' + (j + 1))
            let text = specArr[j].valueText || ''
            let module = moduleMap[name]
            if (!module) {
              module = {
                name: name,
                enableImage: false,
                childNames: Array.isArray(specArr[j].childNames) ? specArr[j].childNames.slice() : [],
                values: []
              }
              moduleMap[name] = module
              modules.push(module)
            }
            if (text && !module.values.some(function(v) { return v.text === text })) {
              module.values.push({ text: text, image: specArr[j].image || '', subValues: Object.assign({}, specArr[j].subValues || {}) })
            }
            // 历史数据里该规格值带过图片，则本维度恢复为“允许配图”
            if (specArr[j].image) {
              module.enableImage = true
            }
          }
        }
        if (modules.length === 0) return
        // 每个规格至少保留一个空输入位，方便继续录入新规格值
        for (let i = 0; i < modules.length; i++) {
          if (modules[i].values.length === 0) {
            modules[i].values.push({ text: '', image: '', subValues: {} })
          }
        }
        this.suppressSpecSync = true
        this.specModules = modules
        this.categorySelected = true
        this.specLocked = true
        this.$nextTick(() => {
          this.updateSkuColumns()
          // 列已按规格还原，重新按规格值生成行并回填，保证列内容与规格输入框一致
          this.autoSkuList()
          this.suppressSpecSync = false
        })
      },
      // 旧格式 manySku 转 specModules（兼容编辑旧商品）
      migrateOldAttributeToSpecModules(attrObj) {
        let manySkuIds = attrObj.manySku || []
        let skuValuesArr = [attrObj.skuOne, attrObj.skuTwo, attrObj.skuThree]
        for (let i = 0; i < manySkuIds.length; i++) {
          let attrId = manySkuIds[i]
          let attrItem = this.materialAttributeList.find(function(a) { return a.value === attrId })
          let moduleName = attrItem ? attrItem.name : ('规格' + (i + 1))
          let values = (skuValuesArr[i] || []).map(function(v) { return { text: v, image: '' } })
          this.specModules.push({
            name: moduleName,
            enableImage: false,
            values: values
          })
        }
        // materialAttributeList 可能尚未加载，异步重试填充名称
        if (this.materialAttributeList.length === 0 && manySkuIds.length > 0) {
          let that = this
          let retryCount = 0
          let retryTimer = setInterval(function() {
            retryCount++
            if (that.materialAttributeList.length > 0 || retryCount > 20) {
              clearInterval(retryTimer)
              for (let i = 0; i < that.specModules.length; i++) {
                let attrId = manySkuIds[i]
                let attrItem = that.materialAttributeList.find(function(a) { return a.value === attrId })
                if (attrItem) {
                  that.specModules[i].name = attrItem.name
                }
              }
              that.updateSkuColumns()
            }
          }, 200)
        }
      },
      onAdded(event) {
        // 新行添加时无需自动生成条码（条码在基础信息表单统一填写）
      },
      onDeleted(value) {
        this.meDeleteIdList = (value)
      },
      //单元值改变一个字符就触发一次
      onValueChange(event) {
        const { type, row, column, value, target } = event
        switch(column.key) {
          case "purchaseDecimal":
          case "commodityDecimal":
          case "wholesaleDecimal":
          case "lowDecimal":
            this.changeDecimalByValue(row)
            break;
        }
      },
      //修改商品明细中的价格触发计算（已移除多单位价格联动）
      changeDecimalByValue(row) {
      },
      batchSetPriceModalFormOk(price, batchType) {
        let arr = this.meTable.dataSource
        if(arr.length === 0) {
          this.$message.warning('请先添加规格信息！');
        } else {
          let meTableData = []
          for (let i = 0; i < arr.length; i++) {
            let meInfo = {sku: arr[i].sku,
              image: arr[i].image || '',
              specValues: arr[i].specValues || '',
              purchaseDecimal: arr[i].purchaseDecimal, commodityDecimal: arr[i].commodityDecimal,
              marketPrice: arr[i].marketPrice,
              wholesaleDecimal: arr[i].wholesaleDecimal, lowDecimal: arr[i].lowDecimal}
            // 保留所有规格列（skuField1、skuField2...），避免批量设置后规格值丢失
            for (let key in arr[i]) {
              if (key.indexOf('skuField') === 0) {
                meInfo[key] = arr[i][key]
              }
            }
            if (meInfo.skuField1 === undefined) meInfo.skuField1 = ''
            if (meInfo.skuField2 === undefined) meInfo.skuField2 = ''
            if (meInfo.skuField3 === undefined) meInfo.skuField3 = ''
            if(batchType === 'purchase') {
              meInfo.purchaseDecimal = price-0
            } else if(batchType === 'commodity') {
              meInfo.commodityDecimal = price-0
            } else if(batchType === 'wholesale') {
              meInfo.wholesaleDecimal = price-0
            } else if(batchType === 'low') {
              meInfo.lowDecimal = price-0
            }
            if(arr[i].id) {
              meInfo.id = arr[i].id
            }
            meTableData.push(meInfo)
          }
          this.meTable.dataSource = meTableData
        }
      },
      openOcrModal() {
        this.ocrVisible = true
      },
      handleOcrResult(result) {
        // 表单字段映射：所有属性填充到基础信息表单（含条码）
        var formFields = ['name', 'unit', 'brand', 'mnemonic', 'weight', 'mfrs', 'otherField1', 'otherField2', 'otherField3', 'barCode', 'otherField4', 'otherField5', 'otherField6', 'otherField7', 'fabric', 'safetyCategory', 'executiveStandard', 'grade', 'inspection', 'filler']
        var formValues = {}
        for (var i = 0; i < formFields.length; i++) {
          var field = formFields[i]
          if (result[field] !== undefined && result[field] !== '') {
            formValues[field] = result[field]
          }
        }
        if (Object.keys(formValues).length > 0) {
          this.form.setFieldsValue(formValues)
        }
        // 识别到名称后自动生成助记码
        if (result.name) {
          var that = this
          changeNameToPinYin({name: result.name}).then(function(res) {
            if (res && res.code === 200) {
              that.form.setFieldsValue({mnemonic: res.data})
            }
          })
        }
        this.ocrVisible = false
        this.$message.success('识别结果已应用')
      },
      initMaterialAttribute() {
        getMaterialAttributeNameList().then((res)=>{
          if(res) {
            this.materialAttributeList = res
            this.loadAllCategorySpecSuggestions()
          }
        })
      },
      loadDepotOptions() {
        let that = this
        getDepotList().then(function(res) {
          if (res && res.code === 200 && res.data) {
            that.depotList = res.data
            // 同步选项到 meTable 仓库列
            let opts = []
            for (let i = 0; i < res.data.length; i++) {
              let d = res.data[i]
              opts.push({ value: String(d.id), label: d.name })
            }
            for (let j = 0; j < that.meTable.columns.length; j++) {
              if (that.meTable.columns[j].key === 'depotId') {
                that.meTable.columns[j].options = opts
                break
              }
            }
          }
        })
      },
      loadBasePropertyConfig() {
        const self = this
        // 优先读取后端共享配置（所有设备共用一套），无配置或接口异常时回退本地缓存
        getAction('/platformConfig/getUiConfig', { key: 'material_base_property_setting' }).then(res => {
          let parsed = null
          if (res && res.code === 200 && res.data) {
            try { parsed = JSON.parse(res.data) } catch (e) { parsed = null }
          }
          if (parsed && parsed.config) {
            self.applyBasePropertyConfig(parsed.config, parsed.cols, parsed.order)
          } else {
            self.loadBasePropertyConfigFromLocal()
          }
        }).catch(() => {
          self.loadBasePropertyConfigFromLocal()
        })
      },
      // 本地缓存兜底（旧版本仅保存在浏览器 localStorage）
      loadBasePropertyConfigFromLocal() {
        const saved = Vue.ls.get('material_base_property_config')
        const colsSaved = Vue.ls.get('material_base_property_cols')
        const orderSaved = Vue.ls.get('material_base_property_order')
        this.applyBasePropertyConfig(saved, colsSaved, orderSaved)
      },
      // 应用属性配置：config 为字段配置数组，cols 为每行列数，order 为勾选顺序
      applyBasePropertyConfig(saved, colsSaved, orderSaved) {
        if (saved && saved.length) {
          // 合并保存的配置：所有字段都恢复 visible/sort，仅非fixed字段恢复 anotherName
          for (let i = 0; i < saved.length; i++) {
            const item = this.basePropertyList.find(p => p.field === saved[i].field)
            if (item) {
              item.visible = saved[i].visible !== false
              item.sort = saved[i].sort != null ? saved[i].sort : item.sort
              // 只有可改名的扩展字段（扩展1~7）才恢复保存的别名
              if (!item.fixed && ['otherField1','otherField2','otherField3','otherField4','otherField5','otherField6','otherField7'].indexOf(item.field) > -1) {
                // 旧版本曾把扩展字段别名映射成专属业务字段名，加载时统一清理为「扩展N」
                const dupNames = ['面料','安全类别','执行标准','等级','检验','填充物']
                const savedName = saved[i].anotherName
                item.anotherName = savedName && dupNames.indexOf(savedName) === -1
                  ? savedName : ('扩展' + item.field.replace('otherField', ''))
              }
            }
          }
        }
        // 加载每行列数配置
        if (colsSaved) {
          this.propertyConfigCols = colsSaved
        }
        // 加载勾选顺序配置（按保存的顺序构建 propertyConfigSelected）
        if (orderSaved && orderSaved.length) {
          const ordered = orderSaved.filter(f => this.basePropertyFields.includes(f))
          // 历史配置中不存在的新增字段（默认可见）追加到末尾，避免升级后字段消失
          const missing = this.basePropertyList
            .filter(p => p.visible && this.basePropertyFields.includes(p.field) && ordered.indexOf(p.field) === -1)
            .sort((a, b) => a.sort - b.sort)
            .map(p => p.field)
          this.propertyConfigSelected = ordered.concat(missing)
        } else {
          // 兜底：按 basePropertyList 中可见的基础属性 + sort 顺序
          const visible = this.basePropertyList.filter(p => p.visible && this.basePropertyFields.includes(p.field))
          visible.sort((a, b) => a.sort - b.sort)
          this.propertyConfigSelected = visible.map(p => p.field)
        }
      },
      openPropertyConfig() {
        // 初始化弹窗数据（深拷贝，避免取消时影响原数据）
        this.propertyConfigCols = this.propertyConfigCols || 4
        this.propertyConfigSelected = (this.propertyConfigSelected || []).slice()
        const alias = {}
        for (let i = 1; i <= 7; i++) {
          const field = 'otherField' + i
          const item = this.basePropertyList.find(p => p.field === field)
          alias[field] = item ? item.anotherName : '扩展' + i
        }
        this.propertyConfigAlias = alias
        this.propertyConfigVisible = true
      },
      savePropertyConfig() {
        const selected = this.propertyConfigSelected || []
        // 1. 更新 basePropertyList 的 visible 和 sort
        // 属性设置中可勾选的字段：根据勾选状态设置 visible，按勾选顺序设置 sort
        this.basePropertyList.forEach(item => {
          if (this.basePropertyFields.includes(item.field)) {
            const idx = selected.indexOf(item.field)
            if (idx > -1) {
              item.visible = true
              item.sort = idx
            } else {
              item.visible = false
            }
          }
        })
        // 名称(name)必须始终可见
        const nameItem = this.basePropertyList.find(p => p.field === 'name')
        if (nameItem) {
          nameItem.visible = true
          if (selected.indexOf('name') === -1) {
            this.propertyConfigSelected.unshift('name')
          }
        }
        // 2. 更新扩展1~7别名
        for (let i = 1; i <= 7; i++) {
          const field = 'otherField' + i
          const item = this.basePropertyList.find(p => p.field === field)
          if (item) {
            item.anotherName = this.propertyConfigAlias[field] || ('扩展' + i)
          }
        }
        // 3. 保存到本地存储（缓存）+ 后端（所有设备共用一套设置）
        const configSnapshot = this.basePropertyList.map(p => ({
          field: p.field, visible: p.visible, sort: p.sort, anotherName: p.anotherName
        }))
        Vue.ls.set('material_base_property_config', configSnapshot, 365 * 24 * 60 * 60 * 1000)
        Vue.ls.set('material_base_property_cols', this.propertyConfigCols, 365 * 24 * 60 * 60 * 1000)
        Vue.ls.set('material_base_property_order', this.propertyConfigSelected, 365 * 24 * 60 * 60 * 1000)
        postAction('/platformConfig/saveUiConfig', {
          key: 'material_base_property_setting',
          value: JSON.stringify({
            config: configSnapshot,
            cols: this.propertyConfigCols,
            order: this.propertyConfigSelected
          })
        }).catch(() => {})
        this.propertyConfigVisible = false
      },
      handlePropertyConfigReset() {
        // 恢复默认：每行4列，全部基础属性按默认顺序勾选
        this.propertyConfigCols = 4
        const defaultVisible = this.basePropertyList.filter(p =>
          p.visible && this.basePropertyFields.includes(p.field)
        )
        defaultVisible.sort((a, b) => a.sort - b.sort)
        this.propertyConfigSelected = defaultVisible.map(p => p.field)
        this.propertyConfigAlias = { otherField1: '扩展1', otherField2: '扩展2', otherField3: '扩展3', otherField4: '扩展4', otherField5: '扩展5', otherField6: '扩展6', otherField7: '扩展7' }
      },
      handleNameChange(e) {
        let that = this
        if(e.target.value) {
          if(this.setTimeFlag != null){
            clearTimeout(this.setTimeFlag)
          }
          this.setTimeFlag = setTimeout(()=>{
            changeNameToPinYin({name: e.target.value}).then((res) => {
              if (res && res.code === 200) {
                that.form.setFieldsValue({'mnemonic':res.data})
              } else {
                that.$message.warning(res.data)
              }
            })
          },500)
        } else {
          that.form.setFieldsValue({'mnemonic':''})
        }
      }
    }
  }
</script>
<style scoped>
  /* 嵌入模式（页面内展示）时占满内容区 */
  .mm-inline-form {
    width: 100%;
  }
  /* 非全屏弹窗时，内容区域超出视口可滚动 */
  /deep/ .ant-modal-body {
    max-height: calc(100vh - 180px);
    overflow-y: auto;
  }
  .input-table {
    max-width: 100%;
    min-width: 1200px;
  }
  .tag-info {
    font-size:14px;
    height:32px;
    line-height:32px;
    width:100%;
    padding: 0px 11px;
    color: #bbb;
    background-color: #ffffff;
  }
  .spec-module {
    border: 1px solid #e8e8e8;
    border-radius: 4px;
    padding: 16px;
    margin-bottom: 12px;
    background: #fff;
  }
  /* 头部 */
  .spec-header {
    display: flex;
    align-items: center;
    padding-bottom: 12px;
  }
  .spec-header-name {
    display: inline-block;
    width: 220px;
    color: #333;
    font-weight: 500;
  }
  .spec-header-label {
    font-size: 14px;
    color: #333;
    margin-right: 20px;
    white-space: nowrap;
  }
  .spec-header-delete {
    margin-left: auto;
    padding: 0;
    display: flex;
    align-items: center;
    border: none;
    box-shadow: none;
    background: transparent;
  }
  /* 分割线 */
  .spec-divide {
    border-bottom: 1px solid #f0f0f0;
    margin: 0 -16px 12px -16px;
  }
  /* 主内容区 */
  .spec-main {
    display: flex;
    align-items: flex-start;
  }
  .spec-main-label {
    font-size: 14px;
    color: #333;
    margin-right: 21px;
    margin-top: 7px;
    white-space: nowrap;
  }
  .spec-values-list {
    flex: 1;
    display: flex;
    flex-wrap: wrap;
  }
  .spec-value-row {
    display: flex;
    align-items: center;
    margin-bottom: 8px;
    width: 50%;
    padding-right: 8px;
    box-sizing: border-box;
  }
  .spec-composite-value {
    flex: 1;
    min-width: 0;
  }
  .spec-composite-field {
    display: flex;
    align-items: center;
    margin-bottom: 4px;
  }
  .spec-composite-field > span {
    width: 56px;
    margin-right: 6px;
    color: #666;
    white-space: nowrap;
  }
  .spec-composite-value ::v-deep .ant-input {
    width: 220px;
    flex: 0 0 220px;
  }
  .spec-composite-preview {
    display: block;
    color: #888;
    font-size: 12px;
    margin-top: 3px;
  }
  /* 图片上传方块 */
  .spec-value-img-upload {
    margin-left: 8px;
    cursor: pointer;
    flex: 0 0 auto;
  }
  .spec-value-img-trigger {
    width: 40px;
    height: 40px;
    border: 1px dashed #d9d9d9;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    background: #fafafa;
  }
  .spec-value-img-trigger:hover {
    border-color: #1890ff;
  }
  .spec-value-img-preview {
    width: 40px;
    height: 40px;
    border-radius: 8px;
    overflow: hidden;
    cursor: pointer;
  }
  .spec-value-img-preview img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  /* 删除按钮 */
  .spec-value-delete-btn {
    margin-left: 8px;
    padding: 0;
    border: none;
    box-shadow: none;
    background: transparent;
  }
  /* 底部 */
  .spec-bottom {
    margin-top: 4px;
    padding-left: 0;
  }
  .spec-value-suggestions {
    display: inline-flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
    margin-left: 10px;
    vertical-align: middle;
  }
  .spec-suggestions-label {
    color: #999;
    font-size: 12px;
  }
  .spec-value-suggestion-card {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    max-width: 220px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    padding: 2px 8px;
    border: 1px solid #d6e4ff;
    border-radius: 3px;
    background: #f5f8ff;
    color: #2f54eb;
    font-size: 12px;
  }
  .spec-value-suggestion-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    cursor: pointer;
  }
  .spec-suggestion-delete {
    flex: 0 0 auto;
    color: #999;
    cursor: pointer;
  }
  .spec-suggestion-delete:hover {
    color: #ff4d4f;
  }
  .spec-value-suggestion-card:hover {
    border-color: #1890ff;
    background: #e6f7ff;
  }
  .spec-add-value-btn {
    padding: 0;
    border: none;
    box-shadow: none;
    background: transparent;
    font-size: 10px;
    color: #1890ff;
    line-height: 1;
    height: auto;
  }
  .spec-add-value-btn,
  .spec-add-value-btn span,
  .spec-add-value-btn i {
    color: #1890ff;
    font-size: 10px;
  }
  /* 价格设置区域 */
  .price-setting-wrapper {
    border: 1px solid #e8e8e8;
    border-radius: 8px;
    padding: 12px;
    background: #fff;
  }
  .price-setting-fullscreen {
    position: fixed;
    inset: 12px;
    z-index: 1200;
    overflow: auto;
    box-shadow: 0 4px 24px rgba(0, 0, 0, 0.2);
  }
  .price-setting-top-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    min-height: 28px;
  }
  .price-setting-top-bar-left {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
  }
  .price-setting-top-bar-right {
    display: flex;
    align-items: center;
    gap: 8px;
    white-space: nowrap;
    color: #555;
    font-size: 13px;
  }
  .price-setting-title {
    color: #333;
    font-size: 14px;
    margin-right: 8px;
  }
  .required-mark {
    color: #f5222d;
  }
  .progress-label {
    font-size: 14px;
    color: #333;
    white-space: nowrap;
  }
  .progress-percent {
    color: #16a34a;
    font-weight: 600;
  }
  .price-setting-batch-bar {
    margin-bottom: 8px;
    padding: 7px 0 4px;
    background: #fff;
    border-radius: 4px;
    border-top: 1px solid #e8e8e8;
  }
  .price-setting-batch-row {
    display: flex;
    align-items: center;
    flex-wrap: nowrap;
    gap: 6px;
    overflow-x: auto;
    /* 横向滚动容器会裁切溢出内容，上下留出空间避免边框被裁掉 */
    padding: 2px 0;
  }
  .price-setting-batch-row:last-child {
    margin-bottom: 0;
  }
  .price-setting-input {
    width: 190px;
    min-width: 190px;
    flex: 0 0 190px;
  }
  .spec-filter-trigger {
    display: inline-flex;
    align-items: center;
    justify-content: space-between;
    min-width: 180px;
    max-width: 360px;
    height: 32px;
    min-height: 32px;
    padding: 0 8px;
    box-sizing: border-box;
    border: 1px solid #d9d9d9;
    border-radius: 4px;
    background: #fff;
    cursor: pointer;
    font-size: 12px;
    color: #333;
  }
  .spec-filter-trigger:hover {
    border-color: #1890ff;
  }
  .spec-filter-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    flex: 1;
  }
  .spec-filter-tag {
    display: inline-flex;
    align-items: center;
    background: #f0f5ff;
    border: 1px solid #adc6ff;
    border-radius: 2px;
    padding: 0 4px;
    font-size: 12px;
    color: #1890ff;
    line-height: 22px;
  }
  .spec-filter-tag-close {
    margin-left: 2px;
    font-size: 10px;
    cursor: pointer;
    color: #999;
  }
  .spec-filter-tag-close:hover {
    color: #ff4d4f;
  }
  .spec-filter-panel {
    display: flex;
    flex-wrap: wrap;
    width: 380px;
    max-height: 360px;
  }
  .spec-filter-left {
    width: 150px;
    border-right: 1px solid #f0f0f0;
    overflow-y: auto;
    max-height: 320px;
  }
  .spec-filter-left-item {
    display: flex;
    align-items: center;
    padding: 8px 12px;
    cursor: pointer;
    font-size: 14px;
  }
  .spec-filter-left-item:hover {
    background: #f5f5f5;
  }
  .spec-filter-left-item.active {
    background: #e6f7ff;
    color: #1890ff;
  }
  .spec-filter-left-name {
    margin-left: 4px;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .spec-filter-left-arrow {
    font-size: 10px;
    color: #ccc;
  }
  .spec-filter-right {
    width: 230px;
    padding: 8px 12px;
    overflow-y: auto;
    max-height: 320px;
  }
  .spec-filter-right-values {
    display: flex;
    flex-direction: column;
  }
  .spec-filter-value-item {
    margin-bottom: 6px;
  }
  /* 缩小表格内输入框文字大小 */
  .price-setting-wrapper ::v-deep .input-table input {
    font-size: 12px;
  }
  .price-setting-wrapper ::v-deep .input-table .ant-select {
    font-size: 12px;
  }
  .price-setting-wrapper ::v-deep .input-table .ant-select-selection__rendered {
    font-size: 12px;
  }
  .price-setting-wrapper ::v-deep .input-table .td > span {
    font-size: 12px;
  }
  /* 批量设置工具栏：强制所有控件（含输入组内各层容器）统一为 32px 高，
     与「选择范围」框完全一致 */
  .spec-filter-trigger,
  .price-setting-batch-bar ::v-deep .ant-input-group-wrapper,
  .price-setting-batch-bar ::v-deep .ant-input-group,
  .price-setting-batch-bar ::v-deep .ant-input-group-addon,
  .price-setting-batch-bar ::v-deep .ant-input,
  .price-setting-batch-bar ::v-deep .ant-select,
  .price-setting-batch-bar ::v-deep .ant-select-selection,
  .price-setting-batch-row > .ant-btn {
    height: 32px;
    min-height: 32px;
    box-sizing: border-box;
  }
  /* 文字类控件统一字号与行高，保证垂直居中一致 */
  .price-setting-batch-bar ::v-deep .ant-input-group-addon,
  .price-setting-batch-bar ::v-deep .ant-input,
  .price-setting-batch-bar ::v-deep .ant-select-selection__rendered,
  .price-setting-batch-bar ::v-deep .ant-select-selection-selected-value,
  .price-setting-batch-row > .ant-btn {
    font-size: 12px;
    line-height: 30px;
  }
  /* 输入组合框标题（addon 文字）边框色与输入框保持一致 */
  .price-setting-batch-bar ::v-deep .ant-input-group-addon {
    padding: 0 8px;
    border-color: #d9d9d9;
    color: #333;
  }
  .price-setting-batch-bar ::v-deep .ant-input {
    padding: 0 8px;
    color: #333;
  }
  .price-setting-batch-bar ::v-deep .ant-select-selection {
    border-color: #d9d9d9;
  }
  .price-setting-batch-row > .ant-btn {
    padding: 0 12px;
  }
  @media (max-width: 768px) {
    .price-setting-top-bar {
      align-items: flex-start;
      flex-direction: column;
      gap: 8px;
    }
    .price-setting-input {
      width: 100%;
      max-width: 360px;
    }
    .price-setting-fullscreen {
      inset: 4px;
    }
  }

  /* 手机端：基本信息 / 商品信息 / 价格设置 三页横向滑动切换 */
  .mm-pages.is-mobile {
    display: flex;
    width: 100%;
    max-width: 100vw;
    height: calc(100vh - 232px);
    overflow-x: auto;
    overflow-y: hidden;
    scroll-snap-type: x mandatory;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
  }
  .mm-pages.is-mobile::-webkit-scrollbar {
    display: none;
  }
  .mm-pages.is-mobile > .mm-page {
    flex: 0 0 100%;
    flex-shrink: 0;
    width: 100%;
    min-width: 0;
    max-width: 100vw;
    height: 100%;
    box-sizing: border-box;
    padding: 0 4px 8px;
    overflow-x: hidden;
    overflow-y: auto;
    scroll-snap-align: start;
  }
  /* 独立页面模式（非弹窗）：手机端取消内层固定高度与内部滚动，内容随页面整体滚动到底 */
  .mm-inline-form .mm-pages.is-mobile {
    height: auto;
  }
  .mm-inline-form .mm-pages.is-mobile > .mm-page {
    height: auto;
    overflow-y: visible;
  }
  /* 手机端：收紧弹窗内容区留白，避免三页左侧留白过多 */
  @media (max-width: 768px) {
    ::v-deep .ant-modal-body {
      padding: 6px 4px 0;
    }
    ::v-deep .ant-modal-footer {
      padding: 8px;
    }
  }
  /* 手机端隐藏原有单页签，改用顶部三页动态条 */
  .mm-tabs-mobile ::v-deep .ant-tabs-bar {
    display: none;
  }
  .mm-page-tabs {
    display: flex;
    align-items: stretch;
    height: 38px;
    background: #fff;
    border-bottom: 1px solid #eee;
    margin-bottom: 8px;
  }
  .mm-page-tab {
    flex: 1;
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    color: #888;
  }
  .mm-page-tab.active {
    color: #1890ff;
    font-weight: 600;
  }
  .mm-page-tab-bar {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 3px;
    background: transparent;
    transition: background .2s;
  }
  .mm-page-tab.active .mm-page-tab-bar {
    background: #1890ff;
  }
  /* 手机端：属性标题与输入框同行显示 */
  .mm-pages.is-mobile ::v-deep .ant-form-item {
    display: flex;
    align-items: flex-start;
    margin-bottom: 10px;
  }
  .mm-pages.is-mobile ::v-deep .ant-form-item-label {
    flex: 0 0 88px;
    width: 88px !important;
    max-width: 88px;
    padding-right: 8px;
    text-align: right;
  }
  .mm-pages.is-mobile ::v-deep .ant-form-item-label > label {
    white-space: normal;
    line-height: 1.3;
  }
  .mm-pages.is-mobile ::v-deep .ant-form-item-control-wrapper {
    flex: 1 1 auto;
    width: auto !important;
    min-width: 0;
  }
  /* 手机端：规格大类换行，规格值单列显示 */
  .mm-pages.is-mobile .spec-header {
    flex-wrap: wrap;
  }
  .mm-pages.is-mobile .spec-header-name {
    width: 100%;
    margin-bottom: 8px;
  }
  .mm-pages.is-mobile .spec-header ::v-deep .ant-select {
    width: 100% !important;
    margin-bottom: 8px;
  }
  .mm-pages.is-mobile .spec-header-label {
    margin-right: 8px;
    white-space: normal;
  }
  .mm-pages.is-mobile .spec-value-row {
    width: 100%;
    padding-right: 0;
  }
  /* 手机端：顶部页签可点击切换 */
  .mm-page-tab {
    cursor: pointer;
  }
  /* 手机端：批量设置项单列显示 */
  .mm-pages.is-mobile .price-setting-batch-row {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }
  .mm-pages.is-mobile .price-setting-batch-row > * {
    width: 100% !important;
    margin: 0 0 6px 0;
  }
  .mm-pages.is-mobile .spec-filter-trigger {
    width: 100%;
  }
  /* 手机端：SKU 价格卡片单列显示 */
  .mm-sku-cards {
    padding-top: 8px;
  }
  .mm-sku-card {
    border: 1px solid #e8e8e8;
    border-radius: 6px;
    padding: 8px 10px;
    margin-bottom: 8px;
    background: #fff;
  }
  .mm-sku-card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 6px;
    margin-bottom: 6px;
    border-bottom: 1px dashed #eee;
  }
  .mm-sku-card-title {
    font-weight: 600;
    color: #333;
  }
  .mm-sku-card-spec {
    font-size: 12px;
    color: #1890ff;
    text-align: right;
  }
  .mm-sku-card-field {
    display: flex;
    align-items: center;
    margin-bottom: 4px;
  }
  .mm-sku-card-label {
    flex: 0 0 76px;
    width: 76px;
    color: #666;
    font-size: 13px;
  }
  .mm-sku-card-control {
    flex: 1 1 auto;
    min-width: 0;
  }
  .mm-sku-card-text {
    color: #333;
  }
  .mm-sku-card-empty {
    text-align: center;
    color: #999;
    padding: 20px 0;
  }
  /* 手机端：输入框字号不小于 16px，避免聚焦时页面被放大 */
  .mm-pages.is-mobile ::v-deep input,
  .mm-pages.is-mobile ::v-deep textarea {
    font-size: 16px !important;
  }
  /* 手机端：规格值行统一预留「图片+删除」宽度，所有输入框宽度保持一致 */
  .mm-pages.is-mobile .spec-value-row {
    position: relative;
    padding-right: 78px;
  }
  .mm-pages.is-mobile .spec-value-row ::v-deep .ant-input {
    flex: 1 1 auto;
    width: auto !important;
    min-width: 0;
  }
  .mm-pages.is-mobile .spec-value-row .spec-value-img-upload {
    position: absolute;
    right: 32px;
    top: 50%;
    transform: translateY(-50%);
    margin: 0;
  }
  .mm-pages.is-mobile .spec-value-row .spec-value-delete-btn {
    position: absolute;
    right: 0;
    top: 50%;
    transform: translateY(-50%);
    margin: 0;
    padding: 0 2px;
  }
  /* 手机端：组合规格第 0 行的图片与删除按钮，对齐到行右侧预留区 */
  .mm-pages.is-mobile .spec-composite-field {
    position: relative;
  }
  .mm-pages.is-mobile .spec-composite-field > .spec-value-img-upload {
    position: absolute;
    right: -46px;
    top: 50%;
    transform: translateY(-50%);
    margin: 0;
  }
  .mm-pages.is-mobile .spec-composite-field > .spec-value-delete-btn {
    position: absolute;
    right: -78px;
    top: 50%;
    transform: translateY(-50%);
    margin: 0;
    padding: 0 2px;
  }
  /* 手机端：规格区宽度收敛，避免卡片被内容撑破 */
  .mm-pages.is-mobile .spec-module {
    max-width: 100%;
    box-sizing: border-box;
    padding: 10px;
  }
  .mm-pages.is-mobile .spec-main {
    display: block;
  }
  .mm-pages.is-mobile .spec-values-list {
    width: 100%;
    max-width: 100%;
    min-width: 0;
  }
  .mm-pages.is-mobile .spec-value-suggestions {
    flex-wrap: wrap;
    max-width: 100%;
  }
  /* 手机端：价格批量设置项纵向排列且高度固定，避免上下间距过大 */
  .mm-pages.is-mobile .price-setting-batch-bar {
    padding: 4px 0;
    margin-bottom: 6px;
  }
  .mm-pages.is-mobile .price-setting-batch-row {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    flex-wrap: wrap;
    gap: 6px;
    height: auto;
    overflow: visible;
    justify-content: flex-start;
  }
  .mm-pages.is-mobile .price-setting-batch-row > * {
    width: 100% !important;
    max-width: 100% !important;
    height: 32px !important;
    min-height: 32px;
    flex: 0 0 auto;
    margin: 0;
  }
  .mm-pages.is-mobile .price-setting-batch-row ::v-deep .ant-input,
  .mm-pages.is-mobile .price-setting-batch-row ::v-deep .ant-select-selection {
    height: 32px;
    line-height: 30px;
  }
</style>