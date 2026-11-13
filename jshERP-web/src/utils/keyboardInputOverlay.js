/**
 * 移动端键盘输入弹层
 *
 * 手机浏览器弹出软键盘时会遮挡页面输入框。这里不再滚动页面，而是：
 * 1. 输入框获得焦点时让页面保持原状（记录并恢复滚动位置）；
 * 2. 在键盘上方弹出独立输入弹层，预填该输入框的当前值并自动聚焦、全选；
 * 3. 键盘收起或点击「完成」时把弹层内容回填到原输入框；
 * 4. 点击「取消」则不回填，直接关闭弹层。
 *
 * 由 main.js 全局安装，页面无需接入。
 */

const OVERLAY_ID = 'kb-input-overlay'

// 不需要弹层接管的输入类型（由系统原生选择器或特殊交互处理）
const SKIP_INPUT_TYPES = [
  'button', 'submit', 'reset', 'checkbox', 'radio', 'file', 'range',
  'color', 'date', 'time', 'datetime-local', 'month', 'week', 'hidden',
  'image', 'password'
]

// 带搜索能力的组合组件，其输入框需要保留原始焦点
const SKIP_SELECTOR = '.ant-select, .ant-cascader, .ant-tree-select, .ant-picker, [role="combobox"], [aria-autocomplete]'

let installed = false
let activeEl = null
let rootEl = null
let fieldEl = null
let touchStartValue = ''
let scrollSnapshot = []
let baselineHeight = 0
let viewportBound = false

/** 仅手机/平板等触摸设备启用，桌面端保持原生输入体验 */
function isMobileEnv () {
  if (typeof window === 'undefined') return false
  let coarse = false
  try {
    coarse = !!(window.matchMedia && window.matchMedia('(pointer: coarse)').matches)
  } catch (e) {
    coarse = false
  }
  const ua = navigator.userAgent || ''
  const touchUa = /Android|iPhone|iPad|iPod|Windows Phone|HarmonyOS|Mobile/i.test(ua)
  return (coarse || touchUa) && window.innerWidth <= 1024
}

/** 判断元素是否需要接管 */
function isEditableTarget (el) {
  if (!el || el.nodeType !== 1) return false
  const tag = el.tagName
  if (tag !== 'INPUT' && tag !== 'TEXTAREA') return false
  if (el.closest('#' + OVERLAY_ID)) return false
  if (el.disabled || el.readOnly) return false
  if (el.getAttribute('data-kb-overlay') === 'off') return false
  if (el.closest(SKIP_SELECTOR)) return false
  if (tag === 'INPUT') {
    const type = (el.getAttribute('type') || 'text').toLowerCase()
    if (SKIP_INPUT_TYPES.indexOf(type) >= 0) return false
  }
  const rect = el.getBoundingClientRect()
  return !!(rect.width || rect.height)
}

/** 估算软键盘占用高度；优先使用可视视口，不可用时按窗口高度变化兜底 */
function getKeyboardHeight () {
  const innerHeight = window.innerHeight || 0
  const vv = window.visualViewport
  if (vv) {
    const gap = Math.round(innerHeight - (vv.height + vv.offsetTop))
    return gap > 60 ? gap : 0
  }
  const base = baselineHeight || innerHeight
  const gap = base - innerHeight
  return gap > 80 ? gap : 0
}

/** 记录页面滚动位置，键盘弹出后用于恢复原状 */
function snapshotScroll (el) {
  scrollSnapshot = []
  try {
    const docEl = document.scrollingElement || document.documentElement
    scrollSnapshot.push({ el: docEl, top: docEl.scrollTop, left: docEl.scrollLeft })
    let node = el && el.parentElement
    while (node && node !== document.body) {
      const style = window.getComputedStyle(node)
      const scrollable = /(auto|scroll)/.test(style.overflowY) && node.scrollHeight > node.clientHeight
      if (scrollable) {
        scrollSnapshot.push({ el: node, top: node.scrollTop, left: node.scrollLeft })
      }
      node = node.parentElement
    }
  } catch (e) {
    scrollSnapshot = []
  }
}

function restoreScroll () {
  scrollSnapshot.forEach(item => {
    if (!item.el) return
    if (item.el.scrollTop !== item.top) item.el.scrollTop = item.top
    if (item.el.scrollLeft !== item.left) item.el.scrollLeft = item.left
  })
}

