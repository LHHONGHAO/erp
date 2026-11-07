package com.jsh.erp.print;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.UUID;

/**
 * 打印代理 WebSocket 处理器
 *
 * 连接建立后先下发 auth.ok，之后处理代理的心跳、任务确认、打印结果与状态上报。
 * 超大打印数据由本端主动分帧，代理侧整包接收后再写入打印机。
 */
@Component
public class PrintAgentWebSocketHandler extends TextWebSocketHandler {

    private Logger logger = LoggerFactory.getLogger(PrintAgentWebSocketHandler.class);

    /** 应用层心跳周期（秒） */
    private static final int HEARTBEAT_SEC = 30;

    @Resource
    private PrintAgentStore store;

    @Resource
    private PrintAgentRegistry registry;

    @Resource
    private PrintTaskDispatcher dispatcher;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceId = attribute(session, "deviceId");
        String version = attribute(session, "agentVersion");
        String clientIp = attribute(session, "clientIp");

        PrintAgentSession agentSession = new PrintAgentSession(session, deviceId);
        agentSession.setVersion(version);
        agentSession.setClientIp(clientIp);
        session.getAttributes().put("printAgentSession", agentSession);
        registry.register(agentSession);
        store.updateRuntime(deviceId, version, clientIp, null);

        JSONObject data = new JSONObject();
        data.put("sessionId", session.getId());
        data.put("serverTime", System.currentTimeMillis());
        data.put("heartbeatSec", HEARTBEAT_SEC);
        data.put("maxTaskBytes", PrintTaskDispatcher.MAX_TASK_BYTES);
        agentSession.sendText(envelope("auth.ok", data));
        logger.info("打印代理已连接: device={} version={} ip={}", deviceId, version, clientIp);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        PrintAgentSession agentSession = (PrintAgentSession) session.getAttributes().get("printAgentSession");
        if (agentSession == null) {
            return;
        }
        agentSession.touch();
        JSONObject envelope;
        try {
            envelope = JSONObject.parseObject(message.getPayload());
        } catch (Exception e) {
            logger.warn("打印代理消息不是合法 JSON: device={}", agentSession.getDeviceId());
            return;
        }
        String type = envelope.getString("type");
        if (type == null) {
            return;
        }
        JSONObject data = envelope.getJSONObject("data");
        try {
            switch (type) {
                case "ping":
                    handlePing(agentSession, data);
                    break;
                case "pong":
                    break;
                case "print.ack":
                    dispatcher.onAck(data == null ? null : data.getString("taskId"), data);
                    break;
                case "print.result":
                    dispatcher.onResult(data == null ? null : data.getString("taskId"), data);
                    break;
                case "agent.status":
                    handleStatus(agentSession, data);
                    break;
                case "error":
                    if (data != null) {
                        logger.warn("打印代理报错: device={} code={} message={}",
                                agentSession.getDeviceId(), data.getString("code"), data.getString("message"));
                    }
                    break;
                default:
                    logger.debug("忽略打印代理消息: type={}", type);
                    break;
            }
        } catch (Exception e) {
            logger.error("处理打印代理消息失败: type=" + type, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        PrintAgentSession agentSession = (PrintAgentSession) session.getAttributes().get("printAgentSession");
        if (agentSession == null) {
            return;
        }
        registry.unregister(agentSession.getDeviceId(), agentSession);
        if (!registry.isOnline(agentSession.getDeviceId())) {
            dispatcher.onDisconnect(agentSession.getDeviceId());
        }
        logger.info("打印代理已断开: device={} status={}", agentSession.getDeviceId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.warn("打印代理连接异常: device={} error={}", session.getAttributes().get("deviceId"), exception.getMessage());
    }

    private void handlePing(PrintAgentSession agentSession, JSONObject data) throws Exception {
        JSONObject pong = new JSONObject();
        pong.put("seq", data == null ? null : data.get("seq"));
        agentSession.sendText(envelope("pong", pong));
    }

    private void handleStatus(PrintAgentSession agentSession, JSONObject data) {
        if (data == null) {
            return;
        }
        String version = data.getString("version");
        if (version != null) {
            agentSession.setVersion(version);
        }
        Integer queueDepth = data.getInteger("queueDepth");
        if (queueDepth != null) {
            agentSession.setQueueDepth(queueDepth);
        }
        JSONArray printers = data.getJSONArray("printers");
        String printersText = printers == null ? null : printers.toJSONString();
        agentSession.setPrinters(printersText);
        store.updateRuntime(agentSession.getDeviceId(), version, null, printersText);
    }

    private String attribute(WebSocketSession session, String key) {
        Object value = session.getAttributes().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String envelope(String type, JSONObject data) {
        JSONObject env = new JSONObject();
        env.put("v", 1);
        env.put("type", type);
        env.put("id", UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        env.put("ts", System.currentTimeMillis());
        env.put("data", data);
        return env.toJSONString();
    }
}
