package com.wut.screencommontx.Util;

import com.wut.screencommontx.Http.DefaultMsgResp;
import com.wut.screencommontx.Model.CollectDataModel;

import static com.wut.screencommontx.Static.MsgModuleStatic.COLLECT_COND_INTERVAL;

public class ModelTransformUtil {
    public static DefaultMsgResp getDefaultMsgRespInstance() {
        return new DefaultMsgResp(true, 200, null);
    }

    public static CollectDataModel getCollectEntity(String date, double timestamp) {
        return new CollectDataModel(date, timestamp - COLLECT_COND_INTERVAL + 1, timestamp);
    }

}