/** 创建（或复用）弹层 DOM */
function ensureOverlay () {
  if (rootEl) return rootEl
  const root = document.createElement('div')
  root.id = OVERLAY_ID
  root.style.cssText = [
    'position:fixed',
    'left:0',
    'right:0',
    'bottom:0',
    'z-index:100000',
    'display:none',
    'padding:8px 10px',
    'box-sizing:border-box',
    'background:#fff',
    'border-top:1px solid #e8e8e8',
    'border-radius:12px 12px 0 0',
    'box-shadow:0 -4px 16px rgba(0,0,0,.12)',
    'font-size:14px'
  ].join(';')

  const row = document.createElement('div')
  row.style.cssText = 'display:flex;align-items:flex-end;gap:8px'

  const maskWrap = document.createElement('div')
  maskWrap.style.cssText = 'flex:1;min-width:0;display:flex'

  const field = document.createElement('input')
  field.type = 'text'
  field.setAttribute('data-kb-field', '1')
  field.style.cssText = [
    'flex:1',
    'min-width:0',
    'height:44px',
    'padding:0 12px',
    'box-sizing:border-box',
    'border:1px solid #d9d9d9',
    'border-radius:8px',
    'font-size:15px',
    'color:#262626',
    'background:#fff',
    'outline:none'
  ].join(';')
  maskWrap.appendChild(field)

  const cancelBtn = document.createElement('button')
  cancelBtn.type = 'button'
  cancelBtn.textContent = '取消'
  cancelBtn.style.cssText = 'flex:0 0 auto;height:44px;padding:0 10px;border:none;border-radius:8px;background:#f5f5f5;color:#595959;font-size:15px'

  const okBtn = document.createElement('button')
  okBtn.type = 'button'
  okBtn.textContent = '完成'
  okBtn.style.cssText = 'flex:0 0 auto;height:44px;padding:0 14px;border:none;border-radius:8px;background:#1890ff;color:#fff;font-size:15px'

  // 按钮按下时阻止输入框失焦，保证取消/完成由 click 统一处理
  const keepFocus = e => e.preventDefault()
  cancelBtn.addEventListener('mousedown', keepFocus)
  okBtn.addEventListener('mousedown', keepFocus)
  cancelBtn.addEventListener('touchstart', keepFocus, { passive: false })
  okBtn.addEventListener('touchstart', keepFocus, { passive: false })
  cancelBtn.addEventListener('click', cancel)
  okBtn.addEventListener('click', commit)

  row.appendChild(maskWrap)
  row.appendChild(cancelBtn)
  row.appendChild(okBtn)
  root.appendChild(row)
  document.body.appendChild(root)

  rootEl = root
  fieldEl = field
  bindOverlayEvents(field)
  return root
}

function bindOverlayEvents (field) {
  // 键盘收起导致弹层输入框失焦：视为确认，回填原输入框
  field.addEventListener('blur', () => {
    if (!activeEl) return
    setTimeout(() => {
      if (!activeEl) return
      if (document.activeElement && rootEl.contains(document.activeElement)) return
      commit()
    }, 0)
  })
  field.addEventListener('input', () => {
    if (field.tagName !== 'TEXTAREA') return
    field.style.height = 'auto'
    field.style.height = Math.min(field.scrollHeight, 96) + 'px'
  })
}

/** 按原输入框属性重建弹层输入控件 */
function buildField (el) {
  const isTextarea = el.tagName === 'TEXTAREA'
  const current = fieldEl
  let next
  if (isTextarea) {
    next = document.createElement('textarea')
    next.rows = 2
    next.style.cssText = [
      'flex:1', 'min-width:0', 'max-height:96px', 'padding:10px 12px',
      'box-sizing:border-box', 'border:1px solid #d9d9d9', 'border-radius:8px',
      'font-size:15px', 'line-height:20px', 'color:#262626', 'background:#fff',
      'outline:none', 'resize:none'
    ].join(';')
  } else {
    next = document.createElement('input')
    next.type = 'text'
    next.style.cssText = current ? current.style.cssText : ''
    next.style.height = '44px'
  }
  next.setAttribute('data-kb-field', '1')
  const type = (el.getAttribute('type') || 'text').toLowerCase()
  if (!isTextarea && type) next.type = type
  const inputmode = el.getAttribute('inputmode')
  if (inputmode) next.setAttribute('inputmode', inputmode)
  const maxlength = el.getAttribute('maxlength')
  if (maxlength) next.setAttribute('maxlength', maxlength)
  const placeholder = el.getAttribute('placeholder')
  if (placeholder) next.setAttribute('placeholder', placeholder)
  next.value = el.value || ''

  current.parentNode.replaceChild(next, current)
  fieldEl = next
  bindOverlayEvents(next)
  return next
}

