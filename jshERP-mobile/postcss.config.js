const path = require('path')

// 手机端源码位于 jshERP-web 之外，postcss 配置需要就近声明；
// autoprefixer 复用 jshERP-web 已安装的依赖。
const webModules = path.resolve(__dirname, '../jshERP-web/node_modules')
const autoprefixer = require(path.join(webModules, 'autoprefixer'))

module.exports = {
  plugins: [autoprefixer()]
}
