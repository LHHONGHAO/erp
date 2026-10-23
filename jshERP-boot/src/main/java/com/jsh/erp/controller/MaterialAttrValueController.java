package com.jsh.erp.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.base.BaseController;
import com.jsh.erp.base.TableDataInfo;
import com.jsh.erp.datasource.entities.MaterialAttrValue;
import com.jsh.erp.service.MaterialAttrValueService;
import com.jsh.erp.utils.ErpInfo;
import com.jsh.erp.utils.StringUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jsh.erp.utils.ResponseJsonUtil.returnJson;
import static com.jsh.erp.utils.ResponseJsonUtil.returnStr;

/**
 * 商品属性值字典 Controller
 * 管理颜色/尺码等属性值及其稳定编码 value_code，用于 SKU 编码与溯源
 */
@RestController
@RequestMapping(value = "/materialAttrValue")
@Api(tags = {"商品属性值字典"})
public class MaterialAttrValueController extends BaseController {
    private Logger logger = LoggerFactory.getLogger(MaterialAttrValueController.class);

    @Resource
    private MaterialAttrValueService materialAttrValueService;

    @GetMapping(value = "/list")
    @ApiOperation(value = "获取全部启用的属性值列表")
    public TableDataInfo getList(HttpServletRequest request) throws Exception {
        List<MaterialAttrValue> list = materialAttrValueService.getAllEnabled();
        return getDataTable(list);
    }

    @GetMapping(value = "/getByAttributeId")
    @ApiOperation(value = "按属性名id查属性值列表")
    public JSONArray getByAttributeId(@RequestParam("id") Long id, HttpServletRequest request) throws Exception {
        JSONArray dataArray = new JSONArray();
        try {
            List<MaterialAttrValue> list = materialAttrValueService.getByAttributeId(id);
            if (list != null) {
                for (MaterialAttrValue v : list) {
                    JSONObject item = new JSONObject();
                    item.put("id", v.getId());
                    item.put("attributeId", v.getAttributeId());
                    item.put("valueText", v.getValueText());
                    item.put("valueCode", v.getValueCode());
                    dataArray.add(item);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return dataArray;
    }

    @GetMapping(value = "/getByCategoryAndAttribute")
    @ApiOperation(value = "按品类和规格名获取已保存的规格值")
    public JSONArray getByCategoryAndAttribute(@RequestParam("categoryId") Long categoryId,
                                               @RequestParam("attributeId") Long attributeId,
                                               HttpServletRequest request) throws Exception {
        JSONArray dataArray = new JSONArray();
        List<MaterialAttrValue> list = materialAttrValueService.getByAttributeIdAndCategoryId(attributeId, categoryId);
        if (list != null) {
            for (MaterialAttrValue value : list) {
                JSONObject item = new JSONObject();
                item.put("id", value.getId());
                item.put("categoryId", value.getCategoryId());
                item.put("attributeId", value.getAttributeId());
                item.put("valueText", value.getValueText());
                item.put("valueDetail", value.getValueDetail());
                item.put("valueCode", value.getValueCode());
                dataArray.add(item);
            }
        }
        return dataArray;
    }

    @DeleteMapping(value = "/hideSuggestion")
    @ApiOperation(value = "仅从规格值建议卡片中移除规格值")
    public String hideSuggestion(@RequestParam("id") Long id, HttpServletRequest request) throws Exception {
        Map<String, Object> objectMap = new HashMap<>();
        int updated = materialAttrValueService.hideSuggestionCard(id, request);
        return returnStr(objectMap, updated);
    }

    @GetMapping(value = "/getByValueCode")
    @ApiOperation(value = "按 value_code 反查属性值（SKU溯源用）")
    public String getByValueCode(@RequestParam("code") String code, HttpServletRequest request) throws Exception {
        Map<String, Object> objectMap = new HashMap<>();
        MaterialAttrValue v = materialAttrValueService.getByValueCode(code);
        if (v != null) {
            objectMap.put("info", v);
            return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
        } else {
            return returnJson(objectMap, ErpInfo.ERROR.name, ErpInfo.ERROR.code);
        }
    }

    @PostMapping(value = "/add")
    @ApiOperation(value = "新增属性值")
    public String addResource(@RequestBody JSONObject obj, HttpServletRequest request) throws Exception {
        Map<String, Object> objectMap = new HashMap<>();
        int insert = materialAttrValueService.insertMaterialAttrValue(obj, request);
        return returnStr(objectMap, insert);
    }

    @PutMapping(value = "/update")
    @ApiOperation(value = "修改属性值")
    public String updateResource(@RequestBody JSONObject obj, HttpServletRequest request) throws Exception {
        Map<String, Object> objectMap = new HashMap<>();
        int update = materialAttrValueService.updateMaterialAttrValue(obj, request);
        return returnStr(objectMap, update);
    }

    @DeleteMapping(value = "/delete")
    @ApiOperation(value = "逻辑删除属性值")
    public String deleteResource(@RequestParam("id") Long id, HttpServletRequest request) throws Exception {
        Map<String, Object> objectMap = new HashMap<>();
        int delete = materialAttrValueService.deleteMaterialAttrValue(id, request);
        return returnStr(objectMap, delete);
    }

    @GetMapping(value = "/checkIsCodeExist")
    @ApiOperation(value = "检查编码是否存在")
    public String checkIsCodeExist(@RequestParam Long id, @RequestParam(value = "code", required = false) String code,
                                   HttpServletRequest request) throws Exception {
        Map<String, Object> objectMap = new HashMap<>();
        int exist = materialAttrValueService.countByValueCode(code, id);
        objectMap.put("status", exist > 0);
        return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
    }
}
