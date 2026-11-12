package com.jsh.erp.utils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规格值归一化工具
 * 把 200×230cm / 200 x 230 CM / 200·230 / 200-230 / 200cm*230cm 等不同写法归一到同一个 key，
 * 供规格值样本库比对（如 200x230cm -> 200*230|cm）。
 */
public class SpecValueNormalizer {

    /** 单个维度：数字 + 可选单位 */
    private static final Pattern DIMENSION_PATTERN = Pattern.compile(
            "^(\\d+(?:\\.\\d+)?)([a-z\\u4e00-\\u9fa5]*)$");

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
     * 归一化键：数值 + "|" + 单位（如 200*230|cm）；
     * 多维尺寸按数值升序排列（230x200 与 200x230 等价）；无法拆分时返回压缩后的原文（如 xl）
     */
    public static String normKey(String rawText) {
        Parsed parsed = parse(rawText);
        if (parsed == null) {
            return compact(rawText);
        }
        String numbers = String.join("*", parsed.numbers);
        return parsed.unit.isEmpty() ? numbers : numbers + "|" + parsed.unit;
    }

    /**
     * 数值部分（如 200*230），用于缺失单位时的模糊比对；非数值规格返回 null
     */
    public static String numericKey(String rawText) {
        Parsed parsed = parse(rawText);
        return parsed == null ? null : String.join("*", parsed.numbers);
    }

    /**
     * 从候选标准写法中挑选与原始文本最接近的一个：
     * 先比归一化键，再比数值键（忽略单位），都不中返回 null
     */
    public static String alignToStandard(String rawText, List<String> standardValues) {
        if (standardValues == null || standardValues.isEmpty()) {
            return null;
        }
        String normKey = normKey(rawText);
        String numericKey = numericKey(rawText);
        String numericHit = null;
        for (String standard : standardValues) {
            if (standard == null || standard.trim().isEmpty()) {
                continue;
            }
            if (normKey.equals(normKey(standard))) {
                return standard;
            }
            if (numericHit == null && numericKey != null && numericKey.equals(numericKey(standard))) {
                numericHit = standard;
            }
        }
        return numericHit;
    }

    /** 解析为「数值列表 + 统一单位」，无法解析返回 null */
    private static Parsed parse(String rawText) {
        String compact = compact(rawText);
        if (compact.isEmpty()) {
            return null;
        }
        // 逐维拆分，分别取数字与单位（支持 200cm*230cm 这种逐维单位）
        String[] parts = compact.split("\\*");
        List<String> numbers = new ArrayList<>();
        String unit = null;
        boolean unitConflict = false;
        for (String part : parts) {
            Matcher matcher = DIMENSION_PATTERN.matcher(part);
            if (!matcher.matches()) {
                return null;
            }
            numbers.add(normalizeNumber(matcher.group(1)));
            String partUnit = normalizeUnit(matcher.group(2));
            if (partUnit.isEmpty()) {
                continue;
            }
            if (unit == null) {
                unit = partUnit;
            } else if (!unit.equals(partUnit)) {
                unitConflict = true;
            }
        }
        if (unitConflict) {
            // 单位不一致（如 1m*50cm）无法归到同一单位，退回原文匹配
            return null;
        }
        // 多维尺寸与数值升序无关，排序后比较（230x200 等价于 200x230）
        numbers.sort((a, b) -> new BigDecimal(a).compareTo(new BigDecimal(b)));
        return new Parsed(numbers, unit == null ? "" : unit);
    }

    /** 全角转半角、大小写统一、乘号/分隔符统一、去空白 */
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
        String result = sb.toString();
        // 纯字母 x 只作为尺寸分隔符处理（200x230），避免误伤 XL 等规格
        result = result.replaceAll("(?<=\\d)x(?=\\d)", "*")
                .replaceAll("(?<=\\d)x(?=[a-z\\u4e00-\\u9fa5]|$)", "*");
        // 短横/斜杠/下划线夹在数字之间时按尺寸分隔符处理（200-230、200/230）
        return result.replaceAll("(?<=\\d)[-/](?=\\d)", "*");
    }

    private static String normalizeUnit(String unit) {
        if (unit == null || unit.isEmpty()) {
            return "";
        }
        String alias = UNIT_ALIAS.get(unit);
        return alias == null ? unit : alias;
    }

    /** 去掉多余 0：200.0 -> 200 */
    private static String normalizeNumber(String number) {
        try {
            return new BigDecimal(number).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return number;
        }
    }

    private static class Parsed {
        private final List<String> numbers;
        private final String unit;

        private Parsed(List<String> numbers, String unit) {
            this.numbers = numbers;
            this.unit = unit;
        }
    }
}
