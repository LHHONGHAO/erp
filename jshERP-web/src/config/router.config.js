import { UserLayout, TabLayout, RouteView, BlankLayout, PageView } from '@/components/layouts'

/**
 * 走菜单，走权限控制
 * @type {[null,null]}
 */
export const asyncRouterMap = [

  {
    path: '/',
    name: 'dashboard',
    component: TabLayout,
    meta: { title: '首页' },
    redirect: '/dashboard/analysis',
    children: [

    ]
  },
  {
    path: '*', redirect: '/404', hidden: true
  }
]

/**
 * 基础路由
 * @type { *[] }
 */
export const constantRouterMap = [
  {
    path: '/user',
    component: UserLayout,
    redirect: '/user/login',
    hidden: true,
    children: [
      {
        path: 'login',
        name: 'login',
        component: () => import(/* webpackChunkName: "user" */ '@/views/user/Login')
      },
      {
        path: 'register',
        name: 'register',
        component: () => import(/* webpackChunkName: "user" */ '@/views/user/Register')
      }
    ]
  },
  {
    path: '/dashboard',
    component: TabLayout,
    redirect: '/dashboard/analysis',
    children: [
      {
        path: 'analysis',
        name: 'analysis',
        meta: { title: '首页' },
        component: () => import(/* webpackChunkName: "dashboard" */ '@/views/dashboard/Analysis')
      }
    ]
  },
  {
    path: '/404',
    component: () => import(/* webpackChunkName: "fail" */ '@/views/exception/404')
  },
  // 手机端预览与标准页面模版仅用于本地开发调试，不进入生产包
  ...(process.env.NODE_ENV !== 'production' ? [
    {
      path: '/mobile-preview',
      component: BlankLayout,
      children: [
        {
          path: '',
          name: 'MobilePreview',
          meta: { title: '手机端预览' },
          component: () => import(/* webpackChunkName: "mobile-preview" */ '@/views/system/MobilePreview')
        }
      ]
    },
    {
      path: '/mobile-page-demo',
      component: BlankLayout,
      children: [
        {
          path: '',
          name: 'MobilePageDemo',
          meta: { title: '标准手机页面模版' },
          component: () => import(/* webpackChunkName: "mobile-preview" */ '@/views/system/MobilePageDemo')
        }
      ]
    }
  ] : []),
  {
    path: '/bill/deliveryNote/template',
    component: BlankLayout,
    children: [
      {
        path: '',
        name: 'DeliveryNoteTemplate',
        meta: { title: '发货模板' },
        component: () => import(/* webpackChunkName: "bill-delivery-note" */ '@/views/bill/DeliveryNoteTemplate')
      }
    ]
  },
  {
    path: '/label/edit-mobile',
    name: 'LabelEditMobile',
    component: () => import(/* webpackChunkName: "label" */ '@/views/material/modules/LabelEditMobile'),
    meta: { title: '标签编辑' }
  },
  {
    path: '/material/edit',
    component: TabLayout,
    children: [
      {
        path: '',
        name: 'MaterialEdit',
        component: () => import(/* webpackChunkName: "material" */ '@/views/material/MaterialAdd'),
        meta: { title: '编辑商品' }
      }
    ]
  },

]
