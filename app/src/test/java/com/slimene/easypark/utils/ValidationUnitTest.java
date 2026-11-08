package com.slimene.easypark.utils;

import org.junit.Test;
import static org.junit.Assert.*;

import com.slimene.easypark.ParkingSpot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class ValidationUnitTest {

    @Test
    public void testPlateNormalization() {
        String input = "  210  tu   4512  ";
        String normalized = input.trim().toUpperCase().replaceAll("\\s+", " ");
        assertEquals("210 TU 4512", normalized);
    }

    @Test
    public void testLuhnValidation() {
        // Standard valid Luhn card
        assertTrue(CardValidator.isValidLuhn("79927398713"));
        // Invalid card
        assertFalse(CardValidator.isValidLuhn("79927398714"));
    }

    @Test
    public void testExpiryValidation() {
        // Future date
        assertTrue(CardValidator.isValidExpiry("12/30"));
        // Past date
        assertFalse(CardValidator.isValidExpiry("01/20"));
        // Invalid format
        assertFalse(CardValidator.isValidExpiry("13/25"));
    }

    @Test
    public void testNaturalOrderComparatorSorting() {
        NaturalOrderComparator comparator = new NaturalOrderComparator();
        List<ParkingSpot> spots = new ArrayList<>();
        spots.add(new ParkingSpot("A10", "voiture", "libre", "", "", 0, 0));
        spots.add(new ParkingSpot("A2", "voiture", "libre", "", "", 0, 0));
        spots.add(new ParkingSpot("A1", "voiture", "libre", "", "", 0, 0));
        spots.add(new ParkingSpot("B1", "voiture", "libre", "", "", 0, 0));

        Collections.sort(spots, comparator);

        assertEquals("A1", spots.get(0).getSpotId());
        assertEquals("A2", spots.get(1).getSpotId());
        assertEquals("A10", spots.get(2).getSpotId());
        assertEquals("B1", spots.get(3).getSpotId());
    }

    @Test
    public void testStartOfDayHelper() {
        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.JANUARY, 15, 14, 30, 45);
        long midDayMillis = cal.getTimeInMillis();

        long startOfDay = DateUtils.getStartOfDayMillis(midDayMillis);

        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(startOfDay);

        assertEquals(2025, startCal.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, startCal.get(Calendar.MONTH));
        assertEquals(15, startCal.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, startCal.get(Calendar.MINUTE));
        assertEquals(0, startCal.get(Calendar.SECOND));
        assertEquals(0, startCal.get(Calendar.MILLISECOND));
    }
}
