/**
 * 打印标签预览弹层
 *
 * 交互：商品信息页点击「打印标签」→ 本弹层按当前商品所用标签模板的实际尺寸显示预览
 *   - 打印：按模板直接打印（不进入编辑界面）
 *   - 编辑：交给标签编辑弹层（LabelEditModal）修改模板/样式
 */
<template>
  <a-modal
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
                v-for="attr in selectedAttrList"
                :key="attr.key"
                class="lp-item"
                :class="{ 'is-multiline': attr.value != null && String(attr.value).indexOf('\n') > -1, 'is-code': isCodeItem(attr.key) }"
                :style="itemStyle(attr.key)"
              >
                <template v-if="isCodeItem(attr.key)">
                  <div class="lp-code-box">
                    <div class="lp-code-text" :style="codeTextStyle(attr)">{{ codeText(attr) }}</div>
                    <img v-if="codeImages[attr.key]" :src="codeImages[attr.key]" class="lp-code-img" :style="codeImgStyle(attr)" />
                  </div>
                </template>
                <template v-else>
                  <template v-if="attrLabels[attr.key]">{{ attrLabels[attr.key] }}: </template>{{ attr.value }}
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
              <!-- 打印机类型切换（手机端展示） -->
              <div v-if="isMobile" class="lp-printer-tabs">
                <span
                  v-for="t in printerTabList"
                  :key="t.key"
                  class="lp-tab"
                  :class="{ active: printerType === t.key, disabled: !printerCaps[t.key] }"
                  @click="switchPrinterType(t.key)"
                >
                  <a-icon :type="t.icon" /> {{ t.label }}
                </span>
              </div>
              <a-select
                v-if="!isMobile || printerType === 'backend'"
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
              <div v-if="!loadingPrinters && printers.length === 0 && (!isMobile || printerType === 'backend')" class="lp-tip">
                未获取到打印机，请确认后端所在机器已安装打印机后点击「刷新」
              </div>

              <!-- 蓝牙打印机搜索 -->
              <div v-if="isMobile && printerType === 'bluetooth'" class="lp-printer-search">
                <a-button
                  icon="scan"
                  type="primary"
                  block
                  :loading="btSearching"
                  @click="handleBluetoothSearch"
                >{{ btDevice ? '已连接：' + btDevice.name : '搜索蓝牙打印机' }}</a-button>
                <div v-if="btDevice" class="lp-device-info">
                  <a-icon type="check-circle" theme="twoTone" two-tone-color="#52c41a" />
                  {{ btDevice.name }}
                </div>
              </div>

              <!-- Wi-Fi 打印机搜索 -->
              <div v-if="isMobile && printerType === 'wifi'" class="lp-printer-search">
                <div class="lp-ip-row">
                  <a-input v-model="wifiIp" placeholder="打印机IP，如 192.168.1.100" style="flex:1" />
                  <a-button :loading="wifiProbing" @click="handleWifiProbe">探测</a-button>
                </div>
                <a-button size="small" style="margin-top:6px" @click="handleWifiScan">扫描同网段</a-button>
                <div v-if="wifiList.length > 0" class="lp-device-list">
                  <div
                    v-for="p in wifiList"
                    :key="p.id"
                    class="lp-device-item"
                    :class="{ selected: selectedPrinter === p.id }"
                    @click="selectDiscoveredPrinter(p)"
                  >
                    <a-icon type="wifi" /> {{ p.name }}
                  </div>
                </div>
              </div>

              <!-- 网络共享打印机搜索 -->
              <div v-if="isMobile && printerType === 'network'" class="lp-printer-search">
                <div class="lp-ip-row">
                  <a-input v-model="networkIp" placeholder="共享主机IP" style="flex:1" />
                  <a-input v-model="networkShare" placeholder="共享名(可选)" style="flex:1;margin-left:6px" />
                  <a-button :loading="netProbing" @click="handleNetworkProbe">探测</a-button>
                </div>
                <a-button size="small" style="margin-top:6px" @click="handleNetworkScan">扫描同网段</a-button>
                <div v-if="networkList.length > 0" class="lp-device-list">
                  <div
                    v-for="p in networkList"
                    :key="p.id"
                    class="lp-device-item"
                    :class="{ selected: selectedPrinter === p.id }"
                    @click="selectDiscoveredPrinter(p)"
                  >
                    <a-icon type="share-alt" /> {{ p.name }}
                  </div>
                </div>
              </div>

              <!-- 云打印机 -->
              <div v-if="isMobile && printerType === 'cloud'" class="lp-printer-search">
                <a-button
                  icon="cloud"
                  type="primary"
                  block
                  @click="selectCloudPrinter"
                >使用云打印机 / 系统打印机</a-button>
                <div class="lp-tip" style="margin-top:8px">
                  打印时将弹出浏览器打印对话框，可从中选择已配置的云打印机。
                </div>
              </div>
            </div>
            <div class="lp-field">
              <div class="lp-field-label">打印份数</div>
              <a-input-number v-model="copies" :min="1" :max="99" style="width:100%" />
            </div>
            <div class="lp-tip">
              打印由后端直接发送到所选打印机；若后端不可用，将自动改用浏览器打印对话框。
            </div>
          </div>
        </div>
      </template>
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
  import { fitCodeSize, renderCodeDataUrl } from '@/views/material/utils/labelCode'
  import { fetchLabelTemplates } from '@/views/material/utils/labelTemplateStore'
  import { getAction, postAction } from '@/api/manage'
  import {
    PRINTER_TYPES,
    getPrinterCapabilities,
    searchBluetoothPrinter,
    probeWifiPrinter,
    searchWifiPrinters,
    probeNetworkPrinter,
    searchNetworkPrinters,
    getCloudPrinterInfo
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
        // ===== 打印机搜索（手机端）=====
        printerType: 'backend', // backend | bluetooth | wifi | network | cloud
        printerCaps: getPrinterCapabilities(),
        // 已发现的打印机对象（非后端枚举的）
        discoveredPrinter: null,
        // 蓝牙
        btSearching: false,
        btDevice: null,
        // Wi-Fi
        wifiIp: '',
        wifiProbing: false,
        wifiList: [],
        // 网络共享
        networkIp: '',
        networkShare: '',
        netProbing: false,
        networkList: []
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
          { key: 'network', label: '共享', icon: 'share-alt' },
          { key: 'cloud', label: '云', icon: 'cloud' }
        ].filter(function(t) {
          return t.key === 'backend' || caps[t.key]
        })
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
      // 编码内容签名：属性值、编码类型、尺寸变化时重新生成条码/二维码图片
      codeSignature() {
        var self = this
        return this.selectedAttrList.map(function(attr) {
          var s = self.attrStyles[attr.key] || {}
          return [attr.key, s.codeType || '', attr.value == null ? '' : attr.value, s.width || '', s.height || ''].join('|')
        }).join(';')
      }
    },
    watch: {
      codeSignature() {
        this.refreshCodeImages()
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
    },
    beforeDestroy() {
      if (!this._mql) return
      if (this._mql.removeEventListener) {
        this._mql.removeEventListener('change', this._onMqChange)
      } else if (this._mql.removeListener) {
        this._mql.removeListener(this._onMqChange)
      }
    },
    methods: {
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
      // 编码元素布局：文字高度、盒宽、图形尺寸
      codeLayout(attr) {
        var s = this.attrStyles[attr.key] || {}
        var isQr = s.codeType === 'qrcode'
        var text = this.codeText(attr)
        var lines = text ? text.split('\n').length : 0
        var fontSize = s.fontSize || 14
        var lineHeight = s.lineHeight || 1.3
        // 文字框固定为文本实际长高 + 2px
        var textBoxHeight = lines ? Math.round(lines * fontSize) + 2 : 0
        // 文字与图形之间的间距取原行距留白的三分之一
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
      codeImgStyle(attr) {
        var layout = this.codeLayout(attr)
        return { width: layout.imgWidth + 'px', height: layout.imgHeight + 'px' }
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
        this.selectedPrinter = undefined
        this.copies = 1
        // 重置打印机搜索状态
        this.printerType = this.isMobile ? 'backend' : 'backend'
        this.discoveredPrinter = null
        this.btDevice = null
        this.wifiIp = ''
        this.wifiList = []
        this.networkIp = ''
        this.networkShare = ''
        this.networkList = []
        this.loadPrinters()
        this.visible = true
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
      // Wi-Fi 打印机：探测指定 IP
      handleWifiProbe() {
        var self = this
        if (!this.wifiIp) {
          this.$message.warning('请输入打印机 IP')
          return
        }
        this.wifiProbing = true
        probeWifiPrinter(this.wifiIp).then(function(printer) {
          self.wifiProbing = false
          if (printer) {
            self.wifiList = [printer]
            self.$message.success('发现打印机：' + printer.name)
          } else {
            self.wifiList = []
            self.$message.warning('未在该 IP 发现支持 IPP 的打印机')
          }
        })
      },
      // Wi-Fi 打印机：扫描同网段
      handleWifiScan() {
        var self = this
        this.wifiProbing = true
        this.wifiList = []
        searchWifiPrinters('', 1200).then(function(list) {
          self.wifiProbing = false
          self.wifiList = list
          if (list.length === 0) {
            self.$message.info('同网段未发现打印机，可尝试手动输入 IP')
          }
        })
      },
      // 网络共享打印机：探测指定 IP
      handleNetworkProbe() {
        var self = this
        if (!this.networkIp) {
          this.$message.warning('请输入共享主机 IP')
          return
        }
        this.netProbing = true
        probeNetworkPrinter(this.networkIp, this.networkShare).then(function(printer) {
          self.netProbing = false
          if (printer) {
            self.networkList = [printer]
            self.$message.success('发现共享打印机：' + printer.name)
          } else {
            self.networkList = []
            self.$message.warning('未发现共享打印机')
          }
        })
      },
      // 网络共享打印机：扫描同网段
      handleNetworkScan() {
        var self = this
        this.netProbing = true
        this.networkList = []
        searchNetworkPrinters('').then(function(list) {
          self.netProbing = false
          self.networkList = list
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
      },
      itemStyle(key) {
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
        // 未手动指定宽高时由文字撑开，保证多行规格完整显示
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
      // 与标签编辑弹层共用同一套属性来源，避免模板里的属性在这里丢失
      buildAttrList() {
        return buildLabelAttrs(this.record, this.sku)
      },
      // 编辑：先关闭预览层，动画结束后再交给标签编辑弹层，避免两层重叠
      handleEdit() {
        var payload = {
          record: this.record,
          sku: this.sku,
          categoryTree: this.categoryTree
        }
        this.visible = false
        setTimeout(() => this.$emit('edit', payload), 200)
      },
      // 通过搜索发现的打印机打印（蓝牙/Wi-Fi/网络/云）
      handleDiscoveredPrint() {
        var self = this
        var printer = this.discoveredPrinter
        var type = printer.type
        this.printing = true
        var items = this.buildPrintItems()
        if (type === 'bluetooth') {
          this.printViaBluetooth(printer, items)
        } else if (type === 'wifi' || type === 'network') {
          this.printViaNetwork(printer, items)
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
      // 网络打印：通过 IPP 发送打印任务
      printViaNetwork(printer, items) {
        var self = this
        var url = printer.capabilities.url
        var bodyHtml = this.itemsToHtml(items)
        // IPP Print-Job 操作（简化）：将 HTML 转为 base64 发送
        var base64 = btoa(unescape(encodeURIComponent(bodyHtml)))
        fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/ipp' },
          body: base64
        }).then(function(res) {
          self.printing = false
          if (res.ok) {
            self.$message.success('已发送到网络打印机：' + printer.name)
          } else {
            self.$message.error('网络打印失败，HTTP ' + res.status)
            self.printByBrowser(items)
          }
        }).catch(function() {
          self.printing = false
          self.$message.error('网络打印失败，改用浏览器打印')
          self.printByBrowser(items)
        })
      },
      // 将打印项转为 HTML（供网络/浏览器打印）
      itemsToHtml(items) {
        var html = ''
        items.forEach(function(item) {
          if (item.image) {
            html += '<img src="' + item.image + '" style="position:absolute;left:' + item.x +
              'px;top:' + item.y + 'px;width:' + item.width + 'px;height:' + item.height + 'px;" />'
          } else {
            html += '<div style="position:absolute;left:' + item.x + 'px;top:' + item.y +
              'px;font-size:' + item.fontSize + 'px;">' + item.text + '</div>'
          }
        })
        return html
      },
      // 构造打印项数组（供 discovered 打印复用）
      buildPrintItems() {
        var self = this
        var items = []
        this.selectedAttrList.forEach(function(attr) {
          var style = self.attrStyles[attr.key]
          if (!style) return
          if (style.codeType) {
            var layout = self.codeLayout(attr)
            var codeUrl = self.codeImages[attr.key] || ''
            items.push({
              x: style.x, y: style.y, width: layout.boxWidth, height: null,
              fontSize: style.fontSize, fontWeight: style.fontWeight,
              letterSpacing: style.letterSpacing || 0, lineHeight: style.lineHeight || 1.3,
              text: layout.text
            })
            items.push({
              x: style.x, y: style.y + layout.textHeight,
              width: layout.imgWidth, height: layout.imgHeight,
              codeType: style.codeType, codeText: attr.value == null ? '' : String(attr.value),
              image: codeUrl, text: ''
            })
            return
          }
          var labelText = self.attrLabels[attr.key] || ''
          var displayText = labelText ? labelText + ': ' + attr.value : attr.value
          items.push({
            x: style.x, y: style.y,
            width: style.width || null, height: style.height || null,
            fontSize: style.fontSize, fontWeight: style.fontWeight,
            letterSpacing: style.letterSpacing || 0, lineHeight: style.lineHeight || 1.3,
            text: displayText
          })
        })
        return items
      },
      // 打印：直接发送到所选打印机（后端执行），后端不可用时回退浏览器打印
      handlePrint() {
        if (!this.template) return
        if (!this.selectedPrinter) {
          this.$message.warning('请先选择打印机')
          return
        }
        var self = this
        // 若选择的是搜索发现的打印机（蓝牙/Wi-Fi/网络/云），走对应打印通道
        if (this.discoveredPrinter && this.discoveredPrinter.id === this.selectedPrinter) {
          this.handleDiscoveredPrint()
          return
        }
        var items = []
        this.selectedAttrList.forEach(function(attr) {
          var style = self.attrStyles[attr.key]
          if (!style) return
          // 条码/二维码：文字照常打印，图形打印在文字下方
          if (style.codeType) {
            var layout = self.codeLayout(attr)
            var codeUrl = self.codeImages[attr.key] || ''
            items.push({
              x: style.x,
              y: style.y,
              width: layout.boxWidth,
              height: null,
              fontSize: style.fontSize,
              fontWeight: style.fontWeight,
              letterSpacing: style.letterSpacing || 0,
              lineHeight: style.lineHeight || 1.3,
              text: layout.text
            })
            items.push({
              x: style.x,
              y: style.y + layout.textHeight,
              width: layout.imgWidth,
              height: layout.imgHeight,
              codeType: style.codeType,
              codeText: attr.value == null ? '' : String(attr.value),
              // image 仅用于浏览器兜底打印，后端按 codeType + codeText 下发硬件指令
              image: codeUrl,
              text: ''
            })
            return
          }
          var labelText = self.attrLabels[attr.key] || ''
          var displayText = labelText ? labelText + ': ' + attr.value : attr.value
          items.push({
            x: style.x,
            y: style.y,
            width: style.width || null,
            height: style.height || null,
            fontSize: style.fontSize,
            fontWeight: style.fontWeight,
            letterSpacing: style.letterSpacing || 0,
            lineHeight: style.lineHeight || 1.3,
            text: displayText
          })
        })
        this.printing = true
        postAction('/print/label', {
          printerName: this.selectedPrinter,
          copies: this.copies,
          width: this.labelWidth,
          height: this.labelHeight,
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
  /* 条码 / 二维码：上方文字、下方图形 */
  .lp-code-box {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    width: 100%;
  }
  .lp-code-text {
    white-space: pre-line;
    word-break: break-word;
    text-align: left;
  }
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
  .lp-ip-row {
    display: flex;
    align-items: center;
    gap: 6px;
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
