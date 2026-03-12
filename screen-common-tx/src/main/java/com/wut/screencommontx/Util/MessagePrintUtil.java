package com.wut.screencommontx.Util;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class MessagePrintUtil {
    private static final Logger LOGGER = Logger.getLogger("ROOT");

    public static void printInitParam(String date, long start, long end) {
        LOGGER.info("[PREPARING INIT PARAMS] DATE: " + date);
        LOGGER.info("[PREPARING INIT PARAMS] TIME_START: " + start + " (" + DateParamParseUtil.getDateTimeStr(start) + ")");
        LOGGER.info("[PREPARING INIT PARAMS] TIME_END: " + end + " (" + DateParamParseUtil.getDateTimeStr(end) + ")");
    }

    public static void printProducerTransmit(String key, String data) {
        LOGGER.info("[TX PRODUCER " + key.toUpperCase() + " TRANSMITTED TO RX] " + data);
    }

    public static void printException(Exception e, String info) {
        LOGGER.warning("[CATCH EXCEPTION IN FUNCTION ---" + info + "--- ] " + ExceptionUtils.getStackTrace(e));
    }

    public static void printStartTask() {
        LOGGER.info("[STARTING DATA COLLECTION TASK]");
    }

    public static void printStartCollectDataFromTime() {
        LOGGER.info("[STARTING COLLECT DATA FROM TIME]");
    }

    public static void printCollectDataAndUpdate() {
        LOGGER.info("[COLLECT DATA AND UPDATE]");
    }

    public static void printCollectDataAndUpdateSuccess(String data) {
        LOGGER.info("[COLLECT DATA AND UPDATE SUCCESS] " + data);
    }
    public static void printFilterDataAndUpdateSuccess(String data) {
        LOGGER.info("[FILTER DATA AND UPDATE SUCCESS] " + data);
    }

    public static void printFilterCollectFiberData(String s) {
        LOGGER.info("[FILTER COLLECT FIBER DATA] " + s);
    }

    public static void printFailureToCollectDataAndUpdate() {

        LOGGER.warning("[FAILURE TO COLLECT DATA AND UPDATE Because FiberDataIsEmpty]");
    }

    public static void printCurrentTimestamp(long currentTimestamp) {
        LOGGER.info("[CURRENT TIMESTAMP]= " + currentTimestamp);
    }
}
