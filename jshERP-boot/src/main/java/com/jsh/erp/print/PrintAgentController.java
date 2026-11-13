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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 打印管理：打印代理设备配置、在线状态、测试打印与任务记录
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
     * 重置设备 Token，返回新值，页面提示用户同步修改代理配置
     */
    @PostMapping(value = "/token")
    public BaseResponseInfo resetToken(@RequestBody JSONObject body) {
        BaseResponseInfo res = new BaseResponseInfo();
        Long id = body.getLong("id");
        if (id == null) {
            res.code = 500;
            res.data = "缺少设备 id";
            return res;
        }
        try {
            String token = store.resetToken(id);
            res.code = 200;
            res.data = token;
        } catch (Exception e) {
            logger.error("重置设备 Token 失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "重置失败";
        }
        return res;
    }

    /**
     * 配对预登记：页面填写设备端生成的「设备ID + 配对码」后调用。
     * 设备随后凭这两个值调用 /printAgent/device/pair 换取 Token。
     */
    @PostMapping(value = "/pair/prepare")
    public BaseResponseInfo preparePairing(@RequestBody JSONObject body) {
        BaseResponseInfo res = new BaseResponseInfo();
        String deviceId = trimText(body.getString("deviceId"));
        String pairCode = trimText(body.getString("pairCode"));
        if (deviceId == null || pairCode == null) {
            res.code = 400;
            res.data = "设备ID和配对码不能为空";
            return res;
        }
        try {
            JSONObject data = store.preparePairing(deviceId, pairCode, 10);
            // 旧 Token 已清空，断开在线连接让代理重新配对
            PrintAgentSession session = registry.get(deviceId);
            if (session != null && session.isOpen()) {
                try {
                    session.getSession().close();
                } catch (Exception ignored) {
                    // 关闭失败不影响配对
                }
            }
            res.code = 200;
            res.data = data;
        } catch (Exception e) {
            logger.error("打印设备配对预登记失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "登记失败：" + e.getMessage();
        }
        return res;
    }

    /**
     * 查询配对状态，页面据此轮询「待配对 / 已配对」
     */
    @GetMapping(value = "/pair/status")
    public BaseResponseInfo pairingStatus(@RequestParam(value = "deviceId") String deviceId) {
        BaseResponseInfo res = new BaseResponseInfo();
        JSONObject agent = store.findByDeviceId(deviceId);
        if (agent == null) {
            res.code = 404;
            res.data = "设备不存在";
            return res;
        }
        PrintAgentSession session = registry.get(deviceId);
        JSONObject data = new JSONObject(true);
        data.put("deviceId", deviceId);
        data.put("paired", agent.getString("paired"));
        data.put("revoked", agent.getString("revoked"));
        data.put("pairExpireAt", agent.getString("pairExpireAt"));
        data.put("online", session != null && session.isOpen());
        res.code = 200;
        res.data = data;
        return res;
    }

    /**
     * 吊销设备：清空凭据并断开连接，代理无法再接入
     */
    @PostMapping(value = "/revoke")
    public BaseResponseInfo revoke(@RequestBody JSONObject body) {
        BaseResponseInfo res = new BaseResponseInfo();
        String deviceId = trimText(body.getString("deviceId"));
        if (deviceId == null) {
            res.code = 400;
            res.data = "缺少设备ID";
            return res;
        }
        boolean ok = store.revokeAgent(deviceId);
        PrintAgentSession session = registry.get(deviceId);
        if (session != null && session.isOpen()) {
            try {
                session.getSession().close();
            } catch (Exception ignored) {
                // 关闭失败不影响吊销
            }
        }
        res.code = ok ? 200 : 500;
        res.data = ok ? "ok" : "吊销失败，设备不存在";
        return res;
    }

    private String trimText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 测试打印：下发一张内置测试标签，用于确认代理、打印机与纸张通路是否正常
     */
    @PostMapping(value = "/test")
    public BaseResponseInfo testPrint(@RequestBody JSONObject body) {
        BaseResponseInfo res = new BaseResponseInfo();
        String deviceId = body.getString("deviceId");
        String printerId = body.getString("printerId");
        if (printerId == null || printerId.trim().isEmpty()) {
            printerId = firstPrinterId(deviceId);
        }
        int copies = body.getIntValue("copies") > 0 ? body.getIntValue("copies") : 1;
        int width = body.getIntValue("width") > 0 ? body.getIntValue("width") : 320;
        int height = body.getIntValue("height") > 0 ? body.getIntValue("height") : 240;
        try {
            byte[] data = labelPrinterService.buildTspl(width, height, buildTestItems(), copies, null);
            JSONObject task = dispatcher.dispatch(deviceId, printerId, data, 15000);
            res.code = 200;
            res.data = task;
        } catch (Exception e) {
            logger.error("测试打印失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = e.getMessage();
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
        int copies = body.getIntValue("copies") > 0 ? body.getIntValue("copies") : 1;
        int width = body.getIntValue("width") > 0 ? body.getIntValue("width") : 320;
        int height = body.getIntValue("height") > 0 ? body.getIntValue("height") : 240;
        JSONArray items = body.getJSONArray("items");
        String image = body.getString("image");
        try {
            String cmdSet = resolvePrinterCmdSet(deviceId, printerId);
            byte[] data = labelPrinterService.buildLabel(cmdSet, width, height, items, copies, image);
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
     * 读取打印机配置的指令集（TSPL / CPCL / DDPL / ESC-POS），未配置时默认 TSPL
     */
    private String resolvePrinterCmdSet(String deviceId, String printerId) {
        if (deviceId == null) {
            return "TSPL";
        }
        JSONObject agent = store.findByDeviceId(deviceId);
        if (agent == null) {
            return "TSPL";
        }
        JSONArray printers = agent.getJSONArray("printers");
        if (printers == null) {
            return "TSPL";
        }
        for (int i = 0; i < printers.size(); i++) {
            JSONObject printer = printers.getJSONObject(i);
            if (printerId != null && printerId.equals(printer.getString("id"))) {
                String cmdSet = printer.getString("cmdSet");
                return cmdSet == null || cmdSet.trim().isEmpty() ? "TSPL" : cmdSet.trim();
            }
        }
        return "TSPL";
    }

    /**
     * 内置测试标签：标题、说明、下发时间
     */
    private JSONArray buildTestItems() {
        JSONArray items = new JSONArray();
        items.add(textItem(20, 20, "打印测试", 28));
        items.add(textItem(20, 80, "ERP 打印代理已连通", 18));
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        items.add(textItem(20, 120, time, 16));
        items.add(textItem(20, 170, "printer ok", 14));
        return items;
    }

    private JSONObject textItem(int x, int y, String text, int fontSize) {
        JSONObject item = new JSONObject();
        item.put("x", x);
        item.put("y", y);
        item.put("text", text);
        item.put("fontSize", fontSize);
        return item;
    }
}
