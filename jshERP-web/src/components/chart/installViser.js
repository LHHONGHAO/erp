/**
 * viser-vue（@antv/G2 图表库）按需注册入口
 *
 * 原先在 main.js 全局 Vue.use(Viser)，登录页/移动端也要下载并解析整套图表库。
 * 现在只由真正用到 <v-chart> 的图表组件引入本模块，打包后图表库只进入
 * 桌面端首页等按需 chunk，移动端与登录页不再加载。
 */
import Vue from 'vue'
import Viser from 'viser-vue'

Vue.use(Viser)
