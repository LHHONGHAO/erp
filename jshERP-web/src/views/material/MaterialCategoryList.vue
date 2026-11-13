<!-- by 7527_18_920 -->
<template>
  <div class="mcl-root">
    <!-- 手机端：标准手机页面布局（全局蓝底顶栏 + 可滚动内容 + 底部操作栏） -->
    <mobile-page v-if="device === 'mobile'" class="mcl-page" :standalone="false" body-padding="0 0 calc(76px + env(safe-area-inset-bottom))">
      <div class="mcl-body">
        <!-- 当前选择 -->
        <div class="mcl-card">
          <a-alert type="info" :showIcon="true">
            <div slot="message">
              <span v-if="currSelected.title">当前选择：{{ getCurrSelectedTitle() }}</span>
              <span v-else class="mcl-muted">未选择类别</span>
              <a v-if="currSelected.title" class="mcl-clear" @click="onClearSelected">取消选择</a>
            </div>
          </a-alert>
        </div>

        <!-- 分类目录树 -->
        <div class="mcl-card">
          <div class="mcl-card-title">分类目录</div>
          <a-tree
            class="mcl-tree"
            checkable
            multiple
            @select="onSelect"
            @check="onCheck"
            @rightClick="rightHandle"
            :selectedKeys="selectedKeys"
            :checkedKeys="checkedKeys"
            :treeData="categoryTree"
            :checkStrictly="checkStrictly"
            :expandedKeys="iExpandedKeys"
            :autoExpandParent="true"
            @expand="onExpand" />
        </div>

        <!-- 树操作 -->
        <div class="mcl-card mcl-tree-ops">
          <a-dropdown :trigger="['click']" placement="topCenter">
            <a-menu slot="overlay">
              <a-menu-item key="1" @click="switchCheckStrictly(1)">父子关联</a-menu-item>
              <a-menu-item key="2" @click="switchCheckStrictly(2)">取消关联</a-menu-item>
              <a-menu-item key="3" @click="checkALL">全部勾选</a-menu-item>
              <a-menu-item key="4" @click="cancelCheckALL">取消全选</a-menu-item>
              <a-menu-item key="5" @click="expandAll">展开所有</a-menu-item>
              <a-menu-item key="6" @click="closeAll">合并所有</a-menu-item>
            </a-menu>
            <a-button block class="mcl-tree-ops-btn">
              <span>树操作</span>
              <a-icon type="down" />
            </a-button>
          </a-dropdown>
        </div>

        <!-- 类别信息 -->
        <div v-if="selectedKeys.length > 0" class="mcl-card">
          <div class="mcl-card-title">类别信息</div>
          <a-form :form="form" class="mcl-form">
            <a-form-item label="名称">
              <a-input placeholder="请输入名称" v-decorator="['name', validatorRules.name ]"/>
            </a-form-item>
            <a-form-item label="编号">
              <a-input placeholder="请输入编号" v-decorator="['serialNo', validatorRules.serialNo ]"/>
            </a-form-item>
            <a-form-item label="上级目录">
              <a-tree-select style="width:100%" :dropdownStyle="{maxHeight:'200px',overflow:'auto'}"
                             allow-clear :treeDefaultExpandAll="true"
                             :treeData="treeData" v-decorator="[ 'parentId' ]" placeholder="请选择上级目录"
                             @change="onParentChange">
              </a-tree-select>
            </a-form-item>
            <!-- 单位：仅子类目维护，一级类目无单位属性 -->
            <a-form-item label="单位" v-if="!isTopLevelCategory">
              <a-input placeholder="请输入默认单位（商品录入时自动带出）" v-decorator="['unit']"/>
            </a-form-item>
            <a-form-item label="排序">
              <a-input v-decorator="[ 'sort' ]"/>
            </a-form-item>
            <a-form-item label="备注">
              <a-textarea placeholder="请输入备注" :rows="2" v-decorator.trim="[ 'remark' ]" />
            </a-form-item>
          </a-form>
        </div>
        <div v-else class="mcl-card mcl-empty">请先在上方选择一个类别</div>

        <!-- 类别规格设置：仅二级及以下类目维护，一级类目无规格值 -->
        <div v-if="selectedKeys.length > 0 && hasSelectedCategory && !isTopLevelCategory" class="mcl-card">
          <div class="mcl-card-title">类别规格设置</div>
          <div v-for="(module, mIndex) in specModules" :key="mIndex" class="mcl-spec-card">
            <div class="mcl-spec-head">
              <a-input v-model="module.name" class="mcl-spec-name" placeholder="规格大类（商品规格卡片标题）" />
              <a-icon type="delete" class="mcl-spec-del" @click="removeSpecModule(mIndex)" />
            </div>
            <div class="mcl-spec-switch">
              <a-switch size="small" v-model="module.enableImage" />
              <span>可添加规格图片</span>
            </div>
            <div v-for="(child, childIndex) in module.childNames" :key="childIndex" class="mcl-spec-value">
              <a-input
                :value="module.childNames[childIndex]"
                placeholder="请输入子规格名称"
                @change="onMobileChildSpecChange(mIndex, childIndex, $event)" />
              <a-icon type="delete" class="mcl-spec-del" @click="removeChildSpec(mIndex, childIndex)" />
            </div>
            <a-button type="dashed" size="small" icon="plus" class="mcl-spec-add-child" @click="addChildSpec(mIndex)">添加子规格</a-button>
          </div>
          <a-button type="dashed" icon="plus" block @click="addSpecModule">添加规格</a-button>
          <div v-if="specModules.length === 0" class="mcl-muted mcl-spec-tip">规格值在商品信息页面维护</div>
        </div>
      </div>

      <template slot="footer">
        <div class="mcl-footer">
          <a-button @click="emptyCurrForm">重置</a-button>
          <a-button type="primary" :disabled="selectedKeys.length === 0" @click="submitCurrForm">保存</a-button>
        </div>
      </template>
    </mobile-page>

    <!-- 桌面端：原有左右分栏布局 -->
    <a-row v-else :gutter="10">
    <a-col :md="12" :sm="24">
      <a-card :bordered="false">
        <!-- 按钮操作区域；手机端由全局蓝底顶栏承载（删除/添加），批量删除仅桌面端 -->
        <a-row style="margin-left: 14px" v-if="device !== 'mobile'">
          <a-button v-if="btnEnableList.indexOf(1)>-1" @click="handleAdd()" type="primary">添加类别</a-button>
          <a-button v-if="btnEnableList.indexOf(1)>-1" title="删除多条数据" @click="batchDel" type="default">批量删除</a-button>
          <a-button @click="refresh" type="default" icon="reload">刷新</a-button>
        </a-row>
        <div style="background: #fff;padding-left:16px;height: 100%; margin-top: 5px">
          <a-alert type="info" :showIcon="true">
            <div slot="message">
              当前选择：<span v-if="this.currSelected.title">{{ getCurrSelectedTitle() }}</span>
              <a v-if="this.currSelected.title" style="margin-left: 10px" @click="onClearSelected">取消选择</a>
            </div>
          </a-alert>
          <!-- 树-->
          <a-col :md="10" :sm="24">
            <template>
              <a-dropdown :trigger="[this.dropTrigger]" @visibleChange="dropStatus">
               <span style="user-select: none">
                <a-tree
                  checkable
                  multiple
                  @select="onSelect"
                  @check="onCheck"
                  @rightClick="rightHandle"
                  :selectedKeys="selectedKeys"
                  :checkedKeys="checkedKeys"
                  :treeData="categoryTree"
                  :checkStrictly="checkStrictly"
                  :expandedKeys="iExpandedKeys"
                  :autoExpandParent="true"
                  @expand="onExpand"/>
                </span>
              </a-dropdown>
            </template>
          </a-col>
        </div>
      </a-card>
      <!---- author:os_chengtgen -- date:20190827 --  for:切换父子勾选模式 =======------>
      <div class="drawer-bootom-button">
        <a-dropdown :trigger="['click']" placement="topCenter">
          <a-menu slot="overlay">
            <a-menu-item key="1" @click="switchCheckStrictly(1)">父子关联</a-menu-item>
            <a-menu-item key="2" @click="switchCheckStrictly(2)">取消关联</a-menu-item>
            <a-menu-item key="3" @click="checkALL">全部勾选</a-menu-item>
            <a-menu-item key="4" @click="cancelCheckALL">取消全选</a-menu-item>
            <a-menu-item key="5" @click="expandAll">展开所有</a-menu-item>
            <a-menu-item key="6" @click="closeAll">合并所有</a-menu-item>
          </a-menu>
          <a-button>
            树操作 <a-icon type="up" />
          </a-button>
        </a-dropdown>
      </div>
      <!---- author:os_chengtgen -- date:20190827 --  for:切换父子勾选模式 =======------>
    </a-col>
    <a-col :md="12" :sm="24">
      <a-card :bordered="false" v-if="selectedKeys.length>0">
        <a-form :form="form">
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="名称">
            <a-input placeholder="请输入名称" v-decorator="['name', validatorRules.name ]"/>
          </a-form-item>
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="编号">
            <a-input placeholder="请输入编号" v-decorator="['serialNo', validatorRules.serialNo ]"/>
          </a-form-item>
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="上级目录">
            <a-tree-select style="width:100%" :dropdownStyle="{maxHeight:'200px',overflow:'auto'}"
                           allow-clear :treeDefaultExpandAll="true"
                           :treeData="treeData" v-decorator="[ 'parentId' ]" placeholder="请选择上级目录"
                           @change="onParentChange">
            </a-tree-select>
          </a-form-item>
          <!-- 单位：仅子类目维护，一级类目无单位属性 -->
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="单位" v-if="!isTopLevelCategory">
            <a-input placeholder="请输入默认单位（商品录入时自动带出）" v-decorator="[ 'unit' ]"/>
          </a-form-item>
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="排序">
            <a-input v-decorator="[ 'sort' ]"/>
          </a-form-item>
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="备注">
            <a-textarea placeholder="请输入备注":rows="2" v-decorator.trim="[ 'remark' ]" />
          </a-form-item>
          <!-- 当前类别规格配置：仅二级及以下类目维护，一级类目无规格值 -->
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="类别规格设置" v-if="hasSelectedCategory && !isTopLevelCategory">
            <div v-for="(module, mIndex) in specModules" :key="mIndex" style="border:1px solid #eee;border-radius:4px;padding:10px;margin-top:10px;">
              <div style="display:flex;align-items:center;gap:8px;">
                <a-input v-model="module.name" style="width:220px" placeholder="请输入规格大类（即商品规格卡片标题）" />
                <a-switch size="small" v-model="module.enableImage" />
                <span>可添加规格图片</span>
                <a-button type="link" size="small" @click="removeSpecModule(mIndex)"><a-icon type="delete" /></a-button>
              </div>
              <div style="margin:8px 0 0 4px;">
                <div v-for="(child, childIndex) in module.childNames" :key="childIndex" style="display:flex;align-items:center;margin-bottom:6px;">
                  <a-input v-model="module.childNames[childIndex]" style="width:220px" placeholder="请输入子规格名称" />
                  <a-button type="link" class="child-spec-delete" @click="removeChildSpec(mIndex, childIndex)"><a-icon type="delete" /></a-button>
                </div>
                <a-button type="dashed" class="add-child-spec-btn" icon="plus" @click="addChildSpec(mIndex)">添加子规格</a-button>
              </div>
            </div>
            <div style="margin-top:8px;">
              <a-button type="dashed" icon="plus" @click="addSpecModule">添加规格</a-button>
              <span v-if="specModules.length===0" style="color:#999;font-size:12px;margin-left:8px;">规格值在商品信息页面维护</span>
            </div>
          </a-form-item>
        </a-form>
        <div class="anty-form-btn">
          <a-button @click="emptyCurrForm" type="default" htmlType="button" icon="sync">重置</a-button>
          <a-button @click="submitCurrForm" type="primary" htmlType="button" icon="form">保存</a-button>
        </div>
      </a-card>
      <a-card v-else >
        <a-empty>
          <span slot="description"> 请先选择一个类别! </span>
        </a-empty>
      </a-card>
    </a-col>
  </a-row>

    <material-category-modal ref="materialCategoryModal" @ok="loadTree"></material-category-modal>
  </div>
