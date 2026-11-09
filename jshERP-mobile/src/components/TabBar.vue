<template>
  <div class="tab-bar">
    <div
      v-for="tab in tabs"
      :key="tab.path"
      class="tab-bar__item"
      :class="{ 'is-active': active === tab.path }"
      @click="go(tab.path)">
      <svg class="tab-bar__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
        <path v-for="(d, i) in tab.icon" :key="i" :d="d" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      <span class="tab-bar__text">{{ tab.text }}</span>
    </div>
  </div>
</template>

<script>
const ICONS = {
  home: ['M3 10.5 12 3l9 7.5', 'M5.5 9.5V21h13V9.5'],
  mine: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z', 'M4.5 21a7.5 7.5 0 0 1 15 0']
}

export default {
  name: 'MobileTabBar',
  data () {
    return {
      tabs: [
        { path: '/home', text: '首页', icon: ICONS.home },
        { path: '/mine', text: '我的', icon: ICONS.mine }
      ]
    }
  },
  computed: {
    active () {
      return this.$route.path
    }
  },
  methods: {
    go (path) {
      if (this.$route.path !== path) {
        this.$router.push(path)
      }
    }
  }
}
</script>

<style scoped>
  .tab-bar {
    flex: 0 0 auto;
    display: flex;
    height: calc(52px + env(safe-area-inset-bottom));
    padding-bottom: env(safe-area-inset-bottom);
    background: #fff;
    box-shadow: 0 -1px 0 #eceef1;
  }

  .tab-bar__item {
    flex: 1 1 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    color: #8b949e;
    font-size: 11px;
    cursor: pointer;
  }

  .tab-bar__item.is-active {
    color: #1890ff;
  }

  .tab-bar__icon {
    width: 22px;
    height: 22px;
  }
</style>
