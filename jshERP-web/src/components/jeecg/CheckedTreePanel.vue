<template>
  <a-col :md="10" :sm="24">
    <a-tree
      checkable
      multiple
      :selectedKeys="selectedKeys"
      :checkedKeys="checkedKeys"
      :treeData="treeData"
      :checkStrictly="checkStrictly"
      :expandedKeys="expandedKeys"
      :autoExpandParent="true"
      @check="handleCheck"
      @expand="handleExpand" />
  </a-col>
</template>

<script>
export default {
  name: 'CheckedTreePanel',
  props: {
    treeData: { type: Array, default: function() { return [] } },
    selectedKeys: { type: Array, default: function() { return [] } },
    checkedKeys: { type: Array, default: function() { return [] } },
    expandedKeys: { type: Array, default: function() { return [] } },
    checkStrictly: { type: Boolean, default: false }
  },
  methods: {
    handleCheck(keys, info) {
      this.$emit('check', keys, info)
    },
    handleExpand(keys) {
      this.$emit('expand', keys)
    }
  }
}
</script>
