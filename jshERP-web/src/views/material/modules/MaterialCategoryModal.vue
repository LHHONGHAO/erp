<template>
  <div ref="container">
    <a-modal
      :width="isMobile() ? '100%' : 800"
      :ok=false
      :visible="visible"
      :closable="!isMobile()"
      :confirmLoading="confirmLoading"
      :okButtonProps="{ props: {disabled: disableSubmit} }"
      :getContainer="() => $refs.container"
      :maskStyle="{'top':'93px','left':'154px'}"
      :wrapClassName="isMobile() ? 'mm-sheet-modal' : 'ant-modal-cust-warp depot-mask'"
      :mask="isDesktop()"
      :maskClosable="false"
      :style="isMobile() ? { top: '0', height: '100%' } : { top: '100px' }"
      @ok="handleOk"
      @cancel="handleCancel"
      cancelText="取消"
      okText="保存">
      <template slot="title">
        <!-- 手机端标准弹层顶栏：白底、标题居中、右侧关闭 -->
        <div v-if="isMobile()" class="mm-sheet-header">
          <div class="mm-sheet-title-block">
            <div class="mm-sheet-title">{{ title }}</div>
            <div class="mm-sheet-subtitle" v-if="sheetSubtitle">{{ sheetSubtitle }}</div>
          </div>
          <a-icon type="close" class="mm-sheet-close" @click="handleCancel" />
        </div>
        <span v-else>{{ title }}</span>
      </template>
      <a-spin :spinning="confirmLoading">
        <a-form :form="form" class="mcm-form">
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="名称">
            <a-input placeholder="请输入名称" v-decorator="['name', validatorRules.name ]"/>
          </a-form-item>
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="编号">
            <a-input placeholder="保存时自动生成" disabled v-decorator="['serialNo']"/>
          </a-form-item>
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="上级目录">
            <a-tree-select style="width:100%" :dropdownStyle="{maxHeight:'200px',overflow:'auto'}"
                           allow-clear :treeDefaultExpandAll="true"
                 :treeData="categoryTree" v-decorator="[ 'parentId' ]" placeholder="请选择上级目录"
                 @change="onParentChange">
            </a-tree-select>
          </a-form-item>
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="排序">
            <a-input v-decorator="[ 'sort' ]"/>
          </a-form-item>
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="备注">
            <a-textarea placeholder="请输入备注":rows="2" v-decorator.trim="[ 'remark' ]" />
          </a-form-item>
          <!-- 类别规格配置：选择上级目录（二级类目）后才可配置 -->
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="规格配置" v-if="parentId" class="mcm-spec-item">
            <div v-for="(module, mIndex) in specModules" :key="mIndex" class="mcm-spec-card">
              <div class="mcm-spec-card-head">
                <a-input v-model="module.name" class="mcm-spec-name" placeholder="请输入规格名称" />
                <div class="mcm-spec-card-head-right">
                  <a-switch size="small" v-model="module.enableImage" />
                  <span class="mcm-spec-switch-label">可添加规格图片</span>
                  <a-button type="link" size="small" class="mcm-spec-card-del" @click="removeSpecModule(mIndex)"><a-icon type="delete" /></a-button>
                </div>
              </div>
              <div class="mcm-spec-divider"></div>
              <div v-for="(value, vIndex) in module.childNames" :key="vIndex" class="mcm-spec-value-row">
                <div class="mcm-spec-value-label">规格值{{ vIndex + 1 }}</div>
                <a-input
                  :value="module.childNames[vIndex]"
                  class="mcm-spec-value-input"
                  :placeholder="'请输入规格值' + (vIndex + 1)"
                  @change="onSpecValueChange(mIndex, vIndex, $event)" />
                <a-icon type="delete" class="mcm-spec-value-del" @click="removeSpecValue(mIndex, vIndex)" />
              </div>
              <div class="mcm-spec-card-bottom">
                <a-button type="dashed" size="small" icon="plus" @click="addSpecValue(mIndex)">添加组内规格项</a-button>
              </div>
            </div>
            <div style="margin-top:8px;">
              <a-button type="dashed" icon="plus" @click="addSpecModule">添加规格</a-button>
            </div>
            <span v-if="specModules.length===0" style="color:#999;font-size:12px;">商品规格值在商品信息页面维护</span>
          </a-form-item>
        </a-form>
      </a-spin>
    </a-modal>
  </div>
