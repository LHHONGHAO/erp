<template>
  <div class="web-view">
    <div class="web-view__bar">
      <button class="web-view__back" type="button" @click="goBack">‹</button>
      <span class="web-view__title">{{ title }}</span>
      <span class="web-view__placeholder"></span>
    </div>
    <iframe v-if="url" class="web-view__frame" :src="url" frameborder="0"></iframe>
  </div>
</template>

<script>
export default {
  name: 'MobileWebView',
  computed: {
    url () {
      return this.$route.query.url || ''
    },
    title () {
      return this.$route.query.title || '业务页面'
    }
  },
  methods: {
    goBack () {
      if (window.history.length > 1) {
        this.$router.back()
      } else {
        this.$router.replace('/home')
      }
    }
  }
}
</script>

<style scoped>
  .web-view {
    display: flex;
    flex-direction: column;
    height: 100%;
    background: #fff;
  }

  .web-view__bar {
    display: flex;
    align-items: center;
    flex: 0 0 44px;
    padding: 0 4px;
    background: #1677ff;
    color: #fff;
  }

  .web-view__back {
    width: 40px;
    height: 40px;
    border: 0;
    background: transparent;
    color: #fff;
    font-size: 26px;
    line-height: 1;
  }

  .web-view__title {
    flex: 1;
    text-align: center;
    font-size: 16px;
    font-weight: 600;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .web-view__placeholder {
    width: 40px;
  }

  .web-view__frame {
    flex: 1 1 auto;
    width: 100%;
    min-height: 0;
    border: 0;
  }
</style>
