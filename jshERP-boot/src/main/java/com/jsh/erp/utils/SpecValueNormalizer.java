package com.jsh.erp.utils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规格值归一化工具
 * 把 200×230cm / 200 x 230 CM / 200·230 / 200*230厘米 等不同写法归一到同一个 key，
 * 供规格值样本库比对（如 200x230cm -> 200*230|cm）。
 */
public class SpecValueNormalizer {

    /** 数字部分 + 可选单位，例如 200*230cm、500ml、XL（无数字不匹配） */
    private static final Pattern DIMENSION_PATTERN = Pattern.compile(
            "^(\\d+(?:\\.\\d+)?(?:\\*\\d+(?:\\.\\d+)?)*)([a-z\\u4e00-\\u9fa5]*)$");

    /** 单位别名 -> 标准单位 */
    private static final Map<String, String> UNIT_ALIAS = new HashMap<>();

    static {
        UNIT_ALIAS.put("cm", "cm");
        UNIT_ALIAS.put("厘米", "cm");
        UNIT_ALIAS.put("公分", "cm");
        UNIT_ALIAS.put("mm", "mm");
        UNIT_ALIAS.put("毫米", "mm");
        UNIT_ALIAS.put("m", "m");
        UNIT_ALIAS.put("米", "m");
        UNIT_ALIAS.put("kg", "kg");
        UNIT_ALIAS.put("千克", "kg");
        UNIT_ALIAS.put("公斤", "kg");
        UNIT_ALIAS.put("g", "g");
        UNIT_ALIAS.put("克", "g");
        UNIT_ALIAS.put("ml", "ml");
        UNIT_ALIAS.put("毫升", "ml");
        UNIT_ALIAS.put("l", "l");
        UNIT_ALIAS.put("升", "l");
        UNIT_ALIAS.put("in", "in");
        UNIT_ALIAS.put("inch", "in");
        UNIT_ALIAS.put("英寸", "in");
        UNIT_ALIAS.put("寸", "寸");
        UNIT_ALIAS.put("尺", "尺");
    }

    private SpecValueNormalizer() {
    }

    /**
     * 归一化键：数值 + "|" + 单位（如 200*230|cm）；无法拆分时返回压缩后的原文（如 xl）
     */
    public static String normKey(String rawText) {
        String compact = compact(rawText);
        Matcher matcher = DIMENSION_PATTERN.matcher(compact);
        if (matcher.matches()) {
            String unit = UNIT_ALIAS.get(matcher.group(2));
            if (matcher.group(2).isEmpty() || unit != null) {
                String numbers = normalizeNumbers(matcher.group(1));
                return unit == null || unit.isEmpty() ? numbers : numbers + "|" + unit;
            }
        }
        return compact;
    }

    /**
     * 数值部分（如 200*230），用于缺失单位时的模糊比对；非数值规格返回 null
     */
    public static String numericKey(String rawText) {
        Matcher matcher = DIMENSION_PATTERN.matcher(compact(rawText));
        if (matcher.matches()) {
            return normalizeNumbers(matcher.group(1));
        }
        return null;
    }

    /** 全角转半角、大小写统一、乘号统一、去空白 */
    private static String compact(String rawText) {
        if (rawText == null) {
            return "";
        }
        String text = Normalizer.normalize(rawText.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == '×' || c == '✕' || c == '✖' || c == '╳' || c == '·' || c == '•' || c == '＊') {
                sb.append('*');
            } else {
                sb.append(c);
            }
        }
        // 纯字母 x 只作为尺寸分隔符处理（200x230），避免误伤 XL 等规格
        return sb.toString().replaceAll("(?<=\\d)x(?=\\d)", "*")
                .replaceAll("(?<=\\d)x(?=[a-z\\u4e00-\\u9fa5]|$)", "*");
    }

    /** 去掉多余 0：200.0 -> 200 */
    private static String normalizeNumbers(String numbers) {
        String[] parts = numbers.split("\\*");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append('*');
            }
            try {
                sb.append(new BigDecimal(part).stripTrailingZeros().toPlainString());
            } catch (NumberFormatException e) {
                sb.append(part);
            }
        }
        return sb.toString();
    }
}
