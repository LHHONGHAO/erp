/**
 * 手机端开发服务器：http://localhost:3001/mobile/
 * 复用 jshERP-web 依赖与 jshERP-mobile/vue.config.js。
 */
const path = require('path')
const { spawnSync } = require('child_process')

const webRoot = path.resolve(__dirname, '../../jshERP-web')
const bin = path.join(webRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'vue-cli-service.cmd' : 'vue-cli-service')

const result = spawnSync(bin, ['serve'], {
  cwd: webRoot,
  stdio: 'inherit',
  shell: process.platform === 'win32',
  env: Object.assign({}, process.env, {
    VUE_CLI_SERVICE_CONFIG_PATH: path.resolve(__dirname, '../vue.config.js')
  })
})

process.exit(result.status === null ? 1 : result.status)
