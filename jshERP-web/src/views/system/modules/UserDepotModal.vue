<template>
  <div ref="container">
    <a-modal
      :title="title"
      :width="800"
      :visible="visible"
      :confirmLoading="confirmLoading"
      :getContainer="() => $refs.container"
      :maskStyle="{'top':'93px','left':'154px'}"
      :wrapClassName="wrapClassNameInfo()"
      :mask="isDesktop()"
      :maskClosable="false"
      @ok="handleOk"
      @cancel="handleCancel"
      cancelText="取消"
      okText="保存"
      style="top:5%;height: 95%;">
      <a-spin :spinning="confirmLoading || treeLoading">
        <checked-tree-panel
          :checkedKeys="checkedKeys"
          :treeData="roleFunctionTree"
          :checkStrictly="checkStrictly"
          :expandedKeys="iExpandedKeys"
          @check="onCheck"
          @expand="onExpand" />
      </a-spin>
    </a-modal>
  </div>
</template>
<script>
  import pick from 'lodash.pick'
  import {mixinDevice} from '@/utils/mixin'
  import CheckedTreePanel from '@/components/jeecg/CheckedTreePanel.vue'
  import { getTreeSelectionState } from '@/utils/treeSelection'
  import {addUserBusiness,editUserBusiness,checkUserBusiness} from '@/api/api'
  import {getAction} from '../../../api/manage'
  export default {
    name: "UserDepotModal",
    mixins: [mixinDevice],
    components: { CheckedTreePanel },
    data () {
      return {
        title:"操作",
        visible: false,
        model: {},
        roleId: 0,
        iExpandedKeys: [],
        roleFunctionTree: [],
        checkedKeys: [],
        checkStrictly: false,
        confirmLoading: false,
        treeLoading: false,
        form: this.$form.createForm(this),
      }
    },
    methods: {
      edit (record) {
        this.form.resetFields();
        this.model = Object.assign({}, {});
        this.visible = true;
        this.$nextTick(() => {
          this.form.setFieldsValue(pick(this.model,'name', 'type', 'description'))
        });
        this.roleId = record.id
        this.checkedKeys = []
        this.iExpandedKeys = []
        this.roleFunctionTree = []
        this.loadTree(record.id)
      },
      close () {
        this.$emit('close');
        this.visible = false;
      },
      handleOk () {
        this.form.validateFields((err, values) => {
          if (err) return
          this.confirmLoading = true
          const formData = Object.assign({}, this.model, values, {
            type: 'UserDepot',
            keyId: this.roleId,
            value: this.checkedKeys.slice()
          })
          checkUserBusiness({ type: 'UserDepot', keyId: this.roleId }).then((res) => {
            const existingId = res && res.data && res.data.id
            return existingId
              ? editUserBusiness(Object.assign({}, formData, { id: existingId }))
              : addUserBusiness(formData)
          }).then((res) => {
            if (res && res.code === 200) {
              this.$emit('ok')
              this.close()
            } else {
              this.$message.warning((res && res.data && res.data.message) || '保存失败')
            }
          }).catch((error) => {
            this.$message.error((error && error.message) || '保存失败，请重试')
          }).finally(() => {
            this.confirmLoading = false
          })
        })
      },
      handleCancel () {
        this.close()
      },
      loadTree(id) {
        this.treeLoading = true
        return getAction('/depot/findUserDepot', { UBType: 'UserDepot', UBKeyId: id }).then((res) => {
          this.roleFunctionTree = Array.isArray(res) ? res : []
          const state = getTreeSelectionState(this.roleFunctionTree)
          this.checkedKeys = state.checkedKeys
          this.iExpandedKeys = state.expandedKeys
        }).catch(() => {
          this.$message.error('仓库授权树加载失败，请重试')
        }).finally(() => {
          this.treeLoading = false
        })
      },
      onCheck(checkedKeys) {
        if(this.checkStrictly){
          this.checkedKeys = checkedKeys.checked;
        }else{
          this.checkedKeys = checkedKeys
        }
      },
      onExpand(expandedKeys) {
        this.iExpandedKeys = expandedKeys
      }
    }
  }
</script>
<style scoped>

</style>
