package com.jsh.erp.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.base.BaseController;
import com.jsh.erp.service.LabelPrinterService;
import com.jsh.erp.utils.ErpInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

import static com.jsh.erp.utils.ResponseJsonUtil.returnJson;

/**
 * 标签打印：打印机列表与指定打印机打印
 */
@RestController
@RequestMapping(value = "/print")
@Api(tags = {"打印管理"})
public class PrintController extends BaseController {
    private Logger logger = LoggerFactory.getLogger(PrintController.class);

    @Resource
    private LabelPrinterService labelPrinterService;

    @GetMapping(value = "/printers")
    @ApiOperation(value = "获取本机打印机列表")
    public String getPrinters() {
        Map<String, Object> objectMap = new HashMap<>();
        JSONArray printers = labelPrinterService.listPrinters();
        objectMap.put("printers", printers);
        objectMap.put("count", printers.size());
        return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
    }

    @PostMapping(value = "/label")
    @ApiOperation(value = "按指定打印机打印标签")
    public String printLabel(@RequestBody JSONObject body) {
        Map<String, Object> objectMap = new HashMap<>();
        String printerName = body.getString("printerName");
        if (printerName == null || printerName.trim().isEmpty()) {
            return returnJson(objectMap, "请先选择打印机", ErpInfo.ERROR.code);
        }
        int copies = body.getIntValue("copies") > 0 ? body.getIntValue("copies") : 1;
        int width = body.getIntValue("width") > 0 ? body.getIntValue("width") : 320;
        int height = body.getIntValue("height") > 0 ? body.getIntValue("height") : 240;
        JSONArray items = body.getJSONArray("items");
        try {
            labelPrinterService.printLabel(printerName, copies, width, height, items);
            return returnJson(objectMap, "打印成功", ErpInfo.OK.code);
        } catch (Exception e) {
            logger.error("标签打印失败", e);
            return returnJson(objectMap, "打印失败：" + e.getMessage(), ErpInfo.ERROR.code);
        }
    }
}
