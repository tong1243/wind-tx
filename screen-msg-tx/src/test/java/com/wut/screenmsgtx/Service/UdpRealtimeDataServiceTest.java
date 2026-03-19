package com.wut.screenmsgtx.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wut.screenmsgtx.Context.MsgTaskControlContext;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static com.wut.screencommontx.Static.MsgModuleStatic.TOPIC_NAME_FIBER;
import static com.wut.screencommontx.Static.MsgModuleStatic.TOPIC_NAME_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UdpRealtimeDataServiceTest {
    private static class RecordingKafkaTemplate extends KafkaTemplate<String, String> {
        private final List<String> topics = new ArrayList<>();
        private final List<String> payloads = new ArrayList<>();

        RecordingKafkaTemplate() {
            super(new DefaultKafkaProducerFactory<>(new HashMap<>() {{
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            }}));
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(String topic, String data) {
            topics.add(topic);
            payloads.add(data);
            return CompletableFuture.completedFuture(null);
        }
    }

    private final RecordingKafkaTemplate kafkaTemplate = new RecordingKafkaTemplate();
    private final MsgTaskControlContext msgTaskControlContext = new MsgTaskControlContext();
    private final UdpRealtimeDataService service = new UdpRealtimeDataService(kafkaTemplate, msgTaskControlContext);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        service.destroy();
    }

    @Test
    void shouldParseCsvPayloadAndSendFiberTimestamp() throws Exception {
        String csv = "1773910148000;664607;0;3;1;91.5730032;43.3741122;1074.577;106.1;0.138;1.637;2;4;5437.222;";
        service.handleUdpPayload(csv.getBytes(StandardCharsets.UTF_8));
        Thread.sleep(80);

        List<String> topics = kafkaTemplate.topics;
        List<String> bodies = kafkaTemplate.payloads;
        assertTrue(topics.contains(TOPIC_NAME_FIBER));
        assertTrue(topics.contains(TOPIC_NAME_TIMESTAMP));

        int fiberIndex = IntStream.range(0, topics.size())
                .filter(i -> TOPIC_NAME_FIBER.equals(topics.get(i)))
                .findFirst()
                .orElse(-1);
        assertTrue(fiberIndex >= 0);

        JsonNode root = objectMapper.readTree(bodies.get(fiberIndex));
        JsonNode data = root.path("data");
        assertEquals(1773910148000L, root.path("timestamp").asLong());
        assertEquals(664607, data.path("id").asInt());
        assertEquals(3, data.path("model").asInt());
        assertEquals(4, data.path("Lane_ID").asInt());
        assertEquals(5437.222, data.path("distanceAlongRoad").asDouble(), 1e-6);
    }

    @Test
    void shouldParseJsonPayloadAndSupportAliasFields() throws Exception {
        String json = """
                {
                  "timestamp": 1773910148001,
                  "data": {
                    "vehicleId": 664608,
                    "vehicleType": 2,
                    "carModel": 2,
                    "roadDirect": 2,
                    "longitude": 91.1,
                    "latitude": 43.2,
                    "height": 1000.1,
                    "speedKmh": 80.5,
                    "acceleration": 0.2,
                    "headingAngle": 10.1,
                    "road": 3,
                    "laneId": 2,
                    "frenetX": 1234.5
                  }
                }
                """;
        service.handleUdpPayload(json.getBytes(StandardCharsets.UTF_8));
        Thread.sleep(80);

        List<String> topics = kafkaTemplate.topics;
        List<String> bodies = kafkaTemplate.payloads;

        int fiberIndex = IntStream.range(0, topics.size())
                .filter(i -> TOPIC_NAME_FIBER.equals(topics.get(i)))
                .findFirst()
                .orElse(-1);
        assertTrue(fiberIndex >= 0);

        JsonNode root = objectMapper.readTree(bodies.get(fiberIndex));
        JsonNode data = root.path("data");
        assertEquals(1773910148001L, root.path("timestamp").asLong());
        assertEquals(664608, data.path("id").asInt());
        assertEquals(2, data.path("type").asInt());
        assertEquals(2, data.path("direction").asInt());
        assertEquals(2, data.path("Lane_ID").asInt());
        assertEquals(1234.5, data.path("distanceAlongRoad").asDouble(), 1e-6);
        assertTrue(topics.contains(TOPIC_NAME_TIMESTAMP));
    }
}
