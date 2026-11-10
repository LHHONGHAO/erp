const path = require('path')
const CompressionPlugin = require('compression-webpack-plugin')

function resolve (dir) {
    return path.join(__dirname, dir)
}

// vue.config.js
module.exports = {
    // 生产构建与后端同源部署：访问地址只到端口，统一使用根路径
    publicPath: '/',
    // 如果你不需要生产环境的 source map，可以将其设置为 false 以加速生产环境构建。
    productionSourceMap: false,
    configureWebpack: config => {
    // 生产环境取消 console.log
        if (process.env.NODE_ENV === 'production') {
            config.optimization.minimizer[0].options.terserOptions.compress.drop_console = true
        }
    },
    chainWebpack: (config) => {
        // 删除 Vue CLI 默认注入的 prefetch：它会给每个异步 chunk 都塞一条 <link rel=prefetch>，
        // 本项目有 400+ 个懒加载模块，首页会因此后台下载十几 MB 资源，拖慢首屏。
        // 路由仍按需懒加载，只是不再提前预下载。
        config.plugins.delete('prefetch')
        config.resolve.alias
            .set('@$', resolve('src'))
            .set('@api', resolve('src/api'))
            .set('@assets', resolve('src/assets'))
            .set('@comp', resolve('src/components'))
            .set('@views', resolve('src/views'))
        // 生产环境，开启js\css压缩
        if (process.env.NODE_ENV === 'production') {
            config.plugin('compressionPlugin').use(new CompressionPlugin({
                test: /\.(js|css|less)$/, // 匹配文件名
                threshold: 10240, // 对超过10k的数据压缩
                deleteOriginalAssets: false // 删除源文件
            }))
            // 手机端预览页只在本地开发使用，生产构建时忽略对应模块，避免产出无用 chunk
            const webpack = require('webpack')
            config.plugin('ignore-mobile-preview').use(webpack.IgnorePlugin, [/MobilePreview|MobilePageDemo/])
        }
    },
    css: {
        loaderOptions: {
            less: {
                modifyVars: {
                    /* less 变量覆盖，用于自定义 ant design 主题 */
                    'primary-color': '#1890FF',
                    'link-color': '#1890FF',
                    'border-radius-base': '4px'
                },
                javascriptEnabled: true
            }
        }
    },
    devServer: {
        host: '0.0.0.0',
        port: 3000,
        // 允许通过局域网 IP（手机）访问开发服务器
        disableHostCheck: true,
        // 开发包文件名不带 hash，手机浏览器会缓存旧的异步 chunk，
        // 重新编译后 chunk id 变化会拿到旧文件 → 路由卡在加载中（白屏），这里强制不缓存
        headers: {
            'Cache-Control': 'no-store, no-cache, must-revalidate'
        },
        proxy: {
            '/': {
            // 页面导航与前端静态资源不代理，交给开发服务器
            bypass: function (req) {
                const accept = (req.headers && req.headers.accept) || ''
                if (accept.indexOf('text/html') !== -1) { return '/index.html' }
                if (/^\/(js|css|img|fonts|media|static|sockjs-node|__webpack|favicon\.ico)/.test(req.url)) { return req.url }
                return null
            },
            // 本地开发默认代理到本机后端 HTTPS(9999)；如需走 HTTP 兼容端口用 VUE_APP_API_TARGET=http://localhost:9998
            target: process.env.VUE_APP_API_TARGET || 'https://localhost:9999',
            changeOrigin: true,
            // 本地后端为自签名 HTTPS 直连，关闭证书校验
            secure: false,
            ws: false
            }
        }
    },
    lintOnSave: undefined
}
