import request from '../utils/request'

// 登录（密码由调用方 md5 后传入，与电脑端一致）
export function login (data) {
  return request.post('/user/login', data)
}

export function logout () {
  return request.get('/user/logout')
}

// 是否开启登录验证码，返回纯文本 "1"/"0"
export function getCheckcodeFlag () {
  return request.get('/platformConfig/getPlatform/checkcodeFlag')
}

// 图形验证码（uuid + base64）
export function getRandomImage () {
  return request.get('/user/randomImage')
}

// 当前用户可见菜单（与电脑端同一接口）
export function getMenu (data) {
  return request.post('/function/findMenuByPNumber', data)
}
