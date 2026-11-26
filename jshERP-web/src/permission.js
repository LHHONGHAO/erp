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

// 打印相关页面由前端内置，统一收敛到「打印管理」菜单分组下，不依赖后台菜单表。
// 页面路由已在 router.config.js / injectPrintAgentRoute 中固定注册，
// 这里只负责菜单展示，菜单结构变化不会导致页面打不开。
const PRINT_MENU_PAGES = [
  { id: 'print-agent', text: '管理打印', icon: 'printer', url: '/print/agent', component: '/print/agent/PrintAgentList' },
  { id: 'address-recognize', text: '地址打印', icon: 'environment', url: '/system/address-recognize', component: '/system/AddressRecognize' },
  { id: 'label-print', text: '标签打印', icon: 'tags', url: '/print/label', component: '/print/LabelPrint' }
]

function findMenuByUrl(menuData, url) {
  for (let item of menuData) {
    if ((item.url || '').replace(/\/$/, '') === url) return item
    if (Array.isArray(item.children)) {
      const found = findMenuByUrl(item.children, url)
      if (found) return found
    }
  }
  return null
}

function removeMenuByUrl(menuData, url) {
  for (let i = menuData.length - 1; i >= 0; i--) {
    const item = menuData[i]
    if ((item.url || '').replace(/\/$/, '') === url) {
      menuData.splice(i, 1)
      continue
    }
    if (Array.isArray(item.children)) removeMenuByUrl(item.children, url)
  }
}

function injectPrintManageMenu(menuData) {
  if (!Array.isArray(menuData)) return
  // 已有「打印管理」分组时复用，避免重复创建
  let group = menuData.find(item => item.text === '打印管理' && Array.isArray(item.children))
  if (!group) {
    group = {
      id: 'print-manage',
      text: '打印管理',
      icon: 'printer',
      url: '/print',
      component: '/layouts/RouteView',
      route: '1',
      children: []
    }
    menuData.push(group)
  }
  if (!Array.isArray(group.children)) group.children = []
  PRINT_MENU_PAGES.forEach(page => {
    // 先取出旧配置（后台或旧缓存注入的），再从其他分组里摘除，避免同一页面重复出现
    const existed = findMenuByUrl(menuData, page.url)
    removeMenuByUrl(menuData, page.url)
    // fixedRoute：路由已固定注册；children 置空，菜单渲染为普通子项而不是可展开的下拉项
    group.children.push(Object.assign({}, existed || {}, page, { fixedRoute: true, children: null }))
  })
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
  injectPrintManageMenu(menuData);
  injectAiConfigMenu(menuData);
  const constRoutes = generateIndexRouter(menuData);
  injectMobilePageRoutes(constRoutes)
  injectPrintAgentRoute(constRoutes)
  return store.dispatch('UpdateAppRouter', { constRoutes }).then(() => {
    // 内置页面注入后同步回菜单 store，手机端首页、顶部菜单等直接读取 permissionList
    store.commit('SET_PERMISSIONLIST', menuData)
    router.addRoutes(store.getters.addRouters)
  })
}

function refreshUserBtnList() {
  store.dispatch('GetUserBtnList').then(res => {
    Vue.ls.set('winBtnStrList', res.data.userBtn, 7 * 24 * 60 * 60 * 1000)
  }).catch(() => {})
}

const whiteList = ['/user/login', '/user/register', '/user/register-result'] // no redirect whitelist

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
          // 命中本地缓存：先同步回填权限菜单，否则守卫会因 permissionList 仍为空而反复进入本分支，导致菜单接口死循环
          // 回填与路由使用同一份数据，内置的 AI 配置菜单才能显示在侧边栏
          const cachedMenuData = JSON.parse(JSON.stringify(cachedMenu))
          store.commit('SET_PERMISSIONLIST', cachedMenuData)
          // 立刻渲染，不再等待菜单接口
          applyMenuRoutes(cachedMenuData).then(() => {
            const cachedRedirect = decodeURIComponent(from.query.redirect || to.fullPath)
            next(cachedRedirect)
          })
          // 后台静默刷新菜单与按钮权限，只更新缓存，不打断当前渲染
          store.dispatch('GetPermissionList').then(res => {
            if (res) {
              // 后台返回的是原始菜单，先注入内置页面再写缓存，避免覆盖掉注入结果
              injectPrintManageMenu(res)
              injectAiConfigMenu(res)
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
          injectPrintManageMenu(menuData);
          injectAiConfigMenu(menuData);
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
