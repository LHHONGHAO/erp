<template>
  <data-table
    ref="dataTable"
    v-bind="$attrs"
    :extra-columns="tableColumns"
    :default-columns="resolvedDefaultColumns"
    v-on="$listeners">
    <!-- 父组件自定义列插槽原样透传（内置渲染以外） -->
    <template v-for="name in forwardSlotNames" v-slot:[name]="scope">
      <slot :name="name" v-bind="scope"></slot>
    </template>

    <!-- 内置：图片（100*80、圆角12） -->
    <template slot="mtImage" slot-scope="text, record">
      <img v-if="resolveImg(record)" :src="resolveImg(record)" class="mt-row-img" />
      <span v-else class="mt-row-img-placeholder">无图</span>
    </template>

    <!-- 内置：商品名称（含序/批标记） -->
    <template slot="mtName" slot-scope="text, record">
      {{ record.name }}
      <a-tag v-if="record.enableSerialNumber == 1" color="orange">序</a-tag>
      <a-tag v-if="record.enableBatchNumber == 1" color="orange">批</a-tag>
    </template>

    <!-- 内置：规格显示（每个规格单独一行） -->
    <template slot="mtSpec" slot-scope="text, record">
      <div class="mt-spec-cell">{{ resolveSpec(record) }}</div>
    </template>

    <!-- 内置：操作列 -->
    <template slot="mtAction" slot-scope="text, record">
      <span v-if="visibleActions(record).length > 0">
        <template v-for="(act, index) in visibleActions(record)">
          <a :key="'act-' + index" @click="onAction(act, record)">{{ act.title }}</a>
          <a-divider v-if="index < visibleActions(record).length - 1" :key="'div-' + index" type="vertical" />
        </template>
      </span>
    </template>
  </data-table>
</template>

<script>
  import Vue from 'vue'
  import DataTable from '@/components/DataTable'
  import { MATERIAL_COLUMNS, buildTableColumns, defaultColumnKeys } from '@/views/material/config/materialColumns'
  import { getRowImgUrl, getSpecDisplay } from '@/views/material/utils/materialDisplay'

  /** 内置插槽名：父组件透传时跳过，避免同名插槽冲突 */
  const BUILTIN_SLOTS = ['mtImage', 'mtName', 'mtSpec', 'mtAction']

  export default {
    name: 'MaterialTable',
    components: { DataTable },
    props: {
      /** 列配置（默认使用商品/库存统一列配置） */
      columns: { type: Array, default: () => MATERIAL_COLUMNS },
      /** 数据粒度：spu=商品(单类) / sku=SKU(单品) / skuDepot=SKU+仓库(库存)，为空不过滤 */
      dimension: { type: String, default: '' },
      /** 默认显示列（field 数组），不传则按列配置的 defaultVisible + 默认顺序 */
      defaultColumns: { type: Array, default: undefined },
      /** 操作列按钮：[{ title, click(record), show(record)? }] */
      actions: { type: Array, default: () => [] }
    },
    computed: {
      /** 按粒度过滤并转换为 a-table 列 */
      tableColumns() {
        return buildTableColumns(this.dimension, this.columns)
      },
      /** 默认显示列 */
      resolvedDefaultColumns() {
        if (this.defaultColumns && this.defaultColumns.length > 0) {
          const allKeys = this.tableColumns.map(c => c.dataIndex)
          return this.defaultColumns.filter(k => allKeys.indexOf(k) > -1)
        }
        return defaultColumnKeys(this.dimension, this.columns)
      },
      /** 需要透传给 DataTable 的父组件插槽名 */
      forwardSlotNames() {
        return Object.keys(this.$scopedSlots).filter(name => BUILTIN_SLOTS.indexOf(name) === -1)
      }
    },
    created() {
      // 兼容旧版本列设置：原「规格JSON」列已改为「规格显示」列
      const storageKey = this.$attrs.storageKey || this.$attrs['storage-key']
      if (!storageKey) return
      const saved = Vue.ls.get(storageKey)
      if (saved && typeof saved === 'string' && saved.indexOf('specJson') > -1) {
        Vue.ls.set(storageKey, saved.split(',').map(k => k === 'specJson' ? 'specDisplay' : k))
      }
    },
    methods: {
      resolveImg(record) {
        return getRowImgUrl(record)
      },
      resolveSpec(record) {
        return getSpecDisplay(record)
      },
      visibleActions(record) {
        return (this.actions || []).filter(act => !act.show || act.show(record))
      },
      onAction(act, record) {
        if (act && typeof act.click === 'function') {
          act.click(record)
        }
      },
      /** 暴露给父组件：恢复默认列 */
      resetColumns() {
        this.$refs.dataTable.resetColumns()
      }
    }
  }
</script>

<style scoped>
  .mt-row-img {
    width: 100px;
    height: 80px;
    object-fit: cover;
    border-radius: 12px;
    vertical-align: middle;
  }
  .mt-row-img-placeholder {
    font-size: 12px;
    color: #ccc;
  }
  .mt-spec-cell {
    white-space: pre-line;
    line-height: 18px;
    word-break: break-all;
  }
</style>
