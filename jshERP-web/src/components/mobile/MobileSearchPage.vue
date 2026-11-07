<template>
  <div class="mobile-search-page">
    <!-- 标准顶栏（49px）：返回 + 标题 + 右侧操作 -->
    <mobile-top-bar :title="title" :show-back="showBack" @back="$emit('back')">
      <template #actions>
        <slot name="actions"></slot>
      </template>
    </mobile-top-bar>

    <!-- 信息列表区：滚动，底部预留标准底边栏高度 -->
    <div class="msp-body">
      <slot></slot>
    </div>

    <!-- 标准底边栏：扫码 + 搜索框 + 拍照 + 搜索 + 筛选 -->
    <mobile-bottom-bar left-auto>
      <div class="msp-search-bar">
        <div class="msp-search-box">
          <a-icon v-if="showScan" type="scan" class="msp-scan" @click="$emit('scan')" />
          <a-input
            :placeholder="placeholder"
            :value="value"
            class="msp-input"
            @change="handleInput"
            @pressEnter="$emit('search', value)"
          />
          <span v-if="showCamera" class="msp-camera" @click="$emit('camera')">
            <a-icon type="camera" />
            <span class="msp-ai-tag">AI</span>
          </span>
        </div>
        <a-button type="primary" class="msp-search-btn" @click="$emit('search', value)">{{ searchText }}</a-button>
      </div>
      <a-button v-if="showFilter" class="msp-filter-btn" icon="filter" @click="$emit('filter')">{{ filterText }}</a-button>
    </mobile-bottom-bar>

    <!-- 浮层（抽屉、扫码全屏、弹窗等） -->
    <slot name="overlay"></slot>
  </div>
</template>

<script>
import MobileTopBar from './MobileTopBar'
import MobileBottomBar from './MobileBottomBar'

/**
 * 手机端标准「信息搜索页面」模板
 * 顶栏（返回/标题/操作）+ 可滚动信息列表 + 底部搜索栏（扫码/输入/拍照/搜索/筛选）
 */
export default {
  name: 'MobileSearchPage',
  components: { MobileTopBar, MobileBottomBar },
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
    showBack: {
      type: Boolean,
      default: true
    },
    showScan: {
      type: Boolean,
      default: true
    },
    showCamera: {
      type: Boolean,
      default: true
    },
    showFilter: {
      type: Boolean,
      default: true
    },
    searchText: {
      type: String,
      default: '搜索'
    },
    filterText: {
      type: String,
      default: '筛选'
    }
  },
  methods: {
    handleInput(e) {
      this.$emit('input', e.target.value)
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
/* 底边栏搜索区：白色胶囊输入框，扫码/拍照位于输入框两侧 */
.msp-search-bar {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}
.msp-search-box {
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
.msp-scan {
  flex-shrink: 0;
  margin-right: 8px;
  font-size: 19px;
  color: #999;
  cursor: pointer;
}
.msp-input {
  flex: 1;
  min-width: 0;
  margin: 0;
  background: transparent;
  border: none;
  box-shadow: none;
  font-size: 14px;
  color: #333;
  height: auto;
  border-radius: 0;
  &::placeholder {
    color: #b7b7b7;
  }
  &:focus,
  &.ant-input:focus {
    background: transparent;
    border: none;
    box-shadow: none;
  }
}
.msp-camera {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-left: 4px;
  color: #999;
  cursor: pointer;
  flex-shrink: 0;
  .anticon {
    font-size: 18px;
  }
}
.msp-ai-tag {
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
/* 搜索按钮：位于搜索框与筛选按钮之间 */
.msp-search-btn {
  flex-shrink: 0;
  height: 42px;
  margin-left: 8px;
  padding: 0 14px;
  border-radius: 8px;
  font-size: 15px;
}
/* 筛选按钮：正方形，上图标下文字 */
.msp-filter-btn {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  margin-left: 8px;
  margin-right: -8px;
  padding: 0;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1;
  border-radius: 8px;
  .anticon {
    margin: 0 0 3px 0;
    font-size: 20px;
  }
  .anticon + span {
    margin-left: 0;
    font-size: 10px;
  }
}
</style>
