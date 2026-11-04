<template>
  <mobile-page class="mca-page" :standalone="false" body-padding="12px">
    <a-form :form="form">
      <a-form-item label="名称">
        <a-input placeholder="请输入名称" v-decorator="['name', validatorRules.name]" />
      </a-form-item>
      <a-form-item label="编号">
        <a-input placeholder="保存时自动生成" disabled v-decorator="['serialNo']" />
      </a-form-item>
      <a-form-item label="上级目录">
        <a-tree-select
          style="width:100%"
          :dropdownStyle="{maxHeight:'200px',overflow:'auto'}"
          allow-clear
          :treeDefaultExpandAll="true"
          :treeData="categoryTree"
          v-decorator="['parentId']"
          placeholder="请选择上级目录"
          @change="onParentChange" />
      </a-form-item>
      <a-form-item label="排序">
        <a-input v-decorator="['sort']" />
      </a-form-item>
      <a-form-item label="备注">
        <a-textarea placeholder="请输入备注" :rows="2" v-decorator.trim="['remark']" />
      </a-form-item>
      <!-- 非一级目录（已选择上级目录）才可配置规格 -->
      <a-form-item v-if="parentId" label="规格配置" class="mca-spec-item">
        <div class="mca-spec-cards">
          <div v-for="(module, mIndex) in specModules" :key="mIndex" class="mca-spec-card">
            <div class="mca-spec-card-head">
              <a-input v-model="module.name" class="mca-spec-name" placeholder="请输入规格名" />
              <div class="mca-spec-card-head-right">
                <a-switch size="small" v-model="module.enableImage" />
                <span class="mca-spec-switch-label">添加规格图片</span>
                <a-icon type="delete" class="mca-spec-card-del" @click="removeSpecModule(mIndex)" />
              </div>
            </div>
            <div class="mca-spec-divider"></div>
            <div
              v-for="(value, vIndex) in module.childNames"
              :key="vIndex"
              class="mca-spec-value-row">
              <div class="mca-spec-value-label">规格值{{ vIndex + 1 }}</div>
              <a-input
                :value="module.childNames[vIndex]"
                class="mca-spec-value-input"
                :placeholder="'请输入规格值' + (vIndex + 1)"
                @change="onSpecValueChange(mIndex, vIndex, $event)" />
              <a-icon type="delete" class="mca-spec-value-del" @click="removeSpecValue(mIndex, vIndex)" />
            </div>
            <div class="mca-spec-card-bottom">
              <a-button type="dashed" size="small" icon="plus" @click="addSpecValue(mIndex)">添加规格值</a-button>
              <div v-if="savedValues(module).length > 0" class="mca-spec-saved">
                <span class="mca-spec-saved-label">已保存：</span>
                <a-tag
                  v-for="item in savedValues(module)"
                  :key="item.index"
                  closable
                  @close="removeSpecValue(mIndex, item.index)">{{ item.text }}</a-tag>
              </div>
            </div>
          </div>
        </div>
        <div class="mca-spec-add">
          <a-button type="dashed" icon="plus" @click="addSpecModule">规格设置</a-button>
        </div>
      </a-form-item>
    </a-form>
    <template slot="footer">
      <div class="mca-footer">
        <a-button @click="goBack">取消</a-button>
        <a-button type="primary" :loading="confirmLoading" @click="handleSave">保存</a-button>
      </div>
    </template>
  </mobile-page>
</template>

