package com.wut.screenmsgtx.Service;

import com.wut.screencommontx.Util.MessagePrintUtil;
import com.wut.screendbtx.Model.Fiber;
import com.wut.screenmsgtx.Service.AsyncService.TransmitDataAsyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.wut.screencommontx.Static.MsgModuleStatic.ASYNC_SERVICE_TIMEOUT;

@Component
public class TransmitDataService {
    private final TransmitDataAsyncService transmitDataAsyncService;

    @Autowired
    public TransmitDataService(TransmitDataAsyncService transmitDataAsyncService) {
        this.transmitDataAsyncService = transmitDataAsyncService;
    }

    public void filterDataAndSend(double timestamp) {
//        MessagePrintUtil.printCollectDataAndUpdate();

        var fiberFilterTask = transmitDataAsyncService.filterCollectFiberData(timestamp);

        try {
            CompletableFuture.allOf(fiberFilterTask).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
            sendFilterData(fiberFilterTask.get(), timestamp);
//            MessagePrintUtil.printFilterDataAndUpdateSuccess(fiberFilterTask.get().toString());
        } catch (Exception e) { MessagePrintUtil.printException(e, "filterDataAndSend"); }
    }

    public void sendFilterData( List<Fiber> fiberData, double timestamp) {
        try {
            CompletableFuture.allOf(
                    transmitDataAsyncService.transmitFiberData(fiberData, timestamp)
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
            transmitDataAsyncService.transmitTimestamp(timestamp);
        } catch (Exception e) { MessagePrintUtil.printException(e, "sendFilterData"); }
    }

}
