<template>
  <div class="mobile-app">
    <router-view />
  </div>
</template>

<script>
export default {
  name: 'MobileApp'
}
</script>

<style>
  * {
    box-sizing: border-box;
  }

  html,
  body,
  #app {
    height: 100%;
    margin: 0;
    padding: 0;
  }

  body {
    font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', 'Microsoft YaHei', sans-serif;
    font-size: 14px;
    color: #1f2329;
    background: #f5f6f8;
    -webkit-font-smoothing: antialiased;
    -webkit-tap-highlight-color: transparent;
    overscroll-behavior-y: contain;
  }

  input,
  button,
  textarea {
    font-family: inherit;
  }

  a {
    color: #1890ff;
    text-decoration: none;
  }

  .mobile-app {
    height: 100%;
  }
</style>