function positionOverlay () {
  if (!rootEl || !activeEl) return
  rootEl.style.bottom = getKeyboardHeight() + 'px'
}

function onViewportChange () {
  positionOverlay()
}

function bindViewport () {
  if (viewportBound) return
  viewportBound = true
  window.addEventListener('resize', onViewportChange)
  window.addEventListener('orientationchange', onViewportChange)
  window.addEventListener('scroll', onViewportChange, true)
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', onViewportChange)
    window.visualViewport.addEventListener('scroll', onViewportChange)
  }
}

function unbindViewport () {
  if (!viewportBound) return
  viewportBound = false
  window.removeEventListener('resize', onViewportChange)
  window.removeEventListener('orientationchange', onViewportChange)
  window.removeEventListener('scroll', onViewportChange, true)
  if (window.visualViewport) {
    window.visualViewport.removeEventListener('resize', onViewportChange)
    window.visualViewport.removeEventListener('scroll', onViewportChange)
  }
}

/** 打开弹层 */
function openOverlay (el) {
  if (!rootEl) return
  touchStartValue = el.value || ''
  buildField(el)
  rootEl.style.display = 'block'
  positionOverlay()
  bindViewport()
  // 原输入框先失焦，避免系统再弹一次键盘
  try { el.blur() } catch (e) {}
  try {
    fieldEl.focus({ preventScroll: true })
  } catch (e) {
    try { fieldEl.focus() } catch (e2) {}
  }
  try {
    if (fieldEl.setSelectionRange) {
      const len = fieldEl.value.length
      fieldEl.setSelectionRange(0, len)
    }
  } catch (e) {}
  // 抵消浏览器为聚焦输入框产生的自动滚动，保持页面原状
  requestAnimationFrame(restoreScroll)
  setTimeout(restoreScroll, 120)
  setTimeout(restoreScroll, 320)
}

/** 触发原生事件，兼容不支持 Event 构造器的旧浏览器 */
function fireEvent (el, type) {
  let evt
  try {
    evt = new Event(type, { bubbles: true })
  } catch (e) {
    evt = document.createEvent('HTMLEvents')
    evt.initEvent(type, true, false)
  }
  el.dispatchEvent(evt)
}

/** 回填并关闭 */
function commit () {
  const el = activeEl
  if (!el) return
  const value = fieldEl ? fieldEl.value : ''
  activeEl = null
  unbindViewport()
  if (rootEl) rootEl.style.display = 'none'
  if (document.body.contains(el)) {
    if (el.value !== value) {
      el.value = value
      fireEvent(el, 'input')
      fireEvent(el, 'change')
    }
  }
  restoreScroll()
}

/** 取消：不回填，直接关闭 */
function cancel () {
  activeEl = null
  unbindViewport()
  if (fieldEl) fieldEl.value = touchStartValue
  if (rootEl) rootEl.style.display = 'none'
  restoreScroll()
}

function onFocusIn (e) {
  const el = e.target
  if (!isEditableTarget(el)) return
  if (activeEl === el) return
  // 切换输入框：先把上一项回填
  if (activeEl) commit()
  activeEl = el
  snapshotScroll(el)
  openOverlay(el)
}

function onDocumentClick (e) {
  if (!activeEl || !rootEl) return
  if (rootEl.contains(e.target)) return
  // 点击其它输入框由 focusin 负责切换，这里只处理点到弹层之外（例如关闭了原弹层）
  if (isEditableTarget(e.target)) return
  commit()
}

export function installKeyboardInputOverlay () {
  if (installed) return
  installed = true
  baselineHeight = window.innerHeight || 0
  window.addEventListener('resize', () => {
    if (!activeEl && window.innerHeight > baselineHeight) baselineHeight = window.innerHeight
  })
  document.addEventListener('focusin', onFocusIn, true)
  document.addEventListener('click', onDocumentClick, true)
}

export default installKeyboardInputOverlay
