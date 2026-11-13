<template>
  <div class="msb-wrap">
    <!-- 白色胶囊搜索框：拍照 + 展示态关键字 + 扫码 -->
    <div class="msb-search-box">
      <span class="msb-camera" @click="$emit('camera')">
        <a-icon type="camera" />
        <span class="msb-ai-tag">AI</span>
      </span>
      <span class="msb-display" @click="inputVisible = true">
        <span v-if="value" class="msb-display-text">{{ value }}</span>
        <span v-else class="msb-display-placeholder">{{ placeholder }}</span>
      </span>
      <a-icon type="scan" class="msb-scan" @click="openScanPage" />
    </div>
    <a-button class="msb-filter-btn" icon="filter" @click="$emit('filter')">筛选</a-button>

    <!-- 标准输入弹层：输入内容后点按钮才触发搜索 -->
    <mobile-input-popup
      :visible="inputVisible"
      :value="value"
      :placeholder="placeholder"
      confirm-text="搜索"
      @cancel="inputVisible = false"
      @confirm="onInputConfirm" />

    <!-- 标准条码扫描页 -->
    <mobile-scan-page ref="scanPage" :visible="scanVisible" @close="scanVisible = false" @result="onScanResult" />
  </div>
</template>

<script>
import MobileScanPage from './MobileScanPage'
import MobileInputPopup from './MobileInputPopup'

/**
 * 手机端标准搜索底栏：拍照 + 展示态搜索框 + 扫码 + 筛选
 * 点击搜索框弹出标准输入弹层，弹层内确认后才触发搜索。
 * 供标准搜索页面、商品选择弹层等复用，业务行为保持一致。
 */
export default {
  name: 'MobileSearchBar',
  components: { MobileScanPage, MobileInputPopup },
  props: {
    /** 搜索关键字，支持 v-model */
    value: {
      type: String,
      default: ''
    },
    placeholder: {
      type: String,
      default: '请输入关键字'
    },
    /** 识别到内容后是否自动关闭扫码页，false 时由调用页决定关闭时机 */
    autoScanClose: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return {
      /** 标准条码扫描页是否打开 */
      scanVisible: false,
      /** 标准输入弹层是否打开 */
      inputVisible: false
    }
  },
  methods: {
    /** 输入弹层确认：回填关键字并触发搜索 */
    onInputConfirm(text) {
      this.inputVisible = false
      this.$emit('input', text)
      this.$emit('search', text)
    },
    openScanPage() {
      this.scanVisible = true
    },
    // 扫码页识别结果：默认回填搜索框并触发搜索；autoScanClose=false 时交由调用页处理
    onScanResult(payload) {
      if (!this.autoScanClose) {
        this.$emit('scan-result', payload)
        return
      }
      this.scanVisible = false
      const text = (payload && payload.text) || ''
      if (!text) return
      this.$emit('input', text)
      this.$emit('search', text)
      this.$emit('scan-result', payload)
    },
    // 供调用页主动关闭标准扫码页
    closeScanPage() {
      this.scanVisible = false
    },
    // 供调用页在扫码页内显示提示（如未命中商品）
    setScanTip(text, isError) {
      if (this.$refs.scanPage && this.$refs.scanPage.setTip) {
        this.$refs.scanPage.setTip(text, isError)
      }
    }
  }
}
</script>

<style lang="less" scoped>
.msb-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}
/* 搜索区：白色胶囊输入框，扫码/拍照位于输入框两侧 */
.msb-search-box {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 21px;
  padding: 0 12px;
  height: 42px;
  box-shadow: 0 0 0 1px #e8e8e8 inset;
}
.msb-scan {
  flex-shrink: 0;
  margin-left: 8px;
  font-size: 19px;
  color: #999;
  cursor: pointer;
}
/* 搜索框展示态：点击后弹出标准输入弹层 */
.msb-display {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  height: 42px;
  font-size: 14px;
  overflow: hidden;
  cursor: pointer;
}
.msb-display-text {
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.msb-display-placeholder {
  color: #b7b7b7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.msb-camera {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  margin-right: 6px;
  color: #999;
  cursor: pointer;
  flex-shrink: 0;
  .anticon {
    font-size: 19px;
  }
}
.msb-ai-tag {
  position: absolute;
  top: -4px;
  right: -6px;
  font-size: 9px;
  line-height: 1;
  padding: 1px 3px;
  background: linear-gradient(135deg, #ff6b6b, #ffb347);
  color: #fff;
  border-radius: 6px;
  font-weight: 700;
}
/* 筛选按钮：单行「图标 + 文字」，与搜索框同高 */
.msb-filter-btn {
  flex-shrink: 0;
  height: 42px;
  margin-left: 8px;
  margin-right: -8px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  border-radius: 21px;
  border-color: #e5e6eb;
  background: #fff;
  color: #4e5969;
  font-size: 13px;
  white-space: nowrap;
  /deep/ .anticon {
    margin: 0 4px 0 0;
    font-size: 16px;
  }
  /deep/ span {
    font-size: 13px;
  }
}
</style>
