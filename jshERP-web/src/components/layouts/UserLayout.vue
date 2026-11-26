<template>
  <div class="back-layout" :style="{ backgroundImage: 'url(' + baseUrl + 'static/bgimg.png?v=1)' }">
    <div class="lang-setting">
      <a-select placeholder="请选择语言" showSearch optionFilterProp="children" :value="currentLang" @change="handleChangeLang" class="select-list">
        <a-select-option class="ignore" value="chinese_simplified">简体中文</a-select-option>
        <a-select-option class="ignore" value="chinese_traditional">繁體中文</a-select-option>
        <a-select-option class="ignore" value="english">English</a-select-option>
        <a-select-option class="ignore" value="korean">한국어</a-select-option>
        <a-select-option class="ignore" value="japanese">しろうと</a-select-option>
        <a-select-option class="ignore" value="vietnamese">Tiếng Việt</a-select-option>
        <a-select-option class="ignore" value="hindi">हिन्दी</a-select-option>
        <a-select-option class="ignore" value="russian">Русский язык</a-select-option>
        <a-select-option class="ignore" value="french">Français</a-select-option>
        <a-select-option class="ignore" value="ukrainian">УкраїнськаName</a-select-option>
        <a-select-option class="ignore" value="norwegian">Norge</a-select-option>
        <a-select-option class="ignore" value="welsh">color name</a-select-option>
        <a-select-option class="ignore" value="dutch">nederlands</a-select-option>
        <a-select-option class="ignore" value="filipino">Pilipino</a-select-option>
        <a-select-option class="ignore" value="lao">ກະຣຸນາ</a-select-option>
        <a-select-option class="ignore" value="telugu">తెలుగుQFontDatabase</a-select-option>
        <a-select-option class="ignore" value="romanian">Română</a-select-option>
        <a-select-option class="ignore" value="nepali">नेपालीName</a-select-option>
        <a-select-option class="ignore" value="haitian_creole">Kreyòl ayisyen</a-select-option>
        <a-select-option class="ignore" value="czech">český</a-select-option>
        <a-select-option class="ignore" value="swedish">Svenska</a-select-option>
        <a-select-option class="ignore" value="malagasy">Malagasy</a-select-option>
        <a-select-option class="ignore" value="burmese">ဗာရမ်</a-select-option>
        <a-select-option class="ignore" value="pashto">پښتوName</a-select-option>
        <a-select-option class="ignore" value="thai">คนไทย</a-select-option>
        <a-select-option class="ignore" value="armenian">Արմենյան</a-select-option>
        <a-select-option class="ignore" value="persian">Persian</a-select-option>
        <a-select-option class="ignore" value="kurdish">Kurdî</a-select-option>
        <a-select-option class="ignore" value="turkish">Türkçe</a-select-option>
        <a-select-option class="ignore" value="bulgarian">български</a-select-option>
        <a-select-option class="ignore" value="malay">Malay</a-select-option>
        <a-select-option class="ignore" value="swahili">Kiswahili</a-select-option>
        <a-select-option class="ignore" value="oriya">ଓଡିଆ</a-select-option>
        <a-select-option class="ignore" value="icelandic">ÍslandName</a-select-option>
        <a-select-option class="ignore" value="irish">Íris</a-select-option>
        <a-select-option class="ignore" value="khmer">ខ្មែរKCharselect unicode block name</a-select-option>
        <a-select-option class="ignore" value="gujarati">ગુજરાતી</a-select-option>
        <a-select-option class="ignore" value="slovak">Slovenská</a-select-option>
        <a-select-option class="ignore" value="kannada">ಕನ್ನಡ್Name</a-select-option>
        <a-select-option class="ignore" value="hebrew">היברית</a-select-option>
        <a-select-option class="ignore" value="hungarian">magyar</a-select-option>
        <a-select-option class="ignore" value="marathi">मराठीName</a-select-option>
        <a-select-option class="ignore" value="tamil">தாமில்</a-select-option>
        <a-select-option class="ignore" value="estonian">eesti keel</a-select-option>
        <a-select-option class="ignore" value="malayalam">മലമാലം</a-select-option>
        <a-select-option class="ignore" value="inuktitut">ᐃᓄᒃᑎᑐᑦ</a-select-option>
        <a-select-option class="ignore" value="arabic">بالعربية</a-select-option>
        <a-select-option class="ignore" value="deutsch">Deutsch</a-select-option>
        <a-select-option class="ignore" value="slovene">slovenščina</a-select-option>
        <a-select-option class="ignore" value="bengali">বেঙ্গালী</a-select-option>
        <a-select-option class="ignore" value="urdu">اوردو</a-select-option>
        <a-select-option class="ignore" value="azerbaijani">azerbaijani</a-select-option>
        <a-select-option class="ignore" value="portuguese">português</a-select-option>
        <a-select-option class="ignore" value="samoan">lifiava</a-select-option>
        <a-select-option class="ignore" value="afrikaans">afrikaans</a-select-option>
        <a-select-option class="ignore" value="tongan">汤加语</a-select-option>
        <a-select-option class="ignore" value="greek">ελληνικά</a-select-option>
        <a-select-option class="ignore" value="indonesian">IndonesiaName</a-select-option>
        <a-select-option class="ignore" value="spanish">Español</a-select-option>
        <a-select-option class="ignore" value="danish">dansk</a-select-option>
        <a-select-option class="ignore" value="amharic">amharic</a-select-option>
        <a-select-option class="ignore" value="punjabi">ਪੰਜਾਬੀName</a-select-option>
        <a-select-option class="ignore" value="albanian">albanian</a-select-option>
        <a-select-option class="ignore" value="lithuanian">Lietuva</a-select-option>
        <a-select-option class="ignore" value="italian">italiano</a-select-option>
        <a-select-option class="ignore" value="maltese">Malti</a-select-option>
        <a-select-option class="ignore" value="finnish">suomi</a-select-option>
        <a-select-option class="ignore" value="catalan">català</a-select-option>
        <a-select-option class="ignore" value="croatian">hrvatski</a-select-option>
        <a-select-option class="ignore" value="bosnian">bosnian</a-select-option>
        <a-select-option class="ignore" value="polish">Polski</a-select-option>
        <a-select-option class="ignore" value="latvian">latviešu</a-select-option>
        <a-select-option class="ignore" value="maori">Maori</a-select-option>
      </a-select>
    </div>
    <div id="userLayout" :class="['user-layout-wrapper', device]">
      <div class="container">
        <div class="poster-img">
          <img :src="baseUrl + 'static/rightImg.png?v=320'">
        </div>
        <div class="right-form">
          <div class="top">
            <div class="header">
              <a-row>
                <a-col>
                  <a :href="baseUrl">
                    <span class="title">{{systemTitle}}</span>
                    <small class="desc">V3.6</small>
                  </a>
                </a-col>
              </a-row>
            </div>
          </div>
          <route-view></route-view>
        </div>
      </div>
    </div>
    <div class="footer" v-if="device === 'desktop'">
      <p>
        © 2015-2030 {{systemTitle}} - All Right Reserved 版权所有
      </p>
    </div>
  </div>
