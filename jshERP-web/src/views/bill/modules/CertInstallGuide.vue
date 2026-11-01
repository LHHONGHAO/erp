<template>
  <div class="cert-guide">
    <div class="cert-guide-panel">
      <div class="cert-guide-header">
        <span class="cert-guide-title">无法获取摄像头权限</span>
        <a-icon type="close" class="cert-guide-close" @click="$emit('close')" />
      </div>
      <div class="cert-guide-body">
        <p class="cert-guide-desc">
          {{ reasonText }}扫码需要浏览器允许使用摄像头，且必须是 HTTPS 安全连接。请先下载并安装本系统的证书，再用 HTTPS 地址访问后重新扫码。
        </p>
        <ul class="cert-guide-steps">
          <li>
            <span class="cert-guide-step">1</span>
            <div class="cert-guide-step-main">
              <b>下载证书并按说明安装</b>
              <div class="cert-guide-actions">
                <a class="cert-guide-btn" href="/cert/jsh-erp.cer" download="jsh-erp.cer">下载证书</a>
                <a class="cert-guide-btn secondary" href="/cert/install.html" target="_blank" rel="noopener">查看安装说明</a>
              </div>
            </div>
          </li>
          <li>
            <span class="cert-guide-step">2</span>
            <div class="cert-guide-step-main">
              <b>使用 HTTPS 地址访问系统</b>
              <span class="cert-guide-url">{{ httpsUrl }}</span>
            </div>
          </li>
          <li>
            <span class="cert-guide-step">3</span>
            <div class="cert-guide-step-main">
              <b>返回销售开单页，重新扫码并允许摄像头权限</b>
            </div>
          </li>
        </ul>
        <div class="cert-guide-tip">
          Android 证书安装位置：设置 → 安全 → 加密与凭据 → 安装证书 → CA 证书。<br>
          iPhone：设置 → 已下载描述文件 → 安装，再到 设置 → 通用 → 关于本机 → 证书信任设置 中开启完全信任。
        </div>
      </div>
      <div class="cert-guide-footer">
        <a-button @click="$emit('close')">关闭</a-button>
        <a-button type="primary" style="margin-left: 10px" @click="$emit('retry')">重新尝试</a-button>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'CertInstallGuide',
    computed: {
      // 提示原因：非安全源时直接说明不是 HTTPS
      reasonText () {
        return window.isSecureContext ? '当前浏览器未授权摄像头。' : '当前页面不是 HTTPS 安全连接。'
      },
      // 统一约定的 HTTPS 访问地址（端口固定为本地部署的 9999）
      httpsUrl () {
        return 'https://' + window.location.hostname + ':9999'
      }
    }
  }
</script>

<style scoped>
  .cert-guide {
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 1000;
    background: rgba(0, 0, 0, 0.6);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 16px;
  }
  .cert-guide-panel {
    width: 100%;
    max-width: 520px;
    max-height: 100%;
    overflow-y: auto;
    background: #fff;
    border-radius: 14px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  }
  .cert-guide-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 18px;
    border-bottom: 1px solid #f0f0f0;
  }
  .cert-guide-title {
    font-size: 17px;
    font-weight: 600;
    color: #fa541c;
  }
  .cert-guide-close {
    font-size: 20px;
    color: #8c8c8c;
  }
  .cert-guide-body {
    padding: 16px 18px 4px;
  }
  .cert-guide-desc {
    font-size: 14px;
    line-height: 1.7;
    color: #595959;
    margin-bottom: 14px;
  }
  .cert-guide-steps {
    list-style: none;
    padding: 0;
    margin: 0;
  }
  .cert-guide-steps li {
    display: flex;
    margin-bottom: 16px;
  }
  .cert-guide-step {
    flex: none;
    width: 24px;
    height: 24px;
    margin-right: 10px;
    border-radius: 50%;
    background: #1890ff;
    color: #fff;
    font-size: 13px;
    line-height: 24px;
    text-align: center;
  }
  .cert-guide-step-main {
    flex: 1;
    min-width: 0;
    font-size: 14px;
    color: #262626;
  }
  .cert-guide-actions {
    display: flex;
    margin-top: 10px;
  }
  .cert-guide-btn {
    display: inline-block;
    padding: 8px 16px;
    margin-right: 10px;
    border-radius: 8px;
    background: #1890ff;
    color: #fff;
    font-size: 14px;
    text-decoration: none;
  }
  .cert-guide-btn.secondary {
    background: #fff;
    color: #1890ff;
    border: 1px solid #1890ff;
  }
  .cert-guide-url {
    display: block;
    margin-top: 8px;
    padding: 7px 10px;
    border-radius: 8px;
    background: #f0f5ff;
    color: #0050b3;
    font-size: 13px;
    word-break: break-all;
  }
  .cert-guide-tip {
    padding: 10px 12px;
    border-radius: 8px;
    background: #fafafa;
    color: #8c8c8c;
    font-size: 12px;
    line-height: 1.7;
  }
  .cert-guide-footer {
    display: flex;
    justify-content: flex-end;
    padding: 14px 18px 18px;
  }
</style>
