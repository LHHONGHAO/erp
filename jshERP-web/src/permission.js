import Vue from 'vue'
import router from './router'
import store from './store'
import NProgress from 'nprogress' // progress bar
import 'nprogress/nprogress.css' // progress bar style
import { USER_ID,INDEX_MAIN_PAGE_PATH } from '@/store/mutation-types'
import { generateIndexRouter } from "@/utils/util"

// 将未在后台菜单中配置的移动端页面挂到对应菜单分组下，
// 与商品页共用同一个 TabLayout，避免切换顶层路由时 keep-alive 缓存被销毁导致表单数据丢失
function injectMobilePageRoutes(routes) {
  if (!Array.isArray(routes)) return
  const materialRoute = routes.find(route => (route.path || '').replace(/\/$/, '') === '/material')
  if (!materialRoute || !Array.isArray(materialRoute.children)) return
  const exists = materialRoute.children.some(route => route.path === '/material/category_add')
  if (!exists) {
    materialRoute.children.push({
      path: '/material/category_add',
      name: 'MaterialCategoryAdd',
      meta: { title: '新增品类', keepAlive: true },
      component: () => import('@/views/material/MaterialCategoryAdd')
    })
  }
}

// 打印管理页面由前端内置，挂到首页同一个 TabLayout 下，不依赖后台菜单表
function injectPrintAgentRoute(routes) {
  if (!Array.isArray(routes)) return
  const rootRoute = routes.find(route => route.path === '/')
  if (!rootRoute) return
  if (!Array.isArray(rootRoute.children)) rootRoute.children = []
  const exists = rootRoute.children.some(route => route.path === '/print/agent')
  if (!exists) {
    rootRoute.children.push({
      path: '/print/agent',
      name: 'PrintAgent',
      meta: { title: '打印管理', componentName: 'PrintAgentList', keepAlive: true },
      component: () => import('@/views/print/agent/PrintAgentList')
    })
  }
}

// 地址识别页面由前端内置，注入到「基础资料」菜单分组下，不依赖后台菜单表
function injectAddressRecognizeMenu(menuData) {
  if (!Array.isArray(menuData)) return
  const target = menuData.find(item => (item.url || '').replace(/\/$/, '') === '/systemA' || item.text === '基础资料')
  if (!target) return
  if (!Array.isArray(target.children)) target.children = []
  const exists = target.children.some(item => item.url === '/system/address-recognize')
  if (!exists) {
    target.children.push({
      id: 'address-recognize',
      text: '地址识别',
      icon: 'environment',
      url: '/system/address-recognize',
      component: '/system/AddressRecognize',
      route: '1',
      children: []
    })
  }
}

// AI配置页面由前端内置，注入到「系统管理」菜单分组下，不依赖后台菜单表
function injectAiConfigMenu(menuData) {
  if (!Array.isArray(menuData)) return
  const target = menuData.find(item => (item.url || '').replace(/\/$/, '') === '/system')
  if (!target) return
  if (!Array.isArray(target.children)) target.children = []
  const exists = target.children.some(item => item.url === '/system/ai-config')
  if (!exists) {
    target.children.push({
      id: 'ai-config',
      text: 'AI配置',
      icon: 'setting',
      url: '/system/ai-config',
      component: '/system/AiConfig',
      route: '1',
      children: []
    })
  }
}

// 远程打印配置页面由前端内置，注入到「系统设置 / 系统管理」菜单分组下，不依赖后台菜单表
function injectRemotePrintMenu(menuData) {
  if (!Array.isArray(menuData)) return
  const target = menuData.find(item => (item.url || '').replace(/\/$/, '') === '/system'
    || item.text === '系统设置' || item.text === '系统管理')
  if (!target) return
  if (!Array.isArray(target.children)) target.children = []
  const exists = target.children.some(item => item.url === '/system/remote-print')
  if (!exists) {
    target.children.push({
      id: 'remote-print-config',
      text: '远程打印配置',
      icon: 'printer',
      url: '/system/remote-print',
      component: '/print/RemotePrintConfig',
      route: '1',
      children: []
    })
  }
}

NProgress.configure({ showSpinner: false }) // NProgress Configuration

// 菜单本地缓存：登录态不变时先用缓存渲染，避免首屏等待菜单接口（后端为多级递归查询）
const MENU_CACHE_KEY = 'MENU_CACHE'
const MENU_CACHE_TTL = 7 * 24 * 60 * 60 * 1000

