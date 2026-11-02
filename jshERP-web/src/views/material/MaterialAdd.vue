<template>
  <!-- 固定高度容器，内容超出时页面内滚动 -->
  <div class="material-add-page" :style="cardStyle">
    <!-- 桌面端页头：操作按钮一行并列，滚动时固定在顶部；手机端由全局蓝底顶栏承载（返回+标题+顶部按钮） -->
    <div class="ma-header" v-if="device !== 'mobile'">
      <a-button icon="left" class="ma-back-btn" @click="handleBack">返回</a-button>
      <div class="ma-actions">
        <a-button icon="setting" @click="handlePropertyConfig">属性设置</a-button>
        <a-button @click="handleReset">重置</a-button>
        <a-button type="primary" @click="handleOk">保存</a-button>
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
  import { mixinDevice } from '@/utils/mixin.js'
  import { setMobileHeaderActions, clearMobileHeaderActions } from '@/utils/mobilePageHeader'

  export default {
    name: 'MaterialAdd',
    components: {
      MaterialModal
    },
    mixins: [mixinDevice],
    data () {
      return {
        // 容器高度，页面内容超出时出现纵向滚动条
        cardStyle: '',
        // 是否为编辑模式
        isEdit: false
      }
    },
    mounted () {
      this.setHeight()
      window.addEventListener('resize', this.setHeight)
      this.registerHeaderActions()
      this.$nextTick(() => {
        this.loadRecordFromStorage()
      })
    },
    // keep-alive 激活时重新读取 sessionStorage 中的编辑数据，避免残留上一次编辑的商品
    activated () {
      this.registerHeaderActions()
      this.loadRecordFromStorage()
    },
    deactivated () {
      clearMobileHeaderActions()
    },
    beforeDestroy () {
      window.removeEventListener('resize', this.setHeight)
      clearMobileHeaderActions()
    },
    methods: {
      // 注册手机端顶部按钮：设置（属性设置）、重置、保存
      registerHeaderActions () {
        setMobileHeaderActions([
          { key: 'setting', icon: 'setting', handler: this.handlePropertyConfig },
          { key: 'reset', icon: 'reload', handler: this.handleReset },
          { key: 'save', text: '保存', type: 'primary', handler: this.handleOk }
        ])
      },
      // 从 sessionStorage 读取商品数据并加载到表单（编辑模式），否则打开新增表单
      loadRecordFromStorage () {
        const recordStr = sessionStorage.getItem('materialEditRecord')
        if (recordStr) {
          try {
            const record = JSON.parse(recordStr)
            sessionStorage.removeItem('materialEditRecord')
            this.isEdit = true
            this.openEditForm(record)
          } catch (e) {
            this.openForm()
          }
        } else {
          this.openForm()
        }
      },
      // 按窗口高度计算内容区高度（仅桌面端固定高度；手机端改为页面自然滚动，避免底部内容被裁剪）
      setHeight () {
        if (document.documentElement.clientWidth <= 768) {
          this.cardStyle = ''
          return
        }
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
      // 编辑模式：加载商品数据及其所有SKU
      openEditForm (record) {
        const form = this.$refs.form
        if (!form) return
        form.action = 'edit'
        form.disableSubmit = false
        form.showOkFlag = true
        form.edit(record)
      },
      // 返回加载编辑页面的页面（商品列表）
      handleBack () {
        const from = sessionStorage.getItem('materialEditFrom')
        if (from) {
          sessionStorage.removeItem('materialEditFrom')
          this.$router.push(from)
        } else {
          this.$router.back()
        }
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
    justify-content: flex-start;
    padding: 12px 0;
    margin-bottom: 8px;
    border-bottom: 1px solid #e8e8e8;
    background: #fff;
    position: sticky;
    top: 0;
    z-index: 2;
    gap: 10px;
  }
  .ma-back-btn {
    flex-shrink: 0;
  }
  .ma-actions {
    display: flex;
    align-items: center;
    flex-wrap: nowrap;
    gap: 8px;
    margin-left: auto;
  }
  .ma-body {
    padding-bottom: 24px;
  }
  /* 手机端：不限制容器高度，内容随页面整体滚动到底部 */
  @media (max-width: 768px) {
    .material-add-page {
      height: auto !important;
      overflow: visible;
      padding-bottom: calc(96px + env(safe-area-inset-bottom));
    }
  }
</style>
