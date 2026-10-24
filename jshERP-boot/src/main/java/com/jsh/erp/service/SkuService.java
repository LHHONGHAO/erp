package com.jsh.erp.service;

import com.jsh.erp.datasource.entities.Sku;
import com.jsh.erp.datasource.entities.SkuSpec;
import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.SkuMapper;
import com.jsh.erp.datasource.mappers.SkuSpecMapper;
import com.jsh.erp.utils.SkuSpecUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * SKU 保存服务
 *
 * 严格按交接文档执行：
 * 1. 保存/修改 SKU 必须开启事务
 * 2. 先保存/更新 sku_spec（唯一事实来源）
 * 3. 查出该 SKU 最新的所有规格，按 sort_no 排序
 * 4. 代码自动生成 spec_json 与 spec_summary，严禁前端传入
 * 5. 更新 sku 主表
 */
@Service
public class SkuService {

    @Resource
    private SkuMapper skuMapper;

    @Resource
    private SkuSpecMapper skuSpecMapper;

    @Resource
    private UserService userService;

    /**
     * 保存（新增或更新）SKU 及其规格
     *
     * @param sku   主表记录：id 为空表示新增；spuId、skuCode 必填
     * @param specs 规格明细：规格名 + 规格值，顺序即业务录入顺序
     * @return SKU 主键
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public Long saveSku(Sku sku, List<SkuSpec> specs) throws Exception {
        if (sku == null || sku.getSpuId() == null || isBlank(sku.getSkuCode())) {
            throw new IllegalArgumentException("保存SKU失败：spuId 与 skuCode 不能为空");
        }
        Long tenantId = sku.getTenantId();
        if (tenantId == null) {
            User user = userService.getCurrentUser();
            if (user != null) {
                tenantId = user.getTenantId();
            }
        }
        sku.setTenantId(tenantId);

        // 1. 主表：无 id 则先插入占位记录，拿到自增主键
        if (sku.getId() == null) {
            sku.setSkuName("");
            sku.setSpecJson("[]");
            sku.setSpecSummary("");
            skuMapper.insert(sku);
        }

        // 2. 规格落库（唯一事实来源）：覆盖式保存，sort_no 强制重编号，保证拼接顺序固定
        skuSpecMapper.deleteBySkuId(sku.getId());
        List<SkuSpec> toSave = new ArrayList<>();
        if (specs != null) {
            int sortNo = 1;
            for (SkuSpec spec : specs) {
                if (spec == null || isBlank(spec.getSpecName()) || isBlank(spec.getSpecValue())) {
                    continue;
                }
                SkuSpec item = new SkuSpec();
                item.setSkuId(sku.getId());
                item.setSpecName(spec.getSpecName().trim());
                item.setSpecValue(spec.getSpecValue().trim());
                item.setSortNo(sortNo++);
                item.setTenantId(tenantId);
                toSave.add(item);
            }
        }
        if (!toSave.isEmpty()) {
            skuSpecMapper.batchInsert(toSave);
        }

        // 3. 查回该 SKU 最新规格，按 sort_no 排序（以数据库为准）
        List<SkuSpec> latest = skuSpecMapper.selectBySkuId(sku.getId());

        // 4. 代码生成 spec_json / spec_summary / sku_name
        Sku update = new Sku();
        update.setId(sku.getId());
        update.setSkuName(SkuSpecUtils.buildSkuName(latest));
        update.setSpecJson(SkuSpecUtils.buildSpecJson(latest));
        update.setSpecSummary(SkuSpecUtils.buildSpecSummary(latest));
        update.setSalePrice(sku.getSalePrice());

        // 5. 乐观锁更新主表
        Sku current = skuMapper.selectByPrimaryKey(sku.getId());
        if (current == null) {
            throw new IllegalStateException("保存SKU失败：记录不存在或已删除");
        }
        update.setVersion(current.getVersion());
        if (skuMapper.updateSpecFields(update) == 0) {
            throw new IllegalStateException("保存SKU失败：数据已被其它操作修改，请刷新后重试");
        }
        return sku.getId();
    }

    /**
     * 软删除 SKU（禁止物理删除）
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void deleteSku(Long id) {
        if (id == null) {
            return;
        }
        skuMapper.softDelete(id);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
