<template>
  <!-- 商品AI添加：独立手机页面，只负责选图、选类别与发起识别 -->
  <div class="ai-add-page" :class="{ 'is-mobile': device === 'mobile' }">
    <div class="aip-head" v-if="device !== 'mobile'">
      <a-button icon="left" @click="handleBack">返回</a-button>
      <span class="aip-head__title">商品AI添加</span>
    </div>

    <div class="aip-body">
      <!-- 图片框置顶：占用剩余空白高度，图片按比例完整缩放显示 -->
      <div class="aip-preview" :class="{ 'is-empty': !image }" @click="triggerGallery">
        <img v-if="image" :src="image.preview" />
        <div v-else class="aip-preview__empty">
          <a-icon type="picture" />
          <span>点击选择商品图片</span>
        </div>
        <span v-if="image && image.loading" class="aip-preview__mask">识别中</span>
        <a-icon v-if="image" type="close-circle" class="aip-preview__del" @click.stop="removeImage" />
      </div>

      <!-- 图片加载 / 拍照：位于图片框与属性之间 -->
      <div class="aip-media-row">
        <div class="aip-media-btn" :class="{ active: !!image && pickedFrom === 'gallery' }" @click="triggerGallery">
          <a-icon type="picture" class="aip-media-btn__icon" />
          <div class="aip-media-btn__txt">选择图片</div>
        </div>
        <div class="aip-media-btn" :class="{ active: !!image && pickedFrom === 'camera' }" @click="triggerCamera">
          <a-icon type="camera" class="aip-media-btn__icon" />
          <div class="aip-media-btn__txt">拍照</div>
        </div>
      </div>

      <!-- 属性选择：紧邻底部识别栏 -->
      <div class="aip-card">
        <div class="aip-card__title">商品属性</div>
        <div class="aip-category">
          <span class="aip-category__label">商品类别</span>
          <a-tree-select
            v-model="categoryId"
            class="aip-category__select"
            :dropdownStyle="{ maxHeight: '320px', overflow: 'auto' }"
            :treeData="categoryTree"
            :treeDefaultExpandAll="true"
            allow-clear
            placeholder="请选择类别" />
        </div>
        <div class="aip-card__hint">类别决定识别结果的属性字段与单位，识别前必须选择。</div>
      </div>
    </div>

    <div class="aip-footer">
      <button class="aip-btn" :disabled="!canRecognize" @click="openResult">识 别</button>
    </div>

    <input ref="cameraInput" type="file" accept="image/*" capture="environment" class="aip-file" @change="onFilesChange" />
    <input ref="galleryInput" type="file" accept="image/*" class="aip-file" @change="onFilesChange" />

    <!-- 识别结果：复用商品表单的识别结果弹层，表单本体隐藏，仅用于承载识别流程与保存 -->
    <div v-if="recognizing" class="aip-hidden-form">
      <material-modal ref="form" inline aiMode @ok="onSaved" @agg-close="closeResult" />
    </div>
  </div>
</template>

