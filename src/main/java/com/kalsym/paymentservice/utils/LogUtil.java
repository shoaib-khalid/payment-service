package com.kalsym.paymentservice.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Sarosh
 */
public class LogUtil {

    private static final Logger application = LoggerFactory.getLogger("application");

    public static void info(String prefix, String location, String message, String postfix) {
        application.info(prefix + " " + location + " " + message + " " + postfix + " ");
    }

    public static void warn(String prefix, String location, String message, String postfix) {
        application.warn(prefix + " " + location + " " + message + " " + postfix + " ");
    }

    public static void error(String prefix, String location, String message, String postfix, Exception e) {
        application.error(prefix + " " + location + " " + message + " " + postfix, e);
    }
}
