<template>
  <!-- 元素面板正文：按分组列出全部字段 + 自定义标签（数据源由打开编辑器时按业务自动确定，不提供下拉切换） -->
  <div class="lem-side-body">
    <div v-for="group in groups" :key="group.name" class="lem-elem-group">
      <div class="lem-elem-group-title">{{ group.name }}</div>
      <LabelElementItem
        v-for="attr in group.attrs"
        :key="attr.key"
        :attr="attr"
        :loaded="isLoaded(attr)"
        @toggle="$emit('toggle-item', $event)"
      />
    </div>
    <a-button type="dashed" block size="small" class="lem-add-custom" @click="$emit('add-custom')">
      <a-icon type="plus" />自定义标签
    </a-button>
    <a-button type="dashed" block size="small" class="lem-add-custom" @click="$emit('add-separator')">
      <a-icon type="plus" />间隔符
    </a-button>
    <a-button type="dashed" block size="small" class="lem-add-custom" @click="$emit('add-code')">
      <a-icon type="plus" />二维码/条码
    </a-button>
  </div>
</template>

<script>
import LabelElementItem from './LabelElementItem'

export default {
  name: 'LabelElementPanel',
  components: { LabelElementItem },
  props: {
    // 已分组的字段清单（规格块已聚合为一项）
    groups: { type: Array, default: () => [] },
    // 画布上已加载的元素 key
    selectedAttrs: { type: Array, default: () => [] }
  },
  methods: {
    // 元素面板条目是否已加载（规格块要求块内全部元素都在）
    isLoaded(attr) {
      var keys = attr && attr.isSpecBlock ? attr.blockKeys : [attr && attr.key]
      if (!keys || !keys.length) return false
      var self = this
      return keys.every(function(key) { return self.selectedAttrs.indexOf(key) > -1 })
    }
  }
}
</script>

<style scoped>
/* 勾选式元素列表：分组标题 */
.lem-elem-group-title {
  font-size: 11px;
  color: #999;
  padding: 5px 4px;
  background: #fafafa;
  margin-top: 2px;
}

.lem-add-custom {
  margin-top: 8px;
}
</style>
