package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.datasource.entities.MaterialSpecTemplate;
import com.jsh.erp.datasource.mappers.MaterialSpecTemplateMapper;
import com.jsh.erp.exception.JshException;
import com.jsh.erp.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 商品规格模板 Service
 * 管理商品与规格属性的关联关系，支持动态数量的规格属性
 * 例如：被套关联[颜色,尺寸]，四件套关联[颜色,被套尺寸,床单尺寸,枕套尺寸]
 */
@Service
public class MaterialSpecTemplateService {
    private Logger logger = LoggerFactory.getLogger(MaterialSpecTemplateService.class);

    @Resource
    private MaterialSpecTemplateMapper materialSpecTemplateMapper;

    /**
     * 根据商品ID获取规格模板列表
     */
    public List<MaterialSpecTemplate> getListByMaterialId(Long materialId) {
        try {
            return materialSpecTemplateMapper.selectByMaterialId(materialId);
        } catch (Exception e) {
            JshException.readFail(logger, e);
            return null;
        }
    }

    /**
     * 保存商品规格模板（先删后插）
     * 从前端传入的 specModules 数组中提取规格属性ID，写入规格模板表
     *
     * @param materialId 商品ID
     * @param specModules 前端传入的规格模块数组
     *                    格式：[{"name":"颜色","attributeId":1,"values":[...]},...]
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void saveSpecTemplate(Long materialId, JSONArray specModules) {
        if (materialId == null) {
            return;
        }
        // 先删除原有模板
        materialSpecTemplateMapper.deleteByMaterialId(materialId);
        // 再插入新模板
        if (specModules != null && !specModules.isEmpty()) {
            for (int i = 0; i < specModules.size(); i++) {
                JSONObject module = specModules.getJSONObject(i);
                MaterialSpecTemplate template = new MaterialSpecTemplate();
                template.setMaterialId(materialId);
                // 优先使用前端传入的 attributeId，其次通过属性名查找
                Long attributeId = module.getLong("attributeId");
                template.setAttributeId(attributeId);
                template.setSort(i + 1);
                template.setDeleteFlag("0");
                materialSpecTemplateMapper.insertSelective(template);
            }
        }
    }

    /**
     * 根据商品ID删除规格模板
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void deleteByMaterialId(Long materialId) {
        if (materialId != null) {
            materialSpecTemplateMapper.deleteByMaterialId(materialId);
        }
    }
}
