<template>
  <a-layout-sider
    :class="['sider', isDesktop() ? null : 'shadow', theme, fixSiderbar ? 'ant-fixed-sidemenu' : null ]"
    width="200px"
    :collapsible="collapsible"
    v-model="collapsed"
    :trigger="null">
    <logo />
    <div class="sider-body" :style="bodyStyle">
      <!-- 常用栏：编辑态下菜单项右侧出现 +/−，点击把页面标题加入/移出常用 -->
      <div v-if="!collapsed" class="fav-panel">
        <div class="fav-head">
          <span class="fav-title">常用</span>
          <a-icon class="fav-action" :type="editing ? 'check-circle' : 'plus-circle'"
                  :title="editing ? '完成' : '添加常用页面'" @click="toggleEdit" />
        </div>
        <div class="fav-list">
          <div v-for="item in favorites" :key="item.url" class="fav-item" :title="item.text"
               @click="openFavorite(item)">
            <span class="fav-item__text">{{ item.text }}</span>
            <a-icon v-if="editing" class="fav-item__del" type="minus-circle" @click.stop="removeFavorite(item)" />
          </div>
          <div v-if="!favorites.length" class="fav-empty">{{ editing ? '点击菜单项右侧 + 添加' : '暂无常用' }}</div>
        </div>
      </div>
      <s-menu
        :collapsed="collapsed"
        :menu="menus"
        :theme="theme"
        :editing="editing"
        :favorites="favoriteUrls"
        @select="onSelect"
        @toggle-favorite="onToggleFavorite"
        :mode="mode"
        :style="smenuStyle">
      </s-menu>
    </div>
  </a-layout-sider>

</template>

<script>
  import ALayoutSider from "ant-design-vue/es/layout/Sider"
  import Logo from '../tools/Logo'
  import SMenu from './index'
  import Vue from 'vue'
  import { USER_ID } from '@/store/mutation-types'
  import { mixin, mixinDevice } from '@/utils/mixin.js'

  // 常用页面本地缓存前缀，按用户隔离
  const FAVORITES_PREFIX = 'pc_sidebar_favorites_'

  export default {
    name: "SideMenu",
    components: { ALayoutSider, Logo, SMenu },
    mixins: [mixin, mixinDevice],
    props: {
      mode: {
        type: String,
        required: false,
        default: 'inline'
      },
      theme: {
        type: String,
        required: false,
        default: 'dark'
      },
      collapsible: {
        type: Boolean,
        required: false,
        default: false
      },
      collapsed: {
        type: Boolean,
        required: false,
        default: false
      },
      menus: {
        type: Array,
        required: true
      }
    },
    data () {
      return {
        // 常用页面编辑态：开启后菜单项右侧显示 +/−
        editing: false,
        // 常用页面 [{ url, text }]
        favorites: []
      }
    },
    computed:{
      smenuStyle() {
        return { 'padding': '0' }
      },
      // 固定侧边栏时，常用栏与菜单共用同一滚动区域
      bodyStyle() {
        if (this.fixSiderbar) {
          return { 'height': 'calc(100% - 59px)', 'overflow': 'auto', 'overflow-x': 'hidden' }
        }
        return null
      },
      favoriteUrls() {
        return this.favorites.map(item => item.url)
      },
      storageKey() {
        return FAVORITES_PREFIX + (Vue.ls.get(USER_ID) || 'anonymous')
      }
    },
    created() {
      const saved = Vue.ls.get(this.storageKey)
      const list = Array.isArray(saved) ? saved : []
      // 「远程打印配置」为移动端页面，PC 常用栏不再保留该入口
      this.favorites = list.filter(item => (item.url || '').replace(/\/$/, '') !== '/system/remote-print')
      if (this.favorites.length !== list.length) {
        this.saveFavorites()
      }
    },
    watch: {
      collapsed(val) {
        // 侧边栏收起时不保留编辑态
        if (val) {
          this.editing = false
        }
      }
    },
    methods: {
      onSelect (obj) {
        this.$emit('menuSelect', obj)
      },
      toggleEdit() {
        this.editing = !this.editing
      },
      // 菜单项点击 +/− 时触发
      onToggleFavorite(menu) {
        if (this.favoriteUrls.indexOf(menu.url) > -1) {
          this.favorites = this.favorites.filter(item => item.url !== menu.url)
        } else {
          this.favorites = this.favorites.concat([{ url: menu.url, text: menu.text }])
        }
        this.saveFavorites()
      },
      removeFavorite(item) {
        this.favorites = this.favorites.filter(fav => fav.url !== item.url)
        this.saveFavorites()
      },
      saveFavorites() {
        Vue.ls.set(this.storageKey, this.favorites)
      },
      openFavorite(item) {
        if (this.editing || !item || !item.url) {
          return
        }
        if (item.url.indexOf('http') === 0) {
          window.open(item.url)
          return
        }
        this.$router.push(item.url).catch(() => {})
      }
    }
  }
