/**
 * label-scroll.js
 * 
 * 标签预览区 居中 + 对称滑动 算法
 * 
 * 问题背景：
 *   label 用 transform: scale(zoom) 放大时，DOM 布局宽度不变，
 *   浏览器算 scrollWidth 时不把视觉放大算进去 → 滚动条范围太小
 *   而且 transform-origin: center top 时，左边溢出到 scrollLeft=0 之外
 * 
 * 解法：
 *   在 label 前后各放一个等宽 spacer，让 DOM scrollWidth 的中心 = label 视觉中心
 *   scrollWidth = spacer + labelDOM + spacer = labelDOM × zoom
 *   scrollLeft = max / 2 时，label 视觉中心 = 屏幕中心
 * 
 *   同时根据是否溢出动态切换 justify-content：
 *     - 溢出 → flex-start（配合 spacer 做 scrollLeft 居中）
 *     - 不溢出 → center（直接让 label 在视口内居中）
 * 
 * 用法（Vue SFC）：
 *   import { computeSpacer, setupLabelScroll } from '@/utils/label-scroll'
 *   
 *   // data
 *   wrapScrolling: false
 *   
 *   // computed
 *   scrollSpacer() {
 *     return computeSpacer(this.labelWidth, this.zoom)
 *   },
 *   
 *   // template
 *   <div class="wrap" ref="wrap" :class="wrapScrolling ? 'wrap--scrolling' : 'wrap--fitting'">
 *     <div :style="{ width: scrollSpacer + 'px' }"></div>
 *     <div class="label" style="transform:scale(zoom); transform-origin:center top">...</div>
 *     <div :style="{ width: scrollSpacer + 'px' }"></div>
 *   </div>
 *   
 *   // mounted
 *   setupLabelScroll(this, 'wrap', 'labelWidth', 'zoom', 'wrapScrolling')
 */

/**
 * 计算单侧 spacer 宽度
 * 
 * @param {number} labelDomWidth  label DOM 布局宽度（px）
 * @param {number} zoom           当前缩放比例（1.0 = 原始大小）
 * @returns {number}              单侧 spacer 宽度（px），最小为 0
 * 
 * 公式推导：
 *   label 视觉宽度 = labelDomW × zoom
 *   要让 scrollWidth = label 视觉宽度（刚好覆盖，没有多余）
 *   2 × spacer + labelDomW = labelDomW × zoom
 *   spacer = labelDomW × (zoom - 1) / 2
 * 
 *   zoom=1.0 → spacer=0（标签原始大小，不用 spacer）
 *   zoom=1.5 → spacer=labelDomW × 0.25（左右各 25%）
 *   zoom=2.0 → spacer=labelDomW × 0.5（左右各 50%）
 */
export function computeSpacer(labelDomWidth, zoom) {
  if (!labelDomWidth || !zoom || zoom <= 1) return 0
  return Math.round(labelDomWidth * (zoom - 1) / 2)
}

/**
 * 让 label 视觉中心对齐屏幕中心，并同步更新 wrapScrolling 状态
 * 
 * @param {HTMLElement} wrapEl           外层滚动容器 DOM
 * @param {object}      [vm]             可选：Vue 组件 this（用于更新状态）
 * @param {string}      [scrollingKey]   可选：data 里记录溢出状态的字段名
 * @returns {number}   maxScrollLeft     当前可滚动最大值（scrollWidth - clientWidth）
 * 
 * 逻辑：
 *   max <= 0 → 内容不溢出 → justify-content:center 直接居中，scrollLeft=0
 *   max > 0  → 内容溢出   → flex-start + scrollLeft = max/2 居中
 */
