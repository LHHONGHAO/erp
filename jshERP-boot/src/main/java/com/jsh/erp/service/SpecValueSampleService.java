package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.datasource.entities.SpecLearnLog;
import com.jsh.erp.datasource.entities.SpecValueAlias;
import com.jsh.erp.datasource.entities.SpecValueSample;
import com.jsh.erp.datasource.mappers.SpecLearnLogMapper;
import com.jsh.erp.datasource.mappers.SpecValueAliasMapper;
import com.jsh.erp.datasource.mappers.SpecValueSampleMapper;
import com.jsh.erp.utils.SpecValueNormalizer;
import com.jsh.erp.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 规格值样本库：识别时归一化比对，人工纠正时沉淀样本
 */
@Service
public class SpecValueSampleService {
    private Logger logger = LoggerFactory.getLogger(SpecValueSampleService.class);

    @Resource
    private SpecValueSampleMapper specValueSampleMapper;

    @Resource
    private SpecValueAliasMapper specValueAliasMapper;

    @Resource
    private SpecLearnLogMapper specLearnLogMapper;

    @Resource
    private UserService userService;

    /**
     * 规格值比对：精确匹配 -> 别名匹配 -> 数值模糊匹配（单位缺失/写法不同）
     */
    public JSONObject match(String rawText, Long categoryId, Long attributeId) {
        JSONObject result = new JSONObject();
        String normKey = SpecValueNormalizer.normKey(rawText);
        result.put("normKey", normKey);
        result.put("matched", false);
        if (normKey.isEmpty()) {
            return result;
        }
        Long tenantId = currentTenantId();
        try {
            SpecValueSample sample = specValueSampleMapper.selectByNormKey(normKey, attributeId, categoryId, tenantId);
            String matchType = "exact";
            SpecValueAlias alias = null;
            if (sample == null) {
                alias = specValueAliasMapper.selectByNormKey(normKey, attributeId, categoryId, tenantId);
                if (alias != null) {
                    sample = specValueSampleMapper.selectByPrimaryKey(alias.getSampleId());
                    matchType = "alias";
                    if (sample != null && categoryId != null && sample.getCategoryId() != null
                            && sample.getCategoryId() != 0L && !categoryId.equals(sample.getCategoryId())) {
                        sample = null;
                        alias = null;
                    }
                }
            }
            if (sample == null) {
                String numericKey = SpecValueNormalizer.numericKey(rawText);
                if (StringUtil.isNotEmpty(numericKey)) {
                    sample = specValueSampleMapper.selectByNumericKey(numericKey, attributeId, categoryId, tenantId);
                    matchType = "numeric";
                }
            }
            if (sample == null) {
                return result;
            }
            result.put("matched", true);
            result.put("sampleId", sample.getId());
            result.put("canonicalValue", sample.getCanonicalValue());
            result.put("matchType", matchType);
            result.put("confidence", sample.getConfidence());
            // 命中计数：数值模糊匹配置信度低一些
            specValueSampleMapper.increaseHitCount(sample.getId());
            if (alias != null) {
                specValueAliasMapper.increaseHitCount(alias.getId());
            }
        } catch (Exception e) {
            logger.warn("规格值样本比对失败：{}", e.getMessage());
        }
        return result;
    }

