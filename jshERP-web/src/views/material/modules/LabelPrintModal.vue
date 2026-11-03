/**
 * 打印标签预览弹层
 *
 * 交互：商品信息页点击「打印标签」→ 本弹层按当前商品所用标签模板的实际尺寸显示预览
 *   - 打印：按模板直接打印（不进入编辑界面）
 *   - 编辑：交给标签编辑弹层（LabelEditModal）修改模板/样式
 */
<template>
  <!-- 手机端：全屏标准页面（自定义顶栏 + 内容 + 底部打印按钮） -->
  <template v-if="isMobile">
    <div v-if="visible" class="lp-mobile-page">
      <!-- 顶栏：蓝底 + 白色返回 + 标题 + 设置 -->
      <div class="lmp-header lmp-header-blue">
        <a-icon class="lmp-back" type="arrow-left" @click="handleCancel" />
        <div class="lmp-title-block">
          <div class="lmp-title">{{ mobileTitle }}</div>
          <div class="lmp-subtitle">{{ mobileSubtitle }}</div>
        </div>
        <a-icon v-if="template" class="lmp-settings" type="setting" @click="handleEdit" />
        <span v-else class="lmp-settings placeholder" />
      </div>

      <!-- 主内容 -->
      <div class="lmp-content" :class="{ 'lmp-empty': !template }">
        <template v-if="!template">
          <a-alert type="info" show-icon message="该商品暂无可用标签模板" description="可点击右上角设置图标创建标签模板" />
        </template>
        <template v-else>
          <!-- 预览区（取消卡片，标签缩放填满空白） -->
          <div class="lmp-preview-area" ref="previewArea">
            <div class="lmp-preview" :style="previewStyle">
              <div
                v-for="item in renderItems"
                :key="item.key"
                class="lp-item"
                :class="{ 'is-multiline': !item.isCode && item.attr.value != null && String(item.attr.value).indexOf('\n') > -1, 'is-code': item.isCode }"
                :style="itemStyle(item.key)"
              >
                <template v-if="item.isCode">
                  <img v-if="codeImages[item.key]" :src="codeImages[item.key]" class="lp-code-img" :style="codeImgStyle(item.key)" />
                </template>
                <template v-else>
                  <template v-if="attrLabels[item.attr.key]">{{ attrLabels[item.attr.key] }}: </template>{{ item.attr.value }}
                </template>
              </div>
            </div>
          </div>

          <!-- 底部单列纵向控制区 -->
          <div class="lmp-bottom-wrap">
            <!-- 设备状态行：左标签 + 中间设备名居中 + 箭头 -->
            <div class="lmp-control-row" @click="showMobilePrinterSheet">
              <div class="lmp-control-label">设备状态</div>
              <div class="lmp-control-value lmp-control-value-center">
                <template v-if="mobileDeviceLabel">
                  <span class="lmp-device-name" :class="{ 'online': mobileDeviceOnline }">{{ mobileDeviceLabel }}</span>
                </template>
                <template v-else><span class="lmp-device-name offline">设备未连接</span></template>
              </div>
              <a-icon type="right" class="lmp-row-arrow" />
            </div>

            <!-- 打印方向行 -->
            <div class="lmp-control-row">
              <div class="lmp-control-label">打印方向</div>
              <div class="lmp-control-value">
                <div class="lmp-rotation-tabs">
                  <div
                    v-for="r in rotationOptions"
                    :key="r.value"
                    class="lmp-rot-tab"
                    :class="{ active: printRotation === r.value }"
                    @click="printRotation = r.value"
                  >{{ r.label }}</div>
                </div>
              </div>
            </div>

            <!-- 打印份数行 -->
            <div class="lmp-control-row">
              <div class="lmp-control-label">打印份数</div>
              <div class="lmp-control-value">
                <div class="lmp-qty">
                  <span class="lmp-qty-btn" :class="{ disabled: copies <= 1 }" @click="copies = Math.max(1, copies - 1)">
                    <a-icon type="minus" />
                  </span>
                  <span class="lmp-qty-num">{{ copies }} 份</span>
                  <span class="lmp-qty-btn" @click="copies = Math.min(99, copies + 1)">
                    <a-icon type="plus" />
                  </span>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- 底部打印按钮 -->
      <div class="lmp-footer">
        <a-button
          type="primary"
          block
          size="large"
          class="lmp-print-btn"
          :disabled="!template || !canPrintMobile"
          :loading="printing"
          @click="handlePrint"
        >打印</a-button>
      </div>
    </div>
  </template>

  <!-- 打印机选择弹层（仅手机端） -->
  <a-action-sheet
    v-if="isMobile && showPrinterSheet"
    :visible="showPrinterSheet"
    :maskClosable="true"
    @cancel="showPrinterSheet = false"
    @close="showPrinterSheet = false"
  >
    <a-spin :spinning="loadingPrinters" tip="加载中...">
      <div class="lmp-sheet-title">选择打印机</div>
      <!-- 打印机类型切换 -->
      <div class="lmp-sheet-tabs">
        <span
          v-for="t in printerTabList"
          :key="t.key"
          class="lmp-sheet-tab"
          :class="{ active: printerType === t.key, disabled: !printerCaps[t.key] }"
          @click="switchPrinterType(t.key); if (printerType === t.key) loadPrinters()"
        >
          <a-icon :type="t.icon" /> {{ t.label }}
        </span>
      </div>
      <!-- 后端打印机 -->
      <template v-if="printerType === 'backend'">
        <div v-if="printers.length === 0" class="lmp-sheet-empty">暂无可用打印机，请刷新或在后端机器安装打印机</div>
        <div v-for="p in printers" :key="p.name" class="lmp-sheet-item" :class="{ active: selectedPrinter === p.name }" @click="selectPrinterAndClose(p.name)">
          <a-icon type="printer" /> {{ p.name }}<span v-if="p.isDefault" class="lmp-default-tag">（默认）</span>
          <a-icon v-if="selectedPrinter === p.name" type="check-circle" theme="filled" />
        </div>
      </template>
      <!-- 蓝牙 -->
      <template v-else-if="printerType === 'bluetooth'">
        <div v-if="!btDevice" class="lmp-sheet-empty">
          <a-button icon="scan" type="primary" block :loading="btSearching" @click="handleBluetoothSearch">{{ btSearching ? '搜索中...' : '搜索蓝牙打印机' }}</a-button>
        </div>
        <div v-else class="lmp-sheet-item active" @click="selectPrinterAndClose(btDevice.id)">
          <a-icon type="bluetooth" /> {{ btDevice.name }} <a-icon type="check-circle" theme="filled" />
        </div>
      </template>
      <!-- Wi-Fi -->
      <template v-else-if="printerType === 'wifi'">
        <div v-for="p in wifiPrinters" :key="p.id" class="lmp-sheet-item" :class="{ active: selectedPrinter === p.id }" @click="selectPrinterAndClose(p.id)">
          <a-icon type="wifi" /> {{ p.name }} <span class="lmp-sub">{{ p.ip }}:{{ p.port }}</span>
          <a-tag v-if="wifiStatus[p.id]" color="green" class="lmp-status">已连接</a-tag>
          <a-tag v-else class="lmp-status">未连接</a-tag>
          <a-icon v-if="selectedPrinter === p.id" type="check-circle" theme="filled" />
        </div>
        <a-button type="primary" ghost icon="plus" block style="margin-top:12px" @click="openAddWifiPrinter">添加 Wi-Fi 打印机</a-button>
      </template>
      <!-- 云打印机 -->
      <template v-else-if="printerType === 'cloud'">
        <div class="lmp-sheet-item" :class="{ active: discoveredPrinter && discoveredPrinter.type === 'cloud' }" @click="selectCloudPrinter(); showPrinterSheet = false">
          <a-icon type="cloud" /> 云打印机 / 系统打印机 <a-icon v-if="discoveredPrinter && discoveredPrinter.type === 'cloud'" type="check-circle" theme="filled" />
        </div>
      </template>
    </a-spin>
  </a-action-sheet>

  <!-- 桌面端：保留原有 modal 布局 -->
  <a-modal
    v-if="!isMobile"
    title="打印标签"
    :width="modalWidth"
    :visible="visible"
    :maskClosable="false"
    :footer="null"
    :zIndex="2900"
    :wrapClassName="isMobile ? 'lp-modal-mobile' : ''"
    @cancel="handleCancel"
  >
    <div class="lp-body" :class="{ 'lp-mobile': isMobile }">
      <template v-if="!template">
        <a-alert
          type="info"
          show-icon
          message="该商品暂无可用标签模板"
          description="可直接点击下方「编辑」创建标签模板后再打印"
        />
      </template>
      <template v-else>
        <div class="lp-size-tip">
          标签实际尺寸：{{ labelWidthMm }}mm × {{ labelHeightMm }}mm
          <span class="lp-template-name">{{ template.name }}</span>
        </div>
        <div class="lp-main">
          <!-- 左：标签预览 -->
          <div class="lp-preview-wrap">
            <div class="lp-preview" :style="{ width: labelWidth + 'px', height: labelHeight + 'px' }">
              <div
                v-for="item in renderItems"
                :key="item.key"
                class="lp-item"
                :class="{ 'is-multiline': !item.isCode && item.attr.value != null && String(item.attr.value).indexOf('\n') > -1, 'is-code': item.isCode }"
                :style="itemStyle(item.key)"
              >
                <template v-if="item.isCode">
                  <img v-if="codeImages[item.key]" :src="codeImages[item.key]" class="lp-code-img" :style="codeImgStyle(item.key)" />
                </template>
                <template v-else>
                  <template v-if="attrLabels[item.attr.key]">{{ attrLabels[item.attr.key] }}: </template>{{ item.attr.value }}
                </template>
              </div>
            </div>
          </div>
          <!-- 右：打印设置 -->
          <div class="lp-settings">
            <div class="lp-settings-title">
              打印设置
              <a class="lp-refresh" @click="loadPrinters">刷新</a>
            </div>
            <div class="lp-field">
              <div class="lp-field-label">打印机</div>
              <a-select
                v-model="selectedPrinter"
                :loading="loadingPrinters"
                placeholder="请选择打印机"
                style="width:100%"
                allowClear
              >
                <a-select-option v-for="p in printers" :key="p.name" :value="p.name">
                  {{ p.name }}<span v-if="p.isDefault" class="lp-default-tag">（默认）</span>
                </a-select-option>
              </a-select>
              <div v-if="!loadingPrinters && printers.length === 0" class="lp-tip">
                未获取到打印机，请确认后端所在机器已安装打印机后点击「刷新」
              </div>
            </div>
            <div class="lp-field">
              <div class="lp-field-label">打印份数</div>
              <a-input-number v-model="copies" :min="1" :max="99" style="width:100%" />
            </div>
            <div class="lp-tip">
              打印由后端直接发送到所选打印机；若后端不可用，将自动改用浏览器打印对话框。
              Wi-Fi 打印机按 IP:端口 由后端下发 TSPL 指令，需保证后端所在电脑能访问该地址。
            </div>
          </div>
        </div>
      </template>

      <!-- 添加 Wi-Fi 打印机（名称 / IP 地址 / 端口号） -->
      <a-modal
        :visible="addWifiVisible"
        title="配置"
        width="360px"
        :confirmLoading="addWifiTesting"
        okText="确定"
        cancelText="取消"
        @ok="handleSaveWifiPrinter"
        @cancel="addWifiVisible = false"
      >
        <div class="lp-add-form">
          <div class="lp-add-item">
            <div class="lp-add-label">名称</div>
            <a-input v-model="addWifiForm.name" placeholder="如 汉印 D45" />
          </div>
          <div class="lp-add-item">
            <div class="lp-add-label">IP 地址</div>
            <a-input v-model="addWifiForm.ip" placeholder="如 192.168.10.10" />
          </div>
          <div class="lp-add-item">
            <div class="lp-add-label">端口号</div>
            <a-input-number v-model="addWifiForm.port" :min="1" :max="65535" style="width:100%" />
          </div>
          <div class="lp-tip" style="margin-top:10px">
            指令集固定为 TSPL，端口默认 9100（原始打印端口）。
          </div>
        </div>
      </a-modal>
      <div class="lp-footer">
        <a-button type="primary" icon="printer" :disabled="!template" :loading="printing" @click="handlePrint">打印</a-button>
        <a-button icon="edit" @click="handleEdit">编辑</a-button>
        <a-button @click="handleCancel">取消</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script>
  import { buildLabelAttrs } from '@/views/material/utils/materialDisplay'
  import { printLabel } from '@/views/material/utils/labelPrint'
  import { fitCodeSize, renderCodeDataUrl, isCodeKey, codeSourceKey, migrateCodeStyles, CODE_KEY_PREFIX } from '@/views/material/utils/labelCode'
  import { fetchLabelTemplates } from '@/views/material/utils/labelTemplateStore'
  import { getAction, postAction } from '@/api/manage'
  import {
    DEFAULT_PRINTER_PORT,
    getPrinterCapabilities,
    searchBluetoothPrinter,
    getCloudPrinterInfo,
    getWifiPrinters,
    saveWifiPrinter,
    removeWifiPrinter,
    testWifiPrinter,
    printToWifiPrinter
  } from '@/utils/printerSearch'
  var PX_PER_MM = 3.779528
  export default {
    name: 'LabelPrintModal',
    data() {
      return {
        visible: false,
        record: {},
        sku: {},
        categoryTree: [],
        template: null,
        allAttrs: [],
        selectedAttrs: [],
        attrStyles: {},
        attrLabels: {},
        // 条码/二维码图片缓存（key → dataURL）
        codeImages: {},
        labelWidth: 320,
        labelHeight: 240,
        // 打印机（由后端枚举本机打印机）
        printers: [],
        selectedPrinter: undefined,
        loadingPrinters: false,
        printing: false,
        copies: 1,
        // 手机浏览器（窄屏）时预览/设置改为上下布局
        isMobile: false,
        // ===== 打印机（手机端）=====
        printerType: 'backend', // backend | bluetooth | wifi | cloud
        printerCaps: getPrinterCapabilities(),
        // 已发现的打印机对象（蓝牙/云，非后端枚举的）
        discoveredPrinter: null,
        // 蓝牙
        btSearching: false,
        btDevice: null,
        // Wi-Fi 打印机：手动配置（名称/IP/端口），保存在本地
        wifiPrinters: [],
        activeWifiId: null,
        wifiStatus: {},
        // 添加打印机弹窗
        addWifiVisible: false,
        addWifiTesting: false,
        addWifiForm: { name: '', ip: '', port: DEFAULT_PRINTER_PORT },
        // 手机端：打印方向（0 / 90 / 180）
        printRotation: 0,
        // 手机端：打印机选择弹层
        showPrinterSheet: false,
        // 手机端：预览标签缩放因子（让标签填满空白区域）
        previewScale: 1
      }
    },
    computed: {
      // 打印机类型标签列表
      printerTabList() {
        var caps = this.printerCaps
        return [
          { key: 'backend', label: '后端', icon: 'desktop' },
          { key: 'bluetooth', label: '蓝牙', icon: 'bluetooth' },
          { key: 'wifi', label: 'Wi-Fi', icon: 'wifi' },
          { key: 'cloud', label: '云', icon: 'cloud' }
        ].filter(function(t) {
          return t.key === 'backend' || caps[t.key]
        })
      },
      rotationOptions() {
        return [
          { value: 0, label: '0°', icon: 'edit' },
          { value: 90, label: '90°', icon: 'edit' },
          { value: 180, label: '180°', icon: 'edit' }
        ]
      },
      // 手机端顶栏标题：模板名，无模板时取 SKU 名称
      mobileTitle() {
        if (this.template) return this.template.name || '标签打印'
        return '标签打印'
      },
      // 手机端顶栏副标题：尺寸 + 列数
      mobileSubtitle() {
        if (!this.template) return ''
        return this.labelWidthMm + 'mm × ' + this.labelHeightMm + 'mm' + (this.template.columns ? ', ' + this.template.columns + '列' : '')
      },
      // 手机端：当前打印机显示名
      mobileDeviceLabel() {
        if (this.printerType === 'backend') {
          if (this.selectedPrinter) return this.selectedPrinter
          return ''
        }
        if (this.printerType === 'wifi' && this.activeWifiId) {
          var p = this.wifiPrinters.find(function(x) { return x.id === this.activeWifiId }.bind(this))
          if (p) return p.name
          return ''
        }
        if (this.printerType === 'bluetooth' && this.btDevice) return this.btDevice.name
        if (this.printerType === 'cloud') return '系统打印机'
        return ''
      },
      mobileDeviceOnline() {
        if (this.printerType === 'wifi' && this.activeWifiId) {
          return !!this.wifiStatus[this.activeWifiId]
        }
        return !!this.mobileDeviceLabel
      },
      canPrintMobile() {
        if (this.printerType === 'cloud') return true
        if (this.printerType === 'bluetooth') return !!this.btDevice
        return !!this.selectedPrinter
      },
      printerTypeLabel() {
        var map = { backend: '后端', bluetooth: '蓝牙', wifi: 'Wi-Fi', cloud: '云打印' }
        return map[this.printerType] || '后端'
      },
      // 手机端：预览标签样式（带缩放 + 旋转）
      previewStyle() {
        return {
          width: this.labelWidth + 'px',
          height: this.labelHeight + 'px',
          transform: 'scale(' + this.previewScale + ') rotate(' + this.printRotation + 'deg)',
          transformOrigin: 'center center'
        }
      },
      modalWidth() {
        return this.isMobile ? '100%' : 820
      },
      selectedAttrList() {
        var self = this
        return this.selectedAttrs
          .map(function(key) { return self.allAttrs.find(function(a) { return a.key === key }) })
          .filter(function(attr) { return !!attr })
      },
      labelWidthMm() {
        return Math.round(this.labelWidth / PX_PER_MM)
      },
      labelHeightMm() {
        return Math.round(this.labelHeight / PX_PER_MM)
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
      // 编码内容签名：属性值、编码类型、尺寸变化时重新生成条码/二维码图片
      codeSignature() {
        var self = this
        return this.renderItems.filter(function(item) { return item.isCode }).map(function(item) {
          var s = self.attrStyles[item.key] || {}
          return [item.key, s.codeType || '', item.attr.value == null ? '' : item.attr.value, s.width || '', s.height || ''].join('|')
        }).join(';')
      }
    },
    watch: {
      codeSignature() {
        this.refreshCodeImages()
      },
      // 模板异步加载完成后重新计算缩放
      template() {
        if (this.isMobile && this.visible) this.updatePreviewScale()
      }
    },
    created() {
      var self = this
      this._mql = window.matchMedia('(max-width: 768px)')
      this.isMobile = this._mql.matches
      this._onMqChange = function(e) { self.isMobile = e.matches }
      if (this._mql.addEventListener) {
        this._mql.addEventListener('change', this._onMqChange)
      } else if (this._mql.addListener) {
        this._mql.addListener(this._onMqChange)
      }
      // 手机端：窗口大小变化时重新计算预览缩放
      this._onResize = function() {
        if (self.isMobile && self.visible) self.updatePreviewScale()
      }
      window.addEventListener('resize', this._onResize)
    },
    beforeDestroy() {
      if (!this._mql) return
      if (this._mql.removeEventListener) {
        this._mql.removeEventListener('change', this._onMqChange)
      } else if (this._mql.removeListener) {
        this._mql.removeListener(this._onMqChange)
      }
      window.removeEventListener('resize', this._onResize)
    },
    methods: {
      // 手机端：根据预览区容器尺寸计算标签缩放因子，让标签四周各留 5px 空隙
      updatePreviewScale() {
        var self = this
        this.$nextTick(function() {
          var area = self.$refs.previewArea
          if (!area) { self.previewScale = 1; return }
          var rect = area.getBoundingClientRect()
          if (rect.width <= 0 || rect.height <= 0) { self.previewScale = 1; return }
          // 每侧留 5px 空白
          var scaleW = (rect.width - 10) / self.labelWidth
          var scaleH = (rect.height - 10) / self.labelHeight
          self.previewScale = Math.min(scaleW, scaleH)
        })
      },
      // 是否为独立编码元素
      isCodeItem(key) {
        return isCodeKey(key)
      },
      // 编码元素的图形布局：尺寸取元素自身宽高，按打印机能力换算
      codeLayout(codeKey) {
        var s = this.attrStyles[codeKey] || {}
        var isQr = s.codeType === 'qrcode'
        var sourceKey = codeSourceKey(codeKey)
        var attr = this.allAttrs.find(function(a) { return a.key === sourceKey })
        var value = attr && attr.value != null ? attr.value : ''
        var boxWidth = s.width || (isQr ? 80 : 160)
        var boxHeight = s.height || (isQr ? 80 : 50)
        var fitted = fitCodeSize(s.codeType, value, boxWidth, boxHeight)
        return {
          value: value,
          imgWidth: fitted.width,
          imgHeight: fitted.height,
          // 硬件参数随布局一起下发：窄条宽度（条码）/ 单元宽度（二维码）
          narrow: fitted.narrow,
          cell: fitted.cell
        }
      },
      codeImgStyle(codeKey) {
        var layout = this.codeLayout(codeKey)
        return { width: layout.imgWidth + 'px', height: layout.imgHeight + 'px' }
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
      open(record, sku, categoryTree, preferredTpl) {
        this.record = record || {}
        this.sku = sku || {}
        this.categoryTree = categoryTree || []
        this.allAttrs = this.buildAttrList()
        if (preferredTpl) {
          // 由标签编辑页打开时直接使用其当前编辑内容，保持所见即所得
          this.template = null
          this.applyTemplate(preferredTpl)
        } else {
          this.loadTemplate()
        }
        this.copies = 1
        this.printRotation = 0
        this.discoveredPrinter = null
        this.btDevice = null
        this.activeWifiId = null
        this.showPrinterSheet = false
        // 恢复上次使用的打印机（localStorage）
        var last = null
        try { last = JSON.parse(localStorage.getItem('jsh.lastLabelPrinter') || 'null') } catch (e) { last = null }
        if (last && last.type) {
          this.printerType = last.type
          if (last.type === 'backend') this.selectedPrinter = last.name || undefined
          else if (last.type === 'wifi') { this.activeWifiId = last.name || null; this.selectedPrinter = last.name || undefined }
          else if (last.type === 'cloud') { this.selectedPrinter = undefined }
          else { this.selectedPrinter = undefined }
        } else {
          this.printerType = 'backend'
          this.selectedPrinter = undefined
        }
        this.loadPrinters()
        this.loadWifiPrinters()
        this.visible = true
        // 手机端：打开后计算预览缩放（等 DOM 渲染完）
        if (this.isMobile) {
          var self = this
          this.$nextTick(function() { self.updatePreviewScale() })
          // 模板可能异步加载，延迟再算一次
          setTimeout(function() { self.updatePreviewScale() }, 300)
        }
      },
      // 保存当前打印机选择到 localStorage
      persistLastPrinter() {
        var payload = { type: this.printerType, name: null }
        if (this.printerType === 'backend') payload.name = this.selectedPrinter || null
        else if (this.printerType === 'wifi') payload.name = this.activeWifiId || this.selectedPrinter || null
        try { localStorage.setItem('jsh.lastLabelPrinter', JSON.stringify(payload)) } catch (e) {}
      },
      // 手机端：打开打印机选择弹层
      showMobilePrinterSheet() {
        this.loadPrinters()
        this.showPrinterSheet = true
      },
      // 手机端：选中打印机后关闭弹层
      selectPrinterAndClose(name) {
        if (this.printerType === 'bluetooth') {
          var dev = this.btDevice
          this.selectedPrinter = dev ? dev.id : undefined
          this.discoveredPrinter = dev || null
        } else if (this.printerType === 'wifi') {
          this.activeWifiId = name
          this.selectedPrinter = name
        } else {
          this.selectedPrinter = name
          this.discoveredPrinter = null
        }
        this.persistLastPrinter()
        this.showPrinterSheet = false
      },
      // 加载本机打印机列表（后端枚举），默认选中默认打印机
      loadPrinters() {
        var self = this
        this.loadingPrinters = true
        getAction('/print/printers').then(function(res) {
          self.loadingPrinters = false
          var data = (res && res.data) || {}
          var list = data.printers || []
          self.printers = list
          if (list.length > 0) {
            var def = list.find(function(p) { return p.isDefault })
            self.selectedPrinter = (def || list[0]).name
          } else {
            self.selectedPrinter = undefined
          }
        }).catch(function() {
          self.loadingPrinters = false
          self.printers = []
          self.selectedPrinter = undefined
        })
      },
      // 切换打印机类型
      switchPrinterType(type) {
        this.printerType = type
        this.selectedPrinter = undefined
        this.discoveredPrinter = null
        if (type === 'backend') this.loadPrinters()
        if (type === 'wifi') this.loadWifiPrinters()
      },
      // 蓝牙打印机搜索
      handleBluetoothSearch() {
        var self = this
        this.btSearching = true
        searchBluetoothPrinter().then(function(device) {
          self.btSearching = false
          self.btDevice = device
          self.selectDiscoveredPrinter(device)
        }).catch(function(err) {
          self.btSearching = false
          if (err && err.message && err.message.indexOf('取消') === -1) {
            self.$message.warning(err.message || '蓝牙搜索失败')
          }
        })
      },
      // 读取本地已配置的 Wi-Fi 打印机，并自动选中第一台
      loadWifiPrinters() {
        this.wifiPrinters = getWifiPrinters()
        if (this.wifiPrinters.length > 0 && !this.activeWifiId) {
          this.selectWifiPrinter(this.wifiPrinters[0])
        }
      },
      // 选中一台 Wi-Fi 打印机（同时测试连通性）
      selectWifiPrinter(printer) {
        var self = this
        this.activeWifiId = printer.id
        this.selectedPrinter = printer.id
        this.discoveredPrinter = null
        this.testWifiPrinterStatus(printer)
      },
      // 测试 Wi-Fi 打印机连通性（后端 TCP 探测）
      testWifiPrinterStatus(printer) {
        var self = this
        testWifiPrinter(printer.ip, printer.port).then(function(res) {
          self.$set(self.wifiStatus, printer.id, res.connected)
        }).catch(function() {
          self.$set(self.wifiStatus, printer.id, false)
        })
      },
      // 打开「添加打印机」弹窗
      openAddWifiPrinter() {
        this.addWifiForm = { name: '', ip: '', port: DEFAULT_PRINTER_PORT }
        this.addWifiVisible = true
      },
      // 保存打印机配置：名称 + IP + 端口
      handleSaveWifiPrinter() {
        var self = this
        var form = this.addWifiForm || {}
        var ip = String(form.ip || '').trim()
        if (!/^(\d{1,3}\.){3}\d{1,3}$/.test(ip)) {
          this.$message.warning('请输入正确的 IP 地址，如 192.168.10.10')
          return
        }
        var port = Number(form.port) || DEFAULT_PRINTER_PORT
        this.addWifiTesting = true
        this.wifiPrinters = saveWifiPrinter({ name: form.name, ip: ip, port: port })
        this.addWifiTesting = false
        this.addWifiVisible = false
        var saved = this.wifiPrinters.find(function(p) { return p.ip === ip && p.port === port })
        if (saved) {
          this.selectWifiPrinter(saved)
        }
        this.$message.success('打印机已保存')
      },
      // 删除一台 Wi-Fi 打印机
      handleRemoveWifiPrinter(printer) {
        var self = this
        this.$confirm({
          title: '删除打印机',
          content: '确定删除「' + printer.name + '」？',
          okText: '删除',
          cancelText: '取消',
          onOk: function() {
            self.wifiPrinters = removeWifiPrinter(printer.id)
            self.$delete(self.wifiStatus, printer.id)
            if (self.activeWifiId === printer.id) {
              self.activeWifiId = null
              self.selectedPrinter = undefined
            }
            self.$message.success('已删除')
          }
        })
      },
      // 选中已发现的打印机
      selectDiscoveredPrinter(printer) {
        this.discoveredPrinter = printer
        this.selectedPrinter = printer.id
      },
      // 选择云打印机
      selectCloudPrinter() {
        var cloud = getCloudPrinterInfo()
        this.selectDiscoveredPrinter(cloud)
      },
      handleCancel() {
        this.visible = false
      },
      // 模板优先级：当前商品的普通模板 > 品类的默认模板（云端读取，本地缓存兜底）
      loadTemplate() {
        var self = this
        fetchLabelTemplates().then(function(templates) {
          var record = self.record
          var normal = templates.find(function(t) {
            return t.type === 'normal' && String(t.materialId) === String(record.id)
          })
          var defaultTpl = templates.find(function(t) {
            return t.type === 'default' && String(t.categoryId) === String(record.categoryId)
          })
          self.applyTemplate(normal || defaultTpl || null)
        })
      },
      applyTemplate(tpl) {
        var self = this
        this.template = tpl
        this.selectedAttrs = []
        this.attrStyles = {}
        this.attrLabels = {}
        if (!tpl) return
        // 自定义标签的值保存在模板里，需要合并回属性列表
        if (tpl.customLabels && tpl.customLabels.length > 0) {
          tpl.customLabels.forEach(function(custom) {
            self.allAttrs.push(JSON.parse(JSON.stringify(custom)))
          })
        }
        this.selectedAttrs = JSON.parse(JSON.stringify(tpl.selectedAttrs || []))
        this.selectedAttrs.forEach(function(key) {
          var style = tpl.attrStyles && tpl.attrStyles[key]
          var idx = self.selectedAttrs.indexOf(key)
          if (style) {
            var copy = JSON.parse(JSON.stringify(style))
            // 与标签编辑页一致：旧模板没有字间距/行距时补默认值
            if (copy.letterSpacing == null) copy.letterSpacing = 0
            if (copy.lineHeight == null) copy.lineHeight = 1.3
            // 旧模板默认写死的宽高视为自动，避免多行规格被裁掉
            if (copy.width === 120 && copy.height === 24) {
              delete copy.width
              delete copy.height
            }
            self.$set(self.attrStyles, key, copy)
            // 恢复该属性对应的独立编码元素
            var codeKey = CODE_KEY_PREFIX + key
            if (tpl.attrStyles[codeKey]) {
              self.$set(self.attrStyles, codeKey, JSON.parse(JSON.stringify(tpl.attrStyles[codeKey])))
            }
          } else {
            self.$set(self.attrStyles, key, { fontSize: 14, x: 10, y: 10 + idx * 30, fontWeight: 'normal', letterSpacing: 0, lineHeight: 1.3 })
          }
          // 模板里存在该属性名的记录就按记录恢复（含空字符串，留空表示只显示值）
          if (tpl.attrLabels && Object.prototype.hasOwnProperty.call(tpl.attrLabels, key)) {
            self.$set(self.attrLabels, key, tpl.attrLabels[key])
          } else {
            var attr = self.allAttrs.find(function(a) { return a.key === key })
            self.$set(self.attrLabels, key, attr ? attr.label : '')
          }
        })
        this.labelWidth = tpl.labelWidth || 320
        this.labelHeight = tpl.labelHeight || 240
        // 旧模板（文字与条码合在一个元素里）自动拆分为独立编码元素
        this.attrStyles = migrateCodeStyles(this.attrStyles, this.allAttrs, this.attrLabels)
      },
      itemStyle(key) {
        var s = this.attrStyles[key]
        if (!s) return {}
        // 独立编码元素：宽高即图形实际可打印尺寸
        if (isCodeKey(key)) {
          var codeSize = this.codeLayout(key)
          return {
            left: (s.x || 0) + 'px',
            top: (s.y || 0) + 'px',
            width: codeSize.imgWidth + 'px',
            height: codeSize.imgHeight + 'px'
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
        // 未手动指定宽高时由文字撑开，保证多行规格完整显示
        if (s.width) style.width = s.width + 'px'
        if (s.height) style.height = s.height + 'px'
        return style
      },
      // 与标签编辑弹层共用同一套属性来源，避免模板里的属性在这里丢失
      buildAttrList() {
        return buildLabelAttrs(this.record, this.sku)
      },
      // 编辑：先关闭预览层，动画结束后再交给标签编辑弹层，避免两层重叠
      // 同时带上当前预览使用的模板 id，编辑页按它加载同一套标签数据
      handleEdit() {
        var payload = {
          record: this.record,
          sku: this.sku,
          categoryTree: this.categoryTree,
          templateId: this.template ? this.template.id : null
        }
        this.visible = false
        setTimeout(() => this.$emit('edit', payload), 200)
      },
      // 通过搜索发现的打印机打印（蓝牙/云）
      handleDiscoveredPrint() {
        var self = this
        var printer = this.discoveredPrinter
        var type = printer.type
        this.printing = true
        var items = this.buildPrintItems()
        if (type === 'bluetooth') {
          this.printViaBluetooth(printer, items)
        } else if (type === 'cloud') {
          this.printing = false
          this.printByBrowser(items)
        }
      },
      // 蓝牙打印：连接 GATT 并发送打印数据
      printViaBluetooth(printer, items) {
        var self = this
        var device = printer.device
        if (!device) {
          this.printing = false
          this.$message.error('蓝牙设备未连接')
          return
        }
        device.gatt.connect().then(function(server) {
          // 获取打印服务（取第一个可用的打印服务）
          var servicePromises = printer.capabilities.services.map(function(uuid) {
            return server.getPrimaryService(uuid).catch(function() { return null })
          })
          return Promise.all(servicePromises).then(function(services) {
            var svc = services.find(function(s) { return s !== null })
            if (!svc) throw new Error('未找到打印服务')
            return svc.getCharacteristics()
          })
        }).then(function(chars) {
          // 取第一个可写特征值
          var writable = chars.find(function(c) {
            return c.properties.write || c.properties.writeWithoutResponse
          })
          if (!writable) throw new Error('未找到可写特征值')
          // 将打印内容转为字节流（ESC/POS 简化指令：文本 + 条码）
          var data = self.buildEscPosData(items)
          return self.sendChunks(writable, data)
        }).then(function() {
          self.printing = false
          self.$message.success('已发送到蓝牙打印机：' + printer.name)
        }).catch(function(err) {
          self.printing = false
          self.$message.error('蓝牙打印失败：' + (err.message || err))
        })
      },
      // 分包发送（避免 GATT 写入长度超限）
      sendChunks(char, data, chunkSize) {
        chunkSize = chunkSize || 128
        var i = 0
        var sendNext = function() {
          if (i >= data.length) return Promise.resolve()
          var chunk = data.slice(i, i + chunkSize)
          i += chunkSize
          return char.writeValue(chunk).then(sendNext)
        }
        return sendNext()
      },
      // 构造 ESC/POS 打印数据（简化版）
      buildEscPosData(items) {
        var enc = new TextEncoder()
        var bytes = []
        // 初始化
        bytes.push(0x1b, 0x40)
        items.forEach(function(item) {
          if (item.text) {
            var line = item.text + '\n'
            var arr = enc.encode(line)
            for (var j = 0; j < arr.length; j++) bytes.push(arr[j])
          }
        })
        // 走纸切纸
        bytes.push(0x1d, 0x56, 0x00)
        return new Uint8Array(bytes)
      },
      // 构造打印项数组（供 discovered 打印复用）
      buildPrintItems() {
        return this.buildLayoutItems()
      },
      // 打印：直接发送到所选打印机（后端执行），后端不可用时回退浏览器打印
      handlePrint() {
        if (!this.template) return
        var self = this
        // Wi-Fi 打印机：由后端按 IP:端口 下发 TSPL 指令
        if (this.printerType === 'wifi') {
          this.printViaWifi()
          return
        }
        if (!this.selectedPrinter) {
          this.$message.warning('请先选择打印机')
          return
        }
        // 若选择的是搜索发现的打印机（蓝牙/云），走对应打印通道
        if (this.discoveredPrinter && this.discoveredPrinter.id === this.selectedPrinter) {
          this.handleDiscoveredPrint()
          return
        }
        var items = this.buildLayoutItems()
        this.printing = true
        postAction('/print/label', {
          printerName: self.selectedPrinter,
          copies: self.copies,
          width: self.labelWidth,
          height: self.labelHeight,
          items: items
        }).then(function(res) {
          self.printing = false
          var data = (res && res.data) || {}
          if (res && res.code === 200) {
            self.$message.success('已发送到打印机：' + self.selectedPrinter)
          } else {
            self.$message.error(data.message || '打印失败')
            self.printByBrowser(items)
          }
        }).catch(function() {
          self.printing = false
          self.printByBrowser(items)
        })
      },
      // 组装标签打印项（后端打印机与网络打印机共用同一份布局数据）
      // 按界面元素 1:1 输出：文字项与独立编码项各用自身 x/y/宽高，不再做二次拆分
      buildLayoutItems() {
        var self = this
        var items = []
        this.renderItems.forEach(function(item) {
          var style = self.attrStyles[item.key]
          if (!style) return
          if (item.isCode) {
            // 编码元素：宽高取与预览一致的可打印尺寸
            var layout = self.codeLayout(item.key)
            items.push({
              x: style.x || 0,
              y: style.y || 0,
              width: layout.imgWidth,
              height: layout.imgHeight,
              codeType: style.codeType,
              codeText: layout.value,
              // 硬件取整参数：后端直接使用，不能自行按宽度重算
              narrow: layout.narrow,
              cell: layout.cell,
              // image 仅用于浏览器兜底打印，后端按 codeType + codeText 下发硬件指令
              image: self.codeImages[item.key] || '',
              text: ''
            })
            return
          }
          var attr = item.attr
          var labelText = self.attrLabels[attr.key] || ''
          var value = attr.value == null ? '' : attr.value
          var displayText = labelText ? labelText + ': ' + value : value
          items.push({
            x: style.x || 0,
            y: style.y || 0,
            width: style.width || null,
            height: style.height || null,
            fontSize: style.fontSize,
            fontWeight: style.fontWeight,
            letterSpacing: style.letterSpacing || 0,
            lineHeight: style.lineHeight || 1.3,
            text: displayText
          })
        })
        return items
      },
      // 打印到 Wi-Fi 打印机：后端通过 Socket 发送 TSPL 指令到 ip:端口
      printViaWifi() {
        var self = this
        var printer = this.wifiPrinters.find(function(p) { return p.id === self.activeWifiId })
        if (!printer) {
          this.$message.warning('请先点击「添加打印机」配置打印机')
          return
        }
        this.printing = true
        printToWifiPrinter(printer, {
          copies: self.copies,
          width: self.labelWidth,
          height: self.labelHeight,
          items: this.buildLayoutItems()
        }).then(function(res) {
          self.printing = false
          if (res && res.code === 200) {
            self.$message.success('已发送到打印机：' + printer.name)
            self.$set(self.wifiStatus, printer.id, true)
          } else {
            self.$message.error((res && res.data) || '打印失败')
          }
        }).catch(function() {
          self.printing = false
          self.$message.error('网络打印失败，请检查打印机与后端网络')
        })
      },
      // 浏览器打印（后端打印不可用时兜底）
      printByBrowser(items) {
        var bodyHtml = ''
        items.forEach(function(item) {
          var boxStyle = 'position:absolute;left:' + item.x + 'px;top:' + item.y + 'px;'
          if (item.image) {
            bodyHtml += '<img src="' + item.image + '" style="' + boxStyle +
              'width:' + item.width + 'px;height:' + item.height + 'px;display:block;" />'
            return
          }
          var sizeStyle = ''
          if (item.width) sizeStyle += 'width:' + item.width + 'px;'
          if (item.height) sizeStyle += 'height:' + item.height + 'px;'
          bodyHtml += '<div style="' + boxStyle +
            sizeStyle +
            'font-size:' + item.fontSize + 'px;' +
            'font-weight:' + item.fontWeight + ';' +
            'letter-spacing:' + (item.letterSpacing || 0) + 'px;' +
            'line-height:' + (item.lineHeight || 1.3) + ';' +
            'white-space:pre-line;' +
            'overflow:visible;' +
            '">' + item.text + '</div>'
        })
        printLabel({
          widthPx: this.labelWidth,
          heightPx: this.labelHeight,
          bodyHtml: bodyHtml
        })
      }
    }
  }
</script>

<style scoped>
  .lp-size-tip {
    color: #666;
    font-size: 12px;
    margin-bottom: 8px;
  }
  .lp-template-name {
    margin-left: 8px;
    color: #1890ff;
  }
  .lp-main {
    display: flex;
    align-items: flex-start;
  }
  .lp-preview-wrap {
    flex: 1;
    min-width: 0;
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 300px;
    padding: 12px;
    background: #f5f5f5;
    border-radius: 4px;
    overflow: auto;
  }
  /* 打印设置 */
  .lp-settings {
    width: 260px;
    flex: none;
    margin-left: 16px;
  }
  .lp-settings-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: bold;
    font-size: 14px;
    padding-bottom: 6px;
    margin-bottom: 12px;
    border-bottom: 1px solid #e8e8e8;
  }
  .lp-refresh {
    font-size: 12px;
    font-weight: normal;
  }
  .lp-field {
    margin-bottom: 12px;
  }
  .lp-field-label {
    font-size: 13px;
    color: #333;
    margin-bottom: 6px;
  }
  .lp-tip {
    font-size: 12px;
    color: #999;
    line-height: 1.5;
  }
  .lp-default-tag {
    color: #999;
  }
  .lp-preview {
    position: relative;
    background: #fff;
    border: 1px dashed #d9d9d9;
    overflow: hidden;
    box-sizing: border-box;
  }
  .lp-item {
    position: absolute;
    line-height: 1.2;
    overflow: hidden;
    white-space: nowrap;
  }
  .lp-item.is-multiline {
    /* 规格等属性与表格中的排版一致：逐行、左对齐 */
    white-space: pre-line;
    text-align: left;
    line-height: 1.3;
  }
  .lp-item.is-code {
    overflow: visible;
    white-space: normal;
  }
  /* 条码 / 二维码：独立元素，只渲染图形 */
  .lp-code-img {
    display: block;
    object-fit: contain;
  }
  .lp-footer {
    margin-top: 16px;
    text-align: right;
  }
  .lp-footer .ant-btn {
    margin-left: 8px;
  }
  /* 手机浏览器：预览在上、打印设置在下 */
  .lp-mobile .lp-main {
    flex-direction: column;
  }
  .lp-mobile .lp-preview-wrap {
    width: 100%;
    min-height: 220px;
    max-height: 50vh;
  }
  .lp-mobile .lp-settings {
    width: 100%;
    margin-left: 0;
    margin-top: 16px;
  }
  .lp-mobile .lp-footer {
    text-align: center;
  }
  .lp-mobile .lp-footer .ant-btn {
    margin: 4px;
  }
  /* ===== 打印机类型标签 ===== */
  .lp-printer-tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 10px;
  }
  .lp-tab {
    display: inline-flex;
    align-items: center;
    padding: 4px 10px;
    font-size: 12px;
    border-radius: 14px;
    background: #f0f0f0;
    color: #666;
    cursor: pointer;
    white-space: nowrap;
    .anticon {
      margin-right: 4px;
    }
    &.active {
      background: #1890ff;
      color: #fff;
    }
    &.disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
  }
  /* 打印机搜索区 */
  .lp-printer-search {
    margin-top: 8px;
  }
  /* Wi-Fi 打印机列表项 */
  .lp-device-main {
    flex: 1;
    min-width: 0;
    margin-right: 8px;
  }
  .lp-device-name {
    font-size: 13px;
    color: #333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .lp-device-sub {
    font-size: 11px;
    color: #999;
    margin-top: 2px;
  }
  .lp-device-status {
    margin-right: 6px;
  }
  .lp-device-del {
    color: #ff4d4f;
    cursor: pointer;
  }
  /* 添加打印机表单 */
  .lp-add-item {
    margin-bottom: 12px;
  }
  .lp-add-label {
    font-size: 12px;
    color: #666;
    margin-bottom: 4px;
  }
  .lp-device-info {
    margin-top: 8px;
    padding: 6px 10px;
    background: #f6ffed;
    border: 1px solid #b7eb8f;
    border-radius: 4px;
    font-size: 13px;
    color: #52c41a;
    .anticon {
      margin-right: 4px;
    }
  }
  .lp-device-list {
    margin-top: 8px;
    max-height: 160px;
    overflow-y: auto;
    border: 1px solid #f0f0f0;
    border-radius: 4px;
  }
  .lp-device-item {
    display: flex;
    align-items: center;
    padding: 8px 10px;
    font-size: 13px;
    cursor: pointer;
    border-bottom: 1px solid #f5f5f5;
    .anticon {
      margin-right: 6px;
      color: #1890ff;
    }
    &:last-child {
      border-bottom: none;
    }
    &.selected {
      background: #e6f7ff;
    }
    &:hover {
      background: #fafafa;
    }
  }

  /* ===== 手机端标准页面 ===== */
  .lp-mobile-page {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: #f0f2f5;
    display: flex;
    flex-direction: column;
    z-index: 2900;
  }
  /* 顶栏：蓝底 + 白色图标文字 */
  .lmp-header {
    flex: none;
    height: 50px;
    background: #fff;
    display: flex;
    align-items: center;
    padding: 0 12px;
  }
  .lmp-header-blue {
    background: #1890ff;
    border-bottom: none;
  }
  .lmp-header-blue .lmp-title,
  .lmp-header-blue .lmp-back,
  .lmp-header-blue .lmp-settings {
    color: #fff;
  }
  .lmp-header-blue .lmp-subtitle {
    color: rgba(255, 255, 255, 0.75);
  }
  .lmp-back,
  .lmp-settings {
    font-size: 20px;
    color: #333;
    cursor: pointer;
    width: 32px;
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .lmp-settings.placeholder {
    visibility: hidden;
  }
  .lmp-title-block {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
  }
  .lmp-title {
    font-size: 16px;
    font-weight: 600;
    color: #262626;
    max-width: 60vw;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    line-height: 1.2;
  }
  .lmp-subtitle {
    font-size: 11px;
    color: #999;
    margin-top: 1px;
  }
  /* 主内容：中间空白全部为预览区域 */
  .lmp-content {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    /* 底部 padding 必须 >= footer 高度(70px + safe-area) + 1px 间距，否则最后一张卡片被遮挡 */
    padding: 5px 10px calc(71px + env(safe-area-inset-bottom)) 10px;
    display: flex;
    flex-direction: column;
  }
  .lmp-content.lmp-empty {
    display: flex;
    align-items: flex-start;
  }
  /* 预览区：flex 撑满剩余空间，padding 决定标签离上下组件的距离 */
  .lmp-preview-area {
    flex: 1;
    min-height: 80px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0;
  }
  /* 预览标签（直接在灰色背景上，无白色卡片包裹） */
  .lmp-preview {
    position: relative;
    background: #fff;
    overflow: visible;
    box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.08);
    transform-origin: center center;
    transition: transform 0.2s ease;
  }
  /* 底部单列纵向控制区 */
  .lmp-bottom-wrap {
    flex: none;
    margin-top: 5px;
    display: flex;
    flex-direction: column;
    gap: 1px;
  }
  /* 统一控制行：左标签 + 右值 */
  .lmp-control-row {
    background: #fff;
    border-radius: 10px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
    display: flex;
    align-items: center;
    padding: 12px 14px;
    cursor: pointer;
  }
  .lmp-control-row:active {
    background: #fafafa;
  }
  .lmp-control-label {
    font-size: 14px;
    color: #262626;
    font-weight: 500;
    flex: none;
    min-width: 72px;
  }
  .lmp-control-value {
    flex: 1;
    min-width: 0;
    display: flex;
    justify-content: flex-end;
    align-items: center;
    font-size: 14px;
    color: #333;
  }
  /* 设备状态行：中间名称居中对齐 */
  .lmp-control-value-center {
    justify-content: center;
    text-align: center;
  }
  .lmp-device-name {
    font-size: 14px;
    display: inline-block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    &.online { color: #1890ff; }
    &.offline { color: #bfbfbf; }
  }
  .lmp-row-arrow {
    color: #ccc;
    font-size: 14px;
    flex: none;
    margin-left: 8px;
  }
  /* 打印方向选项 */
  .lmp-rotation-tabs {
    display: flex;
    gap: 5px;
  }
  .lmp-rot-tab {
    display: flex;
    align-items: center;
    gap: 2px;
    padding: 4px 10px;
    border-radius: 14px;
    border: 1px solid #e0e0e0;
    font-size: 12px;
    color: #666;
    cursor: pointer;
    background: #fafafa;
    transition: all 0.15s;
    .anticon {
      font-size: 12px;
    }
    &.active {
      background: #1890ff;
      border-color: #1890ff;
      color: #fff;
    }
  }
  /* 打印份数 */
  .lmp-qty {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .lmp-qty-btn {
    width: 26px;
    height: 26px;
    border-radius: 50%;
    border: 1px solid #e0e0e0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #666;
    cursor: pointer;
    background: #fff;
    font-size: 12px;
    &:active { background: #f0f0f0; }
    &.disabled { color: #ddd; cursor: not-allowed; }
  }
  .lmp-qty-num {
    font-size: 14px;
    color: #262626;
    min-width: 28px;
    text-align: center;
    font-weight: 500;
  }
  /* 底部打印按钮 */
  .lmp-footer {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
    background: #fff;
    border-top: 1px solid #eee;
    z-index: 3001;
  }
  .lmp-print-btn {
    height: 46px;
    font-size: 16px;
    border-radius: 23px;
    background: #1890ff;
    border-color: #1890ff;
    font-weight: 500;
  }
  /* ActionSheet 打印机选择弹层样式 */
  .lmp-sheet-title {
    font-size: 15px;
    font-weight: 600;
    text-align: center;
    padding: 16px 0 12px;
    color: #262626;
  }
  .lmp-sheet-tabs {
    display: flex;
    padding: 0 16px 12px;
    gap: 8px;
    overflow-x: auto;
  }
  .lmp-sheet-tab {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 5px 12px;
    border-radius: 16px;
    font-size: 13px;
    background: #f5f5f5;
    color: #666;
    white-space: nowrap;
    &.active {
      background: #1890ff;
      color: #fff;
    }
    &.disabled {
      opacity: 0.4;
    }
  }
  .lmp-sheet-item {
    display: flex;
    align-items: center;
    padding: 12px 16px;
    font-size: 14px;
    color: #333;
    border-top: 1px solid #f0f0f0;
    cursor: pointer;
    .anticon {
      margin-right: 8px;
      color: #1890ff;
    }
    &.active {
      background: #e6f7ff;
      color: #1890ff;
      .anticon.check-circle {
        margin-left: auto;
        margin-right: 0;
        color: #1890ff;
      }
    }
  }
  .lmp-sheet-empty {
    padding: 24px 16px;
    text-align: center;
    color: #999;
    font-size: 13px;
  }
  .lmp-sub {
    font-size: 11px;
    color: #999;
    margin-left: 6px;
  }
  .lmp-status {
    margin-left: auto;
    margin-right: 8px;
  }
  .lmp-default-tag {
    font-size: 11px;
    color: #999;
    margin-left: 4px;
  }
</style>

<!-- 弹层容器类挂在组件根节点之外，需用非 scoped 样式 -->
<style>
  .lp-modal-mobile .ant-modal {
    top: 0;
    margin: 0;
    max-width: 100vw;
    padding-bottom: 0;
  }
  .lp-modal-mobile .ant-modal-content {
    border-radius: 0;
    min-height: 100vh;
  }
</style>
