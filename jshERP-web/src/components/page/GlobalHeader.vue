<template>
  <!-- , width: fixedHeader ? `calc(100% - ${sidebarOpened ? 256 : 80}px)` : '100%'  -->
  <a-layout-header
    v-if="!headerBarFixed"
    :class="[fixedHeader && 'ant-header-fixedHeader', sidebarOpened ? 'ant-header-side-opened' : 'ant-header-side-closed', ]"
    :style="{ padding: '0' }">

    <div v-if="mode === 'sidemenu'" class="header" :class="theme">
      <a-icon
        v-if="device==='mobile' && showBack"
        class="trigger back-trigger"
        type="arrow-left"
        @click="back"></a-icon>

      <span v-if="device === 'mobile'" class="mobile-title">{{ mobileTitle }}</span>
      <span v-if="device === 'desktop'" class="company-name">{{ companyName }}</span>
      <span v-else-if="device !== 'mobile'">{{ systemTitle }}</span>
      <!-- 手机端页面级顶部按钮（由各页面注册，如设置图标、保存等），主页不注册即为默认按钮 -->
      <!-- 复用标准页顶栏按钮组，保证与标准搜索页的按钮尺寸/图标/顺序完全一致 -->
      <mobile-header-actions
        v-if="device === 'mobile' && mobileHeaderActions.length > 0"
        :actions="mobileBarActions" />
      <jump-info ref="jumpModal"></jump-info>
      <!-- 手机端标准功能页不渲染 UserMenu（搜索/主页/提醒/退出登录），仅主页保留；桌面端不受影响 -->
      <user-menu v-if="device !== 'mobile' || isHomePage" :theme="theme" @searchGlobalHeader="searchGlobalHeader" />
    </div>
    <!-- 顶部导航栏模式 -->
    <div v-else :class="['top-nav-header-index', theme]">
      <div class="header-index-wide">
        <div class="header-index-left" :style="topMenuStyle.headerIndexLeft">
          <logo class="top-nav-header" :show-title="device !== 'mobile'" :style="topMenuStyle.topNavHeader"/>
          <div v-if="device !== 'mobile'" :style="topMenuStyle.topSmenuStyle">
            <s-menu
              mode="horizontal"
              :menu="menus"
              :theme="theme"></s-menu>
          </div>
          <a-icon
            v-else-if="showBack"
            class="trigger back-trigger"
            type="arrow-left"
            @click="back"></a-icon>
        </div>
        <user-menu v-if="device !== 'mobile' || isHomePage" class="header-index-right" :theme="theme" :style="topMenuStyle.headerIndexRight"/>
      </div>
    </div>

  </a-layout-header>
</template>