</template>
<script>
import MaterialCategoryModal from '../material/modules/MaterialCategoryModal'
import pick from 'lodash.pick'
import {queryMaterialCategoryTreeList,queryMaterialCategoryById,checkMaterialCategory} from '@/api/api'
import {httpAction, deleteAction} from '@/api/manage'
import {JeecgListMixin} from '@/mixins/JeecgListMixin'
import {mixinDevice} from '@/utils/mixin.js'
import {setMobileHeaderActions, clearMobileHeaderActions} from '@/utils/mobilePageHeader'
import MobilePage from '@/components/page/MobilePage'
export default {
  name: 'MaterialCategoryList',
  mixins: [JeecgListMixin, mixinDevice],
  components: {
    MaterialCategoryModal,
    MobilePage
  },
  data() {
    return {
      iExpandedKeys: [],
      loading: false,
      currFlowId: '',
      currFlowName: '',
      disable: true,
      treeData: [],
      visible: false,
      categoryTree: [],
      rightClickSelectedKey: '',
      rightClickSelectedOrgCode: '',
      hiding: true,
      model: {},
      dropTrigger: '',
      category: {},
      disableSubmit: false,
      checkedKeys: [],
      selectedKeys: [],
      autoIncr: 1,
      currSelected: {},
      allTreeKeys:[],
      checkStrictly: true,
      form: this.$form.createForm(this),
      urlPath: '/material/material_category',
      labelCol: {
        xs: {span: 24},
        sm: {span: 5}
      },
      wrapperCol: {
        xs: {span: 24},
        sm: {span: 16}
      },
      graphDatasource: {
        nodes: [],
        edges: []
      },
      validatorRules:{
        name: {
          rules: [
            {required: true, message: '请输入名称!'},
            { validator: this.validateName}
          ]
        },
        serialNo: {rules: [{required: true, message: '请输入编号!'}]}
      },
      url: {
        delete: '/materialCategory/delete',
        edit: '/materialCategory/update',
        deleteBatch: '/materialCategory/deleteBatch'
      },
      orgCategoryDisabled:false,
      specModules: [],
      treeLoadSeq: 0,
      // 表单里当前选择的上级目录，用于判断是否为一级类目
      formParentId: null,
    }
  },
  computed: {
    importExcelUrl: function () {
      return `${window._CONFIG['domianURL']}/${this.url.importExcelUrl}`;
    },
    hasSelectedCategory() {
      return this.currSelected && this.currSelected.id != null
    },
    // 一级类目：无上级目录，不维护单位，也不配置规格值
    isTopLevelCategory() {
      if (!this.hasSelectedCategory) return false
      const parentId = this.formParentId
      return parentId === null || parentId === undefined || parentId === ''
    }
  },
  mounted() {
    this.registerHeaderActions()
  },
  // keep-alive 激活时重新注册手机端顶部按钮
  activated() {
    this.registerHeaderActions()
    // 手机端激活时刷新列表
    if (this.isMobile()) {
      this.loadData()
    }
  },
  deactivated() {
    clearMobileHeaderActions()
  },
  beforeDestroy() {
    clearMobileHeaderActions()
  },
  methods: {
    // 注册手机端顶部按钮：删除当前类别、添加类别（需有按钮权限）
    registerHeaderActions() {
      const actions = []
      actions.push({ key: 'delete', icon: 'delete', handler: this.handleDeleteSelected })
      if (this.btnEnableList.indexOf(1) > -1) {
        actions.push({ key: 'add', icon: 'plus', handler: this.handleAdd })
      }
      setMobileHeaderActions(actions)
    },
    loadData() {
      this.refresh();
    },
    loadTree() {
      let that = this
      // created 与 activated 会同时触发加载，用序号只采用最后一次请求结果，避免类别重复加载两遍
      const seq = ++this.treeLoadSeq
      let params = {};
      params.id='';
      queryMaterialCategoryTreeList(params).then((res) => {
        if (seq !== that.treeLoadSeq) return
        that.treeData = []
        that.categoryTree = []
        that.allTreeKeys = []
        that.iExpandedKeys = []
        if (res) {
          for (let i = 0; i < res.length; i++) {
            let temp = res[i]
            that.categoryTree.push(temp)
            that.setThisExpandedKeys(temp)
            that.getAllKeys(temp);
          }
          this.loading = false
        }
      })
    },
    setThisExpandedKeys(node) {
      if (node.children && node.children.length > 0) {
        this.iExpandedKeys.push(node.key)
        for (let a = 0; a < node.children.length; a++) {
          this.setThisExpandedKeys(node.children[a])
        }
      }
    },
    refresh() {
      this.loading = true
      this.loadTree()
    },
    // 右键操作方法
    rightHandle(node) {
      this.dropTrigger = 'contextmenu'
      console.log(node.node.eventKey)
      this.rightClickSelectedKey = node.node.eventKey
      this.rightClickSelectedOrgCode = node.node.dataRef.orgCode
    },
    onExpand(expandedKeys) {
      console.log('onExpand', expandedKeys)
      this.iExpandedKeys = expandedKeys
    },
    backFlowList() {
      this.$router.back(-1)
    },
    // 右键点击下拉框改变事件
    dropStatus(visible) {
      if (visible == false) {
        this.dropTrigger = ''
      }
    },
    // 右键店家下拉关闭下拉框
    closeDrop() {
      this.dropTrigger = ''
    },
    addRootNode() {
      this.$refs.nodeModal.add(this.currFlowId, '')
    },
    batchDel: function () {
      console.log(this.checkedKeys)
      if (this.checkedKeys.length <= 0) {
        this.$message.warning('请选择一条记录！')
      } else {
        let ids = ''
        for (let a = 0; a < this.checkedKeys.length; a++) {
          ids += this.checkedKeys[a] + ','
        }
        let that = this
        this.$confirm({
          title: '确认删除',
          content: '确定要删除所选中的 ' + this.checkedKeys.length + ' 条数据吗?',
          onOk: function () {
            deleteAction(that.url.deleteBatch, {ids: ids}).then((res) => {
              if (res.code == 200) {
                that.$message.success(res.data.message)
                that.loadTree()
                that.onClearSelected()
              } else {
                that.$message.warning(res.data.message)
              }
            })
          }
        })
      }
    },
    // 手机端顶栏：删除当前选中的类别
    handleDeleteSelected() {
      if (!this.hasSelectedCategory) {
        this.$message.warning('请先选择一个类别!')
        return
      }
      let that = this
      const selected = this.currSelected
      this.$confirm({
        title: '确认删除',
        content: '确定要删除类别「' + (selected.title || selected.name || '') + '」吗?',
        onOk: function () {
          return deleteAction(that.url.delete, {id: selected.id}).then((res) => {
            if (res.code == 200) {
              that.$message.success(res.data.message || '删除成功!')
              that.onClearSelected()
              that.loadTree()
            } else {
              that.$message.warning((res.data && res.data.message) || '删除失败')
            }
          })
        }
      })
    },
    nodeModalOk() {
      this.loadTree()
    },
    nodeModalClose() {
    },
    hide() {
      this.visible = false
    },
    onCheck(checkedKeys, info) {
      console.log('onCheck', checkedKeys, info)
      this.hiding = false
      //this.checkedKeys = checkedKeys.checked
      // <!---- author:os_chengtgen -- date:20190827 --  for:切换父子勾选模式 =======------>
      if(this.checkStrictly){
        this.checkedKeys = checkedKeys.checked;
      }else{
        this.checkedKeys = checkedKeys
      }
      // <!---- author:os_chengtgen -- date:20190827 --  for:切换父子勾选模式 =======------>
    },
    onSelect(selectedKeys, e) {
      console.log('selected', selectedKeys, e)
      this.hiding = false
      let record = e.node.dataRef
      let params = {};
      params.id=record.id;
      this.getTreeByParams(params)
      queryMaterialCategoryById(params).then((res) => {
        if (res && res.code == 200) {
          if(res.data){
            record.name = res.data.name;
            record.serialNo = res.data.serialNo;
            record.parentId = res.data.parentId;
            record.sort = res.data.sort;
            record.remark = res.data.remark;
            record.categoryLevel = res.data.categoryLevel;
            record.specConfig = res.data.specConfig
            record.unit = res.data.unit
            this.specModules = this.parseSpecConfig(record.specConfig)
            console.log('onSelect-record', record)
            this.currSelected = Object.assign({}, record)
            this.model = this.currSelected
            this.selectedKeys = [record.key]
            this.model.parentId = record.parentId
            this.formParentId = record.parentId
            this.setValuesToForm(record)
          }
        }
      })
    },
    //根据类别id加载类别树
    getTreeByParams(params) {
      queryMaterialCategoryTreeList(params).then((res) => {
        if (res) {
          this.treeData = []
          for (let i = 0; i < res.length; i++) {
            this.treeData.push(res[i])
          }
        }
      })
    },
    // 触发onSelect事件时,为类别树右侧的form表单赋值
    setValuesToForm(record) {
      this.$nextTick(() => {
        this.form.setFieldsValue(pick(record, 'name','serialNo', 'parentId', 'sort', 'remark'))
        // 子类目才回填单位（一级类目无单位属性，输入框不渲染）
        if (!this.isTopLevelCategory) {
          this.form.setFieldsValue({ unit: record.unit || '' })
        }
      })
    },
    // 改变上级目录：切换一级/子类目时同步单位输入框
    onParentChange(value) {
      this.formParentId = value === undefined || value === '' ? null : value
      if (this.isTopLevelCategory) {
        this.form.setFieldsValue({ unit: '' })
      }
    },
    getCurrSelectedTitle() {
      return !this.currSelected.title ? '' : this.currSelected.title
    },
    onClearSelected() {
      this.hiding = true
      this.checkedKeys = []
      this.currSelected = {}
      this.form.resetFields()
      this.formParentId = null
      this.selectedKeys = []
      this.specModules = []
    },
    handleNodeTypeChange(val) {
      this.currSelected.nodeType = val
    },
    notifyTriggerTypeChange(value) {
      this.currSelected.notifyTriggerType = value
    },
    receiptTriggerTypeChange(value) {
      this.currSelected.receiptTriggerType = value
    },
    submitCurrForm() {
      this.form.validateFields((err, values) => {
        if (!err) {
          if (!this.currSelected.id) {
            this.$message.warning('请点击选择要修改类别!')
            return
          }
          let formData = Object.assign(this.currSelected, values)
          // 一级类目无单位属性：保存时清空单位
          if (this.isTopLevelCategory) {
            formData.unit = null
          }
          // 一级类目无规格值：不校验规格，也不覆盖已有规格配置
          if (!this.isTopLevelCategory) {
            const configuredModules = this.specModules.map(item => ({
              name: (item.name || '').trim(),
              enableImage: !!item.enableImage,
              childNames: (item.childNames || []).map(name => (name || '').trim()).filter(Boolean)
            }))
            if (configuredModules.some(item => !item.name)) {
              this.$message.warning('请填写所有规格名称')
              return
            }
            const names = configuredModules.map(item => item.name.toLowerCase())
            if (new Set(names).size !== names.length) {
              this.$message.warning('规格名称不能重复')
              return
            }
            // 每个规格大类（商品规格卡片标题）下至少需要一个子规格
            const emptyChildModule = configuredModules.find(item => item.childNames.length === 0)
            if (emptyChildModule) {
              this.$message.warning('规格大类「' + emptyChildModule.name + '」至少需要一个子规格')
              return
            }
            for (let i = 0; i < configuredModules.length; i++) {
              const childNames = configuredModules[i].childNames
              if (new Set(childNames.map(name => name.toLowerCase())).size !== childNames.length) {
                this.$message.warning('同一规格下的子规格名称不能重复')
                return
              }
            }
            // 保存当前类别的规格配置
            if (this.hasSelectedCategory) {
              formData.specConfig = configuredModules.length ? JSON.stringify(configuredModules) : null
            }
          }
          console.log('Received values of form: ', formData)
          httpAction(this.url.edit, formData, 'put').then((res) => {
            if (res.code == 200) {
              this.$message.success('保存成功!')
              this.loadTree()
              let params = {}
              params.id = formData.id
              this.getTreeByParams(params)
            } else {
              this.$message.warning(res.data.message)
            }
          })
        }
      })
    },
    emptyCurrForm() {
      this.form.resetFields()
    },
    nodeSettingFormSubmit() {
      this.form.validateFields((err, values) => {
        if (!err) {
          console.log('Received values of form: ', values)
        }
      })
    },
    openSelect() {
      this.$refs.sysDirectiveModal.show()
    },
    validateName(rule, value, callback){
      let params = {
        name: value,
        parentId: this.form.getFieldValue('parentId'),
        id: this.model.id?this.model.id:0
      };
      checkMaterialCategory(params).then((res)=>{
        if(res && res.code===200) {
          if(!res.data.status){
            callback();
          } else {
            callback("名称已经存在");
          }
        } else {
          callback(res.data);
        }
      });
    },
    handleAdd() {
      // 手机端与桌面端统一使用标准弹层
      this.$refs.materialCategoryModal.add()
      this.$refs.materialCategoryModal.title = '新增'
    },
    selectDirectiveOk(record) {
      console.log('选中指令数据', record)
      this.nodeSettingForm.setFieldsValue({directiveCode: record.directiveCode})
      this.currSelected.sysCode = record.sysCode
    },
    getFlowGraphData(node) {
      this.graphDatasource.nodes.push({
        id: node.id,
        text: node.flowNodeName
      })
      if (node.children.length > 0) {
        for (let a = 0; a < node.children.length; a++) {
          let temp = node.children[a]
          this.graphDatasource.edges.push({
            source: node.id,
            target: temp.id
          })
          this.getFlowGraphData(temp)
        }
      }
    },
    // <!---- author:os_chengtgen -- date:20190827 --  for:切换父子勾选模式 =======------>
    expandAll () {
      this.iExpandedKeys = this.allTreeKeys
    },
    closeAll () {
      this.iExpandedKeys = []
    },
    checkALL () {
      this.checkStriccheckStrictlytly = false
      this.checkedKeys = this.allTreeKeys
    },
    cancelCheckALL () {
      //this.checkedKeys = this.defaultCheckedKeys
      this.checkedKeys = []
    },
    switchCheckStrictly (v) {
      if(v==1){
        this.checkStrictly = false
      }else if(v==2){
        this.checkStrictly = true
      }
    },
    getAllKeys(node) {
      // console.log('node',node);
      this.allTreeKeys.push(node.key)
      if (node.children && node.children.length > 0) {
        for (let a = 0; a < node.children.length; a++) {
          this.getAllKeys(node.children[a])
        }
      }
    },
    parseSpecConfig(config) {
      if (!config) return []
      try {
        const parsed = JSON.parse(config)
        if (!Array.isArray(parsed)) return []
        return parsed.map(item => typeof item === 'string'
          ? { name: item, enableImage: false, childNames: [] }
          : { name: item.name || '', enableImage: !!item.enableImage, childNames: Array.isArray(item.childNames) ? item.childNames.slice() : [] })
      } catch (e) { return [] }
    },
    addSpecModule() {
      this.specModules.push({ name: '', enableImage: false, childNames: [] })
    },
    addChildSpec(index) { this.$set(this.specModules[index], 'childNames', (this.specModules[index].childNames || []).concat([''])) },
    removeChildSpec(index, childIndex) { this.specModules[index].childNames.splice(childIndex, 1) },
    removeSpecModule(index) { this.specModules.splice(index, 1) },
    // 手机端子规格输入：数组下标赋值需用 $set 才能触发视图更新
    onMobileChildSpecChange(moduleIndex, childIndex, event) {
      const value = event && event.target ? event.target.value : ''
      this.$set(this.specModules[moduleIndex].childNames, childIndex, value)
    },
    // <!---- author:os_chengtgen -- date:20190827 --  for:切换父子勾选模式 =======------>

  },
  created() {
    this.currFlowId = this.$route.params.id
    this.currFlowName = this.$route.params.name
    // this.loadTree()
  },

}
</script>
<style scoped>
.ant-card-body .table-operator {
  margin: 15px;
}

