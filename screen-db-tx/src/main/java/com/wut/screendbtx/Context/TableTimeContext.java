package com.wut.screendbtx.Context;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;

import java.util.HashMap;
import java.util.Map;

public class TableTimeContext {
    private static final ThreadLocal<Map<String, String>> TABLE_TIME = new ThreadLocal<>();

    public static String getTime(String param) {
        Map<String, String> map = getTime();
        if (CollectionUtils.isNotEmpty(map)) {
            return map.get(param);
        }
        return null;
    }

    public static void setTime(String param, String value) {
        setTime(new HashMap<String, String>(){{
            put(param, value);
        }});
    }

    public static void setTime(Map<String, String> map) {
        TABLE_TIME.set(map);
    }

    public static Map<String, String> getTime() {
        return TABLE_TIME.get();
    }

    public static void clearTime() {
        TABLE_TIME.remove();
    }

}