<script>
  import JumpInfo from './JumpInfo'
  import UserMenu from '../tools/UserMenu'
  import SMenu from '../menu/'
  import Logo from '../tools/Logo'
  import MobileHeaderActions from '../mobile/MobileHeaderActions'
  import { getCurrentSystemConfig } from '@/api/api'
  import { mixin } from '@/utils/mixin.js'
  import { mobilePageHeader } from '@/utils/mobilePageHeader'

  export default {
    name: 'GlobalHeader',
    components: {
      JumpInfo,
      UserMenu,
      SMenu,
      Logo,
      MobileHeaderActions
    },
    mixins: [mixin],
    props: {
      mode: {
        type: String,
        // sidemenu, topmenu
        default: 'sidemenu'
      },
      menus: {
        type: Array,
        required: true
      },
      theme: {
        type: String,
        required: false,
        default: 'dark'
      },
      collapsed: {
        type: Boolean,
        required: false,
        default: false
      },
      device: {
        type: String,
        required: false,
        default: 'desktop'
      }
    },
    data() {
      return {
        headerBarFixed: false,
        systemTitle: window.SYS_TITLE,
        companyName: '',
        topMenuStyle: {
          headerIndexLeft: {},
          topNavHeader: {},
          headerIndexRight: {},
          topSmenuStyle: {}
        }
      }
    },
    computed: {
      // 手机端首页不显示退回按钮
      showBack() {
        return this.$route.path !== '/dashboard/analysis'
      },
      // 是否为主页（手机端主页保留 UserMenu 顶部按钮）
      isHomePage() {
        return this.$route.path === '/dashboard/analysis'
      },
      mobileTitle() {
        // 页面可通过 setMobileHeaderTitle 覆盖标题（如新增/编辑共用同一页面）
        if (mobilePageHeader.title) {
          return mobilePageHeader.title
        }
        return (this.$route.meta && this.$route.meta.title) || this.systemTitle
      },
      // 当前页面注册的手机端顶部按钮
      mobileHeaderActions() {
        return mobilePageHeader.actions
      },
      // 转换为标准顶栏按钮组格式：{ key, icon, title, text, primary, visible, click }
      mobileBarActions() {
        return (this.mobileHeaderActions || []).map(function (act) {
          return {
            key: act.key,
            icon: act.icon,
            text: act.text,
            title: act.title || act.text || '',
            primary: act.type === 'primary',
            visible: act.visible !== false,
            click: act.handler
          }
        })
      }
    },
    watch: {
      /** 监听设备变化 */
      device() {
        if (this.mode === 'topmenu') {
          this.buildTopMenuStyle()
        }
      },
      /** 监听导航栏模式变化 */
      mode(newVal) {
        if (newVal === 'topmenu') {
          this.buildTopMenuStyle()
        }
      }
    },
    mounted() {
      window.addEventListener('scroll', this.handleScroll)
      if (this.mode === 'topmenu') {
        this.buildTopMenuStyle()
      }
      if(window.location.host === 'cloud.huaxiaerp.vip' || window.location.host === 'cloud.huaxiaerp.com') {
        this.showJump()
      }
    },
    created () {
      this.initSystemConfig()
    },
    methods: {
      showJump() {
        this.$refs.jumpModal.handleShow()
      },
      handleScroll() {
        if (this.autoHideHeader) {
          let scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop
          if (scrollTop > 100) {
            this.headerBarFixed = true
          } else {
            this.headerBarFixed = false
          }
        } else {
          this.headerBarFixed = false
        }
      },
      toggle() {
        this.$emit('toggle')
      },
      back() {
        this.$emit('back')
      },
      buildTopMenuStyle() {
        if (this.mode === 'topmenu') {
          if (this.device === 'mobile') {
            // 手机端需要清空样式，否则显示会错乱
            this.topMenuStyle.topNavHeader = {}
            this.topMenuStyle.topSmenuStyle = {}
            this.topMenuStyle.headerIndexRight = {}
            this.topMenuStyle.headerIndexLeft = {}
          } else {
            let rightWidth = '360px'
            this.topMenuStyle.topNavHeader = { 'min-width': '165px' }
            this.topMenuStyle.topSmenuStyle = { 'width': 'calc(100% - 165px)' }
            this.topMenuStyle.headerIndexRight = { 'min-width': rightWidth }
            this.topMenuStyle.headerIndexLeft = { 'width': `calc(100% - ${rightWidth})` }
          }
        }
      },
      searchGlobalHeader(key, id, title, component){
        this.$emit("searchGlobalLayout", key, id, title, component)
      },
      initSystemConfig() {
        getCurrentSystemConfig().then((res) => {
          if(res.code === 200 && res.data){
            this.companyName = res.data.companyName
          }
        })
      },
    }
  }
</script>

<style lang="less" scoped>

  @height: 49px;

  .layout {

    .top-nav-header-index {

      .header-index-wide {
        margin-left: 10px;

        .ant-menu.ant-menu-horizontal {
          height: @height;
          line-height: @height;
        }
      }
      .trigger {
        line-height: 64px;
        &:hover {
          background: rgba(0, 0, 0, 0.05);
        }
      }
    }

    .header {
      z-index: 2;
      color: white;
      height: @height;
      background-color: @primary-color;
      transition: background 300ms;

      /* dark 样式 */
      &.dark {
        color: #000;
        box-shadow: 0 0 4px rgba(0, 0, 0, 0.2);
        background-color: white !important;
      }
    }

    .header, .top-nav-header-index {
      &.dark .trigger:hover {
        background: rgba(0, 0, 0, 0.05);
      }
    }
  }

  .ant-layout-header {
    height: @height;
    line-height: @height;
  }

  /* 手机端统一页面顶栏：蓝底、固定、左侧返回、标题 16px */
  .layout.mobile {
    .ant-layout-header {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      width: 100%;
      height: @height;
      line-height: @height;
      z-index: 10;
      padding: 0;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
    }

    .header,
    .header.dark {
      height: @height;
      line-height: @height;
      padding: 0 8px 0 0;
      display: flex;
      align-items: center;
      background-color: @primary-color !important;
      color: #fff !important;
      box-shadow: none;
    }

    /* 返回按钮：与底边栏等高（58px）的正方形，左边贴屏幕左边 */
    .trigger.back-trigger {
      flex: 0 0 auto;
      width: 58px;
      height: 58px;
      line-height: 1;
      margin-left: 0;
      padding: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      color: #fff;
      vertical-align: middle;
    }

    /* 标题：与标准顶栏（MobileTopBar）完全一致，左对齐、16px/500、返回区后留 4px */
    .mobile-title {
      flex: 1 1 auto;
      min-width: 0;
      padding-left: 4px;
      text-align: left;
      font-size: 16px;
      font-weight: 500;
      color: #fff;
      line-height: 1.2;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }

  .company-name {
    font-size:16px;
    padding-left:16px
  }

  .mobile-title {
    display: inline-block;
    max-width: none;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: middle;
    font-size: 16px;
    font-weight: 500;
    padding-left: 4px;
  }

  .change-title {
    font-size:14px;
    padding-left:16px;
    color:yellow;
  }

  .change-title a {
    font-size:14px;
    color:yellow;
    text-decoration:underline;
  }

</style>