<template>
  <!-- 远程打印机配置页：打印机设备 / 打印机 / 远程连接 / 当前指令 + 品牌与指令集选择 -->
  <div v-if="visible" class="pdc-page">
    <div class="pdc-header">
      <a-icon class="pdc-back" type="arrow-left" @click="$emit('close')" />
      <div class="pdc-title">编辑设备</div>
      <span class="pdc-placeholder" />
    </div>

    <div class="pdc-info">
      <div class="pdc-card">
        <div class="pdc-row" @click="openDeviceSheet">
          <div class="pdc-label">打印机设备</div>
          <div class="pdc-value" :class="{ muted: !form.deviceName }">{{ form.deviceName || '请选择设备' }}</div>
          <a-icon type="right" class="pdc-arrow" />
        </div>
        <div class="pdc-row" @click="openPrinterSheet">
          <div class="pdc-label">打印机</div>
          <div class="pdc-value" :class="{ muted: !form.printerName }">{{ form.printerName || '请选择打印机' }}</div>
          <a-icon type="right" class="pdc-arrow" />
        </div>
        <div class="pdc-row">
          <div class="pdc-label">IP 地址</div>
          <input v-model.trim="form.host" class="pdc-input" placeholder="如 192.168.10.10" />
        </div>
        <div class="pdc-row">
          <div class="pdc-label">端口</div>
          <input v-model.number="form.port" type="number" class="pdc-input" placeholder="9100" />
        </div>
        <div class="pdc-row">
          <div class="pdc-label">当前指令</div>
          <div class="pdc-value">{{ form.cmdSet || 'TSPL' }}</div>
        </div>
      </div>
      <div class="pdc-section">选择打印机品牌</div>
    </div>

    <div class="pdc-grid">
      <div
        v-for="b in brands"
        :key="b.key"
        class="pdc-cell"
        :class="{ active: form.brand === b.key }"
        @click="chooseBrand(b)">
        <div class="pdc-logo" :style="{ color: b.color }">{{ b.name }}</div>
        <div class="pdc-name">{{ b.sub || b.name }}</div>
        <div class="pdc-cmd" @click.stop="cmdSheet = true">
          <span>{{ form.cmdSet }}</span>
          <a-icon type="down" />
        </div>
      </div>
    </div>

    <div class="pdc-footer">
      <a-button
        type="primary"
        block
        size="large"
        class="pdc-save"
        :disabled="!form.deviceId || !form.printerId"
        :loading="saving"
        @click="save">保存</a-button>
    </div>

    <!-- 打印机设备选择 -->
    <div v-if="deviceSheet" class="pdc-sheet-mask" @click="deviceSheet = false">
      <div class="pdc-sheet" @click.stop>
        <div class="pdc-sheet-group">
          <div
            v-for="d in devices"
            :key="d.deviceId"
            class="pdc-sheet-item"
            :class="{ active: form.deviceId === d.deviceId }"
            @click="chooseDevice(d)">
            <span>{{ d.name || d.deviceId }}</span>
            <span class="pdc-sheet-sub">{{ d.deviceId }} · {{ d.online ? '在线' : '离线' }}</span>
          </div>
          <div v-if="!devices || devices.length === 0" class="pdc-sheet-empty">暂无远程打印设备</div>
        </div>
        <div class="pdc-sheet-cancel" @click="deviceSheet = false">取消</div>
      </div>
    </div>

    <!-- 打印机选择 -->
    <div v-if="printerSheet" class="pdc-sheet-mask" @click="printerSheet = false">
      <div class="pdc-sheet" @click.stop>
        <div class="pdc-sheet-group">
          <div
            v-for="p in devicePrinters"
            :key="p.id"
            class="pdc-sheet-item"
            :class="{ active: form.printerId === p.id }"
            @click="choosePrinter(p)">
            <span>{{ p.name || p.id }}</span>
            <span class="pdc-sheet-sub">{{ p.host }}:{{ p.port }}</span>
          </div>
          <div v-if="devicePrinters.length === 0" class="pdc-sheet-empty">该设备未配置打印机</div>
        </div>
        <div class="pdc-sheet-cancel" @click="printerSheet = false">取消</div>
      </div>
    </div>

    <!-- 指令集选择 -->
    <div v-if="cmdSheet" class="pdc-sheet-mask" @click="cmdSheet = false">
      <div class="pdc-sheet" @click.stop>
        <div class="pdc-sheet-group">
          <div
            v-for="c in cmdSets"
            :key="c"
            class="pdc-sheet-item"
            :class="{ active: form.cmdSet === c }"
            @click="form.cmdSet = c; cmdSheet = false">{{ c }}</div>
        </div>
        <div class="pdc-sheet-cancel" @click="cmdSheet = false">取消</div>
      </div>
    </div>
  </div>
