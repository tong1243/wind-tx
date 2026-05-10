package com.wut.screenmsgtx.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wut.screencommontx.Model.TransmitDataModel;
import com.wut.screenmsgtx.Context.MsgTaskControlContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wut.screencommontx.Static.MsgModuleStatic.TOPIC_NAME_FIBER;
import static com.wut.screencommontx.Static.MsgModuleStatic.TOPIC_NAME_TIMESTAMP;
import static com.wut.screencommontx.Static.MsgModuleStatic.TOPIC_NAME_WIND;

@Component
public class UdpRealtimeDataService {
    private static final Logger log = LoggerFactory.getLogger(UdpRealtimeDataService.class);
    private static final Pattern WIND_HYPHEN_RECORD_PATTERN = Pattern.compile(
            "^([0-9]{10,13})-([kK][0-9]+(?:\\+[0-9]+)?)-([kK][0-9]+(?:\\+[0-9]+)?)-([+-]?[0-9]+(?:\\.[0-9]+)?)$"
    );
    private static final Pattern TIMESTAMP_TOKEN_PATTERN = Pattern.compile("(?<!\\d)(\\d{10,13})(?!\\d)");
    private static final String CSV_TOKEN_SPLIT_REGEX = "[;,]";
    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MsgTaskControlContext msgTaskControlContext;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong lastTimestamp = new AtomicLong(Long.MIN_VALUE);
    private final ScheduledExecutorService timestampScheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<Long, ScheduledFuture<?>> timestampTaskMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService secondBucketScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentSkipListMap<Long, ConcurrentLinkedQueue<String>> secondBucketMap = new ConcurrentSkipListMap<>();
    private final ScheduledExecutorService summaryScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> secondBucketTaskFuture;
    private ScheduledFuture<?> summaryTaskFuture;

    private final LongAdder udpPayloadCount = new LongAdder();
    private final LongAdder csvControlLineCount = new LongAdder();
    private final LongAdder csvVehicleLineCount = new LongAdder();
    private final LongAdder csvWindLineCount = new LongAdder();
    private final LongAdder csvInvalidLineCount = new LongAdder();
    private final LongAdder fiberSendCount = new LongAdder();
    private final LongAdder windSendCount = new LongAdder();
    private final LongAdder timestampSendCount = new LongAdder();
    private final LongAdder sendFailCount = new LongAdder();

    @Value("${msg.udp.timestamp-on-change:true}")
    private boolean timestampOnChange;

    @Value("${msg.udp.timestamp-debounce-ms:150}")
    private long timestampDebounceMs;

    @Value("${msg.udp.send-by-second-enabled:true}")
    private boolean sendBySecondEnabled;

    @Value("${msg.udp.send-interval-sec:1}")
    private long sendIntervalSec;

    @Value("${msg.udp.summary-log-enabled:true}")
    private boolean summaryLogEnabled;

    @Value("${msg.udp.summary-log-interval-sec:1}")
    private long summaryLogIntervalSec;

    @Value("${msg.udp.debug-log-enabled:false}")
    private boolean debugLogEnabled;

    @Value("${msg.udp.debug-log-max-len:2000}")
    private int debugLogMaxLen;

    @Value("${msg.udp.debug-log-ts-summary-enabled:false}")
    private boolean debugLogTsSummaryEnabled;

    public UdpRealtimeDataService(KafkaTemplate<String, String> kafkaTemplate, MsgTaskControlContext msgTaskControlContext) {
        this.kafkaTemplate = kafkaTemplate;
        this.msgTaskControlContext = msgTaskControlContext;
    }

    @PostConstruct
    public void initSummaryLogger() {
        if (sendBySecondEnabled) {
            long intervalSec = Math.max(sendIntervalSec, 1L);
            secondBucketTaskFuture = secondBucketScheduler.scheduleAtFixedRate(
                    this::flushOneSecondBucket, intervalSec, intervalSec, TimeUnit.SECONDS
            );
        }
        if (!summaryLogEnabled) {
            return;
        }
        long intervalSec = Math.max(summaryLogIntervalSec, 1L);
        summaryTaskFuture = summaryScheduler.scheduleAtFixedRate(this::printAndResetSummary, intervalSec, intervalSec, TimeUnit.SECONDS);
    }

