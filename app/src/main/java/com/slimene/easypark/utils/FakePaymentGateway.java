package com.slimene.easypark.utils;

public class FakePaymentGateway implements PaymentGateway {
    @Override
    public void processPayment(String name, String cardNumber, String expiry, String cvv, double amount, PaymentCallback callback) {
        // Simulated instant payment processing
        callback.onSuccess();
    }
}
