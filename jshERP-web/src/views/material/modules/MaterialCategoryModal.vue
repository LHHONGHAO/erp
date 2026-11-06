<template>
  <div ref="container">
    <a-modal
      :title="title"
      :width="isMobile() ? '100%' : 800"
      :ok=false
      :visible="visible"
      :closable="!isMobile()"
      :confirmLoading="confirmLoading"
      :okButtonProps="{ props: {disabled: disableSubmit} }"
      :getContainer="() => $refs.container"
      :maskStyle="{'top':'93px','left':'154px'}"
      :wrapClassName="wrapClassNameInfo() + (isMobile() ? ' mcm-mobile-modal' : '')"
      :mask="isDesktop()"
      :maskClosable="false"
      :style="isMobile() ? { top: '0', height: '100%' } : { top: '100px' }"
      @ok="handleOk"
      @cancel="handleCancel"
      cancelText="取消"
      okText="保存">
      <template slot="title">
        <div v-if="isMobile()" class="mcm-mobile-header">
          <a-icon type="arrow-left" class="mcm-mobile-back" @click="handleCancel" />
          <span class="mcm-mobile-title">{{ title }}</span>
          <a-button type="link" class="mcm-mobile-save" :loading="confirmLoading" @click="handleOk">保存</a-button>
        </div>
        <span v-else>{{ title }}</span>
      </template>
      <a-spin :spinning="confirmLoading">
        <a-form :form="form">
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="名称">
            <a-input placeholder="请输入名称" v-decorator="['name', validatorRules.name ]"/>
          </a-form-item>
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="编号">
            <a-input placeholder="保存时自动生成" disabled v-decorator="['serialNo']"/>
          </a-form-item>
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="上级目录">
            <a-tree-select style="width:100%" :dropdownStyle="{maxHeight:'200px',overflow:'auto'}"
                           allow-clear :treeDefaultExpandAll="true"
                 :treeData="categoryTree" v-decorator="[ 'parentId' ]" placeholder="请选择上级目录">
            </a-tree-select>
          </a-form-item>
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="排序">
            <a-input v-decorator="[ 'sort' ]"/>
          </a-form-item>
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="备注">
            <a-textarea placeholder="请输入备注":rows="2" v-decorator.trim="[ 'remark' ]" />
          </a-form-item>
          <!-- 类别规格配置 -->
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="规格配置" v-show="isFirstLevel" class="mcm-spec-item">
            <div v-for="(module, mIndex) in specModules" :key="mIndex" class="mcm-spec-card">
              <div class="mcm-spec-card-head">
                <a-input v-model="module.name" class="mcm-spec-name" placeholder="请输入规格名称" />
                <div class="mcm-spec-card-head-right">
                  <a-switch size="small" v-model="module.enableImage" />
                  <span class="mcm-spec-switch-label">可添加规格图片</span>
                  <a-button type="link" size="small" class="mcm-spec-card-del" @click="removeSpecModule(mIndex)"><a-icon type="delete" /></a-button>
                </div>
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
        specModules: [],
      }
    },
    computed: {
      isFirstLevel() {
        return !this.form.getFieldValue('parentId')
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
        this.specModules = []
      },
      edit (record) {
        this.form.resetFields();
        this.model = Object.assign({}, record || {});
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
              enableImage: !!item.enableImage
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
            if (configuredModules.length > 0) {
              const modules = configuredModules.map(item => ({
                name: item.name,
                enableImage: item.enableImage
              }))
              formData.specConfig = modules.length ? JSON.stringify(modules) : null
            } else {
              formData.specConfig = null
            }
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
            ? { name: item, enableImage: false }
            : { name: item.name || '', enableImage: !!item.enableImage })
        } catch (e) { return [] }
      },
      addSpecModule() {
        this.specModules.push({ name: '', enableImage: false })
      },
      removeSpecModule(index) { this.specModules.splice(index, 1) }
    }
  }
</script>

<style>
  /* 手机端：品类新增改为标准页面（全屏 + 蓝色顶栏），输入框沿用全局无边框样式 */
  .mcm-mobile-modal .ant-modal {
    top: 0 !important;
    padding-bottom: 0 !important;
    margin: 0 !important;
    width: 100% !important;
    max-width: 100% !important;
  }
  .mcm-mobile-modal .ant-modal-content {
    height: 100vh;
    border-radius: 0;
    display: flex;
    flex-direction: column;
  }
  .mcm-mobile-modal .ant-modal-header {
    background: #1890ff;
    padding: 0 12px;
    border-bottom: none;
  }
  .mcm-mobile-modal .ant-modal-body {
    flex: 1;
    overflow-y: auto;
    padding: 12px;
  }
  .mcm-mobile-modal .ant-modal-footer,
  .mcm-mobile-modal .ant-modal-close {
    display: none;
  }
  .mcm-mobile-header {
    display: flex;
    align-items: center;
    height: 44px;
    color: #fff;
  }
  .mcm-mobile-back,
  .mcm-mobile-save {
    color: #fff;
    font-size: 16px;
  }
  .mcm-mobile-title {
    flex: 1;
    text-align: center;
    font-size: 16px;
    font-weight: 500;
  }
  /* 手机端：属性标题与输入框同行显示 */
  .mcm-mobile-modal .ant-form-item {
    display: flex;
    align-items: center;
    margin-bottom: 0;
    padding: 8px 0;
    border-bottom: 1px solid #e8e8e8;
  }
  .mcm-mobile-modal .ant-form-item-label {
    flex: 0 0 80px;
    width: 80px !important;
    max-width: 80px;
    padding-right: 8px;
    text-align: right;
  }
  .mcm-mobile-modal .ant-form-item-label > label {
    color: #333;
    font-size: 16px;
    line-height: 24px;
    white-space: nowrap;
  }
  .mcm-mobile-modal .ant-form-item-control-wrapper {
    flex: 1 1 auto;
    width: auto !important;
    min-width: 0;
  }
  /* 规格配置内容较多，单独占一行完整展示 */
  .mcm-mobile-modal .ant-form-item.mcm-spec-item {
    display: block;
  }
  .mcm-mobile-modal .ant-form-item.mcm-spec-item .ant-form-item-label {
    width: auto !important;
    max-width: none;
    flex: none;
    text-align: left;
  }
  .mcm-mobile-modal .mcm-spec-item .ant-input {
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
</style>

