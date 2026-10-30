<template>
  <div class="retail-sale-order-desktop">
    <!-- 复用零售出库的新增单据表单，全屏展示 -->
    <retail-out-modal ref="form" @ok="onOk" @close="onClose" />
    <!-- 表单关闭后展示入口按钮，避免页面空白，方便连续开单 -->
    <div v-if="showCreateBtn" class="rso-desktop-empty">
      <a-button type="primary" size="large" icon="plus" @click="openForm">新建销售开单</a-button>
    </div>
  </div>
</template>

<script>
  import RetailOutModal from './modules/RetailOutModal'

  export default {
    name: 'RetailSaleOrderDesktop',
    components: {
      RetailOutModal
    },
    data () {
      return {
        // 是否展示“新建销售开单”按钮
        showCreateBtn: false
      }
    },
    mounted () {
      // 进入页面后自动打开新增表单
      this.$nextTick(() => {
        this.openForm()
      })
    },
    methods: {
      // 打开新增单据表单
      openForm () {
        this.showCreateBtn = false
        this.$nextTick(() => {
          const form = this.$refs.form
          if (!form) return
          form.action = 'add'
          form.title = '销售开单'
          form.disableSubmit = false
          form.add()
        })
      },
      // 保存成功后回到按钮状态，便于连续开单
      onOk () {
        this.showCreateBtn = true
      },
      // 取消/关闭后回到按钮状态，避免页面空白
      onClose () {
        this.showCreateBtn = true
      }
    }
  }
</script>

<style scoped>
  .retail-sale-order-desktop {
    min-height: calc(100vh - 120px);
    background: #fff;
  }
  .rso-desktop-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 60vh;
  }
</style>