.anty-form-btn {
  width: 100%;
  text-align: center;
}

.anty-form-btn button {
  margin: 0 5px;
}

.anty-node-layout .ant-layout-header {
  padding-right: 0
}

.header {
  padding: 0 8px;
}

.header button {
  margin: 0 3px
}

.ant-modal-cust-warp {
  height: 100%
}

.ant-modal-cust-warp .ant-modal-body {
  height: calc(100% - 110px) !important;
  overflow-y: auto
}

.ant-modal-cust-warp .ant-modal-content {
  height: 90% !important;
  overflow-y: hidden
}

#app .desktop {
  height: auto !important;
}

/** Button按钮间距 */
.ant-btn {
  margin-left: 3px
}

.drawer-bootom-button {
  /*position: absolute;*/
  bottom: 0;
  width: 100%;
  border-top: 1px solid #e8e8e8;
  padding: 10px 16px;
  text-align: left;
  left: 0;
  background: #fff;
  border-radius: 0 0 2px 2px;
}

/* 添加子规格：小号按钮，不占满行高 */
.add-child-spec-btn {
  height: 24px;
  padding: 0 8px;
  font-size: 12px;
  line-height: 22px;
  margin-top: 2px;
}

/* 子规格行删除图标：贴紧输入框 */
.child-spec-delete {
  padding: 0 4px;
  height: 24px;
  line-height: 24px;
  margin-left: 2px;
}

