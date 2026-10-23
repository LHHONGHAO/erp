package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.jsh.erp.constants.BusinessConstants;
import com.jsh.erp.constants.ExceptionConstants;
import com.jsh.erp.datasource.entities.Material;
import com.jsh.erp.datasource.entities.MaterialExample;
import com.jsh.erp.datasource.entities.MaterialExtend;
import com.jsh.erp.datasource.entities.MaterialExtendExample;
import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.MaterialExtendMapper;
import com.jsh.erp.datasource.mappers.MaterialExtendMapperEx;
import com.jsh.erp.datasource.mappers.MaterialMapper;
import com.jsh.erp.datasource.vo.MaterialExtendVo4List;
import com.jsh.erp.exception.BusinessRunTimeException;
import com.jsh.erp.exception.JshException;
import com.jsh.erp.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;


@Service
public class MaterialExtendService {
    private Logger logger = LoggerFactory.getLogger(MaterialExtendService.class);

    @Resource
    private MaterialExtendMapper materialExtendMapper;
    @Resource
    private MaterialExtendMapperEx materialExtendMapperEx;
    @Resource
    private UserService userService;
    @Resource
    private RedisService redisService;
    @Resource
    private SkuCodeGenerator skuCodeGenerator;
    @Resource
    private MaterialMapper materialMapper;
    @Resource
    private MaterialAttrValueService materialAttrValueService;
    