<script>
  import MaterialModal from './modules/MaterialModal'
  import { queryMaterialCategoryTreeList } from '@/api/api'
  import { mixinDevice } from '@/utils/mixin.js'
  import { setMobileHeaderActions, clearMobileHeaderActions, setMobileHeaderTitle, clearMobileHeaderTitle } from '@/utils/mobilePageHeader'
  import { axios as uploadHttp } from '@/utils/request'
  import { recognizeTextFromImage } from '@/views/material/utils/productTextScan'

  export default {
    name: 'MaterialAiAdd',
    components: {
      MaterialModal
    },
    mixins: [mixinDevice],
    data() {
      return {
        // 已选图片（单图单商品）
        image: null,
        pickedFrom: '',
        // 商品类别
        categoryId: undefined,
        categoryTree: [],
        // 是否正在展示识别结果弹层
        recognizing: false
      }
    },
    computed: {
      canRecognize() {
        return !!this.categoryId && !!this.image && !this.image.loading
      }
    },
    mounted() {
      document.body.classList.add('mm-no-pull-refresh')
      this.registerMobileHeader()
      this.loadCategoryTree()
    },
    // keep-alive 缓存页面返回时重新注册顶栏标题
    activated() {
      document.body.classList.add('mm-no-pull-refresh')
      this.registerMobileHeader()
    },
    // 离开本页清空顶栏标题，避免残留在首页等其它页面
    deactivated() {
      clearMobileHeaderActions()
      clearMobileHeaderTitle()
      document.body.classList.remove('mm-no-pull-refresh')
    },
    beforeDestroy() {
      clearMobileHeaderActions()
      clearMobileHeaderTitle()
      document.body.classList.remove('mm-no-pull-refresh')
    },
    methods: {
      registerMobileHeader() {
        clearMobileHeaderActions()
        setMobileHeaderTitle('商品AI添加')
      },
      handleBack() {
        this.$router.back()
      },
      // 类别树：一级类目不可选，与表单内类别选择保持一致
      loadCategoryTree() {
        queryMaterialCategoryTreeList({ id: '' }).then(res => {
          this.categoryTree = this.disableLevelOneNodes(res || [])
        }).catch(() => {
          this.categoryTree = []
        })
      },
      disableLevelOneNodes(nodes) {
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
      triggerCamera() {
        this.$refs.cameraInput && this.$refs.cameraInput.click()
      },
      triggerGallery() {
        this.$refs.galleryInput && this.$refs.galleryInput.click()
      },
      onFilesChange(e) {
        const files = Array.from((e.target && e.target.files) || [])
        const fromCamera = e.target === this.$refs.cameraInput
        e.target.value = ''
        if (!files.length) return
        this.pickedFrom = fromCamera ? 'camera' : 'gallery'
        this.addImage(files[0])
      },
      addImage(file) {
        const item = { preview: '', imagePath: '', rawText: '', loading: true }
        this.image = item
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
          else this.$message.warning('有图片未识别到文字，可换一张或直接识别')
          this.image = Object.assign({}, item)
        }).catch(() => {
          item.loading = false
          this.$message.error('图片识别失败，请重试')
          this.image = Object.assign({}, item)
        })
      },
      removeImage() {
        this.image = null
        this.pickedFrom = ''
      },
      fileToBase64(file) {
        return new Promise((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = e => resolve(e.target.result)
          reader.onerror = reject
          reader.readAsDataURL(file)
        })
      },
      // 识别：弹出识别结果弹层，本页状态保持不变
      openResult() {
        if (!this.canRecognize) return
        const item = this.image || {}
        const image = { rawText: item.rawText || '', imagePath: item.imagePath || '' }
        this.recognizing = true
        this.$nextTick(() => {
          const form = this.$refs.form
          if (!form) return
          form.action = 'add'
          form.disableSubmit = false
          form.showOkFlag = true
          form.add()
          if (form.applyPresetCategory) form.applyPresetCategory(this.categoryId, this.resolveCategoryUnit(this.categoryId))
          this.$nextTick(() => this.waitSpecReadyAndRecognize(image, 0))
        })
      },
      // 解析所选类别单位：当前节点为空时回溯最近上级类目
      resolveCategoryUnit(categoryId) {
        if (!categoryId) return ''
        const path = []
        const walk = nodes => {
          for (let i = 0; i < (nodes || []).length; i++) {
            const node = nodes[i]
            if (String(node.id) === String(categoryId) || String(node.value) === String(categoryId)) {
              path.push(node)
              return true
            }
            if (node.children && node.children.length > 0 && walk(node.children)) {
              path.unshift(node)
              return true
            }
          }
          return false
        }
        walk(this.categoryTree)
        for (let i = path.length - 1; i >= 0; i--) {
          const unit = (path[i].unit || '').trim()
          if (unit) return unit
        }
        return ''
      },
      waitSpecReadyAndRecognize(image, tries) {
        const form = this.$refs.form
        if (!form) return
        if ((form.specModules || []).length > 0) {
          form.openSpecRecognize(image)
          return
        }
        if (tries >= 20) {
          this.$message.warning('该类别未配置规格，无法使用AI识别')
          this.closeResult()
          return
        }
        setTimeout(() => this.waitSpecReadyAndRecognize(image, tries + 1), 150)
      },
      // 关闭识别结果弹层：保留图片与类别，可继续识别
      closeResult() {
        this.recognizing = false
      },
      onSaved() {
        // 保存成功：关闭识别结果弹层，并清空本页图片与类别
        this.recognizing = false
        this.image = null
        this.pickedFrom = ''
        this.categoryId = undefined
        this.$message.success('保存成功')
      }
    }
  }
</script>

