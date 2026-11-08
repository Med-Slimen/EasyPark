package com.slimene.easypark.utils;

import com.slimene.easypark.ParkingSpot;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NaturalOrderComparator implements Comparator<ParkingSpot> {

    private static final Pattern PATTERN = Pattern.compile("^([A-Za-z]+)(\\d+)$");

    @Override
    public int compare(ParkingSpot spot1, ParkingSpot spot2) {
        String id1 = spot1 != null && spot1.getSpotId() != null ? spot1.getSpotId() : "";
        String id2 = spot2 != null && spot2.getSpotId() != null ? spot2.getSpotId() : "";

        Matcher m1 = PATTERN.matcher(id1);
        Matcher m2 = PATTERN.matcher(id2);

        if (m1.matches() && m2.matches()) {
            String prefix1 = m1.group(1);
            String prefix2 = m2.group(1);

            int prefixCompare = prefix1.compareToIgnoreCase(prefix2);
            if (prefixCompare != 0) {
                return prefixCompare;
            }

            try {
                long num1 = Long.parseLong(m1.group(2));
                long num2 = Long.parseLong(m2.group(2));
                return Long.compare(num1, num2);
            } catch (NumberFormatException ignored) {
            }
        }

        return id1.compareToIgnoreCase(id2);
    }
}
