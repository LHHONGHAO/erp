<template>
  <!-- 远程打印配置（手机端 / 系统设置）：打印机设备列表，点击打印机进入配置页 -->
  <div class="rpc-page">
    <div class="rpc-head">
      <div class="rpc-head-title">打印机设备</div>
      <a class="rpc-refresh" @click="loadDevices(true)">
        <a-icon type="reload" :spin="loading" /> 刷新
      </a>
    </div>

    <a-spin :spinning="loading && devices.length === 0">
      <div v-if="devices.length === 0 && !loading" class="rpc-empty">
        <a-icon type="printer" class="rpc-empty-icon" />
        <div class="rpc-empty-text">暂无远程打印设备</div>
        <div class="rpc-empty-sub">请先在电脑端「打印管理」新增打印代理设备与打印机</div>
      </div>

      <div v-for="dev in devices" :key="dev.deviceId" class="rpc-device">
        <div class="rpc-device-head">
          <span class="rpc-device-name">{{ dev.name || dev.deviceId }}</span>
          <span class="rpc-tag" :class="dev.online ? 'online' : 'offline'">{{ dev.online ? '在线' : '离线' }}</span>
          <span v-if="isDisabled(dev)" class="rpc-tag disabled">停用</span>
        </div>
        <div class="rpc-device-sub">{{ dev.deviceId }}<template v-if="printersOf(dev).length"> · {{ printersOf(dev).length }} 台打印机</template></div>

        <div
          v-for="p in printersOf(dev)"
          :key="p.id"
          class="rpc-printer"
          @click="openConfig(dev, p)">
          <a-icon type="printer" class="rpc-printer-icon" />
          <div class="rpc-printer-text">
            <div class="rpc-printer-name">{{ p.name || p.id }}</div>
            <div class="rpc-printer-sub">{{ hostText(p) }} · {{ brandText(p) }} · {{ p.cmdSet || 'TSPL' }}</div>
          </div>
          <span class="rpc-printer-edit">编辑</span>
        </div>

        <div v-if="printersOf(dev).length === 0" class="rpc-no-printer">该设备未配置打印机</div>
      </div>
    </a-spin>

    <printer-device-config
      :visible="configVisible"
      :devices="devices"
      :device-id="configDeviceId"
      :printer-id="configPrinterId"
      @close="configVisible = false"
      @saved="handleSaved" />
  </div>
</template>

<script>
  import { getPrintAgentList } from '@/api/printAgent'
  import PrinterDeviceConfig from '@/views/print/agent/PrinterDeviceConfig'

  export default {
    name: 'RemotePrintConfig',
    components: { PrinterDeviceConfig },
    data() {
      return {
        devices: [],
        loading: false,
        timer: null,
        configVisible: false,
        configDeviceId: '',
        configPrinterId: ''
      }
    },
    created() {
      this.loadDevices()
      this.startTimer()
    },
    activated() {
      this.loadDevices()
      this.startTimer()
    },
    deactivated() {
      this.configVisible = false
      this.stopTimer()
    },
    beforeDestroy() {
      this.stopTimer()
    },
    methods: {
      startTimer() {
        var self = this
        this.stopTimer()
        this.timer = setInterval(function() { self.loadDevices() }, 15000)
      },
      stopTimer() {
        if (this.timer) {
          clearInterval(this.timer)
          this.timer = null
        }
      },
      loadDevices(manual) {
        var self = this
        if (this.loading) return
        this.loading = true
        getPrintAgentList().then(function(res) {
          self.loading = false
          var data = (res && res.data) || []
          self.devices = Array.isArray(data) ? data : []
          if (manual && self.devices.length === 0) self.$message.info('暂无远程打印设备')
        }).catch(function() {
          self.loading = false
          if (manual) self.$message.error('加载远程打印设备失败')
        })
      },
      printersOf(device) {
        if (!device) return []
        var printers = device.printers
        if (typeof printers === 'string') {
          try { printers = JSON.parse(printers) } catch (e) { printers = [] }
        }
        return printers || []
      },
      isDisabled(device) {
        return device.enabled === '0' || device.enabled === 0
      },
      hostText(p) {
        if (!p.host) return '未配置地址'
        return p.host + ':' + (p.port || 9100)
      },
      brandText(p) {
        var map = {
          chiteng: '驰腾',
          gainscha: '佳博',
          sprt: '思普瑞特',
          hprt: '汉印',
          clabel: 'Clabel',
          kuaimai: '快麦',
          qirui: '启锐',
          tsc: 'TSC',
          urprtcn: '优悠'
        }
        return map[p.brand] || '未选品牌'
      },
      openConfig(device, printer) {
        this.configDeviceId = device.deviceId
        this.configPrinterId = printer.id
        this.configVisible = true
      },
      handleSaved() {
        this.configVisible = false
        this.loadDevices()
      }
    }
  }
</script>

<style scoped>
  .rpc-page {
    min-height: 100%;
    background: #f0f2f5;
    padding: 12px 12px calc(20px + env(safe-area-inset-bottom));
    box-sizing: border-box;
  }
  .rpc-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 4px 10px;
  }
  .rpc-head-title {
    font-size: 14px;
    color: #8c8c8c;
  }
  .rpc-refresh {
    font-size: 13px;
    color: #1890ff;
  }
  .rpc-refresh .anticon {
    margin-right: 3px;
  }
  .rpc-device {
    background: #fff;
    border-radius: 10px;
    padding: 12px;
    margin-bottom: 10px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  }
  .rpc-device-head {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .rpc-device-name {
    font-size: 15px;
    font-weight: 600;
    color: #262626;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .rpc-tag {
    flex: none;
    font-size: 11px;
    line-height: 18px;
    padding: 0 8px;
    border-radius: 9px;
  }
  .rpc-tag.online {
    color: #389e0d;
    background: #f6ffed;
  }
  .rpc-tag.offline {
    color: #8c8c8c;
    background: #f5f5f5;
  }
  .rpc-tag.disabled {
    color: #d46b08;
    background: #fff7e6;
  }
  .rpc-device-sub {
    margin-top: 3px;
    font-size: 12px;
    color: #999;
  }
  .rpc-printer {
    margin-top: 10px;
    padding: 10px 10px;
    border-radius: 8px;
    background: #fafafa;
    display: flex;
    align-items: center;
    cursor: pointer;
  }
  .rpc-printer:active {
    background: #f0f8ff;
  }
  .rpc-printer-icon {
    font-size: 20px;
    color: #1890ff;
    margin-right: 10px;
  }
  .rpc-printer-text {
    flex: 1;
    min-width: 0;
  }
  .rpc-printer-name {
    font-size: 14px;
    color: #333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .rpc-printer-sub {
    margin-top: 3px;
    font-size: 12px;
    color: #999;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .rpc-printer-edit {
    flex: none;
    margin-left: 10px;
    font-size: 13px;
    color: #1890ff;
    padding: 4px 10px;
    border: 1px solid #1890ff;
    border-radius: 14px;
  }
  .rpc-no-printer {
    margin-top: 10px;
    padding: 14px 0;
    text-align: center;
    font-size: 13px;
    color: #999;
    background: #fafafa;
    border-radius: 8px;
  }
  .rpc-empty {
    margin-top: 60px;
    text-align: center;
  }
  .rpc-empty-icon {
    font-size: 44px;
    color: #d9d9d9;
  }
  .rpc-empty-text {
    margin-top: 12px;
    font-size: 15px;
    color: #666;
  }
  .rpc-empty-sub {
    margin-top: 6px;
    font-size: 12px;
    color: #999;
  }
</style>
