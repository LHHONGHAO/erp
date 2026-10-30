<template>
  <!-- 固定高度容器，内容超出时页面内滚动（与商品类别等列表页一致的显示方式） -->
  <div class="sale-order-page" :style="cardStyle">
    <!-- 页面头部：标题 + 操作按钮，滚动时固定在顶部 -->
    <div class="so-header">
      <span class="so-title">销售开单</span>
      <div class="so-actions">
        <a-button icon="import" @click="handleImport">导入明细</a-button>
        <a-button icon="setting" style="margin-left: 8px" @click="handleColumnSetting">列设置</a-button>
        <a-button style="margin-left: 8px" @click="handleReset">重置</a-button>
        <a-button type="primary" style="margin-left: 8px" @click="handleOk">保存（Ctrl+S）</a-button>
      </div>
    </div>
    <!-- 开单表单直接铺在页面中，非弹窗 -->
    <div class="so-body">
      <retail-out-modal ref="form" inline @close="onClose" @ok="onSaved" />
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
        // 容器高度，页面内容超出时出现滚动条
        cardStyle: ''
      }
    },
    mounted () {
      this.setHeight()
      window.addEventListener('resize', this.setHeight)
      // 进入页面即初始化新增单据表单
      this.$nextTick(() => {
        this.openForm()
      })
    },
    beforeDestroy () {
      window.removeEventListener('resize', this.setHeight)
    },
    methods: {
      // 按窗口高度计算内容区高度（与商品信息列表页保持一致）
      setHeight () {
        this.cardStyle = 'height:' + (document.documentElement.clientHeight - 100) + 'px'
      },
      // 初始化新增单据表单
      openForm () {
        const form = this.$refs.form
        if (!form) return
        form.action = 'add'
        form.title = '销售开单'
        form.disableSubmit = false
        form.add()
      },
      // 保存
      handleOk () {
        const form = this.$refs.form
        if (!form) return
        form.handleOkOnly()
      },
      // 表单关闭（取消或保存成功后）重新初始化为空白单据，便于连续开单
      onClose () {
        this.openForm()
      },
      // 保存成功后给出提示（内嵌页面没有列表页的提示，需在此显示）
      onSaved () {
        this.$message.success('保存成功')
      },
      // 重置单据
      handleReset () {
        const form = this.$refs.form
        if (!form) return
        form.close()
      },
      // 导入明细（调用单据表单内的导入弹窗）
      handleImport () {
        const form = this.$refs.form
        if (form && typeof form.onImport === 'function') {
          form.onImport(form.prefixNo)
        }
      },
      // 打开明细表的列设置弹窗
      handleColumnSetting () {
        const form = this.$refs.form
        const table = form && form.$refs.materialDataTable
        if (table && typeof table.openColumnSetting === 'function') {
          table.openColumnSetting()
        }
      }
    }
  }
</script>

<style scoped>
  .sale-order-page {
    background: #fff;
    overflow: auto;
    padding: 0 12px 40px;
  }
  .so-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 0;
    margin-bottom: 8px;
    border-bottom: 1px solid #e8e8e8;
    background: #fff;
    position: sticky;
    top: 0;
    z-index: 2;
  }
  .so-title {
    font-size: 16px;
    font-weight: 600;
    color: rgba(0, 0, 0, 0.85);
  }
  .so-body {
    padding-bottom: 24px;
  }
</style>
