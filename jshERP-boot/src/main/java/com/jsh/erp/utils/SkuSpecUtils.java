package com.jsh.erp.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.datasource.entities.SkuSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SKU 规格冗余字段生成工具
 *
 * 三条硬性约定（交接文档红线）：
 * 1. spec_json / spec_summary 必须由后端生成，严禁前端传入
 * 2. 拼接顺序必须统一按 sort_no 排序，否则组合搜索会失效
 * 3. 格式固定：spec_summary = 颜色:红;尺码:L（英文冒号分号，无空格）
 */
public class SkuSpecUtils {

    /** 摘要中规格名与规格值的分隔符 */
    public static final String SUMMARY_NAME_VALUE_SEPARATOR = ":";

    /** 摘要中规格项之间的分隔符 */
    public static final String SUMMARY_ITEM_SEPARATOR = ";";

    /** sku_name 中规格值之间的分隔符 */
    public static final String SKU_NAME_SEPARATOR = " ";

    private SkuSpecUtils() {
    }

    /**
     * 过滤空规格并按 sort_no 升序排序（sort_no 相同保持原顺序，稳定排序）
     */
    public static List<SkuSpec> sortBySortNo(List<SkuSpec> specs) {
        List<SkuSpec> list = new ArrayList<>();
        if (specs == null) {
            return list;
        }
        for (SkuSpec spec : specs) {
            if (spec == null) {
                continue;
            }
            if (isBlank(spec.getSpecName()) || isBlank(spec.getSpecValue())) {
                continue;
            }
            list.add(spec);
        }
        list.sort(Comparator.comparingInt(spec -> spec.getSortNo() == null ? 0 : spec.getSortNo()));
        return list;
    }

    /**
     * 生成前端渲染用 JSON：[{"name":"颜色","value":"红"}]
     */
    public static String buildSpecJson(List<SkuSpec> specs) {
        JSONArray array = new JSONArray();
        for (SkuSpec spec : sortBySortNo(specs)) {
            JSONObject item = new JSONObject(true);
            item.put("name", spec.getSpecName().trim());
            item.put("value", spec.getSpecValue().trim());
            array.add(item);
        }
        return array.toJSONString();
    }

    /**
     * 生成搜索用摘要：颜色:红;尺码:L
     */
    public static String buildSpecSummary(List<SkuSpec> specs) {
        StringBuilder builder = new StringBuilder();
        for (SkuSpec spec : sortBySortNo(specs)) {
            if (builder.length() > 0) {
                builder.append(SUMMARY_ITEM_SEPARATOR);
            }
            builder.append(spec.getSpecName().trim())
                   .append(SUMMARY_NAME_VALUE_SEPARATOR)
                   .append(spec.getSpecValue().trim());
        }
        return builder.toString();
    }

    /**
     * 生成展示名：红 L（仅规格值，按 sort_no 顺序空格连接）
     */
    public static String buildSkuName(List<SkuSpec> specs) {
        StringBuilder builder = new StringBuilder();
        for (SkuSpec spec : sortBySortNo(specs)) {
            if (builder.length() > 0) {
                builder.append(SKU_NAME_SEPARATOR);
            }
            builder.append(spec.getSpecValue().trim());
        }
        return builder.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
