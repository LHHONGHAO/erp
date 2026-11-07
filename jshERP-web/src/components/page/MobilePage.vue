<template>
  <div class="mobile-page" :class="pageClass">
    <!-- 标准手机页面顶栏：蓝底、左侧返回、标题居左 -->
    <div v-if="standalone" class="mobile-page-header">
      <div v-if="showBack" class="mobile-page-back" @click="$emit('back')">
        <a-icon type="arrow-left" />
      </div>
      <span class="mobile-page-title">{{ title }}</span>
      <div class="mobile-page-actions">
        <slot name="header-actions"></slot>
      </div>
    </div>
    <!-- 白色内容区：可滚动 -->
    <div class="mobile-page-body" :style="{ padding: bodyPadding }">
      <slot></slot>
    </div>
    <!-- 底部固定操作栏（可选） -->
    <div v-if="$slots.footer" class="mobile-page-footer">
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'MobilePage',
    props: {
      // 页面标题（居左显示）
      title: {
        type: String,
        default: ''
      },
      // 是否显示返回按钮
      showBack: {
        type: Boolean,
        default: true
      },
      // 是否自带顶栏：弹层页为 true；路由页（已有全局顶栏）传 false，仅提供内容区和底部栏
      standalone: {
        type: Boolean,
        default: true
      },
      // 内容区内边距
      bodyPadding: {
        type: String,
        default: '12px'
      },
      // 是否占满整屏：true 时底部操作栏紧贴屏幕底边且不随内容滚动
      fill: {
        type: Boolean,
        default: true
      }
    },
    computed: {
      pageClass() {
        return {
          'mobile-page-fill': this.fill,
          'mobile-page-no-header': !this.standalone
        }
      }
    }
  }
</script>

<style scoped>
  .mobile-page {
    display: flex;
    flex-direction: column;
    height: 100%;
    overflow: hidden;
    background: #fff;
  }

  /* 占满整屏：底部操作栏固定贴底，内容区独立滚动 */
  .mobile-page-fill {
    height: 100vh;
  }

  /* 路由页已由全局顶栏（49px）占据，页面高度需扣除 */
  .mobile-page-fill.mobile-page-no-header {
    height: calc(100vh - 49px);
  }

  .mobile-page-header {
    flex: 0 0 49px;
    height: 49px;
    display: flex;
    align-items: center;
    padding: 0 8px 0 0;
    background: #1890ff;
    color: #fff;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  }

  /* 返回按钮：与底边栏等高（58px）的正方形，左边贴屏幕左边 */
  .mobile-page-back {
    width: 58px;
    height: 58px;
    margin-left: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    color: #fff;
    cursor: pointer;
  }

  .mobile-page-back:active {
    background: rgba(255, 255, 255, 0.2);
  }

  .mobile-page-title {
    flex: 1 1 auto;
    min-width: 0;
    text-align: center;
    font-size: 16px;
    font-weight: 500;
    color: #fff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .mobile-page-actions {
    margin-left: auto;
    height: 49px;
    display: flex;
    align-items: center;
  }

  .mobile-page-body {
    flex: 1 1 auto;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    background: #fff;
    box-sizing: border-box;
  }

  .mobile-page-footer {
    flex: 0 0 auto;
    background: #fff;
    border-top: 1px solid #eee;
    padding: 8px 12px;
    box-sizing: border-box;
  }
</style>
