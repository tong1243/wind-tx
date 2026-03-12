package com.wut.screenmsgtx.Context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.wut.screencommontx.Static.MsgModuleStatic.*;

@Component
public class MsgRedisDataContext {
    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public MsgRedisDataContext(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void resetDataContext() {
        stringRedisTemplate.delete(REDIS_KEY_FIBER_DATA);
    }
    public void deleteFiberData(double timestamp) {
        stringRedisTemplate.opsForZSet().removeRangeByScore(REDIS_KEY_FIBER_DATA, timestamp - 120000, timestamp - 60000);
    }


    public boolean isFiberDataEmpty() {
        return Boolean.FALSE.equals(stringRedisTemplate.hasKey(REDIS_KEY_FIBER_DATA)) || Objects.requireNonNull(stringRedisTemplate.opsForZSet().size(REDIS_KEY_FIBER_DATA)) == 0;
    }


}
