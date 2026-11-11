package com.jsh.erp.datasource.entities;

import java.util.Date;

/**
 * 规格值学习日志：记录一次识别结果与人工纠正值
 */
public class SpecLearnLog {
    private Long id;
    private Long tenantId;
    private String rawText;
    private Long attrId;
    private String originalValue;
    private String correctedValue;
    private Long materialId;
    private Long userId;
    private Date createTime;
    private String deleteFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public Long getAttrId() { return attrId; }
    public void setAttrId(Long attrId) { this.attrId = attrId; }

    public String getOriginalValue() { return originalValue; }
    public void setOriginalValue(String originalValue) { this.originalValue = originalValue; }

    public String getCorrectedValue() { return correctedValue; }
    public void setCorrectedValue(String correctedValue) { this.correctedValue = correctedValue; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getDeleteFlag() { return deleteFlag; }
    public void setDeleteFlag(String deleteFlag) { this.deleteFlag = deleteFlag; }
}
