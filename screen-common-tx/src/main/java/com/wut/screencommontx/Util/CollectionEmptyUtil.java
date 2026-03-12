package com.wut.screencommontx.Util;

import java.util.List;

public class CollectionEmptyUtil {
    public static <T> Boolean forList(List<T> list) {
        return list == null || list.isEmpty();
    }
}
