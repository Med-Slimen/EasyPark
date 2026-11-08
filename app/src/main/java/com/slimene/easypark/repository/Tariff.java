package com.slimene.easypark.repository;

import java.util.Objects;

public class Tariff {
    private final long carHourlyRateMillimes;
    private final long motoHourlyRateMillimes;
    private final boolean loaded;

    public Tariff(long carHourlyRateMillimes, long motoHourlyRateMillimes, boolean loaded) {
        this.carHourlyRateMillimes = carHourlyRateMillimes;
        this.motoHourlyRateMillimes = motoHourlyRateMillimes;
        this.loaded = loaded;
    }

    public static Tariff defaultTariff() {
        return new Tariff(2000L, 1000L, true);
    }

    public static Tariff unloaded() {
        return defaultTariff();
    }

    public long getCarHourlyRateMillimes() {
        return carHourlyRateMillimes;
    }

    public long getMotoHourlyRateMillimes() {
        return motoHourlyRateMillimes;
    }

    public double getCarHourlyRateDinars() {
        return carHourlyRateMillimes / 1000.0;
    }

    public double getMotoHourlyRateDinars() {
        return motoHourlyRateMillimes / 1000.0;
    }

    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tariff tariff = (Tariff) o;
        return carHourlyRateMillimes == tariff.carHourlyRateMillimes &&
                motoHourlyRateMillimes == tariff.motoHourlyRateMillimes &&
                loaded == tariff.loaded;
    }

    @Override
    public int hashCode() {
        return Objects.hash(carHourlyRateMillimes, motoHourlyRateMillimes, loaded);
    }
}
