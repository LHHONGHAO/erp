package com.jsh.erp.print;

import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * 打印代理握手鉴权
 *
 * 代理不在 ERP 登录体系内，改用设备 Token 认证：
 *   Authorization: Bearer <token>
 *   X-Device-Id: <deviceId>
 * 账号不存在、被停用或 Token 不正确时直接返回 401/403，代理会退避重连。
 */
@Component
public class PrintAgentHandshakeInterceptor implements HandshakeInterceptor {

    private Logger logger = LoggerFactory.getLogger(PrintAgentHandshakeInterceptor.class);

    @Resource
    private PrintAgentStore store;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String deviceId = trim(request.getHeaders().getFirst("X-Device-Id"));
        String authorization = trim(request.getHeaders().getFirst("Authorization"));
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? trim(authorization.substring("Bearer ".length())) : null;
        if (deviceId == null || token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        JSONObject agent = store.findByDeviceId(deviceId);
        if (agent == null) {
            logger.warn("打印代理鉴权失败(未登记): {}", deviceId);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        if (!"1".equals(agent.getString("enabled"))) {
            logger.warn("打印代理鉴权失败(已停用): {}", deviceId);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        String expected = agent.getString("token");
        if (expected == null || !expected.equals(token)) {
            logger.warn("打印代理鉴权失败(Token 不正确): {}", deviceId);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put("deviceId", deviceId);
        String version = trim(request.getHeaders().getFirst("X-Agent-Version"));
        attributes.put("agentVersion", version == null ? "" : version);
        attributes.put("clientIp", clientIp(request.getRemoteAddress()));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    private String clientIp(InetSocketAddress address) {
        if (address == null || address.getAddress() == null) {
            return "";
        }
        return address.getAddress().getHostAddress();
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
