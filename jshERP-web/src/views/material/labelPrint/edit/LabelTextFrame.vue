<template>
  <!-- 编辑态文字外框：所有标签元素（含规格块）统一由本组件加载、渲染、承载拖动 -->
  <div
    class="lem-elem"
    :class="[extraClass, { 'lem-elem--active': active }]"
    :data-key="elementKey || null"
    :style="rootStyle"
    @pointerdown.stop.prevent="$emit('drag-start', dragKey, $event, 'move')"
  >
    <!-- 内容层单独裁切，手柄放在内容层之外，避免被 overflow 裁掉 -->
    <div v-if="withInner" class="lem-elem-inner">
      <slot></slot>
    </div>
    <span v-if="showFrame" class="lem-elem-frame" aria-hidden="true"></span>
    <span
      v-if="showHandle"
      class="lem-resize-handle"
      :style="handleStyle"
      @pointerdown.stop.prevent="$emit('drag-start', dragKey, $event, 'resize')"
    ></span>
    <span
      v-if="showDelete"
      class="lem-delete-btn"
      :style="deleteStyle"
      @pointerdown.stop.prevent
      @click.stop="$emit('delete')"
    >×</span>
  </div>
</template>

<script>
import { handleAntiScaleStyle, deleteBtnAntiScaleStyle } from './labelFrameGeometry'

export default {
  name: 'LabelTextFrame',
  props: {
    // 拖动/选中使用的元素 key；规格块传锚点 key
    dragKey: { type: String, required: true },
    // 供布局查询使用的 data-key；规格块没有对应单个元素时留空
    elementKey: { type: String, default: '' },
    // 元素布局样式（位置、尺寸、字体）
    frameStyle: { type: [Object, Array], default: () => ({}) },
    zoom: { type: Number, default: 1 },
    // 选中态：外框高亮
    active: { type: Boolean, default: false },
    showFrame: { type: Boolean, default: true },
    showHandle: { type: Boolean, default: false },
    showDelete: { type: Boolean, default: false },
    // 是否渲染内容层（规格块只有外框）
    withInner: { type: Boolean, default: true },
    extraClass: { type: String, default: '' }
  },
  computed: {
    rootStyle() {
      return [this.frameStyle, { '--lem-zoom': String(this.zoom || 1) }]
    },
    handleStyle() {
      return handleAntiScaleStyle(this.zoom)
    },
    deleteStyle() {
      return deleteBtnAntiScaleStyle(this.zoom)
    }
  }
}
</script>

<style scoped>
.lem-elem {
  position: absolute;
  cursor: move;
  /* 与打印预览一致：无内边距/边框，文字从元素 x 起始，宽度即文本框宽度 */
  padding: 0;
  border: none;
  box-sizing: border-box;
  white-space: nowrap;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
  z-index: 1;
}

/* 所有已加载元素统一虚线外框（点击后只显示手柄）
   注意：不能使用 outline/小于 1px 的边框线宽——WebKit（iOS Safari）会把小于 1px 的
   边框和 outline 线宽按边丢弃，导致左右边线不显示。这里用 1px 边框的子元素按 1/zoom
   反缩放，缩放后仍是 1px 视觉线宽，且四条边都能稳定绘制 */
.lem-elem-frame {
  position: absolute;
  left: 0;
  top: 0;
  box-sizing: border-box;
  width: calc(100% * var(--lem-zoom, 1));
  height: calc(100% * var(--lem-zoom, 1));
  /* 未选中/未点击：灰色边框 */
  border: 2px dashed #bfbfbf;
  transform: scale(calc(1 / var(--lem-zoom, 1)));
  transform-origin: top left;
  pointer-events: none;
}

/* 被点击选中的元素：沿用原来的蓝色边框 */
.lem-elem--active .lem-elem-frame {
  border-color: rgba(24, 144, 255, 0.85);
}

/* 规格块整块：属性名 + 全部规格值共用一个外框，样式与普通文字标签一致，可整体拖动/缩放 */
.lem-spec-block {
  cursor: move;
  touch-action: none;
  /* 高于块内文字元素，整块接收点击与拖动 */
  z-index: 6;
}

/* 内容层：只裁切内容，不裁选手柄 */
.lem-elem-inner {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.lem-resize-handle {
  position: absolute;
  width: 20px;
  height: 20px;
  background: #1890ff;
  border-radius: 50%;
  cursor: nwse-resize;
  touch-action: none;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3;
}

/* 视觉仍是 20px 圆点，透明感应区扩到 44×44 */
.lem-resize-handle::before,
.lem-delete-btn::before {
  content: '';
  position: absolute;
  inset: -12px;
  border-radius: 50%;
  background: transparent;
}

.lem-resize-handle::after {
  content: '';
  width: 7px;
  height: 7px;
  border-right: 1.5px solid #fff;
  border-bottom: 1.5px solid #fff;
  margin-top: -1px;
  margin-left: -1px;
}

.lem-delete-btn {
  position: absolute;
  width: 20px;
  height: 20px;
  background: #ff4d4f;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  touch-action: none;
  z-index: 4;
}
</style>
