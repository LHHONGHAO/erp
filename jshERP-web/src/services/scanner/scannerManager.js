/**
 * 连续帧确认：相同内容 + 相同格式，在时间窗口内达到次数阈值后只触发一次。
 * 用于过滤偶发误识别，并防止识别成功后重复提交。
 */
export function createFrameConfirmer (options = {}) {
  const minConfirmCount = options.minConfirmCount || 3
  const confirmWindow = options.confirmWindow || 1500
  const onConfirm = options.onConfirm || function () {}
  const onProgress = options.onProgress || function () {}

  let currentKey = ''
  let hits = []
  let locked = false

  function reset () {
    currentKey = ''
    hits = []
    locked = false
    onProgress(0)
  }

  function submit (result) {
    if (locked) return false
    if (!result || !result.content) {
      reset()
      return false
    }
    const now = result.timestamp || Date.now()
    const key = (result.format || 'UNKNOWN') + '|' + result.content
    // 内容或格式变化即视为不连续，重新计数
    if (key !== currentKey) {
      currentKey = key
      hits = []
    }
    hits.push(now)
    hits = hits.filter(item => now - item <= confirmWindow)
    onProgress(hits.length)
    if (hits.length >= minConfirmCount) {
      locked = true
      onConfirm({
        content: result.content,
        format: result.format,
        type: result.type,
        timestamp: now
      })
      return true
    }
    return false
  }

  return {
    submit: submit,
    reset: reset,
    isLocked: function () { return locked }
  }
}
