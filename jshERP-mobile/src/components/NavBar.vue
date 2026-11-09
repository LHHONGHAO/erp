<template>
  <div class="nav-bar">
    <div class="nav-bar__side">
      <slot name="left" />
    </div>
    <div class="nav-bar__title">{{ title }}</div>
    <div class="nav-bar__side nav-bar__side--right">
      <slot name="right">
        <div v-if="showAvatar" class="nav-bar__avatar" role="button" aria-label="个人中心" @click="$emit('avatar')">{{ avatarText }}</div>
      </slot>
    </div>
  </div>
</template>

<script>
export default {
  name: 'MobileNavBar',
  props: {
    title: { type: String, default: '' },
    showAvatar: { type: Boolean, default: false },
    avatarText: { type: String, default: '我' }
  }
}
</script>

<style scoped>
  .nav-bar {
    position: sticky;
    top: 0;
    z-index: 10;
    display: flex;
    align-items: center;
    height: calc(48px + env(safe-area-inset-top));
    padding: env(safe-area-inset-top) 12px 0;
    background: linear-gradient(135deg, #1890ff, #0d6fd8);
    color: #fff;
  }

  .nav-bar__side {
    flex: 0 0 44px;
    display: flex;
    align-items: center;
  }

  .nav-bar__side--right {
    justify-content: flex-end;
  }

  .nav-bar__title {
    flex: 1 1 auto;
    min-width: 0;
    font-size: 16px;
    font-weight: 600;
    text-align: center;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .nav-bar__avatar {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.22);
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
  }
</style>
