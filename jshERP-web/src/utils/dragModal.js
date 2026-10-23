/**
 * 全局弹窗拖动功能
 * 在 document 上监听 mousedown 事件，自动给所有 a-modal 的标题栏添加拖动功能
 * 拖动时修改 .ant-modal 的 transform 实现位置移动
 * 弹窗关闭后重新打开时自动重置位置
 */
export function initModalDrag() {
  document.addEventListener('mousedown', function (e) {
    var header = e.target.closest('.ant-modal-header')
    if (!header) return
    // 已初始化过则跳过
    if (header.dataset.dragInit === '1') return
    header.dataset.dragInit = '1'
    header.style.cursor = 'move'

    var modal = header.closest('.ant-modal')
    var dragWrap = modal ? modal.parentElement : null
    var isDragging = false
    var startX = 0
    var startY = 0
    var baseX = 0
    var baseY = 0

    header.addEventListener('mousedown', function (e) {
      // 点击关闭按钮或操作按钮时不拖动
      if (e.target.closest('button') || e.target.closest('.ant-modal-close')) return
      isDragging = true
      var match = modal.style.transform.match(/translate\(([-\d.]+)px,\s*([-\d.]+)px\)/)
      baseX = match ? parseFloat(match[1]) : 0
      baseY = match ? parseFloat(match[2]) : 0
      startX = e.clientX
      startY = e.clientY
      e.preventDefault()
    })

    document.addEventListener('mousemove', function (e) {
      if (!isDragging) return
      var x = baseX + (e.clientX - startX)
      var y = baseY + (e.clientY - startY)
      modal.style.transform = 'translate(' + x + 'px, ' + y + 'px)'
    })

    document.addEventListener('mouseup', function () {
      isDragging = false
    })

    // 监听弹窗关闭（display 变为 none），重置位置
    if (dragWrap) {
      var observer = new MutationObserver(function () {
        if (dragWrap.style.display === 'none') {
          modal.style.transform = ''
        }
      })
      observer.observe(dragWrap, { attributes: true, attributeFilter: ['style'] })
    }
  }, true)
}
