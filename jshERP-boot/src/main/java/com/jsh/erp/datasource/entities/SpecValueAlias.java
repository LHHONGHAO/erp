package com.jsh.erp.datasource.entities;

import java.util.Date;

/**
 * 规格值别名：同一标准写法的不同原始写法
 */
public class SpecValueAlias {
    private Long id;
    private Long tenantId;
    private Long sampleId;
    private Long categoryId;
    private Long attributeId;
    private String childName;
    private String rawText;
    private String normKey;
    private Integer hitCount;
    private Date createTime;
    private String deleteFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getSampleId() { return sampleId; }
    public void setSampleId(Long sampleId) { this.sampleId = sampleId; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public Long getAttributeId() { return attributeId; }
    public void setAttributeId(Long attributeId) { this.attributeId = attributeId; }

    public String getChildName() { return childName; }
    public void setChildName(String childName) { this.childName = childName == null ? "" : childName.trim(); }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText == null ? null : rawText.trim(); }

    public String getNormKey() { return normKey; }
    public void setNormKey(String normKey) { this.normKey = normKey == null ? null : normKey.trim(); }

    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getDeleteFlag() { return deleteFlag; }
    public void setDeleteFlag(String deleteFlag) { this.deleteFlag = deleteFlag; }
}
