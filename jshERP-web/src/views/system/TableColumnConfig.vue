<template>
  <div style="padding: 24px;">
    <h2>表格列显示配置</h2>
    <p style="color:#999;margin-top:-8px;">左侧选择菜单，配置对应表格的数据表、字段与默认显示列。</p>

    <a-row :gutter="16">
      <!-- 左侧：主菜单树（复制左侧菜单栏） -->
      <a-col :span="6">
        <a-card title="菜单" size="small" class="menu-card" style="height:100%;">
          <a-select
            show-search
            option-filter-prop="children"
            placeholder="选择表格场景（含弹窗/明细）"
            style="width:100%;margin-bottom:8px;"
            :value="currentScene ? currentScene.key : undefined"
            @change="onSceneSelect">
            <a-select-option v-for="s in sceneOptions" :key="s.key" :value="s.key">
              {{ s.name }}（{{ s.remark }}）
            </a-select-option>
          </a-select>
          <a-menu
            v-if="menus.length"
            mode="inline"
            :defaultOpenKeys="topLevelKeys"
            :selectedKeys="[activeKey]"
            @click="onMenuClick">
            <template v-for="menu in menus">
              <a-sub-menu v-if="menu.children && menu.children.length" :key="String(menu.id)">
                <span slot="title">{{ menu.text }}</span>
                <template v-for="child in menu.children">
                  <a-sub-menu v-if="child.children && child.children.length" :key="String(child.id)">
                    <span slot="title">{{ child.text }}</span>
                    <a-menu-item v-for="g in child.children" :key="String(g.id)">{{ g.text }}</a-menu-item>
                  </a-sub-menu>
                  <a-menu-item v-else :key="String(child.id)">{{ child.text }}</a-menu-item>
                </template>
              </a-sub-menu>
              <a-menu-item v-else :key="String(menu.id)">{{ menu.text }}</a-menu-item>
            </template>
          </a-menu>
          <div v-else style="color:#bbb;">加载菜单中...</div>
          <template v-if="popupScenes.length">
            <div style="margin:8px 0 4px;padding:8px 16px 0;border-top:1px solid #f0f0f0;color:#999;font-size:12px;">弹窗 / 明细场景</div>
            <a-menu
              mode="inline"
              :selectedKeys="[activeKey]"
              @click="onPopupClick">
              <a-menu-item v-for="s in popupScenes" :key="'scene_' + s.key">{{ s.name }}</a-menu-item>
            </a-menu>
          </template>
        </a-card>
      </a-col>

      <!-- 右侧：顶部表格预览 + 底部配置详情 -->
      <a-col :span="18">
        <a-card size="small" class="preview-card" style="margin-bottom:16px;">
          <template slot="title">
            <span v-if="currentScene">{{ currentScene.name }}（{{ currentScene.remark }}）</span>
            <span v-else>表格预览</span>
          </template>
          <data-table
            v-if="currentScene"
            :extra-columns="previewColumns"
            :selected-columns.sync="form.config.columns"
            :default-columns="previewColumnKeys"
            :data-source="previewData"
            :editable="false"
            :show-column-setting="true" />
          <div v-else style="color:#999;text-align:center;padding:24px 0;">请从左侧菜单选择要配置的表格</div>
        </a-card>

        <a-card title="配置详情" size="small" class="config-card">
          <div v-if="currentScene">
            <!-- 数据表管理 -->
            <div class="section-title">数据表</div>
            <div class="table-select-block">
              <div class="table-select-tags">
                <a-tag
                  v-for="t in selectedTableItems"
                  :key="t.table"
                  :color="t.table === activeTable ? 'blue' : ''"
                  closable
                  @close="removeTable(t.table)"
                  @click="setActiveTable(t.table)">
                  {{ t.name }}（{{ t.table }}）
                </a-tag>
                <a-select
                  v-if="selectableTables.length"
                  :value="undefined"
                  size="small"
                  placeholder="添加数据表"
                  style="width:200px;"
                  show-search
                  option-filter-prop="children"
                  @change="addTable">
                  <a-select-option v-for="t in selectableTables" :key="t.table" :value="t.table">
                    {{ t.name }}（{{ t.table }}）
                  </a-select-option>
                </a-select>
                <a-button size="small" icon="plus" @click="openCreateTable">新建数据表</a-button>
              </div>
            </div>

            <!-- 字段 / 表格列（合并为一表） -->
            <div class="field-config-list" style="margin-bottom:12px;">
              <div class="field-config-row field-config-header">
                <span class="col-check">显示</span>
                <span class="col-name">字段名</span>
                <span class="col-comment">字段注释</span>
                <span class="col-type">类型</span>
                <span class="col-title">表格标题</span>
                <span class="col-op">操作</span>
              </div>
              <div style="display:block;width:100%;">
                <div class="field-config-row" v-for="f in pagedFields" :key="f.kind + '_' + f.dataIndex">
                  <span class="col-check"><a-checkbox :checked="form.config.columns.indexOf(f.dataIndex) > -1" @change="e => toggleColumn(f.dataIndex, e.target.checked)" /></span>
                  <span class="col-name" :title="f.dataIndex">{{ f.dataIndex }}</span>
                  <span class="col-comment" :title="f.comment">{{ f.comment || '-' }}</span>
                  <span class="col-type">{{ f.dataType || '-' }}</span>
                  <span class="col-title">
                    <a-input size="small" :value="fieldTitle(f)" @change="e => setFieldTitle(f.dataIndex, e.target.value)" />
                  </span>
                  <span class="col-op">
                    <a v-if="f.kind === 'physical' && canDropField(f.rawColumnName)" @click="dropField(f)">删除</a>
                    <span v-else-if="f.kind === 'physical'" style="color:#ccc;">系统字段</span>
                    <a v-else-if="f.kind === 'custom'" @click="removeCustomColumn(f.dataIndex)">删除</a>
                    <span v-else style="color:#ccc;">-</span>
                  </span>
                </div>
              </div>
              <div v-if="!mergedFields.length" style="padding:12px;color:#999;">暂无字段</div>
            </div>

            <div class="field-config-pager">
              <a-pagination
                size="small"
                :current="fieldPage"
                :page-size="fieldPageSize"
                :total="mergedFields.length"
                show-size-changer
                :page-size-options="['10','20','30','50','100']"
                @change="onFieldPageChange"
                @showSizeChange="onFieldSizeChange" />
            </div>

            <div style="margin-bottom:16px;">
              <a-button size="small" icon="plus" @click="openAddField">添加字段</a-button>
              <a-button size="small" icon="plus" @click="openAddColumn" style="margin-left:8px;">添加列</a-button>
            </div>

            <div style="margin-top:16px;">
              <a-button type="primary" @click="save">保存</a-button>
              <a-button style="margin-left:8px;" @click="del">删除配置</a-button>
              <a-button style="margin-left:8px;" @click="resetSelection">清空选择</a-button>
            </div>
          </div>
          <div v-else style="color:#999;">请从左侧菜单选择要配置的表格</div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 新建数据表弹窗 -->
    <a-modal v-model="createTableVisible" title="新建数据表" :width="720" @ok="createTable" @cancel="createTableVisible = false">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="表名（英文字母/数字/下划线）" required>
              <a-input v-model="createTableForm.tableName" placeholder="如 jsh_custom_xxx" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="表注释">
              <a-input v-model="createTableForm.tableComment" placeholder="中文说明" />
            </a-form-item>
          </a-col>
        </a-row>
        <div class="section-title">初始字段</div>
        <div v-for="(c, idx) in createTableForm.columns" :key="idx" style="margin-bottom:8px;">
          <a-row :gutter="8">
            <a-col :span="8"><a-input v-model="c.columnName" placeholder="字段名" /></a-col>
            <a-col :span="8"><a-input v-model="c.columnComment" placeholder="字段注释" /></a-col>
            <a-col :span="6">
              <a-select v-model="c.dataType" style="width:100%;">
                <a-select-option v-for="t in dataTypeOptions" :key="t" :value="t">{{ t }}</a-select-option>
              </a-select>
            </a-col>
            <a-col :span="2"><a-icon type="delete" @click="removeCreateTableColumn(idx)" /></a-col>
          </a-row>
        </div>
        <a-button size="small" @click="createTableForm.columns.push({ columnName: '', columnComment: '', dataType: 'varchar' })">+ 添加字段</a-button>
      </a-form>
    </a-modal>

    <!-- 添加字段弹窗 -->
    <a-modal v-model="addFieldVisible" title="添加字段" :width="480" @ok="addField" @cancel="addFieldVisible = false">
      <a-form layout="vertical">
        <a-form-item label="字段名（英文字母/数字/下划线）" required>
          <a-input v-model="addFieldForm.columnName" placeholder="如 my_field" />
        </a-form-item>
        <a-form-item label="字段注释">
          <a-input v-model="addFieldForm.columnComment" placeholder="如 我的字段" />
        </a-form-item>
        <a-form-item label="数据类型">
          <a-select v-model="addFieldForm.dataType" style="width:100%;">
            <a-select-option v-for="t in dataTypeOptions" :key="t" :value="t">{{ t }}</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 添加列（公式列）弹窗 -->
    <a-modal v-model="addColumnVisible" title="添加列（公式列）" :width="520" @ok="addCustomColumn" @cancel="addColumnVisible = false">
      <a-form layout="vertical">
        <a-form-item label="列标识（字段名，英文）" required>
          <a-input v-model="addColumnForm.dataIndex" placeholder="如 totalAmount" />
        </a-form-item>
        <a-form-item label="列标题" required>
          <a-input v-model="addColumnForm.title" placeholder="如 合计金额" />
        </a-form-item>
        <a-form-item label="公式（引用字段，支持 + - * / 和括号）" required>
          <a-input v-model="addColumnForm.formula" placeholder="如 materialCount * totalPrice" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
  import Vue from 'vue'
  import { getAction, postAction, deleteAction } from '@/api/manage'
  import { queryPermissionsByUser } from '@/api/api'
  import { USER_ID } from '@/store/mutation-types'
  import { TABLE_SCENES, DB_TABLES, getSceneExtraFields } from '@/config/tableSceneConfig'
  import DataTable from '@/components/DataTable'

  export default {
    name: 'TableColumnConfig',
    components: { DataTable },
    data() {
      return {
        menus: [],
        menuMap: {},
        activeKey: '',
        currentScene: null,
        activeTable: '',
        allFields: {},
        fieldPageSize: 10,
        fieldPage: 1,
        dbTables: [],
        sceneOptions: TABLE_SCENES,
        dataTypeOptions: ['varchar', 'int', 'bigint', 'decimal', 'double', 'datetime', 'date', 'text'],
        previewData: [{ id: 'sample_1' }, { id: 'sample_2' }, { id: 'sample_3' }],
        form: {
          tableKey: '',
          tableNames: '',
          config: { columns: [], titles: {}, customColumns: [], widths: {} }
        },
        _savedColumns: null,
        _pendingTables: [],
        _defaultApplied: false,
        // 新建数据表弹窗
        createTableVisible: false,
        createTableForm: { tableName: '', tableComment: '', columns: [{ columnName: '', columnComment: '', dataType: 'varchar' }] },
        // 添加字段弹窗
        addFieldVisible: false,
        addFieldForm: { columnName: '', columnComment: '', dataType: 'varchar' },
        // 添加列弹窗
        addColumnVisible: false,
        addColumnForm: { dataIndex: '', title: '', formula: '' }
      }
    },
    computed: {
      topLevelKeys() {
        return this.menus.filter(m => m.children && m.children.length).map(m => String(m.id))
      },
      // 非菜单场景（弹窗/明细，如选择商品、单据明细），单独列在左侧
      popupScenes() {
        const menuTexts = {}
        const walk = (list) => {
          (list || []).forEach(item => {
            menuTexts[item.text] = true
            if (item.children && item.children.length) walk(item.children)
          })
        }
        walk(this.menus)
        return TABLE_SCENES.filter(s => !menuTexts[s.name])
      },
      // 当前选中的数据库表名列表
      selectedTables() {
        return (this.form.tableNames || '').split(',').map(s => s.trim()).filter(Boolean)
      },
      // 表名 -> 中文名（静态清单优先，动态清单兜底）
      tableNameMap() {
        const map = {}
        DB_TABLES.forEach(t => { map[t.table] = t.name })
        this.dbTables.forEach(t => { if (!map[t.table]) map[t.table] = t.comment || t.table })
        return map
      },
      // 尚未添加的数据表（供「添加数据表」下拉选择）
      selectableTables() {
        const selected = this.selectedTables
        const all = DB_TABLES.map(t => t.table).concat(this.dbTables.map(t => t.table))
        const unique = []
        all.forEach(t => { if (unique.indexOf(t) === -1) unique.push(t) })
        return unique.filter(t => selected.indexOf(t) === -1).map(t => ({ table: t, name: this.tableNameMap[t] || t }))
      },
      // 当前选中数据表对应的元数据（含中文名），用于标签展示
      selectedTableItems() {
        return this.selectedTables.map(t => ({ table: t, name: this.tableNameMap[t] || t }))
      },
      // 展示列：业务别名字段 + 物理字段（physical 场景）或仅物理字段（无业务字段的基础场景）
      displayFields() {
        if (!this.currentScene) return []
        const list = []
        const extra = getSceneExtraFields(this.form.tableKey)
        const includePhysical = this.currentScene.physical === true
        if (includePhysical || extra.length === 0) {
          this.selectedTables.forEach(t => {
            (this.allFields[t] || []).forEach(f => {
              if (!list.some(x => x.dataIndex === f.columnName)) {
                list.push({
                  dataIndex: f.columnName,
                  rawColumnName: f.rawColumnName || f.columnName,
                  title: f.columnComment || f.columnName,
                  comment: f.columnComment || '',
                  dataType: f.dataType || '',
                  table: t,
                  kind: 'physical'
                })
              }
            })
          })
        }
        extra.forEach(f => {
          if (!list.some(x => x.dataIndex === f.dataIndex)) {
            list.push({ dataIndex: f.dataIndex, title: f.title, comment: f.title, dataType: '', table: '', kind: 'extra' })
          }
        })
        return list
      },
      // 合并字段 + 公式列，供配置详情统一表格展示（物理字段按当前激活表过滤，标签切换即切换显示）
      mergedFields() {
        if (!this.currentScene) return []
        const list = []
        const extra = getSceneExtraFields(this.form.tableKey)
        // 始终展示当前激活表的物理字段，实现按表分开显示
        if (this.activeTable) {
          (this.allFields[this.activeTable] || []).forEach(f => {
            list.push({
              dataIndex: f.columnName,
              rawColumnName: f.rawColumnName || f.columnName,
              title: f.columnComment || f.columnName,
              comment: f.columnComment || '',
              dataType: f.dataType || '',
              table: this.activeTable,
              kind: 'physical'
            })
          })
        }
        extra.forEach(f => {
          if (!list.some(x => x.dataIndex === f.dataIndex)) {
            list.push({ dataIndex: f.dataIndex, title: f.title, comment: f.title, dataType: '', table: '', kind: 'extra' })
          }
        })
        ;(this.form.config.customColumns || []).forEach(c => {
          if (!list.some(x => x.dataIndex === c.dataIndex)) {
            list.push({ dataIndex: c.dataIndex, title: c.title || c.dataIndex, comment: c.formula || '', dataType: '', table: '', kind: 'custom' })
          }
        })
        return list
      },
      displayFieldKeys() {
        return this.displayFields.map(f => f.dataIndex)
      },
      // 字段列表分页（每页行数可设置）
      pagedFields() {
        const start = (this.fieldPage - 1) * this.fieldPageSize
        return this.mergedFields.slice(start, start + this.fieldPageSize)
      },
      // 预览列：展示列 + 公式列（带 formula 标记，供 DataTable 逐行计算）
      previewColumns() {
        const list = this.displayFields.slice()
        ;(this.form.config.customColumns || []).forEach(c => {
          if (!list.some(x => x.dataIndex === c.dataIndex)) {
            list.push({ dataIndex: c.dataIndex, title: c.title || c.dataIndex, comment: c.formula || '', formula: c.formula, __customColumn: true })
          }
        })
        return list
      },
      previewColumnKeys() {
        return this.previewColumns.map(c => c.dataIndex)
      }
    },
    created() {
      const list = this.$store.getters.permissionList
      if (list && list.length) {
        this.menus = list
        this.buildMenuMap()
      } else {
        this.loadMenus()
      }
      this.loadDbTables()
    },
    methods: {
      loadMenus() {
        queryPermissionsByUser({ pNumber: 0, userId: Vue.ls.get(USER_ID) }).then(data => {
          if (data && data.length) {
            this.menus = data
            this.buildMenuMap()
          }
        })
      },
      buildMenuMap() {
        const map = {}
        const walk = (list) => {
          (list || []).forEach(item => {
            map[String(item.id)] = item
            if (item.children && item.children.length) walk(item.children)
          })
        }
        walk(this.menus)
        this.menuMap = map
      },
      loadDbTables() {
        getAction('/tableMeta/listTables', {}).then(res => {
          if (res && res.code === 200 && res.data) {
            this.dbTables = res.data
          }
        }).catch(() => {})
      },
      onMenuClick({ key }) {
        const item = this.menuMap[String(key)]
        if (!item) return
        this.activeKey = String(key)
        const scene = TABLE_SCENES.find(s => s.name === item.text)
        if (scene) {
          this.selectScene(scene)
        } else {
          this.currentScene = null
          this.activeTable = ''
          this.form = { tableKey: '', tableNames: '', config: { columns: [], titles: {}, customColumns: [], widths: {} } }
          this.allFields = {}
          this.$message.info('该菜单暂未配置列显示')
        }
      },
      onPopupClick({ key }) {
        const sceneKey = key.replace(/^scene_/, '')
        const scene = TABLE_SCENES.find(s => s.key === sceneKey)
        if (scene) {
          this.activeKey = key
          this.selectScene(scene)
        }
      },
      onSceneSelect(key) {
        if (!key) return
        const scene = TABLE_SCENES.find(s => s.key === key)
        if (scene) {
          this.activeKey = 'scene_' + scene.key
          this.selectScene(scene)
        }
      },
      selectScene(scene) {
        this.currentScene = scene
        this.form = {
          tableKey: scene.key,
          tableNames: scene.tables.join(','),
          config: { columns: [], titles: {}, customColumns: [], widths: {} }
        }
        this.allFields = {}
        this.activeTable = scene.tables[0] || ''
        this.fieldPage = 1
        this._savedColumns = null
        this._defaultApplied = false
        this._pendingTables = []
        this.loadSceneFields(scene.tables)
        this.loadSavedConfig(scene.key)
      },
      setActiveTable(table) {
        if (table) {
          this.activeTable = table
          this.fieldPage = 1
        }
      },
      onFieldPageChange(page) {
        this.fieldPage = page
      },
      onFieldSizeChange(current, size) {
        this.fieldPageSize = size
        this.fieldPage = 1
      },
      // 加载数据表字段（所有场景均加载物理字段，供「数据表」DDL 管理）
      loadSceneFields(tables) {
        (tables || []).forEach(t => this.loadFieldsByTable(t))
      },
      loadSavedConfig(tableKey) {
        getAction('/tableColumnConfig/' + tableKey, {}).then(res => {
          let cfg = null
          if (res && res.code === 200 && res.data) {
            if (res.data.tableNames) {
              this.form.tableNames = res.data.tableNames
              this.loadSceneFields(this.selectedTables)
            }
            if (res.data.config) {
              cfg = res.data.config
            }
          }
          if (cfg && cfg.columns && cfg.columns.length > 0) {
            this._savedColumns = cfg.columns.slice()
            this.form.config.columns = cfg.columns.slice()
          } else {
            this._savedColumns = []
          }
          this.form.config.titles = (cfg && cfg.titles) ? Object.assign({}, cfg.titles) : {}
          this.form.config.customColumns = (cfg && cfg.customColumns) ? cfg.customColumns.slice() : []
          this.form.config.widths = (cfg && cfg.widths) ? Object.assign({}, cfg.widths) : {}
          this.tryApplyDefaultColumns()
        }).catch(() => {
          this._savedColumns = []
          this.form.config.titles = {}
          this.form.config.customColumns = []
          this.form.config.widths = {}
          this.tryApplyDefaultColumns()
        })
      },
      loadFieldsByTable(table) {
        if (!table || this.allFields[table]) return
        if (this._pendingTables.indexOf(table) === -1) {
          this._pendingTables.push(table)
        }
        getAction('/tableMeta/' + table, {}).then(res => {
          if (res && res.code === 200 && res.data) {
            this.$set(this.allFields, table, res.data)
          }
        }).catch(() => {
        }).then(() => {
          this._pendingTables = this._pendingTables.filter(t => t !== table)
          this.tryApplyDefaultColumns()
        })
      },
      tryApplyDefaultColumns() {
        if (this._defaultApplied) return
        if (this._savedColumns === null) return
        // 业务场景用业务列（同步），无需等待数据库字段；基础场景/物理字段场景需等待数据库字段加载完成
        const hasExtra = getSceneExtraFields(this.form.tableKey).length > 0
        const needPhysical = !hasExtra || (this.currentScene && this.currentScene.physical === true)
        if (needPhysical && this._pendingTables.length > 0) return
        // 无已保存配置时，默认全部展示字段为显示状态
        if (this._savedColumns.length === 0) {
          this.form.config.columns = this.displayFieldKeys.slice()
        }
        this._defaultApplied = true
      },
      addTable(table) {
        if (!table) return
        const list = this.selectedTables
        if (list.indexOf(table) > -1) return
        this.form.tableNames = list.concat([table]).join(',')
        this.activeTable = table
        this.loadSceneFields([table])
      },
      removeTable(table) {
        const removedKeys = (this.allFields[table] || []).map(f => f.columnName)
        this.form.tableNames = this.selectedTables.filter(t => t !== table).join(',')
        this.$delete(this.allFields, table)
        if (removedKeys.length) {
          this.form.config.columns = this.form.config.columns.filter(k => removedKeys.indexOf(k) === -1)
        }
        if (this.activeTable === table) {
          this.activeTable = this.selectedTables[0] || ''
        }
      },
      // 新建真实数据表
      openCreateTable() {
        this.createTableForm = { tableName: '', tableComment: '', columns: [{ columnName: '', columnComment: '', dataType: 'varchar' }] }
        this.createTableVisible = true
      },
      removeCreateTableColumn(idx) {
        this.createTableForm.columns.splice(idx, 1)
      },
      createTable() {
        const { tableName, tableComment, columns } = this.createTableForm
        if (!tableName || !/^[A-Za-z0-9_]+$/.test(tableName.trim())) {
          this.$message.warning('表名只能包含字母、数字、下划线')
          return
        }
        const cols = (columns || []).filter(c => c.columnName && c.columnName.trim())
        postAction('/tableMeta/createTable', { tableName: tableName.trim(), tableComment, columns: cols }).then(res => {
          if (res && res.code === 200) {
            this.$message.success('创建成功')
            this.createTableVisible = false
            this.loadDbTables()
            const list = this.selectedTables
            if (list.indexOf(tableName.trim()) === -1) {
              this.form.tableNames = list.concat([tableName.trim()]).join(',')
            }
            this.activeTable = tableName.trim()
            this.$delete(this.allFields, tableName.trim())
            this.loadFieldsByTable(tableName.trim())
          } else {
            this.$message.error((res && res.data) || '创建失败')
          }
        })
      },
      // 给当前激活表添加字段
      openAddField() {
        if (!this.activeTable) {
          this.$message.warning('请先选择数据表')
          return
        }
        this.addFieldForm = { columnName: '', columnComment: '', dataType: 'varchar' }
        this.addFieldVisible = true
      },
      addField() {
        const { columnName, columnComment, dataType } = this.addFieldForm
        if (!columnName || !/^[A-Za-z0-9_]+$/.test(columnName.trim())) {
          this.$message.warning('字段名只能包含字母、数字、下划线')
          return
        }
        postAction('/tableMeta/addColumn', { tableName: this.activeTable, columnName: columnName.trim(), columnComment, dataType }).then(res => {
          if (res && res.code === 200) {
            this.$message.success('添加成功')
            this.addFieldVisible = false
            this.addFieldForm = { columnName: '', columnComment: '', dataType: 'varchar' }
            this.$delete(this.allFields, this.activeTable)
            this.loadFieldsByTable(this.activeTable)
          } else {
            this.$message.error((res && res.data) || '添加失败')
          }
        })
      },
      canDropField(rawColumnName) {
        return ['id', 'delete_flag', 'tenant_id'].indexOf(rawColumnName) === -1
      },
      dropField(f) {
        const table = f.table || this.activeTable
        this.$confirm({
          title: `确认删除字段 ${f.dataIndex}？`,
          content: '将执行 ALTER TABLE 删除该列，且不可恢复。',
          onOk: () => {
            postAction('/tableMeta/dropColumn', { tableName: table, columnName: f.rawColumnName }).then(res => {
              if (res && res.code === 200) {
                this.$message.success('删除成功')
                this.form.config.columns = this.form.config.columns.filter(k => k !== f.dataIndex)
                this.$delete(this.form.config.titles, f.dataIndex)
                this.$delete(this.allFields, table)
                this.loadFieldsByTable(table)
              } else {
                this.$message.error((res && res.data) || '删除失败')
              }
            })
          }
        })
      },
      // 添加公式列
      openAddColumn() {
        this.addColumnForm = { dataIndex: '', title: '', formula: '' }
        this.addColumnVisible = true
      },
      addCustomColumn() {
        const { dataIndex, title, formula } = this.addColumnForm
        if (!dataIndex || !/^[A-Za-z_][A-Za-z0-9_]*$/.test(dataIndex)) {
          this.$message.warning('列标识需以字母/下划线开头，仅含字母数字下划线')
          return
        }
        if (!title) {
          this.$message.warning('请输入列标题')
          return
        }
        if (!formula || !/^[A-Za-z0-9_+\-*/().\s]+$/.test(formula)) {
          this.$message.warning('公式仅支持字段、数字与 + - * / ( )')
          return
        }
        const list = (this.form.config.customColumns || []).slice()
        if (list.some(c => c.dataIndex === dataIndex)) {
          this.$message.warning('列标识已存在')
          return
        }
        list.push({ dataIndex, title, formula })
        this.form.config.customColumns = list
        if (this.form.config.columns.indexOf(dataIndex) === -1) {
          this.form.config.columns.push(dataIndex)
        }
        this.addColumnVisible = false
        this.addColumnForm = { dataIndex: '', title: '', formula: '' }
      },
      removeCustomColumn(dataIndex) {
        this.form.config.customColumns = (this.form.config.customColumns || []).filter(c => c.dataIndex !== dataIndex)
        this.form.config.columns = this.form.config.columns.filter(k => k !== dataIndex)
        this.$delete(this.form.config.titles, dataIndex)
      },
      fieldTitle(f) {
        const t = this.form.config.titles[f.dataIndex]
        return (t === undefined || t === null || t === '') ? (f.title || f.dataIndex) : t
      },
      setFieldTitle(dataIndex, value) {
        this.$set(this.form.config.titles, dataIndex, value)
      },
      toggleColumn(dataIndex, checked) {
        if (checked) {
          if (this.form.config.columns.indexOf(dataIndex) === -1) {
            this.form.config.columns.push(dataIndex)
          }
        } else {
          this.form.config.columns = this.form.config.columns.filter(k => k !== dataIndex)
        }
      },
      save() {
        if (!this.currentScene) {
          this.$message.warning('请先选择左侧菜单对应表格')
          return
        }
        this.form.tableKey = this.currentScene.key
        const validKeys = this.previewColumnKeys
        // 仅保存当前有效列（业务字段 + 物理字段 + 公式列），避免残留已删除字段
        this.form.config.columns = this.form.config.columns.filter(k => validKeys.indexOf(k) > -1)
        // 清理空标题
        const titles = {}
        Object.keys(this.form.config.titles || {}).forEach(k => {
          const v = this.form.config.titles[k]
          if (v !== undefined && v !== null && String(v).trim() !== '') {
            titles[k] = v
          }
        })
        this.form.config.titles = titles
        postAction('/tableColumnConfig/save', this.form).then(res => {
          if (res && res.code === 200) {
            this.$message.success('保存成功')
          } else {
            this.$message.error((res && res.data) ? res.data : '保存失败')
          }
        })
      },
      del() {
        if (!this.currentScene) {
          this.$message.warning('请先选择要删除的场景')
          return
        }
        this.$confirm({
          title: '确认删除该场景配置？',
          onOk: () => {
            deleteAction('/tableColumnConfig/' + this.currentScene.key, {}).then(res => {
              if (res && res.code === 200) {
                this.$message.success('删除成功')
                this.form.config = { columns: [], titles: {}, customColumns: [], widths: {} }
              } else {
                this.$message.error('删除失败')
              }
            })
          }
        })
      },
      resetSelection() {
        this.form.config.columns = []
      }
    }
  }
