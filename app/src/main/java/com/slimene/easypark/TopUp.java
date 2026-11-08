package com.slimene.easypark;

public class TopUp {
    private String topUpId;
    private String clientId;
    private double amount;
    private String cardLast4;
    private long timestamp;

    // Default empty constructor required for Firebase Firestore
    public TopUp() {
    }

    public TopUp(String topUpId, String clientId, double amount, String cardLast4, long timestamp) {
        this.topUpId = topUpId;
        this.clientId = clientId;
        this.amount = amount;
        this.cardLast4 = cardLast4;
        this.timestamp = timestamp;
    }

    public String getTopUpId() {
        return topUpId;
    }

    public void setTopUpId(String topUpId) {
        this.topUpId = topUpId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public void setCardLast4(String cardLast4) {
        this.cardLast4 = cardLast4;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
