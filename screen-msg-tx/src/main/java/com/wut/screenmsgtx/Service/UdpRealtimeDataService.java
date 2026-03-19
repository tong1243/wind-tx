package com.wut.screenmsgtx.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wut.screencommontx.Model.TransmitDataModel;
import com.wut.screendbtx.Model.Fiber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static com.wut.screencommontx.Static.MsgModuleStatic.TOPIC_NAME_FIBER;
import static com.wut.screencommontx.Static.MsgModuleStatic.TOPIC_NAME_TIMESTAMP;

@Slf4j
@Component
public class UdpRealtimeDataService {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong lastTimestamp = new AtomicLong(Long.MIN_VALUE);

    @Value("${msg.udp.timestamp-on-change:true}")
    private boolean timestampOnChange;

    public UdpRealtimeDataService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void handleUdpPayload(byte[] payload) {
        try {
            String text = new String(payload, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return;
            }
            JsonNode root = objectMapper.readTree(text);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    parseAndSend(node);
                }
            } else {
                parseAndSend(root);
            }
        } catch (Exception e) {
            log.warn("Ignore unsupported UDP payload, raw={}", new String(payload, StandardCharsets.UTF_8), e);
        }
    }

    private void parseAndSend(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }

        if (node.has("data")) {
            long timestamp = parseTimestamp(node.path("timestamp"), parseTimestamp(node.path("data").path("timestamp"), System.currentTimeMillis()));
            Fiber fiber = objectMapper.convertValue(node.path("data"), Fiber.class);
            sendFiberAndTimestamp(fiber, timestamp);
            return;
        }

        Fiber fiber = objectMapper.convertValue(node, Fiber.class);
        long timestamp = parseTimestamp(node.path("timestamp"), System.currentTimeMillis());
        sendFiberAndTimestamp(fiber, timestamp);
    }

    private long parseTimestamp(JsonNode timestampNode, long defaultTimestamp) {
        if (timestampNode == null || timestampNode.isNull() || timestampNode.isMissingNode()) {
            return defaultTimestamp;
        }
        if (timestampNode.isNumber()) {
            double timestampValue = timestampNode.asDouble();
            if (timestampValue <= 0) {
                return defaultTimestamp;
            }
            return (long) timestampValue;
        }
        try {
            return Long.parseLong(timestampNode.asText());
        } catch (Exception e) {
            return defaultTimestamp;
        }
    }

    private void sendFiberAndTimestamp(Fiber fiber, long timestamp) {
        try {
            String message = objectMapper.writeValueAsString(new TransmitDataModel(timestamp, fiber));
            kafkaTemplate.send(TOPIC_NAME_FIBER, message);
            sendTimestampIfNeeded(timestamp);
        } catch (Exception e) {
            log.error("Send realtime fiber data failed", e);
        }
    }

    private void sendTimestampIfNeeded(long timestamp) {
        if (timestamp <= 0L) {
            return;
        }
        if (timestampOnChange) {
            long last = lastTimestamp.getAndSet(timestamp);
            if (last == timestamp) {
                return;
            }
        }
        kafkaTemplate.send(TOPIC_NAME_TIMESTAMP, Long.toString(timestamp));
    }
}
