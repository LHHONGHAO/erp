import Vue from 'vue'
import './utils/storage'
import App from './App.vue'
import router from './router'
import store from './store'
import { initPwa } from './utils/pwa'

Vue.config.productionTip = false

initPwa()

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app')