/* ==================== 手机端布局 ==================== */
.mcl-body {
  padding: 10px 12px 12px;
  background: #f0f2f5;
  box-sizing: border-box;
  min-height: 100%;
}

.mcl-card {
  background: #fff;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
}

.mcl-card-title {
  font-size: 13px;
  line-height: 18px;
  color: #999;
  margin-bottom: 6px;
}

.mcl-muted {
  color: #999;
  font-size: 13px;
}

.mcl-clear {
  margin-left: 12px;
}

.mcl-empty {
  text-align: center;
  color: #999;
  font-size: 14px;
  padding: 24px 12px;
}

/* 树操作：整行可点，标题左、箭头右 */
.mcl-tree-ops-btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 40px;
  padding: 0;
  border: none;
  box-shadow: none;
  background: transparent;
  font-size: 15px;
}

.mcl-tree-ops-btn:hover,
.mcl-tree-ops-btn:focus {
  color: inherit;
  background: transparent;
}

/* 规格卡片 */
.mcl-spec-card {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
}

.mcl-spec-head {
  display: flex;
  align-items: center;
}

.mcl-spec-name {
  flex: 1 1 auto;
  min-width: 0;
}

.mcl-spec-del {
  flex: none;
  margin-left: 10px;
  font-size: 16px;
  color: #ff4d4f;
}

