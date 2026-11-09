<template>
  <div class="mine">
    <div class="mine-header">
      <div class="mine-avatar">{{ initial }}</div>
      <div class="mine-info">
        <div class="mine-info__name">{{ userName || '未登录' }}</div>
        <div class="mine-info__account">账号：{{ loginName || '-' }}</div>
      </div>
    </div>

    <div class="mine-list">
      <a class="mine-item" href="/">
        <span>打开电脑版</span>
        <span class="mine-item__arrow">›</span>
      </a>
      <div class="mine-item">
        <span>手机端版本</span>
        <span class="mine-item__value">{{ version }}</span>
      </div>
    </div>

    <button class="mine-logout" type="button" @click="handleLogout">退出登录</button>
  </div>
</template>

<script>
export default {
  name: 'MobileMine',
  data () {
    return {
      version: '3.5.0'
    }
  },
  computed: {
    userName () {
      return this.$store.getters.userName
    },
    loginName () {
      return this.$store.getters.loginName
    },
    initial () {
      return (this.userName || 'U').charAt(0).toUpperCase()
    }
  },
  methods: {
    handleLogout () {
      if (!window.confirm('确定要退出登录吗？')) {
        return
      }
      this.$store.dispatch('Logout').then(() => {
        this.$router.replace('/login')
      })
    }
  }
}
</script>

<style scoped>
  .mine {
    min-height: 100%;
    padding-bottom: 24px;
    background: #f5f6f8;
  }

  .mine-header {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: calc(24px + env(safe-area-inset-top)) 18px 28px;
    background: linear-gradient(135deg, #1890ff, #0d6fd8);
    color: #fff;
  }

  .mine-avatar {
    width: 54px;
    height: 54px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.22);
    line-height: 54px;
    text-align: center;
    font-size: 22px;
    font-weight: 600;
  }

  .mine-info__name {
    font-size: 17px;
    font-weight: 600;
  }

  .mine-info__account {
    margin-top: 5px;
    font-size: 12px;
    opacity: 0.82;
  }

  .mine-list {
    margin: 12px;
    border-radius: 12px;
    background: #fff;
    overflow: hidden;
  }

  .mine-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 15px 16px;
    color: #1f2329;
    font-size: 14px;
  }

  .mine-item + .mine-item {
    border-top: 1px solid #f0f1f3;
  }

  .mine-item__arrow {
    color: #c4c9d1;
    font-size: 18px;
  }

  .mine-item__value {
    color: #8b949e;
    font-size: 13px;
  }

  .mine-logout {
    width: calc(100% - 24px);
    height: 46px;
    margin: 24px 12px 0;
    border: 0;
    border-radius: 23px;
    background: #fff;
    color: #f5222d;
    font-size: 15px;
    font-weight: 600;
  }
</style>
