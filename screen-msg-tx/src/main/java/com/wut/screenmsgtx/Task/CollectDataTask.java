package com.wut.screenmsgtx.Task;

import com.wut.screencommontx.Http.DateTimeOrderReq;
import com.wut.screencommontx.Util.MessagePrintUtil;
import com.wut.screenmsgtx.Context.MsgTaskParamContext;
import com.wut.screenmsgtx.Service.CollectDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

import static com.wut.screencommontx.Static.MsgModuleStatic.COLLECT_COND_INTERVAL;

@Component
public class CollectDataTask {
    @Qualifier("collectDataTaskScheduler")
    private final ThreadPoolTaskScheduler collectDataTaskScheduler;
    private final CollectDataService collectDataService;
    private final MsgTaskParamContext msgTaskParamContext;
    private ScheduledFuture future;
    private static final ReentrantLock COLLECT_DATA_LOCK = new ReentrantLock(true);

    @Autowired
    public CollectDataTask(ThreadPoolTaskScheduler collectDataTaskScheduler, MsgTaskParamContext msgTaskParamContext, CollectDataService collectDataService) {
        this.collectDataTaskScheduler = collectDataTaskScheduler;
        this.msgTaskParamContext = msgTaskParamContext;
        this.collectDataService = collectDataService;
    }

    public void startTask() {
        try {
            MessagePrintUtil.printStartTask();
            future = collectDataTaskScheduler.scheduleAtFixedRate(this::collectDataFromTime, Duration.ofMillis(COLLECT_COND_INTERVAL));
        } catch (Exception e) { MessagePrintUtil.printException(e, "startCollectDataTask"); }
    }

    public void endTask() {
        if (future != null) {
            future.cancel(false);
        }
    }

    public void collectDataFromTime() {
        try {
//            MessagePrintUtil.printStartCollectDataFromTime();
            COLLECT_DATA_LOCK.lock();
            long currentTimestamp = msgTaskParamContext.updateAndGetCollectTimestamp();
            collectDataService.collectDataAndUpdate(currentTimestamp);
            if (currentTimestamp >= MsgTaskParamContext.END_TIMESTAMP) {
                endTask();
            }
        } catch(Exception e) { MessagePrintUtil.printException(e, "collectDataFromTime"); }
        finally { COLLECT_DATA_LOCK.unlock(); }
    }

}
