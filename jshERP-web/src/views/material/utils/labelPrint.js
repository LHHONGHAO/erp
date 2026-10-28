/**
 * 标签打印工具
 *
 * 通过隐藏 iframe 打印，不依赖 window.open（避免被拦截、避免弹窗中打印机列表加载不全），
 * 并按标签实际尺寸设置 @page 纸张大小，保证打印位置与预览一致。
 */

var PX_PER_MM = 3.779528

function buildHtml(options) {
  var widthPx = options.widthPx || 320
  var heightPx = options.heightPx || 240
  var widthMm = (widthPx / PX_PER_MM).toFixed(2)
  var heightMm = (heightPx / PX_PER_MM).toFixed(2)
  return '<html><head><title>打印标签</title><style>' +
    '@page{size:' + widthMm + 'mm ' + heightMm + 'mm;margin:0}' +
    'html,body{margin:0;padding:0}' +
    'body{font-family:Arial,"Microsoft YaHei",sans-serif;-webkit-print-color-adjust:exact}' +
    '.label{position:relative;width:' + widthPx + 'px;height:' + heightPx + 'px;overflow:hidden}' +
    '</style></head><body>' +
    '<div class="label">' + (options.bodyHtml || '') + '</div>' +
    '</body></html>'
}

/**
 * 打印标签
 * @param {Object} options
 *   widthPx  标签宽（px）
 *   heightPx 标签高（px）
 *   bodyHtml 标签内容（绝对定位的元素 HTML）
 */
export function printLabel(options) {
  var iframe = document.createElement('iframe')
  iframe.setAttribute('aria-hidden', 'true')
  iframe.style.position = 'fixed'
  iframe.style.left = '-9999px'
  iframe.style.top = '0'
  iframe.style.width = (options.widthPx || 320) + 'px'
  iframe.style.height = (options.heightPx || 240) + 'px'
  iframe.style.border = '0'
  document.body.appendChild(iframe)

  var doc = iframe.contentWindow.document
  doc.open()
  doc.write(buildHtml(options))
  doc.close()

  var win = iframe.contentWindow
  var removed = false
  var cleanup = function() {
    if (removed) return
    removed = true
    if (iframe.parentNode) iframe.parentNode.removeChild(iframe)
  }
  // 打印对话框关闭后清理 iframe
  win.onafterprint = function() { setTimeout(cleanup, 300) }

  // 等文档渲染完成再调起打印，否则部分浏览器打印对话框里打印机列表不完整或内容空白
  setTimeout(function() {
    try {
      win.focus()
      win.print()
    } catch (e) {
      cleanup()
      return
    }
    // 不支持 onafterprint 的浏览器兜底清理
    setTimeout(cleanup, 60000)
  }, 200)
}
