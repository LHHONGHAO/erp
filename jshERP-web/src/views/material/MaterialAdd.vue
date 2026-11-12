<template>
  <!-- 固定高度容器，内容超出时页面内滚动 -->
  <div class="material-add-page" :style="cardStyle">
    <!-- 桌面端页头：操作按钮一行并列，滚动时固定在顶部；手机端由全局蓝底顶栏承载（返回+标题+顶部按钮） -->
    <div class="ma-header" v-if="device !== 'mobile'">
      <a-button icon="left" class="ma-back-btn" @click="handleBack">返回</a-button>
      <div class="ma-actions" v-if="!showCategoryGate">
        <a-button icon="setting" @click="handlePropertyConfig">属性设置</a-button>
        <a-button @click="handleReset">重置</a-button>
        <a-button type="primary" @click="handleOk">保存</a-button>
      </div>
    </div>
    <!-- 表单直接铺在页面中，非弹窗 -->
    <div class="ma-body">
      <!-- 新增商品入口：AI 添加 / 人工添加 -->
      <div v-if="showCategoryGate" class="ma-gate">
        <a-button type="primary" size="large" block icon="camera" class="ma-gate__btn" @click="startAiAdd">AI添加</a-button>
        <a-button size="large" block icon="edit" class="ma-gate__btn" @click="startManualAdd">人工添加</a-button>
      </div>
      <template v-else>
        <!-- AI添加：先添加商品图片、选择商品类别，再进入识别结果页 -->
        <div v-if="mode === 'ai' && !aiCategoryChosen" class="ma-ai-setup">
          <div class="ma-ai-setup__label">商品图片</div>
          <div class="ma-ai-images">
            <div v-for="(img, index) in aiImages" :key="img.id" class="ma-ai-image">
              <img :src="img.preview" />
              <span v-if="img.loading" class="ma-ai-image__mask">识别中</span>
              <a-icon type="close-circle" class="ma-ai-image__remove" @click="removeAiImage(index)" />
            </div>
            <div v-if="!aiImages.length" class="ma-ai-images__empty">请拍摄或从相册选择商品图片（可多张）</div>
          </div>
          <div class="ma-ai-image-actions">
            <a-button icon="camera" @click="triggerAiCamera">拍照</a-button>
            <a-button icon="picture" @click="triggerAiGallery">图片加载</a-button>
          </div>
          <div class="ma-ai-setup__label ma-ai-setup__label-gap">商品类别</div>
          <a-tree-select
            v-model="gateCategoryId"
            class="ma-gate__select"
            :dropdownStyle="{ maxHeight: '320px', overflow: 'auto' }"
            :treeData="gateTreeData"
            :treeDefaultExpandAll="true"
            allow-clear
            placeholder="请选择商品类别（须选二级类目）" />
          <a-button type="primary" size="large" block class="ma-ai-recognize" :disabled="!canAiRecognize" @click="startAiRecognize">识别</a-button>
          <input ref="aiCameraInput" type="file" accept="image/*" capture="environment" class="ma-ai-file" @change="onAiFilesChange" />
          <input ref="aiGalleryInput" type="file" accept="image/*" multiple class="ma-ai-file" @change="onAiFilesChange" />
        </div>
        <material-modal v-else ref="form" inline :aiMode="mode === 'ai'" @close="onClose" @ok="onSaved" />
      </template>
    </div>
    <!-- 类别新增弹层：手机端为全屏蓝底标准弹层，桌面端为居中弹窗 -->
    <material-category-modal ref="categoryModal" @ok="handleCategoryAdded" />
  </div>
</template>

