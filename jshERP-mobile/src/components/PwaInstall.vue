<template>
  <div v-if="visible" class="pwa-install" :class="'pwa-install--' + variant">
    <template v-if="variant === 'item'">
      <div class="pwa-install__row" @click="handleItemClick">
        <span>{{ ios ? '添加到主屏幕' : '添加到桌面' }}</span>
        <span class="pwa-install__hint">{{ itemHint }}</span>
      </div>
      <div v-if="showGuide" class="pwa-install__guide">{{ guideText }}</div>
    </template>

    <template v-else>
      <div class="pwa-install__main">
        <div class="pwa-install__title">{{ ios ? '添加到主屏幕' : '添加到手机桌面' }}</div>
        <div class="pwa-install__desc">{{ cardDesc }}</div>
      </div>
      <button v-if="installable" class="pwa-install__btn" type="button" @click="install">立即安装</button>
      <span class="pwa-install__close" @click="close">&times;</span>
    </template>
  </div>
</template>

<script>
import {
  canInstall,
  isIOS,
  isStandalone,
  promptInstall,
  isInstallTipDismissed,
  dismissInstallTip,
  subscribeInstall
} from '../utils/pwa'

const IOS_GUIDE = '在 Safari 底部点击「分享」图标，然后选择「添加到主屏幕」。'
const OTHER_GUIDE = '请通过浏览器菜单选择「安装应用」或「添加到主屏幕」。'

export default {
  name: 'PwaInstall',
  props: {
    // card：首页提示卡片；item：个人中心列表项
    variant: { type: String, default: 'card' }
  },
  data () {
    return {
      installable: canInstall(),
      ios: isIOS(),
      standalone: isStandalone(),
      dismissed: isInstallTipDismissed(),
      showGuide: false,
      guideText: ''
    }
  },
  computed: {
    visible () {
      if (this.standalone) {
        return false
      }
      if (this.variant === 'card' && this.dismissed) {
        return false
      }
      return this.installable || this.ios
    },
    itemHint () {
      if (this.ios) return '查看方法'
      if (this.installable) return '点击安装'
      return '需用手机浏览器'
    },
    cardDesc () {
      return this.ios ? IOS_GUIDE : '像 App 一样从桌面直接打开，使用更方便'
    }
  },
  created () {
    this.unsubscribe = subscribeInstall(this.refresh)
  },
  beforeDestroy () {
    if (this.unsubscribe) {
      this.unsubscribe()
    }
  },
  methods: {
    refresh () {
      this.installable = canInstall()
      this.dismissed = isInstallTipDismissed()
    },
    install () {
      promptInstall().then(accepted => {
        this.refresh()
        if (!accepted) {
          this.showGuide = true
          this.guideText = OTHER_GUIDE
        }
      })
    },
    handleItemClick () {
      if (this.installable) {
        this.install()
        return
      }
      this.guideText = this.ios ? IOS_GUIDE : OTHER_GUIDE
      this.showGuide = !this.showGuide
    },
    close () {
      dismissInstallTip()
      this.refresh()
    }
  }
}
</script>

<style scoped>
  .pwa-install--card {
    position: relative;
    display: flex;
    align-items: center;
    gap: 12px;
    margin: 12px 12px 0;
    padding: 14px 16px;
    border-radius: 12px;
    background: linear-gradient(135deg, #eaf4ff, #f6fbff);
    border: 1px solid #d8ecff;
  }

  .pwa-install__main {
    flex: 1 1 auto;
    min-width: 0;
    padding-right: 16px;
  }

  .pwa-install__title {
    color: #1f2329;
    font-size: 14px;
    font-weight: 600;
  }

  .pwa-install__desc {
    margin-top: 4px;
    color: #64748b;
    font-size: 12px;
    line-height: 1.5;
  }

  .pwa-install__btn {
    flex: 0 0 auto;
    padding: 7px 16px;
    border: 0;
    border-radius: 16px;
    background: #1890ff;
    color: #fff;
    font-size: 13px;
  }

  .pwa-install__close {
    position: absolute;
    top: 6px;
    right: 10px;
    color: #9aa3af;
    font-size: 16px;
    line-height: 1;
  }

  .pwa-install__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 15px 16px;
    border-top: 1px solid #f0f1f3;
    color: #1f2329;
    font-size: 14px;
  }

  .pwa-install__hint {
    color: #8b949e;
    font-size: 13px;
  }

  .pwa-install__guide {
    padding: 0 16px 14px;
    color: #64748b;
    font-size: 12px;
    line-height: 1.6;
  }
</style>
