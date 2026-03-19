package com.wut.screenmsgtx.Context;

import com.wut.screencommontx.Http.DateTimeOrderReq;
import com.wut.screencommontx.Util.DateParamParseUtil;
import com.wut.screenmsgtx.Service.CollectDataService;
import com.wut.screenmsgtx.Service.TransmitDataService;
import com.wut.screenmsgtx.Task.CollectDataTask;
import com.wut.screenmsgtx.Task.TransmitDataTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MsgTaskControlContext {
    @Value("${msg.udp.enabled:false}")
    private boolean udpRealtimeEnabled;

    private final MsgTaskParamContext msgTaskParamContext;
    private final MsgRedisDataContext msgRedisDataContext;
    private final CollectDataTask collectDataTask;
    private final TransmitDataTask transmitDataTask;
    private final CollectDataService collectDataService;
    private final TransmitDataService transmitDataService;

    @Autowired
    public MsgTaskControlContext(MsgTaskParamContext msgTaskParamContext, MsgRedisDataContext msgRedisDataContext, CollectDataTask collectDataTask, TransmitDataTask transmitDataTask, CollectDataService collectDataService, TransmitDataService transmitDataService) {
        this.msgTaskParamContext = msgTaskParamContext;
        this.msgRedisDataContext = msgRedisDataContext;
        this.collectDataTask = collectDataTask;
        this.transmitDataTask = transmitDataTask;
        this.collectDataService = collectDataService;
        this.transmitDataService = transmitDataService;
    }

    public void controlStartMsgTask(DateTimeOrderReq req) {
        if (udpRealtimeEnabled) {
            controlEndMsgTask();
            msgRedisDataContext.resetDataContext();
            return;
        }
        if (!DateParamParseUtil.isDateTimeOrderValid(req)) { return; }
        controlEndMsgTask();
        setInitParams(req);
        collectDataTask.startTask();
        transmitDataTask.startTask();
    }

    public void controlEndMsgTask() {
        collectDataTask.endTask();
        transmitDataTask.endTask();
    }

    public void setInitParams(DateTimeOrderReq req) {
        msgRedisDataContext.resetDataContext();
        msgTaskParamContext.resetTimeParam();
        // 没有指定时间戳时,默认获取当天的所有数据
        msgTaskParamContext.initTodayParam(req);
    }

}