.mcl-spec-switch {
  display: flex;
  align-items: center;
  margin-top: 10px;
  font-size: 14px;
  color: #333;
}

.mcl-spec-switch span {
  margin-left: 8px;
}

.mcl-spec-value {
  display: flex;
  align-items: center;
  margin-top: 8px;
}

.mcl-spec-value .ant-input {
  flex: 1 1 auto;
  min-width: 0;
}

.mcl-spec-add-child {
  margin-top: 10px;
}

.mcl-spec-tip {
  margin-top: 8px;
}

/* 标准底栏：按钮高 40px，右对齐 */
.mcl-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.mcl-footer .ant-btn {
  height: 40px;
  padding: 0 20px;
  font-size: 15px;
}

.mcl-footer .ant-btn + .ant-btn {
  margin-left: 10px;
}

.mcl-footer .ant-btn-primary {
  min-width: 96px;
  font-weight: 500;
}
</style>
<style>
/* 手机端：MobilePage 内部节点与表单行样式（子组件内部结构，scoped 无法命中） */
.mcl-page .mobile-page-body {
  background: #f0f2f5;
}

/* 页面高度按动态视口计算，避免 iOS 底部工具栏遮挡底栏 */
.mcl-page.mobile-page-fill.mobile-page-no-header {
  height: calc(100vh - 49px);
  height: calc(100dvh - 49px);
}