export function centerScroll(wrapEl, vm, scrollingKey) {
  if (!wrapEl) return 0
  var max = wrapEl.scrollWidth - wrapEl.clientWidth
  var scrolling = max > 0
  // 同步状态（触发 Vue 响应式更新 justify-content）
  if (vm && scrollingKey) {
    vm.$set ? vm.$set(vm, scrollingKey, scrolling) : (vm[scrollingKey] = scrolling)
  }
  if (max <= 0) {
    wrapEl.scrollLeft = 0
    return 0
  }
  var target = Math.round(max / 2)
  wrapEl.scrollLeft = target
  // 某些浏览器 scrollWidth 异步更新，下一帧再设一次确保生效
  if (typeof requestAnimationFrame === 'function') {
    requestAnimationFrame(function() {
      if (wrapEl.scrollLeft !== target) {
        wrapEl.scrollLeft = target
        // rAF 里再检查一次溢出状态（可能异步布局变化）
        if (vm && scrollingKey) {
          var max2 = wrapEl.scrollWidth - wrapEl.clientWidth
          var scrolling2 = max2 > 0
          if (scrolling2 !== scrolling) {
            vm.$set ? vm.$set(vm, scrollingKey, scrolling2) : (vm[scrollingKey] = scrolling2)
          }
        }
      }
    })
  }
  return max
}

/**
 * 一次性完整初始化：watch(zoom/labelWidth) + 首次居中 + resize 监听
 * 
 * @param {object} vm       Vue 组件 this
 * @param {string} wrapRef  wrap 的 ref 名称
 * @param {string} labelDomWidthKey  label DOM 宽度的 data/computed 字段名
 * @param {string} zoomKey  zoom 的 data 字段名
 * @param {string} [scrollingStateKey]  可选：wrap 溢出状态的 data 字段名
 */
export function setupLabelScroll(vm, wrapRef, labelDomWidthKey, zoomKey, scrollingStateKey) {
  // 首次居中（等模板渲染完 + async 数据回来）
  var doCenter = function() {
    centerScroll(vm.$refs[wrapRef], vm, scrollingStateKey)
  }
  // 多次重试，保证异步加载的模板数据回来后也能居中
  ;[0, 100, 400, 800, 1600].forEach(function(d) {
    setTimeout(doCenter, d)
  })
  
  // zoom 或 labelWidth 变化时重新居中
  vm.$watch(zoomKey, function() {
    vm.$nextTick(doCenter)
  })
  vm.$watch(labelDomWidthKey, function() {
    vm.$nextTick(doCenter)
  })

  // 视口 resize 时重新检测溢出状态 + 居中
  vm._labelScrollResize = function() {
    doCenter()
  }
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', vm._labelScrollResize)
  }
}

/**
 * 容器内等比缩放（fit）——用于打印预览页、发货单/小票查看页
 * 
 * @param {number} paperW      纸张/标签 DOM 布局宽度（px）
 * @param {number} paperH      纸张/标签 DOM 布局高度（px）
 * @param {number} containerW   容器可视宽度（px，通常是 wrap.clientWidth）
 * @param {number} containerH   容器可视高度（px，通常是 wrap.clientHeight）
 * @param {number} [padding=12] 四周留白（px）
 * @param {number} [maxScale=1] 最大缩放上限（默认 1：不放大，只缩小）
 * @returns {number}            缩放因子
 * 
 * 用法（Vue computed）：
 *   paperScale() {
 *     var wrap = this.$refs.paperWrap
 *     return computeFitScale(this.paperW, this.paperH,
 *       wrap ? wrap.clientWidth : window.innerWidth,
 *       wrap ? wrap.clientHeight : window.innerHeight,
 *       12, 1)
 *   }
 */
export function computeFitScale(paperW, paperH, containerW, containerH, padding, maxScale) {
  padding = padding == null ? 12 : padding
  maxScale = maxScale == null ? 1 : maxScale
  var availW = containerW - padding * 2
  var availH = containerH - padding * 2
  if (availW <= 0 || availH <= 0 || paperW <= 0 || paperH <= 0) return maxScale
  var scaleW = availW / paperW
  var scaleH = availH / paperH
  return Math.min(maxScale, scaleW, scaleH)
}