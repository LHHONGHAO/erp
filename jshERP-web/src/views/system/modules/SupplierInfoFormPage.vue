<template>
  <div class="sif-root">
    <!-- 手机端标准页面：固定顶栏 + 可滚动内容区 + 固定底栏 -->
    <mobile-modal-page
      class="sif-page"
      :visible="visible"
      :title="pageTitle"
      @close="handleCancel">
      <div class="sif-body">
        <a-form :form="form" id="supplierInfoForm">
          <!-- ==================== 会员 ==================== -->
          <template v-if="isMember">
            <a-form-item label="会员卡号">
              <a-input placeholder="请输入会员卡号" v-decorator.trim="['supplier', validatorRules.supplier]" />
            </a-form-item>
            <a-form-item label="联系人">
              <div class="sif-inline">
                <a-input placeholder="请输入联系人" v-decorator.trim="['contacts']" @blur="checkDuplicate" />
                <a-button size="small" type="primary" class="sif-inline-btn" @click="ocrVisible = true">
                  <a-icon type="environment" /> 地址识别
                </a-button>
              </div>
            </a-form-item>
            <a-form-item label="电话">
              <div class="sif-list">
                <div v-for="(phone, idx) in phoneList" :key="'phone' + idx" class="sif-list-row">
                  <a-input v-model="phoneList[idx]" placeholder="请输入电话" @blur="checkDuplicate" />
                  <a-button v-if="idx === phoneList.length - 1" size="small" icon="plus" class="sif-mini-btn" @click="addPhone" />
                  <a-button v-if="phoneList.length > 1" size="small" icon="minus" class="sif-mini-btn" @click="removePhone(idx)" />
                </div>
              </div>
            </a-form-item>
            <a-form-item label="电子邮箱">
              <a-input placeholder="请输入电子邮箱" v-decorator.trim="['email']" />
            </a-form-item>
            <a-form-item label="微信账户">
              <a-input placeholder="请输入微信账户" v-decorator.trim="['wechatAccount']" />
            </a-form-item>
            <a-form-item label="抖音账户">
              <a-input placeholder="请输入抖音账户" v-decorator.trim="['douyinAccount']" />
            </a-form-item>
            <a-form-item label="支付宝账户">
              <a-input placeholder="请输入支付宝账户" v-decorator.trim="['alipayAccount']" />
            </a-form-item>
            <a-form-item label="其它账户">
              <a-input placeholder="请输入其它账户" v-decorator.trim="['otherAccount']" />
            </a-form-item>
            <a-form-item label="备注">
              <a-textarea :rows="2" placeholder="请输入备注" v-decorator.trim="['description']" />
            </a-form-item>
            <a-form-item label="联系地址">
              <div class="sif-list">
                <div v-for="(addr, idx) in addressList" :key="'addr' + idx" class="sif-list-row">
                  <a-input v-model="addressList[idx]" placeholder="请输入联系地址" />
                  <a-button v-if="idx === addressList.length - 1" size="small" icon="plus" class="sif-mini-btn" @click="addAddress" />
                  <a-button v-if="addressList.length > 1" size="small" icon="minus" class="sif-mini-btn" @click="removeAddress(idx)" />
                </div>
              </div>
            </a-form-item>
          </template>

          <!-- ==================== 客户 / 供应商 ==================== -->
          <template v-else>
            <a-form-item label="名称">
              <a-input placeholder="请输入名称" v-decorator.trim="['supplier', validatorRules.supplier]" />
            </a-form-item>
            <a-form-item label="联系人">
              <a-input placeholder="请输入联系人" v-decorator.trim="['contacts']" />
            </a-form-item>
            <a-form-item label="手机号码">
              <a-input placeholder="请输入手机号码" v-decorator.trim="['telephone']" />
            </a-form-item>
            <a-form-item label="联系电话">
              <a-input placeholder="请输入联系电话" v-decorator.trim="['phoneNum']" />
            </a-form-item>
            <a-form-item label="电子邮箱">
              <a-input placeholder="请输入电子邮箱" v-decorator.trim="['email']" />
            </a-form-item>
            <a-form-item label="传真">
              <a-input placeholder="请输入传真" v-decorator.trim="['fax']" />
            </a-form-item>
            <a-form-item :label="isVendor ? '期初应付' : '期初应收'">
              <a-input :placeholder="isVendor ? '请输入期初应付' : '请输入期初应收'" v-decorator.trim="[isVendor ? 'beginNeedPay' : 'beginNeedGet']" />
            </a-form-item>
            <a-form-item :label="isVendor ? '期末应付' : '期末应收'">
              <a-input placeholder="自动计算" :readOnly="true" v-decorator.trim="[isVendor ? 'allNeedPay' : 'allNeedGet']" />
            </a-form-item>
            <a-form-item label="纳税人识别号">
              <a-input placeholder="请输入纳税人识别号" v-decorator.trim="['taxNum']" />
            </a-form-item>
            <a-form-item label="税率(%)">
              <a-input-number style="width:100%" placeholder="请输入税率" v-decorator.trim="['taxRate']" />
            </a-form-item>
            <a-form-item label="开户行">
              <a-input placeholder="请输入开户行" v-decorator.trim="['bankName']" />
            </a-form-item>
            <a-form-item label="账号">
              <a-input placeholder="请输入账号" v-decorator.trim="['accountNumber']" />
            </a-form-item>
            <a-form-item label="地址">
              <a-input placeholder="请输入地址" v-decorator.trim="['address']" />
            </a-form-item>
            <a-form-item label="排序">
              <a-input placeholder="请输入排序" v-decorator.trim="['sort']" />
            </a-form-item>
            <a-form-item label="备注">
              <a-textarea :rows="2" placeholder="请输入备注" v-decorator.trim="['description']" />
            </a-form-item>
          </template>
        </a-form>
      </div>
      <template slot="footer">
        <div class="sif-footer">
          <a-button class="sif-footer-btn" @click="handleCancel">取消</a-button>
          <a-button v-if="!readOnly" class="sif-footer-btn" type="primary" :loading="confirmLoading" @click="handleOk">保存</a-button>
        </div>
      </template>
    </mobile-modal-page>

    <!-- 地址识别（仅会员） -->
    <ocr-member-recognize-modal
      v-if="isMember"
      :visible="ocrVisible"
      :z-index="3000"
      @cancel="ocrVisible = false"
      @ok="handleOcrResult" />

    <!-- 重复会员确认 -->
    <a-modal
      v-if="isMember"
      :visible="duplicateVisible"
      :width="400"
      :zIndex="3000"
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
  </div>
