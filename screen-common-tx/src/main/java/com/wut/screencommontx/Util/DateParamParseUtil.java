package com.wut.screencommontx.Util;

import com.wut.screencommontx.Http.DateTimeOrderReq;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;

import static com.wut.screencommontx.Static.MsgModuleStatic.TRANSMIT_COND_INTERVAL;

public class DateParamParseUtil {
    // 日期和时间戳参数不能同时为空
    public static boolean isDateTimeOrderValid(DateTimeOrderReq req) {
        return !((req.getToday() == null || Objects.equals(req.getToday(), ""))
                && req.getTime() == 0L);
    }

    // 毫秒级时间戳转日期格式化字符串(yyyyMMdd)
    public static String getDateStr(long timestamp) {
        LocalDateTime datetime = new Date(timestamp).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        String monthStr = datetime.getMonthValue() < 10 ? "0" + datetime.getMonthValue() : Integer.toString(datetime.getMonthValue());
        String dayStr = datetime.getDayOfMonth() < 10 ? "0" + datetime.getDayOfMonth() : Integer.toString(datetime.getDayOfMonth());
        return datetime.getYear() + monthStr + dayStr;
    }

    public static String getDateTimeStr(long timestamp) {
        LocalDateTime datetime = new Date(timestamp).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return datetime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }


    // 日期格式化字符串(yyyyMMdd)转当天起始毫秒级时间戳(对应00:00:00)
    public static long getStartTimestamp(String date) {
        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
        LocalDateTime startOfDay = localDate.atStartOfDay();
        ZonedDateTime zonedDateTime = startOfDay.atZone(ZoneId.systemDefault());
        return zonedDateTime.toInstant().toEpochMilli();
    }

    // 格式化传送的时间戳(查询时间单位200ms),普遍的规则是设为上一轮的时间戳
    // 如果时间戳是200的倍数,发送的数据中最小时间戳就是它本身
    // 如果时间戳不是200的倍数,发送的数据中最小时间戳是它的下一个单位
    public static long getRoundTimestamp(long timestamp) {
        long parseTimestamp = Double.valueOf(timestamp).longValue();
        long last = parseTimestamp % TRANSMIT_COND_INTERVAL;
        return last == 0 ? parseTimestamp : parseTimestamp - last;
    }

    // 日期格式化字符串(yyyyMMdd)转当天结束毫秒级时间戳(对应24:00:00)
    public static long getEndTimestamp(String date) {
        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd")).plusDays(1);
        LocalDateTime startOfNextDay = localDate.atStartOfDay();
        ZonedDateTime zonedDateTime = startOfNextDay.atZone(ZoneId.systemDefault());
        return zonedDateTime.toInstant().toEpochMilli();
    }

}
