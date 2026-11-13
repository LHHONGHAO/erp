package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.constants.BusinessConstants;
import com.jsh.erp.constants.ExceptionConstants;
import com.jsh.erp.datasource.entities.*;
import com.jsh.erp.datasource.mappers.*;
import com.jsh.erp.datasource.vo.DepotHeadVo4StatementAccount;
import com.jsh.erp.datasource.vo.SupplierSimple;
import com.jsh.erp.exception.BusinessRunTimeException;
import com.jsh.erp.exception.JshException;
import com.jsh.erp.utils.*;
import jxl.Sheet;
import jxl.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;


@Service
public class SupplierService {
    private Logger logger = LoggerFactory.getLogger(SupplierService.class);

    @Resource
    private SupplierMapper supplierMapper;

    @Resource
    private SupplierMapperEx supplierMapperEx;
    @Resource
    private LogService logService;
    @Resource
    private UserService userService;
    @Resource
    private AccountHeadMapperEx accountHeadMapperEx;
    @Resource
    private DepotHeadMapperEx depotHeadMapperEx;
    @Resource
    private AccountItemMapperEx accountItemMapperEx;
    @Resource
    private DepotHeadService depotHeadService;
    @Resource
    private UserBusinessService userBusinessService;
    @Resource
    private UserBusinessMapper userBusinessMapper;

    @Value(value="${file.exportTmp}")
    private String fileExportTmp;