</template>

<script>
  import { savePrintAgent } from '@/api/printAgent'

  export default {
    name: 'PrinterDeviceConfig',
    props: {
      visible: { type: Boolean, default: false },
      // 远程打印代理设备列表（含 printers）
      devices: { type: Array, default: function() { return [] } },
      deviceId: { type: String, default: '' },
      printerId: { type: String, default: '' }
    },
    data() {
      return {
        form: {
          deviceId: '',
          deviceName: '',
          printerId: '',
          printerName: '',
          host: '',
          port: 9100,
          brand: '',
          cmdSet: 'TSPL'
        },
        saving: false,
        deviceSheet: false,
        printerSheet: false,
        cmdSheet: false,
        cmdSets: ['TSPL', 'CPCL', 'DDPL', 'ESC/POS'],
        brands: [
          { key: 'chiteng', name: '驰腾', cmdSet: 'TSPL', color: '#2f54eb' },
          { key: 'gainscha', name: 'Gainscha', sub: '佳博', cmdSet: 'TSPL', color: '#2f54eb' },
          { key: 'sprt', name: 'SPRT', sub: '思普瑞特', cmdSet: 'TSPL', color: '#2f54eb' },
          { key: 'hprt', name: '汉印 HPRT', sub: '汉印', cmdSet: 'CPCL', color: '#cf1322' },
          { key: 'clabel', name: 'Clabel', sub: 'Clabel', cmdSet: 'TSPL', color: '#2f54eb' },
          { key: 'kuaimai', name: '快麦', sub: '快麦', cmdSet: 'TSPL', color: '#fa541c' },
          { key: 'qirui', name: '启锐', sub: '启锐', cmdSet: 'TSPL', color: '#2f54eb' },
          { key: 'tsc', name: 'TSC', sub: 'TSC', cmdSet: 'TSPL', color: '#2f54eb' },
          { key: 'urprtcn', name: 'URPRTCN', sub: '优悠', cmdSet: 'TSPL', color: '#fa8c16' }
        ]
      }
    },
    computed: {
      devicePrinters() {
        var dev = this.findDevice(this.form.deviceId)
        return this.printersOf(dev)
      }
    },
    watch: {
      visible(val) {
        if (val) this.initForm()
      },
      deviceId() {
        if (this.visible) this.initForm()
      },
      printerId() {
        if (this.visible) this.initForm()
      }
    },
    methods: {
      findDevice(deviceId) {
        return (this.devices || []).find(function(d) { return d.deviceId === deviceId })
      },
      printersOf(device) {
        if (!device) return []
        var printers = device.printers
        if (typeof printers === 'string') {
          try { printers = JSON.parse(printers) } catch (e) { printers = [] }
        }
        return printers || []
      },
      initForm() {
        var device = this.findDevice(this.deviceId) || (this.devices || [])[0]
        var printer = null
        if (device) {
          var printers = this.printersOf(device)
          printer = printers.find(function(p) { return p.id === this.printerId }, this) || printers[0] || null
        }
        this.form = {
          deviceId: device ? device.deviceId : '',
          deviceName: device ? (device.name || device.deviceId) : '',
          printerId: printer ? printer.id : '',
          printerName: printer ? (printer.name || printer.id) : '',
          host: (printer && printer.host) || '',
          port: (printer && printer.port) || 9100,
          brand: (printer && printer.brand) || '',
          cmdSet: (printer && printer.cmdSet) || 'TSPL'
        }
        this.deviceSheet = false
        this.printerSheet = false
        this.cmdSheet = false
      },
      openDeviceSheet() {
        if (!this.devices || this.devices.length === 0) {
          this.$message.info('暂无远程打印设备，请先在电脑端「打印管理」新增设备')
          return
        }
        this.deviceSheet = true
      },
      openPrinterSheet() {
        if (!this.form.deviceId) {
          this.$message.info('请先选择打印机设备')
          return
        }
        if (this.devicePrinters.length === 0) {
          this.$message.info('该设备未配置打印机，请先在电脑端「打印管理」添加')
          return
        }
        this.printerSheet = true
      },
      chooseDevice(device) {
        var printers = this.printersOf(device)
        var printer = printers[0] || null
        this.form.deviceId = device.deviceId
        this.form.deviceName = device.name || device.deviceId
        this.form.printerId = printer ? printer.id : ''
        this.form.printerName = printer ? (printer.name || printer.id) : ''
        this.form.host = (printer && printer.host) || ''
        this.form.port = (printer && printer.port) || 9100
        this.form.brand = (printer && printer.brand) || ''
        this.form.cmdSet = (printer && printer.cmdSet) || 'TSPL'
        this.deviceSheet = false
      },
      choosePrinter(printer) {
        this.form.printerId = printer.id
        this.form.printerName = printer.name || printer.id
        this.form.host = printer.host || ''
        this.form.port = printer.port || 9100
        this.form.brand = printer.brand || ''
        this.form.cmdSet = printer.cmdSet || 'TSPL'
        this.printerSheet = false
      },
      chooseBrand(brand) {
        this.form.brand = brand.key
        this.form.cmdSet = brand.cmdSet
      },
      save() {
        var self = this
        var form = this.form
        var device = this.findDevice(form.deviceId)
        if (!device) {
          this.$message.error('设备不存在，请刷新后重试')
          return
        }
        var host = String(form.host || '').trim()
        if (!host) {
          this.$message.warning('请输入打印机 IP 地址')
          return
        }
        var port = Number(form.port)
        if (!port || port < 1 || port > 65535) {
          this.$message.warning('端口号需在 1-65535 之间')
          return
        }
        var printers = this.printersOf(device).map(function(p) {
          if (p.id !== form.printerId) return p
          return Object.assign({}, p, { host: host, port: port, brand: form.brand, cmdSet: form.cmdSet })
        })
        this.saving = true
        savePrintAgent({
          id: device.id,
          deviceId: device.deviceId,
          name: device.name,
          enabled: device.enabled,
          printers: printers
        }).then(function(res) {
          self.saving = false
          if (res && res.code === 200) {
            self.$message.success('已保存，后续打印将使用 ' + form.cmdSet + ' 指令')
            self.$emit('saved', { deviceId: form.deviceId, printerId: form.printerId })
            self.$emit('close')
          } else {
            self.$message.error((res && res.data) || '保存失败')
          }
        }).catch(function(err) {
          self.saving = false
          self.$message.error((err && err.message) || '保存失败')
        })
      }
    }
  }
