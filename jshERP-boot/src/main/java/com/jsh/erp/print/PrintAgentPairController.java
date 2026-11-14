package com.jsh.erp.print;

import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.utils.BaseResponseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 打印代理配对接口（设备侧，不登录）
 *
 * 主机页生成一次性配对码并展示通信地址；代理设置页填入通信地址与配对码后调用本接口，
 * 主机登记设备、分配设备编号并下发 Token，代理写回本地配置后建立 WebSocket 连接。
 *
 * 该接口在 LogCostFilter 白名单内，鉴权依赖 10 分钟有效的一次性配对码。
 */
@RestController
@RequestMapping(value = "/printAgent/device")
public class PrintAgentPairController {

    private Logger logger = LoggerFactory.getLogger(PrintAgentPairController.class);

    @Resource
    private PrintAgentStore store;

    /**
     * 配对：配对码 + 门店/型号/打印机 + 代理版本，换取设备编号、Token 与服务端地址
     */
    @PostMapping(value = "/pair")
    public BaseResponseInfo pair(@RequestBody JSONObject body, HttpServletRequest request) {
        BaseResponseInfo res = new BaseResponseInfo();
        String pairCode = trim(body == null ? null : body.getString("pairCode"));
        if (pairCode == null) {
            res.code = 400;
            res.data = "配对码不能为空";
            return res;
        }
        String printers = body.getJSONArray("printers") == null
                ? null : body.getJSONArray("printers").toJSONString();
        try {
            JSONObject agent = store.pairDevice(pairCode, body.getString("storeName"),
                    body.getString("printerModel"), printers, body.getString("version"), clientIp(request));
            JSONObject data = new JSONObject(true);
            data.put("deviceId", agent.getString("deviceId"));
            data.put("token", agent.getString("token"));
            data.put("serverUrl", resolveWsUrl(request));
            data.put("printers", agent.get("printers"));
            res.code = 200;
            res.data = data;
            logger.info("打印代理配对成功: {}", agent.getString("deviceId"));
        } catch (IllegalArgumentException e) {
            res.code = 400;
            res.data = e.getMessage();
        } catch (Exception e) {
            logger.error("打印代理配对失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "配对失败：" + e.getMessage();
        }
        return res;
    }

    /**
     * 推导代理应连接的 WebSocket 地址；反向代理后优先取 X-Forwarded-* 头
     */
    private String resolveWsUrl(HttpServletRequest request) {
        String proto = firstHeader(request, "X-Forwarded-Proto");
        String host = firstHeader(request, "X-Forwarded-Host");
        if (host == null) {
            host = request.getServerName();
        }
        if (host == null || host.trim().isEmpty()) {
            host = "127.0.0.1";
        }
        host = host.trim();
        boolean secure = "https".equalsIgnoreCase(proto) || "wss".equalsIgnoreCase(proto)
                || (proto == null && "https".equalsIgnoreCase(request.getScheme()));
        String scheme = secure ? "wss" : "ws";
        boolean hasPort = host.indexOf(':') > -1;
        int port = request.getServerPort();
        StringBuilder url = new StringBuilder(scheme).append("://").append(host);
        if (!hasPort && port > 0 && !((secure && port == 443) || (!secure && port == 80))) {
            url.append(':').append(port);
        }
        return url.append("/ws/print-agent").toString();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = firstHeader(request, "X-Forwarded-For");
        if (forwarded != null) {
            return forwarded;
        }
        return request.getRemoteAddr();
    }

    private String firstHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null) {
            return null;
        }
        int idx = value.indexOf(',');
        value = idx > -1 ? value.substring(0, idx) : value;
        return trim(value);
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
