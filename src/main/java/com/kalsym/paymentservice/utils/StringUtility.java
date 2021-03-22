/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package com.kalsym.paymentservice.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

/**
 *
 * @author user
 */
public class StringUtility {
     /**
     * Generates a 16 character unique hex terminated and prefix appended Random
     * UUID
     *
     * @param appInitials prefix to be appended
     * @return
     */
    public static String CreateRefID(String appInitials) {
        int min = 4097; // hex equivalant 1001
        int max = 65534; // hex equivalant fffe

        Random r = new Random();
        int decRand = r.nextInt(max - min + 1) + min;
        //    System.err.println(decRand);
        String hexRand = Integer.toHexString(decRand);
        // System.out.println(hexRand);
        String Prefix = appInitials;

        DateFormat dateFormat = new SimpleDateFormat("ddMMyyHHmmss");
        // get current date time with Date()
        Date date = new Date();

        // get current date time with Calendar()
        Calendar cal = Calendar.getInstance();

        String dateStr = dateFormat.format(cal.getTime());
        String refID = Prefix + dateStr + hexRand;
        return refID;
    }

}
