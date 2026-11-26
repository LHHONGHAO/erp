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
      },
      {
        // 地址打印页固定路由：与首页共用同一个 TabLayout，返回首页时不会销毁重建。
        // 路由在打包时即存在，不依赖后台菜单注入；菜单仅负责展示入口。
        // 绝对路径子路由会保留最后的完整路径 /system/address-recognize。
        path: '/system/address-recognize',
        name: 'AddressRecognize',
        meta: { title: '地址打印', componentName: 'AddressRecognize', keepAlive: true },
        component: () => import(/* webpackChunkName: "address-recognize" */ '@/views/system/AddressRecognize')
      },
      {
        // 标签打印页固定路由：搜索/扫码商品后直接打开标签打印弹层
        path: '/print/label',
        name: 'LabelPrint',
        meta: { title: '标签打印', componentName: 'LabelPrint', keepAlive: true },
        component: () => import(/* webpackChunkName: "label-print" */ '@/views/print/LabelPrint')
      }
    ]
  },
  {
    path: '/404',
    component: () => import(/* webpackChunkName: "fail" */ '@/views/exception/404')
  },
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
  {
    path: '/material/material_add',
    component: TabLayout,
    children: [
      {
        path: '',
        name: 'MaterialManualAdd',
        component: () => import(/* webpackChunkName: "material" */ '@/views/material/MaterialAdd'),
        meta: { title: '人工添加商品' }
      }
    ]
  },

]
