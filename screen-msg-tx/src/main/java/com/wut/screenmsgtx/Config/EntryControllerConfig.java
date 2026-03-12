package com.wut.screenmsgtx.Config;

import com.wut.screencommontx.Http.DateTimeOrderReq;
import com.wut.screencommontx.Http.DefaultMsgResp;
import com.wut.screencommontx.Util.ModelTransformUtil;
import com.wut.screenmsgtx.Context.MsgTaskControlContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class EntryControllerConfig {
    private final MsgTaskControlContext msgTaskControlContext;

    @Autowired
    public EntryControllerConfig(MsgTaskControlContext msgTaskControlContext) {
        this.msgTaskControlContext = msgTaskControlContext;
    }

    @GetMapping("/connect")
    public DefaultMsgResp initConnect() {
        return ModelTransformUtil.getDefaultMsgRespInstance();
    }

    @PostMapping("/data/start")
    public DefaultMsgResp msgDataTaskStart(@RequestBody DateTimeOrderReq req) {
        msgTaskControlContext.controlStartMsgTask(req);
        return ModelTransformUtil.getDefaultMsgRespInstance();
    }

    @GetMapping("/data/end")
    public DefaultMsgResp msgDataTaskEnd() {
        msgTaskControlContext.controlEndMsgTask();
        return ModelTransformUtil.getDefaultMsgRespInstance();
    }

    @GetMapping("/data/direct")
    public DefaultMsgResp msgDataTaskDirect() {
        return ModelTransformUtil.getDefaultMsgRespInstance();
    }

}
