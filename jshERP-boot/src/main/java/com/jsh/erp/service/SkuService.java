package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.datasource.entities.Sku;
import com.jsh.erp.datasource.entities.SkuSpec;
import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.MaterialExtendMapperEx;
import com.jsh.erp.datasource.mappers.SkuMapper;
import com.jsh.erp.datasource.mappers.SkuSpecMapper;
import com.jsh.erp.datasource.vo.MaterialExtendVo4List;
import com.jsh.erp.utils.SkuSpecUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private Logger logger = LoggerFactory.getLogger(SkuService.class);

    @Resource
    private SkuMapper skuMapper;

    @Resource
    private SkuSpecMapper skuSpecMapper;

    @Resource
    private MaterialExtendMapperEx materialExtendMapperEx;

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
        //    规格图片只允许一个维度携带（同一 SKU 最多一行有图），其余维度一律置空
        skuSpecMapper.deleteBySkuId(sku.getId());
        List<SkuSpec> toSave = new ArrayList<>();
        boolean imageAssigned = false;
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
                if (!imageAssigned && !isBlank(spec.getImageUrl())) {
                    item.setImageUrl(spec.getImageUrl().trim());
                    imageAssigned = true;
                }
                item.setSortNo(sortNo++);
                item.setTenantId(tenantId);
                toSave.add(item);
            }
        }
        if (!toSave.isEmpty()) {
            for (SkuSpec spec : toSave) {
                skuSpecMapper.insert(spec);
            }
        }

        // 3. 查回该 SKU 最新规格，按 sort_no 排序（以数据库为准）
        List<SkuSpec> latest = skuSpecMapper.selectBySkuId(sku.getId());

        // 4. 代码生成 spec_json / spec_summary / sku_name
        Sku update = new Sku();
        update.setId(sku.getId());
        update.setSkuName(SkuSpecUtils.buildSkuName(latest));
        update.setSpecJson(SkuSpecUtils.buildSpecJson(latest));
        update.setSpecSummary(SkuSpecUtils.buildSpecSummary(latest));
        update.setPurchaseDecimal(sku.getPurchaseDecimal());
        update.setCommodityDecimal(sku.getCommodityDecimal());
        update.setMarketPrice(sku.getMarketPrice());
        update.setUpdateSerial(sku.getUpdateSerial());

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

    /**
     * 商品保存后，把 SKU 明细（jsh_material_extend）同步到 sku / sku_spec 新模型。
     * - 展示名 / 规格JSON / 规格摘要 由扩展行的 spec_values 按规格录入顺序生成；
     * - 首次插入时 create_time 取保存时的系统时间（数据库默认值），
     *   后续每次编辑保存都会更新 sku 记录，update_time 由数据库自动刷新为本次保存时间；
     * - 已不存在的 SKU 执行软删除，避免列表出现脏数据。
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void syncFromMaterialExtend(Long spuId) throws Exception {
        if (spuId == null) {
            return;
        }
        List<MaterialExtendVo4List> extendList = materialExtendMapperEx.getDetailList(spuId);
        Long tenantId = getCurrentTenantId();
        Map<String, Sku> existMap = new HashMap<>();
        List<Sku> existing = skuMapper.selectBySpuId(spuId, tenantId);
        if (existing != null) {
            for (Sku sku : existing) {
                if (!isBlank(sku.getSkuCode())) {
                    existMap.put(sku.getSkuCode(), sku);
                }
            }
        }
        Set<String> keepCodes = new HashSet<>();
        if (extendList != null) {
            for (MaterialExtendVo4List extend : extendList) {
                String skuCode = extend.getSkuCode();
                if (isBlank(skuCode)) {
                    continue;
                }
                keepCodes.add(skuCode);
                Sku payload = new Sku();
                Sku exist = existMap.get(skuCode);
                if (exist != null) {
                    payload.setId(exist.getId());
                }
                payload.setSpuId(spuId);
                payload.setSkuCode(skuCode);
                payload.setTenantId(tenantId);
                payload.setPurchaseDecimal(extend.getPurchaseDecimal());
                payload.setCommodityDecimal(extend.getCommodityDecimal());
                saveSku(payload, buildSpecsFromSpecValues(extend.getSpecValues()));
            }
        }
        for (Sku sku : existMap.values()) {
            if (!keepCodes.contains(sku.getSkuCode())) {
                skuMapper.softDelete(sku.getId());
            }
        }
    }

    /**
     * 从扩展行 spec_values 还原规格明细，顺序与录入顺序一致（saveSku 会重排 sort_no）
     * spec_values 元素格式：{attributeName, valueText, image, ...}
     */
    private List<SkuSpec> buildSpecsFromSpecValues(String specValues) {
        List<SkuSpec> specs = new ArrayList<>();
        if (isBlank(specValues)) {
            return specs;
        }
        try {
            JSONArray array = JSONArray.parseArray(specValues);
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getJSONObject(i);
                if (item == null || isBlank(item.getString("valueText"))) {
                    continue;
                }
                String specName = item.getString("attributeName");
                SkuSpec spec = new SkuSpec();
                spec.setSpecName(isBlank(specName) ? ("规格" + (i + 1)) : specName);
                spec.setSpecValue(item.getString("valueText"));
                spec.setImageUrl(item.getString("image"));
                specs.add(spec);
            }
        } catch (Exception e) {
            logger.error("解析商品规格值失败 specValues={}", specValues, e);
        }
        return specs;
    }

    /** 当前登录用户租户ID */
    private Long getCurrentTenantId() throws Exception {
        User user = userService.getCurrentUser();
        return user != null ? user.getTenantId() : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