</script>
<style lang="less" scoped>

  /* update_begin author:sunjianlei date:20190509 for: 修改侧边导航栏滚动条的样式 */
  .sider {
    @scrollBarSize: 10px;

    ul.ant-menu {

      /* 定义滚动条高宽及背景 高宽分别对应横竖滚动条的尺寸*/
      &::-webkit-scrollbar {
        width: @scrollBarSize;
        height: @scrollBarSize;
        background-color: transparent;
        display: none;
      }

      & .-o-scrollbar {
        display: none;
      }

      /* 兼容IE */
      -ms-overflow-style: none;
      -ms-scroll-chaining: chained;
      -ms-content-zooming: zoom;
      -ms-scroll-rails: none;
      -ms-content-zoom-limit-min: 100%;
      -ms-content-zoom-limit-max: 500%;
      -ms-scroll-snap-type: proximity;
      -ms-scroll-snap-points-x: snapList(100%, 200%, 300%, 400%, 500%);

      /* 定义滚动条轨道 */
      &::-webkit-scrollbar-track {
        background-color: transparent;
      }

      /* 定义滑块 */
      &::-webkit-scrollbar-thumb {
        border-radius: @scrollBarSize;
        background-color: #eee;
        box-shadow: inset 0 0 6px rgba(0, 0, 0, 0.1);

        &:hover {
          background-color: #dddddd;
        }

        &:active {
          background-color: #bbbbbb;
        }
      }
    }

    /** 暗色系滚动条样式 */
    &.dark ul.ant-menu {
      &::-webkit-scrollbar-thumb {
        background-color: #666666;

        &:hover {
          background-color: #808080;
        }

        &:active {
          background-color: #999999;
        }
      }
    }

  }

  /* update_end author:sunjianlei date:20190509 for: 修改侧边导航栏滚动条的样式 */

  /* 常用栏 */
  .fav-panel {
    padding: 6px 8px 8px;
    border-bottom: 1px solid rgba(0, 0, 0, 0.06);

    .fav-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 4px;
    }

    .fav-title {
      font-size: 12px;
      color: rgba(0, 0, 0, 0.45);
    }

    .fav-action {
      cursor: pointer;
      font-size: 14px;
      color: rgba(0, 0, 0, 0.45);

      &:hover {
        color: @primary-color;
      }
    }

    .fav-list {
      display: flex;
      flex-wrap: wrap;
    }

    .fav-item {
      width: 50%;
      box-sizing: border-box;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 2px 2px;
      font-size: 12px;
      color: rgba(0, 0, 0, 0.65);
      cursor: pointer;

      &:hover {
        color: @primary-color;
      }
    }

    .fav-item__text {
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .fav-item__del {
      flex: none;
      margin-left: 2px;
      color: #f5222d;
    }

    .fav-empty {
      padding: 2px 0;
      font-size: 12px;
      color: rgba(0, 0, 0, 0.35);
    }
  }

  /* 暗色主题 */
  .sider.dark .fav-panel {
    border-bottom-color: rgba(255, 255, 255, 0.12);

    .fav-title,
    .fav-action {
      color: rgba(255, 255, 255, 0.45);
    }

    .fav-item {
      color: rgba(255, 255, 255, 0.65);

      &:hover {
        color: #fff;
      }
    }

    .fav-empty {
      color: rgba(255, 255, 255, 0.35);
    }
  }

</style>

<!-- update_begin author:sunjianlei date:20190530 for: 选中首页的时候不显示背景颜色 -->
<style lang="less">
  .ant-menu.ant-menu-root {
    & > .ant-menu-item:first-child {
      background-color: transparent;

      & > a, & > a:hover {
        color: rgba(0, 0, 0, 0.65);
      }

      &.ant-menu-item-selected {
        & > a, & > a:hover {
          color: @primary-color;
        }
      }
    }

    &.ant-menu-dark > .ant-menu-item:first-child {
      & > a, & > a:hover {
        color: rgba(255, 255, 255, 0.65);
      }

      &.ant-menu-item-selected {
        & > a, & > a:hover {
          color: rgba(255, 255, 255, 1);
        }
      }
    }
  }
</style>
<!-- update_end author:sunjianlei date:20190530 for: 选中首页的时候不显示背景颜色 -->