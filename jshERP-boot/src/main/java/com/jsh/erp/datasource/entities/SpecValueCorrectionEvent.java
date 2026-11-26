package com.jsh.erp.datasource.entities;

import java.util.Date;

/**
 * 识别值学习事件：每次保存商品时按「类目 + 字段 + 子项 + 识别原文」记录最终采纳值
 * 确认（未修改）与纠错（手动修改）都会记录，由占比统计决定是否升级为标准值
 */
public class SpecValueCorrectionEvent {

    private Long id;

    private Long tenantId;

    private Long categoryId;

    private Long attributeId;

    private String childName;

    private String rawText;

    private String rawNormKey;

    private String autoValue;

    private String finalValue;

    private String finalNormKey;

    /** 1=用户手动修改过；0=沿用自动回填值 */
    private String modified;

    private Long materialId;

    private Long userId;

    private Date createTime;

    private String deleteFlag;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getAttributeId() {
        return attributeId;
    }

    public void setAttributeId(Long attributeId) {
        this.attributeId = attributeId;
    }

    public String getChildName() {
        return childName;
    }

    public void setChildName(String childName) {
        this.childName = childName;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getRawNormKey() {
        return rawNormKey;
    }

    public void setRawNormKey(String rawNormKey) {
        this.rawNormKey = rawNormKey;
    }

    public String getAutoValue() {
        return autoValue;
    }

    public void setAutoValue(String autoValue) {
        this.autoValue = autoValue;
    }

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

    public String getModified() {
        return modified;
    }

    public void setModified(String modified) {
        this.modified = modified;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getDeleteFlag() {
        return deleteFlag;
    }

    public void setDeleteFlag(String deleteFlag) {
        this.deleteFlag = deleteFlag;
    }
}
