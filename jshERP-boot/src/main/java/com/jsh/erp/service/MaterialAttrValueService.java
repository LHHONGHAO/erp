package com.jsh.erp.service;

import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.constants.BusinessConstants;
import com.jsh.erp.datasource.entities.MaterialAttrValue;
import com.jsh.erp.datasource.entities.MaterialAttribute;
import com.jsh.erp.datasource.entities.MaterialAttributeExample;
import com.jsh.erp.datasource.mappers.MaterialAttrValueMapper;
import com.jsh.erp.datasource.mappers.MaterialAttributeMapper;
import com.jsh.erp.exception.BusinessRunTimeException;
import com.jsh.erp.exception.JshException;
import com.jsh.erp.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品属性值字典 Service
 * 结构化存储颜色/尺码等属性值，value_code 稳定不变，用于 SKU 编码与溯源
 */
@Service
public class MaterialAttrValueService {
    private Logger logger = LoggerFactory.getLogger(MaterialAttrValueService.class);

    @Resource
    private MaterialAttrValueMapper materialAttrValueMapper;

    @Resource
    private MaterialAttributeMapper materialAttributeMapper;

    @Resource
    private LogService logService;

    public MaterialAttrValue getInfoById(Long id) {
        try {
            return materialAttrValueMapper.selectByPrimaryKey(id);
        } catch (Exception e) {
            JshException.readFail(logger, e);
            return null;
        }
    }

    /** 获取全部启用的属性值（前端下拉用） */
    public List<MaterialAttrValue> getAllEnabled() {
        try {
            return materialAttrValueMapper.selectAllEnabled();
        } catch (Exception e) {
            JshException.readFail(logger, e);
            return null;
        }
    }

    /** 按属性名id查属性值列表 */
    public List<MaterialAttrValue> getByAttributeId(Long attributeId) {
        try {
            return materialAttrValueMapper.selectByAttributeId(attributeId);
        } catch (Exception e) {
            JshException.readFail(logger, e);
            return null;
        }
    }

    /** 按品类和规格名获取已保存的规格值 */
    public List<MaterialAttrValue> getByAttributeIdAndCategoryId(Long attributeId, Long categoryId) {
        if (attributeId == null || categoryId == null) return java.util.Collections.emptyList();
        try {
            List<MaterialAttrValue> values = materialAttrValueMapper.selectByAttributeIdAndCategoryId(attributeId, categoryId);
            Map<String, MaterialAttrValue> firstValueByKey = new LinkedHashMap<>();
            if (values != null) {
                for (MaterialAttrValue value : values) {
                    if (value != null && StringUtil.isNotEmpty(value.getValueText())) {
                        firstValueByKey.putIfAbsent(normalizeSpecValueKey(value.getValueText()), value);
                    }
                }
            }
            return new java.util.ArrayList<>(firstValueByKey.values());
        } catch (Exception e) {
            JshException.readFail(logger, e);
            return null;
        }
    }

