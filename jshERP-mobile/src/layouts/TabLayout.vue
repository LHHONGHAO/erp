<template>
  <div class="tab-layout">
    <div class="tab-layout__body">
      <keep-alive>
        <router-view />
      </keep-alive>
    </div>
    <TabBar v-if="!$route.meta.hideTabBar" />
  </div>
</template>

<script>
import TabBar from '../components/TabBar.vue'

export default {
  name: 'MobileTabLayout',
  components: { TabBar }
}
</script>

<style scoped>
  .tab-layout {
    display: flex;
    flex-direction: column;
    height: 100%;
  }

  .tab-layout__body {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
  }
</style>
