<!--
  手机端标准页顶栏右侧图标按钮组
  所有使用标准页面（标准搜索页/全局顶栏）的业务页共用本组件，保证按钮尺寸、图标大小、间距与顺序完全一致
  标准顺序（右起）：添加、删除、启用、停用、导入、导出
-->
<template>
  <div class="mha-wrap">
    <a-button
      v-for="item in visibleActions"
      :key="item.key"
      :type="item.primary ? 'primary' : 'default'"
      size="small"
      :icon="item.icon"
      :title="item.title"
      :class="{ 'mha-text-btn': item.text }"
      @click="item.click"
    >
      <span v-if="item.text" class="mha-text">{{ item.text }}</span>
    </a-button>
  </div>
</template>

<script>
  // 标准按钮顺序：数组靠前者显示在左侧，因此左起为 导出、导入、停用、启用、删除、添加
  // 即从屏幕右侧向左数依次为 添加、删除、启用、停用、导入、导出
  const STANDARD_ORDER = ['export', 'import', 'disable', 'enable', 'delete', 'add']

  export default {
    name: 'MobileHeaderActions',
    props: {
      // [{ key, icon, title, text, primary, visible, click }]
      actions: {
        type: Array,
        default: function () { return [] }
      }
    },
    computed: {
      visibleActions() {
        const list = (this.actions || []).filter(function (item) {
          return item && item.visible !== false
        })
        return list
          .map(function (item, index) {
            return { item: item, index: index, rank: STANDARD_ORDER.indexOf(item.key) }
          })
          .sort(function (a, b) {
            // 非标准按钮（如刷新）作为扩展按钮放在最左侧
            const rankA = a.rank < 0 ? -1 : a.rank
            const rankB = b.rank < 0 ? -1 : b.rank
            if (rankA === rankB) return a.index - b.index
            return rankA - rankB
          })
          .map(function (entry) { return entry.item })
      }
    }
  }
</script>

<style>
  /* 非 scoped：a-button 内部的 .anticon 不带父组件作用域标记，需用 .mha-wrap 前缀全局限定 */
  .mha-wrap {
    display: flex;
    gap: 0;
    flex-wrap: nowrap;
    justify-content: flex-end;
    flex-shrink: 0;
  }
  /* 纯图标按钮：统一 36×36 方形，图标统一 20px，底色与顶栏一致 */
  .mha-wrap .ant-btn {
    width: 36px;
    height: 36px;
    padding: 0;
    font-size: 11px;
    line-height: 1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: transparent;
    border-color: transparent;
    color: #fff;
    box-shadow: none;
  }
  .mha-wrap .ant-btn .anticon {
    margin: 0;
    font-size: 20px;
    color: #fff;
  }
  /* 文字按钮（如保存）：宽度自适应，保持与其他图标按钮同一行高 */
  .mha-wrap .ant-btn.mha-text-btn {
    width: auto;
    padding: 0 12px;
    font-size: 15px;
  }
  .mha-wrap .mha-text {
    margin-left: 4px;
    color: #fff;
  }
  /* 按下/悬停仅做浅色高亮，不改变底色 */
  .mha-wrap .ant-btn:hover,
  .mha-wrap .ant-btn:focus,
  .mha-wrap .ant-btn:active,
  .mha-wrap .ant-btn.ant-btn-primary:hover,
  .mha-wrap .ant-btn.ant-btn-primary:focus,
  .mha-wrap .ant-btn.ant-btn-primary:active {
    background: rgba(255, 255, 255, 0.18);
    border-color: transparent;
    color: #fff;
  }
</style>
