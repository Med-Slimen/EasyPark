package com.slimene.easypark.utils;

import java.util.Calendar;

public class DateUtils {

    public static long getStartOfTodayMillis() {
        return getStartOfDayMillis(System.currentTimeMillis());
    }

    public static long getStartOfDayMillis(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
