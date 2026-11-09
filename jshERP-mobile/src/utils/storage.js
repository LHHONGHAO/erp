import Vue from 'vue'
import Storage from 'vue-ls'

// 与电脑端共用同一份本地存储（命名空间 pro__），手机端登录后电脑端可直接复用登录态。
// 该模块在被 import 时立即安装插件，保证 store 初始化时 Vue.ls 已可用。
Vue.use(Storage, {
  namespace: 'pro__',
  name: 'ls',
  storage: 'local'
})

export default Vue.ls
