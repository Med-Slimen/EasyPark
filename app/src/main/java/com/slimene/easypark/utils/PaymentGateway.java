package com.slimene.easypark.utils;

public interface PaymentGateway {
    interface PaymentCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    void processPayment(String name, String cardNumber, String expiry, String cvv, double amount, PaymentCallback callback);
}
