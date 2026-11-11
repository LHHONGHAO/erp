package com.jsh.erp.datasource.entities;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 规格值样本：标准写法 + 归一化键
 */
public class SpecValueSample {
    private Long id;
    private Long tenantId;
    private Long categoryId;
    private Long attributeId;
    private String canonicalValue;
    private String normKey;
    private Long attrValueId;
    private String source;
    private BigDecimal confidence;
    private Integer hitCount;
    private String status;
    private Date createTime;
    private Date updateTime;
    private String deleteFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public Long getAttributeId() { return attributeId; }
    public void setAttributeId(Long attributeId) { this.attributeId = attributeId; }

    public String getCanonicalValue() { return canonicalValue; }
    public void setCanonicalValue(String canonicalValue) {
        this.canonicalValue = canonicalValue == null ? null : canonicalValue.trim();
    }

    public String getNormKey() { return normKey; }
    public void setNormKey(String normKey) { this.normKey = normKey == null ? null : normKey.trim(); }

    public Long getAttrValueId() { return attrValueId; }
    public void setAttrValueId(Long attrValueId) { this.attrValueId = attrValueId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public String getDeleteFlag() { return deleteFlag; }
    public void setDeleteFlag(String deleteFlag) { this.deleteFlag = deleteFlag; }
}
