package com.jsh.erp.service;

import com.jsh.erp.datasource.entities.MaterialCategory;
import com.jsh.erp.datasource.mappers.MaterialCategoryMapper;
import com.jsh.erp.utils.StringUtil;
import com.jsh.erp.utils.Tools;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Locale;

/**
 * Generates the three product identifiers used by the inventory workflow.
 * SPU: SP + two-digit type code + style number.
 * SKU: SPU + color code + size code.
 * Unit: SKU + unique unit serial.
 */
@Component
public class SkuCodeGenerator {
    @Resource
    private MaterialCategoryMapper materialCategoryMapper;

    /** Build an SPU using a stable, readable type segment and a unique style number. */
    public String generateSpuCode(Long categoryId, String styleNumber) {
        String style = normalizeStyleSegment(styleNumber);
        if ("NA".equals(style)) {
            style = Tools.getUUID_32().substring(0, 8).toUpperCase(Locale.ROOT);
        }
        return "SP" + getTypeCode(categoryId) + "-" + style;
    }

    /** Build a variant code. Attribute codes should come from the attribute dictionary. */
    public String generateSkuCode(String spuCode, String colorCode, String sizeCode) {
        if (!StringUtil.isNotEmpty(spuCode)) {
            throw new IllegalArgumentException("SPU码不能为空");
        }
        return spuCode.trim() + "-" + normalizeSegment(colorCode) + "-" + normalizeSegment(sizeCode);
    }

    /** Build a unique variant code from every configured specification dimension. */
    public String generateSkuCode(String spuCode, List<String> specificationCodes) {
        if (!StringUtil.isNotEmpty(spuCode)) {
            throw new IllegalArgumentException("SPU码不能为空");
        }
        StringBuilder code = new StringBuilder(spuCode.trim());
        int appended = 0;
        if (specificationCodes != null) {
            for (String specificationCode : specificationCodes) {
                if (!StringUtil.isNotEmpty(specificationCode)) {
                    continue;
                }
                code.append('-').append(normalizeSegment(specificationCode));
                appended++;
            }
        }
        if (appended == 0) {
            code.append("-NA-NA");
        }
        if (code.length() > 80) {
            throw new IllegalArgumentException("SKU编码超过80位，请精简规格值编码");
        }
        return code.toString();
    }

    /** Build one physical-unit label code from its SKU and a unique serial supplied by the caller. */
    public String generateUnitCode(String skuCode, String unitSerial) {
        if (!StringUtil.isNotEmpty(skuCode)) {
            throw new IllegalArgumentException("SKU码不能为空");
        }
        if (!StringUtil.isNotEmpty(unitSerial) || !unitSerial.matches("[0-9]{1,8}")) {
            throw new IllegalArgumentException("单件流水号须为1至8位数字");
        }
        return skuCode.trim() + "-" + normalizeSegment(unitSerial);
    }

    /** Build a unit code from a numeric serial allocated by a persistent sequence. */
    public String generateUnitCode(String skuCode, long unitSerial) {
        if (unitSerial < 1 || unitSerial > 99999999L) {
            throw new IllegalArgumentException("单件流水号范围为1至99999999");
        }
        return generateUnitCode(skuCode, String.format(Locale.ROOT, "%08d", unitSerial));
    }

    /** Return the category's two-digit type code; missing codes fall back to category ID. */
    public String getTypeCode(Long categoryId) {
        String rawCode = null;
        if (categoryId != null) {
            MaterialCategory category = materialCategoryMapper.selectByPrimaryKey(categoryId);
            if (category != null && StringUtil.isNotEmpty(category.getCode())) {
                rawCode = category.getCode();
            } else {
                rawCode = String.valueOf(categoryId);
            }
        }
        String digits = digitsOnly(rawCode);
        if (digits.isEmpty()) {
            return "00";
        }
        if (digits.length() == 1) {
            return "0" + digits;
        }
        return digits.substring(digits.length() - 2);
    }

    private String normalizeSegment(String value) {
        if (!StringUtil.isNotEmpty(value)) {
            return "NA";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (normalized.isEmpty()) {
            return "NA";
        }
        if (normalized.length() > 8) {
            throw new IllegalArgumentException("编码段不能超过8位: " + value);
        }
        return normalized;
    }

    private String normalizeStyleSegment(String value) {
        if (!StringUtil.isNotEmpty(value)) {
            return "NA";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (normalized.length() > 16) {
            throw new IllegalArgumentException("款号不能超过16位: " + value);
        }
        return normalized.isEmpty() ? "NA" : normalized;
    }

    private String digitsOnly(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }
}
