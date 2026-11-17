<template>
  <!-- 手机端标准商品卡片：左侧图片通栏 + 名称/规格/信息行，右侧可放操作 -->
  <div class="mpc-card" :class="{ 'is-alt': alt, 'is-clickable': clickable, 'is-fill': fill }" @click="onClick">
    <div class="mpc-card__thumb">
      <img v-if="imgUrl" :src="imgUrl" alt="" />
      <a-icon v-else type="picture" class="mpc-card__thumb-icon" />
    </div>
    <div class="mpc-card__main">
      <div class="mpc-card__name">
        <span class="mpc-card__name-text">{{ name }}</span>
        <span v-if="order > 0" class="mpc-card__order">{{ order }}</span>
        <slot name="nameExtra"></slot>
      </div>
      <div v-for="(line, index) in specs" :key="'spec-' + index" class="mpc-card__spec">
        <span class="mpc-card__spec-label">{{ line.label ? line.label + '：' : '' }}</span>
        <span class="mpc-card__spec-value">{{ line.value }}</span>
      </div>
      <!-- 信息行：与规格行同间距；组件间最小间距 16px，数值位最小宽 40px，多余空间作为组件之间的留白 -->
      <div v-if="hasMeta" class="mpc-card__meta">
        <span v-for="(item, index) in items" :key="'kv-' + index" class="mpc-card__kv">
          <span class="mpc-card__kv-label">{{ item.label }}</span>
          <span class="mpc-card__kv-value">{{ item.value }}</span>
        </span>
        <slot name="metaExtra"></slot>
      </div>
    </div>
    <div v-if="hasSide" class="mpc-card__side">
      <slot name="side"></slot>
    </div>
  </div>
</template>

<script>
/**
 * 手机端标准商品卡片（商品选择 / 商品信息 / 销售开单共用）
 * props:
 *   name    商品名称（nameExtra 插槽用于放序/批等标签）
 *   imgUrl  缩略图地址，为空显示占位图标
 *   specs   规格行数组 [{ label, value }]
 *   items   信息行数组 [{ label, value }]，值位最小宽 40px
 *   alt     是否使用交替浅灰底色（列表偶数项）
 *   order   选中序号，大于 0 时显示在名称后
 *   fill    是否填满外部容器高度（虚拟列表等固定高度场景）
 *   side    插槽：卡片右侧操作区（垂直居中）
 *   metaExtra 插槽：信息行内追加内容（如数量加减器）
 */
export default {
  name: 'MobileProductCard',
  props: {
    name: { type: String, default: '' },
    imgUrl: { type: String, default: '' },
    specs: { type: Array, default: () => [] },
    items: { type: Array, default: () => [] },
    alt: { type: Boolean, default: false },
    order: { type: Number, default: 0 },
    fill: { type: Boolean, default: false }
  },
  computed: {
    clickable() {
      return !!(this.$listeners && this.$listeners.click)
    },
    hasSide() {
      return !!(this.$slots.side || this.$scopedSlots.side)
    },
    hasMeta() {
      return this.items.length > 0 || !!(this.$slots.metaExtra || this.$scopedSlots.metaExtra)
    }
  },
  methods: {
    onClick(event) {
      if (this.clickable) this.$emit('click', event)
    }
  }
}
</script>

<style scoped>
/* 卡片：左图 72×72 正方形，右侧内容纵向排列 */
.mpc-card {
  display: flex;
  align-items: flex-start;
  padding: 8px 12px 8px 8px;
  margin-bottom: 8px;
  background: #fff;
  border: 1px solid #e3e7ee;
  border-radius: 12px;
}
.mpc-card.is-alt {
  background: #eceff3;
  border-color: #e0e4ea;
}
/* 固定高度场景：撑满外部容器；名称单行省略，信息行紧跟规格行，超出裁掉 */
.mpc-card.is-fill {
  height: 100%;
  margin-bottom: 0;
  overflow: hidden;
}
.mpc-card.is-fill .mpc-card__main {
  height: 100%;
}
.mpc-card.is-fill .mpc-card__name {
  flex-wrap: nowrap;
  overflow: hidden;
}
.mpc-card.is-fill .mpc-card__name-text {
  flex: 1 1 auto;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.mpc-card.is-fill .mpc-card__meta {
  margin-top: 4px;
}
/* 固定高度：规格单行省略，避免挤压信息行 */
.mpc-card.is-fill .mpc-card__spec {
  white-space: nowrap;
  overflow: hidden;
}
.mpc-card.is-clickable {
  cursor: pointer;
}
/* 缩略图：宽高 72 的正方形，顶部与文字对齐 */
.mpc-card__thumb {
  flex: none;
  width: 72px;
  height: 72px;
  align-self: flex-start;
  margin-right: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 8px;
  background: #edf3fb;
}
/* 图片按宽度等比放大后，超出正方形的底部裁掉 */
.mpc-card__thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center top;
}
.mpc-card__thumb-icon {
  font-size: 20px;
  color: #c3d4e8;
}
/* 右侧内容列 */
.mpc-card__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.mpc-card__name {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  font-size: 15px;
  font-weight: 600;
  line-height: 18px;
  color: #1d2129;
}
.mpc-card__name-text {
  word-break: break-all;
}
/* 选中序号徽标 */
.mpc-card__order {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  margin-left: 6px;
  padding: 0 4px;
  border-radius: 9px;
  background: #1890ff;
  color: #fff;
  font-size: 12px;
  line-height: 18px;
  font-weight: 600;
}
.mpc-card__spec {
  display: flex;
  align-items: flex-start;
  margin-top: 1px;
  font-size: 12px;
  line-height: 16px;
  color: #40a9ff;
  text-align: left;
  word-break: break-all;
}
.mpc-card__spec-label {
  flex: none;
}
.mpc-card__spec-value {
  flex: 1;
  min-width: 0;
  word-break: break-all;
}
/* 信息行：与规格行同间距，两端分布，多余宽度留白 */
.mpc-card__meta {
  margin-top: 1px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: nowrap;
  gap: 16px;
  font-size: 12px;
  line-height: 16px;
  color: #40a9ff;
  text-align: left;
}
.mpc-card__kv {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.mpc-card__kv-label {
  flex: none;
}
.mpc-card__kv-value {
  display: inline-block;
  min-width: 40px;
  padding: 0 2px;
  text-align: right;
  background: rgba(24, 144, 255, 0.1);
  border-radius: 4px;
}
/* 右侧操作列：垂直居中，与卡片内容等高 */
.mpc-card__side {
  flex: none;
  align-self: stretch;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 10px;
}
</style>