<script>
  import MaterialModal from './modules/MaterialModal'
  import MaterialCategoryModal from './modules/MaterialCategoryModal'
  import { queryMaterialCategoryTreeList } from '@/api/api'
  import { mixinDevice } from '@/utils/mixin.js'
  import { setMobileHeaderActions, clearMobileHeaderActions, setMobileHeaderTitle, clearMobileHeaderTitle } from '@/utils/mobilePageHeader'
  import { axios as uploadHttp } from '@/utils/request'
  import { recognizeTextFromImage } from '@/views/material/utils/productTextScan'

  export default {
    name: 'MaterialAdd',
    components: {
      MaterialModal,
      MaterialCategoryModal
    },
    mixins: [mixinDevice],
    data () {
      return {
        // 容器高度，页面内容超出时出现纵向滚动条
        cardStyle: '',
        // 是否为编辑模式
        isEdit: false,
        // 新增商品时先选择 AI添加 / 人工添加
        showCategoryGate: true,
        // 新增入口：ai=AI添加，manual=人工添加
        mode: '',
        // AI添加：是否已选好商品类别
        aiCategoryChosen: false,
        // AI添加：已添加的商品图片（拍照/相册）
        aiImages: [],
        aiImageSeq: 0,
        gateCategoryId: undefined,
        gateTreeData: []
      }
    },
    mounted () {
      this.setHeight()
      window.addEventListener('resize', this.setHeight)
      // 录入页禁止下拉刷新，避免手势触发页面重新加载
      document.body.classList.add('mm-no-pull-refresh')
      this.$nextTick(() => {
        this.initPage()
      })
    },
    // keep-alive 激活时重新读取 sessionStorage 中的编辑数据，避免残留上一次编辑的商品
    activated () {
      document.body.classList.add('mm-no-pull-refresh')
      this.registerHeaderActions()
      // 仍停留在类别选择页时不重置，保留用户的选择
      if (this.showCategoryGate) {
        return
      }
      this.loadRecordFromStorage()
    },
    deactivated () {
      clearMobileHeaderActions()
      clearMobileHeaderTitle()
      document.body.classList.remove('mm-no-pull-refresh')
    },
    beforeDestroy () {
      window.removeEventListener('resize', this.setHeight)
      clearMobileHeaderActions()
      clearMobileHeaderTitle()
      document.body.classList.remove('mm-no-pull-refresh')
    },
    computed: {
      // 识别按钮：已选类别、至少一张图片且识别完成时可点击
      canAiRecognize () {
        return !!this.gateCategoryId && this.aiImages.length > 0 && !this.aiImages.some(item => item.loading)
      }
    },
    methods: {
      // 页面初始化：编辑商品直接进表单；新增商品先显示类别选择
      initPage () {
        const recordStr = sessionStorage.getItem('materialEditRecord')
        if (!recordStr) {
          this.showCategoryGate = true
          this.loadGateTree()
          this.registerHeaderActions()
          return
        }
        this.showCategoryGate = false
        this.registerHeaderActions()
        this.loadRecordFromStorage()
      },
      // 加载类别树（一级类目不可选，与表单内的类别选择保持一致）
      loadGateTree () {
        queryMaterialCategoryTreeList({ id: '' }).then(res => {
          this.gateTreeData = this.disableLevelOneNodes(res || [])
        }).catch(() => {
          this.gateTreeData = []
        })
      },
      disableLevelOneNodes (nodes) {
        return (nodes || []).map(node => {
          const item = Object.assign({}, node)
          if (item.categoryLevel === 1) {
            item.disabled = true
          }
          if (item.children && item.children.length > 0) {
            item.children = this.disableLevelOneNodes(item.children)
          }
          return item
        })
      },
      // 打开类别新增弹层（手机端为标准全屏弹层，桌面端为居中弹窗）
      handleAddCategory () {
        const modal = this.$refs.categoryModal
        if (!modal) return
        modal.title = '新增品类'
        modal.add()
      },
      // AI添加：进入 AI添加商品 页面，先选择商品类别
      startAiAdd () {
        this.mode = 'ai'
        this.aiCategoryChosen = false
        this.gateCategoryId = undefined
        this.isEdit = false
        this.showCategoryGate = false
        this.registerHeaderActions()
      },
      // 人工添加：直接进入人工添加商品页面
      startManualAdd () {
        this.mode = 'manual'
        this.aiCategoryChosen = false
        this.isEdit = false
        this.showCategoryGate = false
        this.registerHeaderActions()
        this.$nextTick(() => this.openForm())
      },
      // 类别新增成功后刷新类别选择树
      handleCategoryAdded () {
        this.loadGateTree()
      },
      // 拍照 / 图片加载
      triggerAiCamera () {
        this.$refs.aiCameraInput && this.$refs.aiCameraInput.click()
      },
      triggerAiGallery () {
        this.$refs.aiGalleryInput && this.$refs.aiGalleryInput.click()
      },
      onAiFilesChange (e) {
        const files = Array.from((e.target && e.target.files) || [])
        e.target.value = ''
        files.slice(0, 9 - this.aiImages.length).forEach(file => this.addAiImage(file))
      },
      addAiImage (file) {
        const item = { id: ++this.aiImageSeq, preview: '', imagePath: '', rawText: '', loading: true }
        this.aiImages.push(item)
        const that = this
        this.fileToBase64(file).then(base64 => {
          item.preview = base64
          const formData = new FormData()
          formData.append('file', file)
          formData.append('biz', 'material')
          uploadHttp.post('/systemConfig/upload', formData).then(res => {
            if (res && res.code === 200 && res.data) item.imagePath = res.data
          }).catch(() => {})
          return recognizeTextFromImage(base64)
        }).then(res => {
          item.loading = false
          const rawText = res && res.code === 200 && res.data && typeof res.data.rawText === 'string' ? res.data.rawText : ''
          if (rawText) item.rawText = rawText
          else that.$message.warning('有图片未识别到文字，可换一张或直接识别')
          that.aiImages = that.aiImages.slice()
        }).catch(() => {
          item.loading = false
          that.$message.error('图片识别失败，请重试')
          that.aiImages = that.aiImages.slice()
        })
      },
      removeAiImage (index) {
        this.aiImages.splice(index, 1)
      },
      fileToBase64 (file) {
        return new Promise((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = e => resolve(e.target.result)
          reader.onerror = reject
          reader.readAsDataURL(file)
        })
      },
      // 识别：类别已选且图片识别完成后可用，点击进入识别结果页
      startAiRecognize () {
        if (!this.canAiRecognize) return
        const images = this.aiImages.map(item => ({ rawText: item.rawText || '', imagePath: item.imagePath || '' }))
        this.aiCategoryChosen = true
        this.registerHeaderActions()
        this.$nextTick(() => {
          this.openForm()
          const form = this.$refs.form
          if (form && form.applyPresetCategory) {
            form.applyPresetCategory(this.gateCategoryId)
          }
          this.$nextTick(() => this.waitSpecReadyAndAggregate(images, 0))
        })
      },
      // 等待类别规格加载完成后打开识别结果页
      waitSpecReadyAndAggregate (images, tries) {
        const form = this.$refs.form
        if (!form) return
        if ((form.specModules || []).length > 0) {
          form.openSpecAggregateForImages(images)
          return
        }
        if (tries >= 20) {
          this.$message.warning('该类别未配置规格，无法使用AI识别')
          return
        }
        setTimeout(() => this.waitSpecReadyAndAggregate(images, tries + 1), 150)
      },
      // 注册手机端顶部按钮：设置（属性设置）、重置、保存（类别选择阶段不显示）
      registerHeaderActions () {
        if (this.showCategoryGate || (this.mode === 'ai' && !this.aiCategoryChosen)) {
          setMobileHeaderActions([])
          this.updateHeaderTitle()
          return
        }
        setMobileHeaderActions([
          { key: 'setting', icon: 'setting', handler: this.handlePropertyConfig },
          { key: 'reset', icon: 'reload', handler: this.handleReset },
          { key: 'save', text: '保存', type: 'primary', handler: this.handleOk }
        ])
        this.updateHeaderTitle()
      },
      // 顶栏标题按当前模式显示
      updateHeaderTitle () {
        let title = '新增商品'
        if (this.showCategoryGate) {
          title = '新增商品'
        } else if (this.mode === 'ai') {
          title = 'AI添加商品'
        } else if (this.mode === 'manual' || !this.isEdit) {
          title = '人工添加商品'
        } else if (this.isEdit) {
          title = '编辑商品'
        }
        setMobileHeaderTitle(title)
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
        this.isEdit = false
        this.updateHeaderTitle()
        form.action = 'add'
        form.disableSubmit = false
        form.showOkFlag = true
        form.add()
      },
      // 编辑模式：加载商品数据及其所有SKU
      openEditForm (record) {
        const form = this.$refs.form
        if (!form) return
        this.isEdit = true
        this.updateHeaderTitle()
        form.action = 'edit'
        form.disableSubmit = false
        form.showOkFlag = true
        form.edit(record)
      },
      // 返回上一页（商品列表）：直接回退历史记录，避免新增一条历史导致列表与编辑页来回循环
      handleBack () {
        sessionStorage.removeItem('materialEditFrom')
        sessionStorage.removeItem('materialEditRecord')
        this.$router.back()
      },
      // 保存
      handleOk () {
        const form = this.$refs.form
        if (!form) return
        form.handleOk()
      },
      // 保存成功后给出提示并重置为空白表单，便于连续录入
      onSaved () {
        if (this.mode === 'ai') {
          // AI 商品页保存成功：不入商品添加页，清空已识别图片与类别，停留在 AI 添加页继续录入
          this.aiImages = []
          this.gateCategoryId = undefined
          this.aiCategoryChosen = false
          this.registerHeaderActions()
        }
      },
      // 表单关闭（取消或重置）：AI 添加页关闭后仍停留在当前 AI 添加页，不再回到新增入口
      onClose () {
        if (this.mode === 'ai') {
          this.aiImages = []
          this.gateCategoryId = undefined
          this.registerHeaderActions()
          this.$nextTick(() => {
            const form = this.$refs.form
            if (form && typeof form.add === 'function') form.add()
          })
          return
        }
        this.returnToCategoryGate()
      },
      // 回到新增入口（AI添加 / 人工添加）
      returnToCategoryGate () {
        this.gateCategoryId = undefined
        this.isEdit = false
        this.mode = ''
        this.aiCategoryChosen = false
        this.showCategoryGate = true
        this.registerHeaderActions()
      },
      // 打开基础属性设置弹窗
      handlePropertyConfig () {
        const form = this.$refs.form
        if (!form) return
        form.openPropertyConfig()
      },
      // 重置表单（有未保存数据时先确认）
      handleReset () {
        const form = this.$refs.form
        if (!form) return
        form.confirmIfDirty(() => form.close())
      }
    },
    // 离开本页（顶栏返回、手势返回、路由跳转）前，若页面数据会丢失则确认
    beforeRouteLeave (to, from, next) {
      const form = this.$refs.form
      if (form && form.hasUnsavedData()) {
        form.confirmIfDirty(() => next(), () => next(false))
      } else {
        next()
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
  /* 类别选择步骤：页面只展示一个类别选择框 */
  .ma-gate {
    max-width: 480px;
    margin: 0 auto;
    padding: 48px 8px 0;
    text-align: center;
  }
  .ma-gate__title {
    color: #1f2329;
    font-size: 20px;
    font-weight: 600;
  }
  .ma-gate__desc {
    margin: 10px 0 24px;
    color: #8b949e;
    font-size: 13px;
    line-height: 1.6;
  }
  .ma-gate__select {
    width: 100%;
    text-align: left;
  }
  .ma-ai-images {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .ma-ai-image {
    position: relative;
    width: 84px;
    height: 84px;
    overflow: hidden;
    border-radius: 6px;
    background: #f5f5f5;
  }
  .ma-ai-image img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  .ma-ai-image__mask {
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    padding: 2px 0;
    font-size: 11px;
    color: #fff;
    text-align: center;
    background: rgba(0, 0, 0, 0.45);
  }
  .ma-ai-image__remove {
    position: absolute;
    top: 2px;
    right: 2px;
    padding: 1px;
    font-size: 14px;
    color: #fff;
    background: rgba(0, 0, 0, 0.35);
    border-radius: 50%;
  }
  .ma-ai-images__empty {
    width: 100%;
    padding: 20px 12px;
    color: #8c8c8c;
    text-align: center;
    background: #fafafa;
    border: 1px dashed #d9d9d9;
    border-radius: 6px;
  }
  .ma-ai-image-actions {
    display: flex;
    gap: 12px;
    margin-top: 10px;
  }
  .ma-ai-setup__label-gap {
    margin-top: 20px;
  }
  .ma-ai-recognize {
    height: 46px;
    margin-top: 20px;
    font-size: 16px;
  }
  .ma-ai-file {
    display: none;
  }
  .ma-gate__btn {
    height: 48px;
    font-size: 16px;
  }
  .ma-gate__btn + .ma-gate__btn {
    margin-top: 16px;
  }
  .ma-ai-setup {
    max-width: 480px;
    margin: 0 auto;
    padding: 24px 8px 0;
  }
  .ma-ai-setup__label {
    margin-bottom: 8px;
    color: #1f2329;
    font-size: 14px;
    font-weight: 600;
  }
  .ma-gate__add {
    margin-top: 12px;
    height: 40px;
  }
  /* 手机端：不限制容器高度，内容随页面整体滚动到底部 */
  @media (max-width: 768px) {
    .material-add-page {
      height: auto !important;
      overflow: visible;
      padding-bottom: calc(96px + env(safe-area-inset-bottom));
    }
    .ma-gate {
      padding-top: 32px;
    }
  }
</style>

<style>
/* 商品录入页禁止下拉刷新（iOS 16+ / Android），避免手势刷新导致录入数据丢失 */
body.mm-no-pull-refresh {
  overscroll-behavior-y: contain;
}
</style>
