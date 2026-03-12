package com.wut.screendbtx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screencommontx.Model.CollectDataModel;
import com.wut.screendbtx.Context.TableTimeContext;
import com.wut.screendbtx.Mapper.FiberMapper;
import com.wut.screendbtx.Model.Fiber;
import com.wut.screendbtx.Service.FiberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wut.screencommontx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;

@Service
public class FiberServiceImpl extends ServiceImpl<FiberMapper, Fiber> implements FiberService {
    private final FiberMapper fiberMapper;

    @Autowired
    public FiberServiceImpl(FiberMapper fiberMapper) {
        this.fiberMapper = fiberMapper;
    }

    @Override
    public List<Fiber> collectFromToday(CollectDataModel param) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, param.getTableName());
        LambdaQueryWrapper<Fiber> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Fiber::getTimestamp);
        return fiberMapper.selectList(wrapper);
    }

    @Override
    public List<Fiber> collectFromTime(CollectDataModel param) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, param.getTableName());
        LambdaQueryWrapper<Fiber> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Fiber::getTimestamp)
                .between(Fiber::getTimestamp, param.getStart(), param.getEnd());
        return fiberMapper.selectList(wrapper);
    }

    @Override
    public Fiber collectMinTimestamp(CollectDataModel param) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, param.getTableName());
        LambdaQueryWrapper<Fiber> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Fiber::getTimestamp)
                .isNotNull(Fiber::getTimestamp)
                .last("LIMIT 1");
        return fiberMapper.selectOne(wrapper);
    }
}
