<template>
  <!-- 元素面板正文：数据表选择 + 按分组列出全部字段 + 自定义标签 -->
  <div class="lem-side-body">
    <!-- 数据表选择：字段随所选表加载，逻辑与 Web 端「业务数据源」一致 -->
    <div v-if="providers.length" class="lem-provider">
      <span class="lem-provider-label">数据表</span>
      <a-select
        :value="provider"
        size="small"
        class="lem-provider-select"
        placeholder="选择数据表"
        @change="$emit('provider-change', $event)"
      >
        <a-select-option v-for="p in providers" :key="p.provider" :value="p.provider">{{ p.name }}</a-select-option>
      </a-select>
    </div>
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
    // 可选业务数据表
    providers: { type: Array, default: () => [] },
    // 当前选中的数据表标识
    provider: { type: String, default: '' },
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
/* 数据表选择：窄栏内纵向排列 */
.lem-provider {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.lem-provider-label {
  flex: none;
  font-size: 11px;
  color: #999;
}

.lem-provider-select {
  width: 100%;
}

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
