<template>
  <!-- 元素面板单个条目：中文名 + 字段 key，点击切换是否加载到画布 -->
  <div class="lem-elem-item" :class="{ 'lem-elem-item-active': loaded }" @click="$emit('toggle', attr)">
    <div class="lem-elem-text">
      <span class="lem-elem-cn">{{ attr.isSpecBlock ? attr.label : (attr.comment || attr.label) }}</span>
      <span v-if="attr.isSpecBlock" class="lem-elem-key">{{ attr.specText || ('规格块 · ' + attr.valueCount + ' 个值') }}</span>
    </div>
    <a-icon
      :type="loaded ? 'check-circle' : 'plus-circle'"
      :theme="loaded ? 'filled' : 'outlined'"
      :class="{ 'lem-elem-icon--on': loaded }"
    />
  </div>
</template>

<script>
export default {
  name: 'LabelElementItem',
  props: {
    attr: { type: Object, required: true },
    // 该条目是否已加载到画布（规格块要求块内全部元素都在）
    loaded: { type: Boolean, default: false }
  }
}
</script>

<style scoped>
/* 元素列表：窄栏单列，中文名在上、字段 key 在下 */
.lem-elem-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 4px;
  padding: 6px 4px;
  border-bottom: 1px solid #f5f5f5;
}

.lem-elem-text {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.lem-elem-item .anticon {
  color: #bfbfbf;
  flex: 0 0 auto;
}

.lem-elem-icon--on {
  color: #1890ff;
}

.lem-elem-cn {
  font-size: 12px;
  line-height: 16px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lem-elem-key {
  font-size: 11px;
  line-height: 14px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lem-elem-item-active {
  background: #e6f7ff;
}

.lem-elem-item-active .lem-elem-cn {
  color: #1890ff;
}
</style>