</template>

<script>
  import { addSupplier, editSupplier, checkSupplier } from '@/api/api'
  import { postAction } from '@/api/manage'
  import MobileModalPage from '@/components/mobile/MobileModalPage'
  import OcrMemberRecognizeModal from './OcrMemberRecognizeModal'

  /**
   * 标准信息添加页（手机端全屏）
   * 会员 / 客户 / 供应商 共用同一页面：固定顶栏、固定底栏、内容区滚动。
   * 会员额外支持地址识别、多电话、多地址与重复会员提示。
   */
  export default {
    name: 'SupplierInfoFormPage',
    components: { MobileModalPage, OcrMemberRecognizeModal },
    props: {
      visible: { type: Boolean, default: false },
      /** 会员 / 客户 / 供应商 */
      type: { type: String, default: '会员' },
      record: { type: Object, default: () => ({}) },
      readOnly: { type: Boolean, default: false }
    },
    data () {
      return {
        model: {},
        isNewRecord: true,
        confirmLoading: false,
        ocrVisible: false,
        duplicateVisible: false,
        duplicateInfo: {},
        phoneList: [''],
        addressList: [''],
        form: this.$form.createForm(this)
      }
    },
    computed: {
      validatorRules () {
        return {
          supplier: {
            rules: [
              { required: true, message: this.isMember ? '请输入会员卡号!' : '请输入名称!' },
              { min: 2, max: 60, message: '长度在 2 到 60 个字符', trigger: 'blur' },
              { validator: this.validateSupplierName }
            ]
          }
        }
      },
      isMember () {
        return this.type === '会员'
      },
      isVendor () {
        return this.type === '供应商'
      },
      pageTitle () {
        return (this.isNewRecord ? '新增' : '编辑') + this.type
      }
    },
    watch: {
      visible (val) {
        if (val) {
          this.initPage()
        }
      }
    },
    methods: {
      initPage () {
        this.form.resetFields()
        this.model = Object.assign({}, this.record || {})
        this.isNewRecord = !this.model.id
        this.duplicateInfo = {}
        this.duplicateVisible = false
        this.ocrVisible = false

        var tel = this.model.telephone || ''
        this.phoneList = tel ? tel.split('\n').filter(function (v) { return v.trim() }) : ['']
        if (this.phoneList.length === 0) this.phoneList = ['']

        var addr = this.model.address || ''
        this.addressList = addr ? addr.split('\n').filter(function (v) { return v.trim() }) : ['']
        if (this.addressList.length === 0) this.addressList = ['']

        this.$nextTick(() => {
          var fields = {}
          var keys = ['supplier', 'contacts', 'email', 'wechatAccount', 'douyinAccount',
            'alipayAccount', 'otherAccount', 'description', 'phoneNum', 'fax',
            'beginNeedGet', 'beginNeedPay', 'allNeedGet', 'allNeedPay', 'taxNum',
            'taxRate', 'bankName', 'accountNumber', 'sort']
          keys.forEach(key => {
            if (this.model[key] !== undefined && this.model[key] !== null) {
              fields[key] = this.model[key]
            }
          })
          // 客户/供应商地址为单行输入
          if (!this.isMember && this.model.address) {
            fields.address = this.model.address
          }
          this.form.setFieldsValue(fields)
        })

        // 新增会员：自动生成会员卡号
        if (this.isMember && this.isNewRecord) {
          this.generateMemberId()
        }
      },
      generateMemberId () {
        var that = this
        postAction('/supplier/generateMemberId', {}).then(function (res) {
          if (res && res.code === 200 && res.data && res.data.memberId) {
            that.model.supplier = res.data.memberId
            that.form.setFieldsValue({ supplier: res.data.memberId })
          }
        })
      },
      validateSupplierName (rule, value, callback) {
        let params = {
          name: value,
          type: this.type,
          id: this.model.id ? this.model.id : 0
        }
        checkSupplier(params).then((res) => {
          if (res && res.code === 200) {
            if (!res.data.status) {
              callback()
            } else {
              callback(this.isMember ? '会员卡号已经存在' : '名称已经存在')
            }
          } else {
            callback(res.data)
          }
        })
      },
      checkDuplicate () {
        if (!this.isMember || !this.isNewRecord) return
        if (this.duplicateVisible) return

        var that = this
        var contacts = this.form.getFieldValue('contacts') || ''
        var telephone = this.phoneList[0] ? this.phoneList[0].trim() : ''
        if (!contacts && !telephone) return

        if (this._dupTimer) clearTimeout(this._dupTimer)
        this._dupTimer = setTimeout(function () {
          postAction('/supplier/checkMemberDuplicate', {
            contacts: contacts,
            telephone: telephone
          }).then(function (res) {
            if (res && res.code === 200 && res.data && res.data.exists) {
              that.duplicateInfo = res.data
              that.duplicateVisible = true
            }
          })
        }, 600)
      },
      handleDuplicateNew () {
        this.duplicateVisible = false
        this.model.id = null
        this.isNewRecord = true
        this.generateMemberId()
      },
      handleDuplicateUpdate () {
        this.duplicateVisible = false
        this.model.id = this.duplicateInfo.id
        this.model.supplier = this.duplicateInfo.supplier
        this.isNewRecord = false
        this.form.setFieldsValue({ supplier: this.duplicateInfo.supplier })
        this.$message.info('将更新会员 [' + this.duplicateInfo.supplier + '] 的信息')
      },
      addPhone () {
        this.phoneList.push('')
      },
      removePhone (index) {
        if (this.phoneList.length > 1) {
          this.phoneList.splice(index, 1)
        }
      },
      addAddress () {
        this.addressList.push('')
      },
      removeAddress (index) {
        if (this.addressList.length > 1) {
          this.addressList.splice(index, 1)
        }
      },
      handleOcrResult (result) {
        if (result.contacts) {
          this.model.contacts = result.contacts
          this.form.setFieldsValue({ contacts: result.contacts })
        }
        if (result.telephone) {
          var phones = result.telephone.split('\n').filter(function (v) { return v.trim() })
          phones.forEach(p => {
            var emptyIdx = -1
            for (var i = 0; i < this.phoneList.length; i++) {
              if (!this.phoneList[i].trim()) { emptyIdx = i; break }
            }
            if (emptyIdx >= 0) {
              this.phoneList.splice(emptyIdx, 1, p)
            } else {
              this.phoneList.push(p)
            }
          })
        }
        if (result.email) {
          this.model.email = result.email
          this.form.setFieldsValue({ email: result.email })
        }
        if (result.address) {
          var addrs = result.address.split('\n').filter(function (v) { return v.trim() })
          addrs.forEach(a => {
            var emptyIdx = -1
            for (var i = 0; i < this.addressList.length; i++) {
              if (!this.addressList[i].trim()) { emptyIdx = i; break }
            }
            if (emptyIdx >= 0) {
              this.addressList.splice(emptyIdx, 1, a)
            } else {
              this.addressList.push(a)
            }
          })
        }
        this.ocrVisible = false
        this.$message.success('地址识别信息已填充')
      },
      handleOk () {
        if (this.readOnly) return
        this.form.validateFields((err, values) => {
          if (err) return
          let formData = Object.assign(this.model, values)
          if (this.model.beginNeedGet && this.model.beginNeedPay) {
            this.$message.warn('期初应收和期初应付不能同时输入')
            return
          }
          formData.type = this.type
          if (this.isMember) {
            var phones = this.phoneList.filter(function (v) { return v.trim() })
            formData.telephone = phones.join('\n')
            formData.phoneNum = formData.telephone
            var addrs = this.addressList.filter(function (v) { return v.trim() })
            formData.address = addrs.join('\n')
          }
          this.confirmLoading = true
          let request = this.model.id ? editSupplier(formData) : addSupplier(formData)
          request.then((res) => {
            if (res.code === 200) {
              this.$emit('ok', formData)
              this.handleCancel()
            } else {
              this.$message.warning(res.data.message)
            }
          }).finally(() => {
            this.confirmLoading = false
          })
        })
      },
      handleCancel () {
        this.$emit('close')
      }
    }
  }
