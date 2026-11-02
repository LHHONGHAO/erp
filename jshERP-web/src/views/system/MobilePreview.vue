<template>
  <div class="mp-page">
    <div class="mp-toolbar">
      <span class="mp-brand">手机端预览</span>
      <a-select :value="deviceWidth" size="small" style="width: 150px" @change="onDeviceChange">
        <a-select-option v-for="d in devices" :key="d.width" :value="d.width">{{ d.label }}</a-select-option>
      </a-select>
      <a-select :value="currentPath" size="small" style="width: 150px" @change="onPageChange">
        <a-select-option v-for="p in pages" :key="p.path" :value="p.path">{{ p.name }}</a-select-option>
      </a-select>
      <a-input
        :value="currentPath"
        size="small"
        style="width: 260px"
        placeholder="页面路径，如 /material/material_add"
        @change="onPathInput"
        @pressEnter="reload" />
      <a-button size="small" icon="reload" @click="reload">刷新</a-button>
      <a-button size="small" icon="export" @click="openNew">新窗口打开</a-button>
      <span class="mp-tip">iframe 宽度小于 768px 时自动进入手机端布局</span>
    </div>
    <div class="mp-stage">
      <div class="mp-phone" :style="{ width: deviceWidth + 'px', height: deviceHeight + 'px' }">
        <iframe :key="frameKey" :src="currentPath" class="mp-frame"></iframe>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'MobilePreview',
    data () {
      return {
        deviceWidth: 390,
        deviceHeight: 844,
        devices: [
          { width: 375, height: 812, label: '手机 375×812' },
          { width: 390, height: 844, label: '手机 390×844' },
          { width: 414, height: 896, label: '手机 414×896' },
          { width: 768, height: 1024, label: '平板 768×1024' }
        ],
        pages: [
          { name: '首页', path: '/dashboard/analysis' },
          { name: '新增商品', path: '/material/material_add' },
          { name: '商品类别', path: '/material/material_category' },
          { name: '商品信息', path: '/material/material' },
          { name: '销售开单', path: '/bill/retail_sale_order' },
          { name: '零售出库', path: '/bill/retail_out' }
        ],
        currentPath: '/dashboard/analysis',
        frameKey: 0
      }
    },
    methods: {
      onDeviceChange (width) {
        const device = this.devices.find(d => d.width === width)
        this.deviceWidth = width
        this.deviceHeight = device ? device.height : 844
      },
      onPageChange (path) {
        this.currentPath = path
        this.reload()
      },
      onPathInput (e) {
        this.currentPath = e.target.value
      },
      // 通过变更 key 强制重新加载 iframe
      reload () {
        this.frameKey++
      },
      openNew () {
        window.open(this.currentPath, '_blank')
      }
    }
  }
</script>

<style scoped>
  .mp-page {
    display: flex;
    flex-direction: column;
    height: 100vh;
    background: #f0f2f5;
  }
  .mp-toolbar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
    padding: 10px 16px;
    background: #fff;
    border-bottom: 1px solid #e8e8e8;
  }
  .mp-brand {
    font-size: 15px;
    font-weight: 600;
    color: #1890ff;
  }
  .mp-tip {
    color: #999;
    font-size: 12px;
  }
  .mp-stage {
    flex: 1;
    overflow: auto;
    display: flex;
    align-items: flex-start;
    justify-content: center;
    padding: 20px;
  }
  .mp-phone {
    flex-shrink: 0;
    overflow: hidden;
    background: #fff;
    border: 10px solid #222;
    border-radius: 28px;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  }
  .mp-frame {
    display: block;
    width: 100%;
    height: 100%;
    border: 0;
  }
</style>
