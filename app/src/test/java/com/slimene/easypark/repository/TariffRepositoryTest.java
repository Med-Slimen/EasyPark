package com.slimene.easypark.repository;

import org.junit.Test;
import static org.junit.Assert.*;

import com.slimene.easypark.utils.MoneyFormat;

public class TariffRepositoryTest {

    @Test
    public void testUnloadedTariffDefaults() {
        Tariff unloaded = Tariff.unloaded();
        assertTrue(unloaded.isLoaded());
        assertEquals(2000L, unloaded.getCarHourlyRateMillimes());
        assertEquals(1000L, unloaded.getMotoHourlyRateMillimes());
        assertEquals(2.0, unloaded.getCarHourlyRateDinars(), 0.001);
        assertEquals(1.0, unloaded.getMotoHourlyRateDinars(), 0.001);
    }

    @Test
    public void testLoadedTariffMillimesAndDinarsConversion() {
        Tariff tariff = new Tariff(2500L, 1200L, true);
        assertTrue(tariff.isLoaded());
        assertEquals(2500L, tariff.getCarHourlyRateMillimes());
        assertEquals(1200L, tariff.getMotoHourlyRateMillimes());
        assertEquals(2.5, tariff.getCarHourlyRateDinars(), 0.001);
        assertEquals(1.2, tariff.getMotoHourlyRateDinars(), 0.001);
    }

    @Test
    public void testTariffFormatting() {
        Tariff tariff = new Tariff(3000L, 1500L, true);
        assertEquals("3.000 DT", MoneyFormat.format(tariff.getCarHourlyRateMillimes()));
        assertEquals("1.500 DT", MoneyFormat.format(tariff.getMotoHourlyRateMillimes()));
    }

    @Test
    public void testTariffEqualsAndHashCode() {
        Tariff t1 = new Tariff(2000L, 1000L, true);
        Tariff t2 = new Tariff(2000L, 1000L, true);
        Tariff t3 = new Tariff(2500L, 1000L, true);

        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
        assertNotEquals(t1, t3);
    }
}
