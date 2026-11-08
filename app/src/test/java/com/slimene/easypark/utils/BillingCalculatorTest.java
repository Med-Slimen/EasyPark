package com.slimene.easypark.utils;

import org.junit.Test;
import static org.junit.Assert.*;
import java.text.ParseException;
import com.slimene.easypark.AppConfig;

public class BillingCalculatorTest {

    private static final long RATE_MOTO = 1000L; // 1.000 DT
    private static final long RATE_CAR = 2000L;  // 2.000 DT

    @Test
    public void testAppConfigConstants() {
        assertEquals(15, AppConfig.MAX_RESERVATION_MINUTES);
        assertEquals(0, AppConfig.GRACE_PERIOD_MINUTES);
    }

    @Test
    public void testCompute_0min() {
        BillingCalculator.BillingResult result = BillingCalculator.compute(0, 0, RATE_CAR);
        assertEquals(0, result.durationMinutes);
        assertEquals(1, result.billedHours);
        assertEquals(2000L, result.amountMillimes);
    }

    @Test
    public void testCompute_0min_withGrace() {
        BillingCalculator.BillingResult result = BillingCalculator.compute(0, 0, RATE_CAR, 5);
        assertEquals(0, result.durationMinutes);
        assertEquals(0, result.billedHours);
        assertEquals(0L, result.amountMillimes);
    }

    @Test
    public void testCompute_1min() {
        BillingCalculator.BillingResult result = BillingCalculator.compute(0, 60000, RATE_CAR);
        assertEquals(1, result.durationMinutes);
        assertEquals(1, result.billedHours);
        assertEquals(2000L, result.amountMillimes);
    }

    @Test
    public void testCompute_59min() {
        BillingCalculator.BillingResult result = BillingCalculator.compute(0, 59 * 60000, RATE_CAR);
        assertEquals(59, result.durationMinutes);
        assertEquals(1, result.billedHours);
        assertEquals(2000L, result.amountMillimes);
    }

    @Test
    public void testCompute_60min() {
        BillingCalculator.BillingResult result = BillingCalculator.compute(0, 60 * 60000, RATE_CAR);
        assertEquals(60, result.durationMinutes);
        assertEquals(1, result.billedHours);
        assertEquals(2000L, result.amountMillimes);
    }

    @Test
    public void testCompute_61min() {
        BillingCalculator.BillingResult result = BillingCalculator.compute(0, 61 * 60000, RATE_CAR);
        assertEquals(61, result.durationMinutes);
        assertEquals(2, result.billedHours);
        assertEquals(4000L, result.amountMillimes);
    }

    @Test
    public void testCompute_125min() {
        BillingCalculator.BillingResult result = BillingCalculator.compute(0, 125 * 60000, RATE_MOTO);
        assertEquals(125, result.durationMinutes);
        assertEquals(3, result.billedHours); // 125/60 = 2.08 -> 3 hours
        assertEquals(3000L, result.amountMillimes);
    }

    @Test
    public void testMoneyFormat() {
        assertEquals("12.500 DT", MoneyFormat.format(12500));
        assertEquals("0.000 DT", MoneyFormat.format(0));
        assertEquals("1.000 DT", MoneyFormat.format(1000));
    }

    @Test
    public void testParseDT() throws ParseException {
        assertEquals(2500L, MoneyFormat.parseDT("2.5"));
        assertEquals(2500L, MoneyFormat.parseDT("2,5"));
        assertEquals(2500L, MoneyFormat.parseDT("2.5 DT"));
        assertEquals(2500L, MoneyFormat.parseDT("2,5 DT"));
        assertEquals(12500L, MoneyFormat.parseDT("12,500"));
        assertEquals(0L, MoneyFormat.parseDT(""));
    }
}
