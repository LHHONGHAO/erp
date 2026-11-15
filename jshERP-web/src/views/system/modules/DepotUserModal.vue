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
          :treeData="depotUserTree"
          :checkStrictly="checkStrictly"
          :expandedKeys="iExpandedKeys"
          @check="onCheck"
          @expand="onExpand" />
      </a-spin>
    </a-modal>
  </div>
</template>
<script>
  import {mixinDevice} from '@/utils/mixin'
  import CheckedTreePanel from '@/components/jeecg/CheckedTreePanel.vue'
  import { getTreeSelectionState } from '@/utils/treeSelection'
  import {updateOneValueByKeyIdAndType} from '@/api/api'
  import {getAction} from '../../../api/manage'
  export default {
    name: "DepotUserModal",
    mixins: [mixinDevice],
    components: { CheckedTreePanel },
    data () {
      return {
        title:"操作",
        visible: false,
        model: {},
        depotId: 0,
        iExpandedKeys: [],
        depotUserTree: [],
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
        this.visible = true
        this.depotId = record.id
        this.checkedKeys = []
        this.iExpandedKeys = []
        this.depotUserTree = []
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
            type: 'UserDepot', keyIds: this.checkedKeys.slice(), oneValue: this.depotId
          })
          updateOneValueByKeyIdAndType(formData).then((res) => {
            if (res && res.code === 200) {
              this.$message.info('保存成功')
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
        return getAction('/user/getUserWithChecked', { UBType: 'UserDepot', UBValue: id }).then((res) => {
          this.depotUserTree = Array.isArray(res) ? res : []
          const state = getTreeSelectionState(this.depotUserTree)
          this.checkedKeys = state.checkedKeys
          this.iExpandedKeys = state.expandedKeys
        }).catch(() => {
          this.$message.error('用户授权树加载失败，请重试')
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