    /**
     * 以类别配置的规格值为标准，对识别文本做对齐：
     * 配置值优先 -> 样本库命中 -> 返回近似候选
     */
    public JSONObject align(String rawText, Long categoryId, Long attributeId, List<String> standards) {
        JSONObject result = new JSONObject();
        result.put("matched", false);
        result.put("value", "");
        result.put("matchType", "none");
        result.put("notConfigured", false);
        result.put("candidates", buildCandidates(rawText, standards));
        if (!StringUtil.isNotEmpty(rawText)) {
            return result;
        }
        // 1. 类别配置的标准写法优先
        String standard = SpecValueNormalizer.alignToStandard(rawText, standards);
        if (standard != null) {
            result.put("matched", true);
            result.put("value", standard);
            result.put("matchType", "config");
            return result;
        }
        // 2. 样本库命中
        JSONObject sample = match(rawText, categoryId, attributeId);
        if (sample.getBooleanValue("matched")) {
            String canonical = sample.getString("canonicalValue");
            String configured = SpecValueNormalizer.alignToStandard(canonical, standards);
            result.put("matched", true);
            result.put("value", configured != null ? configured : canonical);
            result.put("matchType", configured != null ? "sample" : "sample_unconfigured");
            result.put("notConfigured", configured == null && standards != null && !standards.isEmpty());
            return result;
        }
        return result;
    }

    /** 从配置值中挑出与原始文本相近的候选，供前端让用户选择 */
    private List<String> buildCandidates(String rawText, List<String> standards) {
        List<String> candidates = new ArrayList<>();
        if (standards == null || standards.isEmpty()) {
            return candidates;
        }
        String normRaw = SpecValueNormalizer.normKey(rawText);
        String numericRaw = SpecValueNormalizer.numericKey(rawText);
        for (String standard : standards) {
            if (standard == null || standard.trim().isEmpty()) {
                continue;
            }
            String normStd = SpecValueNormalizer.normKey(standard);
            boolean similar = normStd.equals(normRaw)
                    || (numericRaw != null && numericRaw.equals(SpecValueNormalizer.numericKey(standard)))
                    || normStd.contains(normRaw) || normRaw.contains(normStd)
                    || normStd.startsWith(normRaw) || normRaw.startsWith(normStd);
            if (similar) {
                candidates.add(standard);
            }
            if (candidates.size() >= 5) {
                break;
            }
        }
        return candidates;
    }

