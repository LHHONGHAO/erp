<template>
  <div class="mobile-search-page">
    <!-- 标准顶栏（49px）：返回 + 标题 + 右侧操作 -->
    <mobile-top-bar :title="title" @back="$emit('back')">
      <template #actions>
        <slot name="actions"></slot>
      </template>
    </mobile-top-bar>

    <!-- 统一搜索布局：内容区 + 悬浮搜索胶囊 + 右侧悬浮筛选面板 -->
    <mobile-search-layout
      ref="layout"
      :value="value"
      :placeholder="placeholder"
      :auto-scan-close="autoScanClose"
      :filter-visible="filterVisible"
      :filter-groups="filterGroups"
      :left-hand="leftHand"
      :show-hand-switch="showHandSwitch"
      :scroll-body="scrollBody"
      @input="$emit('input', $event)"
      @search="$emit('search', $event)"
      @camera="$emit('camera')"
      @scan-result="$emit('scan-result', $event)"
      @filter-change="onFilterChange"
      @filter-clear="$emit('filter-clear')"
      @update:filterVisible="$emit('update:filterVisible', $event)"
      @update:leftHand="$emit('update:leftHand', $event)">
      <!-- 信息列表区 -->
      <slot></slot>
      <template #filter-extra>
        <slot name="filter-extra"></slot>
      </template>
      <template #float-extra>
        <slot name="float-extra"></slot>
      </template>
      <!-- 浮层（扫码全屏、弹窗等） -->
      <template #sheet>
        <slot name="overlay"></slot>
      </template>
    </mobile-search-layout>
  </div>
</template>

<script>
import MobileTopBar from './MobileTopBar'
import MobileSearchLayout from './MobileSearchLayout'

/**
 * 手机端标准「信息搜索页面」模板
 * 顶栏（返回/标题/操作）+ 统一搜索布局（内容区 + 悬浮搜索胶囊 + 右侧悬浮筛选面板）。
 * 筛选条件通过 filterGroups 配置标签组，复杂表单放 #filter-extra 插槽。
 */
export default {
  name: 'MobileSearchPage',
  components: { MobileTopBar, MobileSearchLayout },
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
    },
    /** 筛选面板是否可见，支持 .sync */
    filterVisible: {
      type: Boolean,
      default: false
    },
    /** 筛选标签组：[{ key, label, value, options: [{ text, value }], emptyText }] */
    filterGroups: {
      type: Array,
      default: () => []
    },
    /** 左右手布局，支持 .sync */
    leftHand: {
      type: Boolean,
      default: false
    },
    /** 是否显示筛选面板底部的左右手切换 */
    showHandSwitch: {
      type: Boolean,
      default: false
    },
    /** 内容区是否由布局滚动（卡片列表页面用；商品流自带滚动时保持 false） */
    scrollBody: {
      type: Boolean,
      default: false
    }
  },
  methods: {
    // 供调用页主动关闭标准扫码页
    closeScanPage() {
      if (this.$refs.layout) this.$refs.layout.closeScanPage()
    },
    // 供调用页在扫码页内显示提示（如未命中商品）
    setScanTip(text, isError) {
      if (this.$refs.layout) this.$refs.layout.setScanTip(text, isError)
    },
    // 筛选标签点击：透传给调用页处理
    onFilterChange(key, opt) {
      this.$emit('filter-change', key, opt)
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
  /* 页面底色纯白（商品卡片已无底色，靠间距分隔） */
  background: #fff;
  overflow: hidden;
  z-index: 10;
}
</style>
