<template>
  <div class="home">
    <NavBar title="工作台" show-avatar :avatar-text="avatarText" @avatar="goMine" />

    <div class="home-hero">
      <div class="home-hero__main">
        <div class="home-hero__greeting">{{ greeting }}，{{ userName || '你好' }}</div>
        <div class="home-hero__date">{{ today }}</div>
      </div>
      <a class="home-hero__pc" href="/">电脑版</a>
    </div>

    <PwaInstall />

    <div v-if="loading" class="home-loading">功能加载中...</div>

    <div v-else-if="errorText" class="home-error">
      <p>{{ errorText }}</p>
      <button type="button" @click="loadMenu">重新加载</button>
    </div>

    <template v-else>
      <div v-if="groups.length" class="home-section-title">全部功能</div>

      <div v-for="group in groups" :key="group.id" class="home-card">
        <div class="home-card__head">
          <span class="home-card__title">{{ group.text }}</span>
        </div>
        <div class="home-grid">
          <div
            v-for="item in group.items"
            :key="item.url"
            class="home-entry"
            @click="open(item)">
            <span class="home-entry__icon">{{ initialLetter(item.text) }}</span>
            <span class="home-entry__text">{{ item.text }}</span>
          </div>
        </div>
      </div>

      <div v-if="groups.length === 0" class="home-empty">当前账号暂无可访问的功能菜单</div>
      <div class="home-tip">业务页面当前沿用电脑版的移动适配页面，后续将逐步迁移到手机端。</div>
    </template>
  </div>
</template>

<script>
import ls from '../utils/storage'
import { getMenu } from '../api/auth'
import { USER_ID } from '../store/mutation-types'
import NavBar from '../components/NavBar.vue'
import PwaInstall from '../components/PwaInstall.vue'

export default {
  name: 'MobileHome',
  components: { NavBar, PwaInstall },
  data () {
    return {
      groups: [],
      loading: true,
      errorText: ''
    }
  },
  computed: {
    userName () {
      return this.$store.getters.userName
    },
    avatarText () {
      return (this.userName || '我').charAt(0).toUpperCase()
    },
    greeting () {
      const hour = new Date().getHours()
      if (hour < 6) return '凌晨好'
      if (hour < 12) return '早上好'
      if (hour < 14) return '中午好'
      if (hour < 18) return '下午好'
      return '晚上好'
    },
    today () {
      const now = new Date()
      const week = ['日', '一', '二', '三', '四', '五', '六'][now.getDay()]
      return (now.getMonth() + 1) + '月' + now.getDate() + '日 星期' + week
    }
  },
  created () {
    this.restoreCache()
    // 有缓存时先渲染，再后台静默刷新
    this.loadMenu(this.groups.length > 0)
  },
  methods: {
    menuCacheKey () {
      return 'mobile_menu_cache_' + (ls.get(USER_ID) || 'guest')
    },
    restoreCache () {
      try {
        const raw = sessionStorage.getItem(this.menuCacheKey())
        const groups = raw ? JSON.parse(raw) : null
        if (Array.isArray(groups) && groups.length) {
          this.groups = groups
        }
      } catch (e) {}
    },
    saveCache () {
      try {
        sessionStorage.setItem(this.menuCacheKey(), JSON.stringify(this.groups))
      } catch (e) {}
    },
    loadMenu (silent) {
      if (!silent) {
        this.loading = true
        this.errorText = ''
      }
      getMenu({ pNumber: 0, userId: ls.get(USER_ID) }).then(res => {
        this.groups = this.buildGroups(res)
        this.saveCache()
      }).catch(() => {
        if (!this.groups.length) {
          this.errorText = '功能菜单加载失败，请检查网络后重试'
        }
      }).finally(() => {
        this.loading = false
      })
    },
    buildGroups (nodes) {
      const groups = []
      const flatten = (list, out) => {
        (list || []).forEach(node => {
          if (node.children && node.children.length) {
            flatten(node.children, out)
          } else if (node.url && node.url !== '#' && node.route !== '0') {
            out.push({ text: node.text, url: node.url })
          }
        })
        return out
      }
      (nodes || []).forEach(node => {
        const items = flatten(node.children, [])
        if (!items.length && node.url && node.url !== '#' && node.route !== '0') {
          items.push({ text: node.text, url: node.url })
        }
        // 地址识别由前端内置在「基础资料」下（与电脑端 permission.js 保持一致，不依赖后台菜单表）
        const isBaseData = node.text === '基础资料' || String(node.url || '').replace(/\/$/, '') === '/systemA'
        if (isBaseData && !items.some(item => item.url === '/system/address-recognize')) {
          items.push({ text: '地址识别', url: '/system/address-recognize' })
        }
        if (items.length) {
          groups.push({ id: node.id, text: node.text, items })
        }
      })
      return groups
    },
    initialLetter (text) {
      return (text || '').charAt(0)
    },
    goMine () {
      this.$router.push('/mine')
    },
    open (item) {
      if (!item || !item.url) {
        return
      }
      if (/^https?:\/\//.test(item.url)) {
        window.open(item.url, '_blank')
        return
      }
      // 在移动端 SPA 内打开电脑版页面，返回时首页不销毁、不重新加载
      this.$router.push({ path: '/web', query: { url: item.url, title: item.text } })
    }
  }
}
</script>

