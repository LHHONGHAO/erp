package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.datasource.entities.SpecLearnLog;
import com.jsh.erp.datasource.entities.SpecValueAlias;
import com.jsh.erp.datasource.entities.SpecValueCorrectionEvent;
import com.jsh.erp.datasource.entities.SpecValueCorrectionStat;
import com.jsh.erp.datasource.entities.SpecValueSample;
import com.jsh.erp.datasource.mappers.SpecLearnLogMapper;
import com.jsh.erp.datasource.mappers.SpecValueAliasMapper;
import com.jsh.erp.datasource.mappers.SpecValueCorrectionEventMapper;
import com.jsh.erp.datasource.mappers.SpecValueSampleMapper;
import com.jsh.erp.utils.SpecValueNormalizer;
import com.jsh.erp.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

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
    private SpecValueCorrectionEventMapper specValueCorrectionEventMapper;

    /** 基础属性学习门槛：同一识别原文下，某最终值至少出现 3 次且占比不低于 60% 才生效 */
    private static final int CORRECTION_MIN_COUNT = 3;
    private static final double CORRECTION_MIN_RATIO = 0.6D;
    /** 只统计最近 90 天的保存记录，避免历史习惯永久锁死 */
    private static final int CORRECTION_WINDOW_DAYS = 90;

    @Resource
    private SpecLearnLogMapper specLearnLogMapper;

    @Resource
    private UserService userService;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Resource
    private MaterialCategoryService materialCategoryService;

    /**
     * 规格值比对：精确匹配 -> 别名匹配 -> 数值模糊匹配（单位缺失/写法不同）
     */
    public JSONObject match(String rawText, Long categoryId, Long attributeId) {
        return match(rawText, categoryId, attributeId, "");
    }

    /**
     * 规格值比对：精确匹配 -> 别名匹配 -> 数值模糊匹配（单位缺失/写法不同）
     * childName 用于隔离同一规格名下的不同子项（尺寸下的被套/床单/枕套、花色下的颜色）
     */
    public JSONObject match(String rawText, Long categoryId, Long attributeId, String childName) {
        // 学习样本按一级类目隔离
        categoryId = materialCategoryService.resolveRootCategoryId(categoryId);
        String scopeChild = cut(childName == null ? "" : childName.trim(), 64);
        JSONObject result = new JSONObject();
        String normKey = SpecValueNormalizer.normKey(rawText);
        result.put("normKey", normKey);
        result.put("matched", false);
        if (normKey.isEmpty()) {
            return result;
        }
        Long tenantId = currentTenantId();
        try {
            SpecValueSample sample = specValueSampleMapper.selectByNormKey(normKey, attributeId, categoryId, scopeChild, tenantId);
            String matchType = "exact";
            SpecValueAlias alias = null;
            if (sample == null) {
                alias = specValueAliasMapper.selectByNormKey(normKey, attributeId, categoryId, scopeChild, tenantId);
                if (alias != null) {
                    sample = specValueSampleMapper.selectByPrimaryKey(alias.getSampleId());
                    matchType = "alias";
                    // 已被判定为错误并停用的样本不再参与回填
                    if (sample != null && (!"0".equals(sample.getStatus()) || "1".equals(sample.getDeleteFlag()))) {
                        sample = null;
                        alias = null;
                    }
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
                    sample = specValueSampleMapper.selectByNumericKey(numericKey, attributeId, categoryId, scopeChild, tenantId);
                    if (sample != null && !SpecValueNormalizer.numericKeyCompatible(rawText, sample.getCanonicalValue())) {
                        sample = null;
                    }
                    matchType = "numeric";
                }
            }
            // 非数值文本（面料/安全类别等）OCR 错字兜底：同作用域内取相似度最高的样本
            if (sample == null) {
                sample = fuzzyMatch(normKey, attributeId, categoryId, scopeChild, tenantId);
                if (sample != null) {
                    matchType = "similar";
                }
            }
            if (sample == null) {
                return result;
            }
            result.put("matched", true);
            result.put("sampleId", sample.getId());
            result.put("canonicalValue", sample.getCanonicalValue());
            result.put("matchType", matchType);
            result.put("confidence", "similar".equals(matchType) ? new BigDecimal("0.6") : sample.getConfidence());
            // 命中计数：数值模糊匹配置信度低一些
            // A fuzzy candidate is only a suggestion until a user confirms it; do not let it boost its own ranking.
            if (!"similar".equals(matchType)) {
                specValueSampleMapper.increaseHitCount(sample.getId());
                if (alias != null) {
                    specValueAliasMapper.increaseHitCount(alias.getId());
                }
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
        return align(rawText, categoryId, attributeId, "", standards);
    }

    /**
     * 以类别配置的规格值为标准，对识别文本做对齐：
     * 配置值优先 -> 样本库命中 -> 返回近似候选
     * childName 用于只取该子项自己的样本（被套不会命中床单学到的值）
     */
    public JSONObject align(String rawText, Long categoryId, Long attributeId, String childName, List<String> standards) {
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
        JSONObject sample = match(rawText, categoryId, attributeId, childName);
        if (sample.getBooleanValue("matched")) {
            String canonical = sample.getString("canonicalValue");
            if ("similar".equals(sample.getString("matchType"))) {
                result.put("matched", false);
                result.put("value", rawText.trim());
                result.put("matchType", "similar");
                result.put("confidence", sample.get("confidence"));
                JSONArray candidates = result.getJSONArray("candidates");
                if (StringUtil.isNotEmpty(canonical) && !candidates.contains(canonical)) {
                    candidates.add(0, canonical);
                }
                return result;
            }
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
     * 一致性评估：某属性（含子项）历史上是否长期集中在少数几个值。
     * 通过保存次数加权计算总数、去重值与最高占比。
     */
    public JSONObject evaluate(Long categoryId, Long attributeId, String childName) {
        Long rootCategoryId = materialCategoryService.resolveRootCategoryId(categoryId);
        String scopeChild = cut(childName == null ? "" : childName.trim(), 64);
        JSONObject result = new JSONObject();
        result.put("consistent", false);
        result.put("total", 0);
        result.put("distinct", 0);
        result.put("top1Share", 0D);
        result.put("values", new JSONArray());
        try {
            List<SpecValueSample> samples = specValueSampleMapper.selectByScope(
                    attributeId, rootCategoryId, scopeChild, currentTenantId(), 200);
            int total = 0;
            int distinct = 0;
            int top = 0;
            JSONArray values = new JSONArray();
            for (SpecValueSample sample : samples) {
                if (sample == null || !StringUtil.isNotEmpty(sample.getNormKey())) {
                    continue;
                }
                int saveCount = sample.getSaveCount() == null ? 1 : Math.max(sample.getSaveCount(), 1);
                total += saveCount;
                distinct++;
                if (saveCount > top) {
                    top = saveCount;
                }
                JSONObject item = new JSONObject();
                item.put("value", sample.getCanonicalValue());
                item.put("normKey", sample.getNormKey());
                item.put("sampleId", sample.getId());
                item.put("saveCount", saveCount);
                values.add(item);
            }
            double top1Share = total > 0 ? (double) top / total : 0D;
            for (int i = 0; i < values.size(); i++) {
                JSONObject item = values.getJSONObject(i);
                item.put("share", total > 0 ? (double) item.getIntValue("saveCount") / total : 0D);
            }
            // 样本足够且集中于少数值：单一值型或两值型
            boolean consistent = total >= 5
                    && ((distinct <= 3 && top1Share >= 0.6D) || (distinct <= 2 && top1Share >= 0.5D));
            result.put("consistent", consistent);
            result.put("total", total);
            result.put("distinct", distinct);
            result.put("top1Share", top1Share);
            result.put("values", values);
        } catch (Exception e) {
            logger.warn("规格值一致性评估失败：{}", e.getMessage());
        }
        return result;
    }

    /**
     * 标准值判定：配置值/精确样本/别名/数值结构命中直接判为标准值；
     * 一致属性再做相似度归一，非一致属性不自动替换，避免多样性数据被误归一。
     */
    public JSONObject judge(String rawText, Long categoryId, Long attributeId, String childName,
                            List<String> standards) {
        Long rootCategoryId = materialCategoryService.resolveRootCategoryId(categoryId);
        String scopeChild = cut(childName == null ? "" : childName.trim(), 64);
        JSONObject judgeResult = new JSONObject();
        judgeResult.put("matched", false);
        judgeResult.put("value", StringUtil.isNotEmpty(rawText) ? rawText.trim() : "");
        judgeResult.put("status", "CUSTOM");
        judgeResult.put("matchType", "none");
        judgeResult.put("score", 0D);
        JSONObject evaluation = evaluate(categoryId, attributeId, scopeChild);
        judgeResult.put("consistent", evaluation.getBooleanValue("consistent"));
        judgeResult.put("total", evaluation.getIntValue("total"));
        judgeResult.put("distinct", evaluation.getIntValue("distinct"));
        judgeResult.put("top1Share", evaluation.getDoubleValue("top1Share"));
        if (!StringUtil.isNotEmpty(rawText)) {
            return judgeResult;
        }
        String raw = rawText.trim();
        String normKey = SpecValueNormalizer.normKey(raw);
        if (normKey.isEmpty()) {
            return judgeResult;
        }
        // 1. 类别配置的标准值
        String configured = SpecValueNormalizer.alignToStandard(raw, standards);
        if (configured != null) {
            judgeResult.put("matched", true);
            judgeResult.put("value", configured);
            judgeResult.put("status", "STANDARD");
            judgeResult.put("matchType", "config");
            judgeResult.put("score", 1D);
            return judgeResult;
        }
        Long tenantId = currentTenantId();
        try {
            // 2. 已学样本精确命中
            SpecValueSample sample = specValueSampleMapper.selectByNormKey(
                    normKey, attributeId, rootCategoryId, scopeChild, tenantId);
            String matchType = "exact";
            if (sample == null) {
                SpecValueAlias alias = specValueAliasMapper.selectByNormKey(
                        normKey, attributeId, rootCategoryId, scopeChild, tenantId);
                if (alias != null) {
                    sample = specValueSampleMapper.selectByPrimaryKey(alias.getSampleId());
                    matchType = "alias";
                }
            }
            // 3. 数值+单位结构命中
            if (sample == null) {
                String numericKey = SpecValueNormalizer.numericKey(raw);
                if (StringUtil.isNotEmpty(numericKey)) {
                    sample = specValueSampleMapper.selectByNumericKey(
                            numericKey, attributeId, rootCategoryId, scopeChild, tenantId);
                    if (sample != null && !SpecValueNormalizer.numericKeyCompatible(raw, sample.getCanonicalValue())) {
                        sample = null;
                    }
                    matchType = "numeric";
                }
            }
            if (sample != null) {
                judgeResult.put("matched", true);
                judgeResult.put("value", sample.getCanonicalValue());
                judgeResult.put("status", "STANDARD");
                judgeResult.put("matchType", matchType);
                judgeResult.put("score", 1D);
                judgeResult.put("sampleId", sample.getId());
                return judgeResult;
            }
            // 4. 一致性属性：相似度归一（阈值随值长度收紧，且要求与第二名拉开差距）
            if (!evaluation.getBooleanValue("consistent") || !isTextValue(normKey)) {
                judgeResult.put("status", evaluation.getIntValue("total") < 5 ? "INSUFFICIENT" : "DIVERSE");
                return judgeResult;
            }
            List<SpecValueSample> samples = specValueSampleMapper.selectByScope(
                    attributeId, rootCategoryId, scopeChild, tenantId, 200);
            SpecValueSample best = null;
            SpecValueSample second = null;
            double bestScore = 0D;
            double secondScore = 0D;
            for (SpecValueSample item : samples) {
                String key = item.getNormKey();
                if (key == null || !isTextValue(key)) {
                    continue;
                }
                int max = Math.max(key.length(), normKey.length());
                int min = Math.min(key.length(), normKey.length());
                if (max > min * 2) {
                    continue;
                }
                double score = 1.0D - (double) distance(normKey, key) / max;
                if (score > bestScore) {
                    secondScore = bestScore;
                    second = best;
                    bestScore = score;
                    best = item;
                } else if (score > secondScore) {
                    secondScore = score;
                    second = item;
                }
            }
            double threshold = autoThreshold(normKey);
            if (best == null || bestScore < threshold) {
                return judgeResult;
            }
            // Keep the OCR value in the editable field. Even a strong fuzzy match is a suggestion,
            // and becomes a learned mapping only after the user confirms/corrects it and saves.
            judgeResult.put("value", raw);
            judgeResult.put("score", bestScore);
            judgeResult.put("sampleId", best.getId());
            judgeResult.put("suggestedValue", best.getCanonicalValue());
            judgeResult.put("status", "SUGGEST");
            judgeResult.put("matchType", "similar");
            JSONArray candidates = new JSONArray();
            candidates.add(best.getCanonicalValue());
            if (bestScore - secondScore >= 0.05D) {
                judgeResult.put("candidates", candidates);
            } else {
                if (second != null && secondScore >= threshold) {
                    if (!candidates.contains(second.getCanonicalValue())) {
                        candidates.add(second.getCanonicalValue());
                    }
                }
                judgeResult.put("candidates", candidates);
            }
        } catch (Exception e) {
            logger.warn("规格值判定失败：{}", e.getMessage());
        }
        return judgeResult;
    }

    /** OCR 误差容忍度：值越短越严格 */
    private static double autoThreshold(String normKey) {
        int length = normKey == null ? 0 : normKey.length();
        if (length <= 4) {
            return 0.95D;
        }
        if (length <= 8) {
            return 0.90D;
        }
        return 0.85D;
    }

    /**
     * 批量学习：商品保存时把「识别原文 -> 最终采纳值」逐项沉淀
     * 每项独立事务：单条失败不影响其他项，并返回失败明细供前端提示
     */
    public JSONObject learnBatch(JSONArray items, Long categoryId, Long materialId, Long userId) {
        JSONObject result = new JSONObject();
        JSONArray failed = new JSONArray();
        int count = 0;
        result.put("count", 0);
        result.put("failed", failed);
        if (items == null || items.isEmpty()) {
            return result;
        }
        // 基础属性统一按一级类目统计
        final Long rootCategoryId = materialCategoryService.resolveRootCategoryId(categoryId);
        final TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null) {
                continue;
            }
            // 带 autoValue/finalValue 的是基础属性：先记录事件，占比达标才升级为标准值
            // 其余为规格值：沿用原有即时学习口径
            final boolean gated = item.containsKey("finalValue") || item.containsKey("autoValue");
            final String rawText = item.getString("rawText");
            final String correctedValue = gated ? item.getString("finalValue") : item.getString("correctedValue");
            if (!StringUtil.isNotEmpty(rawText) || !StringUtil.isNotEmpty(correctedValue)) {
                continue;
            }
            if (!gated && SpecValueNormalizer.normKey(rawText).equals(SpecValueNormalizer.normKey(correctedValue))) {
                // 规格值归一化后一致，无需沉淀别名
                continue;
            }
            final Long attributeId = item.getLong("attributeId");
            final String childName = item.getString("childName");
            final String autoValue = item.getString("autoValue");
            final boolean modified = item.getBooleanValue("modified");
            try {
                Boolean promoted = template.execute(new TransactionCallback<Boolean>() {
                    @Override
                    public Boolean doInTransaction(org.springframework.transaction.TransactionStatus status) {
                        if (gated) {
                            return recordBaseCorrection(rawText, correctedValue, autoValue, modified,
                                    rootCategoryId, attributeId, childName, materialId, userId);
                        }
                        learn(rawText, correctedValue, categoryId, attributeId, childName, materialId, userId);
                        return Boolean.TRUE;
                    }
                });
                if (Boolean.TRUE.equals(promoted)) {
                    count++;
                }
            } catch (Exception e) {
                logger.warn("批量学习规格值失败：{}", e.getMessage());
                JSONObject fail = new JSONObject();
                fail.put("rawText", rawText);
                fail.put("correctedValue", correctedValue);
                fail.put("message", rootMessage(e));
                failed.add(fail);
            }
        }
        result.put("count", count);
        return result;
    }

    /**
     * 记录一次基础属性学习事件，并判断是否达到升级门槛
     * 1. 确认（未修改）与纠错（手动修改）都计入各自最终值
     * 2. 同一识别原文下某最终值出现 >= 3 次且占比 >= 60%，才写为标准值
     * 3. 达标时修正别名指向，并停用原文精确命中的旧错误样本
     *
     * @return 本次是否产生了标准值升级
     */
    private boolean recordBaseCorrection(String rawText, String finalValue, String autoValue, boolean modified,
                                         Long categoryId, Long attributeId, String childName,
                                         Long materialId, Long userId) {
        String raw = cut(rawText.trim(), 500);
        String finalText = cut(finalValue.trim(), 500);
        String rawKey = cut(SpecValueNormalizer.normKey(raw), 255);
        String finalKey = cut(SpecValueNormalizer.normKey(finalText), 255);
        if (rawKey.isEmpty() || finalKey.isEmpty()) {
            return false;
        }
        Long attrId = attributeId == null ? 0L : attributeId;
        Long catId = categoryId == null ? 0L : categoryId;
        String scopeChild = cut(childName == null ? "" : childName.trim(), 64);
        Long tenantId = currentTenantId();

        SpecValueCorrectionEvent event = new SpecValueCorrectionEvent();
        event.setTenantId(tenantId == null ? 0L : tenantId);
        event.setCategoryId(catId);
        event.setAttributeId(attrId);
        event.setChildName(scopeChild);
        event.setRawText(raw);
        event.setRawNormKey(rawKey);
        event.setAutoValue(cut(autoValue == null ? "" : autoValue.trim(), 500));
        event.setFinalValue(finalText);
        event.setFinalNormKey(finalKey);
        event.setModified(modified ? "1" : "0");
        event.setMaterialId(materialId);
        event.setUserId(resolveUserId(userId));
        event.setDeleteFlag("0");
        specValueCorrectionEventMapper.insertSelective(event);

        List<SpecValueCorrectionStat> stats = specValueCorrectionEventMapper
                .statByRaw(tenantId, catId, attrId, scopeChild, rawKey, CORRECTION_WINDOW_DAYS);
        long total = 0L;
        SpecValueCorrectionStat top = null;
        for (SpecValueCorrectionStat stat : stats) {
            long cnt = stat.getTotal() == null ? 0L : stat.getTotal();
            total += cnt;
            if (top == null || cnt > (top.getTotal() == null ? 0L : top.getTotal())) {
                top = stat;
            }
        }
        if (top == null || total <= 0L || top.getTotal() < CORRECTION_MIN_COUNT) {
            return false;
        }
        if ((double) top.getTotal() / total < CORRECTION_MIN_RATIO) {
            return false;
        }

        String winner = top.getFinalValue();
        String winnerKey = top.getFinalNormKey();
        if (!StringUtil.isNotEmpty(winner) || !StringUtil.isNotEmpty(winnerKey)) {
            return false;
        }

        // 已经绑定到该标准值则无需重复学习
        SpecValueSample winnerSample = specValueSampleMapper.selectByNormKey(winnerKey, attrId, catId, scopeChild, tenantId);
        SpecValueAlias alias = specValueAliasMapper.selectByNormKey(rawKey, attrId, catId, scopeChild, tenantId);
        boolean bound = winnerSample != null && (alias != null
                ? winnerSample.getId().equals(alias.getSampleId())
                : rawKey.equals(winnerKey));
        if (bound) {
            return false;
        }

        learn(raw, winner, catId, attrId, scopeChild, materialId, userId);
        winnerSample = specValueSampleMapper.selectByNormKey(winnerKey, attrId, catId, scopeChild, tenantId);
        // 历史别名可能指向旧的错误样本，达标后改指向新的标准值
        if (winnerSample != null && alias != null && !winnerSample.getId().equals(alias.getSampleId())) {
            specValueAliasMapper.updateSampleId(alias.getId(), winnerSample.getId());
        }
        // 原文本身能精确命中另一条样本时停用，避免错误值继续直接回填
        if (!rawKey.equals(winnerKey)) {
            SpecValueSample exact = specValueSampleMapper.selectByNormKey(rawKey, attrId, catId, scopeChild, tenantId);
            if (exact != null && !"1".equals(exact.getStatus())) {
                SpecValueSample disabled = new SpecValueSample();
                disabled.setId(exact.getId());
                disabled.setStatus("1");
                specValueSampleMapper.updateByPrimaryKeySelective(disabled);
            }
        }
        return true;
    }

    /**
     * 学习一个规格值：rawText 为用户看到的原始写法，correctedValue 为纠正后的标准写法
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public JSONObject learn(String rawText, String correctedValue, Long categoryId, Long attributeId,
                            Long materialId, Long userId) {
        return learn(rawText, correctedValue, categoryId, attributeId, "", materialId, userId);
    }

    /**
     * 学习一个规格值：rawText 为用户看到的原始写法，correctedValue 为纠正后的标准写法
     * childName 把样本限制在同一规格名下的具体子项（被套/床单/枕套、颜色），避免子项之间互相串值
     */
    public JSONObject learn(String rawText, String correctedValue, Long categoryId, Long attributeId,
                            String childName, Long materialId, Long userId) {
        // 学习样本按一级类目沉淀
        categoryId = materialCategoryService.resolveRootCategoryId(categoryId);
        String scopeChild = cut(childName == null ? "" : childName.trim(), 64);
        JSONObject result = new JSONObject();
        if (!StringUtil.isNotEmpty(rawText) && !StringUtil.isNotEmpty(correctedValue)) {
            result.put("success", false);
            result.put("message", "原始值和标准值不能同时为空");
            return result;
        }
        String raw = cut(StringUtil.isNotEmpty(rawText) ? rawText.trim() : correctedValue.trim(), 255);
        String canonical = cut(StringUtil.isNotEmpty(correctedValue) ? correctedValue.trim() : raw, 255);
        String normKey = SpecValueNormalizer.normKey(canonical);
        if (normKey.isEmpty()) {
            normKey = SpecValueNormalizer.normKey(raw);
        }
        if (normKey.isEmpty()) {
            result.put("success", false);
            result.put("message", "规格值不能为空");
            return result;
        }
        normKey = cut(normKey, 255);
        Long attrId = attributeId == null ? 0L : attributeId;
        Long catId = categoryId == null ? 0L : categoryId;
        Long tenantId = currentTenantId();

        SpecValueSample sample = specValueSampleMapper.selectByNormKey(normKey, attrId, catId, scopeChild, tenantId);
        if (sample == null) {
            sample = new SpecValueSample();
            sample.setTenantId(tenantId == null ? 0L : tenantId);
            sample.setCategoryId(catId);
            sample.setAttributeId(attrId);
            sample.setChildName(scopeChild);
            sample.setCanonicalValue(canonical);
            sample.setNormKey(normKey);
            sample.setSource("manual");
            sample.setConfidence(BigDecimal.ONE);
            sample.setHitCount(1);
            sample.setSaveCount(1);
            sample.setStatus("0");
            sample.setDeleteFlag("0");
            try {
                specValueSampleMapper.insertSelective(sample);
            } catch (DuplicateKeyException e) {
                sample = specValueSampleMapper.selectByNormKey(normKey, attrId, catId, scopeChild, tenantId);
            }
        } else {
            SpecValueSample update = new SpecValueSample();
            update.setId(sample.getId());
            update.setCanonicalValue(canonical);
            update.setSource("manual");
            specValueSampleMapper.updateByPrimaryKeySelective(update);
            specValueSampleMapper.increaseSaveCount(sample.getId());
        }

        // 原始写法与标准写法归一化后不同，才记别名
        String rawNormKey = cut(SpecValueNormalizer.normKey(raw), 255);
        if (sample != null && StringUtil.isNotEmpty(rawNormKey) && !rawNormKey.equals(sample.getNormKey())) {
            SpecValueAlias alias = specValueAliasMapper.selectByNormKey(rawNormKey, attrId, catId, scopeChild, tenantId);
            if (alias == null) {
                alias = new SpecValueAlias();
                alias.setTenantId(tenantId == null ? 0L : tenantId);
                alias.setSampleId(sample.getId());
                alias.setCategoryId(catId);
                alias.setAttributeId(attrId);
                alias.setChildName(scopeChild);
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

    /**
     * 非数值文本的相似度兜底：同品类、同规格名（含子项）下取最相似的已学标准值
     */
    private SpecValueSample fuzzyMatch(String normKey, Long attributeId, Long categoryId, String childName, Long tenantId) {
        if (!isTextValue(normKey) || normKey.length() < 5) {
            return null;
        }
        try {
            List<SpecValueSample> samples = specValueSampleMapper.selectByScope(attributeId, categoryId, childName, tenantId, 200);
            SpecValueSample best = null;
            double bestScore = 0;
            for (SpecValueSample sample : samples) {
                String key = sample.getNormKey();
                if (key == null || !isTextValue(key)) {
                    continue;
                }
                int max = Math.max(key.length(), normKey.length());
                int min = Math.min(key.length(), normKey.length());
                if (max > min * 2) {
                    continue;
                }
                double score = 1.0 - (double) distance(normKey, key) / max;
                if (score > bestScore) {
                    bestScore = score;
                    best = sample;
                }
            }
            return bestScore >= 0.65 ? best : null;
        } catch (Exception e) {
            logger.warn("规格值相似度比对失败：{}", e.getMessage());
            return null;
        }
    }

    /** 文本类值：至少 2 个汉字、且数字占比不超过一半（排除尺寸/纯数字） */
    private static boolean isTextValue(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        int cjk = 0;
        int digits = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                cjk++;
            } else if (Character.isDigit(c)) {
                digits++;
            }
        }
        return cjk >= 2 && digits <= value.length() / 2;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        if (msg == null) {
            msg = t.getClass().getSimpleName();
        }
        return cut(msg, 200);
    }

    private Long currentTenantId() {
        try {
            return userService.getCurrentUser().getTenantId();
        } catch (Exception e) {
            return 0L;
        }
    }
}