    /** 按 value_code 反查（SKU 溯源用） */
    public MaterialAttrValue getByValueCode(String valueCode) {
        if (!StringUtil.isNotEmpty(valueCode)) {
            return null;
        }
        try {
            return materialAttrValueMapper.selectByValueCode(valueCode);
        } catch (Exception e) {
            JshException.readFail(logger, e);
            return null;
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int insertMaterialAttrValue(JSONObject obj, HttpServletRequest request) throws Exception {
        MaterialAttrValue m = JSONObject.parseObject(obj.toJSONString(), MaterialAttrValue.class);
        if (!StringUtil.isNotEmpty(m.getValueCode())) {
            throw new BusinessRunTimeException(400, "属性值编码不能为空");
        }
        if (countByValueCode(m.getValueCode(), null) > 0) {
            throw new BusinessRunTimeException(400, "属性值编码已存在：" + m.getValueCode());
        }
        if (m.getStatus() == null) {
            m.setStatus("0");
        }
        if (m.getDeleteFlag() == null) {
            m.setDeleteFlag("0");
        }
        try {
            materialAttrValueMapper.insertSelective(m);
            logService.insertLog("商品属性值",
                    new StringBuffer(BusinessConstants.LOG_OPERATION_TYPE_ADD).append(m.getValueText()).toString(), request);
            return 1;
        } catch (Exception e) {
            JshException.writeFail(logger, e);
            return 0;
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int updateMaterialAttrValue(JSONObject obj, HttpServletRequest request) throws Exception {
        MaterialAttrValue m = JSONObject.parseObject(obj.toJSONString(), MaterialAttrValue.class);
        if (m.getId() == null) {
            throw new BusinessRunTimeException(400, "id不能为空");
        }
        if (StringUtil.isNotEmpty(m.getValueCode()) && countByValueCode(m.getValueCode(), m.getId()) > 0) {
            throw new BusinessRunTimeException(400, "属性值编码已存在：" + m.getValueCode());
        }
        try {
            materialAttrValueMapper.updateByPrimaryKeySelective(m);
            logService.insertLog("商品属性值",
                    new StringBuffer(BusinessConstants.LOG_OPERATION_TYPE_EDIT).append(m.getValueText()).toString(), request);
            return 1;
        } catch (Exception e) {
            JshException.writeFail(logger, e);
            return 0;
        }
    }

    /** 逻辑删除（被引用过的属性值不能物理删，保证历史SKU溯源不断链） */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int deleteMaterialAttrValue(Long id, HttpServletRequest request) throws Exception {
        try {
            materialAttrValueMapper.updateDeleteFlagById(id);
            logService.insertLog("商品属性值",
                    new StringBuffer(BusinessConstants.LOG_OPERATION_TYPE_DELETE).append(id).toString(), request);
            return 1;
        } catch (Exception e) {
            JshException.writeFail(logger, e);
            return 0;
        }
    }

    /** Hide a common-value suggestion card while retaining the value row for SKU code lookups. */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int hideSuggestionCard(Long id, HttpServletRequest request) throws Exception {
        MaterialAttrValue selected = id == null ? null : materialAttrValueMapper.selectByPrimaryKey(id);
        if (selected == null || selected.getCategoryId() == null || selected.getAttributeId() == null) {
            return 0;
        }
        String selectedKey = normalizeSpecValueKey(selected.getValueText());
        List<MaterialAttrValue> categoryValues = materialAttrValueMapper.selectByAttributeIdAndCategoryId(
                selected.getAttributeId(), selected.getCategoryId());
        int hiddenCount = 0;
        if (categoryValues != null) {
            for (MaterialAttrValue value : categoryValues) {
                if (selectedKey.equals(normalizeSpecValueKey(value.getValueText()))) {
                    MaterialAttrValue update = new MaterialAttrValue();
                    update.setId(value.getId());
                    update.setStatus("1");
                    hiddenCount += materialAttrValueMapper.updateByPrimaryKeySelective(update);
                }
            }
        }
        if (hiddenCount > 0) {
            logService.insertLog("商品规格值卡片", new StringBuffer(BusinessConstants.LOG_OPERATION_TYPE_DELETE)
                    .append(selected.getValueText()).toString(), request);
        }
        return hiddenCount;
    }

    public int countByValueCode(String valueCode, Long excludeId) {
        try {
            return materialAttrValueMapper.countByValueCode(valueCode, excludeId);
        } catch (Exception e) {
            JshException.readFail(logger, e);
            return 0;
        }
    }

    /**
     * 根据属性名和属性值文本获取或创建 valueCode
     * 如果字典中不存在该属性值，则自动创建新记录
     *
     * @param attributeName 属性名（如"颜色"、"尺码"）
     * @param valueText     属性值文本（如"红"、"XL"）
     * @return value_code（如"01"），失败返回 null
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public String getOrCreateValueCode(String attributeName, String valueText) {
        return getOrCreateValueCode(null, attributeName, valueText, null);
    }

    /** 根据品类、规格名、规格值获取或创建稳定编码及子规格明细 */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public String getOrCreateValueCode(Long categoryId, String attributeName, String valueText, String valueDetail) {
        if (!StringUtil.isNotEmpty(attributeName) || !StringUtil.isNotEmpty(valueText)) {
            return null;
        }
        try {
            // 1. 查找属性名对应的 attribute_id
            MaterialAttributeExample attrExample = new MaterialAttributeExample();
            attrExample.createCriteria().andAttributeNameEqualTo(attributeName).andDeleteFlagNotEqualTo("1");
            List<MaterialAttribute> attrs = materialAttributeMapper.selectByExample(attrExample);
            Long attributeId = null;
            if (attrs != null && !attrs.isEmpty()) {
                attributeId = attrs.get(0).getId();
            } else {
                // 属性名不存在，自动创建
                MaterialAttribute newAttr = new MaterialAttribute();
                newAttr.setAttributeName(attributeName);
                newAttr.setAttributeValue("");
                newAttr.setDeleteFlag("0");
                materialAttributeMapper.insertSelective(newAttr);
                attributeId = newAttr.getId();
                logger.info("自动创建属性名: {}", attributeName);
            }

            // 2. 查找该属性下是否已有该属性值
            List<MaterialAttrValue> existValues = categoryId == null
                    ? materialAttrValueMapper.selectByAttributeId(attributeId)
                    : materialAttrValueMapper.selectByAttributeIdAndCategoryId(attributeId, categoryId);
            if (existValues != null) {
                for (MaterialAttrValue v : existValues) {
                    if (normalizeSpecValueKey(valueText).equals(normalizeSpecValueKey(v.getValueText()))) {
                        if (valueText.equals(v.getValueText()) && categoryId != null && StringUtil.isNotEmpty(valueDetail)
                                && !valueDetail.equals(v.getValueDetail())) {
                            MaterialAttrValue update = new MaterialAttrValue();
                            update.setId(v.getId());
                            update.setValueDetail(valueDetail);
                            materialAttrValueMapper.updateByPrimaryKeySelective(update);
                        }
                        return v.getValueCode();
                    }
                }
            }

            // 3. 不存在，创建新记录
            int seq = (existValues != null ? existValues.size() : 0) + 1;
            // 确保唯一性：如果 code 已存在，递增 seq 直到不冲突
            String valueCode = String.format("%02d", seq);
            while (countByValueCode(valueCode, null) > 0) {
                seq++;
                valueCode = String.format("%02d", seq);
            }

            MaterialAttrValue newValue = new MaterialAttrValue();
            newValue.setAttributeId(attributeId);
            newValue.setCategoryId(categoryId);
            newValue.setValueText(valueText);
            newValue.setValueDetail(valueDetail);
            newValue.setValueCode(valueCode);
            newValue.setSort(seq);
            newValue.setStatus("0");
            newValue.setDeleteFlag("0");
            materialAttrValueMapper.insertSelective(newValue);

            logger.info("自动创建属性值字典: {}={} -> {}", attributeName, valueText, valueCode);
            return valueCode;
        } catch (Exception e) {
            logger.error("获取或创建属性值编码失败: {}={}", attributeName, valueText, e);
            return null;
        }
    }

    /** Normalize numeric-only values and dimensions for common-card deduplication. */
    private String normalizeSpecValueKey(String valueText) {
        if (valueText == null) return "";
        String normalized = Normalizer.normalize(valueText.trim(), Normalizer.Form.NFKC);
        String compact = normalized.replaceAll("\\s+", "");
        if (!compact.matches("\\d+(?:\\.\\d+)?(?:[xX×*]\\d+(?:\\.\\d+)?)*")) {
            return normalized;
        }
        String[] parts = compact.split("[xX×*]");
        StringBuilder key = new StringBuilder();
        for (String part : parts) {
            if (key.length() > 0) key.append('x');
            try {
                key.append(new BigDecimal(part).stripTrailingZeros().toPlainString());
            } catch (NumberFormatException e) {
                return normalized;
            }
        }
        return key.toString();
    }
}
