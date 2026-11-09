import ls from './storage'
import axios from 'axios'
import { ACCESS_TOKEN } from '../store/mutation-types'

// 与电脑端同源部署，接口统一走根路径
const service = axios.create({
  baseURL: '',
  timeout: 60000
})

service.interceptors.request.use(config => {
  const token = ls.get(ACCESS_TOKEN)
  if (token) {
    config.headers['X-Access-Token'] = token
  }
  return config
}, error => Promise.reject(error))

service.interceptors.response.use(response => response.data, error => {
  const response = error.response || {}
  // 后端登录态失效时统一回到手机端登录页
  if (response.status === 500 && response.data === 'loginOut') {
    ls.remove(ACCESS_TOKEN)
    if (window.location.pathname.indexOf('/mobile/login') === -1) {
      window.location.replace(process.env.BASE_URL + 'login')
    }
  }
  return Promise.reject(error)
})

export default service