</script>

<style scoped>
  .pdc-page {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: #f0f2f5;
    display: flex;
    flex-direction: column;
    z-index: 3100;
  }
  .pdc-header {
    flex: none;
    height: 49px;
    background: #1890ff;
    display: flex;
    align-items: center;
  }
  .pdc-back {
    font-size: 22px;
    color: #fff;
    cursor: pointer;
    width: 58px;
    height: 58px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .pdc-title {
    flex: 1;
    text-align: center;
    font-size: 16px;
    font-weight: 500;
    color: #fff;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .pdc-placeholder {
    width: 58px;
    flex: none;
  }
  .pdc-info {
    flex: none;
    padding: 10px 10px 0;
  }
  .pdc-card {
    background: #fff;
    border-radius: 10px;
    padding: 0 12px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  }
  .pdc-row {
    display: flex;
    align-items: center;
    min-height: 48px;
    border-bottom: 1px solid #f5f5f5;
    cursor: pointer;
  }
  .pdc-row:last-child {
    border-bottom: none;
  }
  .pdc-label {
    width: 92px;
    flex: none;
    font-size: 14px;
    color: #333;
  }
  .pdc-value {
    flex: 1;
    font-size: 14px;
    color: #666;
    text-align: right;
    padding-right: 6px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .pdc-value.muted {
    color: #bbb;
  }
  .pdc-input {
    flex: 1;
    min-width: 0;
    border: none;
    outline: none;
    background: transparent;
    font-size: 14px;
    color: #333;
    text-align: right;
    padding: 0;
  }
  .pdc-input::placeholder {
    color: #bbb;
  }
  .pdc-arrow {
    flex: none;
    color: #ccc;
    font-size: 12px;
  }
  .pdc-section {
    padding: 14px 4px 6px;
    font-size: 13px;
    color: #8c8c8c;
  }
  .pdc-grid {
    flex: 1;
    overflow-y: auto;
    background: #fff;
    padding: 12px 6px 88px;
    display: flex;
    flex-wrap: wrap;
    align-content: flex-start;
  }
  .pdc-cell {
    width: 33.33%;
    padding: 16px 8px 12px;
    text-align: center;
    cursor: pointer;
    border-radius: 10px;
  }
  .pdc-cell.active {
    background: #e6f7ff;
  }
  .pdc-logo {
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 19px;
    font-weight: 700;
    letter-spacing: 0.5px;
    white-space: nowrap;
    overflow: hidden;
  }
  .pdc-name {
    margin-top: 8px;
    font-size: 14px;
    color: #333;
  }
  .pdc-cmd {
    margin: 8px auto 0;
    width: 88px;
    height: 26px;
    border: 1px solid #1890ff;
    border-radius: 13px;
    color: #1890ff;
    font-size: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 3px;
  }
  .pdc-cmd .anticon {
    font-size: 10px;
  }
  .pdc-footer {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
    background: #fff;
    border-top: 1px solid #eee;
    z-index: 3101;
  }
  .pdc-save {
    height: 46px;
    font-size: 16px;
    border-radius: 23px;
    background: #1890ff;
    border-color: #1890ff;
    font-weight: 500;
  }
  .pdc-sheet-mask {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.45);
    z-index: 3102;
  }
  .pdc-sheet {
    position: absolute;
    left: 8px;
    right: 8px;
    bottom: calc(8px + env(safe-area-inset-bottom));
  }
  .pdc-sheet-group {
    background: #fff;
    border-radius: 12px;
    overflow: hidden;
    max-height: 60vh;
    overflow-y: auto;
  }
  .pdc-sheet-item {
    padding: 14px 16px;
    font-size: 15px;
    color: #333;
    border-bottom: 1px solid #f0f0f0;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .pdc-sheet-item:last-child {
    border-bottom: none;
  }
  .pdc-sheet-item.active {
    color: #1890ff;
    font-weight: 600;
    background: #f0f8ff;
  }
  .pdc-sheet-sub {
    font-size: 12px;
    color: #999;
    font-weight: 400;
  }
  .pdc-sheet-empty {
    padding: 22px 0;
    text-align: center;
    font-size: 14px;
    color: #999;
  }
  .pdc-sheet-cancel {
    margin-top: 8px;
    background: #fff;
    border-radius: 12px;
    padding: 15px 0;
    text-align: center;
    font-size: 16px;
    color: #1890ff;
    cursor: pointer;
  }
</style>
