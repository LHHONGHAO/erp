<template>
  <!-- 手机端标准搜索布局：内容区 + 悬浮搜索胶囊 + 右侧悬浮筛选面板 + 底部弹层 -->
  <div class="msearch-layout" :class="{ 'is-left-hand': leftHand, 'has-scroll-body': scrollBody }">
    <!-- 内容区：商品流（自带滚动）或卡片列表（scrollBody 时由本区滚动） -->
    <div class="msearch-layout__body">
      <slot></slot>
    </div>

    <!-- 右侧悬浮筛选面板：配置驱动标签组 + 自定义表单项 -->
    <div v-if="filterVisible" class="msearch-filter">
      <div class="msearch-filter__head">
        <span class="msearch-filter__title">筛选</span>
        <a class="msearch-filter__close" @click="$emit('filter-clear')">全部关闭</a>
      </div>
      <div class="msearch-filter__body">
        <div v-for="group in filterGroups" :key="group.key" class="msearch-filter__item">
          <span class="msearch-filter__label">{{ group.label }}</span>
          <div class="msearch-chips">
            <span
              v-for="opt in (group.options || [])"
              :key="opt.value"
              class="msearch-chip"
              :class="{ 'is-active': isActive(group, opt) }"
              @click="$emit('filter-change', group.key, opt)">{{ opt.text }}</span>
            <span v-if="!(group.options || []).length" class="msearch-chips__empty">{{ group.emptyText || '暂无数据' }}</span>
          </div>
        </div>
        <!-- 自定义筛选表单（如下拉、输入框） -->
        <slot name="filter-extra"></slot>
      </div>
      <!-- 左右手切换：左手时整页布局水平镜像 -->
      <div v-if="showHandSwitch" class="msearch-filter__foot">
        <span class="msearch-filter__foot-label">布局</span>
        <div class="msearch-hand-switch">
          <span
            class="msearch-hand-switch__item"
            :class="{ 'is-active': leftHand }"
            @click="$emit('update:leftHand', true)">左手</span>
          <span
            class="msearch-hand-switch__item"
            :class="{ 'is-active': !leftHand }"
            @click="$emit('update:leftHand', false)">右手</span>
        </div>
      </div>
    </div>

    <!-- 悬浮搜索胶囊：搜索 / 扫码 / 拍照 / 筛选，整体浮在内容之上 -->
    <div class="msearch-floatbar">
      <mobile-search-bar
        ref="searchBar"
        :value="value"
        :placeholder="placeholder"
        :auto-scan-close="autoScanClose"
        :show-filter="showFilter"
        @input="$emit('input', $event)"
        @search="$emit('search', $event)"
        @camera="$emit('camera')"
        @filter="$emit('update:filterVisible', !filterVisible)"
        @scan-result="$emit('scan-result', $event)">
        <!-- 追加操作置于筛选按钮左侧，与筛选按钮互换位置 -->
        <template #before-filter>
          <slot name="float-extra"></slot>
        </template>
      </mobile-search-bar>
    </div>

    <!-- 底部弹层（如已选商品清单） -->
    <slot name="sheet"></slot>
  </div>
</template>

<script>
import MobileSearchBar from './MobileSearchBar'

/**
 * 手机端标准搜索布局
 * 1. 内容区插槽放商品流或卡片列表，底部预留悬浮胶囊高度；
 * 2. 悬浮搜索胶囊统一搜索 / 扫码 / 拍照 / 筛选入口，右侧可追加操作（float-extra）；
 * 3. 右侧悬浮筛选面板按 filterGroups 渲染标签组，可追加自定义表单（filter-extra）；
 * 4. 筛选面板显隐、左右手通过 .sync 与调用页双向绑定；
 * 5. 视觉样式统一由全局 common.less 的 msearch-* 提供，业务页面无需重复编写。
 */
export default {
  name: 'MobileSearchLayout',
  components: { MobileSearchBar },
  props: {
    /** 搜索关键字，支持 v-model */
    value: { type: String, default: '' },
    placeholder: { type: String, default: '请输入关键字' },
    /** 识别到内容后是否自动关闭扫码页，false 时由调用页决定关闭时机 */
    autoScanClose: { type: Boolean, default: true },
    /** 筛选面板是否可见，支持 .sync */
    filterVisible: { type: Boolean, default: false },
    /** 是否显示搜索胶囊上的筛选按钮（新版商品选择页改用左侧类目栏后隐藏） */
    showFilter: { type: Boolean, default: true },
    /** 筛选标签组：[{ key, label, value, options: [{ text, value }], emptyText }] */
    filterGroups: { type: Array, default: () => [] },
    /** 左右手布局，支持 .sync */
    leftHand: { type: Boolean, default: false },
    /** 是否显示面板底部的左右手切换 */
    showHandSwitch: { type: Boolean, default: false },
    /** 内容区是否由本组件滚动（卡片列表页面用；商品流自带滚动时保持 false） */
    scrollBody: { type: Boolean, default: false }
  },
  methods: {
    /** 标签是否选中：空值统一按空串比较 */
    isActive(group, opt) {
      var cur = (group.value === undefined || group.value === null) ? '' : String(group.value)
      var val = (opt.value === undefined || opt.value === null) ? '' : String(opt.value)
      return cur === val
    },
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
