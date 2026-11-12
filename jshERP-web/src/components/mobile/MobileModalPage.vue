<template>
  <div v-if="visible" class="mmp-page">
    <!-- 标准顶栏：白底、标题居中、右侧关闭（与全局 .mm-sheet-modal 同一规范） -->
    <div class="mmp-header">
      <a-icon v-if="showBack" class="mmp-icon mmp-back" type="arrow-left" @click="$emit('back')" />
      <div class="mmp-title-block">
        <div class="mmp-title">{{ title }}</div>
        <div v-if="subtitle" class="mmp-subtitle">{{ subtitle }}</div>
      </div>
      <div class="mmp-tools">
        <slot name="actions"></slot>
        <a-icon v-if="closable" class="mmp-icon" type="close" title="关闭" @click="$emit('close')" />
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
 * 全屏浮层 + 标准顶栏（白底、标题居中、右侧关闭），内容与底部按钮通过插槽传入。
 * a-modal 形式的弹层请使用全局样式 .mm-sheet-modal + .mm-sheet-header（同一视觉规范）。
 */
export default {
  name: 'MobileModalPage',
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, default: '' },
    subtitle: { type: String, default: '' },
    showBack: { type: Boolean, default: false },
    closable: { type: Boolean, default: true }
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
  height: 60px;
  padding: 0 4px;
  background: #fff;
  border-bottom: none;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
.mmp-title-block {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 56%;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.mmp-title {
  max-width: 100%;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.2;
  color: rgba(0, 0, 0, 0.85);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mmp-subtitle {
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.2;
  color: #999;
}
.mmp-back {
  position: absolute;
  left: 0;
  top: 0;
}
.mmp-tools {
  flex: none;
  display: flex;
  align-items: center;
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
  color: #c8c8c8;
  cursor: pointer;
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
}
</style>
