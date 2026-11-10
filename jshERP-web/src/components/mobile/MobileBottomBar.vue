<template>
  <div class="mobile-bottom-bar" :class="{ 'mbb-left-auto': leftAuto, 'mbb-static': !fixed }">
    <div class="mbb-left">
      <slot name="left"></slot>
    </div>
    <slot></slot>
    <div class="mbb-right">
      <slot name="right"></slot>
    </div>
  </div>
</template>

<script>
/**
 * 手机端统一底边栏：高度与开单收银页一致（内容 42px + 上下内边距各 8px = 58px）
 * 左侧放「标题 + 数据」指标，中间放悬浮入口（绝对定位），右侧放主操作按钮
 */
export default {
  name: 'MobileBottomBar',
  props: {
    /** 左侧内容按自身宽度排布，不占据剩余空间（如只有一个筛选按钮时使用） */
    leftAuto: {
      type: Boolean,
      default: false
    },
    /** 是否固定在视口底部；弹层内使用时传 false，跟随弹层布局 */
    fixed: {
      type: Boolean,
      default: true
    }
  }
}
</script>

<style lang="less" scoped>
.mobile-bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  height: calc(58px + env(safe-area-inset-bottom));
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
  background: #fff;
  // 分隔线用内阴影，避免边框占用高度导致中间悬浮按钮无法与底栏等高
  box-shadow: inset 0 1px 0 #eee, 0 -2px 8px rgba(0, 0, 0, 0.06);
  .mbb-left {
    flex: 1 1 auto;
    display: flex;
    align-items: center;
    gap: 18px;
    min-width: 0;
  }
  /* 左侧不占剩余空间，把宽度让给中间搜索区 */
  &.mbb-left-auto .mbb-left {
    flex: 0 0 auto;
  }
  /* 弹层内使用：不脱离文档流 */
  &.mbb-static {
    position: relative;
    left: auto;
    right: auto;
    bottom: auto;
    z-index: 1;
  }
  .mbb-right {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    align-self: stretch;
    margin-left: auto;
    padding-left: 8px;
    /deep/ .ant-btn {
      height: 100%;
      min-height: 40px;
      padding: 0 18px;
      font-size: 15px;
      font-weight: 600;
    }
  }
}
</style>
