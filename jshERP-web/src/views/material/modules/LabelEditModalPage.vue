<template>
  <label-edit-mobile
    v-if="visible"
    :key="openKey"
    embedded
    :initial-type="type"
    :initial-business-type="businessType"
    :initial-template-id="templateId"
    @close="handleClose" />
</template>

<script>
  /**
   * 标准复用标签编辑弹层页面
   *
   * 所有「编辑标签 / 新建标签模板」入口统一调用 open(options)：
   *   1. 写入 jsh.labelEdit.context（商品、模板、数据源上下文）
   *   2. 以标准弹层方式挂载 LabelEditMobile
   *   3. 每次 open 重新挂载，避免复用上一次的编辑状态
   */
  export default {
    name: 'LabelEditModalPage',
    // 异步引入：LabelEditMobile 内部会引入打印弹层，避免形成循环依赖
    components: { LabelEditMobile: () => import('./LabelEditMobile') },
    data() {
      return {
        visible: false,
        openKey: 0,
        type: 'label',
        businessType: '',
        templateId: null
      }
    },
    methods: {
      /**
       * @param {Object} options
       * @param {String} options.type            label / delivery / member / address
       * @param {String} options.businessType    product / delivery / member / ''
       * @param {String|Number} options.templateId 当前使用的模板 id
       * @param {Object} options.record          业务数据（商品、会员、地址等）
       * @param {Object} options.sku             单品数据
       * @param {Object} options.template        当前预览的模板快照
       * @param {String} options.provider        业务数据源标识
       */
      open(options) {
        var opts = options || {}
        try {
          sessionStorage.setItem('jsh.labelEdit.context', JSON.stringify({
            record: opts.record || {},
            sku: opts.sku || {},
            template: opts.template || null,
            provider: opts.provider || '',
            businessType: opts.businessType || ''
          }))
        } catch (e) { /* 忽略 */ }
        this.type = opts.type || 'label'
        this.businessType = opts.businessType || ''
        this.templateId = opts.templateId != null ? opts.templateId : null
        this.openKey += 1
        this.visible = true
      },
      close() {
        this.visible = false
      },
      handleClose() {
        this.visible = false
        this.$emit('close')
      }
    }
  }
</script>
