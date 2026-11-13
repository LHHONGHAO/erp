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

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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

    @Resource
    private DataSource dataSource;

    /**
     * 启动时自愈表结构，避免旧库缺列导致「数据写入异常」。
     * 该表由 MyBatis-Plus 租户插件自动追加 tenant_id 条件，缺列时所有增删改查都会报
     * Unknown column 'jsh_material_spec_template.tenant_id'。
     */
    @PostConstruct
    public void initSchema() {
        String createSql = "CREATE TABLE IF NOT EXISTS `jsh_material_spec_template` (" +
                "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键'," +
                "`material_id` bigint NOT NULL COMMENT '商品ID'," +
                "`attribute_id` bigint DEFAULT NULL COMMENT '规格属性ID'," +
                "`sort` int DEFAULT NULL COMMENT '排序'," +
                "`tenant_id` bigint DEFAULT NULL COMMENT '租户ID'," +
                "`delete_flag` varchar(1) DEFAULT '0' COMMENT '删除标记 0正常 1删除'," +
                "PRIMARY KEY (`id`)," +
                "KEY `idx_material` (`material_id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='商品规格模板'";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
        } catch (Exception e) {
            logger.error("初始化商品规格模板表失败: " + e.getMessage(), e);
            return;
        }
        if (!hasColumn("tenant_id")) {
            try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE `jsh_material_spec_template` ADD COLUMN `tenant_id` bigint DEFAULT NULL COMMENT '租户ID'");
                logger.info("jsh_material_spec_template 已补充列: tenant_id");
            } catch (Exception e) {
                logger.error("补充 jsh_material_spec_template.tenant_id 失败: " + e.getMessage(), e);
            }
        }
        // 历史数据没有租户ID，按所属商品回填，否则登录用户查不到自己的规格模板
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            int updated = stmt.executeUpdate("UPDATE jsh_material_spec_template t " +
                    "JOIN jsh_material m ON m.id = t.material_id " +
                    "SET t.tenant_id = m.tenant_id WHERE t.tenant_id IS NULL");
            if (updated > 0) {
                logger.info("jsh_material_spec_template 租户ID回填: " + updated + " 行");
            }
        } catch (Exception e) {
            logger.warn("jsh_material_spec_template 租户ID回填失败(忽略): " + e.getMessage());
        }
    }

    private boolean hasColumn(String column) {
        String sql = "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() " +
                "AND TABLE_NAME = 'jsh_material_spec_template' AND COLUMN_NAME = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            logger.warn("检查 jsh_material_spec_template 列失败: " + column + " - " + e.getMessage());
            return false;
        }
    }

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