<script>
  import { httpAction } from '@/api/manage'
  import { queryMaterialCategoryTreeList, checkMaterialCategory } from '@/api/api'
  import MobilePage from '@/components/page/MobilePage'
  import pick from 'lodash.pick'

  export default {
    name: 'MaterialCategoryAdd',
    components: { MobilePage },
    data() {
      return {
        categoryTree: [],
        parentId: undefined,
        specModules: [],
        confirmLoading: false,
        form: this.$form.createForm(this),
        validatorRules: {
          name: {
            rules: [
              { required: true, message: '请输入名称!' },
              { validator: this.validateName }
            ]
          }
        },
        url: {
          add: '/materialCategory/add'
        }
      }
    },
    mounted() {
      this.loadTreeData()
    },
    // keep-alive 再次进入时重置表单，避免残留上一次内容
    activated() {
      this.form.resetFields()
      this.parentId = undefined
      this.specModules = []
      this.loadTreeData()
    },
    methods: {
      loadTreeData() {
        queryMaterialCategoryTreeList({ id: '' }).then(res => {
          if (res) {
            this.categoryTree = res.slice()
          }
        })
      },
      onParentChange(value) {
        this.parentId = value
        if (!value) {
          this.specModules = []
        }
      },
      validateName(rule, value, callback) {
        if (!value) {
          callback()
          return
        }
        checkMaterialCategory({
          name: value,
          parentId: this.form.getFieldValue('parentId'),
          id: 0
        }).then(res => {
          if (res && res.code === 200) {
            if (!res.data.status) {
              callback()
            } else {
              callback('名称已经存在')
            }
          } else {
            callback(res.data)
          }
        })
      },
      // 新增一张规格卡片
      addSpecModule() {
        this.specModules.push({ name: '', enableImage: false, childNames: [] })
      },
      removeSpecModule(index) {
        this.specModules.splice(index, 1)
      },
      // 卡片内新增一行规格值
      addSpecValue(index) {
        this.specModules[index].childNames.push('')
      },
      removeSpecValue(index, valueIndex) {
        this.specModules[index].childNames.splice(valueIndex, 1)
      },
      // 数组下标赋值需用 $set 才能触发「已保存」标签的即时更新
      onSpecValueChange(index, valueIndex, event) {
        var value = event && event.target ? event.target.value : ''
        this.$set(this.specModules[index].childNames, valueIndex, value)
      },
      // 已填写（即时展示）的规格值，用于「已保存」标签
      savedValues(module) {
        var list = []
        var values = module.childNames || []
        values.forEach(function(value, index) {
          var text = (value || '').trim()
          if (text) list.push({ index: index, text: text })
        })
        return list
      },
      handleSave() {
        this.form.validateFields((err, values) => {
          if (err) return
          const configuredModules = this.specModules.map(item => ({
            name: (item.name || '').trim(),
            enableImage: !!item.enableImage,
            childNames: (item.childNames || []).map(value => (value || '').trim()).filter(value => !!value)
          }))
          if (configuredModules.some(item => !item.name)) {
            this.$message.warning('请填写所有规格名')
            return
          }
          const names = configuredModules.map(item => item.name.toLowerCase())
          if (new Set(names).size !== names.length) {
            this.$message.warning('规格名不能重复')
            return
          }
          const duplicatedValue = configuredModules.some(item => {
            const values = item.childNames.map(value => value.toLowerCase())
            return new Set(values).size !== values.length
          })
          if (duplicatedValue) {
            this.$message.warning('同一规格下的规格值不能重复')
            return
          }
          const formData = Object.assign({}, values)
          formData.specConfig = configuredModules.length ? JSON.stringify(configuredModules) : null
          this.confirmLoading = true
          httpAction(this.url.add, formData, 'post').then(res => {
            if (res.code === 200) {
              this.$message.success(res.data.message)
              this.goBack()
            } else {
              this.$message.warning(res.data.message)
            }
          }).finally(() => {
            this.confirmLoading = false
          })
        })
      },
      goBack() {
        this.$router.back()
      }
    }
  }
</script>

<style scoped>
  .mca-footer {
    display: flex;
    justify-content: flex-end;
  }

  .mca-footer .ant-btn {
    margin-left: 8px;
  }

  /* 规格卡片 */
  .mca-spec-card {
    border: 1px solid #eee;
    border-radius: 8px;
    padding: 10px 12px;
    margin-bottom: 10px;
    background: #fff;
  }

  .mca-spec-card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  /* 添加图片按钮：位于卡片右侧，与卡片标题上下居中对齐 */
  .mca-spec-card-head-right {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: none;
  }

  .mca-spec-name {
    flex: 1 1 auto;
    min-width: 0;
  }

  .mca-spec-switch-label {
    font-size: 14px;
    color: #333;
    white-space: nowrap;
  }

  .mca-spec-card-del {
    flex: none;
    color: #ff4d4f;
    font-size: 16px;
  }

  .mca-spec-divider {
    height: 1px;
    background: #e8e8e8;
    margin: 10px 0;
  }

  .mca-spec-value-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    padding-bottom: 8px;
    border-bottom: 1px dashed #f0f0f0;
  }

  .mca-spec-value-label {
    flex: 0 0 auto;
    font-size: 16px;
    color: #333;
    white-space: nowrap;
  }

  .mca-spec-value-input {
    flex: 1 1 auto;
    min-width: 0;
  }

  .mca-spec-value-del {
    flex: none;
    color: #ff4d4f;
    font-size: 14px;
  }

  .mca-spec-card-bottom {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
  }

  .mca-spec-saved {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #999;
  }

  .mca-spec-saved .ant-tag {
    margin: 0;
  }

  .mca-spec-add {
    margin-top: 4px;
  }
</style>
<style>
  /* 手机端标准表单：标题左、输入框右，行间分隔线，输入框无边框 */
  .mca-page .ant-form-item {
    display: flex;
    align-items: center;
    margin-bottom: 0;
    padding: 8px 0;
    border-bottom: 1px solid #e8e8e8;
  }
  .mca-page .ant-form-item-label {
    flex: 0 0 80px;
    width: 80px !important;
    max-width: 80px;
    padding-right: 8px;
    text-align: right;
  }
  .mca-page .ant-form-item-label > label {
    color: #333;
    font-size: 16px;
    line-height: 24px;
    white-space: nowrap;
  }
  .mca-page .ant-form-item-control-wrapper {
    flex: 1 1 auto;
    width: auto !important;
    min-width: 0;
  }
  /* 规格配置：标题独立一行，卡片区占满整行宽度 */
  .mca-page .ant-form-item.mca-spec-item {
    display: block;
  }
  .mca-page .ant-form-item.mca-spec-item .ant-form-item-label {
    width: auto !important;
    max-width: none;
    padding-right: 0;
    text-align: left;
  }
</style>
