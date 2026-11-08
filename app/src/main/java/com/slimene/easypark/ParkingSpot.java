package com.slimene.easypark;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParkingSpot {
    private String spotId;
    private String type; // "voiture" or "moto"
    private String status; // "libre", "reservee", "occupee"
    private String clientId;
    private String clientPlate;
    private long reservedAt;
    private long startTime;
    private String sortKey;

    private static final Pattern PATTERN = Pattern.compile("^([A-Za-z]+)(\\d+)$");

    public static String generateSortKey(String id) {
        if (id == null) return "";
        Matcher m = PATTERN.matcher(id.trim());
        if (m.matches()) {
            String prefix = m.group(1) != null ? m.group(1).toUpperCase(Locale.US) : "";
            try {
                String numStr = m.group(2);
                long num = numStr != null ? Long.parseLong(numStr) : 0;
                return String.format(Locale.US, "%s%05d", prefix, num);
            } catch (NumberFormatException ignored) {
            }
        }
        return id.trim().toUpperCase();
    }

    public ParkingSpot() {
    }

    public ParkingSpot(String spotId, String type, String status, String clientId, String clientPlate, long reservedAt, long startTime) {
        this.spotId = spotId;
        this.type = type != null ? type : "voiture";
        this.status = status;
        this.clientId = clientId;
        this.clientPlate = clientPlate;
        this.reservedAt = reservedAt;
        this.startTime = startTime;
        this.sortKey = generateSortKey(spotId);
    }

    public ParkingSpot(String spotId, String status, String clientId, String clientPlate, long reservedAt, long startTime) {
        this(spotId, "voiture", status, clientId, clientPlate, reservedAt, startTime);
    }

    public String getSpotId() {
        return spotId;
    }

    public void setSpotId(String spotId) {
        this.spotId = spotId;
        if (this.sortKey == null || this.sortKey.isEmpty()) {
            this.sortKey = generateSortKey(spotId);
        }
    }

    public String getType() {
        return type != null ? type : "voiture";
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientPlate() {
        return clientPlate != null ? clientPlate : "";
    }

    public void setClientPlate(String clientPlate) {
        this.clientPlate = clientPlate;
    }

    public long getReservedAt() {
        return reservedAt;
    }

    public void setReservedAt(long reservedAt) {
        this.reservedAt = reservedAt;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public String getSortKey() {
        if (sortKey == null || sortKey.isEmpty()) {
            return generateSortKey(spotId);
        }
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }
}
