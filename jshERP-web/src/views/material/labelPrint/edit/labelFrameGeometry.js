/**
 * 编辑态文字外框几何：手柄、删除按钮在标签缩放下的等视觉尺寸换算。
 * 外层 label 使用 scale(zoom) 缩放，子元素的尺寸与偏移都会同步放大。
 * 因此偏移量除以 zoom，元素自身再反向缩放，保证任何缩放下视觉尺寸恒定。
 */

// 手柄/删除按钮视觉半径：20px 圆点，向外偏移 10px
var ANTI_SCALE_OFFSET = 10

/** 缩放手柄：贴右下角 */
export function handleAntiScaleStyle(zoom) {
  var inv = 1 / (zoom || 1)
  return {
    right: (-ANTI_SCALE_OFFSET * inv) + 'px',
    bottom: (-ANTI_SCALE_OFFSET * inv) + 'px',
    transform: 'scale(' + inv + ')',
    transformOrigin: 'bottom right'
  }
}

/** 删除按钮：贴左上角 */
export function deleteBtnAntiScaleStyle(zoom) {
  var inv = 1 / (zoom || 1)
  return {
    top: (-ANTI_SCALE_OFFSET * inv) + 'px',
    left: (-ANTI_SCALE_OFFSET * inv) + 'px',
    transform: 'scale(' + inv + ')',
    transformOrigin: 'top left'
  }
}