/* 底栏固定贴屏幕底部，并预留 iPhone 底部安全区 */
.mcl-page .mobile-page-footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9;
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1px solid #eee;
  box-shadow: 0 -1px 4px rgba(0, 0, 0, 0.04);
}

.mcl-page .ant-tree {
  font-size: 15px;
}

.mcl-page .ant-tree li {
  padding: 4px 0;
}

.mcl-page .ant-tree li .ant-tree-node-content-wrapper {
  line-height: 26px;
}

.mcl-page .ant-tree-checkbox,
.mcl-page .ant-tree-switcher {
  margin-right: 6px;
}

/* 表单：标题左、控件右，行间分隔线，输入控件无边框 */
.mcl-page .mcl-tree-ops .ant-dropdown-trigger {
  display: block;
  width: 100%;
}

.mcl-page .mcl-form .ant-form-item {
  display: flex;
  align-items: center;
  margin-bottom: 0;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.mcl-page .mcl-form .ant-form-item:last-child {
  border-bottom: none;
}

.mcl-page .mcl-form .ant-form-item-label {
  flex: 0 0 76px;
  width: 76px !important;
  max-width: 76px;
  padding-right: 8px;
  line-height: 24px;
  text-align: left;
}

.mcl-page .mcl-form .ant-form-item-label > label {
  font-size: 15px;
  color: #333;
  white-space: nowrap;
}

.mcl-page .mcl-form .ant-form-item-label > label::after {
  content: '';
  margin: 0;
}

.mcl-page .mcl-form .ant-form-item-control-wrapper {
  flex: 1 1 auto;
  width: auto !important;
  min-width: 0;
}

.mcl-page .mcl-form .ant-input,
.mcl-page .mcl-form .ant-select-selection {
  border: none;
  box-shadow: none;
  padding-left: 0;
  font-size: 15px;
  background: transparent;
}

.mcl-page .mcl-form .ant-select-selection__rendered {
  margin-left: 0;
}
</style>