    public Supplier getSupplier(long id)throws Exception {
        Supplier result=null;
        try{
            result=supplierMapper.selectByPrimaryKey(id);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return result;
    }

    public List<Supplier> getSupplierListByIds(String ids)throws Exception {
        List<Long> idList = StringUtil.strToLongList(ids);
        List<Supplier> list = new ArrayList<>();
        try{
            SupplierExample example = new SupplierExample();
            example.createCriteria().andIdIn(idList);
            list = supplierMapper.selectByExample(example);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    public List<Supplier> getSupplier()throws Exception {
        SupplierExample example = new SupplierExample();
        example.createCriteria().andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<Supplier> list=null;
        try{
            list=supplierMapper.selectByExample(example);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    public List<Supplier> select(String supplier, String type, String contacts, String phonenum, String telephone) throws Exception{
        List<Supplier> list = new ArrayList<>();
        try{
            String [] creatorArray = depotHeadService.getCreatorArray();
            PageUtils.startPage();
            list = supplierMapperEx.selectByConditionSupplier(supplier, type, contacts, phonenum, telephone, creatorArray);
            for(Supplier s : list) {
                Integer supplierId = s.getId().intValue();
                String beginTime = Tools.getYearBegin();
                String endTime = Tools.getCenternTime(new Date());
                BigDecimal sum = BigDecimal.ZERO;
                String supplierType = type;
                String inOutType = "";
                String subType = "";
                String typeBack = "";
                String subTypeBack = "";
                String billType = "";
                if (("供应商").equals(supplierType)) {
                    inOutType = "入库";
                    subType = "采购";
                    typeBack = "出库";
                    subTypeBack = "采购退货";
                    billType = "付款";
                } else if (("客户").equals(supplierType)) {
                    inOutType = "出库";
                    subType = "销售";
                    typeBack = "入库";
                    subTypeBack = "销售退货";
                    billType = "收款";
                }
                List<DepotHeadVo4StatementAccount> saList = depotHeadService.getStatementAccount(beginTime, endTime, supplierId, null,
                        1, supplierType, inOutType, subType, typeBack, subTypeBack, billType, null, null);
                if(saList.size()>0) {
                    DepotHeadVo4StatementAccount item = saList.get(0);
                    //期初 = 起始期初金额+上期欠款金额-上期退货的欠款金额-上期收付款
                    BigDecimal preNeed = item.getBeginNeed().add(item.getPreDebtMoney()).subtract(item.getPreReturnDebtMoney()).subtract(item.getPreBackMoney());
                    item.setPreNeed(preNeed);
                    //实际欠款 = 本期欠款-本期退货的欠款金额
                    BigDecimal realDebtMoney = item.getDebtMoney().subtract(item.getReturnDebtMoney());
                    item.setDebtMoney(realDebtMoney);
                    //期末 = 期初+实际欠款-本期收款
                    BigDecimal allNeedGet = preNeed.add(realDebtMoney).subtract(item.getBackMoney());
                    sum = sum.add(allNeedGet);
                }
                if(("客户").equals(s.getType())) {
                    s.setAllNeedGet(sum);
                } else if(("供应商").equals(s.getType())) {
                    s.setAllNeedPay(sum);
                }
            }
        } catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int insertSupplier(JSONObject obj, HttpServletRequest request)throws Exception {
        Supplier supplier = JSONObject.parseObject(obj.toJSONString(), Supplier.class);
        int result=0;
        try{
            supplier.setEnabled(true);
            User userInfo=userService.getCurrentUser();
            supplier.setCreator(userInfo==null?null:userInfo.getId());
            result=supplierMapper.insertSelective(supplier);
            //新增客户时给当前用户和租户自动授权
            setUserCustomerPermission(request, supplier);
            logService.insertLog("商家",
                    new StringBuffer(BusinessConstants.LOG_OPERATION_TYPE_ADD).append(supplier.getSupplier()).toString(),request);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return result;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int updateSupplier(JSONObject obj, HttpServletRequest request)throws Exception {
        Supplier supplier = JSONObject.parseObject(obj.toJSONString(), Supplier.class);
        if(supplier.getBeginNeedPay() == null) {
            supplier.setBeginNeedPay(BigDecimal.ZERO);
        }
        if(supplier.getBeginNeedGet() == null) {
            supplier.setBeginNeedGet(BigDecimal.ZERO);
        }
        int result=0;
        try{
            result=supplierMapper.updateByPrimaryKeySelective(supplier);
            logService.insertLog("商家",
                    new StringBuffer(BusinessConstants.LOG_OPERATION_TYPE_EDIT).append(supplier.getSupplier()).toString(), request);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return result;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int deleteSupplier(Long id, HttpServletRequest request)throws Exception {
        return batchDeleteSupplierByIds(id.toString());
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int batchDeleteSupplier(String ids, HttpServletRequest request) throws Exception{
        return batchDeleteSupplierByIds(ids);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int batchDeleteSupplierByIds(String ids)throws Exception {
        int result=0;
        String [] idArray=ids.split(",");
        //校验财务主表	jsh_accounthead
        List<AccountHead> accountHeadList=null;
        try{
            accountHeadList = accountHeadMapperEx.getAccountHeadListByOrganIds(idArray);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        if(accountHeadList!=null&&accountHeadList.size()>0){
            logger.error("异常码[{}],异常提示[{}],参数,OrganIds[{}]",
                    ExceptionConstants.DELETE_FORCE_CONFIRM_CODE,ExceptionConstants.DELETE_FORCE_CONFIRM_MSG,ids);
            throw new BusinessRunTimeException(ExceptionConstants.DELETE_FORCE_CONFIRM_CODE,
                    ExceptionConstants.DELETE_FORCE_CONFIRM_MSG);
        }
        //校验单据主表	jsh_depot_head
        List<DepotHead> depotHeadList=null;
        try{
            depotHeadList = depotHeadMapperEx.getDepotHeadListByOrganIds(idArray);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        if(depotHeadList!=null&&depotHeadList.size()>0){
            logger.error("异常码[{}],异常提示[{}],参数,OrganIds[{}]",
                    ExceptionConstants.DELETE_FORCE_CONFIRM_CODE,ExceptionConstants.DELETE_FORCE_CONFIRM_MSG,ids);
            throw new BusinessRunTimeException(ExceptionConstants.DELETE_FORCE_CONFIRM_CODE,
                    ExceptionConstants.DELETE_FORCE_CONFIRM_MSG);
        }
        //记录日志
        StringBuffer sb = new StringBuffer();
        sb.append(BusinessConstants.LOG_OPERATION_TYPE_DELETE);
        List<Supplier> list = getSupplierListByIds(ids);
        for(Supplier supplier: list){
            sb.append("[").append(supplier.getSupplier()).append("]");
        }
        logService.insertLog("商家", sb.toString(),
                ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest());
        User userInfo=userService.getCurrentUser();
        //校验通过执行删除操作
        try{
            result = supplierMapperEx.batchDeleteSupplierByIds(new Date(),userInfo==null?null:userInfo.getId(),idArray);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return result;
    }

    public int checkIsNameExist(Long id, String name)throws Exception {
        SupplierExample example = new SupplierExample();
        example.createCriteria().andIdNotEqualTo(id).andSupplierEqualTo(name).andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<Supplier> list=null;
        try{
            list= supplierMapper.selectByExample(example);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list==null?0:list.size();
    }

    public int checkIsNameAndTypeExist(Long id, String name, String type)throws Exception {
        name = name == null? "": name;
        SupplierExample example = new SupplierExample();
        example.createCriteria().andIdNotEqualTo(id).andSupplierEqualTo(name).andTypeEqualTo(type)
                .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<Supplier> list=null;
        try{
            list= supplierMapper.selectByExample(example);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list==null?0:list.size();
    }

    /**
     * 更新会员的预付款
     * @param supplierId
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void updateAdvanceIn(Long supplierId) {
        try{
            //查询会员在收预付款单据的总金额
            BigDecimal financialAllPrice = accountHeadMapperEx.getFinancialAllPriceByOrganId(supplierId);
            //查询会员在零售出库单据的总金额
            BigDecimal billAllPrice = depotHeadMapperEx.getBillAllPriceByOrganId(supplierId);
            Supplier supplier = new Supplier();
            supplier.setId(supplierId);
            supplier.setAdvanceIn(financialAllPrice.subtract(billAllPrice));
            supplierMapper.updateByPrimaryKeySelective(supplier);
        } catch (Exception e){
            JshException.writeFail(logger, e);
        }
    }

    public List<Supplier> findBySelectCus(String key, Long organId, Integer limit)throws Exception {
        List<Supplier> list=null;
        try{
            list = supplierMapperEx.findByTypeAndKey("客户", key, limit);
            if(organId!=null) {
                list = addOrganToList(list, organId);
            }
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    public List<Supplier> findBySelectSup(String key, Long organId, Integer limit)throws Exception {
        List<Supplier> list=null;
        try{
            list = supplierMapperEx.findByTypeAndKey("供应商", key, limit);
            if(organId!=null) {
                list = addOrganToList(list, organId);
            }
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    public List<Supplier> findBySelectRetail(String key, Long organId, Integer limit)throws Exception {
        List<Supplier> list=null;
        try{
            list = supplierMapperEx.findByTypeAndKey("会员", key, limit);
            if(organId!=null) {
                list = addOrganToList(list, organId);
            }
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    /**
     * 给列表追加供应商信息
     * @param list
     * @param organId
     * @return
     */
    public List<Supplier> addOrganToList(List<Supplier> list, Long organId) {
        boolean isExist = false;
        for(Supplier supplier: list) {
            if(supplier.getId().equals(organId)) {
                isExist = true;
            }
        }
        if(!isExist) {
            //列表里面不存在则追加
            Supplier info = supplierMapperEx.getInfoById(organId);
            if(info!=null) {
                list.add(info);
            }
        }
        return list;
    }

    public List<Supplier> findById(Long supplierId)throws Exception {
        SupplierExample example = new SupplierExample();
        example.createCriteria().andIdEqualTo(supplierId)
                .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        example.setOrderByClause("sort asc, id desc");
        List<Supplier> list=null;
        try{
            list = supplierMapper.selectByExample(example);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int batchSetStatus(Boolean status, String ids)throws Exception {
        logService.insertLog("商家",
                new StringBuffer(BusinessConstants.LOG_OPERATION_TYPE_ENABLED).toString(),
                ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest());
        List<Long> supplierIds = StringUtil.strToLongList(ids);
        Supplier supplier = new Supplier();
        supplier.setEnabled(status);
        SupplierExample example = new SupplierExample();
        example.createCriteria().andIdIn(supplierIds);
        int result=0;
        try{
            result = supplierMapper.updateByExampleSelective(supplier, example);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return result;
    }

    public List<Supplier> findUserCustomer()throws Exception{
        SupplierExample example = new SupplierExample();
        example.createCriteria().andTypeEqualTo("客户")
                .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        example.setOrderByClause("sort asc, id desc");
        List<Supplier> list=null;
        try{
            list = supplierMapper.selectByExample(example);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    public List<Supplier> findByAll(String supplier, String type, String phonenum, String telephone) throws Exception{
        List<Supplier> list=null;
        try{
            list = supplierMapperEx.findByAll(supplier, type, phonenum, telephone);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    public Map<String, Object> getBeginNeedByOrganId(Long organId) throws Exception {
        Supplier supplier = getSupplier(organId);
        Map<String, Object> map = new HashMap<>();
        BigDecimal needDebt = BigDecimal.ZERO;
        if("供应商".equals(supplier.getType())) {
            needDebt = supplier.getBeginNeedPay();
        } else if("客户".equals(supplier.getType())) {
            needDebt = supplier.getBeginNeedGet();
        }
        BigDecimal finishDebt = accountItemMapperEx.getFinishDebtByOrganId(organId).abs();
        BigDecimal eachAmount = BigDecimal.ZERO;
        if(needDebt != null) {
            eachAmount = needDebt.subtract(finishDebt);
        }
        //应收欠款
        map.put("needDebt", needDebt);
        //已收欠款
        map.put("finishDebt", finishDebt);
        //本次收款
        map.put("eachAmount", eachAmount);
        return map;
    }

    /**
     * 校验文件格式
     * @param file
     */
    public void checkFileExt(MultipartFile file) {
        //文件扩展名只能为xls
        String fileName = file.getOriginalFilename();
        if(StringUtil.isNotEmpty(fileName)) {
            String fileExt = fileName.substring(fileName.lastIndexOf(".")+1);
            if(!"xls".equals(fileExt)) {
                throw new BusinessRunTimeException(ExceptionConstants.FILE_EXTENSION_ERROR_CODE,
                        ExceptionConstants.FILE_EXTENSION_ERROR_MSG);
            }
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void importVendor(MultipartFile file, HttpServletRequest request) throws Exception{
        String type = "供应商";
        User userInfo = userService.getCurrentUser();
        Workbook workbook = Workbook.getWorkbook(file.getInputStream());
        Sheet src = workbook.getSheet(0);
        //'名称', '联系人', '手机号码', '联系电话', '电子邮箱', '传真', '期初应付', '纳税人识别号', '税率(%)', '开户行', '账号', '地址', '备注', '排序', '状态'
        List<Supplier> sList = new ArrayList<>();
        for (int i = 2; i < src.getRows(); i++) {
            String supplierName = ExcelUtils.getContent(src, i, 0);
            String enabled = ExcelUtils.getContent(src, i, 14);
            if(StringUtil.isNotEmpty(supplierName) && StringUtil.isNotEmpty(enabled)) {
                Supplier s = new Supplier();
                s.setType(type);
                s.setSupplier(supplierName);
                s.setContacts(ExcelUtils.getContent(src, i, 1));
                s.setTelephone(ExcelUtils.getContent(src, i, 2));
                s.setPhoneNum(ExcelUtils.getContent(src, i, 3));
                s.setEmail(ExcelUtils.getContent(src, i, 4));
                s.setFax(ExcelUtils.getContent(src, i, 5));
                s.setBeginNeedPay(parseBigDecimalEx(ExcelUtils.getContent(src, i, 6)));
                s.setTaxNum(ExcelUtils.getContent(src, i, 7));
                s.setTaxRate(parseBigDecimalEx(ExcelUtils.getContent(src, i, 8)));
                s.setBankName(ExcelUtils.getContent(src, i, 9));
                s.setAccountNumber(ExcelUtils.getContent(src, i, 10));
                s.setAddress(ExcelUtils.getContent(src, i, 11));
                s.setDescription(ExcelUtils.getContent(src, i, 12));
                s.setSort(ExcelUtils.getContent(src, i, 13));
                s.setCreator(userInfo==null?null:userInfo.getId());
                s.setEnabled("1".equals(enabled));
                sList.add(s);
            }
        }
        importExcel(sList, type, request);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void importCustomer(MultipartFile file, HttpServletRequest request) throws Exception{
        String type = "客户";
        User userInfo = userService.getCurrentUser();
        Workbook workbook = Workbook.getWorkbook(file.getInputStream());
        Sheet src = workbook.getSheet(0);
        //'名称', '联系人', '手机号码', '联系电话', '电子邮箱', '传真', '期初应收', '纳税人识别号', '税率(%)', '开户行', '账号', '地址', '备注', '排序', '状态'
        List<Supplier> sList = new ArrayList<>();
        for (int i = 2; i < src.getRows(); i++) {
            String supplierName = ExcelUtils.getContent(src, i, 0);
            String enabled = ExcelUtils.getContent(src, i, 14);
            if(StringUtil.isNotEmpty(supplierName) && StringUtil.isNotEmpty(enabled)) {
                Supplier s = new Supplier();
                s.setType(type);
                s.setSupplier(supplierName);
                s.setContacts(ExcelUtils.getContent(src, i, 1));
                s.setTelephone(ExcelUtils.getContent(src, i, 2));
                s.setPhoneNum(ExcelUtils.getContent(src, i, 3));
                s.setEmail(ExcelUtils.getContent(src, i, 4));
                s.setFax(ExcelUtils.getContent(src, i, 5));
                s.setBeginNeedGet(parseBigDecimalEx(ExcelUtils.getContent(src, i, 6)));
                s.setTaxNum(ExcelUtils.getContent(src, i, 7));
                s.setTaxRate(parseBigDecimalEx(ExcelUtils.getContent(src, i, 8)));
                s.setBankName(ExcelUtils.getContent(src, i, 9));
                s.setAccountNumber(ExcelUtils.getContent(src, i, 10));
                s.setAddress(ExcelUtils.getContent(src, i, 11));
                s.setDescription(ExcelUtils.getContent(src, i, 12));
                s.setSort(ExcelUtils.getContent(src, i, 13));
                s.setCreator(userInfo==null?null:userInfo.getId());
                s.setEnabled("1".equals(enabled));
                sList.add(s);
            }
        }
        importExcel(sList, type, request);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void importMember(MultipartFile file, HttpServletRequest request) throws Exception{
        String type = "会员";
        User userInfo = userService.getCurrentUser();
        Workbook workbook = Workbook.getWorkbook(file.getInputStream());
        Sheet src = workbook.getSheet(0);
        //'名称', '联系人', '手机号码', '联系电话', '电子邮箱', '备注', '排序', '状态'
        List<Supplier> sList = new ArrayList<>();
        for (int i = 2; i < src.getRows(); i++) {
            String supplierName = ExcelUtils.getContent(src, i, 0);
            String enabled = ExcelUtils.getContent(src, i, 7);
            if(StringUtil.isNotEmpty(supplierName) && StringUtil.isNotEmpty(enabled)) {
                Supplier s = new Supplier();
                s.setType(type);
                s.setSupplier(supplierName);
                s.setContacts(ExcelUtils.getContent(src, i, 1));
                s.setTelephone(ExcelUtils.getContent(src, i, 2));
                s.setPhoneNum(ExcelUtils.getContent(src, i, 3));
                s.setEmail(ExcelUtils.getContent(src, i, 4));
                s.setDescription(ExcelUtils.getContent(src, i, 5));
                s.setSort(ExcelUtils.getContent(src, i, 6));
                s.setCreator(userInfo==null?null:userInfo.getId());
                s.setEnabled("1".equals(enabled));
                sList.add(s);
            }
        }
        importExcel(sList, type, request);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public BaseResponseInfo importExcel(List<Supplier> mList, String type, HttpServletRequest request) throws Exception {
        logService.insertLog(type,
                new StringBuffer(BusinessConstants.LOG_OPERATION_TYPE_IMPORT).append(mList.size()).append(BusinessConstants.LOG_DATA_UNIT).toString(),
                ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest());
        BaseResponseInfo info = new BaseResponseInfo();
        Map<String, Object> data = new HashMap<>();
        try {
            for(Supplier supplier: mList) {
                SupplierExample example = new SupplierExample();
                example.createCriteria().andSupplierEqualTo(supplier.getSupplier()).andTypeEqualTo(type).andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
                List<Supplier> list= supplierMapper.selectByExample(example);
                if(list.size() <= 0) {
                    supplierMapper.insertSelective(supplier);
                    //新增客户时给当前用户和租户自动授权
                    setUserCustomerPermission(request, supplier);
                } else {
                    Long id = list.get(0).getId();
                    supplier.setId(id);
                    supplierMapper.updateByPrimaryKeySelective(supplier);
                }
            }
            info.code = 200;
            data.put("message", "成功");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            info.code = 500;
            data.put("message", e.getMessage());
        }
        info.data = data;
        return info;
    }

    public BigDecimal parseBigDecimalEx(String str)throws Exception{
        if(!StringUtil.isEmpty(str)) {
            return new BigDecimal(str);
        } else {
            return null;
        }
    }

    public File exportExcel(List<Supplier> dataList, String type) throws Exception {
        if("供应商".equals(type)) {
            return exportExcelVendorOrCustomer(dataList, type);
        } else if("客户".equals(type)) {
            return exportExcelVendorOrCustomer(dataList, type);
        } else {
            //会员
            String[] names = {"会员卡号*", "联系人", "手机号码", "联系电话", "电子邮箱", "备注", "排序", "状态*"};
            String title = "信息内容";
            List<Object[]> objects = new ArrayList<>();
            if (null != dataList) {
                for (Supplier s : dataList) {
                    Object[] objs = new Object[names.length];
                    objs[0] = s.getSupplier();
                    objs[1] = s.getContacts();
                    objs[2] = s.getTelephone();
                    objs[3] = s.getPhoneNum();
                    objs[4] = s.getEmail();
                    objs[5] = s.getDescription();
                    objs[6] = s.getSort();
                    objs[7] = s.getEnabled() ? "1" : "0";
                    objects.add(objs);
                }
            }
            return ExcelUtils.exportObjectsOneSheet(fileExportTmp, title, "*导入时本行内容请勿删除，切记！", names, title, objects);
        }
    }

    private File exportExcelVendorOrCustomer(List<Supplier> dataList, String type) throws Exception {
        String beginNeedStr = "";
        if("供应商".equals(type)) {
            beginNeedStr = "期初应付";
        } else if("客户".equals(type)) {
            beginNeedStr = "期初应收";
        }
        String[] names = {"名称*", "联系人", "手机号码", "联系电话", "电子邮箱", "传真", beginNeedStr,
                "纳税人识别号", "税率(%)", "开户行", "账号", "地址", "备注", "排序", "状态*"};
        String title = "信息内容";
        List<Object[]> objects = new ArrayList<>();
        if (null != dataList) {
            for (Supplier s : dataList) {
                Object[] objs = new Object[names.length];
                objs[0] = s.getSupplier();
                objs[1] = s.getContacts();
                objs[2] = s.getTelephone();
                objs[3] = s.getPhoneNum();
                objs[4] = s.getEmail();
                objs[5] = s.getFax();
                if(("客户").equals(s.getType())) {
                    objs[6] = s.getBeginNeedGet() == null? "" : s.getBeginNeedGet().setScale(2,BigDecimal.ROUND_HALF_UP);
                } else if(("供应商").equals(s.getType())) {
                    objs[6] = s.getBeginNeedPay() == null? "" : s.getBeginNeedPay().setScale(2,BigDecimal.ROUND_HALF_UP);
                }
                objs[7] = s.getTaxNum();
                objs[8] = s.getTaxRate() == null? "" : s.getTaxRate().setScale(2,BigDecimal.ROUND_HALF_UP);
                objs[9] = s.getBankName();
                objs[10] = s.getAccountNumber();
                objs[11] = s.getAddress();
                objs[12] = s.getDescription();
                objs[13] = s.getSort();
                objs[14] = s.getEnabled() ? "1" : "0";
                objects.add(objs);
            }
        }
        return ExcelUtils.exportObjectsOneSheet(fileExportTmp, title, "*导入时本行内容请勿删除，切记！", names, title, objects);
    }

    /**
     * 新增客户时给当前用户和租户自动授权
     * @param request
     * @param supplier
     * @throws Exception
     */
    private void setUserCustomerPermission(HttpServletRequest request, Supplier supplier) throws Exception {
        if("客户".equals(supplier.getType())) {
            User user = userService.getCurrentUser();
            Supplier sInfo = supplierMapperEx.getSupplierByNameAndType(supplier.getSupplier(), supplier.getType());
            String ubKey = "[" + sInfo.getId() + "]";
            //授权当前用户
            setPermissionByParam(user.getId(), ubKey);
            if(!user.getId().equals(user.getTenantId())) {
                //授权当前租户
                setPermissionByParam(user.getTenantId(), ubKey);
            }
        }
    }

    /**
     * 权限授权操作
     * @param userId
     * @param ubKey
     * @throws Exception
     */
    private void setPermissionByParam(Long userId, String ubKey) throws Exception {
        List<UserBusiness> ubList = userBusinessService.getBasicData(userId.toString(), "UserCustomer");
        if(ubList ==null || ubList.size() == 0) {
            JSONObject ubObj = new JSONObject();
            ubObj.put("type", "UserCustomer");
            ubObj.put("keyId", userId);
            ubObj.put("value", ubKey);
            UserBusiness userBusiness = JSONObject.parseObject(ubObj.toJSONString(), UserBusiness.class);
            userBusinessMapper.insertSelective(userBusiness);
        } else {
            UserBusiness ubInfo = ubList.get(0);
            JSONObject ubObj = new JSONObject();
            ubObj.put("id", ubInfo.getId());
            ubObj.put("type", ubInfo.getType());
            ubObj.put("keyId", ubInfo.getKeyId());
            ubObj.put("value", ubInfo.getValue() + ubKey);
            UserBusiness userBusiness = JSONObject.parseObject(ubObj.toJSONString(), UserBusiness.class);
            userBusinessMapper.updateByPrimaryKeySelective(userBusiness);
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int batchSetAdvanceIn(String ids) throws Exception {
        int res = 0;
        List<Long> idList = StringUtil.strToLongList(ids);
        for(Long sId: idList) {
            updateAdvanceIn(sId);
            res = 1;
        }
        return res;
    }

    public List<SupplierSimple> getAllCustomer() {
        return supplierMapperEx.getAllCustomer();
    }

    public Supplier getInfoByName(String name, String type) {
        return supplierMapperEx.getInfoByName(name, type);
    }

    /**
     * 生成会员卡号：YYMMDD + 三位序号（屏蔽3/4/7）
     */
    public String generateMemberId() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd");
        String datePrefix = sdf.format(new Date());

        // 获取当天最大序号
        String maxSeq = supplierMapperEx.getMaxMemberSeqToday(datePrefix);
        int currentSeq = 0;
        if (maxSeq != null && maxSeq.length() >= 9) {
            String seqStr = maxSeq.substring(6);
            try {
                currentSeq = Integer.parseInt(seqStr);
            } catch (NumberFormatException e) {
                currentSeq = 0;
            }
        }

        // 找下一个不包含3/4/7的序号
        int nextSeq = currentSeq + 1;
        while (containsBlockedDigits(nextSeq)) {
            nextSeq++;
        }
        // 确保不超过999
        if (nextSeq > 999) {
            nextSeq = 999;
        }
        return datePrefix + String.format("%03d", nextSeq);
    }

    private boolean containsBlockedDigits(int num) {
        int temp = num;
        while (temp > 0) {
            int digit = temp % 10;
            if (digit == 3 || digit == 4 || digit == 7) return true;
            temp /= 10;
        }
        return false;
    }

    /**
     * 按联系人或电话查找已存在的会员
     */
    public Supplier checkMemberDuplicate(String contacts, String telephone) {
        List<Supplier> list = supplierMapperEx.findByContactsOrPhone(contacts, telephone, "会员");
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    /**
     * 地址打印成功后保存会员信息：
     * 保存前先与已有会员比对（电话 > 平台账号 > 联系人与地址），命中则补充更新，未命中则新增。
     * decision: create 强制新建；update 强制更新指定 supplierId；不传则按比对结果自动处理。
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public JSONObject saveMemberFromAddress(JSONObject obj, HttpServletRequest request) throws Exception {
        JSONObject result = new JSONObject();
        String contacts = normalizeText(obj.getString("contacts"));
        String platform = normalizeText(obj.getString("platform"));
        String account = normalizeText(obj.getString("account"));
        String remark = normalizeText(obj.getString("remark"));
        String accountField = accountFieldOf(platform);
        List<String> phones = splitLines(obj.getString("telephone"), true);
        List<String> addresses = splitLines(obj.getString("address"), false);
        // 联系人、电话、账号全空：不产生会员数据
        if (contacts.isEmpty() && phones.isEmpty() && account.isEmpty()) {
            result.put("action", "skip");
            result.put("reason", "联系人和电话均为空");
            return result;
        }
        String decision = normalizeText(obj.getString("decision"));
        Long supplierId = obj.getLong("supplierId");

        // 强制更新：直接对指定会员做合并补充
        if ("update".equals(decision) && supplierId != null) {
            Supplier target = supplierMapper.selectByPrimaryKey(supplierId);
            if (target != null && "会员".equals(target.getType())) {
                return mergeMember(target, contacts, phones, addresses, accountField, account, remark, request);
            }
            decision = "create";
        }
        // 强制新建
        if ("create".equals(decision)) {
            return createMember(contacts, phones, addresses, accountField, account, remark, request);
        }

        // 自动比对：先粗筛候选，再在 Java 内精确打分
        List<String> candidatePhones = new ArrayList<>();
        for (String phone : phones) {
            String normalized = normalizePhone(phone);
            if (!normalized.isEmpty()) candidatePhones.add(normalized);
        }
        List<Supplier> candidates = supplierMapperEx.findMemberCandidates(contacts, candidatePhones,
                "wechat".equals(accountField) ? account : null,
                "douyin".equals(accountField) ? account : null,
                "alipay".equals(accountField) ? account : null,
                "other".equals(accountField) ? account : null);
        List<CandidateMatch> matches = new ArrayList<>();
        if (candidates != null) {
            for (Supplier candidate : candidates) {
                CandidateMatch match = scoreCandidate(candidate, contacts, phones, addresses, accountField, account);
                if (match.score > 0) {
                    matches.add(match);
                }
            }
        }
        matches.sort((a, b) -> {
            if (a.score != b.score) return b.score - a.score;
            return Long.compare(b.supplier.getId() == null ? 0 : b.supplier.getId(),
                    a.supplier.getId() == null ? 0 : a.supplier.getId());
        });

        if (!matches.isEmpty()) {
            CandidateMatch best = matches.get(0);
            // 最高分 >=80 自动合并；60~79 或前两名分差 <5 时交由前端确认
            boolean ambiguous = matches.size() > 1 && best.score - matches.get(1).score < 5;
            if (best.score >= 80 && !ambiguous) {
                return mergeMember(best.supplier, contacts, phones, addresses, accountField, account, remark, request);
            }
            if (best.score >= 60) {
                JSONObject confirm = new JSONObject();
                confirm.put("action", "confirm");
                confirm.put("matchedBy", best.matchedBy);
                JSONArray candidateArr = new JSONArray();
                int limit = Math.min(matches.size(), 5);
                for (int i = 0; i < limit; i++) {
                    CandidateMatch match = matches.get(i);
                    JSONObject item = new JSONObject();
                    item.put("id", match.supplier.getId());
                    item.put("supplier", match.supplier.getSupplier());
                    item.put("contacts", match.supplier.getContacts());
                    item.put("telephone", match.supplier.getTelephone());
                    item.put("address", match.supplier.getAddress());
                    item.put("score", match.score);
                    item.put("matchedBy", match.matchedBy);
                    candidateArr.add(item);
                }
                confirm.put("candidates", candidateArr);
                return confirm;
            }
        }
        return createMember(contacts, phones, addresses, accountField, account, remark, request);
    }

    /**
     * 新增会员：生成卡号后直接保存
     */
    private JSONObject createMember(String contacts, List<String> phones, List<String> addresses,
                                    String accountField, String account, String remark,
                                    HttpServletRequest request) throws Exception {
        Supplier supplier = new Supplier();
        supplier.setSupplier(generateMemberId());
        supplier.setType("会员");
        supplier.setEnabled(true);
        User user = userService.getCurrentUser();
        supplier.setCreator(user == null ? null : user.getId());
        supplier.setContacts(contacts.isEmpty() ? null : contacts);
        String telephoneText = String.join("\n", mergeUnique(new ArrayList<>(), phones, 6, true));
        supplier.setTelephone(telephoneText.isEmpty() ? null : telephoneText);
        supplier.setPhoneNum(telephoneText.isEmpty() ? null : telephoneText);
        String addressText = String.join("\n", mergeUnique(new ArrayList<>(), addresses, 6, false));
        supplier.setAddress(addressText.isEmpty() ? null : addressText);
        if (!account.isEmpty()) {
            setAccountValue(supplier, accountField, account);
        }
        supplier.setDescription(buildDescription(remark));
        supplierMapper.insertSelective(supplier);
        logService.insertLog("商家",
                new StringBuffer(BusinessConstants.LOG_OPERATION_TYPE_ADD).append(supplier.getSupplier()).toString(), request);

        JSONObject result = new JSONObject();
        result.put("action", "create");
        result.put("memberId", supplier.getSupplier());
        result.put("supplier", JSONObject.toJSON(supplier));
        result.put("changes", new JSONArray());
        return result;
    }

    /**
     * 合并更新已有会员：电话/地址追加去重，联系人/账号/备注仅在原值为空时补充
     */
    private JSONObject mergeMember(Supplier supplier, String contacts, List<String> phones, List<String> addresses,
                                   String accountField, String account, String remark,
                                   HttpServletRequest request) throws Exception {
        JSONArray changes = new JSONArray();
        // 电话：原多行 + 新增，去重后最多 6 条
        List<String> oldPhones = splitLines(supplier.getTelephone(), true);
        List<String> mergedPhones = mergeUnique(oldPhones, phones, 6, true);
        String oldPhoneText = String.join("\n", oldPhones);
        String newPhoneText = String.join("\n", mergedPhones);
        if (!newPhoneText.equals(oldPhoneText)) {
            supplier.setTelephone(newPhoneText.isEmpty() ? null : newPhoneText);
            supplier.setPhoneNum(newPhoneText.isEmpty() ? null : newPhoneText);
            changes.add("补充电话 " + newPhoneText.replace("\n", "、"));
        }
        // 地址：原多行 + 新增，去重后最多 6 条
        List<String> oldAddresses = splitLines(supplier.getAddress(), false);
        List<String> mergedAddresses = mergeUnique(oldAddresses, addresses, 6, false);
        String oldAddressText = String.join("\n", oldAddresses);
        String newAddressText = String.join("\n", mergedAddresses);
        if (!newAddressText.equals(oldAddressText)) {
            supplier.setAddress(newAddressText.isEmpty() ? null : newAddressText);
            changes.add("补充地址 " + newAddressText.replace("\n", "、"));
        }
        // 联系人：原值为空才写入
        if (isEmpty(supplier.getContacts()) && !contacts.isEmpty()) {
            supplier.setContacts(contacts);
            changes.add("补充联系人 " + contacts);
        }
        // 平台账号：原值为空才写入
        if (isEmpty(getAccountValue(supplier, accountField)) && !account.isEmpty()) {
            setAccountValue(supplier, accountField, account);
            changes.add("补充账号 " + account);
        }
        // 备注：原值为空才写入，并标记来源
        if (isEmpty(supplier.getDescription()) && (!remark.isEmpty() || !changes.isEmpty())) {
            supplier.setDescription(buildDescription(remark));
            changes.add("补充备注");
        }
        if (changes.isEmpty()) {
            JSONObject skip = new JSONObject();
            skip.put("action", "skip");
            skip.put("memberId", supplier.getSupplier());
            skip.put("supplier", JSONObject.toJSON(supplier));
            skip.put("changes", changes);
            return skip;
        }
        supplierMapper.updateByPrimaryKeySelective(supplier);
        logService.insertLog("商家",
                new StringBuffer(BusinessConstants.LOG_OPERATION_TYPE_EDIT).append(supplier.getSupplier()).toString(), request);

        JSONObject result = new JSONObject();
        result.put("action", "update");
        result.put("memberId", supplier.getSupplier());
        result.put("supplier", JSONObject.toJSON(supplier));
        result.put("changes", changes);
        return result;
    }

    /**
     * 候选会员打分：电话一致 100、平台账号一致 90、联系人与地址一致 80、联系人相同且地址相近 60
     */
    private CandidateMatch scoreCandidate(Supplier supplier, String contacts, List<String> phones,
                                          List<String> addresses, String accountField, String account) {
        // R1：任一电话归一化后一致
        if (!phones.isEmpty()) {
            Set<String> existingPhones = new HashSet<>();
            for (String phone : splitLines(supplier.getTelephone(), true)) {
                existingPhones.add(normalizePhone(phone));
            }
            for (String phone : splitLines(supplier.getPhoneNum(), true)) {
                existingPhones.add(normalizePhone(phone));
            }
            existingPhones.remove("");
            for (String phone : phones) {
                if (!normalizePhone(phone).isEmpty() && existingPhones.contains(normalizePhone(phone))) {
                    return new CandidateMatch(supplier, 100, "电话一致");
                }
            }
        }
        // R2：平台 + 账号完全一致
        if (!account.isEmpty()) {
            String existingAccount = getAccountValue(supplier, accountField);
            if (!existingAccount.isEmpty() && existingAccount.trim().equalsIgnoreCase(account)) {
                return new CandidateMatch(supplier, 90, "平台账号一致");
            }
        }
        // R3/R4：联系人相同，再看地址
        if (!contacts.isEmpty() && contacts.equals(supplier.getContacts())) {
            List<String> existingAddresses = splitLines(supplier.getAddress(), false);
            double bestSimilarity = 0;
            for (String address : addresses) {
                String input = normalizeAddress(address);
                if (input.isEmpty()) continue;
                for (String existing : existingAddresses) {
                    String old = normalizeAddress(existing);
                    if (old.isEmpty()) continue;
                    if (old.equals(input) || old.contains(input) || input.contains(old)) {
                        return new CandidateMatch(supplier, 80, "联系人与地址一致");
                    }
                    bestSimilarity = Math.max(bestSimilarity, addressSimilarity(old, input));
                }
            }
            if (bestSimilarity >= 0.75) {
                return new CandidateMatch(supplier, 60, "联系人相同，地址相近");
            }
        }
        return new CandidateMatch(supplier, 0, null);
    }

    /**
     * 地址相似度：按二元组 Dice 系数计算
     */
    private double addressSimilarity(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        if (left.equals(right)) return 1;
        Set<String> leftSet = bigrams(left);
        Set<String> rightSet = bigrams(right);
        int intersection = 0;
        for (String item : leftSet) {
            if (rightSet.contains(item)) intersection++;
        }
        return (2.0 * intersection) / (leftSet.size() + rightSet.size());
    }

    private Set<String> bigrams(String text) {
        Set<String> set = new HashSet<>();
        if (text.length() == 1) {
            set.add(text);
            return set;
        }
        for (int i = 0; i < text.length() - 1; i++) {
            set.add(text.substring(i, i + 2));
        }
        return set;
    }

    private void setAccountValue(Supplier supplier, String field, String value) {
        if ("wechat".equals(field)) supplier.setWechatAccount(value);
        else if ("douyin".equals(field)) supplier.setDouyinAccount(value);
        else if ("alipay".equals(field)) supplier.setAlipayAccount(value);
        else supplier.setOtherAccount(value);
    }

    private String getAccountValue(Supplier supplier, String field) {
        String value = null;
        if ("wechat".equals(field)) value = supplier.getWechatAccount();
        else if ("douyin".equals(field)) value = supplier.getDouyinAccount();
        else if ("alipay".equals(field)) value = supplier.getAlipayAccount();
        else value = supplier.getOtherAccount();
        return value == null ? "" : value.trim();
    }

    private String accountFieldOf(String platform) {
        if (platform == null) return "other";
        if (platform.contains("微信")) return "wechat";
        if (platform.contains("抖音")) return "douyin";
        if (platform.contains("支付宝")) return "alipay";
        return "other";
    }

    /**
     * 合并列表：按归一化值去重，保留原有顺序，最多 limit 条
     */
    private List<String> mergeUnique(List<String> existing, List<String> added, int limit, boolean isPhone) {
        List<String> merged = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        List<String> all = new ArrayList<>();
        if (existing != null) all.addAll(existing);
        if (added != null) all.addAll(added);
        for (String item : all) {
            if (item == null || item.trim().isEmpty()) continue;
            String value = item.trim();
            String key = isPhone ? normalizePhone(value) : normalizeAddress(value);
            if (key.isEmpty() || keys.contains(key)) continue;
            keys.add(key);
            merged.add(value);
            if (merged.size() >= limit) break;
        }
        return merged;
    }

    /**
     * 多行/多值拆分：电话同时按逗号、分号拆分，地址只按换行拆分
     */
    private List<String> splitLines(String text, boolean splitByComma) {
        List<String> list = new ArrayList<>();
        if (text == null) return list;
        String[] parts = text.split(splitByComma ? "[\\r\\n,，;；、]+" : "[\\r\\n]+");
        for (String part : parts) {
            String value = part == null ? "" : part.trim();
            if (!value.isEmpty()) list.add(value);
        }
        return list;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizePhone(String value) {
        if (value == null) return "";
        String digits = value.replaceAll("[^0-9]", "");
        // 去掉国际区号 +86
        if (digits.length() >= 13 && digits.startsWith("86")) {
            digits = digits.substring(2);
        }
        return digits;
    }

    private String normalizeAddress(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\s　,，。.、;；:：]", "");
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 备注：用户备注 + 来源标记
     */
    private String buildDescription(String remark) {
        String source = "来源：地址打印 " + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        return remark.isEmpty() ? source : remark + "\n" + source;
    }

    /**
     * 候选会员比对结果
     */
    private static class CandidateMatch {
        private final Supplier supplier;
        private final int score;
        private final String matchedBy;

        private CandidateMatch(Supplier supplier, int score, String matchedBy) {
            this.supplier = supplier;
            this.score = score;
            this.matchedBy = matchedBy;
        }
    }
}
