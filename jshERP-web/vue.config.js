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
        proxy: [{
            // 后端已取消 context-path，接口挂在根路径；
            // 仅代理接口请求，页面导航与前端构建资源仍由开发服务器处理
            context: function (pathname, req) {
                const accept = (req.headers && req.headers.accept) || ''
                if (accept.indexOf('text/html') !== -1) { return false }
                return !/^\/(js|css|img|fonts|media|static|sockjs-node|__webpack|favicon\.ico)/.test(pathname)
            },
            target: 'http://localhost:9999',
            changeOrigin: true,
            ws: false
        }]
    },
    lintOnSave: undefined
}
