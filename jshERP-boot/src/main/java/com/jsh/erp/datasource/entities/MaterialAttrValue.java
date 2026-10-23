package com.jsh.erp.datasource.entities;

/**
 * 商品属性值字典
 * 结构化存储颜色/尺码等属性值，value_code 稳定不变，用于 SKU 编码与溯源
 */
public class MaterialAttrValue {
    private Long id;
    private Long attributeId;
    private Long categoryId;
    private String valueText;
    private String valueDetail;
    private String valueCode;
    private Integer sort;
    private String status;
    private Long tenantId;
    private String deleteFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAttributeId() { return attributeId; }
    public void setAttributeId(Long attributeId) { this.attributeId = attributeId; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText == null ? null : valueText.trim(); }

    public String getValueDetail() { return valueDetail; }
    public void setValueDetail(String valueDetail) { this.valueDetail = valueDetail; }

    public String getValueCode() { return valueCode; }
    public void setValueCode(String valueCode) { this.valueCode = valueCode == null ? null : valueCode.trim(); }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status == null ? null : status.trim(); }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getDeleteFlag() { return deleteFlag; }
    public void setDeleteFlag(String deleteFlag) { this.deleteFlag = deleteFlag == null ? null : deleteFlag.trim(); }
}
