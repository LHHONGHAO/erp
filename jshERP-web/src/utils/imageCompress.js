/**
 * 按裁剪区域生成正方形商品图
 * 1. 以 rect（原图像素坐标）裁切，等比缩放到 size×size
 * 2. JPEG 质量从 0.9 起逐级降低，仍超限则缩小尺寸，直到不超过 maxSizeKB
 * @param {HTMLImageElement} img 已加载完成的原图
 * @param {{x:number,y:number,w:number,h:number}} rect 裁剪区域（原图像素）
 * @param {{size?:number,maxSizeKB?:number,name?:string}} options
 * @returns {Promise<File>}
 */
export function cropImageFile(img, rect, options) {
  var opts = options || {}
  var size = opts.size || 600
  var maxSize = (opts.maxSizeKB || 500) * 1024
  var name = (opts.name || 'image').replace(/\.[^.]+$/, '') + '_crop.jpg'
  return new Promise(function(resolve, reject) {
    try {
      var canvas = document.createElement('canvas')
      var ctx = canvas.getContext('2d')
      var quality = 0.9
      var render = function() {
        canvas.width = size
        canvas.height = size
        ctx.fillStyle = '#fff'
        ctx.fillRect(0, 0, size, size)
        if (ctx.imageSmoothingQuality) ctx.imageSmoothingQuality = 'high'
        ctx.drawImage(img, rect.x, rect.y, rect.w, rect.h, 0, 0, size, size)
        canvas.toBlob(function(blob) {
          if (!blob) {
            reject(new Error('crop failed'))
            return
          }
          if (blob.size <= maxSize) {
            resolve(new File([blob], name, { type: 'image/jpeg' }))
            return
          }
          if (quality > 0.5) {
            quality -= 0.1
            render()
            return
          }
          if (size <= 320) {
            resolve(new File([blob], name, { type: 'image/jpeg' }))
            return
          }
          size = Math.round(size * 0.8)
          quality = 0.9
          render()
        }, 'image/jpeg', quality)
      }
      render()
    } catch (e) {
      reject(e)
    }
  })
}

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
