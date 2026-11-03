<template>
  <div class="mpd" :class="{ 'mpd-mobile': isMobileView }">
    <div class="mpd-frame">
      <mobile-page title="标准手机页面" :fill="false" @back="handleBack">
        <div class="mpd-card">
          <div class="mpd-row" v-for="row in rows" :key="row.label">
            <span class="mpd-label">
              <i v-if="row.required" class="mpd-required">*</i>{{ row.label }}
            </span>
            <a-input :placeholder="row.placeholder" class="mpd-input" />
          </div>
        </div>
        <div class="mpd-card">
          <div class="mpd-card-title">示例内容区</div>
          <div class="mpd-desc">
            页面内容统一放在白色内容区，可上下滚动；蓝色顶栏（返回 + 居左标题）与底部操作栏由标准页面组件提供。
          </div>
        </div>
        <template slot="footer">
          <div class="mpd-footer">
            <a-button class="mpd-cancel">取消</a-button>
            <a-button type="primary" class="mpd-save" @click="handleSave">保存</a-button>
          </div>
        </template>
      </mobile-page>
    </div>
    <div class="mpd-tip">
      标准手机页面模版预览：蓝色顶栏 + 最左返回 + 标题居左 + 白色内容区 + 底部操作栏（手机上打开为全屏效果）
    </div>
  </div>
</template>

<script>
  import MobilePage from '@/components/page/MobilePage'

  export default {
    name: 'MobilePageDemo',
    components: { MobilePage },
    data() {
      return {
        isMobileView: window.innerWidth <= 768,
        rows: [
          { label: '名称', placeholder: '请输入名称', required: true },
          { label: '单位', placeholder: '请输入单位', required: true },
          { label: '品牌', placeholder: '请输入品牌' },
          { label: '助记码', placeholder: '自动生成助记码' },
          { label: '类别', placeholder: '请选择类别' },
          { label: '制造商', placeholder: '请输入制造商' }
        ]
      }
    },
    mounted() {
      window.addEventListener('resize', this.onWindowResize)
    },
    beforeDestroy() {
      window.removeEventListener('resize', this.onWindowResize)
    },
    methods: {
      onWindowResize() {
        this.isMobileView = window.innerWidth <= 768
      },
      handleBack() {
        this.$message.info('点击了返回按钮（示例）')
      },
      handleSave() {
        this.$message.success('保存成功（示例）')
      }
    }
  }
</script>

<style scoped>
  .mpd {
    min-height: 100vh;
    box-sizing: border-box;
    background: #f0f2f5;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 20px 16px;
  }

  /* 手机端全屏显示 */
  .mpd-mobile {
    padding: 0;
    height: 100vh;
  }

  .mpd-mobile .mpd-frame {
    width: 100%;
    height: 100vh;
    border-radius: 0;
    box-shadow: none;
  }

  .mpd-mobile .mpd-tip {
    display: none;
  }

  .mpd-frame {
    width: 390px;
    height: 720px;
    max-height: calc(100vh - 120px);
    background: #fff;
    border-radius: 8px;
    overflow: hidden;
    box-shadow: 0 6px 24px rgba(0, 0, 0, 0.15);
  }

  .mpd-tip {
    margin-top: 14px;
    color: #8c8c8c;
    font-size: 13px;
    text-align: center;
  }

  .mpd-card {
    background: #fff;
    border-radius: 6px;
    border: 1px solid #f0f0f0;
    overflow: hidden;
    margin-bottom: 12px;
  }

  .mpd-row {
    display: flex;
    align-items: center;
    min-height: 48px;
    padding: 4px 12px;
    border-bottom: 1px solid #f0f0f0;
  }

  .mpd-row:last-child {
    border-bottom: none;
  }

  .mpd-label {
    flex: 0 0 84px;
    width: 84px;
    color: #333;
    font-size: 14px;
    text-align: right;
    padding-right: 10px;
    box-sizing: border-box;
  }

  .mpd-required {
    color: #f5222d;
    font-style: normal;
    margin-right: 2px;
  }

  .mpd-input {
    flex: 1 1 auto;
    border: none !important;
    background: transparent !important;
    box-shadow: none !important;
    font-size: 15px;
  }

  .mpd-input:focus,
  .mpd-input:hover {
    border: none !important;
    box-shadow: none !important;
  }

  .mpd-card-title {
    padding: 12px 12px 4px;
    font-size: 15px;
    font-weight: 600;
    color: rgba(0, 0, 0, 0.85);
  }

  .mpd-desc {
    padding: 0 12px 14px;
    font-size: 13px;
    line-height: 20px;
    color: #8c8c8c;
  }

  .mpd-footer {
    display: flex;
    justify-content: flex-end;
  }

  .mpd-cancel {
    margin-right: 8px;
  }

  .mpd-save {
    min-width: 88px;
  }
</style>
