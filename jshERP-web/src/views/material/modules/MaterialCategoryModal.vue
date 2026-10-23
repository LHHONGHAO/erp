<template>
  <div ref="container">
    <a-modal
      :title="title"
      :width="800"
      :ok=false
      :visible="visible"
      :confirmLoading="confirmLoading"
      :okButtonProps="{ props: {disabled: disableSubmit} }"
      :getContainer="() => $refs.container"
      :maskStyle="{'top':'93px','left':'154px'}"
      :wrapClassName="wrapClassNameInfo()"
      :mask="isDesktop()"
      :maskClosable="false"
      @ok="handleOk"
      @cancel="handleCancel"
      style="top:100px;height: 50%;"
      cancelText="取消"
      okText="保存">
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
          <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="规格配置" v-show="isFirstLevel">
            <div v-for="(module, mIndex) in specModules" :key="mIndex" style="border:1px solid #eee;border-radius:4px;padding:10px;margin-top:10px;">
              <div style="display:flex;align-items:center;gap:8px;">
                <a-input v-model="module.name" style="width:220px" placeholder="请输入规格名称" />
                <a-switch size="small" v-model="module.enableImage" />
                <span>可添加规格图片</span>
                <a-button type="link" size="small" @click="removeSpecModule(mIndex)"><a-icon type="delete" /></a-button>
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

<style scoped>

</style>
