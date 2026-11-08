<template>
  <div class="mobile-top-bar">
    <div v-if="showBack" class="mtb-back" @click="$emit('back')">
      <a-icon type="arrow-left" />
    </div>
    <div class="mtb-title">
      <slot name="title">{{ title }}</slot>
    </div>
    <div class="mtb-actions">
      <slot name="actions"></slot>
    </div>
  </div>
</template>

<script>
/**
 * 手机端标准顶栏：与全局 GlobalHeader 手机端保持一致
 * 49px 蓝底；返回图标 22px（44×49 点击区）；标题 16px/400 白色左对齐
 */
export default {
  name: 'MobileTopBar',
  props: {
    title: {
      type: String,
      default: ''
    },
    showBack: {
      type: Boolean,
      default: true
    }
  }
}
</script>

<style lang="less" scoped>
.mobile-top-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  height: 49px;
  padding: 0 8px 0 0;
  box-sizing: border-box;
  background: #1890ff;
  color: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  /* 返回按钮：与底边栏等高（58px）的正方形，左边贴屏幕左边 */
  .mtb-back {
    flex: 0 0 auto;
    width: 58px;
    height: 58px;
    margin-left: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    color: #fff;
    cursor: pointer;
    user-select: none;
    &:active {
      background: rgba(255, 255, 255, 0.2);
    }
  }
  .mtb-title {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    align-items: center;
    justify-content: flex-start;
    /* 标题左对齐：起点与商品信息页标题一致（返回按钮 58px 之后留 4px） */
    padding-left: 4px;
    text-align: left;
    font-size: 16px;
    font-weight: 500;
    color: #fff;
    line-height: 1.2;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .mtb-actions {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    height: 49px;
    margin-left: auto;
    /deep/ .mtb-action {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      height: 100%;
      padding: 0 12px;
      font-size: 15px;
      color: #fff;
      cursor: pointer;
      user-select: none;
      &:active {
        background: rgba(255, 255, 255, 0.2);
      }
    }
  }
}
</style>