    public MaterialExtend getMaterialExtend(long id)throws Exception {
        MaterialExtend result=null;
        try{
            result=materialExtendMapper.selectByPrimaryKey(id);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return result;
    }
    public List<MaterialExtendVo4List> getDetailList(Long materialId) {
        List<MaterialExtendVo4List> list=null;
        try{
            list = materialExtendMapperEx.getDetailList(materialId);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    public List<MaterialExtend> getListByMIds(List<Long> idList) {
        List<MaterialExtend> meList = null;
        try{
            Long [] idArray= StringUtil.listToLongArray(idList);
            if(idArray!=null && idArray.length>0) {
                meList = materialExtendMapperEx.getListByMId(idArray);
            }
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return meList;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public String saveDetials(JSONObject obj, String sortList, Long materialId, String type) throws Exception {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        JSONArray meArr = obj.getJSONArray("meList");
        JSONArray insertedJson = new JSONArray();
        JSONArray updatedJson = new JSONArray();
        JSONArray deletedJson = obj.getJSONArray("meDeleteIdList");
        JSONArray sortJson = JSONArray.parseArray(sortList);
        if (null != meArr) {
            if("insert".equals(type)){
                for (int i = 0; i < meArr.size(); i++) {
                    JSONObject tempJson = meArr.getJSONObject(i);
                    insertedJson.add(tempJson);
                }
            } else if("update".equals(type)){
                for (int i = 0; i < meArr.size(); i++) {
                    JSONObject tempJson = meArr.getJSONObject(i);
                    String tempId = tempJson.getString("id");
                    if(tempId.length()>19){
                        insertedJson.add(tempJson);
                    } else {
                        updatedJson.add(tempJson);
                    }
                }
                //针对多属性商品要考虑到有SKU被删的情况，需要和原来的SKU明细进行对比
                if(StringUtil.isNotEmpty(obj.getString("manySku"))) {
                    //1.先查询原来的SKU列表
                    List<MaterialExtendVo4List> meList = materialExtendMapperEx.getDetailList(materialId);
                    //2.构造新的SKU id列表map（只保留真实ID，即长度<=19的）
                    Map<Long, Long> idMap = new HashMap<>();
                    for (int i = 0; i < meArr.size(); i++) {
                        JSONObject tempJson = meArr.getJSONObject(i);
                        String tempId = tempJson.getString("id");
                        if(tempId != null && tempId.length() <= 19) {
                            idMap.put(tempJson.getLong("id"), tempJson.getLong("id"));
                        }
                    }
                    //3.如果老的SKU id在新的里面不存在，则丢入删除队列
                    for(MaterialExtendVo4List me: meList) {
                        if(idMap.get(me.getId()) == null) {
                            deletedJson.add(me.getId());
                        }
                    }
                }
            }
        }
        if (null != deletedJson) {
            StringBuffer bf=new StringBuffer();
            for (int i = 0; i < deletedJson.size(); i++) {
                bf.append(deletedJson.getString(i));
                if(i<(deletedJson.size()-1)){
                    bf.append(",");
                }
            }
            if(StringUtil.isNotEmpty(bf.toString())) {
                this.batchDeleteMaterialExtendByIds(bf.toString(), request);
            }
        }
        Set<String> generatedSkuCodes = new HashSet<>();
        if (null != insertedJson) {
            Material material = materialMapper.selectByPrimaryKey(materialId);
            String materialCode = material != null ? material.getCode() : null;
            Long categoryId = material != null ? material.getCategoryId() : null;
            for (int i = 0; i < insertedJson.size(); i++) {
                MaterialExtend materialExtend = new MaterialExtend();
                JSONObject tempInsertedJson = JSONObject.parseObject(insertedJson.getString(i));
                materialExtend.setMaterialId(materialId);
                // 设置规格值组合JSON
                String specValues = tempInsertedJson.getString("specValues");
                if (StringUtils.isNotEmpty(specValues)) {
                    materialExtend.setSpecValues(specValues);
                }
                if (StringUtils.isNotEmpty(tempInsertedJson.getString("barCode"))) {
                    materialExtend.setBarCode(tempInsertedJson.getString("barCode"));
                }
                if (StringUtils.isNotEmpty(tempInsertedJson.getString("commodityUnit"))) {
                    materialExtend.setCommodityUnit(tempInsertedJson.getString("commodityUnit"));
                }
                if (StringUtils.isNotEmpty(tempInsertedJson.getString("sku"))) {
                    materialExtend.setSku(tempInsertedJson.getString("sku"));
                }
                // SKU码统一由后端按新版规则生成：SPU码 + 颜色码 + 尺码码
                String skuCode = StringUtil.isNotEmpty(materialCode)
                        ? generateSkuCode(categoryId, materialId, materialCode, specValues) : null;
                if (StringUtil.isNotEmpty(skuCode)) {
                    if (!generatedSkuCodes.add(skuCode)) {
                        throw new BusinessRunTimeException(400,
                                "商品存在重复规格组合，SKU编码重复：" + materialExtend.getSku());
                    }
                    materialExtend.setSkuCode(skuCode);
                }
                if (StringUtils.isNotEmpty(tempInsertedJson.getString("purchaseDecimal"))) {
                    materialExtend.setPurchaseDecimal(tempInsertedJson.getBigDecimal("purchaseDecimal"));
                }
                if (StringUtils.isNotEmpty(tempInsertedJson.getString("commodityDecimal"))) {
                    materialExtend.setCommodityDecimal(tempInsertedJson.getBigDecimal("commodityDecimal"));
                }
                if (StringUtils.isNotEmpty(tempInsertedJson.getString("wholesaleDecimal"))) {
                    materialExtend.setWholesaleDecimal(tempInsertedJson.getBigDecimal("wholesaleDecimal"));
                }
                if (StringUtils.isNotEmpty(tempInsertedJson.getString("lowDecimal"))) {
                    materialExtend.setLowDecimal(tempInsertedJson.getBigDecimal("lowDecimal"));
                }
                if (StringUtils.isNotEmpty(tempInsertedJson.getString("image"))) {
                    materialExtend.setImgUrl(tempInsertedJson.getString("image"));
                }
                this.insertMaterialExtend(materialExtend);
            }
        }
        if (null != updatedJson) {
            Material material = materialMapper.selectByPrimaryKey(materialId);
            String materialCode = material != null ? material.getCode() : null;
            Long categoryId = material != null ? material.getCategoryId() : null;
            for (int i = 0; i < updatedJson.size(); i++) {
                JSONObject tempUpdatedJson = JSONObject.parseObject(updatedJson.getString(i));
                MaterialExtend materialExtend = new MaterialExtend();
                materialExtend.setId(tempUpdatedJson.getLong("id"));
                // 设置规格值组合JSON
                String specValues = tempUpdatedJson.getString("specValues");
                if (StringUtils.isNotEmpty(specValues)) {
                    materialExtend.setSpecValues(specValues);
                }
                if (StringUtils.isNotEmpty(tempUpdatedJson.getString("barCode"))) {
                    materialExtend.setBarCode(tempUpdatedJson.getString("barCode"));
                }
                if (StringUtils.isNotEmpty(tempUpdatedJson.getString("commodityUnit"))) {
                    materialExtend.setCommodityUnit(tempUpdatedJson.getString("commodityUnit"));
                }
                if (StringUtils.isNotEmpty(tempUpdatedJson.getString("sku"))) {
                    materialExtend.setSku(tempUpdatedJson.getString("sku"));
                }
                // 忽略客户端旧码，统一按新版规则生成 SKU 码
                String skuCode = StringUtil.isNotEmpty(materialCode)
                        ? generateSkuCode(categoryId, materialId, materialCode, specValues) : null;
                if (StringUtil.isNotEmpty(skuCode)) {
                    if (!generatedSkuCodes.add(skuCode)) {
                        throw new BusinessRunTimeException(400,
                                "商品存在重复规格组合，SKU编码重复：" + materialExtend.getSku());
                    }
                    materialExtend.setSkuCode(skuCode);
                }
                if (StringUtils.isNotEmpty(tempUpdatedJson.getString("purchaseDecimal"))) {
                    materialExtend.setPurchaseDecimal(tempUpdatedJson.getBigDecimal("purchaseDecimal"));
                }
                if (StringUtils.isNotEmpty(tempUpdatedJson.getString("commodityDecimal"))) {
                    materialExtend.setCommodityDecimal(tempUpdatedJson.getBigDecimal("commodityDecimal"));
                }
                if (StringUtils.isNotEmpty(tempUpdatedJson.getString("wholesaleDecimal"))) {
                    materialExtend.setWholesaleDecimal(tempUpdatedJson.getBigDecimal("wholesaleDecimal"));
                }
                if (StringUtils.isNotEmpty(tempUpdatedJson.getString("lowDecimal"))) {
                    materialExtend.setLowDecimal(tempUpdatedJson.getBigDecimal("lowDecimal"));
                }
                if (StringUtils.isNotEmpty(tempUpdatedJson.getString("image"))) {
                    materialExtend.setImgUrl(tempUpdatedJson.getString("image"));
                }
                this.updateMaterialExtend(materialExtend);
                //如果金额为空，此处单独置空
                materialExtendMapperEx.specialUpdatePrice(materialExtend);
            }
        }
        //处理SKU的排序，基本单位排第一个
        if (null != sortJson && sortJson.size()>0) {
            //此处为更新的逻辑
            for (int i = 0; i < sortJson.size(); i++) {
                JSONObject tempSortJson = JSONObject.parseObject(sortJson.getString(i));
                MaterialExtend materialExtend = new MaterialExtend();
                if(StringUtil.isExist(tempSortJson.get("id"))) {
                    materialExtend.setId(tempSortJson.getLong("id"));
                }
                if(StringUtil.isExist(tempSortJson.get("defaultFlag"))) {
                    materialExtend.setDefaultFlag(tempSortJson.getString("defaultFlag"));
                }
                this.updateMaterialExtend(materialExtend);
            }
        } else {
            //新增的时候将第一条记录设置为默认基本单位
            MaterialExtendExample example = new MaterialExtendExample();
            example.createCriteria().andMaterialIdEqualTo(materialId).andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
            List<MaterialExtend> meList = materialExtendMapper.selectByExample(example);
            if(meList!=null) {
                for(int i=0; i<meList.size(); i++) {
                    MaterialExtend materialExtend = new MaterialExtend();
                    materialExtend.setId(meList.get(i).getId());
                    if(i==0) {
                        materialExtend.setDefaultFlag("1"); //默认
                    } else {
                        materialExtend.setDefaultFlag("0"); //非默认
                    }
                    this.updateMaterialExtend(materialExtend);
                }
            }
        }
        return null;
    }

    /**
     * 从 specModules 构建 "规格值文本 → valueCode" 的映射
     * 遍历前端传来的规格模块，为每个规格值自动查找/创建属性值字典记录
     */
    private Map<String, String> buildValueCodeMap(JSONObject obj) {
        Map<String, String> map = new HashMap<>();
        if (obj == null) return map;
        JSONArray specModules = obj.getJSONArray("specModules");
        Long categoryId = obj.getLong("categoryId");
        if (specModules == null || specModules.isEmpty()) return map;
        for (int i = 0; i < specModules.size(); i++) {
            JSONObject module = specModules.getJSONObject(i);
            String moduleName = module.getString("name");
            if (!StringUtil.isNotEmpty(moduleName)) continue;
            JSONArray values = module.getJSONArray("values");
            if (values == null || values.isEmpty()) continue;
            for (int j = 0; j < values.size(); j++) {
                JSONObject value = values.getJSONObject(j);
                String text = value.getString("text");
                if (StringUtil.isNotEmpty(text)) {
                    String detail = value.getJSONObject("subValues") == null ? null : value.getJSONObject("subValues").toJSONString();
                    String code = materialAttrValueService.getOrCreateValueCode(categoryId, moduleName, text, detail);
                    if (StringUtil.isNotEmpty(code)) {
                        map.put(text, code);
                    }
                }
            }
        }
        return map;
    }

    /** Generate a variant-level SKU code: SPU + color code + size code. */
    private String generateSkuCode(Long categoryId, Long materialId, String materialCode, String specValues) {
        List<String> orderedCodes = new ArrayList<>();
        if (StringUtil.isNotEmpty(specValues)) {
            try {
                JSONArray arr = JSONArray.parseArray(specValues);
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    String attributeName = item.getString("attributeName");
                    String valueText = item.getString("valueText");
                    if (!StringUtil.isNotEmpty(valueText)) {
                        continue;
                    }
                    // 优先使用前端带出的编码，缺失时查询/创建属性值字典
                    String valueCode = item.getString("valueCode");
                    if (!StringUtil.isNotEmpty(valueCode)) {
                        JSONObject subValues = item.getJSONObject("subValues");
                        String detail = subValues == null ? null : subValues.toJSONString();
                        valueCode = materialAttrValueService.getOrCreateValueCode(categoryId, attributeName, valueText, detail);
                    }
                    if (!StringUtil.isNotEmpty(valueCode)) {
                        continue;
                    }
                    orderedCodes.add(valueCode);
                }
            } catch (Exception e) {
                logger.error("解析规格值生成SKU码失败 materialId={} specValues={}", materialId, specValues, e);
            }
        }
        // 类别允许任意多个规格维度，所有值编码都需纳入SKU码以保持组合唯一。
        return skuCodeGenerator.generateSkuCode(materialCode, orderedCodes);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int insertMaterialExtend(MaterialExtend materialExtend)throws Exception {
        User user = userService.getCurrentUser();
        materialExtend.setDeleteFlag(BusinessConstants.DELETE_FLAG_EXISTS);
        materialExtend.setCreateTime(new Date());
        materialExtend.setUpdateTime(new Date().getTime());
        materialExtend.setCreateSerial(user.getLoginName());
        materialExtend.setUpdateSerial(user.getLoginName());
        int result =0;
        try{
            result= materialExtendMapper.insertSelective(materialExtend);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return result;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int updateMaterialExtend(MaterialExtend materialExtend) throws Exception{
        User user = userService.getCurrentUser();
        materialExtend.setUpdateTime(System.currentTimeMillis());
        materialExtend.setUpdateSerial(user.getLoginName());
        int res =0;
        try{
            res= materialExtendMapper.updateByPrimaryKeySelective(materialExtend);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return res;
    }

    public int checkIsBarCodeExist(Long id, String barCode)throws Exception {
        MaterialExtendExample example = new MaterialExtendExample();
        MaterialExtendExample.Criteria criteria = example.createCriteria();
        criteria.andBarCodeEqualTo(barCode);
        if (id > 0) {
            criteria.andIdNotEqualTo(id).andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        } else {
            criteria.andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        }
        List<MaterialExtend> list =null;
        try{
            list = materialExtendMapper.selectByExample(example);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list==null?0:list.size();
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int deleteMaterialExtend(Long id, HttpServletRequest request)throws Exception {
        int result =0;
        MaterialExtend materialExtend = new MaterialExtend();
        materialExtend.setId(id);
        materialExtend.setDeleteFlag(BusinessConstants.DELETE_FLAG_DELETED);
        Long userId = Long.parseLong(redisService.getObjectFromSessionByKey(request,"userId").toString());
        User user = userService.getUser(userId);
        materialExtend.setUpdateTime(new Date().getTime());
        materialExtend.setUpdateSerial(user.getLoginName());
        try{
            result= materialExtendMapper.updateByPrimaryKeySelective(materialExtend);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return result;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int batchDeleteMaterialExtendByIds(String ids, HttpServletRequest request) throws Exception{
        String [] idArray=ids.split(",");
        int result = 0;
        try{
            result = materialExtendMapperEx.batchDeleteMaterialExtendByIds(idArray);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return result;
    }

    public int insertMaterialExtend(JSONObject obj, HttpServletRequest request) throws Exception{
        MaterialExtend materialExtend = JSONObject.parseObject(obj.toJSONString(), MaterialExtend.class);
        int result=0;
        try{
            result = materialExtendMapper.insertSelective(materialExtend);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return result;
    }

    public int updateMaterialExtend(JSONObject obj, HttpServletRequest request)throws Exception {
        MaterialExtend materialExtend = JSONObject.parseObject(obj.toJSONString(), MaterialExtend.class);
        int result=0;
        try{
            result = materialExtendMapper.updateByPrimaryKeySelective(materialExtend);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return result;
    }

    public List<MaterialExtend> getMaterialExtendByTenantAndTime(Long tenantId, Long lastTime, Long syncNum)throws Exception {
        List<MaterialExtend> list=new ArrayList<MaterialExtend>();
        try{
            //先获取最大的时间戳，再查两个时间戳之间的数据，这样同步能够防止丢失数据（应为时间戳有重复）
            Long maxTime = materialExtendMapperEx.getMaxTimeByTenantAndTime(tenantId, lastTime, syncNum);
            if(tenantId!=null && lastTime!=null && maxTime!=null) {
                MaterialExtendExample example = new MaterialExtendExample();
                example.createCriteria().andTenantIdEqualTo(tenantId)
                        .andUpdateTimeGreaterThan(lastTime)
                        .andUpdateTimeLessThanOrEqualTo(maxTime);
                list=materialExtendMapper.selectByExample(example);
            }
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public Long selectIdByMaterialIdAndDefaultFlag(Long materialId, String defaultFlag) {
        Long id = 0L;
        MaterialExtendExample example = new MaterialExtendExample();
        example.createCriteria().andMaterialIdEqualTo(materialId).andDefaultFlagEqualTo(defaultFlag)
                                .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<MaterialExtend> list = materialExtendMapper.selectByExample(example);
        if(list!=null && list.size()>0) {
            id = list.get(0).getId();
        }
        return id;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public Long selectIdByMaterialIdAndBarCode(Long materialId, String barCode) {
        Long id = 0L;
        MaterialExtendExample example = new MaterialExtendExample();
        example.createCriteria().andMaterialIdEqualTo(materialId).andBarCodeEqualTo(barCode)
                .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<MaterialExtend> list = materialExtendMapper.selectByExample(example);
        if(list!=null && list.size()>0) {
            id = list.get(0).getId();
        }
        return id;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public List<MaterialExtend> getListByMaterialIdAndDefaultFlagAndBarCode(Long materialId, String defaultFlag, String barCode) {
        MaterialExtendExample example = new MaterialExtendExample();
        example.createCriteria().andMaterialIdEqualTo(materialId).andDefaultFlagEqualTo(defaultFlag).andBarCodeNotEqualTo(barCode)
                .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        return materialExtendMapper.selectByExample(example);
    }

    public MaterialExtend getInfoByBarCode(String barCode)throws Exception {
        MaterialExtendExample example = new MaterialExtendExample();
        example.createCriteria().andBarCodeEqualTo(barCode)
                .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<MaterialExtend> list = materialExtendMapper.selectByExample(example);
        if(list!=null && list.size()>0) {
            return list.get(0);
        } else {
            return null;
        }
    }

    public MaterialExtend getInfoBySkuCode(String skuCode)throws Exception {
        if(!StringUtil.isNotEmpty(skuCode)) {
            return null;
        }
        MaterialExtendExample example = new MaterialExtendExample();
        example.createCriteria().andSkuCodeEqualTo(skuCode)
                .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<MaterialExtend> list = materialExtendMapper.selectByExample(example);
        if(list!=null && list.size()>0) {
            return list.get(0);
        } else {
            return null;
        }
    }

    /**
     * 商品的副条码和数据库里面的商品条码存在重复（除自身商品之外）
     * bar_code已移到jsh_material主表，改为查询主表
     * @param manyBarCode
     * @param barCode
     * @return
     */
    public int getCountByManyBarCodeWithoutUs(String manyBarCode, String barCode) {
        // bar_code已移到jsh_material表，直接查询主表
        MaterialExample example = new MaterialExample();
        example.createCriteria().andBarCodeEqualTo(manyBarCode).andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<Material> list = materialMapper.selectByExample(example);
        if(list!=null && list.size()>0) {
            for(Material m: list) {
                if(m!=null && !barCode.equals(m.getBarCode())) {
                    return 1;
                }
            }
        }
        return 0;
    }
}
