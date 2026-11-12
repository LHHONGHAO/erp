package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.utils.OcrUtil;
import com.jsh.erp.utils.SpecValueNormalizer;
import com.jsh.erp.utils.StringUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多图商品识别聚合：按「关键词优先 + 一致性判定」把多张图片的文字归纳为一个商品（SPU）及其 SKU。
 * 纯计算，不落库。
 */
@Service
public class SpecAggregateService {

    /** 参与 SPU 一致性判定的公共属性（条码/价格属于 SKU，不参与） */
    private static final String[] SPU_FIELDS = {
            "name", "brand", "unit", "mfrs", "fabric", "safetyCategory",
            "executiveStandard", "grade", "inspection", "filler", "weight"
    };

    private static final Map<String, String> FIELD_LABELS = new LinkedHashMap<>();

    static {
        FIELD_LABELS.put("name", "名称");
        FIELD_LABELS.put("brand", "品牌");
        FIELD_LABELS.put("unit", "单位");
        FIELD_LABELS.put("mfrs", "制造商");
        FIELD_LABELS.put("fabric", "面料");
        FIELD_LABELS.put("safetyCategory", "安全类别");
        FIELD_LABELS.put("executiveStandard", "执行标准");
        FIELD_LABELS.put("grade", "等级");
        FIELD_LABELS.put("inspection", "检验");
        FIELD_LABELS.put("filler", "填充物");
        FIELD_LABELS.put("weight", "填充物重量");
    }