    public void handleUdpPayload(byte[] payload) {
        udpPayloadCount.increment();
        if (!msgTaskControlContext.isActive()) {
            return;
        }
        String text = new String(payload, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return;
        }
        printUdpDebug(text);

        if (maybeCsv(text)) {
            parseAndSendCsv(text);
            return;
        }
        parseAndSendJson(text);
    }

    private boolean maybeCsv(String text) {
        if (text.startsWith("{") || text.startsWith("[")) {
            return false;
        }
        return text.indexOf(';') >= 0 || text.indexOf('；') >= 0 || looksLikeWindHyphenRecord(text);
    }

    private void parseAndSendJson(String text) {
        try {
            JsonNode root = objectMapper.readTree(text);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    normalizeJsonAndSend(node, System.currentTimeMillis());
                }
                return;
            }
            normalizeJsonAndSend(root, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("Ignore unsupported UDP payload, raw={}", text, e);
        }
    }

    private void normalizeJsonAndSend(JsonNode rootNode, long inheritedTimestamp) {
        if (rootNode == null || rootNode.isNull() || rootNode.isMissingNode()) {
            return;
        }

        JsonNode dataNode = rootNode.has("data") ? rootNode.path("data") : rootNode;
        long timestamp = parseTimestamp(
                firstNonNull(rootNode.path("timestamp"), rootNode.path("timeStamp"), rootNode.path("time"), rootNode.path("ts")),
                parseTimestamp(firstNonNull(dataNode.path("timestamp"), dataNode.path("timeStamp"), dataNode.path("time"), dataNode.path("ts")), inheritedTimestamp)
        );

        if (dataNode.isArray()) {
            for (JsonNode item : dataNode) {
                normalizeJsonAndSend(item, timestamp);
            }
            return;
        }

        if (looksLikeWindJson(rootNode, dataNode)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", parseIntegerOrNull(firstNonNull(dataNode.path("direction"), dataNode.path("roadDirect"), dataNode.path("road_direction"), rootNode.path("direction"), rootNode.path("roadDirect"), rootNode.path("road_direction"))));
            data.put("startStake", parseText(firstNonNull(dataNode.path("startStake"), dataNode.path("start_stake"), dataNode.path("startKm"), dataNode.path("start_km"), rootNode.path("startStake"), rootNode.path("start_stake"))));
            data.put("endStake", parseText(firstNonNull(dataNode.path("endStake"), dataNode.path("end_stake"), dataNode.path("endKm"), dataNode.path("end_km"), rootNode.path("endStake"), rootNode.path("end_stake"))));
            data.put("sectionName", parseText(firstNonNull(dataNode.path("sectionName"), dataNode.path("section_name"), rootNode.path("sectionName"), rootNode.path("section_name"))));
            data.put("windSpeed", parseDouble(firstNonNull(dataNode.path("windSpeed"), dataNode.path("wind_speed"), dataNode.path("wind"), rootNode.path("windSpeed"), rootNode.path("wind_speed"), rootNode.path("wind")), 0.0));
            data.put("windDirection", parseText(firstNonNull(dataNode.path("windDirection"), dataNode.path("wind_direction"), dataNode.path("windDir"), dataNode.path("wind_dir"), rootNode.path("windDirection"), rootNode.path("wind_direction"))));
            data.put("heavyVehicleSpeedLimit", parseIntegerOrNull(firstNonNull(dataNode.path("heavyVehicleSpeedLimit"), dataNode.path("heavy_vehicle_speed_limit"), rootNode.path("heavyVehicleSpeedLimit"), rootNode.path("heavy_vehicle_speed_limit"))));
            data.put("lightVehicleSpeedLimit", parseIntegerOrNull(firstNonNull(dataNode.path("lightVehicleSpeedLimit"), dataNode.path("light_vehicle_speed_limit"), rootNode.path("lightVehicleSpeedLimit"), rootNode.path("light_vehicle_speed_limit"))));
            data.put("controlLevel", parseIntegerOrNull(firstNonNull(dataNode.path("controlLevel"), dataNode.path("control_level"), rootNode.path("controlLevel"), rootNode.path("control_level"))));
            data.put("dataSource", parseText(firstNonNull(dataNode.path("dataSource"), dataNode.path("data_source"), dataNode.path("source"), rootNode.path("dataSource"), rootNode.path("data_source"), rootNode.path("source"))));
            sendWindData(data, timestamp);
            return;
        }

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
        data.put("headingAngle", data.get("yaw"));
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
            if (line.endsWith(";") || line.endsWith("；")) {
                line = line.substring(0, line.length() - 1);
            }

            if (parseAndSendWindHyphenRecords(line)) {
                continue;
            }

            if (line.indexOf('=') >= 0) {
                if (parseAndSendKeyValueCsv(line)) {
                    continue;
                }
            }

            String[] cols = splitCsvTokens(line);
            if (looksLikeWindPlainCsv(cols)) {
                long timestamp = parseLong(cols[0], System.currentTimeMillis());
                Map<String, Object> data = new LinkedHashMap<>();
                boolean noDirectionLayout = looksLikeWindPlainCsvWithoutDirection(cols);
                int startStakeIndex = noDirectionLayout ? 1 : 2;
                int endStakeIndex = noDirectionLayout ? 2 : 3;
                int windSpeedIndex = noDirectionLayout ? 3 : 4;
                int windDirectionIndex = noDirectionLayout ? 4 : 5;
                int dataSourceIndex = noDirectionLayout ? 5 : 6;
                int lightLimitIndex = noDirectionLayout ? 6 : 7;
                int heavyLimitIndex = noDirectionLayout ? 7 : 8;
                int controlLevelIndex = noDirectionLayout ? 8 : 9;

                data.put("direction", noDirectionLayout ? null : parseIntegerOrNull(cols.length > 1 ? cols[1] : ""));
                data.put("startStake", cols.length > startStakeIndex ? cols[startStakeIndex].trim() : "");
                data.put("endStake", cols.length > endStakeIndex ? cols[endStakeIndex].trim() : "");
                data.put("windSpeed", parseDouble(cols.length > windSpeedIndex ? cols[windSpeedIndex] : "", 0.0));
                data.put("windDirection", cols.length > windDirectionIndex ? cols[windDirectionIndex].trim() : "");
                data.put("dataSource", cols.length > dataSourceIndex ? cols[dataSourceIndex].trim() : "");
                data.put("lightVehicleSpeedLimit", parseIntegerOrNull(cols.length > lightLimitIndex ? cols[lightLimitIndex] : ""));
                data.put("heavyVehicleSpeedLimit", parseIntegerOrNull(cols.length > heavyLimitIndex ? cols[heavyLimitIndex] : ""));
                data.put("controlLevel", parseIntegerOrNull(cols.length > controlLevelIndex ? cols[controlLevelIndex] : ""));
                csvWindLineCount.increment();
                sendWindData(data, timestamp);
                continue;
            }

            if (cols.length < 14) {
                csvInvalidLineCount.increment();
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
            data.put("headingAngle", data.get("yaw"));
            data.put("road", parseInt(cols[11], 0));
            data.put("Lane_ID", parseInt(cols[12], 1));
            data.put("distanceAlongRoad", parseDouble(cols[13], 0.0));
            csvVehicleLineCount.increment();
            sendRealtimeData(data, timestamp);
        }
    }

    private boolean parseAndSendKeyValueCsv(String line) {
        Map<String, String> kv = parseKeyValuePairs(line);
        if (kv.isEmpty()) {
            return false;
        }

        if (looksLikeWindKvLine(kv)) {
            long timestamp = parseLong(firstValue(kv, "TIMESTAMP", "TIME", "TS"), System.currentTimeMillis());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", parseIntegerOrNull(firstValue(kv, "DIRECTION", "ROAD_DIRECT", "ROAD_DIRECTION")));
            data.put("startStake", firstValue(kv, "START_STAKE", "STARTSTAKE", "START_KM", "STARTKM"));
            data.put("endStake", firstValue(kv, "END_STAKE", "ENDSTAKE", "END_KM", "ENDKM"));
            data.put("sectionName", firstValue(kv, "SECTION_NAME", "SECTION"));
            data.put("windSpeed", parseDouble(firstValue(kv, "WIND_SPEED", "WINDSPEED", "WIND", "SPEED"), 0.0));
            data.put("windDirection", firstValue(kv, "WIND_DIRECTION", "WINDDIRECTION", "WIND_DIR", "WINDDIR"));
            data.put("heavyVehicleSpeedLimit", parseIntegerOrNull(firstValue(kv, "HEAVY_VEHICLE_SPEED_LIMIT", "HEAVY_SPEED_LIMIT", "TRUCK_SPEED_LIMIT")));
            data.put("lightVehicleSpeedLimit", parseIntegerOrNull(firstValue(kv, "LIGHT_VEHICLE_SPEED_LIMIT", "LIGHT_SPEED_LIMIT", "CAR_SPEED_LIMIT")));
            data.put("controlLevel", parseIntegerOrNull(firstValue(kv, "CONTROL_LEVEL", "LEVEL")));
            data.put("dataSource", firstValue(kv, "DATA_SOURCE", "SOURCE"));
            csvWindLineCount.increment();
            sendWindData(data, timestamp);
            return true;
        }

        if (looksLikeVehicleKvLine(kv)) {
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
            data.put("yaw", parseDouble(firstValue(kv, "YAW", "HEADING_ANGLE", "HEADINGANGLE"), 0.0));
            data.put("headingAngle", data.get("yaw"));
            data.put("road", parseInt(firstValue(kv, "ROAD"), 0));
            data.put("Lane_ID", parseInt(firstValue(kv, "LANE_ID", "LANE", "LANEID"), 1));
            data.put("distanceAlongRoad", parseDouble(firstValue(kv, "DISTANCE_ALONG_ROAD", "DISTANCEALONGROAD", "FRENET_X", "FIBER_X"), 0.0));
            csvVehicleLineCount.increment();
            sendRealtimeData(data, timestamp);
            return true;
        }

        if (isControlKvLine(kv)) {
            csvControlLineCount.increment();
            return true;
        }

        return false;
    }

    private Map<String, String> parseKeyValuePairs(String line) {
        Map<String, String> kv = new HashMap<>();
        String[] cols = splitCsvTokens(line);
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
        if (looksLikeVehicleKvLine(kv) || looksLikeWindKvLine(kv)) {
            return false;
        }
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

    private boolean looksLikeWindKvLine(Map<String, String> kv) {
        String msgType = firstValue(kv, "MSG_TYPE", "TYPE", "BIZ_TYPE");
        boolean explicitWindType = "WIND".equalsIgnoreCase(msgType);
        boolean hasStake = hasText(firstValue(kv, "START_STAKE", "STARTSTAKE", "START_KM", "STARTKM"))
                || hasText(firstValue(kv, "END_STAKE", "ENDSTAKE", "END_KM", "ENDKM"));
        boolean hasWindField = hasText(firstValue(kv, "WIND_SPEED", "WINDSPEED", "WIND", "WIND_DIRECTION", "WINDDIRECTION"));
        return explicitWindType || (hasStake && hasWindField);
    }

    private boolean looksLikeWindPlainCsv(String[] cols) {
        return looksLikeWindPlainCsvWithoutDirection(cols) || looksLikeWindPlainCsvWithDirection(cols);
    }

    private boolean looksLikeWindPlainCsvWithoutDirection(String[] cols) {
        if (cols == null || cols.length < 4) {
            return false;
        }
        if (!isNumeric(cols[0])) {
            return false;
        }
        String startStake = cols[1] == null ? "" : cols[1].trim();
        String endStake = cols[2] == null ? "" : cols[2].trim();
        boolean hasStake = looksLikeStake(startStake) && looksLikeStake(endStake);
        if (!hasStake) {
            return false;
        }
        return isNumeric(cols[3]);
    }

    private boolean looksLikeWindPlainCsvWithDirection(String[] cols) {
        if (cols == null || cols.length < 5) {
            return false;
        }
        if (!isNumeric(cols[0])) {
            return false;
        }
        String startStake = cols[2] == null ? "" : cols[2].trim();
        String endStake = cols[3] == null ? "" : cols[3].trim();
        boolean hasStake = looksLikeStake(startStake) && looksLikeStake(endStake);
        if (!hasStake) {
            return false;
        }
        return isNumeric(cols[4]);
    }

    private boolean parseAndSendWindHyphenRecords(String line) {
        if (!hasText(line)) {
            return false;
        }
        String[] records = splitCsvTokens(line);
        int parsedCount = 0;
        for (String raw : records) {
            String token = raw == null ? "" : raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            Matcher matcher = WIND_HYPHEN_RECORD_PATTERN.matcher(token);
            if (!matcher.matches()) {
                continue;
            }
            long timestamp = parseLong(matcher.group(1), System.currentTimeMillis());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", null);
            data.put("startStake", matcher.group(2));
            data.put("endStake", matcher.group(3));
            data.put("windSpeed", parseDouble(matcher.group(4), 0.0));
            data.put("windDirection", "");
            data.put("dataSource", "");
            data.put("lightVehicleSpeedLimit", null);
            data.put("heavyVehicleSpeedLimit", null);
            data.put("controlLevel", null);
            csvWindLineCount.increment();
            sendWindData(data, timestamp);
            parsedCount++;
        }
        return parsedCount > 0;
    }

    private String[] splitCsvTokens(String line) {
        return line.split(CSV_TOKEN_SPLIT_REGEX);
    }

    private void printUdpDebug(String text) {
        if (!debugLogEnabled && !debugLogTsSummaryEnabled) {
            return;
        }
        if (debugLogEnabled) {
            log.info("UDP raw payload: {}", truncateForLog(text));
        }
        if (debugLogTsSummaryEnabled) {
            printTimestampSummary(text);
        }
    }

    private String truncateForLog(String text) {
        int maxLen = Math.max(debugLogMaxLen, 200);
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...(truncated,totalChars=" + text.length() + ")";
    }

    private void printTimestampSummary(String text) {
        Matcher matcher = TIMESTAMP_TOKEN_PATTERN.matcher(text);
        HashSet<Long> uniqueTs = new HashSet<>();
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        int total = 0;
        while (matcher.find()) {
            long normalized = normalizePossibleEpochTimestamp(parseLong(matcher.group(1), 0L));
            if (normalized <= 0L) {
                continue;
            }
            total++;
            uniqueTs.add(normalized);
            if (normalized < min) {
                min = normalized;
            }
            if (normalized > max) {
                max = normalized;
            }
        }
        if (total == 0) {
            return;
        }
        long spanMin = Math.max(0L, (max - min) / 60000L);
        log.info(
                "UDP timestamp summary: totalTokens={}, unique={}, min={}, max={}, spanMin={}",
                total, uniqueTs.size(), formatTimestamp(min), formatTimestamp(max), spanMin
        );
    }

    private long normalizePossibleEpochTimestamp(long ts) {
        if (ts <= 0L) {
            return 0L;
        }
        if (ts < 1000000000000L) {
            return ts * 1000L;
        }
        return ts;
    }

    private String formatTimestamp(long ts) {
        return ts + "(" + TS_FORMATTER.format(Instant.ofEpochMilli(ts)) + ")";
    }

    private boolean looksLikeWindHyphenRecord(String text) {
        if (!hasText(text)) {
            return false;
        }
        return WIND_HYPHEN_RECORD_PATTERN.matcher(text.trim()).matches();
    }

    private boolean looksLikeWindJson(JsonNode rootNode, JsonNode dataNode) {
        JsonNode typeNode = firstNonNull(
                dataNode.path("msgType"),
                dataNode.path("type"),
                dataNode.path("bizType"),
                rootNode.path("msgType"),
                rootNode.path("type"),
                rootNode.path("bizType")
        );
        String type = parseText(typeNode);
        boolean explicitWindType = "wind".equalsIgnoreCase(type);

        JsonNode startStakeNode = firstNonNull(dataNode.path("startStake"), dataNode.path("start_stake"), dataNode.path("startKm"), dataNode.path("start_km"), rootNode.path("startStake"), rootNode.path("start_stake"));
        JsonNode endStakeNode = firstNonNull(dataNode.path("endStake"), dataNode.path("end_stake"), dataNode.path("endKm"), dataNode.path("end_km"), rootNode.path("endStake"), rootNode.path("end_stake"));
        boolean hasStake = hasText(parseText(startStakeNode)) || hasText(parseText(endStakeNode));

        JsonNode windNode = firstNonNull(dataNode.path("windSpeed"), dataNode.path("wind_speed"), dataNode.path("wind"), rootNode.path("windSpeed"), rootNode.path("wind_speed"), rootNode.path("wind"));
        JsonNode windDirectionNode = firstNonNull(dataNode.path("windDirection"), dataNode.path("wind_direction"), rootNode.path("windDirection"), rootNode.path("wind_direction"));
        boolean hasWindField = hasText(parseText(windNode)) || hasText(parseText(windDirectionNode));

        return explicitWindType || (hasStake && hasWindField);
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
            if (sendBySecondEnabled) {
                long bucketTimestamp = normalizeSecondTimestamp(timestamp);
                secondBucketMap.computeIfAbsent(bucketTimestamp, key -> new ConcurrentLinkedQueue<>()).add(message);
                return;
            }
            kafkaTemplate.send(TOPIC_NAME_FIBER, message);
            fiberSendCount.increment();
            scheduleTimestampSend(timestamp);
        } catch (Exception e) {
            sendFailCount.increment();
            log.error("Send realtime data failed", e);
        }
    }

    private void sendWindData(Map<String, Object> data, long timestamp) {
        try {
            String message = objectMapper.writeValueAsString(new TransmitDataModel(timestamp, data));
            kafkaTemplate.send(TOPIC_NAME_WIND, message);
            windSendCount.increment();
        } catch (Exception e) {
            sendFailCount.increment();
            log.error("Send wind data failed", e);
        }
    }

    private long normalizeSecondTimestamp(long timestamp) {
        long safeTimestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        return safeTimestamp / 1000L * 1000L;
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

    private Integer parseIntegerOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return parseIntegerOrNull(node.asText());
    }

    private Integer parseIntegerOrNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (Exception e) {
            return null;
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

    private String parseText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        return node.asText("").trim();
    }

    private boolean isNumeric(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean looksLikeStake(String value) {
        if (!hasText(value)) {
            return false;
        }
        String text = value.trim();
        if (text.startsWith("K") || text.startsWith("k")) {
            return true;
        }
        try {
            return Double.parseDouble(text) >= 1000D;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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
                timestampSendCount.increment();
            } catch (Exception e) {
                sendFailCount.increment();
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

    private void flushOneSecondBucket() {
        Map.Entry<Long, ConcurrentLinkedQueue<String>> entry;
        while ((entry = secondBucketMap.pollFirstEntry()) != null) {
            long timestamp = entry.getKey();
            ConcurrentLinkedQueue<String> queue = entry.getValue();
            String message;
            while ((message = queue.poll()) != null) {
                try {
                    kafkaTemplate.send(TOPIC_NAME_FIBER, message);
                    fiberSendCount.increment();
                } catch (Exception e) {
                    sendFailCount.increment();
                    log.error("Send realtime data failed in second-bucket mode, timestamp={}", timestamp, e);
                }
            }
            trySendTimestamp(timestamp);
        }
    }

    private void trySendTimestamp(long timestamp) {
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
        timestampSendCount.increment();
    }

    private void printAndResetSummary() {
        long payload = udpPayloadCount.sumThenReset();
        long control = csvControlLineCount.sumThenReset();
        long vehicle = csvVehicleLineCount.sumThenReset();
        long wind = csvWindLineCount.sumThenReset();
        long invalid = csvInvalidLineCount.sumThenReset();
        long fiberSent = fiberSendCount.sumThenReset();
        long windSent = windSendCount.sumThenReset();
        long timestampSent = timestampSendCount.sumThenReset();
        long failed = sendFailCount.sumThenReset();

        long total = payload + control + vehicle + wind + invalid + fiberSent + windSent + timestampSent + failed;
        if (total == 0L) {
            return;
        }

        log.info(
                "UDP summary: payload={}, controlLine={}, vehicleLine={}, windLine={}, invalidLine={}, fiberSent={}, windSent={}, timestampSent={}, failed={}",
                payload, control, vehicle, wind, invalid, fiberSent, windSent, timestampSent, failed
        );
    }

    @PreDestroy
    public void destroy() {
        timestampTaskMap.values().forEach(task -> task.cancel(false));
        timestampTaskMap.clear();
        timestampScheduler.shutdownNow();
        if (secondBucketTaskFuture != null) {
            secondBucketTaskFuture.cancel(false);
        }
        while (!secondBucketMap.isEmpty()) {
            flushOneSecondBucket();
        }
        secondBucketScheduler.shutdownNow();
        if (summaryTaskFuture != null) {
            summaryTaskFuture.cancel(false);
        }
        summaryScheduler.shutdownNow();
    }
}
