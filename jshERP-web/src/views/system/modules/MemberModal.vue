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
      :style="modalStyle" style="top:15%;height: 60%;">
      <template slot="footer">
        <a-button key="back" v-if="isReadOnly" @click="handleCancel">取消</a-button>
        <a-button key="back" @click="handleCancel" v-if="!isReadOnly">取消</a-button>
        <a-button key="submit" type="primary" :loading="confirmLoading" @click="handleOk" v-if="!isReadOnly">保存</a-button>
      </template>
      <a-spin :spinning="confirmLoading">
        <a-form :form="form" id="memberModal">
          <a-row class="form-row" :gutter="24">
            <a-col :span="12">
              <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="会员卡号">
                <a-input placeholder="请输入会员卡号" v-decorator.trim="[ 'supplier', validatorRules.supplier]" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="联系人">
                <span style="display:flex;width:100%">
                  <a-input placeholder="请输入联系人" v-decorator.trim="[ 'contacts' ]" style="flex:1;margin-right:6px" @blur="checkDuplicate" />
                  <a-button size="small" type="primary" @click="openOcrRecognize" style="flex-shrink:0">
                    <a-icon type="environment" /> 地址识别
                  </a-button>
                </span>
              </a-form-item>
            </a-col>
          </a-row>
          <a-row class="form-row" :gutter="24">
            <a-col :span="12">
              <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="电话">
                <div v-for="(phone, idx) in phoneList" :key="idx" style="display:flex;margin-bottom:4px">
                  <a-input v-model="phoneList[idx]" placeholder="请输入电话" style="flex:1;margin-right:4px" @blur="checkDuplicate" />
                  <a-button v-if="idx === phoneList.length - 1" size="small" icon="plus" @click="addPhone" />
                  <a-button v-if="phoneList.length > 1" size="small" icon="minus" @click="removePhone(idx)" />
                </div>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="电子邮箱">
                <a-input placeholder="请输入电子邮箱" v-decorator.trim="[ 'email' ]" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-row class="form-row" :gutter="24">
            <a-col :span="12">
              <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="微信账户">
                <a-input placeholder="请输入微信账户" v-decorator.trim="[ 'wechatAccount' ]" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="抖音账户">
                <a-input placeholder="请输入抖音账户" v-decorator.trim="[ 'douyinAccount' ]" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-row class="form-row" :gutter="24">
            <a-col :span="12">
              <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="支付宝账户">
                <a-input placeholder="请输入支付宝账户" v-decorator.trim="[ 'alipayAccount' ]" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="其它账户">
                <a-input placeholder="请输入其它账户" v-decorator.trim="[ 'otherAccount' ]" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-row class="form-row" :gutter="24">
            <a-col :span="12">
              <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="备注">
                <a-textarea :rows="2" placeholder="请输入备注" v-decorator.trim="[ 'description' ]" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-row class="form-row" :gutter="24">
            <a-col :span="24">
              <a-form-item :labelCol="labelCol" :wrapperCol="{
                xs: { span: 24 },
                sm: { span: 20 },
              }" label="联系地址">
                <div v-for="(addr, idx) in addressList" :key="idx" style="display:flex;margin-bottom:4px">
                  <a-input v-model="addressList[idx]" placeholder="请输入联系地址" style="flex:1;margin-right:4px" />
                  <a-button v-if="idx === addressList.length - 1" size="small" icon="plus" @click="addAddress" />
                  <a-button v-if="addressList.length > 1" size="small" icon="minus" @click="removeAddress(idx)" />
                </div>
              </a-form-item>
            </a-col>
          </a-row>
        </a-form>
      </a-spin>

      <!-- 地址识别弹层 -->
      <ocr-member-recognize-modal
        :visible="ocrVisible"
        @cancel="ocrVisible = false"
        @ok="handleOcrResult" />

      <!-- 重复会员确认弹窗 -->
      <a-modal
        :visible="duplicateVisible"
        :width="400"
        :footer="null"
        :maskClosable="false"
        @cancel="duplicateVisible = false">
        <template slot="title">
          <span>会员信息已存在</span>
        </template>
        <div style="margin-bottom:12px">
          <p>联系人 <b>{{ duplicateInfo.contacts }}</b> 或电话 <b>{{ duplicateInfo.telephone }}</b> 已存在会员：</p>
          <p style="font-size:16px;font-weight:bold;color:#1890ff">卡号：{{ duplicateInfo.supplier }}</p>
        </div>
        <p>是否更新该会员信息？</p>
        <div style="text-align:right;margin-top:16px">
          <a-button @click="handleDuplicateNew" style="margin-right:8px">新增（新卡号）</a-button>
          <a-button type="primary" @click="handleDuplicateUpdate">更新（补充信息）</a-button>
        </div>
      </a-modal>
    </a-modal>
  </div>
