<template>
  <div class="home">
    <div class="home-header">
      <div class="home-header__top">
        <div>
          <div class="home-header__greeting">{{ greeting }}，{{ userName || '你好' }}</div>
          <div class="home-header__date">{{ today }}</div>
        </div>
        <a class="home-header__pc" href="/">电脑版</a>
      </div>
    </div>

    <div v-if="loading" class="home-loading">功能加载中...</div>

    <div v-else-if="errorText" class="home-error">
      <p>{{ errorText }}</p>
      <button type="button" @click="loadMenu">重新加载</button>
    </div>

    <template v-else>
      <div v-for="group in groups" :key="group.id" class="home-group">
        <div class="home-group__title">{{ group.text }}</div>
        <div class="home-group__grid">
          <div
            v-for="item in group.items"
            :key="item.url"
            class="home-entry"
            @click="open(item)">
            <span class="home-entry__icon">{{ initial(item.text) }}</span>
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

export default {
  name: 'MobileHome',
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
    this.loadMenu()
  },
  methods: {
    loadMenu () {
      this.loading = true
      this.errorText = ''
      getMenu({ pNumber: 0, userId: ls.get(USER_ID) }).then(res => {
        this.groups = this.buildGroups(res)
      }).catch(() => {
        this.errorText = '功能菜单加载失败，请检查网络后重试'
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
        if (items.length) {
          groups.push({ id: node.id, text: node.text, items })
        }
      })
      return groups
    },
    initial (text) {
      return (text || '').charAt(0)
    },
    open (item) {
      if (/^https?:\/\//.test(item.url)) {
        window.open(item.url, '_blank')
        return
      }
      window.location.href = item.url
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

  .home-header {
    padding: calc(20px + env(safe-area-inset-top)) 18px 26px;
    background: linear-gradient(135deg, #1890ff, #0d6fd8);
    color: #fff;
  }

  .home-header__top {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
  }

  .home-header__greeting {
    font-size: 18px;
    font-weight: 600;
  }

  .home-header__date {
    margin-top: 6px;
    font-size: 12px;
    opacity: 0.8;
  }

  .home-header__pc {
    padding: 5px 12px;
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.2);
    color: #fff;
    font-size: 12px;
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

  .home-group {
    margin: 12px 12px 0;
    padding: 14px 8px 6px;
    border-radius: 12px;
    background: #fff;
  }

  .home-group__title {
    padding: 0 6px 10px;
    color: #1f2329;
    font-size: 14px;
    font-weight: 600;
  }

  .home-group__grid {
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
