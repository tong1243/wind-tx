package com.wut.screenmsgtx.Context;

import com.wut.screencommontx.Http.DateTimeOrderReq;
import com.wut.screencommontx.Util.DateParamParseUtil;
import com.wut.screencommontx.Util.MessagePrintUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

import static com.wut.screencommontx.Static.MsgModuleStatic.*;

@Component
public class MsgTaskParamContext {
    public static String TODAY_STR = null;
    public static long END_TIMESTAMP = 0L;
    private static final AtomicLong COLLECT_TIME = new AtomicLong(0L);
    private static final AtomicLong TRANSMIT_TIME = new AtomicLong(0L);

    @PostConstruct
    public void initMsgTaskParam() {
        TODAY_STR = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DEFAULT_DATE_PATTERN));
        COLLECT_TIME.set(0L);
        TRANSMIT_TIME.set(0L);
    }

    public void resetTimeParam() {
        TODAY_STR = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DEFAULT_DATE_PATTERN));
        COLLECT_TIME.set(0L);
        TRANSMIT_TIME.set(0L);
    }

    // 初始化时间参数
    public void initTodayParam(DateTimeOrderReq req) {
        long timestamp = 0L;
        // 设置日期时,时间戳和日期必须至少传入一个,且时间戳的优先级更高
        if (req.getTime() == 0L) {
            TODAY_STR = req.getToday();
            timestamp = DateParamParseUtil.getStartTimestamp(TODAY_STR);
        } else {
            TODAY_STR = DateParamParseUtil.getDateStr(req.getTime());
            // 取数据时,设初始时间戳为起始时间戳A,首次取得的是[A, A + 2000]的数据,不影响发送的顺序
            // 发送数据时,设初始时间戳为起始时间戳A - 200,首次发送的是A时间点的数据(<A的在初始化偏移量时设为跳过)
            timestamp = DateParamParseUtil.getRoundTimestamp(req.getTime());
        }
        // 设置任务的结束时间戳
        END_TIMESTAMP = DateParamParseUtil.getEndTimestamp(TODAY_STR);
        MessagePrintUtil.printInitParam(TODAY_STR, timestamp, END_TIMESTAMP);
        while (!COLLECT_TIME.compareAndSet(0L, timestamp - TRANSMIT_COND_INTERVAL)) {}
        while (!TRANSMIT_TIME.compareAndSet(0L, timestamp - TRANSMIT_COND_INTERVAL)) {}
    }

    public long updateAndGetCollectTimestamp() {
        return COLLECT_TIME.accumulateAndGet(COLLECT_COND_INTERVAL, Long::sum);
    }

    public long updateAndGetTransmitTimestamp() {
        return TRANSMIT_TIME.accumulateAndGet(TRANSMIT_COND_INTERVAL, Long::sum);
    }

}
