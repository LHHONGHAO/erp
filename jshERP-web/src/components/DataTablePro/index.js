import DataTablePro from './index.vue'

/**
 * 通用表格模块
 * 全局注册后可直接在业务页面使用 <data-table-pro ... />
 */
DataTablePro.install = function(Vue) {
  Vue.component(DataTablePro.name, DataTablePro)
}

export default DataTablePro
