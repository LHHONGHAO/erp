import Vue from 'vue'

/**
 * 手机端页面顶栏（蓝底、左侧返回、标题）的动作按钮通道。
 * 标准页面模版为全局顶栏 GlobalHeader：左侧返回 + 标题，右侧默认搜索/主页/消息/退出登录。
 * 各页面（除主页外）不再渲染自己的顶部按钮区，改为在挂载时通过 setMobileHeaderActions
 * 注册本页需要的顶部按钮（如设置图标、保存等），卸载时清空。
 *
 * 动作项格式：{ key: 'save', icon: 'setting', text: '保存', type: 'primary', handler: fn }
 * - icon 与 text 可单独或同时使用
 * - type 为 'primary' 时渲染为高亮主按钮
 */
export const mobilePageHeader = Vue.observable({
  actions: []
})

export function setMobileHeaderActions (actions) {
  mobilePageHeader.actions = Array.isArray(actions) ? actions : []
}

export function clearMobileHeaderActions () {
  mobilePageHeader.actions = []
}
