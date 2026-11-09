import ls from './storage'

const DISMISS_KEY = 'mobile_pwa_dismissed'
const DISMISS_EXPIRE = 30 * 24 * 60 * 60 * 1000

let deferredPrompt = null
let initialized = false
const listeners = []

function notify () {
  listeners.forEach(fn => fn())
}

export function isStandalone () {
  return (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches) ||
    window.navigator.standalone === true
}

export function isIOS () {
  return /iphone|ipad|ipod/i.test(window.navigator.userAgent)
}

export function canInstall () {
  return !!deferredPrompt
}

export function isInstallTipDismissed () {
  return ls.get(DISMISS_KEY) === true
}

export function dismissInstallTip () {
  ls.set(DISMISS_KEY, true, DISMISS_EXPIRE)
  notify()
}

export async function promptInstall () {
  if (!deferredPrompt) {
    return false
  }
  deferredPrompt.prompt()
  const result = await deferredPrompt.userChoice
  deferredPrompt = null
  notify()
  return !!result && result.outcome === 'accepted'
}

export function subscribeInstall (fn) {
  listeners.push(fn)
  return () => {
    const index = listeners.indexOf(fn)
    if (index > -1) listeners.splice(index, 1)
  }
}

export function initPwa () {
  if (initialized) {
    return
  }
  initialized = true
  // Android / Chrome：保存安装事件，供页面按钮主动触发
  window.addEventListener('beforeinstallprompt', event => {
    event.preventDefault()
    deferredPrompt = event
    notify()
  })
  window.addEventListener('appinstalled', () => {
    deferredPrompt = null
    notify()
  })
}
