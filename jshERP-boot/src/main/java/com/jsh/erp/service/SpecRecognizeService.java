package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.utils.OcrUtil;
import com.jsh.erp.utils.SpecValueNormalizer;
import com.jsh.erp.utils.StringUtil;
import com.jsh.erp.datasource.mappers.MaterialMapperEx;
import com.jsh.erp.datasource.entities.User;
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
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单图商品识别：解析一张商品图片中的文字，完成属性名归一、属性值归一与属性补全建议。
 * 只处理「一张图 = 一个商品」，不做多图聚合，识别阶段不写入商品数据。
 */
@Service
public class SpecRecognizeService {

    /** 基础属性的输出顺序 */
    private static final String[] BASE_FIELD_ORDER = {
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

    @Resource
    private MaterialMapperEx materialMapperEx;

    @Resource
    private UserService userService;

    /**
     * 识别单张图片
     *
     * @param image       图中文字 {rawText, imagePath}
     * @param categoryId  类别
     * @param dimensions  类别已配置的规格维度 [{name, attributeId, childNames[], standards[]}]
     * @param labelAliases 前端维护的属性名别名 {识别到的属性名: 基础属性字段}
     */
    public JSONObject recognize(JSONObject image, Long categoryId, JSONArray dimensions, JSONObject labelAliases) {
        String rawText = image == null ? "" : trim(image.getString("rawText"));
        String imagePath = image == null ? "" : trim(image.getString("imagePath"));
        JSONObject result = new JSONObject();
        result.put("rawText", rawText);
        result.put("imagePath", imagePath);
        // 同类别历史属性分布：用于提示「类别常值建议」（补全）
        JSONObject profiles = propertyProfiles(categoryId);
        result.put("propertyProfiles", profiles);

        List<JSONObject> dims = normalizeDimensions(dimensions);
        Map<String, String> aliasMap = buildAliasMap(labelAliases);
        Set<String> specLabels = collectSpecLabels(dims);

        // 1. 属性名归一：识别标签 -> 基础属性字段；值归一：命中样本库时回填标准写法
        Map<String, String> props = OcrUtil.parseProductText(rawText);
        Map<String, String> fieldValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            String label = entry.getKey();
            String value = trim(entry.getValue());
            if (value.isEmpty() || "rawText".equals(label)) {
                continue;
            }
            String field = resolvePropField(label, aliasMap, categoryId);
            if (field != null) {
                // 尺寸是规格值；类别已配置尺寸/规格维度时，不把尺寸同时写进商品基础颜色属性
                if ("color".equals(field) && hasSizeDimension(dims) && !extractSizes(value).isEmpty()) {
                    continue;
                }
                fieldValues.put(field, value);
            }
        }
        JSONObject commonProps = new JSONObject();
        JSONObject commonPropsRaw = new JSONObject();
        Set<String> fields = new LinkedHashSet<>(Arrays.asList(BASE_FIELD_ORDER));
        fields.addAll(fieldValues.keySet());
        for (String field : fields) {
            String value = trim(fieldValues.get(field));
            if (value.isEmpty()) {
                continue;
            }
            commonPropsRaw.put(field, value);
            commonProps.put(field, normalizeBaseValue(field, value, categoryId, profiles.getJSONObject(field)));
        }
        result.put("commonProps", commonProps);
        result.put("commonPropsRaw", commonPropsRaw);

        // 2. 规格维度：单图内按关键词/子规格/行内写法抽取并归一到类别标准写法
        ImageData data = new ImageData();
        data.rawText = rawText;
        data.imagePath = imagePath;
        data.props = props;
        data.values = new LinkedHashMap<>();
        for (JSONObject dim : dims) {
            extractDimensionValue(data, dim, categoryId);
        }
        JSONArray dimensionList = new JSONArray();
        for (JSONObject dim : dims) {
            JSONObject out = new JSONObject(true);
            out.put("name", dim.getString("name"));
            out.put("attributeId", dim.get("attributeId"));
            out.put("childNames", dim.getJSONArray("childNames") == null ? new JSONArray() : dim.getJSONArray("childNames"));
            JSONObject childValues = childStandardValues(dim);
            if (!childValues.isEmpty()) {
                out.put("childValues", childValues);
            }
            JSONObject hit = data.values.get(dim.getString("name"));
            JSONArray values = new JSONArray();
            if (hit != null && StringUtil.isNotEmpty(hit.getString("value"))) {
                hit.put("imagePath", imagePath);
                hit.put("rawText", rawText);
                values.add(hit);
            }
            out.put("values", values);
            out.put("detected", values.size() > 0);
            out.put("needConfirm", hit != null && hit.getBooleanValue("needConfirm"));
            dimensionList.add(out);
        }
        result.put("dimensions", dimensionList);

        // 3. 识别到但未对应到基础属性的标签：交给前端确认，确认后学习属性名别名
        JSONArray unmatchedProps = new JSONArray();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            String label = entry.getKey();
            String value = trim(entry.getValue());
            if (value.isEmpty() || "rawText".equals(label)) {
                continue;
            }
            if (resolvePropField(label, aliasMap, categoryId) != null) {
                continue;
            }
            if ("standard".equals(label) || "model".equals(label) || "color".equals(label)
                    || "barCode".equals(label) || "mnemonic".equals(label)
                    || specLabels.contains(normLabel(label)) || isSkuPriceLabel(label)) {
                continue;
            }
            JSONObject item = new JSONObject(true);
            item.put("label", label);
            item.put("value", value);
            item.put("rawValue", value);
            item.put("rawText", rawText);
            item.put("imagePath", imagePath);
            unmatchedProps.add(item);
        }
        result.put("unmatchedProps", unmatchedProps);

