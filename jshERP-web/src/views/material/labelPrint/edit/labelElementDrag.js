/**
 * 编辑态元素拖动控制器：移动 / 缩放 / 吸附 / 点击选中切换。
 * 编辑页只把外框组件抛出的 drag-start 转给这里，拖动逻辑全站只走这一条路径。
 * 通过编辑器实例读写布局状态，不复制一份数据，保证与面板、撤销等行为一致。
 */
export function createLabelElementDrag(vm) {
  // 拖动会话状态；仅控制器内部使用
  var dragging = createDraggingState()
  var pointerDownKey = null
  var selectedBeforeDown = []
  var prevFocus = null
  var blockDragKeys = null
  var pressTimer = null
  var longPressRequired = false
  var longPressReady = false
  var pressCancelled = false

  function clearLongPressTimer() {
    if (pressTimer) {
      clearTimeout(pressTimer)
      pressTimer = null
    }
  }

  function selectionTargetsFor(key) {
    var blockKeys = vm.specBlockKeysOf(key)
    var hiddenKeys = vm.hiddenItemKeys || []
    var targets = blockKeys.length
      ? blockKeys.filter(function(k) { return hiddenKeys.indexOf(k) === -1 })
      : [key]
    return targets.length ? targets : [key]
  }

  function createDraggingState() {
    return {
      active: false, mode: '', key: '',
      startX: 0, startY: 0, origX: 0, origY: 0, origW: 0, origH: 0,
      minW: 12, minH: 12, moved: false, multiDrag: false,
      blockDragKeys: null, dragOrigins: null, snapTargets: null, keyW: 0, keyH: 0
    }
  }

  // 拖拽移动/缩放：统一用 Pointer Events + setPointerCapture，
  // 手指移出元素后事件仍持续派发到捕获元素，不依赖 touch passive 机制
  // 选中逻辑也在这里完成（彻底移除 click 事件避免时序冲突）
  function startPointerDrag(key, e, mode) {
    clearLongPressTimer()
    // 已选中的文字可直接拖动；未选中的文字仍需先长按获取焦点，再跟随手指移动
    var alreadySelected = vm.selectedKeys.indexOf(key) !== -1
    longPressRequired = mode === 'move' && vm.isTextElem && vm.isTextElem(key) && !alreadySelected
    longPressReady = false
    pressCancelled = false
    // 记录 pointerdown 瞬间的快照，留到 pointerup 做 toggle 判断
    pointerDownKey = key
    selectedBeforeDown = vm.selectedKeys.slice()
    prevFocus = vm.currentAttrKey
    // 不在这里改 currentAttrKey！焦点只能在 pointerup toggle 或长按激活后决定
    // 规格块整体拖动：属性名 + 全部规格值跟随同一个位移
    var blockKeys = vm.specBlockKeysOf(key)
    blockDragKeys = mode === 'move' && blockKeys.length > 1 ? blockKeys : null
    var willMultiDrag = (mode === 'move' || mode === 'resize') &&
      (vm.selectedKeys.length > 1 || !!blockDragKeys)
    startActualDrag(key, e, mode, willMultiDrag)
    if (longPressRequired) {
      pressTimer = setTimeout(function() {
        pressTimer = null
        if (!dragging.active || pressCancelled) return
        longPressReady = true
        // 长按激活文本手柄；多选模式下保留已有选择
        var base = vm.multiSelectMode ? vm.selectedKeys.slice() : []
        selectionTargetsFor(key).forEach(function(k) {
          if (base.indexOf(k) === -1) base.push(k)
        })
        vm.selectedKeys = base
        vm.currentAttrKey = key
        if (navigator.vibrate) navigator.vibrate(10)
      }, 600)
    }
  }

  // 测量元素文字本身的自然尺寸（不换行、不裁剪），用于限制缩放边框的最小值
  function measureContentSize(key) {
    var el = document.querySelector('.lem-label .lem-elem[data-key="' + key + '"]')
    if (!el) return { width: 0, height: 0 }
    var clone = el.cloneNode(true)
    clone.style.position = 'absolute'
    clone.style.visibility = 'hidden'
    clone.style.left = '0'
    clone.style.top = '0'
    clone.style.width = 'auto'
    clone.style.height = 'auto'
    clone.style.whiteSpace = 'nowrap'
    clone.style.overflow = 'visible'
    var inner = clone.querySelector('.lem-elem-inner')
    if (inner) {
      inner.style.width = 'auto'
      inner.style.height = 'auto'
      inner.style.overflow = 'visible'
    }
    var handles = clone.querySelectorAll('.lem-resize-handle, .lem-delete-btn')
    Array.prototype.forEach.call(handles, function(node) {
      if (node.parentNode) node.parentNode.removeChild(node)
    })
    el.parentNode.appendChild(clone)
    var size = { width: clone.offsetWidth, height: clone.offsetHeight }
    el.parentNode.removeChild(clone)
    return size
  }

  function startActualDrag(key, e, mode, multiDrag) {
    // 锚点可能还没有样式（旧模板或名称被隐藏）：先补一个空样式，拖动后再写入几何
    if (!vm.attrStyles[key]) vm.$set(vm.attrStyles, key, {})
    var style = vm.attrStyles[key] || {}
    // resize 模式下如果没有显式 width/height，从 DOM 实际尺寸除以 zoom 得到
    var origW = style.width || 0
    var origH = style.height || 0
    if (mode === 'resize' && (!origW || !origH)) {
      var el = document.querySelector('.lem-elem[data-key="' + key + '"]')
      if (el) {
        var r = el.getBoundingClientRect()
        if (!origW) origW = Math.round(r.width / vm.zoom)
        if (!origH) origH = Math.round(r.height / vm.zoom)
      }
    }
    // 规格块未锁宽时没有保存宽度，用当前行布局的自然宽度作为缩放起点
    if (mode === 'resize' && !origW && vm.isSpecAttrKey && vm.isSpecAttrKey(key) && vm.specBlockViews) {
      var specView = vm.specBlockViews.find(function(v) { return v.anchorKey === key })
      if (specView) origW = specView.layout.width
    }
    // 记录整体拖拽的所有元素起点（规格块拖动时包含块内隐藏项，保持布局不散）
    var allKeys = multiDrag ? (blockDragKeys || vm.selectedKeys.slice()) : [key]
    allKeys = allKeys.filter(function(k) { return !!vm.attrStyles[k] })
    var dragOrigins = {}
    allKeys.forEach(function(k) {
      var s = vm.attrStyles[k] || {}
      dragOrigins[k] = { x: s.x || 0, y: s.y || 0, width: s.width || 0, height: s.height || 0 }
    })
    // 缩放边框时的最小尺寸
    var minW = 12
    var minH = 12
    if (mode === 'resize') {
      if (vm.isSpecAttrKey && vm.isSpecAttrKey(key)) {
        // 规格块：最小宽度 = 块内全部属性名排开所需宽度，名称不压缩
        minW = vm.specBlockMinWidth ? vm.specBlockMinWidth(key) : 12
        minH = 12
      } else if (vm.isTextElem(key)) {
        // 文字类允许拖窄到文字自然宽度以下：由文字自动折行，高度随内容增长
        minW = 12
        minH = 12
      } else if (vm.isCodeElem && vm.isCodeElem(key)) {
        // 条码/二维码：手柄自由调节宽高，不再按图片自然尺寸限制最小值
        minW = 12
        minH = 12
      } else {
        var natural = measureContentSize(key)
        if (natural.width > 0) minW = natural.width
        if (natural.height > 0) minH = natural.height
      }
    }
    dragging = {
      active: true,
      mode: mode,
      key: key,
      startX: e.clientX,
      startY: e.clientY,
      origX: style.x || 0,
      origY: style.y || 0,
      origW: origW,
      origH: origH,
      minW: minW,
      minH: minH,
      moved: false,
      multiDrag: multiDrag,
      dragOrigins: dragOrigins,
      // 吸附基准：未参与本次拖动的元素外接矩形
      snapTargets: collectSnapTargets(allKeys),
      keyW: vm.getItemSize(key).w,
      keyH: vm.getItemSize(key).h
    }
    try {
      if (e.currentTarget.setPointerCapture) {
        e.currentTarget.setPointerCapture(e.pointerId)
      }
    } catch (err) { /* 忽略捕获失败，仍有 document 级监听兜底 */ }
    document.addEventListener('pointermove', onPointerMove)
    document.addEventListener('pointerup', onPointerUp)
    document.addEventListener('pointercancel', onPointerUp)
  }

  function unbindPointerEvents() {
    document.removeEventListener('pointermove', onPointerMove)
    document.removeEventListener('pointerup', onPointerUp)
    document.removeEventListener('pointercancel', onPointerUp)
  }

  // 收集吸附基准：除本次拖动元素外的所有已加载元素外接矩形
  function collectSnapTargets(movingKeys) {
    return vm.selectedAttrs.filter(function(key) {
      return movingKeys.indexOf(key) === -1
    }).map(function(key) {
      var s = vm.attrStyles[key] || {}
      var size = vm.getItemSize(key)
      return {
        key: key,
        x: s.x || 0,
        y: s.y || 0,
        w: s.width > 0 ? s.width : size.w,
        h: s.height > 0 ? s.height : size.h
      }
    })
  }

  // 位置吸附：与最近元素的左/中/右、上/中/下对齐，返回吸附后的坐标与对齐线
  function snapPosition(x, y, targets, size) {
    var TH = 6 / vm.zoom
    var ownV = [0, size.w / 2, size.w]
    var ownH = [0, size.h / 2, size.h]
    var bestV = null
    var bestH = null
    ;(targets || []).forEach(function(t) {
      var tv = [t.x, t.x + t.w / 2, t.x + t.w]
      var th = [t.y, t.y + t.h / 2, t.y + t.h]
      tv.forEach(function(line) {
        ownV.forEach(function(off) {
          var diff = line - (x + off)
          if (Math.abs(diff) <= TH && (!bestV || Math.abs(diff) < Math.abs(bestV.diff))) {
            bestV = { diff: diff, line: line }
          }
        })
      })
      th.forEach(function(line) {
        ownH.forEach(function(off) {
          var diff = line - (y + off)
          if (Math.abs(diff) <= TH && (!bestH || Math.abs(diff) < Math.abs(bestH.diff))) {
            bestH = { diff: diff, line: line }
          }
        })
      })
    })
    return {
      x: bestV ? x + bestV.diff : x,
      y: bestH ? y + bestH.diff : y,
      guides: { v: bestV ? bestV.line : null, h: bestH ? bestH.line : null }
    }
  }

  function onPointerMove(e) {
    if (!dragging.active) return
    var d = dragging
    var dx = e.clientX - d.startX
    var dy = e.clientY - d.startY
    if (longPressRequired && !longPressReady) {
      // 文本未达到长按时长就移动：取消本次激活，不触发拖动或点击选中
      if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
        pressCancelled = true
        d.moved = true
        clearLongPressTimer()
      }
      return
    }
    if (!d.moved && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
      d.moved = true
    }
    if (d.mode === 'move') {
      // 先按指针位移算出目标位置，再吸附到最近元素的对齐线
      var rawX = d.origX + dx / vm.zoom
      var rawY = d.origY + dy / vm.zoom
      var snapped = snapPosition(rawX, rawY, d.snapTargets, { w: d.keyW, h: d.keyH })
      vm.guides = snapped.guides
      if (d.multiDrag && d.dragOrigins) {
        // 多选整体移动（不限制边界，可拖到屏幕任意可视位置）
        var offX = snapped.x - d.origX
        var offY = snapped.y - d.origY
        Object.keys(d.dragOrigins).forEach(function(k) {
          var origin = d.dragOrigins[k]
          vm.$set(vm.attrStyles[k], 'x', Math.round((origin.x || 0) + offX))
          vm.$set(vm.attrStyles[k], 'y', Math.round((origin.y || 0) + offY))
        })
      } else {
        // 单个元素移动（不限制边界，文字标签超出屏幕时可拖到任意可视位置）
        vm.$set(vm.attrStyles[d.key], 'x', Math.round(snapped.x))
        vm.$set(vm.attrStyles[d.key], 'y', Math.round(snapped.y))
      }
    } else if (d.mode === 'resize') {
      var minW = d.minW || 12
      if (vm.isSpecAttrKey && vm.isSpecAttrKey(d.key)) {
        // 规格块：只改锚点块宽（几何唯一归属），文案按块宽重新折行
        var blockW = Math.max(minW, Math.round(d.origW + dx / vm.zoom))
        vm.$set(vm.attrStyles[d.key], 'width', blockW)
        // 记录用户显式设定的块宽，重新加载时按该宽度折行
        vm.$set(vm.attrStyles[d.key], 'specWidthLocked', true)
        if (vm.attrStyles[d.key].height) vm.$delete(vm.attrStyles[d.key], 'height')
        return
      }
      if (vm.isTextElem(d.key)) {
        // 文字类：只改宽度，清掉固定高度，让文字折行后自动撑高/回缩
        var textW = Math.max(minW, Math.round(d.origW + dx / vm.zoom))
        vm.$set(vm.attrStyles[d.key], 'width', textW)
        if (vm.attrStyles[d.key].height) vm.$delete(vm.attrStyles[d.key], 'height')
      } else {
        // 其他元素（条码/表格等）：保持原宽高联动
        var minH = d.minH || 12
        var newW = Math.max(minW, Math.round(d.origW + dx / vm.zoom))
        var newH = Math.max(minH, Math.round(d.origH + dy / vm.zoom))
        vm.$set(vm.attrStyles[d.key], 'width', newW)
        vm.$set(vm.attrStyles[d.key], 'height', newH)
      }
    }
  }

  function onPointerUp() {
    clearLongPressTimer()
    var prevKeys = selectedBeforeDown || []
    var pKey = pointerDownKey
    var didMove = dragging.moved
    var dragMode = dragging.mode
    var wasLongPress = longPressReady
    if (dragging.active) {
      dragging.active = false
      dragging.moved = false
      vm.guides = { v: null, h: null }
      vm.pushSnapshot()
    }
    // 没移动 + 不是 resize 手柄操作 → 点击意图，执行 toggle
    if (!didMove && !wasLongPress && pKey && (!dragMode || dragMode === 'move')) {
      // 规格块内任一元素：点击按整块选中/取消（已关闭的规格值保持关闭），普通元素按单项处理
      var targets = selectionTargetsFor(pKey)
      var allSelected = targets.every(function(k) { return prevKeys.indexOf(k) > -1 })
      if (allSelected) {
        // 之前已选中 → 整块移除
        var next = prevKeys.filter(function(k) { return targets.indexOf(k) === -1 })
        vm.selectedKeys = next
        // 焦点逻辑：
        //   移除的是 prevFocus → 无焦点（不自动转给剩余元素）
        //   移除的是非焦点  → prevFocus 保持不变
        if (prevFocus && targets.indexOf(prevFocus) > -1) {
          vm.currentAttrKey = null
        } else {
          vm.currentAttrKey = prevFocus
        }
        // 全部取消后退出多选状态
        if (!next.length) vm.multiSelectMode = false
      } else {
        // 之前未选中：规格块整块选中；多选状态下追加，普通单击则单选
        var base = vm.multiSelectMode ? prevKeys.slice() : []
        targets.forEach(function(k) {
          if (base.indexOf(k) === -1) base.push(k)
        })
        vm.selectedKeys = base
        vm.currentAttrKey = pKey
      }
      // 选择/取消选择也计入撤销历史
      vm.pushSnapshot()
    }
    blockDragKeys = null
    // 移动过 → 拖拽意图，pointerdown 没改 currentAttrKey，所以这里不需要恢复焦点
    pointerDownKey = null
    selectedBeforeDown = []
    prevFocus = null
    longPressRequired = false
    longPressReady = false
    pressCancelled = false
    unbindPointerEvents()
  }

  return {
    startPointerDrag: startPointerDrag,
    // 编辑页销毁时解绑全局监听并取消未完成的长按
    destroy: function() {
      clearLongPressTimer()
      unbindPointerEvents()
    }
  }
}
