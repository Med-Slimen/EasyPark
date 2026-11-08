package com.slimene.easypark.utils;

import java.util.Locale;
import java.text.NumberFormat;
import java.text.ParseException;

public class MoneyFormat {
    public static String format(long millimes) {
        double dinars = millimes / 1000.0;
        return String.format(Locale.US, "%.3f DT", dinars);
    }
    
    public static long parseDT(String dtStr) throws ParseException {
        if (dtStr == null || dtStr.trim().isEmpty()) {
            return 0;
        }
        String cleanStr = dtStr.replace("DT", "").trim();
        cleanStr = cleanStr.replace(',', '.'); // fix decimal comma
        double dinars = Double.parseDouble(cleanStr);
        return Math.round(dinars * 1000.0);
    }
}
