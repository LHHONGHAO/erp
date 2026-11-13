import Vue from 'vue'
import Router from 'vue-router'
import { constantRouterMap } from '@/config/router.config'

//update-begin-author:taoyan date:20191011 for:TASK #3214 【优化】访问online功能测试 浏览器控制台抛出异常
try {
  const originalPush = Router.prototype.push
  Router.prototype.push = function push(location) {
    return originalPush.call(this, location).catch(err => err)
  }
} catch (e) {
}
//update-end-author:taoyan date:20191011 for:TASK #3214 【优化】访问online功能测试 浏览器控制台抛出异常

Vue.use(Router)

const router = new Router({
  mode: 'history',
  base: process.env.BASE_URL,
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRouterMap
})

// 异步 chunk 加载失败（旧缓存/编译后 chunk 变更）时页面会一直白屏，
// 这里自动整页刷新一次拿到新的 chunk 清单，10 秒内只重试一次避免刷屏
router.onError(error => {
  const message = (error && error.message) || ''
  if (!/Loading (CSS )?chunk|Importing a module script failed/i.test(message)) return
  try {
    const last = Number(sessionStorage.getItem('jsh.chunkReloadAt') || 0)
    if (Date.now() - last < 10000) return
    sessionStorage.setItem('jsh.chunkReloadAt', String(Date.now()))
  } catch (e) { /* 忽略 */ }
  window.location.reload()
})

export default router