</template>
<script>
  import pick from 'lodash.pick'
  import {addSupplier,editSupplier,checkSupplier } from '@/api/api'
  import {postAction} from '@/api/manage'
  import {autoJumpNextInput} from "@/utils/util"
  import {mixinDevice} from '@/utils/mixin'
  import OcrMemberRecognizeModal from './OcrMemberRecognizeModal'
  export default {
    name: "MemberModal",
    mixins: [mixinDevice],
    components: {
      OcrMemberRecognizeModal
    },
    data () {
      return {
        title:"操作",
        visible: false,
        ocrVisible: false,
        duplicateVisible: false,
        model: {},
        isReadOnly: false,
        isNewRecord: true,
        duplicateInfo: {},
        phoneList: [''],
        addressList: [''],
        labelCol: {
          xs: { span: 24 },
          sm: { span: 4 },
        },
        wrapperCol: {
          xs: { span: 24 },
          sm: { span: 20 },
        },
        confirmLoading: false,
        form: this.$form.createForm(this),
        validatorRules:{
          supplier:{
            rules: [
              { required: true, message: '请输入会员卡号!' },
              { min: 2, max: 60, message: '长度在 2 到 60 个字符', trigger: 'blur' },
              { validator: this.validateSupplierName}
            ]
          }
        },
      }
    },
    methods: {
      add () {
        var that = this;
        this.edit({});
        // 自动生成会员卡号
        postAction('/supplier/generateMemberId', {}).then(function(res) {
          if (res && res.code === 200 && res.data && res.data.memberId) {
            that.model.supplier = res.data.memberId;
            that.form.setFieldsValue({ supplier: res.data.memberId });
          }
        });
      },
      edit (record) {
        this.form.resetFields();
        this.model = Object.assign({}, record);
        this.isNewRecord = !record.id;
        this.duplicateInfo = {};
        this.duplicateVisible = false;
        this.visible = true;
        // 初始化电话列表
        var tel = record.telephone || ''
        this.phoneList = tel ? tel.split('\n').filter(function(v){ return v.trim() }) : ['']
        if (this.phoneList.length === 0) this.phoneList = ['']
        // 初始化地址列表
        var addr = record.address || ''
        this.addressList = addr ? addr.split('\n').filter(function(v){ return v.trim() }) : ['']
        if (this.addressList.length === 0) this.addressList = ['']
        this.$nextTick(() => {
          // 新增模式不设置supplier，等generateMemberId异步返回后填入
          var fieldsToSet = {};
          if (this.model.supplier) fieldsToSet.supplier = this.model.supplier;
          if (this.model.contacts) fieldsToSet.contacts = this.model.contacts;
          if (this.model.telephone) fieldsToSet.telephone = this.model.telephone;
          if (this.model.email) fieldsToSet.email = this.model.email;
          if (this.model.phoneNum) fieldsToSet.phoneNum = this.model.phoneNum;
          if (this.model.wechatAccount) fieldsToSet.wechatAccount = this.model.wechatAccount;
          if (this.model.douyinAccount) fieldsToSet.douyinAccount = this.model.douyinAccount;
          if (this.model.alipayAccount) fieldsToSet.alipayAccount = this.model.alipayAccount;
          if (this.model.otherAccount) fieldsToSet.otherAccount = this.model.otherAccount;
          if (this.model.description) fieldsToSet.description = this.model.description;
          this.form.setFieldsValue(fieldsToSet);
          autoJumpNextInput('memberModal')
        });
      },
      checkDuplicate() {
        // 仅新增模式检查重复
        if (!this.isNewRecord) return;
        if (this.duplicateVisible) return;

        var that = this;
        var contacts = this.form.getFieldValue('contacts');
        if (!contacts && this.phoneList[0]) {
          contacts = '';
        }
        // 至少要有联系人或电话
        if (!contacts && !this.phoneList[0]) return;

        var telephone = this.phoneList[0] ? this.phoneList[0].trim() : '';
        if (!contacts && !telephone) return;

        // 防抖
        if (this._dupTimer) clearTimeout(this._dupTimer);
        this._dupTimer = setTimeout(function() {
          postAction('/supplier/checkMemberDuplicate', {
            contacts: contacts,
            telephone: telephone
          }).then(function(res) {
            if (res && res.code === 200 && res.data && res.data.exists) {
              that.duplicateInfo = res.data;
              that.duplicateVisible = true;
            }
          });
        }, 600);
      },
      handleDuplicateNew() {
        // 重新生成新卡号
        var that = this;
        this.duplicateVisible = false;
        this.model.id = null;
        this.isNewRecord = true;
        postAction('/supplier/generateMemberId', {}).then(function(res) {
          if (res && res.code === 200 && res.data && res.data.memberId) {
            that.model.supplier = res.data.memberId;
            that.form.setFieldsValue({ supplier: res.data.memberId });
          }
        });
      },
      handleDuplicateUpdate() {
        // 切换到编辑已有会员模式
        this.duplicateVisible = false;
        this.model.id = this.duplicateInfo.id;
        this.model.supplier = this.duplicateInfo.supplier;
        this.isNewRecord = false;
        // 更新显示卡号
        this.form.setFieldsValue({ supplier: this.duplicateInfo.supplier });
        this.$message.info('将更新会员 [' + this.duplicateInfo.supplier + '] 的信息');
      },
      addPhone() {
        this.phoneList.push('')
      },
      removePhone(index) {
        if (this.phoneList.length > 1) {
          this.phoneList.splice(index, 1)
        }
      },
      addAddress() {
        this.addressList.push('')
      },
      removeAddress(index) {
        if (this.addressList.length > 1) {
          this.addressList.splice(index, 1)
        }
      },
      close () {
        this.$emit('close');
        this.visible = false;
      },
      handleOk () {
        const that = this;
        this.form.validateFields((err, values) => {
          if (!err) {
            that.confirmLoading = true;
            let formData = Object.assign(this.model, values);
            if(this.model.beginNeedGet && this.model.beginNeedPay) {
              that.$message.warn("期初应收和期初应付不能同时输入");
              that.confirmLoading = false;
              return;
            }
            formData.type = "会员";
            // 多电话合并
            var phones = that.phoneList.filter(function(v){ return v.trim() })
            formData.telephone = phones.join('\n')
            formData.phoneNum = formData.telephone
            // 多地址合并
            var addrs = that.addressList.filter(function(v){ return v.trim() })
            formData.address = addrs.join('\n')
            let obj;
            if(!this.model.id){
              obj=addSupplier(formData);
            }else{
              obj=editSupplier(formData);
            }
            obj.then((res)=>{
              if(res.code === 200){
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
      openOcrRecognize() {
        this.ocrVisible = true
      },
      handleOcrResult(result) {
        var that = this
        // 联系人
        if (result.contacts) {
          this.model.contacts = result.contacts
          this.form.setFieldsValue({ contacts: result.contacts })
        }
        // 电话：填到第一个空位
        if (result.telephone) {
          var phones = result.telephone.split('\n').filter(function(v){ return v.trim() })
          phones.forEach(function(p) {
            var emptyIdx = -1
            for (var i = 0; i < that.phoneList.length; i++) {
              if (!that.phoneList[i].trim()) { emptyIdx = i; break }
            }
            if (emptyIdx >= 0) {
              that.phoneList.splice(emptyIdx, 1, p)
            } else {
              that.phoneList.push(p)
            }
          })
        }
        // 邮箱
        if (result.email) {
          this.model.email = result.email
          this.form.setFieldsValue({ email: result.email })
        }
        // 地址：追加到新行
        if (result.address) {
          var addrs = result.address.split('\n').filter(function(v){ return v.trim() })
          addrs.forEach(function(a) {
            var emptyIdx = -1
            for (var i = 0; i < that.addressList.length; i++) {
              if (!that.addressList[i].trim()) { emptyIdx = i; break }
            }
            if (emptyIdx >= 0) {
              that.addressList.splice(emptyIdx, 1, a)
            } else {
              that.addressList.push(a)
            }
          })
        }
        this.ocrVisible = false
        this.$message.success('地址识别信息已填充')
      },
      validateSupplierName(rule, value, callback){
        let params = {
          name: value,
          type: '会员',
          id: this.model.id?this.model.id:0
        };
        checkSupplier(params).then((res)=>{
          if(res && res.code===200) {
            if(!res.data.status){
              callback();
            } else {
              callback("会员卡号已经存在");
            }
          } else {
            callback(res.data);
          }
        });
      }
    }
  }
</script>
<style scoped>

</style>
