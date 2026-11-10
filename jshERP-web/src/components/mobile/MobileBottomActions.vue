<template>
  <div class="mobile-bottom-actions" :class="{ 'is-single': buttons.length === 1 }">
    <button v-for="btn in buttons" :key="btn.key" type="button" class="mba-btn" @click="$emit('click', btn.key)">
      {{ btn.text }}
    </button>
  </div>
</template>

<script>
/**
 * 手机端标准底部操作按钮栏
 * 固定在标准底栏（58px）正上方，蓝色胶囊按钮等宽并排，供各业务页面复用
 */
export default {
  name: 'MobileBottomActions',
  props: {
    // [{ key: 'select', text: '选择添加' }]，数组顺序即按钮从左到右顺序
    buttons: { type: Array, default: () => [] }
  }
}
</script>

<style scoped>
.mobile-bottom-actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(58px + env(safe-area-inset-bottom));
  z-index: 99;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 16px;
  background: #f5f5f5;
}
.mobile-bottom-actions.is-single {
  justify-content: center;
}
.mobile-bottom-actions.is-single .mba-btn {
  flex: 0 0 calc(50% - 8px);
}
.mba-btn {
  flex: 1;
  height: 44px;
  padding: 0;
  border: none;
  border-radius: 22px;
  background: #1890ff;
  color: #fff;
  font-size: 16px;
  font-weight: 500;
  line-height: 44px;
  text-align: center;
  cursor: pointer;
  outline: none;
}
.mba-btn:active {
  background: #096dd9;
}
</style>
