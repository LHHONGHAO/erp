/**
 * 手机端生产构建：复用 jshERP-web 依赖，使用 jshERP-mobile/vue.config.js，
 * 产物输出到 jshERP-web/dist/mobile（不能先于电脑端构建，否则会被电脑端构建清空）。
 */
const path = require('path')
const { spawnSync } = require('child_process')

const webRoot = path.resolve(__dirname, '../../jshERP-web')
const bin = path.join(webRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'vue-cli-service.cmd' : 'vue-cli-service')

const result = spawnSync(bin, ['build'], {
  cwd: webRoot,
  stdio: 'inherit',
  shell: process.platform === 'win32',
  env: Object.assign({}, process.env, {
    VUE_CLI_SERVICE_CONFIG_PATH: path.resolve(__dirname, '../vue.config.js')
  })
})

process.exit(result.status === null ? 1 : result.status)
