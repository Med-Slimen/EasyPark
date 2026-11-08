package com.slimene.easypark;

public class Vehicle {
    private String vehicleId;
    private String clientId;
    private String plate;
    private String brand;
    private String type; // "voiture" or "moto"

    // Default empty constructor required for Firebase Firestore
    public Vehicle() {
    }

    public Vehicle(String vehicleId, String clientId, String plate, String brand, String type) {
        this.vehicleId = vehicleId;
        this.clientId = clientId;
        this.plate = plate;
        this.brand = brand;
        this.type = type;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
