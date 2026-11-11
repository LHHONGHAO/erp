import Vue from 'vue'
import Router from 'vue-router'
import ls from '../utils/storage'
import TabLayout from '../layouts/TabLayout.vue'
import { ACCESS_TOKEN } from '../store/mutation-types'

Vue.use(Router)

// hash 模式：静态文件可直接部署在 /mobile/ 下，无需后端为 /mobile/* 提供 history 回退
const router = new Router({
  mode: 'hash',
  scrollBehavior: () => ({ y: 0 }),
  routes: [
    {
      path: '/login',
      name: 'MobileLogin',
      component: () => import(/* webpackChunkName: "login" */ '../views/Login.vue')
    },
    {
      path: '/',
      component: TabLayout,
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'MobileHome',
          meta: { title: '首页' },
          component: () => import(/* webpackChunkName: "home" */ '../views/Home.vue')
        },
        {
          path: 'mine',
          name: 'MobileMine',
          meta: { title: '我的' },
          component: () => import(/* webpackChunkName: "mine" */ '../views/Mine.vue')
        },
        {
          path: 'web',
          name: 'MobileWebView',
          meta: { title: '业务页面', hideTabBar: true },
          component: () => import(/* webpackChunkName: "webview" */ '../views/WebView.vue')
        }
      ]
    },
    {
      path: '*',
      redirect: '/home'
    }
  ]
})

router.beforeEach((to, from, next) => {
  const token = ls.get(ACCESS_TOKEN)
  if (!token && to.path !== '/login') {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  if (token && to.path === '/login') {
    next({ path: '/home' })
    return
  }
  next()
})

export default router
