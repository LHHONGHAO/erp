package com.jsh.erp.service;

import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.constants.BusinessConstants;
import com.jsh.erp.constants.ExceptionConstants;
import com.jsh.erp.datasource.entities.Material;
import com.jsh.erp.datasource.entities.MaterialCategory;
import com.jsh.erp.datasource.entities.MaterialCategoryExample;
import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.MaterialCategoryMapper;
import com.jsh.erp.datasource.mappers.MaterialCategoryMapperEx;
import com.jsh.erp.datasource.mappers.MaterialMapperEx;
import com.jsh.erp.datasource.vo.TreeNode;
import com.jsh.erp.exception.BusinessRunTimeException;
import com.jsh.erp.exception.JshException;
import com.jsh.erp.utils.PageUtils;
import com.jsh.erp.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class MaterialCategoryService {
    private Logger logger = LoggerFactory.getLogger(MaterialCategoryService.class);

    @Resource
    private MaterialCategoryMapper materialCategoryMapper;
    @Resource
    private MaterialCategoryMapperEx materialCategoryMapperEx;
    @Resource
    private UserService userService;
    @Resource
    private LogService logService;
    @Resource
    private MaterialMapperEx materialMapperEx;

    public MaterialCategory getMaterialCategory(long id)throws Exception {
        MaterialCategory result=null;
        try{
            result=materialCategoryMapper.selectByPrimaryKey(id);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return result;
    }

    public List<MaterialCategory> getMaterialCategoryListByIds(String ids)throws Exception {
        List<Long> idList = StringUtil.strToLongList(ids);
        List<MaterialCategory> list = new ArrayList<>();
        try{
            MaterialCategoryExample example = new MaterialCategoryExample();
            example.createCriteria().andIdIn(idList);
            list = materialCategoryMapper.selectByExample(example);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    public List<MaterialCategory> getMaterialCategory()throws Exception {
        MaterialCategoryExample example = new MaterialCategoryExample();
        List<MaterialCategory> list=null;
        try{
            list=materialCategoryMapper.selectByExample(example);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    public List<MaterialCategory> getAllList(Long parentId)throws Exception {
        List<MaterialCategory> list=null;
        try{
            list = getMCList(parentId);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    public List<MaterialCategory> getMCList(Long parentId)throws Exception {
        List<MaterialCategory> res= new ArrayList<MaterialCategory>();
        List<MaterialCategory> list=null;
        MaterialCategoryExample example = new MaterialCategoryExample();
        example.createCriteria().andParentIdEqualTo(parentId).andIdNotEqualTo(1L);
        example.setOrderByClause("id");
        list=materialCategoryMapper.selectByExample(example);
        if(list!=null && list.size()>0) {
            res.addAll(list);
            for(MaterialCategory mc : list) {
                List<MaterialCategory> mcList = getMCList(mc.getId());
                if(mcList!=null && mcList.size()>0) {
                    res.addAll(mcList);
                }
            }
        }
        return res;
    }

    public List<MaterialCategory> select(String name, Integer parentId) throws Exception{
        List<MaterialCategory> list=null;
        try{
            PageUtils.startPage();
            list=materialCategoryMapperEx.selectByConditionMaterialCategory(name, parentId);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    private static final String BASE36_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** 将整数格式化为指定位数的36进制字符串 */
    private String formatBase36(int num, int digits) {
        if (num < 0) num = 0;
        String result = "";
        int n = num;
        do {
            result = BASE36_CHARS.charAt(n % 36) + result;
            n /= 36;
        } while (n > 0);
        while (result.length() < digits) {
            result = "0" + result;
        }
        return result;
    }

    /** 将36进制字符串解析为整数 */
    private int parseBase36(String str) {
        if (str == null || str.isEmpty()) return 0;
        try {
            return Integer.parseInt(str.toUpperCase(), 36);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 生成一级类目编号（2位36进制，按添加顺序递增） */
    private String generateLevel1SerialNo() throws Exception {
        MaterialCategoryExample example = new MaterialCategoryExample();
        example.createCriteria().andParentIdIsNull().andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<MaterialCategory> list = materialCategoryMapper.selectByExample(example);
        int maxNum = 0;
        if (list != null) {
            for (MaterialCategory mc : list) {
                if (mc.getSerialNo() != null && mc.getSerialNo().length() == 2) {
                    int num = parseBase36(mc.getSerialNo());
                    if (num > maxNum) maxNum = num;
                }
            }
        }
        return formatBase36(maxNum + 1, 2);
    }

    /** 生成二级类目编号（父级编号 + 2位36进制） */
    private String generateLevel2SerialNo(Long parentId, String parentSerialNo) throws Exception {
        MaterialCategoryExample example = new MaterialCategoryExample();
        example.createCriteria().andParentIdEqualTo(parentId).andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<MaterialCategory> list = materialCategoryMapper.selectByExample(example);
        int maxNum = 0;
        if (list != null) {
            for (MaterialCategory mc : list) {
                if (mc.getSerialNo() != null && mc.getSerialNo().length() >= 4) {
                    String suffix = mc.getSerialNo().substring(mc.getSerialNo().length() - 2);
                    int num = parseBase36(suffix);
                    if (num > maxNum) maxNum = num;
                }
            }
        }
        return parentSerialNo + formatBase36(maxNum + 1, 2);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int insertMaterialCategory(JSONObject obj, HttpServletRequest request)throws Exception {
        MaterialCategory materialCategory = JSONObject.parseObject(obj.toJSONString(), MaterialCategory.class);
        materialCategory.setCreateTime(new Date());
        materialCategory.setUpdateTime(new Date());
        // 自动生成编号并设置层级
        materialCategory.setDeleteFlag(BusinessConstants.DELETE_FLAG_EXISTS);
        if (materialCategory.getParentId() == null) {
            // 一级类目
            materialCategory.setSerialNo(generateLevel1SerialNo());
            materialCategory.setCategoryLevel((short) 1);
        } else {
            // 二级类目：校验父级是否为一级（最多2级）
            MaterialCategory parent = materialCategoryMapper.selectByPrimaryKey(materialCategory.getParentId());
            if (parent == null) {
                throw new BusinessRunTimeException(ExceptionConstants.MATERIAL_CATEGORY_ADD_FAILED_CODE, "所选上级目录不存在");
            }
            if (parent.getParentId() != null) {
                throw new BusinessRunTimeException(ExceptionConstants.MATERIAL_CATEGORY_ADD_FAILED_CODE, "类目层级最多为2级，不能在二级类目下添加子类目");
            }
            // 父级编号必须有效（兼容旧格式编号）
            if (parent.getSerialNo() == null || parent.getSerialNo().isEmpty()) {
                throw new BusinessRunTimeException(ExceptionConstants.MATERIAL_CATEGORY_ADD_FAILED_CODE, "父级类目编号格式异常，无法生成子类目编号");
            }
            materialCategory.setSerialNo(generateLevel2SerialNo(materialCategory.getParentId(), parent.getSerialNo()));
            materialCategory.setCategoryLevel((short) 2);
        }
        int result=0;
        try{
            result=materialCategoryMapper.insertSelective(materialCategory);
            logService.insertLog("商品类型",
                    new StringBuffer(BusinessConstants.LOG_OPERATION_TYPE_ADD).append(materialCategory.getName()).toString(), request);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return result;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int updateMaterialCategory(JSONObject obj, HttpServletRequest request) throws Exception{
        MaterialCategory materialCategory = JSONObject.parseObject(obj.toJSONString(), MaterialCategory.class);
        materialCategory.setUpdateTime(new Date());
        int result=0;
        try{
            result=materialCategoryMapperEx.editMaterialCategory(materialCategory);
            logService.insertLog("商品类型",
                    new StringBuffer(BusinessConstants.LOG_OPERATION_TYPE_EDIT).append(materialCategory.getName()).toString(), request);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return result;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int deleteMaterialCategory(Long id, HttpServletRequest request)throws Exception {
        return batchDeleteMaterialCategoryByIds(id.toString());
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int batchDeleteMaterialCategory(String ids, HttpServletRequest request)throws Exception {
        return batchDeleteMaterialCategoryByIds(ids);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int batchDeleteMaterialCategoryByIds(String ids) throws Exception {
        int result=0;
        String [] idArray=ids.split(",");
        //校验产品表	jsh_material
        List<Material> materialList=null;
        try{
            materialList= materialMapperEx.getMaterialListByCategoryIds(idArray);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        if(materialList!=null&&materialList.size()>0){
            logger.error("异常码[{}],异常提示[{}],参数,CategoryIds[{}]",
                    ExceptionConstants.DELETE_FORCE_CONFIRM_CODE,ExceptionConstants.DELETE_FORCE_CONFIRM_MSG,ids);
            throw new BusinessRunTimeException(ExceptionConstants.DELETE_FORCE_CONFIRM_CODE,
                    ExceptionConstants.DELETE_FORCE_CONFIRM_MSG);
        }
        StringBuffer sb = new StringBuffer();
        sb.append(BusinessConstants.LOG_OPERATION_TYPE_DELETE);
        List<MaterialCategory> list = getMaterialCategoryListByIds(ids);
        for(MaterialCategory materialCategory: list){
            sb.append("[").append(materialCategory.getName()).append("]");
        }
        logService.insertLog("商品类型", sb.toString(),
                ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest());
        //更新时间
        Date updateDate =new Date();
        //更新人
        User userInfo=userService.getCurrentUser();
        Long updater=userInfo==null?null:userInfo.getId();
        String strArray[]=ids.split(",");
        if(strArray.length<1){
            return 0;
        }
        List<MaterialCategory> mcList = materialCategoryMapperEx.getMaterialCategoryListByCategoryIds(idArray);
        if(mcList!=null && mcList.size()>0) {
            logger.error("异常码[{}],异常提示[{}]",
                    ExceptionConstants.MATERIAL_CATEGORY_CHILD_NOT_SUPPORT_DELETE_CODE,ExceptionConstants.MATERIAL_CATEGORY_CHILD_NOT_SUPPORT_DELETE_MSG);
            throw new BusinessRunTimeException(ExceptionConstants.MATERIAL_CATEGORY_CHILD_NOT_SUPPORT_DELETE_CODE,
                    ExceptionConstants.MATERIAL_CATEGORY_CHILD_NOT_SUPPORT_DELETE_MSG);
        } else {
            result=materialCategoryMapperEx.batchDeleteMaterialCategoryByIds(updateDate,updater,strArray);
        }
        return result;
    }

    public int checkIsNameExist(Long id, String name, Long parentId)throws Exception {
        MaterialCategoryExample example = new MaterialCategoryExample();
        if(parentId!=null) {
            example.createCriteria().andIdNotEqualTo(id).andNameEqualTo(name).andParentIdEqualTo(parentId).andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        } else {
            example.createCriteria().andIdNotEqualTo(id).andNameEqualTo(name).andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        }
        List<MaterialCategory> list=null;
        try{
            list= materialCategoryMapper.selectByExample(example);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list==null?0:list.size();
    }

    public List<MaterialCategory> findById(Long id)throws Exception {
        List<MaterialCategory> list=null;
        if(id!=null) {
            MaterialCategoryExample example = new MaterialCategoryExample();
            example.createCriteria().andIdEqualTo(id);
            try{
                list=materialCategoryMapper.selectByExample(example);
            }catch(Exception e){
                JshException.readFail(logger, e);
            }
        }
        return list;
    }
    /**
     * description:
     * 获取商品类别树数据
     */
    public List<TreeNode> getMaterialCategoryTree(Long id) throws Exception{
        List<TreeNode> list=null;
        try{
            list=materialCategoryMapperEx.getNodeTree(id);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
       return list;
    }
    /**
     * 根据商品类别编号判断商品类别是否已存在
     * */
    public void  checkMaterialCategorySerialNo(MaterialCategory mc)throws Exception {
        if(mc==null){
            return;
        }
        if(StringUtil.isEmpty(mc.getSerialNo())){
            return;
        }
        //根据商品类别编号查询商品类别
        List<MaterialCategory> mList=null;
        try{
            mList= materialCategoryMapperEx.getMaterialCategoryBySerialNo(mc.getSerialNo(), mc.getId());
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        if(mList==null||mList.size()<1){
            //未查询到对应数据，编号可用
            return;
        }
        if(mList.size()>1){
            //查询到的数据条数大于1，编号已存在
            throw new BusinessRunTimeException(ExceptionConstants.MATERIAL_CATEGORY_SERIAL_ALREADY_EXISTS_CODE,
                    ExceptionConstants.MATERIAL_CATEGORY_SERIAL_ALREADY_EXISTS_MSG);
        }
        if(mc.getId()==null){
            //新增时，编号已存在
            throw new BusinessRunTimeException(ExceptionConstants.MATERIAL_CATEGORY_SERIAL_ALREADY_EXISTS_CODE,
                    ExceptionConstants.MATERIAL_CATEGORY_SERIAL_ALREADY_EXISTS_MSG);
        }
        /**
         * 包装类型用equals来比较
         * */
        if(mc.getId().equals(mList.get(0).getId())){
            //修改时，相同编号，id不同
            throw new BusinessRunTimeException(ExceptionConstants.MATERIAL_CATEGORY_SERIAL_ALREADY_EXISTS_CODE,
                    ExceptionConstants.MATERIAL_CATEGORY_SERIAL_ALREADY_EXISTS_MSG);
        }
    }

    /**
     * 根据名称获取类型
     * @param name
     */
    public Long getCategoryIdByName(String name){
        Long categoryId = null;
        MaterialCategoryExample example = new MaterialCategoryExample();
        example.createCriteria().andNameEqualTo(name).andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<MaterialCategory> list = materialCategoryMapper.selectByExample(example);
        if(list!=null && list.size()>0) {
            categoryId = list.get(0).getId();
        }
        return categoryId;
    }
}
