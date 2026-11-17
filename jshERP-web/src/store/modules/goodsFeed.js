import Vue from 'vue'

/**
 * 商品流（双列瀑布流）状态模块。
 *
 * 只保存「页面级」状态：滚动位置、已加载页、双列累计高度、分页游标。
 * 商品数据本身由 VirtualWaterfall 组件持有并 Object.freeze，避免进入 Vuex 后
 * 被 Vue 2 深度劫持造成大数组内存开销；同时 keep-alive 已能保住组件内数据。
 *
 * 按 feedKey 区分不同列表（如商品信息、后续的闲鱼式商品浏览页），互不影响。
 */
const state = {
  feeds: {}
}

const mutations = {
  /** 保存某个列表的滚动位置/页码/双列高度 */
  SAVE_FEED_STATE(state, payload) {
    if (!payload || !payload.key) return
    const prev = state.feeds[payload.key] || {}
    Vue.set(state.feeds, payload.key, {
      scrollTop: payload.scrollTop == null ? (prev.scrollTop || 0) : payload.scrollTop,
      page: payload.page == null ? (prev.page || 0) : payload.page,
      colHeights: payload.colHeights || prev.colHeights || [0, 0],
      cursor: payload.cursor || prev.cursor || '',
      time: Date.now()
    })
  },
  /** 清空某个列表的状态（如搜索条件变化） */
  CLEAR_FEED_STATE(state, key) {
    if (key) Vue.delete(state.feeds, key)
  }
}

const getters = {
  feedState: state => key => state.feeds[key] || null
}

export default {
  namespaced: true,
  state,
  mutations,
  getters
}
