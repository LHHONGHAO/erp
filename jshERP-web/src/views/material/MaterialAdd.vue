<template>
  <!-- 固定高度容器，内容超出时页面内滚动 -->
  <div class="material-add-page" :style="cardStyle">
    <!-- 页面头部：标题 + 操作按钮，滚动时固定在顶部 -->
    <div class="ma-header">
      <span class="ma-title">新增商品</span>
      <div class="ma-actions">
        <a-button icon="setting" @click="handlePropertyConfig">属性设置</a-button>
        <a-button style="margin-left: 8px" @click="handleReset">重置</a-button>
        <a-button type="primary" style="margin-left: 8px" @click="handleOk">保存（Ctrl+S）</a-button>
      </div>
    </div>
    <!-- 表单直接铺在页面中，非弹窗 -->
    <div class="ma-body">
      <material-modal ref="form" inline @close="onClose" @ok="onSaved" />
    </div>
  </div>
</template>

<script>
  import MaterialModal from './modules/MaterialModal'

  export default {
    name: 'MaterialAdd',
    components: {
      MaterialModal
    },
    data () {
      return {
        // 容器高度，页面内容超出时出现纵向滚动条
        cardStyle: ''
      }
    },
    mounted () {
      this.setHeight()
      window.addEventListener('resize', this.setHeight)
      // 进入页面后初始化新增表单
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
      // 初始化新增商品表单
      openForm () {
        const form = this.$refs.form
        if (!form) return
        form.action = 'add'
        form.disableSubmit = false
        form.showOkFlag = true
        form.add()
      },
      // 保存
      handleOk () {
        const form = this.$refs.form
        if (!form) return
        form.handleOk()
      },
      // 保存成功后给出提示并重置为空白表单，便于连续录入
      onSaved () {
        this.$message.success('保存成功')
      },
      // 表单关闭（取消或保存成功后）重新初始化为空白表单，便于连续录入
      onClose () {
        this.openForm()
      },
      // 打开基础属性设置弹窗
      handlePropertyConfig () {
        const form = this.$refs.form
        if (!form) return
        form.openPropertyConfig()
      },
      // 重置表单
      handleReset () {
        const form = this.$refs.form
        if (!form) return
        form.close()
      }
    }
  }
</script>

<style scoped>
  .material-add-page {
    background: #fff;
    overflow-y: auto;
    overflow-x: hidden;
    padding: 0 12px 40px;
  }
  .ma-header {
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
  .ma-title {
    font-size: 16px;
    font-weight: 600;
    color: rgba(0, 0, 0, 0.85);
  }
  .ma-body {
    padding-bottom: 24px;
  }
</style>
