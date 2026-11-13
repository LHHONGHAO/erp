<template>
  <div class="mobile-search-page">
    <!-- 标准顶栏（49px）：返回 + 标题 + 右侧操作 -->
    <mobile-top-bar :title="title" @back="$emit('back')">
      <template #actions>
        <slot name="actions"></slot>
      </template>
    </mobile-top-bar>

    <!-- 信息列表区：滚动，底部预留标准底边栏高度 -->
    <div class="msp-body">
      <slot></slot>
    </div>

    <!-- 标准底边栏：拍照 + 搜索框 + 扫码 + 筛选（复用标准搜索底栏组件） -->
    <mobile-bottom-bar left-auto>
      <mobile-search-bar
        ref="searchBar"
        :value="value"
        :placeholder="placeholder"
        :auto-scan-close="autoScanClose"
        @input="$emit('input', $event)"
        @search="$emit('search', $event)"
        @camera="$emit('camera')"
        @filter="$emit('filter')"
        @scan-result="$emit('scan-result', $event)" />
    </mobile-bottom-bar>

    <!-- 浮层（抽屉、扫码全屏、弹窗等） -->
    <slot name="overlay"></slot>
  </div>
</template>

<script>
import MobileTopBar from './MobileTopBar'
import MobileBottomBar from './MobileBottomBar'
import MobileSearchBar from './MobileSearchBar'

/**
 * 手机端标准「信息搜索页面」模板
 * 顶栏（返回/标题/操作）+ 可滚动信息列表 + 底部搜索栏（拍照/输入/扫码/筛选）
 */
export default {
  name: 'MobileSearchPage',
  components: { MobileTopBar, MobileBottomBar, MobileSearchBar },
  props: {
    title: {
      type: String,
      default: ''
    },
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
  methods: {
    // 供调用页主动关闭标准扫码页
    closeScanPage() {
      if (this.$refs.searchBar) this.$refs.searchBar.closeScanPage()
    },
    // 供调用页在扫码页内显示提示（如未命中商品）
    setScanTip(text, isError) {
      if (this.$refs.searchBar) this.$refs.searchBar.setScanTip(text, isError)
    }
  }
}
</script>

<style lang="less" scoped>
.mobile-search-page {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  background: #f5f6f7;
  overflow: hidden;
  z-index: 10;
}
/* 信息列表区 */
.msp-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  padding-bottom: calc(66px + env(safe-area-inset-bottom));
  box-sizing: border-box;
  -webkit-overflow-scrolling: touch;
}
</style>
