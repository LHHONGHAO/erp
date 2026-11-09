<template>
  <div class="login-page">
    <div class="login-brand">
      <div class="login-brand__logo">易</div>
      <div class="login-brand__name">{{ systemTitle }}</div>
      <div class="login-brand__desc">移动端 · 进销存</div>
    </div>

    <div class="login-card">
      <label class="login-field">
        <span class="login-field__label">账号</span>
        <input
          v-model.trim="form.loginName"
          class="login-field__input"
          type="text"
          autocomplete="username"
          placeholder="请输入用户名"
          @keyup.enter="submit" />
      </label>

      <label class="login-field">
        <span class="login-field__label">密码</span>
        <input
          v-model="form.password"
          class="login-field__input"
          type="password"
          autocomplete="current-password"
          placeholder="请输入密码"
          @keyup.enter="submit" />
      </label>

      <div v-if="checkcodeEnabled" class="login-field login-field--code">
        <span class="login-field__label">验证码</span>
        <input
          v-model.trim="form.code"
          class="login-field__input"
          type="text"
          placeholder="请输入验证码"
          @keyup.enter="submit" />
        <img
          v-if="captchaImage"
          class="login-captcha"
          :src="captchaImage"
          alt="验证码"
          @click="loadCaptcha" />
        <span v-else class="login-captcha login-captcha--empty" @click="loadCaptcha">点击获取</span>
      </div>

      <div v-if="errorText" class="login-error">{{ errorText }}</div>

      <button class="login-button" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '登录中...' : '登 录' }}
      </button>
    </div>

    <div class="login-footer">
      <a href="/">使用电脑版</a>
    </div>
  </div>
</template>

<script>
import md5 from 'md5'
import { getCheckcodeFlag, getRandomImage } from '../api/auth'

export default {
  name: 'MobileLogin',
  data () {
    return {
      systemTitle: window.SYS_TITLE || '管伊佳ERP',
      form: { loginName: '', password: '', code: '' },
      uuid: '',
      captchaImage: '',
      checkcodeEnabled: false,
      submitting: false,
      errorText: ''
    }
  },
  created () {
    this.loadCheckcodeFlag()
  },
  methods: {
    loadCheckcodeFlag () {
      getCheckcodeFlag().then(res => {
        this.checkcodeEnabled = String(res) === '1'
        if (this.checkcodeEnabled) {
          this.loadCaptcha()
        }
      }).catch(() => {
        this.checkcodeEnabled = false
      })
    },
    loadCaptcha () {
      getRandomImage().then(res => {
        if (res && res.code === 200 && res.data) {
          this.uuid = res.data.uuid
          this.captchaImage = res.data.base64
        }
      }).catch(() => {
        this.captchaImage = ''
      })
    },
    submit () {
      if (this.submitting) {
        return
      }
      this.errorText = ''
      if (!this.form.loginName) {
        this.errorText = '请输入用户名'
        return
      }
      if (!this.form.password) {
        this.errorText = '请输入密码'
        return
      }
      if (this.checkcodeEnabled && !this.form.code) {
        this.errorText = '请输入验证码'
        return
      }

      this.submitting = true
      this.$store.dispatch('Login', {
        loginName: this.form.loginName,
        password: md5(this.form.password),
        code: this.form.code,
        uuid: this.uuid
      }).then(() => {
        const redirect = this.$route.query.redirect
        this.$router.replace(redirect && redirect.indexOf('/') === 0 ? redirect : '/home')
      }).catch(err => {
        this.errorText = (err && err.message) || '登录失败，请稍后重试'
        if (this.checkcodeEnabled) {
          this.form.code = ''
          this.loadCaptcha()
        }
      }).finally(() => {
        this.submitting = false
      })
    }
  }
}
</script>

<style scoped>
  .login-page {
    display: flex;
    flex-direction: column;
    min-height: 100%;
    padding: 0 24px;
    background: linear-gradient(165deg, #1890ff 0%, #0d6fd8 45%, #0a4fae 100%);
  }

  .login-brand {
    padding: calc(64px + env(safe-area-inset-top)) 0 32px;
    color: #fff;
    text-align: center;
  }

  .login-brand__logo {
    width: 60px;
    height: 60px;
    margin: 0 auto 14px;
    border-radius: 16px;
    background: rgba(255, 255, 255, 0.18);
    line-height: 60px;
    font-size: 28px;
    font-weight: 600;
  }

  .login-brand__name {
    font-size: 22px;
    font-weight: 600;
    letter-spacing: 1px;
  }

  .login-brand__desc {
    margin-top: 6px;
    font-size: 13px;
    opacity: 0.8;
  }

  .login-card {
    padding: 20px 18px 18px;
    border-radius: 14px;
    background: #fff;
    box-shadow: 0 12px 32px rgba(4, 40, 90, 0.18);
  }

  .login-field {
    display: flex;
    align-items: center;
    gap: 10px;
    padding-bottom: 12px;
    border-bottom: 1px solid #eceef1;
  }

  .login-field + .login-field {
    margin-top: 14px;
  }

  .login-field__label {
    flex: 0 0 48px;
    color: #6b7280;
    font-size: 14px;
  }

  .login-field__input {
    flex: 1 1 auto;
    min-width: 0;
    height: 40px;
    border: 0;
    outline: none;
    background: transparent;
    font-size: 15px;
    color: #1f2329;
  }

  .login-field--code .login-field__input {
    flex: 1 1 auto;
  }

  .login-captcha {
    flex: 0 0 96px;
    height: 34px;
    border-radius: 4px;
    object-fit: cover;
    background: #f5f6f8;
    cursor: pointer;
  }

  .login-captcha--empty {
    display: flex;
    align-items: center;
    justify-content: center;
    color: #8b949e;
    font-size: 12px;
  }

  .login-error {
    margin-top: 12px;
    color: #f5222d;
    font-size: 13px;
  }

  .login-button {
    width: 100%;
    height: 46px;
    margin-top: 20px;
    border: 0;
    border-radius: 23px;
    background: linear-gradient(90deg, #1890ff, #0d6fd8);
    color: #fff;
    font-size: 16px;
    font-weight: 600;
    letter-spacing: 2px;
    cursor: pointer;
  }

  .login-button:disabled {
    opacity: 0.6;
  }

  .login-footer {
    margin-top: auto;
    padding: 24px 0 calc(24px + env(safe-area-inset-bottom));
    text-align: center;
    font-size: 13px;
  }

  .login-footer a {
    color: rgba(255, 255, 255, 0.85);
  }
</style>
