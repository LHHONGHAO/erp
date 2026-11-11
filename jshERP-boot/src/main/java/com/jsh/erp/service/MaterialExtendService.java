package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.jsh.erp.constants.BusinessConstants;
import com.jsh.erp.datasource.entities.Material;
import com.jsh.erp.datasource.entities.MaterialExample;
import com.jsh.erp.datasource.entities.MaterialExtend;
import com.jsh.erp.datasource.entities.Sku;
import com.jsh.erp.datasource.entities.SkuSpec;
import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.MaterialExtendMapperEx;
import com.jsh.erp.datasource.mappers.MaterialMapper;
import com.jsh.erp.datasource.mappers.SkuMapper;
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


/**
 * 商品 SKU 服务（唯一存储：sku / sku_spec）
 * 对外保留原有方法签名，内部统一读写 sku，jsh_material_extend 已彻底移除。
 */
@Service
public class MaterialExtendService {
    private Logger logger = LoggerFactory.getLogger(MaterialExtendService.class);

    @Resource
    private SkuMapper skuMapper;
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
    @Resource
    private SkuService skuService;

    public MaterialExtend getMaterialExtend(long id)throws Exception {
        MaterialExtend result=null;
        try{
            result=toExtend(skuMapper.selectByPrimaryKey(id));
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
                    if(!isExistingId(tempId)){
                        insertedJson.add(tempJson);
                    } else {
                        updatedJson.add(tempJson);
                    }
                }
                //针对多属性商品要考虑到有SKU被删的情况，需要和原来的SKU明细进行对比
                if(StringUtil.isNotEmpty(obj.getString("manySku"))) {
                    //1.先查询原来的SKU列表
                    List<MaterialExtendVo4List> meList = materialExtendMapperEx.getDetailList(materialId);
                    //2.构造新的SKU id列表map（只保留真实ID）
                    Map<Long, Long> idMap = new HashMap<>();
                    for (int i = 0; i < meArr.size(); i++) {
                        JSONObject tempJson = meArr.getJSONObject(i);
                        String tempId = tempJson.getString("id");
                        if(isExistingId(tempId)) {
                            Long existingId = Long.parseLong(tempId);
                            idMap.put(existingId, existingId);
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
        User user = userService.getCurrentUser();
        String serial = user != null ? user.getLoginName() : null;
        Long tenantId = user != null ? user.getTenantId() : null;
        Set<String> generatedSkuCodes = new HashSet<>();
        Material material = materialMapper.selectByPrimaryKey(materialId);
        String materialCode = material != null ? material.getCode() : null;
        Long categoryId = material != null ? material.getCategoryId() : null;
        if (null != insertedJson) {
            for (int i = 0; i < insertedJson.size(); i++) {
                JSONObject tempJson = insertedJson.getJSONObject(i);
                String specValues = tempJson.getString("specValues");
                String skuCode = StringUtil.isNotEmpty(materialCode)
                        ? generateSkuCode(categoryId, materialId, materialCode, specValues) : null;
                if (StringUtil.isNotEmpty(skuCode) && !generatedSkuCodes.add(skuCode)) {
                    throw new BusinessRunTimeException(400,
                            "商品存在重复规格组合，SKU编码重复：" + tempJson.getString("sku"));
                }
                Sku sku = new Sku();
                sku.setSpuId(materialId);
                sku.setSkuCode(skuCode);
                sku.setPurchaseDecimal(tempJson.getBigDecimal("purchaseDecimal"));
                sku.setCommodityDecimal(tempJson.getBigDecimal("commodityDecimal"));
                sku.setWholesaleDecimal(tempJson.getBigDecimal("wholesaleDecimal"));
                sku.setMarketPrice(tempJson.getBigDecimal("marketPrice"));
                sku.setLowDecimal(tempJson.getBigDecimal("lowDecimal"));
                sku.setImageUrl(tempJson.getString("image"));
                sku.setDefaultFlag("0");
                sku.setTenantId(tenantId);
                sku.setCreateSerial(serial);
                sku.setUpdateSerial(serial);
                skuService.saveSku(sku, buildSpecs(specValues));
            }
        }
        if (null != updatedJson) {
            for (int i = 0; i < updatedJson.size(); i++) {
                JSONObject tempJson = updatedJson.getJSONObject(i);
                Long id = tempJson.getLong("id");
                Sku current = skuMapper.selectByPrimaryKey(id);
                if (current == null) {
                    continue;
                }
                String specValues = tempJson.getString("specValues");
                // 忽略客户端旧码，统一按新版规则生成 SKU 码
                String skuCode = StringUtil.isNotEmpty(materialCode)
                        ? generateSkuCode(categoryId, materialId, materialCode, specValues) : null;
                if (StringUtil.isNotEmpty(skuCode) && !generatedSkuCodes.add(skuCode)) {
                    throw new BusinessRunTimeException(400,
                            "商品存在重复规格组合，SKU编码重复：" + tempJson.getString("sku"));
                }
                Sku sku = new Sku();
                sku.setId(id);
                sku.setSpuId(materialId);
                sku.setSkuCode(skuCode);
                sku.setPurchaseDecimal(tempJson.getBigDecimal("purchaseDecimal"));
                sku.setCommodityDecimal(tempJson.getBigDecimal("commodityDecimal"));
                sku.setWholesaleDecimal(tempJson.getBigDecimal("wholesaleDecimal"));
                sku.setMarketPrice(tempJson.getBigDecimal("marketPrice"));
                sku.setLowDecimal(tempJson.getBigDecimal("lowDecimal"));
                sku.setImageUrl(tempJson.getString("image"));
                sku.setUpdateSerial(serial);
                skuService.saveSku(sku, buildSpecs(specValues));
                //金额为空时此处显式置空
                skuMapper.updatePrices(sku);
            }
        }
        //处理SKU的排序，基本单位排第一个
        if (null != sortJson && sortJson.size()>0) {
            for (int i = 0; i < sortJson.size(); i++) {
                JSONObject tempSortJson = sortJson.getJSONObject(i);
                if(StringUtil.isExist(tempSortJson.get("id")) && StringUtil.isExist(tempSortJson.get("defaultFlag"))) {
                    skuMapper.updateDefaultFlag(tempSortJson.getLong("id"), tempSortJson.getString("defaultFlag"));
                }
            }
        } else {
            //新增的时候将第一条记录设置为默认基本单位
            List<MaterialExtend> meList = materialExtendMapperEx.selectListBySpuId(materialId);
            if(meList!=null) {
                for(int i=0; i<meList.size(); i++) {
                    skuMapper.updateDefaultFlag(meList.get(i).getId(), i==0 ? "1" : "0");
                }
            }
        }
        return null;
    }

    /** 前端新增行的临时ID不是纯数字，仅纯数字且不超过19位视为已落库的SKU主键 */
    private boolean isExistingId(String id) {
        return id != null && id.matches("\\d{1,19}");
    }

    /**
     * 从 specValues 还原规格明细，顺序与录入顺序一致（saveSku 会重排 sort_no）
     * specValues 元素格式：{attributeName, valueText, image, valueCode, ...}
     */
    private List<SkuSpec> buildSpecs(String specValues) {
        List<SkuSpec> specs = new ArrayList<>();
        if (StringUtil.isEmpty(specValues)) {
            return specs;
        }
        try {
            JSONArray array = JSONArray.parseArray(specValues);
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String valueText = item.getString("valueText");
                if (StringUtil.isEmpty(valueText)) {
                    valueText = item.getString("value");
                }
                if (StringUtil.isEmpty(valueText)) {
                    continue;
                }
                String specName = item.getString("attributeName");
                if (StringUtil.isEmpty(specName)) {
                    specName = item.getString("name");
                }
                SkuSpec spec = new SkuSpec();
                spec.setSpecName(StringUtil.isEmpty(specName) ? ("规格" + (i + 1)) : specName);
                spec.setSpecValue(valueText);
                spec.setImageUrl(item.getString("image"));
                specs.add(spec);
            }
        } catch (Exception e) {
            logger.error("解析商品规格值失败 specValues={}", specValues, e);
        }
        return specs;
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
        String skuCode = materialExtend.getSkuCode();
        if (StringUtil.isEmpty(skuCode) && materialExtend.getMaterialId() != null) {
            Material material = materialMapper.selectByPrimaryKey(materialExtend.getMaterialId());
            if (material != null && StringUtil.isNotEmpty(material.getCode())) {
                skuCode = generateSkuCode(material.getCategoryId(), material.getId(), material.getCode(),
                        materialExtend.getSpecValues());
            }
        }
        Sku sku = new Sku();
        sku.setSpuId(materialExtend.getMaterialId());
        sku.setSkuCode(skuCode);
        sku.setPurchaseDecimal(materialExtend.getPurchaseDecimal());
        sku.setCommodityDecimal(materialExtend.getCommodityDecimal());
        sku.setWholesaleDecimal(materialExtend.getWholesaleDecimal());
        sku.setMarketPrice(materialExtend.getMarketPrice());
        sku.setLowDecimal(materialExtend.getLowDecimal());
        sku.setImageUrl(materialExtend.getImgUrl());
        sku.setDefaultFlag(StringUtil.isEmpty(materialExtend.getDefaultFlag()) ? "1" : materialExtend.getDefaultFlag());
        sku.setTenantId(materialExtend.getTenantId() != null ? materialExtend.getTenantId()
                : (user != null ? user.getTenantId() : null));
        sku.setCreateSerial(user != null ? user.getLoginName() : null);
        sku.setUpdateSerial(user != null ? user.getLoginName() : null);
        skuService.saveSku(sku, buildSpecs(materialExtend.getSpecValues()));
        return 1;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int updateMaterialExtend(MaterialExtend materialExtend) throws Exception{
        if (materialExtend.getId() == null) {
            return 0;
        }
        User user = userService.getCurrentUser();
        Sku patch = new Sku();
        patch.setId(materialExtend.getId());
        patch.setSkuCode(materialExtend.getSkuCode());
        patch.setPurchaseDecimal(materialExtend.getPurchaseDecimal());
        patch.setCommodityDecimal(materialExtend.getCommodityDecimal());
        patch.setWholesaleDecimal(materialExtend.getWholesaleDecimal());
        patch.setMarketPrice(materialExtend.getMarketPrice());
        patch.setLowDecimal(materialExtend.getLowDecimal());
        patch.setImageUrl(materialExtend.getImgUrl());
        patch.setDefaultFlag(materialExtend.getDefaultFlag());
        patch.setUpdateSerial(user != null ? user.getLoginName() : null);
        int res = 0;
        try{
            res = skuMapper.updateExtendFields(patch);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return res;
    }

    /**
     * 价格显式覆盖（允许置空）
     */
    public int specialUpdatePrice(MaterialExtend materialExtend) {
        return materialExtendMapperEx.specialUpdatePrice(materialExtend);
    }

    public int checkIsBarCodeExist(Long id, String barCode)throws Exception {
        // bar_code 已移到 jsh_material 主表，改为查询主表
        Long excludeMaterialId = null;
        if (id != null && id > 0) {
            Sku sku = skuMapper.selectByPrimaryKey(id);
            if (sku != null) {
                excludeMaterialId = sku.getSpuId();
            }
        }
        MaterialExample example = new MaterialExample();
        MaterialExample.Criteria criteria = example.createCriteria();
        criteria.andBarCodeEqualTo(barCode).andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        if (excludeMaterialId != null) {
            criteria.andIdNotEqualTo(excludeMaterialId);
        }
        List<Material> list = null;
        try{
            list = materialMapper.selectByExample(example);
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list==null?0:list.size();
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int deleteMaterialExtend(Long id, HttpServletRequest request)throws Exception {
        int result =0;
        try{
            result = skuMapper.softDelete(id);
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

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int batchDeleteMaterialExtendByMIds(String ids) throws Exception{
        String [] idArray=ids.split(",");
        int result = 0;
        try{
            result = materialExtendMapperEx.batchDeleteMaterialExtendByMIds(idArray);
        }catch(Exception e){
            JshException.writeFail(logger, e);
        }
        return result;
    }

    public int insertMaterialExtend(JSONObject obj, HttpServletRequest request) throws Exception{
        MaterialExtend materialExtend = JSONObject.parseObject(obj.toJSONString(), MaterialExtend.class);
        return insertMaterialExtend(materialExtend);
    }

    public int updateMaterialExtend(JSONObject obj, HttpServletRequest request)throws Exception {
        MaterialExtend materialExtend = JSONObject.parseObject(obj.toJSONString(), MaterialExtend.class);
        return updateMaterialExtend(materialExtend);
    }

    public List<MaterialExtend> getMaterialExtendByTenantAndTime(Long tenantId, Long lastTime, Long syncNum)throws Exception {
        List<MaterialExtend> list=new ArrayList<MaterialExtend>();
        try{
            //先获取最大的时间戳，再查两个时间戳之间的数据，这样同步能够防止丢失数据（应为时间戳有重复）
            Long maxTime = materialExtendMapperEx.getMaxTimeByTenantAndTime(tenantId, lastTime, syncNum);
            if(tenantId!=null && lastTime!=null && maxTime!=null) {
                list = materialExtendMapperEx.selectByTenantAndUpdateTime(tenantId, lastTime, maxTime);
            }
        }catch(Exception e){
            JshException.readFail(logger, e);
        }
        return list;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public Long selectIdByMaterialIdAndDefaultFlag(Long materialId, String defaultFlag) {
        Long id = materialExtendMapperEx.selectIdBySpuIdAndDefaultFlag(materialId, defaultFlag);
        return id != null ? id : 0L;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public Long selectIdByMaterialIdAndBarCode(Long materialId, String barCode) {
        // bar_code 已移到 jsh_material 主表，此处按商品查询默认SKU
        return selectIdByMaterialIdAndDefaultFlag(materialId, "1");
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public List<MaterialExtend> getListByMaterialIdAndDefaultFlagAndBarCode(Long materialId, String defaultFlag, String barCode) {
        return materialExtendMapperEx.selectListBySpuIdAndDefaultFlag(materialId, defaultFlag);
    }

    public MaterialExtend getInfoByBarCode(String barCode)throws Exception {
        // bar_code 已移到 jsh_material 主表，先按主表查商品，再取默认SKU
        MaterialExample mExample = new MaterialExample();
        mExample.createCriteria().andBarCodeEqualTo(barCode)
                .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<Material> materials = materialMapper.selectByExample(mExample);
        if(materials!=null && materials.size()>0) {
            List<MaterialExtend> list = materialExtendMapperEx.selectListBySpuId(materials.get(0).getId());
            if(list!=null && list.size()>0) {
                return list.get(0);
            }
        }
        return null;
    }

    public MaterialExtend getInfoBySkuCode(String skuCode)throws Exception {
        if(!StringUtil.isNotEmpty(skuCode)) {
            return null;
        }
        List<MaterialExtend> list = materialExtendMapperEx.selectBySkuCode(skuCode);
        return (list != null && list.size() > 0) ? list.get(0) : null;
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

    /** sku 实体转换为对外的 MaterialExtend 视图 */
    private MaterialExtend toExtend(Sku sku) {
        if (sku == null) {
            return null;
        }
        MaterialExtend me = new MaterialExtend();
        me.setId(sku.getId());
        me.setMaterialId(sku.getSpuId());
        me.setSpecValues(sku.getSpecJson());
        me.setSkuCode(sku.getSkuCode());
        me.setPurchaseDecimal(sku.getPurchaseDecimal());
        me.setCommodityDecimal(sku.getCommodityDecimal());
        me.setWholesaleDecimal(sku.getWholesaleDecimal());
        me.setMarketPrice(sku.getMarketPrice());
        me.setLowDecimal(sku.getLowDecimal());
        me.setDefaultFlag(sku.getDefaultFlag());
        me.setImgUrl(sku.getImageUrl());
        me.setCreateTime(sku.getCreateTime());
        me.setCreateSerial(sku.getCreateSerial());
        me.setUpdateSerial(sku.getUpdateSerial());
        me.setUpdateTime(sku.getUpdateTime() != null ? sku.getUpdateTime().getTime() : null);
        me.setTenantId(sku.getTenantId());
        me.setDeleteFlag(sku.getDeletedAt() == null ? BusinessConstants.DELETE_FLAG_EXISTS : BusinessConstants.DELETE_FLAG_DELETED);
        return me;
    }
}
