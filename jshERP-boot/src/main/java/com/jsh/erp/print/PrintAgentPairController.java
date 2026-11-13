package com.jsh.erp.print;

import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.utils.BaseResponseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 打印代理自助配对接口（设备侧，不登录）
 *
 * 流程：
 *   1. 设备端启用后本地生成 设备ID + 一次性配对码，展示给用户
 *   2. ERP 打印管理页填写这两个值，调用 /printAgent/pair/prepare 登记
 *   3. 代理带这两个值调用 /printAgent/device/pair 换取 Token 与服务端地址
 *   4. 之后定期调用 /printAgent/device/refresh 续期 Token
 *
 * 这两个接口在 LogCostFilter 的白名单内，鉴权完全依赖一次性配对码与设备 Token。
 */
@RestController
@RequestMapping(value = "/printAgent/device")
public class PrintAgentPairController {

    private Logger logger = LoggerFactory.getLogger(PrintAgentPairController.class);

    /** Token 续期间隔建议值（秒），代理据此决定多久续期一次 */
    private static final int HEARTBEAT_SECONDS = 6 * 60 * 60;

    @Resource
    private PrintAgentStore store;

    @Resource
    private PrintAgentRegistry registry;

    /**
     * 设备凭配对码换取 Token
     */
    @PostMapping(value = "/pair")
    public BaseResponseInfo pair(@RequestBody JSONObject body, HttpServletRequest request) {
        BaseResponseInfo res = new BaseResponseInfo();
        String deviceId = trim(body == null ? null : body.getString("deviceId"));
        String pairCode = trim(body == null ? null : body.getString("pairCode"));
        if (deviceId == null || pairCode == null) {
            res.code = 400;
            res.data = "设备ID和配对码不能为空";
            return res;
        }
        String serverUrl = resolveWsUrl(request);
        JSONObject agent;
        try {
            agent = store.confirmPairing(deviceId, pairCode, serverUrl);
        } catch (IllegalArgumentException e) {
            res.code = 400;
            res.data = e.getMessage();
            return res;
        } catch (Exception e) {
            logger.error("设备配对异常: " + e.getMessage(), e);
            res.code = 500;
            res.data = "配对失败：" + e.getMessage();
            return res;
        }
        // 旧连接用旧 Token，主动断开让代理用新 Token 重连
        closeSession(deviceId);
        res.code = 200;
        res.data = buildConfig(deviceId, agent.getString("token"), agent, request);
        logger.info("打印代理配对成功: {} ({})", deviceId, serverUrl);
        return res;
    }

    /**
     * Token 续期：返回新的 Token 与服务端配置
     */
    @PostMapping(value = "/refresh")
    public BaseResponseInfo refresh(@RequestHeader(value = "X-Device-Id", required = false) String deviceHeader,
                                    @RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestBody(required = false) JSONObject body,
                                    HttpServletRequest request) {
        BaseResponseInfo res = new BaseResponseInfo();
        String deviceId = trim(deviceHeader);
        if (deviceId == null && body != null) {
            deviceId = trim(body.getString("deviceId"));
        }
        String token = bearer(authorization);
        if (token == null && body != null) {
            token = trim(body.getString("token"));
        }
        if (deviceId == null || token == null) {
            res.code = 401;
            res.data = "缺少设备ID或Token";
            return res;
        }
        String check = store.checkToken(deviceId, token);
        if (!"ok".equals(check)) {
            res.code = 401;
            res.data = tokenErrorMessage(check);
            return res;
        }
        String newToken = store.renewToken(deviceId, token);
        if (newToken == null) {
            res.code = 401;
            res.data = "Token 已失效，请重新配对";
            return res;
        }
        JSONObject agent = store.findByDeviceId(deviceId);
        res.code = 200;
        res.data = buildConfig(deviceId, newToken, agent, request);
        return res;
    }

    private JSONObject buildConfig(String deviceId, String token, JSONObject agent, HttpServletRequest request) {
        JSONObject data = new JSONObject(true);
        data.put("deviceId", deviceId);
        data.put("token", token);
        String serverUrl = agent == null ? null : agent.getString("serverUrl");
        data.put("serverUrl", serverUrl == null ? resolveWsUrl(request) : serverUrl);
        data.put("serverName", resolveServerName(request));
        data.put("heartbeatSeconds", HEARTBEAT_SECONDS);
        if (agent != null) {
            data.put("printers", agent.get("printers"));
        }
        return data;
    }

    private void closeSession(String deviceId) {
        PrintAgentSession session = registry.get(deviceId);
        if (session == null) {
            return;
        }
        try {
            if (session.isOpen()) {
                session.getSession().close();
            }
        } catch (Exception e) {
            logger.warn("关闭旧打印代理连接失败: " + e.getMessage());
        }
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

    /**
     * 证书主机名：去掉端口后的主机
     */
    private String resolveServerName(HttpServletRequest request) {
        String host = firstHeader(request, "X-Forwarded-Host");
        if (host == null) {
            host = request.getServerName();
        }
        if (host == null) {
            return "";
        }
        int idx = host.indexOf(':');
        return idx > -1 ? host.substring(0, idx) : host;
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

    private String tokenErrorMessage(String check) {
        if ("not_registered".equals(check)) {
            return "该设备未登记，请先在打印管理中填写设备ID和配对码";
        }
        if ("revoked".equals(check)) {
            return "该设备已被吊销";
        }
        if ("disabled".equals(check)) {
            return "该设备已停用";
        }
        if ("pending".equals(check)) {
            return "该设备尚未完成配对";
        }
        if ("expired".equals(check)) {
            return "Token 已过期，请重新配对";
        }
        if ("wrong_token".equals(check)) {
            return "Token 不正确";
        }
        return "设备校验失败，请重新配对";
    }

    private String bearer(String authorization) {
        if (authorization == null) {
            return null;
        }
        String value = authorization.trim();
        if (value.toLowerCase().startsWith("bearer ")) {
            return trim(value.substring("bearer ".length()));
        }
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