</script>

<style lang="less">
  /* 标准页面外壳沿用 MobileModalPage，只在本页内收敛行高与输入框高度 */
  .sif-page {
    .mmp-body {
      background: #f4f6fb;
    }
  }

  .sif-body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    padding: 10px 12px;
  }

  .sif-body .ant-form {
    padding: 0 12px;
    background: #fff;
    border-radius: 12px;
    overflow: hidden;
  }

  /* 表单项：标签固定宽度靠右，输入区占满剩余宽度，行高紧凑
     标签与输入内容统一字体大小和行高，首行底部对齐 */
  .sif-page .ant-form-item {
    display: flex;
    align-items: flex-start;
    margin-bottom: 0;
    padding: 5px 0;
    border-bottom: 1px solid #eef1f6;
  }
  .sif-page .ant-form-item:last-child {
    border-bottom: none;
  }
  .sif-page .ant-form-item-label {
    flex: 0 0 96px;
    width: 96px !important;
    max-width: 96px;
    padding-right: 8px;
    text-align: right;
    line-height: 32px;
  }
  .sif-page .ant-form-item-label > label {
    color: #64748b;
    font-size: 16px;
    line-height: 32px;
    white-space: nowrap;
  }
  .sif-page .ant-form-item-control-wrapper {
    flex: 1 1 auto;
    width: auto !important;
    min-width: 0;
  }

  /* 输入框：无边框、降低高度 */
  .sif-page .ant-input,
  .sif-page .ant-input-number,
  .sif-page .ant-select-selection,
  .sif-page .ant-select-selection__rendered {
    border: none;
    border-radius: 0;
    background: transparent;
    box-shadow: none;
  }
  .sif-page .ant-input {
    height: 32px;
    padding: 0;
    line-height: 32px;
    font-size: 16px !important;
    background: transparent;
    border: none;
    box-shadow: none;
  }
  .sif-page textarea.ant-input {
    height: auto;
    min-height: 48px;
    padding: 4px 0;
    line-height: 24px;
  }
  .sif-page .ant-input-number {
    width: 100%;
  }
  .sif-page .ant-input-number-input {
    height: 32px;
    padding: 0;
    line-height: 32px;
  }
  .sif-page .ant-form-explain {
    margin-top: 2px;
    font-size: 12px;
  }

  .sif-inline {
    display: flex;
    align-items: center;
    width: 100%;
  }
  .sif-inline .ant-input {
    flex: 1 1 auto;
    min-width: 0;
  }
  .sif-inline-btn {
    flex: 0 0 auto;
    height: 32px;
    margin-left: 8px;
    border-radius: 8px;
  }

  .sif-list {
    width: 100%;
  }
  .sif-list-row {
    display: flex;
    align-items: center;
    width: 100%;
  }
  .sif-list-row + .sif-list-row {
    margin-top: 2px;
  }
  .sif-list-row .ant-input {
    flex: 1 1 auto;
    min-width: 0;
  }
  .sif-mini-btn {
    flex: 0 0 auto;
    height: 32px;
    margin-left: 6px;
    border-radius: 8px;
  }

  /* 底栏：取消 + 保存，固定高度 */
  .sif-footer {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
  }
  .sif-footer-btn {
    flex: 1 1 0;
    height: 44px;
    border-radius: 12px;
    font-size: 15px;
  }
</style>
