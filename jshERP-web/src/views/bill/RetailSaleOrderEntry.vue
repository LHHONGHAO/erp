<template>
  <div class="retail-sale-order-entry">
    <!-- 手机端：保留原有扫码开单页面 -->
    <retail-sale-order-list v-if="isMobile" />
    <!-- 电脑端：直接打开销售开单新增表单 -->
    <retail-sale-order-desktop v-else />
  </div>
</template>

<script>
  import RetailSaleOrderList from './RetailSaleOrderList'
  import RetailSaleOrderDesktop from './RetailSaleOrderDesktop'

  export default {
    name: 'RetailSaleOrderEntry',
    components: {
      RetailSaleOrderList,
      RetailSaleOrderDesktop
    },
    data () {
      return {
        // 是否手机宽度（<=768px 视为手机端）
        isMobile: window.matchMedia('(max-width: 768px)').matches
      }
    },
    mounted () {
      this.updateDevice()
      window.addEventListener('resize', this.updateDevice)
    },
    beforeDestroy () {
      window.removeEventListener('resize', this.updateDevice)
    },
    methods: {
      // 根据屏幕宽度切换手机端/电脑端页面
      updateDevice () {
        this.isMobile = window.matchMedia('(max-width: 768px)').matches
      }
    }
  }
</script>