</template>

<script>
  import RouteView from "@/components/layouts/RouteView"
  import { mixinDevice } from '@/utils/mixin.js'

  export default {
    name: "UserLayout",
    components: { RouteView },
    mixins: [mixinDevice],
    data () {
      return {
        systemTitle: window.SYS_TITLE,
        // 静态资源前缀，兼容本地开发(/)与后端同源部署(/jshERP-boot/)
        baseUrl: process.env.BASE_URL,
        currentLang: '',
      }
    },
    mounted () {
      document.body.classList.add('userLayout')
    },
    beforeDestroy () {
      document.body.classList.remove('userLayout')
    },
    created () {
      this.currentLang = translate.language.getCurrent()
    },
    methods: {
      handleChangeLang(value) {
        translate.changeLanguage(value)
        this.currentLang = translate.language.getCurrent()
      }
    }
  }
</script>

<style scoped>
  .back-layout {
    width: 100%;
    height: 100%;
    background-size: cover;
    background-repeat: no-repeat;
    position: relative;
    overflow: hidden;
  }
  .back-layout .lang-setting {
    float: right;
    padding-top: 10px;
  }
  .back-layout .lang-setting .select-list {
    width:100px;
    text-align: left;
  }
  .back-layout .lang-setting .select-list >>> .ant-select-selection {
    border-color: #f8f8f8 !important;
  }
  .back-layout .lang-setting .select-list >>> .ant-select:hover .ant-select-selection {
    border-color: #e8e8e8 !important;
  }
  .back-layout .lang-setting .select-list >>> .ant-select-focused .ant-select-selection {
    border-color: #e8e8e8 !important;
    box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.05) !important;
  }
  #userLayout.user-layout-wrapper.mobile {
    position: fixed;
    left: 6%;
    top: 10%;
    margin-left: 0px;
  }
</style>
<style lang="less" scoped>
  #userLayout.user-layout-wrapper {
    position: fixed;
    left: 50%;
    top: 12%;
    margin-left: -543px;
    height: 100%;

    &.mobile {
      // 手机端卡片高度随内容变化：固定 460px 加 overflow:hidden 会把版权与版本号裁在卡片外
      .container .right-form {
        height: auto;
        min-height: 460px;
        padding-bottom: 16px;
      }
      .container {
        .main {
          max-width: 368px;
          width: 98%;
        }
      }
      .poster-img {
        display: none;
      }
    }

    .container {
      float: left;
      width: 100%;
      z-index: 99;
      height: 70%;

      .poster-img {
        float: left;
        height: 100%;
      }

      .right-form {
        background-size: 100%;
        position: relative;
        width: 340px;
        height: 460px;
        background: rgba(255, 255, 255, 1);
        border-radius: 8px;
        right: 0;
        top: 0;
        padding: 10px 30px 0 30px;
        margin-top: 50px;
        -webkit-box-shadow: 0 2px 6px 0 rgb(200 200 200);
        box-shadow: 0 2px 6px 0 rgb(200 200 200);
        overflow: hidden;

        a {
          text-decoration: none;
        }

        .top {
          text-align: center;

          .header {
            height: 44px;
            line-height: 44px;
            margin-top: 35px;
            margin-bottom: 35px;
            .title {
              font-size: 35px;
              color: #666;
              font-family: "Chinese Quote", -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
              font-weight: 700;
              position: relative;
              top: 2px;
            }
            .desc {
              font-size: 16px;
              color: #666;
              margin-top: 12px;
              margin-left: 10px;
              margin-bottom: 40px;
            }
          }
        }

        .main {
          min-width: 260px;
          width: 280px;
          margin: 0 auto;
        }
      }
    }
  }
  .footer {
    position: absolute;
    bottom: 0;
    padding: 0 16px;
    margin: 48px 0 12px;
    text-align: center;
    left: 33%;
    right: 33%;

    .links {
      margin-bottom: 8px;
      font-size: 14px;
      a {
        color: rgba(0, 0, 0, 0.45);
        transition: all 0.3s;
        &:not(:last-child) {
          margin-right: 40px;
        }
      }
    }
    .copyright {
      color: rgba(0, 0, 0, 0.45);
      font-size: 14px;
    }
  }
</style>