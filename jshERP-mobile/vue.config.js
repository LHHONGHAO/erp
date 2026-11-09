const path = require('path')

const mobileRoot = __dirname
const mobilePublic = path.join(mobileRoot, 'public')

/**
 * 手机端独立构建配置。
 * 构建入口由 jshERP-mobile/scripts/build.js 通过 VUE_CLI_SERVICE_CONFIG_PATH 指定，
 * 产物输出到 jshERP-web/dist/mobile，与电脑端产物互不影响。
 */
module.exports = {
  // 产物部署在站点 /mobile/ 目录下，静态资源按绝对路径 /mobile/ 引用
  publicPath: '/mobile/',
  outputDir: 'dist/mobile',
  productionSourceMap: false,
  lintOnSave: false,

  pages: {
    index: {
      entry: path.join(mobileRoot, 'src/main.js'),
      template: path.join(mobilePublic, 'index.html'),
      filename: 'index.html'
    }
  },

  chainWebpack: config => {
    config.resolve.alias.set('@mobile', path.join(mobileRoot, 'src'))
    // 只复制手机端 public（manifest / 图标 / sw.js），不带入电脑端 public 资源
    config.plugin('copy').tap(() => [[{
      from: mobilePublic,
      to: '.',
      toType: 'dir',
      ignore: ['index.html']
    }]])
  },

  devServer: {
    host: '0.0.0.0',
    port: 3001,
    disableHostCheck: true,
    headers: {
      'Cache-Control': 'no-store, no-cache, must-revalidate'
    },
    // 开发地址：http://localhost:3001/mobile/
    historyApiFallback: {
      index: '/mobile/index.html'
    },
    proxy: {
      '/': {
        bypass: function (req) {
          const url = req.url || ''
          // 手机端页面与静态资源交给开发服务器
          if (url.indexOf('/mobile') === 0) {
            return false
          }
          const accept = (req.headers && req.headers.accept) || ''
          if (accept.indexOf('text/html') !== -1) {
            return '/mobile/index.html'
          }
          return null
        },
        // 与电脑端开发环境一致：默认代理到本机后端 HTTPS(9999)
        target: process.env.VUE_APP_API_TARGET || 'https://localhost:9999',
        changeOrigin: true,
        secure: false,
        ws: false
      }
    }
  }
}