        // 4. 单图单商品的 SKU 信息（条码与价格），前端用于回填唯一 SKU 行
        JSONObject skuRow = new JSONObject();
        skuRow.put("barCode", trim(props.get("barCode")));
        skuRow.put("imagePath", imagePath);
        skuRow.put("commodityDecimal", findPrice(rawText, "零售价", "售价", "吊牌价"));
        skuRow.put("marketPrice", findPrice(rawText, "划线价", "原价", "市场价"));
        skuRow.put("purchaseDecimal", findPrice(rawText, "进货价", "采购价"));
        skuRow.put("wholesaleDecimal", findPrice(rawText, "批发价"));
        result.put("skuRow", skuRow);
        return result;
    }

    /** 每个子规格在本品类下已配置的标准值（少量固定值），作为识别结果的补全建议 */
    private JSONObject childStandardValues(JSONObject dim) {
        JSONObject result = new JSONObject(true);
        List<String> childNames = toStringList(dim.getJSONArray("childNames"));
        if (childNames.isEmpty()) {
            return result;
        }
        List<StandardValue> standards = toStandardValues(dim.getJSONArray("standards"), childNames);
        for (String child : childNames) {
            JSONArray values = new JSONArray();
            for (String value : childStandardValues(child, standards)) {
                if (!values.contains(value)) {
                    values.add(value);
                }
            }
            if (!values.isEmpty()) {
                result.put(child, values);
            }
        }
        return result;
    }

    private Map<String, String> buildAliasMap(JSONObject labelAliases) {
        Map<String, String> aliasMap = new LinkedHashMap<>();
        if (labelAliases == null) {
            return aliasMap;
        }
        for (String key : labelAliases.keySet()) {
            String field = labelAliases.getString(key);
            if (StringUtil.isNotEmpty(key) && StringUtil.isNotEmpty(field) && isPropertyField(field)) {
                aliasMap.put(normLabel(key), field);
            }
        }
        return aliasMap;
    }

    /** 规格维度名及其子项（如 规格/被套/床单/枕套）属于 SKU 基础属性，不进入「其他属性」 */
    private Set<String> collectSpecLabels(List<JSONObject> dims) {
        Set<String> specLabels = new LinkedHashSet<>();
        for (JSONObject dim : dims) {
            specLabels.add(normLabel(dim.getString("name")));
            for (String child : toStringList(dim.getJSONArray("childNames"))) {
                specLabels.add(normLabel(child));
            }
        }
        return specLabels;
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
                data.values.put(name, buildHit(standard.value, standard.value, false, "keyword", childNames, standard.subValues, standard.subValues));
                return;
            }
        }

        // 2. 规格以组判定：一张图 = 一个 SKU，按品类约定的子规格组（被套/床单/枕套）依次归位
        // 仅尺寸类规格参与：花色/颜色等维度即使配置了子项，也不能因文本中出现床品尺寸而误收尺寸值
        List<String> sizes = isSizeGroupDimension(name, childNames, standards)
                ? extractSizes(raw) : Collections.<String>emptyList();
        if (!sizes.isEmpty()) {
            JSONObject sizeHit = buildHitBySizes(sizes, standards, childNames, categoryId, attributeId);
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
                data.values.put(name, buildHit(standard.value, standard.value, true, "keyword", childNames, standard.subValues, standard.subValues));
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

    /**
     * 是否是「尺寸组」规格：只有规格名属于尺寸类，或子规格本身的标准值就是尺寸时，
     * 才把图中尺寸依次归入子规格；花色/颜色这类普通维度不参与。
     */
    private boolean isSizeGroupDimension(String name, List<String> childNames, List<StandardValue> standards) {
        if (childNames == null || childNames.isEmpty()) return false;
        if (isSizeDimension(name)) return true;
        for (String child : childNames) {
            if (isSizeDimension(child)) return true;
        }
        for (StandardValue standard : standards) {
            for (String sub : standard.subValues.values()) {
                if (SIZE_PATTERN.matcher(sub).find()) return true;
            }
        }
        return false;
    }

    private boolean hasSizeDimension(List<JSONObject> dimensions) {
        if (dimensions == null) return false;
        for (JSONObject dimension : dimensions) {
            if (dimension != null && isSizeDimension(dimension.getString("name"))) return true;
        }
        return false;
    }

    /** 子规格标签命中：把每张标签上的「子规格：值」归一到该子规格自己的类别标准写法 */
    private JSONObject buildHitFromChildLabels(Map<String, String> labelSubValues, List<StandardValue> standards,
                                               List<String> childNames, Long categoryId, Long attributeId) {
        Map<String, String> subValues = new LinkedHashMap<>();
        Map<String, String> rawSubValues = new LinkedHashMap<>();
        List<String> values = new ArrayList<>();
        boolean needConfirm = false;
        List<String> orderedNames = childNames.isEmpty() ? new ArrayList<>(labelSubValues.keySet()) : childNames;
        for (String childName : orderedNames) {
            String text = trim(labelSubValues.get(childName));
            if (text.isEmpty()) {
                continue;
            }
            List<String> childStandards = childStandardValues(childName, standards);
            String aligned = alignValue(text, categoryId, attributeId, childName, childStandards);
            subValues.put(childName, aligned);
            rawSubValues.put(childName, text);
            values.add(aligned);
            if (!childStandards.isEmpty() && !containsValue(childStandards, aligned)) {
                needConfirm = true;
            }
        }
        return buildHit(joinSlash(values), joinSlash(values), needConfirm, "label", childNames, subValues, rawSubValues);
    }

    /** 子规格名命中：尺寸优先取该子规格已配置的写法，否则原样保留尺寸 */
    private JSONObject buildHitByChildName(String childName, String size, List<StandardValue> standards,
                                           Long categoryId, Long attributeId) {
        Map<String, String> subValues = new LinkedHashMap<>();
        Map<String, String> rawSubValues = new LinkedHashMap<>();
        if (size.isEmpty()) {
            return buildHit(childName, childName, true, "keyword", Collections.<String>emptyList(), subValues, rawSubValues);
        }
        List<String> childStandards = childStandardValues(childName, standards);
        String aligned = alignValue(size, categoryId, attributeId, childName, childStandards);
        boolean needConfirm = !childStandards.isEmpty() && !containsValue(childStandards, aligned);
        subValues.put(childName, aligned);
        rawSubValues.put(childName, size);
        return buildHit(aligned, size, needConfirm, "keyword", Collections.singletonList(childName), subValues, rawSubValues);
    }

    /**
     * 规格按组判定：一张标签上的多个尺寸依次归入品类约定的子规格（被套/床单/枕套）。
     * 已标定的尺寸采用标定归属，未标定的按约定顺序补位并标记需确认。
     */
    private JSONObject buildHitBySizes(List<String> sizes, List<StandardValue> standards, List<String> childNames,
                                       Long categoryId, Long attributeId) {
        Map<String, String> subValues = new LinkedHashMap<>();
        Map<String, String> rawSubValues = new LinkedHashMap<>();
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
            subValues.put(child, alignChildValue(child, size, standards, categoryId, attributeId));
            rawSubValues.put(child, size);
        }
        if (subValues.isEmpty()) {
            return null;
        }
        return buildHit(joinSlash(orderedValues(childNames, subValues)), joinSlash(sizes),
                needConfirm, "size", childNames, subValues, rawSubValues);
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

    /** 子规格值的标准写法：取该子规格自己的配置值与样本，避免与其他子规格串值 */
    private String alignChildValue(String childName, String size, List<StandardValue> standards,
                                   Long categoryId, Long attributeId) {
        List<String> childStandards = childStandardValues(childName, standards);
        return alignValue(size, categoryId, attributeId, childName, childStandards);
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
    private String alignValue(String text, Long categoryId, Long attributeId, String childName, List<String> standards) {
        if (text == null) {
            return "";
        }
        JSONObject aligned = specValueSampleService.align(text, categoryId, attributeId, childName, standards);
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
                data.values.put(name, buildHit(finalValue, candidate, needConfirm, source, childNames, null, null));
    }

    /** 构建命中结果：subValues 优先，否则按 "/" 拆出子规格值；subRawValues 保留每个子规格的识别原文用于学习 */
    private JSONObject buildHit(String value, String rawValue, boolean needConfirm, String source,
                                List<String> childNames, Map<String, String> subValues,
                                Map<String, String> rawSubValues) {
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
        JSONObject rawSubObj = new JSONObject(true);
        if (rawSubValues != null) {
            rawSubObj.putAll(rawSubValues);
        }
        hit.put("subRawValues", rawSubObj);
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

    /**
     * 基础属性值归一化：先命中该品类该字段的已学样本，
     * 再对齐到该品类历史上出现过的少量固定写法（如安全类别、执行标准），保持同品类属性值的单一性。
     */
    private String normalizeBaseValue(String field, String value, Long categoryId, JSONObject profile) {
        if (!StringUtil.isNotEmpty(value)) {
            return value;
        }
        try {
            JSONObject learned = specValueSampleService.match(value, categoryId, baseFieldSampleId(field));
            if (learned.getBooleanValue("matched")) {
                String canonical = learned.getString("canonicalValue");
                if (StringUtil.isNotEmpty(canonical) && !isPropertyField(canonical)) {
                    return canonical;
                }
            }
        } catch (Exception e) {
            // 样本库不可用时保留原值
        }
        if (profile != null) {
            JSONArray candidates = profile.getJSONArray("candidates");
            if (candidates != null && candidates.size() > 0) {
                List<String> values = new ArrayList<>();
                for (int i = 0; i < candidates.size(); i++) {
                    values.add(candidates.getString(i));
                }
                String standard = SpecValueNormalizer.alignToStandard(value, values);
                if (StringUtil.isNotEmpty(standard)) {
                    return standard;
                }
            }
        }
        return value;
    }

    /** Negative IDs below -100 isolate learned values by base-property field; -2 remains label-alias learning. */
    private Long baseFieldSampleId(String field) {
        if ("name".equals(field)) return -101L;
        if ("brand".equals(field)) return -102L;
        if ("unit".equals(field)) return -103L;
        if ("mfrs".equals(field)) return -104L;
        if ("fabric".equals(field)) return -105L;
        if ("safetyCategory".equals(field)) return -106L;
        if ("executiveStandard".equals(field)) return -107L;
        if ("grade".equals(field)) return -108L;
        if ("inspection".equals(field)) return -109L;
        if ("filler".equals(field)) return -110L;
        if ("weight".equals(field)) return -111L;
        if ("color".equals(field)) return -112L;
        if ("standard".equals(field)) return -113L;
        if ("model".equals(field)) return -114L;
        if ("otherField1".equals(field)) return -115L;
        if ("otherField2".equals(field)) return -116L;
        if ("otherField3".equals(field)) return -117L;
        if ("otherField4".equals(field)) return -118L;
        if ("otherField5".equals(field)) return -119L;
        if ("otherField6".equals(field)) return -120L;
        if ("otherField7".equals(field)) return -121L;
        return -1L;
    }

    /** Only a category property with enough history and one normalized value is eligible for completion suggestion. */
    private JSONObject propertyProfiles(Long categoryId) {
        try {
            User user = userService.getCurrentUser();
            JSONObject result = new JSONObject();
            result.putAll(propertyProfiles(categoryId, user == null ? null : user.getTenantId()));
            return result;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private Map<String, JSONObject> propertyProfiles(Long categoryId, Long tenantId) {
        Map<String, JSONObject> profiles = new LinkedHashMap<>();
        if (categoryId == null) return profiles;
        Map<String, Integer> totals = new LinkedHashMap<>();
        Map<String, Set<String>> normalizedValues = new LinkedHashMap<>();
        Map<String, String> examples = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> valueCounts = new LinkedHashMap<>();
        accumulatePropertyStats(materialMapperEx.selectCategoryPropertyValueStats(categoryId, tenantId), "", totals, normalizedValues, examples, valueCounts);
        try {
            accumulatePropertyStats(materialMapperEx.selectCategorySkuValueStats(categoryId, tenantId), "spec:", totals, normalizedValues, examples, valueCounts);
        } catch (Exception e) {
            // Older database engines may not support JSON_TABLE; base property profiles still work.
        }
        for (String field : totals.keySet()) {
            JSONObject profile = new JSONObject();
            int samples = totals.get(field);
            int distinct = normalizedValues.get(field).size();
            boolean stableSingle = samples >= 5 && distinct == 1;
            profile.put("field", field);
            profile.put("sampleCount", samples);
            profile.put("distinctValueCount", distinct);
            profile.put("stableSingle", stableSingle);
            profile.put("modeValue", stableSingle ? examples.get(field) : null);
            profile.put("modeRatio", stableSingle ? 1D : 0D);
            // 同品类该属性出现过的少量固定写法，用于值归一与补全
            JSONArray candidates = new JSONArray();
            Map<String, Integer> counts = valueCounts.get(field);
            if (counts != null) {
                List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
                Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
                    @Override
                    public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                        return b.getValue() - a.getValue();
                    }
                });
                for (Map.Entry<String, Integer> entry : entries) {
                    if (candidates.size() >= 8) {
                        break;
                    }
                    candidates.add(entry.getKey());
                }
            }
            profile.put("candidates", candidates);
            profiles.put(field, profile);
        }
        return profiles;
    }

    private void accumulatePropertyStats(List<Map<String, Object>> rows, String prefix,
                                         Map<String, Integer> totals, Map<String, Set<String>> normalizedValues,
                                         Map<String, String> examples, Map<String, Map<String, Integer>> valueCounts) {
        if (rows == null) return;
        for (Map<String, Object> row : rows) {
            String field = prefix + String.valueOf(row.get("fieldName"));
            String value = String.valueOf(row.get("valueText"));
            int count = ((Number) row.get("valueCount")).intValue();
            totals.put(field, totals.getOrDefault(field, 0) + count);
            String normalized = SpecValueNormalizer.normKey(value);
            normalizedValues.computeIfAbsent(field, key -> new HashSet<>()).add(normalized);
            examples.putIfAbsent(field, value);
            valueCounts.computeIfAbsent(field, key -> new LinkedHashMap<>()).merge(value, count, Integer::sum);
        }
    }

    private static class ImageData {
        private String rawText;
        private String imagePath;
        private Map<String, String> props;
        private Map<String, JSONObject> values;
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
