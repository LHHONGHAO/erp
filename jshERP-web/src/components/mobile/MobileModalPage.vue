<template>
  <div v-if="visible" class="mmp-page">
    <!-- 标准顶栏：蓝底 49px、标题左对齐、副标题同行、右侧关闭固定 -->
    <div class="mmp-header">
      <div class="mmp-title-block">
        <div class="mmp-title">{{ title }}</div>
        <div v-if="subtitle" class="mmp-subtitle">{{ subtitle }}</div>
      </div>
      <div v-if="$slots.actions" class="mmp-tools">
        <slot name="actions"></slot>
      </div>
      <div class="mmp-close" @click="$emit('close')">
        <a-icon type="close" title="关闭" />
      </div>
    </div>

    <!-- 内容区 -->
    <div class="mmp-body">
      <slot></slot>
    </div>

    <!-- 底部操作区 -->
    <div v-if="$slots.footer" class="mmp-footer">
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<script>
/**
 * 手机端标准页面弹层
 * 全屏浮层 + 标准顶栏（蓝底 49px、标题左对齐、副标题同行、右侧关闭固定），
 * 底栏白底，最小高度与标准底栏一致（58px）；内容与底部按钮通过插槽传入。
 * a-modal 形式的弹层请使用全局样式 .mm-sheet-modal + .mm-sheet-header（同一视觉规范）。
 */
export default {
  name: 'MobileModalPage',
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, default: '' },
    subtitle: { type: String, default: '' }
  }
}
</script>

<style>
/* 非 scoped：插槽内的图标由父组件渲染，scoped 选择器无法命中 */
.mmp-page {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  background: #f2f4f7;
  z-index: 2900;
}
.mmp-header {
  position: relative;
  flex: none;
  height: 49px;
  box-sizing: border-box;
  padding: 0 44px 0 58px;
  background: #1890ff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
}
.mmp-title-block {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  /* 标题左边固定 66px，与标准手机顶栏一致 */
  padding-left: 8px;
}
.mmp-title {
  flex: 0 1 auto;
  min-width: 0;
  max-width: 100%;
  font-size: 16px;
  font-weight: 500;
  line-height: 1.2;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mmp-subtitle {
  flex: 0 1 auto;
  min-width: 0;
  font-size: 12px;
  line-height: 1.2;
  color: rgba(255, 255, 255, 0.75);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mmp-tools {
  flex: none;
  display: flex;
  align-items: center;
  height: 49px;
}
.mmp-close {
  position: absolute;
  top: 0;
  right: 0;
  width: 44px;
  height: 49px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #fff;
  cursor: pointer;
  user-select: none;
}
.mmp-close:active {
  background: rgba(255, 255, 255, 0.2);
}
.mmp-icon,
.mmp-tools .anticon {
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  line-height: 1;
  color: #fff;
  cursor: pointer;
}
.mmp-icon:active,
.mmp-tools .anticon:active {
  background: rgba(255, 255, 255, 0.2);
}
.mmp-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.mmp-footer {
  flex: none;
  min-height: 58px;
  box-sizing: border-box;
  background: #fff;
}
</style>