<style scoped>
  .home {
    min-height: 100%;
    padding-bottom: 16px;
    background: #f5f6f8;
  }

  .home-hero {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin: 12px 12px 0;
    padding: 16px 18px;
    border-radius: 12px;
    background: linear-gradient(135deg, #eaf4ff, #f6fbff);
    border: 1px solid #d8ecff;
  }

  .home-hero__greeting {
    color: #1f2329;
    font-size: 17px;
    font-weight: 600;
  }

  .home-hero__date {
    margin-top: 6px;
    color: #64748b;
    font-size: 12px;
  }

  .home-hero__pc {
    flex: 0 0 auto;
    padding: 5px 12px;
    border-radius: 14px;
    background: #fff;
    color: #1890ff;
    font-size: 12px;
    border: 1px solid #bcdcff;
  }

  .home-loading {
    padding: 28px 0;
    text-align: center;
    color: #8b949e;
    font-size: 13px;
  }

  .home-error {
    margin: 16px;
    padding: 16px;
    border-radius: 10px;
    background: #fff;
    text-align: center;
    color: #f5222d;
    font-size: 13px;
  }

  .home-error button {
    margin-top: 10px;
    padding: 6px 16px;
    border: 1px solid #1890ff;
    border-radius: 16px;
    background: #fff;
    color: #1890ff;
    font-size: 13px;
  }

  .home-section-title {
    padding: 18px 18px 8px;
    color: #1f2329;
    font-size: 15px;
    font-weight: 600;
  }

  .home-card {
    margin: 12px 12px 0;
    padding: 14px 8px 6px;
    border-radius: 12px;
    background: #fff;
  }

  .home-card__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 6px 10px;
  }

  .home-card__title {
    color: #1f2329;
    font-size: 14px;
    font-weight: 600;
  }

  .home-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 4px 0;
  }

  .home-entry {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    padding: 8px 2px;
    cursor: pointer;
  }

  .home-entry:active {
    opacity: 0.65;
  }

  .home-entry__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 38px;
    height: 38px;
    border-radius: 11px;
    background: #eaf4ff;
    color: #1890ff;
    font-size: 15px;
    font-weight: 600;
  }

  .home-entry__text {
    max-width: 100%;
    color: #4b5563;
    font-size: 12px;
    line-height: 1.3;
    text-align: center;
    word-break: break-all;
  }

  .home-empty {
    padding: 40px 0;
    text-align: center;
    color: #8b949e;
    font-size: 13px;
  }

  .home-tip {
    padding: 16px 20px calc(8px + env(safe-area-inset-bottom));
    color: #9aa3af;
    font-size: 12px;
    line-height: 1.6;
  }
</style>
