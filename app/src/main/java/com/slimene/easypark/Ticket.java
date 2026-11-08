package com.slimene.easypark;

public class Ticket {
    private String ticketId;
    private String spotId;
    private String vehicleType; // "voiture" or "moto"
    private String clientId;
    private String clientPlate;
    private String clientName;
    private String clientEmail;
    
    // Legacy fields (kept for backward compatibility reading)
    private long durationMinutes;
    private double totalAmount;
    private long timestamp;
    
    // New exact fields
    private Long entryTime;
    private Long exitTime;
    private Long billedHours;
    private Long amountMillimes;
    private String processedBy;
    private String source;

    public Ticket() {
    }

    // New comprehensive constructor
    public Ticket(String ticketId, String spotId, String vehicleType, String clientId, String clientPlate, 
                  String clientName, String clientEmail, Long entryTime, Long exitTime, Long billedHours, 
                  Long amountMillimes, String processedBy, String source) {
        this.ticketId = ticketId;
        this.spotId = spotId;
        this.vehicleType = vehicleType != null ? vehicleType : "voiture";
        this.clientId = clientId;
        this.clientPlate = clientPlate;
        this.clientName = clientName;
        this.clientEmail = clientEmail;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.billedHours = billedHours;
        this.amountMillimes = amountMillimes;
        this.processedBy = processedBy;
        this.source = source;
        
        // Populate legacy fields for old versions of the app reading this data
        this.timestamp = exitTime != null ? exitTime : System.currentTimeMillis();
        this.totalAmount = amountMillimes != null ? amountMillimes / 1000.0 : 0.0;
        if (entryTime != null && exitTime != null) {
            this.durationMinutes = (exitTime - entryTime) / 60000;
        } else {
            this.durationMinutes = billedHours != null ? billedHours * 60 : 0;
        }
    }

    // Legacy constructors for compatibility
    public Ticket(String ticketId, String spotId, String vehicleType, String clientId, String clientPlate, String clientName, String clientEmail, long durationMinutes, double totalAmount, long timestamp) {
        this.ticketId = ticketId;
        this.spotId = spotId;
        this.vehicleType = vehicleType != null ? vehicleType : "voiture";
        this.clientId = clientId;
        this.clientPlate = clientPlate;
        this.clientName = clientName;
        this.clientEmail = clientEmail;
        this.durationMinutes = durationMinutes;
        this.totalAmount = totalAmount;
        this.timestamp = timestamp;
    }

    public Ticket(String ticketId, String spotId, String vehicleType, String clientId, String clientPlate, long durationMinutes, double totalAmount, long timestamp) {
        this(ticketId, spotId, vehicleType, clientId, clientPlate, "", "", durationMinutes, totalAmount, timestamp);
    }

    public Ticket(String ticketId, String spotId, String clientId, String clientPlate, long durationMinutes, double totalAmount, long timestamp) {
        this(ticketId, spotId, "voiture", clientId, clientPlate, "", "", durationMinutes, totalAmount, timestamp);
    }

    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public String getSpotId() { return spotId; }
    public void setSpotId(String spotId) { this.spotId = spotId; }

    public String getVehicleType() { return vehicleType != null ? vehicleType : "voiture"; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientPlate() { return clientPlate != null ? clientPlate : ""; }
    public void setClientPlate(String clientPlate) { this.clientPlate = clientPlate; }

    public String getClientName() { return clientName != null ? clientName : ""; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getClientEmail() { return clientEmail != null ? clientEmail : ""; }
    public void setClientEmail(String clientEmail) { this.clientEmail = clientEmail; }

    // Legacy getters and setters
    public long getDurationMinutes() { 
        if (entryTime != null && exitTime != null) {
            return (exitTime - entryTime) / 60000;
        }
        return durationMinutes; 
    }
    public void setDurationMinutes(long durationMinutes) { this.durationMinutes = durationMinutes; }

    public double getTotalAmount() { 
        if (amountMillimes != null) {
            return amountMillimes / 1000.0;
        }
        return totalAmount; 
    }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public long getTimestamp() { 
        if (exitTime != null) {
            return exitTime;
        }
        return timestamp; 
    }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    // New getters and setters
    public Long getEntryTime() { return entryTime; }
    public void setEntryTime(Long entryTime) { this.entryTime = entryTime; }

    public Long getExitTime() { return exitTime; }
    public void setExitTime(Long exitTime) { this.exitTime = exitTime; }

    public Long getBilledHours() { return billedHours; }
    public void setBilledHours(Long billedHours) { this.billedHours = billedHours; }

    public Long getAmountMillimes() { return amountMillimes; }
    public void setAmountMillimes(Long amountMillimes) { this.amountMillimes = amountMillimes; }

    public String getProcessedBy() { return processedBy; }
    public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