    /**
     * 批量学习：商品保存时把「识别原文 -> 最终采纳值」一次性沉淀
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int learnBatch(JSONArray items, Long categoryId, Long materialId, Long userId) {
        int count = 0;
        if (items == null) {
            return count;
        }
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String rawText = item.getString("rawText");
            String correctedValue = item.getString("correctedValue");
            if (!StringUtil.isNotEmpty(rawText) || !StringUtil.isNotEmpty(correctedValue)) {
                continue;
            }
            if (SpecValueNormalizer.normKey(rawText).equals(SpecValueNormalizer.normKey(correctedValue))) {
                // 归一化后一致，无需沉淀别名
                continue;
            }
            try {
                learn(rawText, correctedValue, categoryId, item.getLong("attributeId"), materialId, userId);
                count++;
            } catch (Exception e) {
                logger.warn("批量学习规格值失败：{}", e.getMessage());
            }
        }
        return count;
    }

    /**
     * 学习一个规格值：rawText 为用户看到的原始写法，correctedValue 为纠正后的标准写法
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public JSONObject learn(String rawText, String correctedValue, Long categoryId, Long attributeId,
                            Long materialId, Long userId) {
        JSONObject result = new JSONObject();
        if (!StringUtil.isNotEmpty(rawText) && !StringUtil.isNotEmpty(correctedValue)) {
            result.put("success", false);
            result.put("message", "原始值和标准值不能同时为空");
            return result;
        }
        String raw = StringUtil.isNotEmpty(rawText) ? rawText.trim() : correctedValue.trim();
        String canonical = StringUtil.isNotEmpty(correctedValue) ? correctedValue.trim() : raw;
        String normKey = SpecValueNormalizer.normKey(canonical);
        if (normKey.isEmpty()) {
            normKey = SpecValueNormalizer.normKey(raw);
        }
        if (normKey.isEmpty()) {
            result.put("success", false);
            result.put("message", "规格值不能为空");
            return result;
        }
        Long attrId = attributeId == null ? 0L : attributeId;
        Long catId = categoryId == null ? 0L : categoryId;
        Long tenantId = currentTenantId();

        SpecValueSample sample = specValueSampleMapper.selectByNormKey(normKey, attrId, catId, tenantId);
        if (sample == null) {
            sample = new SpecValueSample();
            sample.setTenantId(tenantId == null ? 0L : tenantId);
            sample.setCategoryId(catId);
            sample.setAttributeId(attrId);
            sample.setCanonicalValue(canonical);
            sample.setNormKey(normKey);
            sample.setSource("manual");
            sample.setConfidence(BigDecimal.ONE);
            sample.setHitCount(1);
            sample.setStatus("0");
            sample.setDeleteFlag("0");
            try {
                specValueSampleMapper.insertSelective(sample);
            } catch (DuplicateKeyException e) {
                sample = specValueSampleMapper.selectByNormKey(normKey, attrId, catId, tenantId);
            }
        } else {
            SpecValueSample update = new SpecValueSample();
            update.setId(sample.getId());
            update.setCanonicalValue(canonical);
            update.setSource("manual");
            specValueSampleMapper.updateByPrimaryKeySelective(update);
        }

        // 原始写法与标准写法归一化后不同，才记别名
        String rawNormKey = SpecValueNormalizer.normKey(raw);
        if (sample != null && StringUtil.isNotEmpty(rawNormKey) && !rawNormKey.equals(sample.getNormKey())) {
            SpecValueAlias alias = specValueAliasMapper.selectByNormKey(rawNormKey, attrId, catId, tenantId);
            if (alias == null) {
                alias = new SpecValueAlias();
                alias.setTenantId(tenantId == null ? 0L : tenantId);
                alias.setSampleId(sample.getId());
                alias.setCategoryId(catId);
                alias.setAttributeId(attrId);
                alias.setRawText(raw);
                alias.setNormKey(rawNormKey);
                alias.setHitCount(1);
                alias.setDeleteFlag("0");
                try {
                    specValueAliasMapper.insertSelective(alias);
                } catch (DuplicateKeyException e) {
                    logger.info("规格值别名已存在：{}", rawNormKey);
                }
            } else {
                specValueAliasMapper.increaseHitCount(alias.getId());
            }
        }

        try {
            SpecLearnLog log = new SpecLearnLog();
            log.setRawText(raw);
            log.setAttrId(attrId);
            log.setOriginalValue(raw);
            log.setCorrectedValue(canonical);
            log.setMaterialId(materialId);
            log.setUserId(resolveUserId(userId));
            log.setDeleteFlag("0");
            specLearnLogMapper.insertSelective(log);
        } catch (Exception e) {
            logger.warn("记录规格值学习日志失败：{}", e.getMessage());
        }

        result.put("success", true);
        result.put("sampleId", sample == null ? null : sample.getId());
        result.put("canonicalValue", canonical);
        result.put("normKey", normKey);
        return result;
    }

    public List<SpecValueSample> list(String keyword, Integer limit) {
        try {
            return specValueSampleMapper.selectList(keyword, limit == null || limit <= 0 ? 100 : Math.min(limit, 500));
        } catch (Exception e) {
            logger.warn("查询规格值样本失败：{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * OCR 识别结果归一化：命中样本库时把 standard 回填为标准写法
     */
    public void enhanceRecognizedStandard(Map<String, String> result) {
        if (result == null) {
            return;
        }
        String standard = result.get("standard");
        if (!StringUtil.isNotEmpty(standard)) {
            return;
        }
        JSONObject match = match(standard, null, null);
        if (match.getBooleanValue("matched")) {
            result.put("standardRaw", standard);
            result.put("standard", match.getString("canonicalValue"));
        }
    }

    private Long resolveUserId(Long userId) {
        if (userId != null) {
            return userId;
        }
        try {
            return userService.getCurrentUser().getId();
        } catch (Exception e) {
            return null;
        }
    }

    private Long currentTenantId() {
        try {
            return userService.getCurrentUser().getTenantId();
        } catch (Exception e) {
            return 0L;
        }
    }
}
