package com.wut.screenmsgtx.Context;

import com.wut.screencommontx.Http.DateTimeOrderReq;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MsgTaskControlContext {
    private final AtomicBoolean active = new AtomicBoolean(true);

    public void controlStartMsgTask(DateTimeOrderReq req) {
        active.set(true);
    }

    public void controlEndMsgTask() {
        active.set(false);
    }

    public boolean isActive() {
        return active.get();
    }
}