</template>

<script>
  import { httpAction } from '@/api/manage'
  import {mixinDevice} from '@/utils/mixin'
  import { queryMaterialCategoryTreeList, checkMaterialCategory } from '@/api/api'
  import pick from 'lodash.pick'
  import ATextarea from 'ant-design-vue/es/input/TextArea'
  export default {
    name: "MaterialCategoryModal",
    mixins: [mixinDevice],
    components: { ATextarea },
    data () {
      return {
        categoryTree:[],
        orgTypeData:[],
        phoneWarning:'',
        departName:"",
        title:"操作",
        visible: false,
        disableSubmit:false,
        model: {},
        menuhidden:false,
        menuusing:true,
        labelCol: {
          xs: { span: 24 },
          sm: { span: 5 },
        },
        wrapperCol: {
          xs: { span: 24 },
          sm: { span: 16 },
        },

        confirmLoading: false,
        form: this.$form.createForm(this),
        validatorRules:{
          name: {
            rules: [
              {required: true, message: '请输入名称!'},
              { validator: this.validateName}
            ]
          },
          serialNo: {rules: []}
        },
        url: {
          add: "/materialCategory/add",
        },
        parentId: undefined,
        specModules: [],
      }
    },
    computed: {
      // 标准弹层副标题（顶栏第二行）
      sheetSubtitle() {
        return this.title.indexOf('编辑') > -1 ? '编辑商品类别' : '新建商品类别'
      }
    },
    created () {
    },
    methods: {
      loadTreeData(){
        var that = this;
        let params = {};
        params.id='';
        queryMaterialCategoryTreeList(params).then((res)=>{
          if(res){
            that.categoryTree = [];
            for (let i = 0; i < res.length; i++) {
              let temp = res[i];
              that.categoryTree.push(temp);
            }
          }
        })
      },
      add () {
        this.edit();
        this.parentId = undefined;
        this.specModules = []
      },
      edit (record) {
        this.form.resetFields();
        this.model = Object.assign({}, record || {});
        this.parentId = this.model.parentId;
        this.specModules = this.parseSpecConfig(this.model.specConfig)
        this.visible = true;
        this.loadTreeData();
        this.$nextTick(() => {
          this.form.setFieldsValue(pick(record, 'name','serialNo', 'parentId', 'sort', 'remark'))
        });
      },
      close () {
        this.$emit('close');
        this.disableSubmit = false;
        this.visible = false;
      },
      handleOk () {
        const that = this;
        // 触发表单验证
        this.form.validateFields((err, values) => {
          if (!err) {
            that.confirmLoading = true;
            let formData = Object.assign(this.model, values);
            const configuredModules = this.specModules.map(item => ({
              name: (item.name || '').trim(),
              enableImage: !!item.enableImage,
              childNames: (item.childNames || []).map(value => (value || '').trim()).filter(value => !!value)
            }))
            if (configuredModules.some(item => !item.name)) {
              that.$message.warning('请填写所有规格名称')
              that.confirmLoading = false
              return
            }
            const names = configuredModules.map(item => item.name.toLowerCase())
            if (new Set(names).size !== names.length) {
              that.$message.warning('规格名称不能重复')
              that.confirmLoading = false
              return
            }
            const duplicatedValue = configuredModules.some(item => {
              const values = item.childNames.map(value => value.toLowerCase())
              return new Set(values).size !== values.length
            })
            if (duplicatedValue) {
              that.$message.warning('同一规格下的规格值不能重复')
              that.confirmLoading = false
              return
            }
            formData.specConfig = configuredModules.length ? JSON.stringify(configuredModules) : null
            console.log(formData)
            httpAction(this.url.add,formData,"post").then((res)=>{
              if(res.code == 200){
                that.$message.success(res.data.message);
                that.loadTreeData();
                that.$emit('ok');
              }else{
                that.$message.warning(res.data.message);
              }
            }).finally(() => {
              that.confirmLoading = false;
              that.close();
            })
          }
        })
      },
      handleCancel () {
        this.close()
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
      parseSpecConfig(config) {
        if (!config) return []
        try {
          const parsed = JSON.parse(config)
          if (!Array.isArray(parsed)) return []
          return parsed.map(item => typeof item === 'string'
            ? { name: item, enableImage: false, childNames: [] }
            : { name: item.name || '', enableImage: !!item.enableImage, childNames: (item.childNames || []).slice() })
        } catch (e) { return [] }
      },
      // 选择/清空上级目录：清空时规格配置一并清掉
      onParentChange(value) {
        this.parentId = value
        if (!value) {
          this.specModules = []
        }
      },
      addSpecModule() {
        this.specModules.push({ name: '', enableImage: false, childNames: [] })
      },
      removeSpecModule(index) { this.specModules.splice(index, 1) },
      // 卡片内新增一行规格值
      addSpecValue(index) {
        this.specModules[index].childNames.push('')
      },
      removeSpecValue(index, valueIndex) {
        this.specModules[index].childNames.splice(valueIndex, 1)
      },
      // 数组下标赋值需用 $set 才能触发更新
      onSpecValueChange(index, valueIndex, event) {
        const value = event && event.target ? event.target.value : ''
        this.$set(this.specModules[index].childNames, valueIndex, value)
      }
    }
  }
</script>

<style>
  /* 手机端：全屏、顶栏与页脚样式由全局标准弹层 .mm-sheet-modal 提供，这里只处理表单布局 */
  /* 表单作为白色圆角卡片铺在浅灰内容区上 */
  .mm-sheet-modal .mcm-form {
    padding: 0 12px;
    background: #fff;
    border-radius: 12px;
    overflow: hidden;
  }
  .mm-sheet-modal .mcm-form > .ant-form-item:last-child {
    border-bottom: none;
  }
  /* 手机端：属性标题与输入框同行显示 */
  .mm-sheet-modal .ant-form-item {
    display: flex;
    align-items: center;
    margin-bottom: 0;
    padding: 8px 0;
    border-bottom: 1px solid #e8e8e8;
  }
  .mm-sheet-modal .ant-form-item-label {
    flex: 0 0 80px;
    width: 80px !important;
    max-width: 80px;
    padding-right: 8px;
    text-align: right;
  }
  .mm-sheet-modal .ant-form-item-label > label {
    color: #333;
    font-size: 16px;
    line-height: 24px;
    white-space: nowrap;
  }
  .mm-sheet-modal .ant-form-item-control-wrapper {
    flex: 1 1 auto;
    width: auto !important;
    min-width: 0;
  }
  /* 规格配置内容较多，单独占一行完整展示 */
  .mm-sheet-modal .ant-form-item.mcm-spec-item {
    display: block;
  }
  .mm-sheet-modal .ant-form-item.mcm-spec-item .ant-form-item-label {
    width: auto !important;
    max-width: none;
    flex: none;
    text-align: left;
  }
  .mm-sheet-modal .mcm-spec-item .ant-input {
    width: 100% !important;
  }
  /* 规格卡片：名称在左，图片按钮与删除按钮位于右侧并与标题垂直居中 */
  .mcm-spec-card {
    border: 1px solid #eee;
    border-radius: 4px;
    padding: 10px;
    margin-top: 10px;
  }
  .mcm-spec-card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }
  .mcm-spec-card-head-right {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: none;
  }
  .mcm-spec-name {
    flex: 1 1 auto;
    min-width: 0;
  }
  .mcm-spec-switch-label {
    font-size: 14px;
    color: #333;
    white-space: nowrap;
  }
  .mcm-spec-card-del {
    padding: 0;
  }
  .mcm-spec-divider {
    height: 1px;
    background: #e8e8e8;
    margin: 10px 0;
  }
  .mcm-spec-value-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    padding-bottom: 8px;
    border-bottom: 1px dashed #f0f0f0;
  }
  .mcm-spec-value-label {
    flex: 0 0 auto;
    font-size: 14px;
    color: #333;
    white-space: nowrap;
  }
  .mcm-spec-value-input {
    flex: 1 1 auto;
    min-width: 0;
  }
  .mcm-spec-value-del {
    flex: none;
    color: #ff4d4f;
    font-size: 14px;
  }
  .mcm-spec-card-bottom {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
  }
</style>

