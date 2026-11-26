package com.jsh.erp.print;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.service.LabelPrinterService;
import com.jsh.erp.utils.BaseResponseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 打印管理：打印代理设备列表、在线状态与任务记录
 *
 * 页面只负责配置与查看，真正的标签数据由本服务经 WebSocket 下发给主机打印代理。
 */
@RestController
@RequestMapping(value = "/printAgent")
public class PrintAgentController {

    private Logger logger = LoggerFactory.getLogger(PrintAgentController.class);

    @Resource
    private PrintAgentStore store;

    @Resource
    private PrintAgentRegistry registry;

    @Resource
    private PrintTaskDispatcher dispatcher;

    @Resource
    private LabelPrinterService labelPrinterService;

    @Resource
    private PrintPrinterStore printerStore;

    /**
     * 设备列表（含在线状态与实时运行信息）
     */
    @GetMapping(value = "/list")
    public BaseResponseInfo list() {
        BaseResponseInfo res = new BaseResponseInfo();
        JSONArray array = new JSONArray();
        try {
            List<JSONObject> agents = store.listAgents();
            for (JSONObject agent : agents) {
                String deviceId = agent.getString("deviceId");
                PrintAgentSession session = registry.get(deviceId);
                boolean online = session != null && session.isOpen();
                agent.put("online", online);
                if (online) {
                    agent.put("agentVersion", session.getVersion());
                    agent.put("connectedAt", session.getConnectedAt());
                    agent.put("lastSeen", session.getLastSeen());
                    agent.put("queueDepth", session.getQueueDepth());
                    if (session.getPrinters() != null) {
                        agent.put("runtimePrinters", JSONArray.parseArray(session.getPrinters()));
                    }
                }
                array.add(agent);
            }
            res.code = 200;
            res.data = array;
        } catch (Exception e) {
            logger.error("查询打印设备失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = array;
        }
        return res;
    }

    /**
     * 新增或修改设备；新增时 token 为空会自动生成
     */
    @PostMapping(value = "/save")
    public BaseResponseInfo save(@RequestBody JSONObject body) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            Long id = store.saveAgent(body);
            res.code = 200;
            res.data = id;
        } catch (IllegalArgumentException e) {
            res.code = 500;
            res.data = e.getMessage();
        } catch (Exception e) {
            logger.error("保存打印设备失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "保存失败：" + e.getMessage();
        }
        return res;
    }

    /**
     * 主机页新增设备：生成 6 位配对码（10 分钟有效、一次性）
     */
    @PostMapping(value = "/pair/code")
    public BaseResponseInfo createPairCode() {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            res.code = 200;
            res.data = store.createPairCode();
        } catch (Exception e) {
            logger.error("生成配对码失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "生成配对码失败：" + e.getMessage();
        }
        return res;
    }

    /**
     * 主机页轮询配对状态：代理填入配对码后自动进入已接入
     */
    @GetMapping(value = "/pair/status")
    public BaseResponseInfo pairStatus(@RequestParam("code") String code) {
        BaseResponseInfo res = new BaseResponseInfo();
        JSONObject data = store.pairStatus(code);
        if (data == null) {
            res.code = 404;
            res.data = "配对码不正确或已过期";
            return res;
        }
        String deviceId = data.getString("deviceId");
        data.put("online", deviceId != null && registry.isOnline(deviceId));
        res.code = 200;
        res.data = data;
        return res;
    }

    @PostMapping(value = "/delete")
    public BaseResponseInfo delete(@RequestBody JSONObject body) {
        BaseResponseInfo res = new BaseResponseInfo();
        Long id = body.getLong("id");
        if (id == null) {
            res.code = 500;
            res.data = "缺少设备 id";
            return res;
        }
        if (store.deleteAgent(id)) {
            res.code = 200;
            res.data = "ok";
        } else {
            res.code = 500;
            res.data = "删除失败，设备不存在";
        }
        return res;
    }

    /**
     * 打印机型号库（主机维护，供代理选择并下发驱动参数）
     */
    @GetMapping(value = "/printers")
    public BaseResponseInfo printers() {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            res.code = 200;
            res.data = printerStore.listAll();
        } catch (Exception e) {
            logger.error("查询打印机型号失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = new JSONArray();
        }
        return res;
    }

