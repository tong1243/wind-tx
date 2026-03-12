package com.wut.screenmsgtx.Service;

import com.wut.screencommontx.Model.CollectDataModel;
import com.wut.screencommontx.Util.MessagePrintUtil;
import com.wut.screencommontx.Util.ModelTransformUtil;
import com.wut.screendbtx.Model.Fiber;
import com.wut.screenmsgtx.Context.MsgRedisDataContext;
import com.wut.screenmsgtx.Context.MsgTaskParamContext;
import com.wut.screenmsgtx.Service.AsyncService.CollectDataAsyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.wut.screencommontx.Static.MsgModuleStatic.ASYNC_SERVICE_TIMEOUT;

@Component
public class CollectDataService {
    private final CollectDataAsyncService collectDataAsyncService;
    private final MsgRedisDataContext msgRedisDataContext;

    @Autowired
    public CollectDataService(CollectDataAsyncService collectDataAsyncService, MsgRedisDataContext msgRedisDataContext) {
        this.collectDataAsyncService = collectDataAsyncService;
        this.msgRedisDataContext = msgRedisDataContext;
    }

    public void collectDataAndUpdate(double timestamp) {
        msgRedisDataContext.deleteFiberData(timestamp);
        CollectDataModel entity = ModelTransformUtil.getCollectEntity(MsgTaskParamContext.TODAY_STR, timestamp);
//        MessagePrintUtil.printCollectDataAndUpdate();

        var fiberDbTask = collectDataAsyncService.collectFiberFromTime(entity);

        try {
            CompletableFuture.allOf(fiberDbTask).get(ASYNC_SERVICE_TIMEOUT,TimeUnit.SECONDS);
            collectDataAsyncService.removeExpireRadarData(timestamp).thenRunAsync(() -> {});
            updateCollectData(fiberDbTask.get());
        } catch (Exception e) { MessagePrintUtil.printException(e, "collectDataAndUpdate"); }
    }

    public void updateCollectData(List<Fiber> fiberData) {
        try {
            CompletableFuture.allOf(
                    collectDataAsyncService.storeCollectFiberData(fiberData)
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception e) { MessagePrintUtil.printException(e, "updateCollectData"); }
    }

}
