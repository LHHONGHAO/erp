package com.jsh.erp.datasource.entities;

/**
 * 同一识别原文下各最终值的出现次数统计
 */
public class SpecValueCorrectionStat {

    private String finalValue;

    private String finalNormKey;

    private Long total;

    public String getFinalValue() {
        return finalValue;
    }

    public void setFinalValue(String finalValue) {
        this.finalValue = finalValue;
    }

    public String getFinalNormKey() {
        return finalNormKey;
    }

    public void setFinalNormKey(String finalNormKey) {
        this.finalNormKey = finalNormKey;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }
}