    /** 尺寸类写法：200x230、200*230cm、1.5×2m */
    private static final Pattern SIZE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?\\s*[xX×*]\\s*\\d+(?:\\.\\d+)?(?:\\s*[cC][mM]|\\s*[mM])?)");

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    @Resource
    private SpecValueSampleService specValueSampleService;

    /**
     * 聚合多图识别结果
     *
     * @param images     每张图的 {rawText, imagePath}
     * @param categoryId 类别
     * @param dimensions 类别已配置的规格维度 [{name, attributeId, childNames[], standards[]}]
     */
    public JSONObject aggregate(JSONArray images, Long categoryId, JSONArray dimensions) {
        return aggregate(images, categoryId, dimensions, null);
    }

    /**
     * 聚合多图识别结果
     *
     * @param images       每张图的 {rawText, imagePath}
     * @param categoryId   类别
     * @param dimensions   类别已配置的规格维度 [{name, attributeId, childNames[], standards[]}]
     * @param labelAliases 前端维护的属性名别名 {识别到的属性名: 基础属性字段}
     */
    public JSONObject aggregate(JSONArray images, Long categoryId, JSONArray dimensions, JSONObject labelAliases) {
        JSONObject result = new JSONObject();
        List<JSONObject> dims = normalizeDimensions(dimensions);
        Map<String, String> aliasMap = new LinkedHashMap<>();
        if (labelAliases != null) {
            for (String key : labelAliases.keySet()) {
                String field = labelAliases.getString(key);
                if (StringUtil.isNotEmpty(key) && StringUtil.isNotEmpty(field) && isPropertyField(field)) {
                    aliasMap.put(normLabel(key), field);
                }
            }
        }
        List<ImageData> dataList = new ArrayList<>();
        // 规格维度名及其子项（如 规格/被套/床单/枕套）属于 SKU 基础属性，不进入「其他属性」
        Set<String> specLabels = new LinkedHashSet<>();
        for (JSONObject dim : dims) {
            specLabels.add(normLabel(dim.getString("name")));
            for (String child : toStringList(dim.getJSONArray("childNames"))) {
                specLabels.add(normLabel(child));
            }
        }
        if (images != null) {
            for (int i = 0; i < images.size(); i++) {
                JSONObject img = images.getJSONObject(i);
                if (img == null) {
                    continue;
                }
                String rawText = img.getString("rawText");
                if (!StringUtil.isNotEmpty(rawText)) {
                    continue;
                }
                ImageData data = new ImageData();
                data.rawText = rawText;
                data.imagePath = img.getString("imagePath");
                data.props = OcrUtil.parseProductText(rawText);
                data.values = new LinkedHashMap<>();
                data.fieldValues = new LinkedHashMap<>();
                data.unmatched = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : data.props.entrySet()) {
                    String label = entry.getKey();
                    String value = trim(entry.getValue());
                    if (value.isEmpty() || "rawText".equals(label)) {
                        continue;
                    }
                    String field = resolvePropField(label, aliasMap, categoryId);
                    if (field != null) {
                        data.fieldValues.put(field, value);
                    } else if (!"standard".equals(label) && !"model".equals(label) && !"color".equals(label)
                            && !"barCode".equals(label) && !"mnemonic".equals(label)
                            && !specLabels.contains(normLabel(label)) && !isSkuPriceLabel(label)) {
                        // 识别到但未对应到现有基础属性的标签，交给前端确认后再确定
                        data.unmatched.put(label, value);
                    }
                }
                for (JSONObject dim : dims) {
                    extractDimensionValue(data, dim, categoryId);
                }
                dataList.add(data);
            }
        }

        // 1. SPU 公共属性：取多数值，冲突项单独提示；值命中样本库时归一化为标准写法
        JSONObject commonProps = new JSONObject();
        JSONObject commonPropsRaw = new JSONObject();
        JSONArray conflicts = new JSONArray();
        boolean spuConsistent = true;
        Set<String> propFields = new LinkedHashSet<>(Arrays.asList(SPU_FIELDS));
        for (ImageData data : dataList) {
            propFields.addAll(data.fieldValues.keySet());
        }
        for (String field : propFields) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (ImageData data : dataList) {
                String value = trim(data.fieldValues.get(field));
                if (!value.isEmpty()) {
                    Integer count = counts.get(value);
                    counts.put(value, count == null ? 1 : count + 1);
                }
            }
            if (counts.isEmpty()) {
                continue;
            }
            String chosen = null;
            int max = 0;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > max) {
                    max = entry.getValue();
                    chosen = entry.getKey();
                }
            }
            commonPropsRaw.put(field, chosen);
            commonProps.put(field, normalizeBaseValue(field, chosen, categoryId));
            if (counts.size() > 1) {
                spuConsistent = false;
                JSONObject conflict = new JSONObject();
                conflict.put("field", field);
                conflict.put("label", FIELD_LABELS.containsKey(field) ? FIELD_LABELS.get(field) : field);
                conflict.put("values", new ArrayList<>(counts.keySet()));
                conflict.put("chosen", chosen);
                conflicts.add(conflict);
            }
        }

        // 1.1 识别到但未对应基础属性的标签：前端确认后学习别名
        JSONArray unmatchedProps = new JSONArray();
        Map<String, JSONObject> unmatchedByLabel = new LinkedHashMap<>();
        for (ImageData data : dataList) {
            for (Map.Entry<String, String> entry : data.unmatched.entrySet()) {
                if (unmatchedByLabel.containsKey(entry.getKey())) {
                    continue;
                }
                JSONObject item = new JSONObject(true);
                item.put("label", entry.getKey());
                item.put("value", entry.getValue());
                item.put("rawValue", entry.getValue());
                item.put("rawText", data.rawText);
                item.put("imagePath", data.imagePath == null ? "" : data.imagePath);
                unmatchedByLabel.put(entry.getKey(), item);
                unmatchedProps.add(item);
            }
        }


        // 2. 维度：只保留识别出的（即 SKU 维度）参与 SKU 生成
        JSONArray dimensionList = new JSONArray();
        List<JSONObject> skuDims = new ArrayList<>();
        for (JSONObject dim : dims) {
            JSONObject out = new JSONObject();
            out.put("name", dim.getString("name"));
            out.put("attributeId", dim.get("attributeId"));
            JSONArray values = new JSONArray();
            Set<String> seen = new LinkedHashSet<>();
            boolean needConfirm = false;
            for (ImageData data : dataList) {
                JSONObject hit = data.values.get(dim.getString("name"));
                if (hit == null) {
                    continue;
                }
                String text = hit.getString("value");
                if (!StringUtil.isNotEmpty(text)) {
                    continue;
                }
                if (!seen.add(SpecValueNormalizer.normKey(text))) {
                    continue;
                }
                hit.put("imagePath", data.imagePath == null ? "" : data.imagePath);
                hit.put("rawText", data.rawText);
                values.add(hit);
                if (hit.getBooleanValue("needConfirm")) {
                    needConfirm = true;
                }
            }
            out.put("values", values);
            out.put("detected", values.size() > 0);
            out.put("needConfirm", needConfirm);
            dimensionList.add(out);
            if (values.size() > 0) {
                skuDims.add(dim);
            }
        }

        // 2.1 按图片分组：每张图各自识别出的规格值（前端按「规格值1/规格值2」展示）
        JSONArray imageGroups = new JSONArray();
        int groupIndex = 0;
        for (ImageData data : dataList) {
            JSONArray groupDims = new JSONArray();
            for (JSONObject dim : dims) {
                JSONObject hit = data.values.get(dim.getString("name"));
                if (hit == null || !StringUtil.isNotEmpty(hit.getString("value"))) {
                    continue;
                }
                JSONObject item = new JSONObject(true);
                item.put("name", dim.getString("name"));
                item.put("value", hit.getString("value"));
                item.put("rawValue", hit.getString("rawValue"));
                item.put("subValues", hit.get("subValues") == null ? new JSONObject(true) : hit.get("subValues"));
                item.put("needConfirm", hit.getBooleanValue("needConfirm"));
                item.put("childNames", dim.getJSONArray("childNames") == null ? new JSONArray() : dim.getJSONArray("childNames"));
                item.put("rawText", data.rawText);
                groupDims.add(item);
            }
            if (groupDims.isEmpty()) {
                continue;
            }
            JSONObject group = new JSONObject(true);
            group.put("index", ++groupIndex);
            group.put("imagePath", data.imagePath == null ? "" : data.imagePath);
            group.put("dimensions", groupDims);
            imageGroups.add(group);
        }

        // 3. SKU 行：按维度值组合去重，完全重复的图片只保留一行
        JSONArray skuRows = new JSONArray();
        boolean duplicated = false;
        int duplicatedCount = 0;
        Map<String, JSONObject> rowsByKey = new LinkedHashMap<>();
        if (skuDims.isEmpty()) {
            // 未识别到任何 SKU 维度：多图视为同一商品，只保留一行
            if (!dataList.isEmpty()) {
                ImageData first = dataList.get(0);
                JSONObject row = new JSONObject();
                row.put("sku", "");
                row.put("specValues", new JSONArray());
                row.put("barCode", trim(first.props.get("barCode")));
                row.put("imagePath", first.imagePath == null ? "" : first.imagePath);
                row.put("commodityDecimal", findPrice(first.rawText, "零售价", "售价", "吊牌价"));
                row.put("marketPrice", findPrice(first.rawText, "划线价", "原价", "市场价"));
                row.put("purchaseDecimal", findPrice(first.rawText, "进货价", "采购价"));
                row.put("wholesaleDecimal", findPrice(first.rawText, "批发价"));
                skuRows.add(row);
                for (int i = 1; i < dataList.size(); i++) {
                    mergeMissingSkuFields(row, dataList.get(i));
                }
                duplicated = dataList.size() > 1;
                duplicatedCount = dataList.size() - 1;
            }
        }
        for (ImageData data : dataList) {
            if (skuDims.isEmpty()) {
                break;
            }
            JSONArray specValues = new JSONArray();
            List<String> texts = new ArrayList<>();
            boolean hasSkuValue = false;
            for (JSONObject dim : skuDims) {
                JSONObject hit = data.values.get(dim.getString("name"));
                String text = hit == null ? "" : hit.getString("value");
                if (text == null) {
                    text = "";
                }
                if (!text.isEmpty()) {
                    hasSkuValue = true;
                }
                texts.add(text);
                JSONObject specValue = new JSONObject();
                specValue.put("attributeName", dim.getString("name"));
                specValue.put("valueText", text);
                specValue.put("subValues", hit == null ? new JSONObject() : hit.get("subValues"));
                specValues.add(specValue);
            }
            // 该图只有商品基础信息、没有任何 SKU 值时，不作为 SKU 行
            if (!hasSkuValue) {
                continue;
            }
            String sku = joinSlash(texts);
            String key = SpecValueNormalizer.normKey(sku);
            if (key.isEmpty()) {
                key = "image_" + skuRows.size();
            }
            if (rowsByKey.containsKey(key)) {
                duplicated = true;
                duplicatedCount++;
                mergeMissingSkuFields(rowsByKey.get(key), data);
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("sku", sku);
            row.put("specValues", specValues);
            row.put("barCode", trim(data.props.get("barCode")));
            row.put("imagePath", data.imagePath == null ? "" : data.imagePath);
            row.put("commodityDecimal", findPrice(data.rawText, "零售价", "售价", "吊牌价"));
            row.put("marketPrice", findPrice(data.rawText, "划线价", "原价", "市场价"));
            row.put("purchaseDecimal", findPrice(data.rawText, "进货价", "采购价"));
            row.put("wholesaleDecimal", findPrice(data.rawText, "批发价"));
            skuRows.add(row);
            rowsByKey.put(key, row);
        }

        result.put("spuConsistent", spuConsistent);
        result.put("commonProps", commonProps);
        result.put("commonPropsRaw", commonPropsRaw);
        result.put("unmatchedProps", unmatchedProps);
        result.put("conflicts", conflicts);
        result.put("dimensions", dimensionList);
        result.put("imageGroups", imageGroups);
        result.put("skuRows", skuRows);
        result.put("duplicated", duplicated);
        result.put("duplicatedCount", duplicatedCount);
        result.put("imageCount", dataList.size());
        return result;
    }

    /** 同一规格组合的重复标签只保留一行，但把首张标签缺失的信息从后续标签补齐。 */
    private void mergeMissingSkuFields(JSONObject row, ImageData data) {
        if (row == null || data == null) {
            return;
        }
        mergeMissing(row, "barCode", data.props.get("barCode"));
        mergeMissing(row, "imagePath", data.imagePath);
        mergeMissing(row, "commodityDecimal", findPrice(data.rawText, "零售价", "售价", "吊牌价"));
        mergeMissing(row, "marketPrice", findPrice(data.rawText, "划线价", "原价", "市场价"));
        mergeMissing(row, "purchaseDecimal", findPrice(data.rawText, "进货价", "采购价"));
        mergeMissing(row, "wholesaleDecimal", findPrice(data.rawText, "批发价"));
    }

    private void mergeMissing(JSONObject target, String field, String candidate) {
        if (target == null || !StringUtil.isNotEmpty(candidate)) {
            return;
        }
        if (!StringUtil.isNotEmpty(target.getString(field))) {
            target.put(field, candidate.trim());
        }
    }

    private List<JSONObject> normalizeDimensions(JSONArray dimensions) {
        List<JSONObject> list = new ArrayList<>();
        if (dimensions == null) {
            return list;
        }
        for (int i = 0; i < dimensions.size(); i++) {
            JSONObject dim = dimensions.getJSONObject(i);
            if (dim == null || !StringUtil.isNotEmpty(dim.getString("name"))) {
                continue;
            }
            list.add(dim);
        }
        return list;
    }

    /**
     * 单张图片的单个维度取值：关键词（类别配置值/子规格名）优先，再做行内「维度名：值」匹配
     */
    private void extractDimensionValue(ImageData data, JSONObject dim, Long categoryId) {
        String name = dim.getString("name");
        List<String> childNames = toStringList(dim.getJSONArray("childNames"));
        List<StandardValue> standards = toStandardValues(dim.getJSONArray("standards"), childNames);
        List<String> standardValues = toStringValues(standards);
        Long attributeId = dim.getLong("attributeId");
        String raw = data.rawText;
        String normRaw = SpecValueNormalizer.normKey(raw);

        // 0. 子规格标签：本张标签上直接标注了归属，如「被套：200*230」
        Map<String, String> labelSubValues = findChildLabelValues(raw, childNames);
        if (!labelSubValues.isEmpty()) {
            data.values.put(name, buildHitFromChildLabels(labelSubValues, standards, childNames, categoryId, attributeId));
            return;
        }

        // 1. 类别配置的规格值直接命中（长值优先，避免短值抢先）
        List<StandardValue> sorted = new ArrayList<>(standards);
        Collections.sort(sorted, new Comparator<StandardValue>() {
            @Override
            public int compare(StandardValue a, StandardValue b) {
                return (b.value == null ? 0 : b.value.length()) - (a.value == null ? 0 : a.value.length());
            }
        });
        for (StandardValue standard : sorted) {
            String normStandard = SpecValueNormalizer.normKey(standard.value);
            if (!normStandard.isEmpty() && normRaw.contains(normStandard)) {
                data.values.put(name, buildHit(standard.value, standard.value, false, "keyword", childNames, standard.subValues));
                return;
            }
        }

        // 2. 规格以组判定：一张图 = 一个 SKU，按品类约定的子规格组（被套/床单/枕套）依次归位
        // 尺寸只属于尺寸类规格；颜色等普通 SKU 维度不能因文本中出现床品尺寸而误收尺寸值。
        List<String> sizes = (childNames != null && !childNames.isEmpty()) || isSizeDimension(name)
                ? extractSizes(raw) : Collections.<String>emptyList();
        if (!sizes.isEmpty()) {
            JSONObject sizeHit = buildHitBySizes(sizes, standards, childNames);
            if (sizeHit != null) {
                data.values.put(name, sizeHit);
                return;
            }
        }

        // 3. 子规格名命中（如 被套/床单/枕套）：用同图尺寸补充该子规格的值
        String childHit = null;
        for (String childName : childNames) {
            String normChild = SpecValueNormalizer.normKey(childName);
            if (!normChild.isEmpty() && normRaw.contains(normChild)) {
                childHit = childName;
                break;
            }
        }
        if (childHit != null) {
            data.values.put(name, buildHitByChildName(childHit, sizes.isEmpty() ? "" : sizes.get(0), standards, categoryId, attributeId));
            return;
        }

        // 4. 组合值按子项模糊命中（如文中只出现其中一项），无法确定归属时仍需人工确认
        for (StandardValue standard : sorted) {
            if (!standard.value.contains("/")) {
                continue;
            }
            boolean tokenHit = false;
            for (String part : standard.value.split("/")) {
                String normPart = SpecValueNormalizer.normKey(part);
                if (normPart.length() >= 2 && normRaw.contains(normPart)) {
                    tokenHit = true;
                    break;
                }
            }
            if (tokenHit) {
                data.values.put(name, buildHit(standard.value, standard.value, true, "keyword", childNames, standard.subValues));
                return;
            }
        }

        // 5. 行内「维度名：值」写法（花色：灰色 / 规格：200x230）
        String labelValue = findLabelValue(raw, name);
        if (labelValue.isEmpty()) {
            labelValue = findLabelValue(raw, dimensionAlias(name));
        }
        if (!labelValue.isEmpty()) {
            applyAligned(data, name, labelValue, categoryId, dim, standardValues, childNames, "line", raw);
            return;
        }

        // 6. 解析字段兜底（颜色 -> color，规格/尺寸 -> standard）
        String field = dimensionField(name);
        if (field != null && StringUtil.isNotEmpty(data.props.get(field))) {
            applyAligned(data, name, data.props.get(field), categoryId, dim, standardValues, childNames, "parse", raw);
        }
    }

    private boolean isSizeDimension(String name) {
        if (name == null) return false;
        String value = name.trim();
        return value.contains("尺寸") || value.contains("规格") || value.contains("大小") || value.contains("尺码");
    }

    /** 子规格标签命中：把每张标签上的「子规格：值」归一到类别标准写法 */
    private JSONObject buildHitFromChildLabels(Map<String, String> labelSubValues, List<StandardValue> standards,
                                               List<String> childNames, Long categoryId, Long attributeId) {
        Map<String, String> subValues = new LinkedHashMap<>();
        List<String> values = new ArrayList<>();
        boolean needConfirm = false;
        List<String> orderedNames = childNames.isEmpty() ? new ArrayList<>(labelSubValues.keySet()) : childNames;
        for (String childName : orderedNames) {
            String text = trim(labelSubValues.get(childName));
            if (text.isEmpty()) {
                continue;
            }
            List<String> childStandards = childStandardValues(childName, standards);
            String aligned = alignValue(text, categoryId, attributeId, childStandards);
            subValues.put(childName, aligned);
            values.add(aligned);
            if (!childStandards.isEmpty() && !containsValue(childStandards, aligned)) {
                needConfirm = true;
            }
        }
        return buildHit(joinSlash(values), joinSlash(values), needConfirm, "label", childNames, subValues);
    }

    /** 子规格名命中：尺寸优先取该子规格已配置的写法，否则原样保留尺寸 */
    private JSONObject buildHitByChildName(String childName, String size, List<StandardValue> standards,
                                           Long categoryId, Long attributeId) {
        Map<String, String> subValues = new LinkedHashMap<>();
        if (size.isEmpty()) {
            return buildHit(childName, childName, true, "keyword", Collections.<String>emptyList(), subValues);
        }
        List<String> childStandards = childStandardValues(childName, standards);
        String aligned = alignValue(size, categoryId, attributeId, childStandards);
        boolean needConfirm = !childStandards.isEmpty() && !containsValue(childStandards, aligned);
        subValues.put(childName, aligned);
        return buildHit(aligned, size, needConfirm, "keyword", Collections.singletonList(childName), subValues);
    }

    /**
     * 规格按组判定：一张标签上的多个尺寸依次归入品类约定的子规格（被套/床单/枕套）。
     * 已标定的尺寸采用标定归属，未标定的按约定顺序补位并标记需确认。
     */
    private JSONObject buildHitBySizes(List<String> sizes, List<StandardValue> standards, List<String> childNames) {
        Map<String, String> subValues = new LinkedHashMap<>();
        boolean needConfirm = false;
        for (String size : sizes) {
            Set<String> matchedChildren = matchChildrenBySize(size, standards);
            String child;
            if (matchedChildren.size() == 1) {
                child = matchedChildren.iterator().next();
            } else if (matchedChildren.size() > 1) {
                // 同一尺寸在不同规格组中归属不同，取第一个并提示确认
                child = matchedChildren.iterator().next();
                needConfirm = true;
            } else {
                child = nextEmptyChild(childNames, subValues);
                needConfirm = true;
            }
            if (child == null || subValues.containsKey(child)) {
                needConfirm = true;
                continue;
            }
            subValues.put(child, alignChildValue(child, size, standards));
        }
        if (subValues.isEmpty()) {
            return null;
        }
        return buildHit(joinSlash(orderedValues(childNames, subValues)), joinSlash(sizes),
                needConfirm, "size", childNames, subValues);
    }

    /** 该尺寸在已标定的规格组中归属哪些子规格 */
    private Set<String> matchChildrenBySize(String size, List<StandardValue> standards) {
        Set<String> children = new LinkedHashSet<>();
        for (StandardValue standard : standards) {
            for (Map.Entry<String, String> entry : standard.subValues.entrySet()) {
                if (sameSpecValue(entry.getValue(), size)) {
                    children.add(entry.getKey());
                }
            }
        }
        return children;
    }

    /** 按品类约定的子规格顺序取第一个尚未填值的子规格 */
    private String nextEmptyChild(List<String> childNames, Map<String, String> subValues) {
        for (String childName : childNames) {
            if (!subValues.containsKey(childName)) {
                return childName;
            }
        }
        return null;
    }

    private List<String> orderedValues(List<String> childNames, Map<String, String> subValues) {
        List<String> values = new ArrayList<>();
        for (String childName : childNames) {
            String value = subValues.get(childName);
            if (StringUtil.isNotEmpty(value)) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            values.addAll(subValues.values());
        }
        return values;
    }

    /** 子规格值的标准写法：优先取该子规格已标定的值，否则压缩尺寸写法 */
    private String alignChildValue(String childName, String size, List<StandardValue> standards) {
        for (StandardValue standard : standards) {
            String sub = standard.subValues.get(childName);
            if (StringUtil.isNotEmpty(sub) && sameSpecValue(sub, size)) {
                return sub;
            }
        }
        return normalizeSize(size);
    }

    /** 提取本行内各子规格的标注值，支持「被套：200*230」「被套200*230」「被套 200x230cm」 */
    private Map<String, String> findChildLabelValues(String raw, List<String> childNames) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!StringUtil.isNotEmpty(raw) || childNames == null) {
            return map;
        }
        for (String childName : childNames) {
            String name = trim(childName);
            if (name.isEmpty() || map.containsKey(name)) {
                continue;
            }
            String value = findChildLabelValue(raw, name);
            if (StringUtil.isNotEmpty(value)) {
                map.put(name, value);
            }
        }
        return map;
    }

    private String findChildLabelValue(String raw, String label) {
        int from = 0;
        while (from < raw.length()) {
            int index = raw.indexOf(label, from);
            if (index < 0) {
                return "";
            }
            String rest = raw.substring(index + label.length());
            String trimmed = rest.replaceFirst("^[\\s:：=＝|｜\\-–—、，,.;；。]+", "");
            boolean adjacent = trimmed.length() != rest.length() || trimmed.matches("^\\d.*");
            if (!adjacent || trimmed.isEmpty()) {
                from = index + label.length();
                continue;
            }
            Matcher sizeMatcher = SIZE_PATTERN.matcher(trimmed);
            if (sizeMatcher.find()) {
                return sizeMatcher.group(1).replaceAll("\\s+", "");
            }
            Matcher numberMatcher = NUMBER_PATTERN.matcher(trimmed);
            if (numberMatcher.lookingAt()) {
                return trimmed.substring(0, numberMatcher.end()).trim();
            }
            return trim(trimmed.split("[\\s,，;；。、]+")[0]);
        }
        return "";
    }

    /** 各子规格已配置的标准值（用于归一化与是否需确认判断） */
    private List<String> childStandardValues(String childName, List<StandardValue> standards) {
        List<String> values = new ArrayList<>();
        for (StandardValue standard : standards) {
            String sub = standard.subValues.get(childName);
            if (StringUtil.isNotEmpty(sub) && !values.contains(sub)) {
                values.add(sub);
            }
        }
        return values;
    }

    /** 归一化：优先采用类别/样本库标准写法，其次压缩尺寸写法 */
    private String alignValue(String text, Long categoryId, Long attributeId, List<String> standards) {
        if (standards == null || standards.isEmpty()) {
            return normalizeSize(text);
        }
        JSONObject aligned = specValueSampleService.align(text, categoryId, attributeId, standards);
        if (aligned.getBooleanValue("matched") && StringUtil.isNotEmpty(aligned.getString("value"))) {
            return aligned.getString("value");
        }
        return normalizeSize(text);
    }

    private String normalizeSize(String text) {
        String size = matchSize(text);
        return size.isEmpty() ? trim(text) : size;
    }

    private boolean containsValue(List<String> values, String target) {
        for (String value : values) {
            if (sameSpecValue(value, target)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameSpecValue(String a, String b) {
        if (!StringUtil.isNotEmpty(a) || !StringUtil.isNotEmpty(b)) {
            return false;
        }
        String normA = SpecValueNormalizer.normKey(a);
        String normB = SpecValueNormalizer.normKey(b);
        if (!normA.isEmpty() && normA.equals(normB)) {
            return true;
        }
        String numericA = SpecValueNormalizer.numericKey(a);
        String numericB = SpecValueNormalizer.numericKey(b);
        return numericA != null && numericA.equals(numericB);
    }

    private void applyAligned(ImageData data, String name, String candidate, Long categoryId, JSONObject dim,
                              List<String> standards, List<String> childNames, String source, String raw) {
        String finalValue = candidate;
        boolean needConfirm = standards.isEmpty();
        if (!standards.isEmpty()) {
            JSONObject aligned = specValueSampleService.align(candidate, categoryId, dim.getLong("attributeId"), standards);
            if (aligned.getBooleanValue("matched") && StringUtil.isNotEmpty(aligned.getString("value"))) {
                finalValue = aligned.getString("value");
                needConfirm = aligned.getBooleanValue("notConfigured");
            } else {
                needConfirm = true;
            }
        }
        data.values.put(name, buildHit(finalValue, candidate, needConfirm, source, childNames, null));
    }

    /** 构建命中结果：subValues 优先，否则按 "/" 拆出子规格值 */
    private JSONObject buildHit(String value, String rawValue, boolean needConfirm, String source,
                                List<String> childNames, Map<String, String> subValues) {
        JSONObject hit = new JSONObject();
        hit.put("value", value);
        hit.put("rawValue", rawValue);
        hit.put("source", source);
        hit.put("needConfirm", needConfirm);
        JSONObject subObj = new JSONObject(true);
        if (subValues != null && !subValues.isEmpty()) {
            subObj.putAll(subValues);
        } else if (childNames != null && !childNames.isEmpty() && value != null) {
            String[] parts = value.split("/");
            if (parts.length == childNames.size()) {
                for (int i = 0; i < childNames.size(); i++) {
                    String part = parts[i].trim();
                    if (!part.isEmpty()) {
                        subObj.put(childNames.get(i), part);
                    }
                }
            }
        }
        hit.put("subValues", subObj);
        return hit;
    }

    private String dimensionAlias(String name) {
        if (name == null) {
            return "";
        }
        if (name.contains("颜色") || name.contains("色")) {
            return "颜色";
        }
        if (name.contains("尺寸") || name.contains("规格") || name.contains("大小")) {
            return "尺寸";
        }
        if (name.contains("尺码") || name.contains("号型")) {
            return "尺码";
        }
        return "";
    }

    private String dimensionField(String name) {
        if (name == null) {
            return null;
        }
        if (name.contains("颜色") || name.contains("色")) {
            return "color";
        }
        if (name.contains("尺寸") || name.contains("规格") || name.contains("面积") || name.contains("大小")) {
            return "standard";
        }
        if (name.contains("尺码") || name.contains("号型")) {
            return "model";
        }
        if (name.contains("重量") || name.contains("克重")) {
            return "weight";
        }
        return null;
    }

    /** 提取「维度名 + 分隔符 + 值」行 */
    private String findLabelValue(String raw, String label) {
        if (!StringUtil.isNotEmpty(raw) || !StringUtil.isNotEmpty(label)) {
            return "";
        }
        for (String line : raw.split("\\r?\\n")) {
            String text = line.trim();
            if (!text.startsWith(label)) {
                continue;
            }
            String rest = text.substring(label.length());
            if (rest.isEmpty() || !rest.matches("^[\\s:：=＝|｜\\-–—、，,.;；。].*")) {
                continue;
            }
            String value = rest.replaceFirst("^[\\s:：=＝|｜\\-–—、，,.;；。]+", "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /** 提取价格：优先行内「零售价：199」，否则取第一个数字 */
    private String findPrice(String raw, String... labels) {
        for (String label : labels) {
            String value = findLabelValue(raw, label);
            if (StringUtil.isNotEmpty(value)) {
                Matcher matcher = NUMBER_PATTERN.matcher(value);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return "";
    }

    private String matchSize(String raw) {
        Matcher matcher = SIZE_PATTERN.matcher(raw == null ? "" : raw);
        return matcher.find() ? matcher.group(1).replaceAll("\\s+", "") : "";
    }

    /** 按出现顺序提取标签上的全部尺寸：一张图对应一个 SKU 的规格组 */
    private List<String> extractSizes(String raw) {
        List<String> sizes = new ArrayList<>();
        Matcher matcher = SIZE_PATTERN.matcher(raw == null ? "" : raw);
        while (matcher.find()) {
            String size = matcher.group(1).replaceAll("\\s+", "");
            if (!size.isEmpty() && !sizes.contains(size)) {
                sizes.add(size);
            }
        }
        return sizes;
    }

    private List<String> toStringList(JSONArray array) {
        List<String> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.size(); i++) {
            String value = array.getString(i);
            if (StringUtil.isNotEmpty(value)) {
                list.add(value.trim());
            }
        }
        return list;
    }

    /**
     * 标准规格值解析：兼容 ["200*230/230*250"] 与 [{value, subValues}] 两种写法；
     * 组合值按子规格顺序拆出标定关系（被套/床单/枕套 -> 各自尺寸）
     */
    private List<StandardValue> toStandardValues(JSONArray array, List<String> childNames) {
        List<StandardValue> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            String value = "";
            Map<String, String> subValues = new LinkedHashMap<>();
            if (item instanceof JSONObject) {
                JSONObject obj = (JSONObject) item;
                value = trim(obj.getString("value"));
                JSONObject subObj = obj.getJSONObject("subValues");
                if (subObj != null) {
                    for (String childName : childNames) {
                        String sub = trim(subObj.getString(childName));
                        if (!sub.isEmpty()) {
                            subValues.put(childName, sub);
                        }
                    }
                }
            } else if (item != null) {
                value = trim(String.valueOf(item));
            }
            if (!StringUtil.isNotEmpty(value)) {
                continue;
            }
            if (subValues.isEmpty() && !childNames.isEmpty() && value.contains("/")) {
                String[] parts = value.split("/", -1);
                if (parts.length == childNames.size()) {
                    for (int j = 0; j < childNames.size(); j++) {
                        String part = parts[j].trim();
                        if (!part.isEmpty()) {
                            subValues.put(childNames.get(j), part);
                        }
                    }
                }
            }
            list.add(new StandardValue(value, subValues));
        }
        return list;
    }

    private List<String> toStringValues(List<StandardValue> standards) {
        List<String> values = new ArrayList<>();
        for (StandardValue standard : standards) {
            values.add(standard.value);
        }
        return values;
    }

    private String joinSlash(List<String> texts) {
        StringBuilder builder = new StringBuilder();
        for (String text : texts) {
            if (builder.length() > 0) {
                builder.append("/");
            }
            builder.append(text == null ? "" : text);
        }
        return builder.toString();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** 属性名校验：只允许现有基础属性字段（含扩展1~7） */
    private boolean isPropertyField(String field) {
        return field != null && (FIELD_LABELS.containsKey(field) || field.matches("otherField[1-7]"));
    }

    /** 属性名归一化：去掉空格与分隔符，便于比对「检测员：」「检测员」 */
    private String normLabel(String label) {
        return label == null ? "" : label.replaceAll("[\\s:：=＝|｜\\-—–、，,.;；。]", "").toLowerCase();
    }

    /** 价格类标签属于 SKU 价格，不作为基础属性提示 */
    private boolean isSkuPriceLabel(String label) {
        return label != null && label.matches(".*(零售价|售价|吊牌价|原价|市场价|批发价|进货价|采购价|划线价).*");
    }

    /**
     * 识别到的标签 -> 基础属性字段：
     * 已知字段直接返回；其次查前端别名与已学习的标签别名（样本库 attributeId = -2）
     */
    private String resolvePropField(String label, Map<String, String> aliasMap, Long categoryId) {
        if (FIELD_LABELS.containsKey(label)) {
            return label;
        }
        String aliased = aliasMap.get(normLabel(label));
        if (isPropertyField(aliased)) {
            return aliased;
        }
        if (!StringUtil.isNotEmpty(label)) {
            return null;
        }
        try {
            JSONObject learned = specValueSampleService.match(label, categoryId, -2L);
            if (learned.getBooleanValue("matched")) {
                String field = learned.getString("canonicalValue");
                if (isPropertyField(field)) {
                    return field;
                }
            }
        } catch (Exception e) {
            // 样本库不可用时不影响识别
        }
        return null;
    }

    /** 基础属性值归一化：命中样本库（attributeId = -1）时回填标准写法 */
    private String normalizeBaseValue(String field, String value, Long categoryId) {
        if (!StringUtil.isNotEmpty(value)) {
            return value;
        }
        try {
            JSONObject learned = specValueSampleService.match(value, categoryId, -1L);
            if (learned.getBooleanValue("matched")) {
                String canonical = learned.getString("canonicalValue");
                if (StringUtil.isNotEmpty(canonical) && !isPropertyField(canonical)) {
                    return canonical;
                }
            }
        } catch (Exception e) {
            // 样本库不可用时保留原值
        }
        return value;
    }

    private static class ImageData {
        private String rawText;
        private String imagePath;
        private Map<String, String> props;
        private Map<String, JSONObject> values;
        /** 已对应到基础属性字段的值 */
        private Map<String, String> fieldValues;
        /** 未对应到基础属性的识别标签 -> 值 */
        private Map<String, String> unmatched;
    }

    /** 标准规格值与其子规格标定（子规格名 -> 已标定值） */
    private static class StandardValue {
        private final String value;
        private final Map<String, String> subValues;

        private StandardValue(String value, Map<String, String> subValues) {
            this.value = value;
            this.subValues = subValues == null ? new LinkedHashMap<String, String>() : subValues;
        }
    }
}
