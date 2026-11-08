package com.slimene.easypark.utils;

public class BillingCalculator {

    public static class BillingResult {
        public final long durationMinutes;
        public final long billedHours;
        public final long amountMillimes;

        public BillingResult(long durationMinutes, long billedHours, long amountMillimes) {
            this.durationMinutes = durationMinutes;
            this.billedHours = billedHours;
            this.amountMillimes = amountMillimes;
        }
    }

    public static BillingResult compute(long entryMillis, long exitMillis, long hourlyRateMillimes, int gracePeriodMinutes) {
        long durationMillis = Math.max(0, exitMillis - entryMillis);
        long durationMinutes = (long) Math.ceil(durationMillis / (1000.0 * 60.0));

        long billedHours;
        if (gracePeriodMinutes > 0 && durationMinutes <= gracePeriodMinutes) {
            billedHours = 0;
        } else {
            billedHours = Math.max(1, (long) Math.ceil((double) durationMinutes / 60.0));
        }

        long amountMillimes = billedHours * hourlyRateMillimes;

        return new BillingResult(durationMinutes, billedHours, amountMillimes);
    }

    public static BillingResult compute(long entryMillis, long exitMillis, long hourlyRateMillimes) {
        return compute(entryMillis, exitMillis, hourlyRateMillimes, 0);
    }
}
