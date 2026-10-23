/**
 * 新增修改完成调用 modalFormOk方法 编辑弹框组件ref定义为modalForm
 * 高级查询按钮调用 superQuery方法  高级查询组件ref定义为superQueryModal
 * data中url定义 list为查询列表  delete为删除单条记录  deleteBatch为批量删除
 */
import { filterObj, getMpListShort, getNowFormatStr } from '@/utils/util'
import { deleteAction, getAction, postAction, downFile, downFilePost, getFileAccessHttpUrl } from '@/api/manage'
import Vue from 'vue'
import { ACCESS_TOKEN } from "@/store/mutation-types"
import {mixinDevice} from '@/utils/mixin.js'

export const JeecgListMixin = {
  mixins: [mixinDevice],
  data(){
    return {
      //token header
      tokenHeader: {'X-Access-Token': Vue.ls.get(ACCESS_TOKEN)},
      /*卡片样式 */
      cardStyle: '',
      /* 查询条件-请不要在queryParam中声明非字符串值的属性 */
      queryParam: {},
      /* 数据源 */
      dataSource:[],
      // 实际列
      columns:[],
      // 实际索引
      settingDataIndex:[],
      /* 分页参数 */
      ipagination:{
        current: 1,
        pageSize: 10,
        pageSizeOptions: ['10', '20', '30', '50', '100'],
        showTotal: (total, range) => {
          return range[0] + "-" + range[1] + " 共" + total + "条"
        },
        showQuickJumper: true,
        showSizeChanger: true,
        total: 0
      },
      /* 控制table高度 */
      scroll: {},
      /* 排序参数 */
      isorter:{
        column: 'createTime',
        order: 'desc',
      },
      /* 筛选参数 */
      filters: {},
      /* table加载状态 */
      loading:false,
      /* table选中keys*/
      selectedRowKeys: [],
      /* table选中records*/
      selectionRows: [],
      /* 查询折叠 */
      toggleSearchStatus:false,
      /* 高级查询条件生效状态 */
      superQueryFlag:false,
      /* 高级查询条件 */
      superQueryParams: '',
      /** 高级查询拼接方式 */
      superQueryMatchType: 'and',
      /** 是否加载时就执行 */
      disableMixinCreated: false,
      /* 按钮权限 */
      btnEnableList: '',
    }
  },
  created() {
    if(this.isDesktop()) {
      this.cardStyle = 'height:' + (document.documentElement.clientHeight-100) + 'px'
    }
    if(!this.disableMixinCreated){
      //console.log(' -- mixin created -- ')
      this.loadData();
      //初始化字典配置 在自己页面定义
      this.initDictConfig();
      //初始化按钮权限
      this.initActiveBtnStr();
    }
  },
  mounted () {
    this.initScroll()
  },
  computed: {
    //列设置弹窗中是否已全部勾选（序号列常驻，不参与判断）
    columnsAllChecked() {
      const allKeys = (this.defColumns || []).map(item => item.dataIndex).filter(k => !!k && k !== 'rowIndex')
      return allKeys.length > 0 && allKeys.every(k => (this.settingDataIndex || []).includes(k))
    }
  },
  methods:{
    loadData(arg) {
      if(!this.url.list){
        this.$message.error("请设置url.list属性!")
        return
      }
      //加载数据 若传入参数1则加载第一页的内容
      if (arg === 1) {
        this.ipagination.current = 1
      }
      let params = this.getQueryParams() //查询条件
      this.loading = true
      getAction(this.url.list, params).then((res) => {
        if (res.code===200) {
          this.dataSource = res.data.rows
          this.ipagination.total = res.data.total
          this.tableAddTotalRow(this.columns, this.dataSource)
        } else if(res.code===510){
          this.$message.warning(res.data)
        } else {
          this.$message.warning(res.data.message)
        }
        this.loading = false
        this.onClearSelected()
      })
    },
    initDictConfig(){
      //console.log("--这是一个假的方法!")
    },
    handleSuperQuery(params, matchType) {
      //高级查询方法
      if(!params){
        this.superQueryParams=''
        this.superQueryFlag = false
      }else{
        this.superQueryFlag = true
        this.superQueryParams=JSON.stringify(params)
        this.superQueryMatchType = matchType
      }
      this.loadData(1)
    },
    getQueryParams() {
      //获取查询条件
      let sqp = {}
      if(this.superQueryParams){
        sqp['superQueryParams']=encodeURI(this.superQueryParams)
        sqp['superQueryMatchType'] = this.superQueryMatchType
      }
      let searchObj = {}
      searchObj.search = JSON.stringify(this.queryParam);
      var param = Object.assign(sqp, searchObj, this.isorter ,this.filters);
      param.field = this.getQueryField();
      param.currentPage = this.ipagination.current;
      param.pageSize = this.ipagination.pageSize;
      return filterObj(param);
    },
    getQueryField() {
      var str = "id,";
      this.columns.forEach(function (value) {
        str += "," + value.dataIndex;
      });
      return str;
    },
    onSelectChange(selectedRowKeys, selectionRows) {
      this.selectedRowKeys = selectedRowKeys;
      this.selectionRows = selectionRows;
    },
    onClearSelected() {
      this.selectedRowKeys = []
      this.selectionRows = []
    },
    searchQuery() {
      this.loadData(1);
    },
    superQuery() {
      this.$refs.superQueryModal.show();
    },
    searchReset() {
      this.queryParam = {}
      this.loadData(1);
    },
    batchSetStatus: function (status) {
      if(!this.url.batchSetStatusUrl){
        this.$message.error("请设置url.batchSetStatusUrl属性!")
        return
      }
      if (this.selectedRowKeys.length <= 0) {
        this.$message.warning('请选择一条记录！');
        return;
      } else {
        var ids = "";
        for (var a = 0; a < this.selectedRowKeys.length; a++) {
          ids += this.selectedRowKeys[a] + ",";
        }
        var that = this;
        this.$confirm({
          title: "确认操作",
          content: "是否操作选中数据?",
          onOk: function () {
            that.loading = true;
            postAction(that.url.batchSetStatusUrl, {status: status, ids: ids}).then((res) => {
              if(res.code === 200){
                that.loadData()
              } else {
                that.$message.warning(res.data.message);
              }
            }).finally(() => {
              that.loading = false;
            });
          }
        });
      }
    },
    batchDel: function () {
      if(!this.url.deleteBatch){
        this.$message.error("请设置url.deleteBatch属性!")
        return
      }
      if (this.selectedRowKeys.length <= 0) {
        this.$message.warning('请选择一条记录！');
        return;
      } else {
        var ids = "";
        for (var a = 0; a < this.selectedRowKeys.length; a++) {
          ids += this.selectedRowKeys[a] + ",";
        }
        var that = this;
        this.$confirm({
          title: "确认删除",
          content: "是否删除选中数据?",
          onOk: function () {
            that.loading = true;
            deleteAction(that.url.deleteBatch, {ids: ids}).then((res) => {
              if(res.code === 200){
                that.loadData()
              } else {
                that.$message.warning(res.data.message);
              }
            }).finally(() => {
              that.loading = false;
            });
          }
        });
      }
    },
    handleDelete: function (id) {
      if(!this.url.delete){
        this.$message.error("请设置url.delete属性!")
        return
      }
      var that = this;
      deleteAction(that.url.delete, {id: id}).then((res) => {
        if(res.code === 200){
          that.loadData();
        } else {
          that.$message.warning(res.data.message);
        }
      });
    },
    handleEdit: function (record) {
      this.$refs.modalForm.edit(record);
      this.$refs.modalForm.title = "编辑";
      this.$refs.modalForm.disableSubmit = false;
    },
    handleAdd: function () {
      this.$refs.modalForm.add();
      this.$refs.modalForm.title = "新增";
      this.$refs.modalForm.disableSubmit = false;
    },
    handleTableChange(pagination, filters, sorter) {
      //分页、排序、筛选变化时触发
      if (Object.keys(sorter).length > 0) {
        if(sorter.order) {
          this.isorter.column = sorter.field
          this.isorter.order = 'ascend' === sorter.order ? 'asc' : 'desc'
        } else {
          this.isorter.column = 'createTime'
          this.isorter.order = 'desc'
        }
      }
      if(pagination && pagination.current) {
        this.ipagination = pagination;
      }
      this.loadData();
    },
    handleToggleSearch(){
      this.toggleSearchStatus = !this.toggleSearchStatus;
    },
    // 给popup查询使用(查询区域不支持回填多个字段，限制只返回一个字段)
    getPopupField(fields){
      return fields.split(',')[0]
    },
    modalFormOk() {
      // 新增/修改 成功时，重载列表
      this.loadData()
    },
    modalFormClose() {
      // 关闭页面时，重载列表
      this.loadData()
    },
    handleDetail:function(record, type, prefixNo){
      this.$refs.modalDetail.show(record, type, prefixNo);
      this.$refs.modalDetail.title=type+"-详情";
    },
    //加载初始化列
    initColumnsSetting(){
      let columnsStr = Vue.ls.get(this.pageName)
      let saved = null
      if (Array.isArray(columnsStr)) {
        saved = columnsStr
      } else if (typeof columnsStr === 'string' && columnsStr.length > 0) {
        // 兼容只勾选一列的历史保存值（无逗号）
        saved = columnsStr.split(',')
      }
      if (saved) {
        const allKeys = (this.defColumns || []).map(item => item.dataIndex)
        const valid = saved.filter(k => !!k && allKeys.indexOf(k) > -1)
        this.settingDataIndex = valid.length > 0 ? valid : this.defDataIndex
      } else {
        this.settingDataIndex = this.defDataIndex
      }
      this.columns = this.defColumns.filter(item => {
        return this.settingDataIndex.includes(item.dataIndex)
      })
    },
    //列设置更改事件
    onColChange (checkedValues) {
      this.columns = this.defColumns.filter(item => {
        return checkedValues.includes(item.dataIndex)
      })
      let columnsStr = checkedValues.join()
      Vue.ls.set(this.pageName, columnsStr)
      this.$nextTick(() => this.syncTableWidth())
    },
    //恢复默认
    handleRestDefault() {
      Vue.ls.remove(this.pageName)
      this.initColumnsSetting()
      this.$nextTick(() => this.syncTableWidth())
    },
    //全选列 / 取消全选（序号列常驻，保证列设置入口不消失）
    handleSelectAllColumns(e) {
      const checked = !!(e && e.target && e.target.checked)
      const allKeys = (this.defColumns || []).map(item => item.dataIndex).filter(k => !!k)
      const checkedKeys = checked ? allKeys : allKeys.filter(k => k === 'rowIndex')
      this.settingDataIndex = checkedKeys
      this.onColChange(checkedKeys)
    },
    /* 导出 */
    handleExportXls2(){
      let paramsStr = encodeURI(JSON.stringify(this.getQueryParams()));
      let url = `${window._CONFIG['domianURL']}/${this.url.exportXlsUrl}?paramsStr=${paramsStr}`;
      window.location.href = url;
    },
    //通过get方式导出Excel
    handleExportXls(fileName){
      if(!fileName || typeof fileName != "string"){
        fileName = "导出文件"
      }
      let param = {...this.queryParam};
      if(this.selectedRowKeys && this.selectedRowKeys.length>0){
        param['selections'] = this.selectedRowKeys.join(",")
      }
      console.log("导出参数",param)
      downFile(this.url.exportXlsUrl,param).then((data)=>{
        if (!data) {
          this.$message.warning("文件下载失败")
          return
        }
        if (typeof window.navigator.msSaveBlob !== 'undefined') {
          window.navigator.msSaveBlob(new Blob([data],{type: 'application/vnd.ms-excel'}), fileName+'.xls')
        }else{
          let url = window.URL.createObjectURL(new Blob([data],{type: 'application/vnd.ms-excel'}))
          let link = document.createElement('a')
          link.style.display = 'none'
          link.href = url
          link.setAttribute('download', fileName + '_' + getNowFormatStr()+'.xls')
          document.body.appendChild(link)
          link.click()
          document.body.removeChild(link); //下载完成移除元素
          window.URL.revokeObjectURL(url); //释放掉blob对象
        }
      })
    },
    //通过post方式导出Excel
    handleExportXlsPost(fileName, title, head, tip, list) {
      if(!fileName || typeof fileName != "string"){
        fileName = "导出文件"
      }
      let paramObj = {'title': title, 'head': head, 'tip': tip, 'list': list}
      console.log("导出参数", paramObj)
      downFilePost(paramObj).then((data)=>{
        if (!data) {
          this.$message.warning("文件下载失败")
          return
        }
        if (typeof window.navigator.msSaveBlob !== 'undefined') {
          window.navigator.msSaveBlob(new Blob([data],{type: 'application/vnd.ms-excel'}), fileName+'.xls')
        }else{
          let url = window.URL.createObjectURL(new Blob([data],{type: 'application/vnd.ms-excel'}))
          let link = document.createElement('a')
          link.style.display = 'none'
          link.href = url
          link.setAttribute('download', fileName + '_' + getNowFormatStr()+'.xls')
          document.body.appendChild(link)
          link.click()
          document.body.removeChild(link); //下载完成移除元素
          window.URL.revokeObjectURL(url); //释放掉blob对象
        }
      })
    },
    /* 导入 */
    handleImportExcel(info){
      this.confirmLoading = true
      if (info.file.status !== 'uploading') {
        console.log(info.file, info.fileList);
      }
      if (info.file.status === 'done') {
        if (info.file.response) {
          // this.$message.success(`${info.file.name} 文件上传成功`);
          if (info.file.response.code === 200) {
            this.$message.success(info.file.response.data || `${info.file.name} 文件上传成功`)
          } else {
            this.$message.warning(info.file.response.data, 8)
          }
          this.confirmLoading = false
          this.visible = false
          this.$emit('ok')
        } else {
          this.$message.error(`${info.file.name} ${info.file.response.data}.`);
          this.confirmLoading = false
        }
      } else if (info.file.status === 'error') {
        this.$message.error(`文件上传失败: ${info.file.msg} `)
        this.confirmLoading = false
      }
    },
    /* 图片预览 */
    getImgView(text){
      if(text && text.indexOf(",")>0){
        text = text.substring(0,text.indexOf(","))
      }
      return getFileAccessHttpUrl(text)
    },
    /* 文件下载 */
    uploadFile(text){
      if(!text){
        this.$message.warning("未知的文件")
        return;
      }
      if(text.indexOf(",")>0){
        text = text.substring(0,text.indexOf(","))
      }
      let url = getFileAccessHttpUrl(text)
      window.open(url);
    },
    /* 按钮权限 */
    initActiveBtnStr() {
      let btnStrList = Vue.ls.get('winBtnStrList'); //按钮功能列表 JSON字符串
      this.btnEnableList = ""; //按钮列表
      if (this.urlPath && btnStrList) {
        for (let i = 0; i < btnStrList.length; i++) {
          if (btnStrList[i].url === this.urlPath) {
            if (btnStrList[i].btnStr) {
              this.btnEnableList = btnStrList[i].btnStr
            }
          }
        }
      }
    },
    /* 初始化表格横向或纵向滚动 */
    initScroll() {
      if (this.isMobile()) {
        this.syncTableWidth()
        this.scroll = Object.assign({}, this.scroll, { y: '' })
      } else {
        let basicWidth = 150
        let basicLength = 274
        let searchWrapperDomLen=0, operatorDomLen=0
        //搜索区域
        let searchWrapperDom = document.getElementsByClassName('table-page-search-wrapper')
        //操作按钮区域
        let operatorDom = document.getElementsByClassName('table-operator')
        if(searchWrapperDom && searchWrapperDom[0]) {
          searchWrapperDomLen = searchWrapperDom[0].offsetHeight
        }
        if(operatorDom && operatorDom[0]) {
          operatorDomLen = operatorDom[0].offsetHeight+10
        }
        //表格宽度固定为列宽之和（小于容器时右侧留空），列宽不可计算时兜底为容器宽度
        let total = this.syncTableWidth()
        if (!total) {
          total = document.documentElement.clientWidth-basicWidth-64
        }
        this.scroll = Object.assign({}, this.scroll, {
          x: total,
          y: document.documentElement.clientHeight-searchWrapperDomLen-operatorDomLen-basicLength
        })
      }
    },
    /** 解析列宽：仅接受数字或像素字符串，其余返回 0 */
    parseColumnWidth(width) {
      if (typeof width === 'number') {
        return width > 0 ? Math.round(width) : 0
      }
      if (typeof width === 'string') {
        const str = width.trim()
        if (/^\d+(\.\d+)?(px)?$/.test(str)) {
          return Math.round(parseFloat(str))
        }
      }
      return 0
    },
    /** 选择列/展开列等非数据列占用的宽度（表头单元格比列定义多出的部分） */
    getExtraHeaderWidth(colCount) {
      const table = this.$refs && this.$refs.table
      const root = table && table.$el
      if (!root) return 0
      // 固定列在主表头中以占位单元格渲染，同样计入主表头宽度
      const head = root.querySelector('.ant-table-thead')
      if (!head) return 0
      const ths = head.querySelectorAll('tr:first-child > th')
      const count = (colCount === undefined ? (this.columns || []).length : colCount)
      const extra = ths.length - count
      if (extra <= 0) return 0
      let width = 0
      for (let i = 0; i < extra; i++) {
        width += Math.round(ths[i].getBoundingClientRect().width)
      }
      return width
    },
    /** 当前显示列的总宽度（含固定列）；任一列缺少数值列宽时返回 0 */
    getColumnsTotalWidth(cols) {
      const list = cols || this.columns || []
      if (list.length === 0) return 0
      let total = 0
      for (let i = 0; i < list.length; i++) {
        const width = this.parseColumnWidth(list[i].width)
        if (!width) return 0
        total += width
      }
      return total + this.getExtraHeaderWidth(list.length)
    },
    /**
     * 把表格宽度同步为“列宽之和”，实现固定宽度：
     * 拖拽某列只改变该列与表格总宽，其它列宽度不变（不足处右侧留空）
     * @returns {number} 同步后的表格宽度，无法计算时返回 0
     */
    syncTableWidth() {
      const total = this.getColumnsTotalWidth(this.columns)
      if (!total) return 0
      if (Math.round(Number((this.scroll || {}).x)) !== total) {
        //整体替换对象，确保表格收到新的 scroll 引用后重新渲染并应用固定宽度
        this.scroll = Object.assign({}, this.scroll, { x: total })
      }
      return total
    },
    //表头列宽拖拽（Excel 方式：只调整当前列，其它列宽不变，表格总宽随之增减）
    handleDrag(column){
      const self = this
      return {
        header: {
          cell: (h, props, children) => {
            const { key, ...restProps } = props
            const drag = h('span', {
              key: 'resize-handle',
              class: 'table-draggable-handle',
              style: {
                position: 'absolute',
                top: 0,
                bottom: 0,
                right: '-5px',
                width: '10px',
                zIndex: 3,
                cursor: 'col-resize'
              },
              attrs: { title: '拖动调整列宽' },
              on: {
                mousedown: (e) => self.onColumnResizeStart(e, key, column),
                click: (e) => e.stopPropagation()
              }
            })
            const thClass = restProps.class ? restProps.class + ' resize-table-th' : 'resize-table-th'
            return h('th', { ...restProps, class: thClass }, [children, drag])
          },
        }
      }
    },
    //开始拖拽列宽：冻结所有列当前宽度，拖动时只改当前列
    onColumnResizeStart(e, fallbackKey, defaultColumns) {
      if (e.button !== undefined && e.button !== 0) return
      e.preventDefault()
      e.stopPropagation()
      const handle = e.currentTarget || e.target
      const th = handle && handle.closest ? handle.closest('th') : null
      if (!th || !th.parentNode) return
      // 固定列（左侧/右侧）由独立表格渲染，不参与列宽拖拽，避免列索引错位
      if (th.closest('.ant-table-fixed-right') || th.closest('.ant-table-fixed-left')) return
      const root = th.closest('.ant-table-wrapper') || th.closest('.ant-table')
      if (!root) return
      const headerThs = Array.prototype.slice.call(th.parentNode.children)
      const index = headerThs.indexOf(th)
      if (index < 0) return
      //列对象在拖拽时解析，避免 components 初始化时缓存旧列数组
      let colList = defaultColumns || []
      let colIndex = colList.findIndex(c => (c.dataIndex || c.key) === fallbackKey)
      if (colIndex < 0 && this.columns) {
        colList = this.columns
        colIndex = colList.findIndex(c => (c.dataIndex || c.key) === fallbackKey)
      }
      if (colIndex < 0) return
      //选择列/展开列等非数据列在表头中的偏移
      const offset = index - colIndex

      //冻结所有列的当前实际宽度
      const startWidths = headerThs.map((el, i) => {
        const column = colList[i - offset]
        return this.parseColumnWidth(column && column.width) || Math.round(el.getBoundingClientRect().width)
      })
      const startTotal = startWidths.reduce((sum, w) => sum + w, 0)
      const tableStates = Array.prototype.slice.call(root.querySelectorAll('table'))
        .map(table => ({
          table,
          cols: Array.prototype.slice.call(table.querySelectorAll('colgroup > col'))
        }))
        .filter(state => state.cols.length === headerThs.length)
      tableStates.forEach(({ table, cols }) => {
        table.style.tableLayout = 'fixed'
        table.style.width = startTotal + 'px'
        cols.forEach((colEl, i) => {
          if (startWidths[i] == null) return
          colEl.style.width = startWidths[i] + 'px'
          colEl.style.minWidth = startWidths[i] + 'px'
        })
      })
      headerThs.forEach((el, i) => { el.style.width = startWidths[i] + 'px' })

      const startX = e.clientX
      const startWidth = startWidths[index] || 0
      let lastWidth = startWidth
      let frame = null

      const apply = () => {
        frame = null
        const total = startTotal + (lastWidth - startWidth)
        tableStates.forEach(({ table, cols }) => {
          table.style.width = total + 'px'
          const colEl = cols[index]
          if (colEl) {
            colEl.style.width = lastWidth + 'px'
            colEl.style.minWidth = lastWidth + 'px'
          }
        })
        th.style.width = lastWidth + 'px'
      }
      const onMove = (ev) => {
        lastWidth = Math.max(40, Math.round(startWidth + ev.clientX - startX))
        if (!frame) frame = window.requestAnimationFrame(apply)
      }
      const onUp = () => {
        document.removeEventListener('mousemove', onMove)
        document.removeEventListener('mouseup', onUp)
        document.body.style.cursor = ''
        document.body.style.userSelect = ''
        if (frame) window.cancelAnimationFrame(frame)
        apply()
        if (lastWidth !== startWidth) {
          //仅更新拖动的列，避免把浏览器分配后的实际宽度写回其它列定义。
          const resizedColumn = colList[colIndex]
          if (resizedColumn) this.$set(resizedColumn, 'width', lastWidth)
          //表格宽度固定为本次实测列宽之和（含自动插入列），其它列宽不参与分摊
          const total = startTotal + (lastWidth - startWidth)
          if (Math.round(Number((this.scroll || {}).x)) !== total) {
            this.scroll = Object.assign({}, this.scroll, { x: total })
          }
          if (typeof this.onColumnWidthsChanged === 'function') this.onColumnWidthsChanged()
        }
        this.$nextTick(() => {
          headerThs.forEach(el => { el.style.width = '' })
          tableStates.forEach(({ cols }) => {
            cols.forEach(colEl => { colEl.style.minWidth = '' })
          })
        })
      }
      document.body.style.cursor = 'col-resize'
      document.body.style.userSelect = 'none'
      document.addEventListener('mousemove', onMove)
      document.addEventListener('mouseup', onUp)
    },
    /** 表格增加合计行 */
    tableAddTotalRow(columns, dataSource) {
      if(dataSource.length>0 && this.ipagination.pageSize%10===1) {
        //分页条数为11、21、31等的时候增加合计行
        let numKey = 'rowIndex'
        let totalRow = { [numKey]: '合计' }
        //需要合计的列，记住最后的逗号不要删除
        let parseCols = 'initialStock,currentStock,currentStockPrice,currentWeight,initialAmount,thisMonthAmount,currentAmount,inSum,inSumPrice,' +
          'inOutSumPrice,outSum,outSumPrice,outInSumPrice,operNumber,allPrice,numSum,priceSum,prevSum,thisSum,thisAllPrice,changeAmount,' +
          'allPrice,taxMoney,currentNumber,lowCritical,highCritical,preNeed,debtMoney,backMoney,allNeed,' +
          'needDebt,realNeedDebt,finishDebt,debt,'
        columns.forEach(column => {
          let { key, dataIndex } = column
          if (![key, dataIndex].includes(numKey)) {
            let total = 0
            dataSource.forEach(data => {
              if(parseCols.indexOf(dataIndex+',')>-1) {
                if(data[dataIndex]) {
                  total += Number.parseFloat(data[dataIndex])
                } else {
                  total += 0
                }
              } else {
                total = '-'
              }
            })
            if (total !== '-') {
              total = total.toFixed(2)
            }
            totalRow[dataIndex] = total
          }
        })
        dataSource.push(totalRow)
        //总数要增加合计的行数，每页都有一行合计，所以总数要加上
        let size = Math.ceil(this.ipagination.total/(this.ipagination.pageSize-1))
        this.ipagination.total = this.ipagination.total + size
      }
    },
    //动态替换扩展字段
    handleChangeOtherField(showQuery) {
      let mpStr = getMpListShort(Vue.ls.get('materialPropertyList'))
      if(mpStr) {
        let mpArr = mpStr.split(',')
        if(mpArr.length ===3) {
          if(showQuery) {
            this.queryTitle.mp1 = mpArr[0]
            this.queryTitle.mp2 = mpArr[1]
            this.queryTitle.mp3 = mpArr[2]
          }
          for (let i = 0; i < this.defColumns.length; i++) {
            if(this.defColumns[i].dataIndex === 'otherField1') {
              this.defColumns[i].title = mpArr[0]
            }
            if(this.defColumns[i].dataIndex === 'otherField2') {
              this.defColumns[i].title = mpArr[1]
            }
            if(this.defColumns[i].dataIndex === 'otherField3') {
              this.defColumns[i].title = mpArr[2]
            }
          }
        }
      }
    },
    paginationChange(page, pageSize) {
      this.ipagination.current = page
      this.ipagination.pageSize = pageSize
      this.loadData(this.ipagination.current);
    },
    paginationShowSizeChange(current, size) {
      this.ipagination.current = current
      this.ipagination.pageSize = size
      this.loadData(this.ipagination.current);
    }
  }

}