<style lang="less" scoped>
  .ai-add-page {
    display: flex;
    flex-direction: column;
    min-height: calc(100vh - 49px);
    background: #f2f4f7;
  }
  /* 手机端：固定为可视高度（dvh 兼容浏览器地址栏），页面内不滚动，底部识别栏常驻 */
  .ai-add-page.is-mobile {
    height: calc(100vh - 49px);
    height: calc(100dvh - 49px);
    min-height: 0;
    overflow: hidden;
  }
  .aip-head {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 16px;
    background: #fff;
    border-bottom: 1px solid #eef2f7;
    &__title {
      font-size: 16px;
      font-weight: 500;
      color: #1f2733;
    }
  }
  .aip-body {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 12px 12px 0;
    overflow: hidden;
  }
  .aip-card {
    flex: none;
    background: #fff;
    border-radius: 14px;
    padding: 14px;
    margin-bottom: 0;
    box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
    &__title {
      display: flex;
      align-items: center;
      font-size: 14px;
      font-weight: 600;
      color: #1f2733;
      margin-bottom: 12px;
      &::before {
        content: '';
        width: 3px;
        height: 13px;
        border-radius: 2px;
        background: #1890ff;
        margin-right: 6px;
      }
    }
    &__hint {
      margin-top: 8px;
      font-size: 12px;
      color: #98a2b3;
      line-height: 1.5;
    }
  }
  .aip-media-row {
    flex: none;
    display: flex;
    gap: 12px;
  }
  .aip-media-btn {
    flex: 1;
    height: 84px;
    border-radius: 12px;
    border: 1px dashed #c7d2e3;
    background: #f7f9fc;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    transition: border-color 0.18s, background 0.18s;
    &.active {
      border-style: solid;
      border-color: #1890ff;
      background: #eaf4ff;
      .aip-media-btn__icon,
      .aip-media-btn__txt {
        color: #1890ff;
      }
    }
    &__icon {
      font-size: 26px;
      color: #5b6b82;
      margin-bottom: 8px;
    }
    &__txt {
      font-size: 14px;
      color: #445064;
    }
  }
  /* 图片框：占用按钮与属性之外的剩余空白高度，图片等比完整显示 */
  .aip-preview {
    flex: 1 1 auto;
    min-height: 96px;
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    padding: 8px;
    background: #fff;
    border: 1px solid #e8eef7;
    border-radius: 14px;
    &.is-empty {
      background: #f7f9fc;
      border-style: dashed;
      border-color: #c7d2e3;
      cursor: default;
    }
    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
      display: block;
    }
    &__empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      font-size: 13px;
      color: #9aa6b8;
      /deep/ .anticon {
        font-size: 34px;
      }
    }
    &__mask {
      position: absolute;
      left: 0;
      right: 0;
      top: 0;
      bottom: 0;
      background: rgba(17, 24, 39, 0.35);
      color: #fff;
      font-size: 13px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    &__del {
      position: absolute;
      top: 8px;
      right: 8px;
      font-size: 22px;
      color: rgba(17, 24, 39, 0.55);
      background: #fff;
      border-radius: 50%;
    }
  }
  .aip-category {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 4px 12px;
    border-radius: 12px;
    background: #f7f9fc;
    border: 1px solid #e8eef7;
    &__label {
      flex: none;
      width: 64px;
      font-size: 13px;
      color: #5b6678;
    }
    &__select {
      flex: 1;
      min-width: 0;
      /deep/ .ant-select-selection {
        border: 0;
        background: transparent;
        box-shadow: none;
      }
      /deep/ .ant-select-selection__rendered {
        margin-left: 0;
        margin-right: 20px;
        line-height: 30px;
      }
      /deep/ .ant-select-arrow {
        color: #b6c0d0;
      }
    }
  }
  .aip-footer {
    flex: none;
    background: #fff;
    padding: 10px 12px calc(10px + env(safe-area-inset-bottom, 0px));
    box-shadow: 0 -1px 0 rgba(16, 24, 40, 0.05);
  }
  .aip-btn {
    width: 100%;
    height: 52px;
    border: 0;
    border-radius: 26px;
    background: #1890ff;
    color: #fff;
    font-size: 17px;
    font-weight: 500;
    letter-spacing: 4px;
    &[disabled] {
      background: #b9d5f7;
      color: #fff;
      cursor: not-allowed;
    }
  }
  .aip-file {
    display: none;
  }
  /* 承载识别流程的表单：保持全屏布局但不显示，识别结果以标准弹层呈现 */
  .aip-hidden-form {
    position: fixed;
    left: 0;
    top: 0;
    width: 100vw;
    height: 100vh;
    overflow: hidden;
    visibility: hidden;
    pointer-events: none;
    z-index: -1;
  }
</style>
