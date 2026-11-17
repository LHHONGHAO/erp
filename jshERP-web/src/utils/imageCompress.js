/**
 * 图片上传前压缩
 * 1. 小于 maxSizeKB 且尺寸不超限的图片原样返回
 * 2. 长边超过 maxEdge 先等比缩小
 * 3. 再逐步降低质量与尺寸，直到体积不超过 maxSizeKB
 * 动图（gif）无法安全重编码，原样返回
 */
export function compressImageFile(file, options) {
  var opts = options || {}
  var maxSize = (opts.maxSizeKB || 500) * 1024
  var maxEdge = opts.maxEdge || 1600
  if (!file || !/^image\//.test(file.type || '') || file.type === 'image/gif') {
    return Promise.resolve(file)
  }
  return new Promise(function(resolve) {
    var url = URL.createObjectURL(file)
    var img = new Image()
    img.onload = function() {
      URL.revokeObjectURL(url)
      var needResize = Math.max(img.width, img.height) > maxEdge
      if (file.size <= maxSize && !needResize) {
        resolve(file)
        return
      }
      var canvas = document.createElement('canvas')
      var ctx = canvas.getContext('2d')
      var scale = Math.min(1, maxEdge / Math.max(img.width, img.height))
      var width = Math.max(1, Math.round(img.width * scale))
      var height = Math.max(1, Math.round(img.height * scale))
      var quality = 0.9
      var toFile = function() {
        canvas.width = width
        canvas.height = height
        ctx.fillStyle = '#fff'
        ctx.fillRect(0, 0, width, height)
        ctx.drawImage(img, 0, 0, width, height)
        canvas.toBlob(function(blob) {
          if (!blob) {
            resolve(file)
            return
          }
          if (blob.size <= maxSize) {
            var name = (file.name || 'image').replace(/\.[^.]+$/, '') + '.jpg'
            resolve(new File([blob], name, { type: 'image/jpeg' }))
            return
          }
          if (quality > 0.6) {
            quality -= 0.1
            toFile()
            return
          }
          if (width <= 480 && height <= 480) {
            resolve(file)
            return
          }
          width = Math.max(1, Math.round(width * 0.8))
          height = Math.max(1, Math.round(height * 0.8))
          quality = 0.9
          toFile()
        }, 'image/jpeg', quality)
      }
      toFile()
    }
    img.onerror = function() {
      URL.revokeObjectURL(url)
      resolve(file)
    }
    img.src = url
  })
}
