package com.wut.screenmsgtx.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wut.screencommontx.Model.TransmitDataModel;
import com.wut.screenmsgtx.Context.MsgTaskControlContext;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.wut.screencommontx.Static.MsgModuleStatic.TOPIC_NAME_FIBER;
import static com.wut.screencommontx.Static.MsgModuleStatic.TOPIC_NAME_TIMESTAMP;

@Component
public class UdpRealtimeDataService {
    private static final Logger log = LoggerFactory.getLogger(UdpRealtimeDataService.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MsgTaskControlContext msgTaskControlContext;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong lastTimestamp = new AtomicLong(Long.MIN_VALUE);
    private final ScheduledExecutorService timestampScheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<Long, ScheduledFuture<?>> timestampTaskMap = new ConcurrentHashMap<>();

    @Value("${msg.udp.timestamp-on-change:true}")
    private boolean timestampOnChange;

    @Value("${msg.udp.timestamp-debounce-ms:150}")
    private long timestampDebounceMs;

    public UdpRealtimeDataService(KafkaTemplate<String, String> kafkaTemplate, MsgTaskControlContext msgTaskControlContext) {
        this.kafkaTemplate = kafkaTemplate;
        this.msgTaskControlContext = msgTaskControlContext;
    }

    public void handleUdpPayload(byte[] payload) {
        if (!msgTaskControlContext.isActive()) {
            return;
        }
        String text = new String(payload, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return;
        }

        if (maybeCsv(text)) {
            parseAndSendCsv(text);
            return;
        }
        parseAndSendJson(text);
    }

    private boolean maybeCsv(String text) {
        return text.indexOf(';') >= 0 && !(text.startsWith("{") || text.startsWith("["));
    }

    private void parseAndSendJson(String text) {
        try {
            JsonNode root = objectMapper.readTree(text);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    normalizeJsonAndSend(node);
                }
                return;
            }
            normalizeJsonAndSend(root);
        } catch (Exception e) {
            log.warn("Ignore unsupported UDP payload, raw={}", text, e);
        }
    }

    private void normalizeJsonAndSend(JsonNode rootNode) {
        if (rootNode == null || rootNode.isNull() || rootNode.isMissingNode()) {
            return;
        }
        JsonNode dataNode = rootNode.has("data") ? rootNode.path("data") : rootNode;
        long timestamp = parseTimestamp(
                rootNode.path("timestamp"),
                parseTimestamp(dataNode.path("timestamp"), System.currentTimeMillis())
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", parseInt(firstNonNull(dataNode.path("id"), dataNode.path("vehicleId")), 0));
        data.put("type", parseInt(firstNonNull(dataNode.path("type"), dataNode.path("vehicleType")), 0));
        data.put("model", parseInt(firstNonNull(dataNode.path("model"), dataNode.path("carModel")), 1));
        data.put("direction", parseInt(firstNonNull(dataNode.path("direction"), dataNode.path("roadDirect")), 1));
        data.put("longitude", parseDouble(dataNode.path("longitude"), 0.0));
        data.put("latitude", parseDouble(dataNode.path("latitude"), 0.0));
        data.put("height", parseDouble(dataNode.path("height"), 0.0));
        data.put("speed", parseDouble(firstNonNull(dataNode.path("speed"), dataNode.path("speedKmh")), 0.0));
        data.put("acc", parseDouble(firstNonNull(dataNode.path("acc"), dataNode.path("acceleration")), 0.0));
        data.put("yaw", parseDouble(firstNonNull(dataNode.path("yaw"), dataNode.path("headingAngle")), 0.0));
        data.put("road", parseInt(dataNode.path("road"), 0));
        data.put("Lane_ID", parseInt(firstNonNull(dataNode.path("Lane_ID"), dataNode.path("laneId"), dataNode.path("lane")), 1));
        data.put("distanceAlongRoad", parseDouble(firstNonNull(dataNode.path("distanceAlongRoad"), dataNode.path("frenetX"), dataNode.path("fiberX")), 0.0));
        sendRealtimeData(data, timestamp);
    }

    private void parseAndSendCsv(String text) {
        String[] lines = text.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.endsWith(";")) {
                line = line.substring(0, line.length() - 1);
            }

            if (line.indexOf('=') >= 0) {
                if (parseAndSendKeyValueCsv(line)) {
                    continue;
                }
            }

            String[] cols = line.split(";");
            if (cols.length < 14) {
                log.warn("CSV field count invalid, need>=14, actual={}, line={}", cols.length, rawLine);
                continue;
            }
            long timestamp = parseLong(cols[0], System.currentTimeMillis());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", parseInt(cols[1], 0));
            data.put("type", parseInt(cols[2], 0));
            data.put("model", parseInt(cols[3], 1));
            data.put("direction", parseInt(cols[4], 1));
            data.put("longitude", parseDouble(cols[5], 0.0));
            data.put("latitude", parseDouble(cols[6], 0.0));
            data.put("height", parseDouble(cols[7], 0.0));
            data.put("speed", parseDouble(cols[8], 0.0));
            data.put("acc", parseDouble(cols[9], 0.0));
            data.put("yaw", parseDouble(cols[10], 0.0));
            data.put("road", parseInt(cols[11], 0));
            data.put("Lane_ID", parseInt(cols[12], 1));
            data.put("distanceAlongRoad", parseDouble(cols[13], 0.0));
            sendRealtimeData(data, timestamp);
        }
    }

    private boolean parseAndSendKeyValueCsv(String line) {
        Map<String, String> kv = parseKeyValuePairs(line);
        if (kv.isEmpty()) {
            return false;
        }

        // UC 分包控制行：FRAME_ID/PACKET_INDEX/PACKET_TOTAL/FRAME_END...
        if (isControlKvLine(kv)) {
            return true;
        }

        // 车辆数据键值对行（兼容大小写/下划线写法）
        if (!looksLikeVehicleKvLine(kv)) {
            return false;
        }

        long timestamp = parseLong(firstValue(kv, "TIMESTAMP", "TIME", "TS"), System.currentTimeMillis());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", parseInt(firstValue(kv, "ID", "VEHICLE_ID", "CAR_ID"), 0));
        data.put("type", parseInt(firstValue(kv, "TYPE", "VEHICLE_TYPE"), 0));
        data.put("model", parseInt(firstValue(kv, "MODEL", "CAR_MODEL"), 1));
        data.put("direction", parseInt(firstValue(kv, "DIRECTION", "ROAD_DIRECT"), 1));
        data.put("longitude", parseDouble(firstValue(kv, "LONGITUDE", "LON"), 0.0));
        data.put("latitude", parseDouble(firstValue(kv, "LATITUDE", "LAT"), 0.0));
        data.put("height", parseDouble(firstValue(kv, "HEIGHT", "ALT"), 0.0));
        data.put("speed", parseDouble(firstValue(kv, "SPEED", "SPEED_KMH"), 0.0));
        data.put("acc", parseDouble(firstValue(kv, "ACC", "ACCELERATION"), 0.0));
        data.put("yaw", parseDouble(firstValue(kv, "YAW", "HEADING_ANGLE"), 0.0));
        data.put("road", parseInt(firstValue(kv, "ROAD"), 0));
        data.put("Lane_ID", parseInt(firstValue(kv, "LANE_ID", "LANE", "LANEID"), 1));
        data.put("distanceAlongRoad", parseDouble(firstValue(kv, "DISTANCE_ALONG_ROAD", "DISTANCEALONGROAD", "FRENET_X", "FIBER_X"), 0.0));
        sendRealtimeData(data, timestamp);
        return true;
    }

    private Map<String, String> parseKeyValuePairs(String line) {
        Map<String, String> kv = new HashMap<>();
        String[] cols = line.split(";");
        for (String col : cols) {
            String token = col.trim();
            if (token.isEmpty()) {
                continue;
            }
            int idx = token.indexOf('=');
            if (idx <= 0 || idx >= token.length() - 1) {
                continue;
            }
            String key = token.substring(0, idx).trim().toUpperCase();
            String value = token.substring(idx + 1).trim();
            kv.put(key, value);
        }
        return kv;
    }

    private boolean isControlKvLine(Map<String, String> kv) {
        return kv.containsKey("FRAME_ID")
                || kv.containsKey("FRAME_END")
                || kv.containsKey("PACKET_INDEX")
                || kv.containsKey("PACKET_TOTAL");
    }

    private boolean looksLikeVehicleKvLine(Map<String, String> kv) {
        return kv.containsKey("ID")
                || kv.containsKey("VEHICLE_ID")
                || kv.containsKey("LONGITUDE")
                || kv.containsKey("LATITUDE")
                || kv.containsKey("DISTANCE_ALONG_ROAD")
                || kv.containsKey("FRENET_X")
                || kv.containsKey("FIBER_X");
    }

    private String firstValue(Map<String, String> kv, String... keys) {
        for (String key : keys) {
            String value = kv.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void sendRealtimeData(Map<String, Object> data, long timestamp) {
        try {
            String message = objectMapper.writeValueAsString(new TransmitDataModel(timestamp, data));
            kafkaTemplate.send(TOPIC_NAME_FIBER, message);
            scheduleTimestampSend(timestamp);
        } catch (Exception e) {
            log.error("Send realtime data failed", e);
        }
    }

    private JsonNode firstNonNull(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && !node.isNull() && !node.isMissingNode() && !node.asText("").isBlank()) {
                return node;
            }
        }
        return null;
    }

    private long parseTimestamp(JsonNode node, long defaultValue) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return defaultValue;
        }
        return parseLong(node.asText(), defaultValue);
    }

    private long parseLong(String value, long defaultValue) {
        try {
            double number = Double.parseDouble(value);
            if (number <= 0) {
                return defaultValue;
            }
            return (long) number;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int parseInt(JsonNode node, int defaultValue) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return defaultValue;
        }
        return parseInt(node.asText(), defaultValue);
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return (int) Double.parseDouble(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double parseDouble(JsonNode node, double defaultValue) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return defaultValue;
        }
        return parseDouble(node.asText(), defaultValue);
    }

    private double parseDouble(String value, double defaultValue) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void scheduleTimestampSend(long timestamp) {
        if (timestamp <= 0L) {
            return;
        }

        ScheduledFuture<?> newFuture = timestampScheduler.schedule(() -> {
            try {
                if (timestampOnChange) {
                    long last = lastTimestamp.getAndSet(timestamp);
                    if (last == timestamp) {
                        return;
                    }
                }
                kafkaTemplate.send(TOPIC_NAME_TIMESTAMP, Long.toString(timestamp));
            } catch (Exception e) {
                log.error("Send timestamp failed, timestamp={}", timestamp, e);
            } finally {
                timestampTaskMap.remove(timestamp);
            }
        }, Math.max(timestampDebounceMs, 0L), TimeUnit.MILLISECONDS);

        ScheduledFuture<?> oldFuture = timestampTaskMap.put(timestamp, newFuture);
        if (oldFuture != null) {
            oldFuture.cancel(false);
        }
    }

    @PreDestroy
    public void destroy() {
        timestampTaskMap.values().forEach(task -> task.cancel(false));
        timestampTaskMap.clear();
        timestampScheduler.shutdownNow();
    }
}
