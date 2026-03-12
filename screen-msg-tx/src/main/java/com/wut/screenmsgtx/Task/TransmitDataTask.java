package com.wut.screenmsgtx.Task;

import com.wut.screencommontx.Util.MessagePrintUtil;
import com.wut.screenmsgtx.Context.MsgTaskParamContext;
import com.wut.screenmsgtx.Service.TransmitDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

import static com.wut.screencommontx.Static.MsgModuleStatic.TRANSMIT_START_TIMEOUT;
import static com.wut.screencommontx.Static.MsgModuleStatic.TRANSMIT_TASK_TIME_RATE;

@Component
public class TransmitDataTask {
    @Qualifier("transmitDataTaskScheduler")
    private final ThreadPoolTaskScheduler transmitDataTaskScheduler;
    private final TransmitDataService transmitDataService;
    private final MsgTaskParamContext msgTaskParamContext;
    private final KafkaTemplate kafkaTemplate;
    private ScheduledFuture future;
    private static final ReentrantLock TRANSMIT_DATA_LOCK = new ReentrantLock(true);

    @Autowired
    public TransmitDataTask(ThreadPoolTaskScheduler transmitDataTaskScheduler, TransmitDataService transmitDataService, MsgTaskParamContext msgTaskParamContext, KafkaTemplate kafkaTemplate) {
        this.transmitDataTaskScheduler = transmitDataTaskScheduler;
        this.transmitDataService = transmitDataService;
        this.msgTaskParamContext = msgTaskParamContext;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void startTask() {
        try {
            Thread.sleep(TRANSMIT_START_TIMEOUT);
            future = transmitDataTaskScheduler.scheduleAtFixedRate(this::startTransmitData, Duration.ofMillis(TRANSMIT_TASK_TIME_RATE));
        } catch(Exception e) { MessagePrintUtil.printException(e, "startTransmitDataTask"); }
    }

    public void endTask() {
        try {
            if (future != null) {
                future.cancel(false);
                kafkaTemplate.flush();
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "endTransmitDataTask"); }
    }

    public void startTransmitData(){
        try {
//            MessagePrintUtil.printStartTask();
            TRANSMIT_DATA_LOCK.lock();
            long currentTimestamp = msgTaskParamContext.updateAndGetTransmitTimestamp();
//            MessagePrintUtil.printCurrentTimestamp(currentTimestamp);
            transmitDataService.filterDataAndSend(currentTimestamp);
            if (currentTimestamp >= MsgTaskParamContext.END_TIMESTAMP) {
                endTask();
            }
        } catch(Exception e) { MessagePrintUtil.printException(e, "startTransmitData"); }
        finally { TRANSMIT_DATA_LOCK.unlock(); }
    }

}
