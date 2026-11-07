package com.jsh.erp.print;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线打印代理会话注册表
 *
 * 以 deviceId 为键，只保留当前活跃的一条连接；同一设备重复连接时新连接顶替旧连接。
 */
@Component
public class PrintAgentRegistry {

    private final Map<String, PrintAgentSession> sessions = new ConcurrentHashMap<>();

    public void register(PrintAgentSession agentSession) {
        PrintAgentSession old = sessions.put(agentSession.getDeviceId(), agentSession);
        if (old != null && old != agentSession) {
            // 旧连接由新连接顶替，主动关闭避免两个客户端同时收到任务
            try {
                old.getSession().close();
            } catch (Exception ignored) {
                // 关闭失败不影响新连接
            }
        }
    }

    /**
     * 仅当注册表里仍是这条连接时才移除，避免旧连接关闭事件误删新连接
     */
    public void unregister(String deviceId, PrintAgentSession agentSession) {
        sessions.remove(deviceId, agentSession);
    }

    public PrintAgentSession get(String deviceId) {
        return deviceId == null ? null : sessions.get(deviceId);
    }

    public boolean isOnline(String deviceId) {
        PrintAgentSession agentSession = get(deviceId);
        return agentSession != null && agentSession.isOpen();
    }
}
