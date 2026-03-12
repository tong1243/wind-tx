package com.wut.screenmsgtx.Service.AsyncService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wut.screencommontx.Model.CollectDataModel;
import com.wut.screencommontx.Util.CollectionEmptyUtil;
import com.wut.screencommontx.Util.MessagePrintUtil;
import com.wut.screendbtx.Model.Fiber;
import com.wut.screendbtx.Service.FiberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.wut.screencommontx.Static.MsgModuleStatic.*;

@Component
public class CollectDataAsyncService {
    @Qualifier("msgCollectTaskAsyncPool")
    private final Executor msgCollectTaskAsyncPool;
    @Qualifier("msgExpireTaskAsyncPool")
    private final Executor msgExpireTaskAsyncPool;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FiberService fiberService;


    @Autowired
    public CollectDataAsyncService(StringRedisTemplate stringRedisTemplate,  FiberService fiberService, Executor msgCollectTaskAsyncPool, Executor msgExpireTaskAsyncPool) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.fiberService = fiberService;
        this.msgCollectTaskAsyncPool = msgCollectTaskAsyncPool;
        this.msgExpireTaskAsyncPool = msgExpireTaskAsyncPool;
    }


    public CompletableFuture<Fiber> collectFiberMinTimestamp(CollectDataModel param) {
        return CompletableFuture.supplyAsync(() -> fiberService.collectMinTimestamp(param), msgCollectTaskAsyncPool);
    }



    public CompletableFuture<List<Fiber>> collectFiberFromTime(CollectDataModel param) {
        return CompletableFuture.supplyAsync(() -> fiberService.collectFromTime(param), msgCollectTaskAsyncPool);
    }



    public CompletableFuture<Void> storeCollectFiberData(List<Fiber> fiberData) {
        return CompletableFuture.runAsync(() -> {
            if (CollectionEmptyUtil.forList(fiberData)) {
                return;
            }

            // 开启Pipeline
            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Fiber fiber : fiberData) {
                    try {
                        // 将对象转换为JSON字符串
                        String fiberJson = objectMapper.writeValueAsString(fiber);
                        // 使用Pipeline添加到有序集合
                        connection.zAdd(REDIS_KEY_FIBER_DATA.getBytes(), fiber.getTimestamp(), fiberJson.getBytes());
                    } catch (JsonProcessingException e) {
                        MessagePrintUtil.printException(e, "storeCollectFiberData");
                    }
                }
                return null;
            });
        }, msgCollectTaskAsyncPool);
    }


    public CompletableFuture<Void> removeExpireRadarData(double timestamp) {
        return CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> stringRedisTemplate.opsForZSet().removeRangeByScore(REDIS_KEY_FIBER_DATA, (timestamp - 240000), (timestamp - 180000)), msgExpireTaskAsyncPool)

        );
    }

}
