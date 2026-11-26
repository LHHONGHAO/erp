<template>
  <!-- 打印机信息（只读）：打印机清单由打印代理配对后自动上报，主机不再下发设备配置 -->
  <mobile-modal-page :visible="visible" title="打印机信息" @close="$emit('close')">
    <div class="pdc-body">
      <div class="pdc-card">
        <div class="pdc-row">
          <div class="pdc-label">门店</div>
          <div class="pdc-value">{{ form.deviceName || '-' }}</div>
        </div>
        <div class="pdc-row">
          <div class="pdc-label">设备编号</div>
          <div class="pdc-value mono">{{ form.deviceId || '-' }}</div>
        </div>
        <div class="pdc-row">
          <div class="pdc-label">在线状态</div>
          <div class="pdc-value" :class="{ ok: deviceOnline }">{{ deviceOnline ? '在线' : '离线' }}</div>
        </div>
        <div class="pdc-row">
          <div class="pdc-label">打印机</div>
          <div class="pdc-value">{{ form.printerName || '-' }}</div>
        </div>
        <div class="pdc-row">
          <div class="pdc-label">型号</div>
          <div class="pdc-value" :class="{ muted: !form.model }">{{ form.model || '未配置型号' }}</div>
        </div>
        <div class="pdc-row">
          <div class="pdc-label">地址</div>
          <div class="pdc-value mono">{{ form.host || '-' }}:{{ form.port || 9100 }}</div>
        </div>
      </div>
      <div class="pdc-tip">
        打印机与型号由打印代理配对后自动上报；型号在代理设置页「配置打印机型号」中维护，主机按该型号的指令方式打印。
      </div>
    </div>

    <template #footer>
      <div class="pdc-footer">
        <a-button type="primary" block size="large" class="pdc-btn" @click="$emit('close')">关闭</a-button>
      </div>
    </template>
  </mobile-modal-page>
</template>

<script>
  import MobileModalPage from '@/components/mobile/MobileModalPage'

  export default {
    name: 'PrinterDeviceConfig',
    components: { MobileModalPage },
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
          model: ''
        }
      }
    },
    computed: {
      deviceOnline() {
        var device = this.findDevice(this.form.deviceId)
        return !!(device && device.online)
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
          deviceName: device ? (device.name || device.storeName || device.deviceId) : '',
          printerId: printer ? printer.id : '',
          printerName: printer ? (printer.name || printer.id) : '',
          host: (printer && printer.host) || '',
          port: (printer && printer.port) || 9100,
          model: (printer && printer.model) || ''
        }
      }
    }
  }
</script>

<style scoped>
  .pdc-body {
    flex: 1;
    overflow-y: auto;
    padding: 10px 10px 16px;
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
    min-width: 0;
    font-size: 14px;
    color: #666;
    text-align: right;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .pdc-value.mono {
    font-family: Consolas, Monaco, monospace;
    font-size: 13px;
  }
  .pdc-value.ok {
    color: #389e0d;
  }
  .pdc-value.muted {
    color: #bbb;
  }
  .pdc-tip {
    margin-top: 12px;
    padding: 0 4px;
    font-size: 12px;
    line-height: 18px;
    color: #999;
  }
  .pdc-footer {
    padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
    background: #fff;
    border-top: 1px solid #eee;
  }
  .pdc-btn {
    height: 46px;
    font-size: 16px;
    border-radius: 23px;
    background: #1890ff;
    border-color: #1890ff;
    font-weight: 500;
  }
</style>