</script>

<style scoped>
  /* 左侧菜单卡片：限制高度、滚动 */
  .menu-card .ant-card-body {
    max-height: calc(100vh - 220px);
    overflow-y: auto;
    overflow-x: hidden;
  }
  /* 右侧预览卡片：限制高度、滚动 */
  .preview-card .ant-card-body {
    max-height: 300px;
    overflow-y: auto;
    overflow-x: auto;
  }
  /* 右侧配置详情卡片：限制高度、滚动 */
  .config-card .ant-card-body {
    max-height: calc(100vh - 360px);
    overflow-y: auto;
    overflow-x: hidden;
  }
  /* 统一自定义滚动条样式 */
  .menu-card .ant-card-body::-webkit-scrollbar,
  .preview-card .ant-card-body::-webkit-scrollbar,
  .config-card .ant-card-body::-webkit-scrollbar {
    width: 8px;
  }
  .menu-card .ant-card-body::-webkit-scrollbar-thumb,
  .preview-card .ant-card-body::-webkit-scrollbar-thumb,
  .config-card .ant-card-body::-webkit-scrollbar-thumb {
    background: #bfbfbf;
    border-radius: 4px;
  }
  .menu-card .ant-card-body::-webkit-scrollbar-thumb:hover,
  .preview-card .ant-card-body::-webkit-scrollbar-thumb:hover,
  .config-card .ant-card-body::-webkit-scrollbar-thumb:hover {
    background: #8c8c8c;
  }
  .menu-card .ant-card-body::-webkit-scrollbar-track,
  .preview-card .ant-card-body::-webkit-scrollbar-track,
  .config-card .ant-card-body::-webkit-scrollbar-track {
    background: #f0f0f0;
    border-radius: 4px;
  }
  .section-title {
    font-weight: 500;
    color: #333;
    margin: 4px 0 8px;
  }
  .table-select-block {
    display: flex;
    align-items: flex-start;
    margin-bottom: 12px;
  }
  .table-select-tags {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px;
  }
  .table-select-tags .ant-tag {
    cursor: pointer;
  }
  .field-config-list {
    border: 1px solid #f0f0f0;
    border-radius: 4px;
  }
  .field-config-pager {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 12px;
  }
  .field-config-row {
    display: flex;
    align-items: center;
    padding: 6px 8px;
    border-bottom: 1px solid #f5f5f5;
  }
  .field-config-header {
    background: #fafafa;
    font-weight: 500;
    color: #666;
    position: sticky;
    top: 0;
    z-index: 1;
  }
  .col-check {
    width: 60px;
    flex-shrink: 0;
  }
  .col-name {
    width: 160px;
    flex-shrink: 0;
    font-family: Consolas, Monaco, monospace;
    color: #333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .col-comment {
    flex: 1;
    min-width: 100px;
    color: #888;
    padding: 0 12px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .col-type {
    width: 90px;
    flex-shrink: 0;
    color: #666;
  }
  .col-title {
    width: 180px;
    flex-shrink: 0;
  }
  .col-op {
    width: 70px;
    flex-shrink: 0;
    text-align: right;
    color: #1890ff;
  }
</style>
