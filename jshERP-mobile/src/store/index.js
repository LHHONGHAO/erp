import Vue from 'vue'
import Vuex from 'vuex'
import ls from '../utils/storage'
import { login, logout } from '../api/auth'
import { ACCESS_TOKEN, USER_ID, USER_LOGIN_NAME, USER_INFO } from './mutation-types'

Vue.use(Vuex)

const EXPIRE = 7 * 24 * 60 * 60 * 1000

// 与电脑端登录页保持一致的失败提示
const LOGIN_TIPS = {
  'user is not exist': '用户不存在',
  'user password error': '用户密码不正确',
  'user is black': '用户被禁用',
  'tenant is black': '用户所属的租户被禁用',
  'tenant is expire': '试用期已结束，请联系客服续费',
  'access service error': '查询服务异常'
}

const store = new Vuex.Store({
  state: {
    token: ls.get(ACCESS_TOKEN) || '',
    user: ls.get(USER_INFO) || null
  },

  getters: {
    isLogin: state => !!state.token,
    loginName: state => (state.user && state.user.loginName) || '',
    userName: state => (state.user && (state.user.username || state.user.loginName)) || ''
  },

  mutations: {
    SET_AUTH (state, { token, user }) {
      state.token = token
      state.user = user
    },
    CLEAR_AUTH (state) {
      state.token = ''
      state.user = null
    }
  },

  actions: {
    Login ({ commit }, params) {
      return login(params).then(res => {
        if (!res || res.code !== 200) {
          throw new Error((res && res.data && res.data.message) || '登录失败')
        }
        const data = res.data || {}
        if (data.msgTip !== 'user can login') {
          throw new Error(LOGIN_TIPS[data.msgTip] || '登录失败')
        }
        const user = data.user || {}
        ls.set(ACCESS_TOKEN, data.token, EXPIRE)
        ls.set(USER_ID, user.id, EXPIRE)
        ls.set(USER_LOGIN_NAME, user.loginName, EXPIRE)
        ls.set(USER_INFO, user, EXPIRE)
        commit('SET_AUTH', { token: data.token, user })
        return data
      })
    },

    Logout ({ commit }) {
      commit('CLEAR_AUTH')
      ls.remove(ACCESS_TOKEN)
      ls.remove(USER_ID)
      ls.remove(USER_LOGIN_NAME)
      ls.remove(USER_INFO)
      return logout().catch(() => {})
    }
  }
})

export default store
