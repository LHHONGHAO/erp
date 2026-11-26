<template>
  <!-- 手机端标准商品卡片：左侧图片通栏 + 名称/规格/信息行，右侧可放操作 -->
  <div class="mpc-card" :class="{ 'is-alt': alt, 'is-clickable': clickable, 'is-fill': fill }" @click="onClick">
    <div class="mpc-card__thumb">
      <img v-if="imgUrl" :src="imgSrc" loading="lazy" decoding="async" alt="" />
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
      <!-- 信息行：数量等信息靠右；零售价定位于文字区左缘与名称对齐；组件间最小间距 16px，数值位最小宽 40px -->
      <div v-if="hasMeta" class="mpc-card__meta">
        <span v-for="(item, index) in items" :key="'kv-' + index" class="mpc-card__kv">
          <span class="mpc-card__kv-label">{{ item.label }}</span>
          <span class="mpc-card__kv-value">{{ item.value }}</span>
        </span>
        <slot name="metaExtra"></slot>
      </div>
      <!-- 零售价：绝对定位相对文字列，左缘与名称文本左缘严格对齐，底部贴卡片底边 -->
      <span v-if="hasPrice" class="mpc-card__price">¥{{ price }}</span>
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
 *   price   零售价，左边缘对齐卡片水平中点、与库存行同行显示
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
    /** 零售价：独立定位于卡片水平 1/2 处（与库存行同行），为空不显示 */
    price: { type: [String, Number], default: '' },
    alt: { type: Boolean, default: false },
    order: { type: Number, default: 0 },
    fill: { type: Boolean, default: false }
  },
  computed: {
    /** 卡片缩略图：原图接口切到后端缩略图接口（200px，约 20KB），原图 3MB 只留给大图预览 */
    imgSrc() {
      return this.imgUrl.replace('/systemConfig/static/', '/systemConfig/static/mini/')
    },
    clickable() {
      return !!(this.$listeners && this.$listeners.click)
    },
    hasSide() {
      return !!(this.$slots.side || this.$scopedSlots.side)
    },
    hasMeta() {
      return this.items.length > 0 || this.hasPrice || !!(this.$slots.metaExtra || this.$scopedSlots.metaExtra)
    },
    hasPrice() {
      return this.price !== '' && this.price !== null && this.price !== undefined
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
/* 卡片：无底色无边框（融入页面背景），仅作布局容器；左图四边圆角独立呈现 */
.mpc-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  padding: 0 12px 0 0;
  margin-bottom: 8px;
  background: transparent;
  border-radius: 14px;
  overflow: hidden;
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
/* 固定高度：规格单行省略，避免挤压信息行 */
.mpc-card.is-fill .mpc-card__spec {
  white-space: nowrap;
  overflow: hidden;
}
/* 固定高度：序/批徽标压进名称行 18px 行高内，保证文字总高估算与实际渲染一致 */
.mpc-card.is-fill .mpc-card__name .ant-tag {
  height: 18px;
  margin: 0 0 0 6px;
  padding: 0 5px;
  line-height: 16px;
  font-size: 11px;
}
.mpc-card.is-clickable {
  cursor: pointer;
}
/* 缩略图：贴边正方形（高度跟随卡片高度，宽度=高度），四边圆角独立呈现 */
.mpc-card__thumb {
  flex: none;
  align-self: stretch;
  width: auto;
  aspect-ratio: 1 / 1;
  position: relative;
  margin-right: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 12px;
  background: #edf3fb;
}
/* 图片铺满正方形区域，超出部分按顶部居中裁掉 */
.mpc-card__thumb img {
  display: block;
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center top;
}
.mpc-card__thumb-icon {
  font-size: 20px;
  color: #c3d4e8;
}
/* 右侧内容列：上下零内边距（名称贴顶、数量行贴底），行距由 space-between 均分；
   relative 作为零售价的定位基准，保证价格左缘与名称左缘对齐 */
.mpc-card__main {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 0;
}
.mpc-card__name {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  font-size: 17px;
  font-weight: 400;
  line-height: 22px;
  color: #111827;
}
.mpc-card__name-text {
  word-break: break-all;
}
/* 零售价：绝对定位细体小字，相对文字列 left:0 —— 盒子左缘与名称左缘一致，不再依赖图片宽度的固定偏移 */
.mpc-card__price {
  position: absolute;
  left: 0;
  bottom: 0;
  font-size: 14px;
  line-height: 22px;
  font-weight: 400;
  color: #FA2C19;
  white-space: nowrap;
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
/* 规格行：与上一行（名称/上一规格）保持均匀行距；与数量行同色浅灰 */
.mpc-card__spec {
  display: flex;
  align-items: flex-start;
  margin-top: 4px;
  font-size: 15px;
  line-height: 18px;
  color: #999999;
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
/* 信息行：与规格行保持同样行距，四行内容（名称/花色/规格/价格）垂直间距均匀；
   空信息行（仅零售价模式）也须占住 18px 行高，否则高度塌陷会让贴底价格与规格行重叠 */
.mpc-card__meta {
  min-height: 18px;
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-wrap: nowrap;
  padding-left: 50%;
  gap: 16px;
  font-size: 14px;
  line-height: 18px;
  color: #999999;
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
