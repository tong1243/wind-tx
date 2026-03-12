package com.wut.screendbtx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screencommontx.Model.CollectDataModel;
import com.wut.screendbtx.Model.Fiber;

import java.util.List;

public interface FiberService extends IService<Fiber> {
    public List<Fiber> collectFromToday(CollectDataModel param);

    public List<Fiber> collectFromTime(CollectDataModel param);

    public Fiber collectMinTimestamp(CollectDataModel param);

}
