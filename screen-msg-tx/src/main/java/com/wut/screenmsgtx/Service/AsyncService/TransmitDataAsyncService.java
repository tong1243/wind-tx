package com.wut.screenmsgtx.Service.AsyncService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wut.screencommontx.Model.TransmitDataModel;
import com.wut.screencommontx.Util.CollectionEmptyUtil;
import com.wut.screencommontx.Util.MessagePrintUtil;
import com.wut.screendbtx.Model.Fiber;
import com.wut.screenmsgtx.Context.MsgRedisDataContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.wut.screencommontx.Static.MsgModuleStatic.*;

@Component
public class TransmitDataAsyncService {
    @Qualifier("msgTransmitTaskAsyncPool")
    private final Executor msgTransmitTaskAsyncPool;
    private final MsgRedisDataContext msgRedisDataContext;
    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaTemplate kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public TransmitDataAsyncService(MsgRedisDataContext msgRedisDataContext, StringRedisTemplate stringRedisTemplate, KafkaTemplate kafkaTemplate, Executor msgTransmitTaskAsyncPool) {
        this.msgRedisDataContext = msgRedisDataContext;
        this.stringRedisTemplate = stringRedisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.msgTransmitTaskAsyncPool = msgTransmitTaskAsyncPool;
    }

    public <T> String getWrapSendDataStr(double timestamp, T data) {
        TransmitDataModel dataToSend = new TransmitDataModel((long)timestamp, data);
        try { return objectMapper.writeValueAsString(dataToSend); }
        catch (JsonProcessingException e) { return null; }
    }


    public CompletableFuture<List<Fiber>> filterCollectFiberData(double timestamp) {
        return CompletableFuture.supplyAsync(() -> {
            List<Fiber> fiberData = new ArrayList<>();
            if (!msgRedisDataContext.isFiberDataEmpty()) {
                MessagePrintUtil.printCurrentTimestamp((long) timestamp);
//                MessagePrintUtil.printFilterCollectFiberData(stringRedisTemplate.opsForZSet().rangeByScore(REDIS_KEY_FIBER_DATA, timestamp, timestamp).toString());
                fiberData.addAll(Objects.requireNonNull(stringRedisTemplate.opsForZSet().rangeByScore(REDIS_KEY_FIBER_DATA, timestamp, timestamp)).stream().map(str -> {
                    try { return objectMapper.readValue(str, Fiber.class); }
                    catch (JsonProcessingException e) { return null; }
                }).filter(Objects::nonNull).toList());
            }
//
//            if (msgRedisDataContext.isFiberDataEmpty()) {
//                MessagePrintUtil.printFailureToCollectDataAndUpdate();
//            }
//            try {
//                MessagePrintUtil.printFilterCollectFiberData("fiberData:"+objectMapper.writeValueAsString(fiberData));
//            } catch (JsonProcessingException e) {
//                throw new RuntimeException(e);
//            }
            return fiberData;
        }, msgTransmitTaskAsyncPool);
    }



    public CompletableFuture<Void> transmitFiberData(List<Fiber> fiberData, double timestamp) {
        return CompletableFuture.runAsync(() -> {
            if (CollectionEmptyUtil.forList(fiberData)) { return; }
            fiberData.stream().forEach(fiber -> {
                Optional.ofNullable(getWrapSendDataStr(timestamp,fiber)).ifPresent(sendData -> {
                    kafkaTemplate.send(TOPIC_NAME_FIBER, sendData);
//                    MessagePrintUtil.printProducerTransmit(TOPIC_NAME_FIBER, sendData);
                });
            });
        }, msgTransmitTaskAsyncPool);
    }



    public void transmitTimestamp(double timestamp) {
        CompletableFuture.runAsync(() -> {
            try {
                kafkaTemplate.send(TOPIC_NAME_TIMESTAMP, String.valueOf(((long)timestamp)));
//                MessagePrintUtil.printProducerTransmit(TOPIC_NAME_TIMESTAMP,String.valueOf(((long)timestamp)));
            } catch (Exception e) { MessagePrintUtil.printException(e, "transmitTimestamp"); }
        }, msgTransmitTaskAsyncPool);
    }

    public void transmitDirectData() {
        try {
            kafkaTemplate.send(TOPIC_NAME_DIRECT, null);
        } catch (Exception e) { MessagePrintUtil.printException(e, "transmitDirectData"); }
    }

}