    /**
     * 新增或修改打印机型号参数
     */
    @PostMapping(value = "/printer/save")
    public BaseResponseInfo savePrinter(@RequestBody JSONObject body) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            res.code = 200;
            res.data = printerStore.save(body);
        } catch (IllegalArgumentException e) {
            res.code = 500;
            res.data = e.getMessage();
        } catch (Exception e) {
            logger.error("保存打印机型号失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "保存失败：" + e.getMessage();
        }
        return res;
    }

    @PostMapping(value = "/printer/delete")
    public BaseResponseInfo deletePrinter(@RequestBody JSONObject body) {
        BaseResponseInfo res = new BaseResponseInfo();
        Long id = body.getLong("id");
        if (id == null) {
            res.code = 500;
            res.data = "缺少型号 id";
            return res;
        }
        if (printerStore.delete(id)) {
            res.code = 200;
            res.data = "ok";
        } else {
            res.code = 500;
            res.data = "删除失败，型号不存在";
        }
        return res;
    }

    /**
     * 业务页面调用：按标签内容下发到指定设备/打印机
     */
    @PostMapping(value = "/print")
    public BaseResponseInfo print(@RequestBody JSONObject body) {
        BaseResponseInfo res = new BaseResponseInfo();
        String deviceId = body.getString("deviceId");
        String printerId = body.getString("printerId");
        if (printerId == null || printerId.trim().isEmpty()) {
            printerId = firstPrinterId(deviceId);
        }
        JSONObject printerConfig = findPrinterConfig(deviceId, printerId);
        // 打印机型号由代理上报，主机按型号库里的指令方式生成打印数据
        String model = printerConfig == null ? null : printerConfig.getString("model");
        if (model == null || model.trim().isEmpty()) {
            res.code = 500;
            res.data = "该打印机未配置型号，请先在打印代理设置页配置";
            return res;
        }
        JSONObject driver = printerStore.findByModel(model);
        if (driver == null) {
            res.code = 500;
            res.data = "型号「" + model + "」未在主机型号库中配置，请先在打印代理设置页配置";
            return res;
        }
        int copies = body.getIntValue("copies") > 0 ? body.getIntValue("copies") : 1;
        int width = body.getIntValue("width") > 0 ? body.getIntValue("width") : 320;
        int height = body.getIntValue("height") > 0 ? body.getIntValue("height") : 240;
        int density = body.getIntValue("density");
        int speed = body.getIntValue("speed");
        JSONArray items = body.getJSONArray("items");
        String image = body.getString("image");
        logger.info("收到打印请求 device=" + deviceId + " printer=" + printerId
                + " model=" + model
                + " size=" + width + "x" + height + " copies=" + copies
                + " image=" + (image != null && !image.isEmpty())
                + " items=" + (items == null ? 0 : items.size()));
        try {
            String cmdSet = driver.getString("cmdSet");
            if (cmdSet == null || cmdSet.trim().isEmpty()) {
                cmdSet = "TSPL";
            }
            byte[] data = labelPrinterService.buildLabel(cmdSet, width, height, items, copies, image, density, speed);
            JSONObject task = dispatcher.dispatch(deviceId, printerId, data, 15000);
            res.code = 200;
            res.data = task;
        } catch (Exception e) {
            logger.error("打印下发失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = e.getMessage();
        }
        return res;
    }

    /**
     * 任务记录
     */
    @GetMapping(value = "/tasks")
    public BaseResponseInfo tasks(@RequestParam(value = "deviceId", required = false) String deviceId,
                                  @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            res.code = 200;
            res.data = store.listTasks(deviceId, limit);
        } catch (Exception e) {
            logger.error("查询打印任务失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = new JSONArray();
        }
        return res;
    }

    /**
     * 取设备配置的第一个打印机 id，未配置时用 default
     */
    private String firstPrinterId(String deviceId) {
        if (deviceId == null) {
            return "default";
        }
        JSONObject agent = store.findByDeviceId(deviceId);
        if (agent != null) {
            JSONArray printers = agent.getJSONArray("printers");
            if (printers != null && !printers.isEmpty()) {
                JSONObject first = printers.getJSONObject(0);
                String id = first.getString("id");
                if (id != null && !id.trim().isEmpty()) {
                    return id.trim();
                }
            }
        }
        return "default";
    }

    /**
     * 读取设备的某台打印机配置（型号、指令集等）
     */
    private JSONObject findPrinterConfig(String deviceId, String printerId) {
        if (deviceId == null) {
            return null;
        }
        JSONObject agent = store.findByDeviceId(deviceId);
        if (agent == null) {
            return null;
        }
        JSONArray printers = agent.getJSONArray("printers");
        if (printers == null) {
            return null;
        }
        for (int i = 0; i < printers.size(); i++) {
            JSONObject printer = printers.getJSONObject(i);
            if (printerId != null && printerId.equals(printer.getString("id"))) {
                return printer;
            }
        }
        return null;
    }
}