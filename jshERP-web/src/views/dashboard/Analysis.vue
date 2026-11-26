<template>
  <div>
    <component v-if="homeComp && isMobile()" :is="homeComp"></component>
    <div v-else-if="homeComp" style="margin: 12px 12px 0px;">
      <component :is="homeComp"></component>
    </div>
  </div>
</template>

<script>
  import { mixinDevice } from '@/utils/mixin.js'

  export default {
    name: "Analysis",
    mixins: [mixinDevice],
    data() {
      return {
        homeComp: null
      }
    },
    created() {
      var self = this
      // 按设备只加载一个首页实现：移动端不再下载/解析桌面端图表代码（含 viser/G2）
      if (this.isMobile()) {
        import(/* webpackChunkName: "dashboard-mobile" */ './AnalysisMobile').then(function (mod) {
          self.homeComp = mod.default
        })
      } else {
        import(/* webpackChunkName: "dashboard" */ './IndexChart').then(function (mod) {
          self.homeComp = mod.default
        })
      }
    }
  }
</script>