function readMenuCache(userId) {
  const cache = Vue.ls.get(MENU_CACHE_KEY)
  if (!cache || !Array.isArray(cache.data) || cache.data.length === 0) return null
  if (String(cache.userId) !== String(userId)) return null
  if (!cache.ts || Date.now() - cache.ts > MENU_CACHE_TTL) return null
  return cache.data
}

function saveMenuCache(userId, menuData) {
  if (!Array.isArray(menuData) || menuData.length === 0) return
  Vue.ls.set(MENU_CACHE_KEY, { userId: userId, ts: Date.now(), data: menuData }, MENU_CACHE_TTL)
}

// 把后台菜单转成路由表并注册
function applyMenuRoutes(menuData) {
  injectAddressRecognizeMenu(menuData);
  injectAiConfigMenu(menuData);
  injectRemotePrintMenu(menuData);
  const constRoutes = generateIndexRouter(menuData);
  injectMobilePageRoutes(constRoutes)
  injectPrintAgentRoute(constRoutes)
  return store.dispatch('UpdateAppRouter', { constRoutes }).then(() => {
    router.addRoutes(store.getters.addRouters)
  })
}

function refreshUserBtnList() {
  store.dispatch('GetUserBtnList').then(res => {
    Vue.ls.set('winBtnStrList', res.data.userBtn, 7 * 24 * 60 * 60 * 1000)
  }).catch(() => {})
}

const whiteList = ['/user/login', '/user/register', '/user/register-result', '/test/datatable', '/test/datatable-pro'] // no redirect whitelist

// 本地开发时手机端预览页不强制登录，业务页面在 iframe 内仍按正常登录态校验
if (process.env.NODE_ENV !== 'production') {
  whiteList.push('/mobile-preview', '/mobile-page-demo')
}

router.beforeEach((to, from, next) => {
  NProgress.start() // start progress bar
  if (Vue.ls.get(USER_ID)) {
    /* has token */
    if (to.path === '/' || to.path === '/user/login') {
      next({ path: INDEX_MAIN_PAGE_PATH })
      NProgress.done()
    } else {
      if (store.getters.permissionList.length === 0) {
        const userId = Vue.ls.get(USER_ID)
        const cachedMenu = readMenuCache(userId)
        if (cachedMenu) {
          // 命中本地缓存：立刻渲染，不再等待菜单接口
          applyMenuRoutes(JSON.parse(JSON.stringify(cachedMenu))).then(() => {
            const cachedRedirect = decodeURIComponent(from.query.redirect || to.fullPath)
            next(cachedRedirect)
          })
          // 后台静默刷新菜单与按钮权限，只更新缓存，不打断当前渲染
          store.dispatch('GetPermissionList').then(res => {
            if (res) {
              saveMenuCache(userId, res)
              refreshUserBtnList()
            }
          }).catch(() => {})
          return
        }
        store.dispatch('GetPermissionList').then(res => {
          const menuData = res;
          if (menuData === null || menuData === "" || menuData === undefined) {
            // 菜单为空时不能直接 return，否则 next() 永不执行，页面会一直白屏
            store.dispatch('Logout').then(() => {
              next({ path: '/user/login' })
            })
            return;
          }
          // 先落盘原始菜单，再注入前端内置页面（注入会修改数组）
          saveMenuCache(userId, menuData)
          // 缓存用户的按钮权限
          refreshUserBtnList()
          let constRoutes = [];
          injectAddressRecognizeMenu(menuData);
          injectAiConfigMenu(menuData);
          injectRemotePrintMenu(menuData);
          constRoutes = generateIndexRouter(menuData);
          injectMobilePageRoutes(constRoutes)
          injectPrintAgentRoute(constRoutes)
          // 添加主界面路由
          store.dispatch('UpdateAppRouter',  { constRoutes }).then(() => {
            // 根据roles权限生成可访问的路由表
            // 动态添加可访问路由表
            router.addRoutes(store.getters.addRouters)
            // 用 fullPath 保留查询参数，否则首次进入 /xxx?number=yyy 会丢失 query
            const redirect = decodeURIComponent(from.query.redirect || to.fullPath)
            next(redirect)
          })
        })
        .catch(() => {
          store.dispatch('Logout').then(() => {
            next({ path: '/user/login' })
          })
        })
      } else {
        if (to.path) {
          _hmt.push(['_trackPageview', '/#' + to.fullPath]);
        }
        next()
      }
    }
  } else {
    if (whiteList.indexOf(to.path) !== -1) {
      // 在免登录白名单，直接进入
      next()
    } else {
      next({ path: '/user/login' })
      NProgress.done() // if current page is login will not trigger afterEach hook, so manually handle it
    }
  }
})

router.afterEach(() => {
  NProgress.done() // finish progress bar
})
