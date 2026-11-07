package com.jsh.erp.print;

import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 打印任务下发
 *
 * 协议约束：同一条连接同一时刻只允许一个任务在传输，因此按设备串行化；
 * 文本帧 print.task 之后紧跟 1..N 个二进制帧（原始 TSPL），最后等待代理回结果。
 */
@Component
public class PrintTaskDispatcher {

    private Logger logger = LoggerFactory.getLogger(PrintTaskDispatcher.class);

    /** 单任务上限，与代理默认 maxTaskBytes 保持一致 */
    public static final int MAX_TASK_BYTES = 4 * 1024 * 1024;
    /** 单帧大小，协议建议不超过 256KB */
    private static final int CHUNK_BYTES = 200 * 1024;

    @Resource
    private PrintAgentStore store;

    @Resource
    private PrintAgentRegistry registry;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "print-task-timeout");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 下发一次打印
     *
     * @param deviceId  目标设备
     * @param printerId 目标打印机（代理配置里的 id）
     * @param data      原始 TSPL 数据
     * @param timeoutMs 建议超时时间
     * @return 任务信息（taskId/totalBytes/sha256）
     */
    public JSONObject dispatch(String deviceId, String printerId, byte[] data, int timeoutMs) throws Exception {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("请选择打印设备");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("打印数据为空");
        }
        if (data.length > MAX_TASK_BYTES) {
            throw new IllegalArgumentException("打印数据超过 4MB，无法下发");
        }
        PrintAgentSession agentSession = registry.get(deviceId);
        if (agentSession == null || !agentSession.isOpen()) {
            throw new IllegalStateException("设备不在线，请检查打印代理是否已连接");
        }

        String taskId = newTaskId();
        String sha256 = sha256(data);
        store.createTask(taskId, deviceId, printerId, data.length, sha256);

        JSONObject payload = new JSONObject();
        payload.put("taskId", taskId);
        payload.put("printerId", printerId);
        payload.put("totalBytes", data.length);
        payload.put("sha256", sha256);
        payload.put("contentType", "raw-tspl");
        payload.put("timeoutMs", timeoutMs);

        try {
            synchronized (agentSession) {
                agentSession.sendText(envelope("print.task", payload));
                int offset = 0;
                while (offset < data.length) {
                    int length = Math.min(CHUNK_BYTES, data.length - offset);
                    byte[] chunk = new byte[length];
                    System.arraycopy(data, offset, chunk, 0, length);
                    agentSession.sendBinary(chunk);
                    offset += length;
                }
            }
            store.updateTaskStatus(taskId, "sent", null, null);
            scheduleTimeout(taskId, timeoutMs);
            logger.info("打印任务已下发: task={} device={} printer={} bytes={}",
                    taskId, deviceId, printerId, data.length);
        } catch (Exception e) {
            store.updateTaskStatus(taskId, "failed", "SEND_FAILED", e.getMessage());
            throw e;
        }

        JSONObject result = new JSONObject();
        result.put("taskId", taskId);
        result.put("totalBytes", data.length);
        result.put("sha256", sha256);
        return result;
    }

    /**
     * 代理收到任务公告后的确认
     */
    public void onAck(String taskId, JSONObject data) {
        if (taskId == null || data == null) {
            return;
        }
        String status = data.getString("status");
        if ("accepted".equals(status)) {
            store.updateTaskStatus(taskId, "accepted", null, null);
        } else {
            store.updateTaskStatus(taskId, "rejected", data.getString("code"), data.getString("message"));
        }
    }

    /**
     * 代理打印结束后的结果上报（可能因断线补报而重复，存储层按终态去重）
     */
    public void onResult(String taskId, JSONObject data) {
        if (taskId == null || data == null) {
            return;
        }
        String status = data.getString("status");
        if ("success".equals(status)) {
            store.updateTaskStatus(taskId, "success", null, null);
        } else if ("cancelled".equals(status)) {
            store.updateTaskStatus(taskId, "cancelled", data.getString("errorCode"), data.getString("errorMessage"));
        } else {
            String errorCode = data.getString("errorCode");
            store.updateTaskStatus(taskId, "failed",
                    errorCode == null || errorCode.isEmpty() ? "PRINT_FAILED" : errorCode,
                    data.getString("errorMessage"));
        }
    }

    /**
     * 连接断开：该设备未完成的任务不会再收到结果，统一置为离线失败
     */
    public void onDisconnect(String deviceId) {
        if (deviceId == null) {
            return;
        }
        store.failUnfinishedByDevice(deviceId, "offline", "代理连接断开，任务未完成");
    }

    private void scheduleTimeout(final String taskId, int timeoutMs) {
        long delay = Math.max(timeoutMs, 5000) + 15000L;
        scheduler.schedule(() -> {
            if (store.isUnfinished(taskId)) {
                store.updateTaskStatus(taskId, "timeout", "TIMEOUT", "等待代理打印结果超时");
                logger.warn("打印任务等待结果超时: {}", taskId);
            }
        }, delay, TimeUnit.MILLISECONDS);
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

    private String newTaskId() {
        String time = new SimpleDateFormat("yyyyMMdd-HHmmssSSS").format(new Date());
        String random = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0x10000)).toUpperCase();
        return time + "-" + random;
    }

    private String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(data);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b & 0xFF));
        }
        return builder.toString();
    }
}
