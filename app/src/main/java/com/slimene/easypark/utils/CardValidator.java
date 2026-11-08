package com.slimene.easypark.utils;

import java.util.Calendar;

public class CardValidator {

    public static boolean isValidLuhn(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) return false;
        String clean = cardNumber.replaceAll("\\s+", "");
        if (!clean.matches("\\d+")) return false;

        int sum = 0;
        boolean alternate = false;
        for (int i = clean.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(clean.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    public static boolean isValidExpiry(String expiry) {
        if (expiry == null || !expiry.matches("^(0[1-9]|1[0-2])\\/([0-9]{2})$")) {
            return false;
        }
        String[] parts = expiry.split("/");
        int month = Integer.parseInt(parts[0]);
        int year = Integer.parseInt("20" + parts[1]);

        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        int currentMonth = cal.get(Calendar.MONTH) + 1; // 1-12

        if (year < currentYear) return false;
        if (year == currentYear && month < currentMonth) return false;
        return true;
    }

    public static boolean isAmex(String cardNumber) {
        if (cardNumber == null) return false;
        String clean = cardNumber.replaceAll("\\s+", "");
        return clean.startsWith("34") || clean.startsWith("37");
    }

    public static boolean isValidCvv(String cardNumber, String cvv) {
        if (cvv == null) return false;
        int expectedLength = isAmex(cardNumber) ? 4 : 3;
        return cvv.length() == expectedLength && cvv.matches("\\d+");
    }
}
