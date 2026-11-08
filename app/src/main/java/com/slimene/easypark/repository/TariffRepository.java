package com.slimene.easypark.repository;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.Set;

public class TariffRepository {
    private static TariffRepository instance;

    public interface TariffObserver {
        void onTariffUpdated(Tariff tariff);
    }

    private Tariff currentTariff = Tariff.defaultTariff();
    private ListenerRegistration listenerRegistration;
    private final Set<TariffObserver> observers = new HashSet<>();

    private TariffRepository() {}

    public static synchronized TariffRepository getInstance() {
        if (instance == null) {
            instance = new TariffRepository();
        }
        return instance;
    }

    public synchronized void addObserver(TariffObserver observer) {
        if (observer == null) return;
        observers.add(observer);

        if (observers.size() == 1 && listenerRegistration == null) {
            startFirestoreListener();
        }

        observer.onTariffUpdated(currentTariff);
    }

    public synchronized void removeObserver(TariffObserver observer) {
        if (observer == null) return;
        observers.remove(observer);

        if (observers.isEmpty() && listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }

    public void startListening() {}
    public void stopListening() {}

    public Tariff getCurrentTariff() {
        return currentTariff;
    }

    private void startFirestoreListener() {
        listenerRegistration = FirebaseFirestore.getInstance().collection("config").document("tarif")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        updateTariff(Tariff.defaultTariff());
                        return;
                    }
                    Tariff newTariff = parseTariffSnapshot(snapshot);
                    updateTariff(newTariff);
                });
    }

    public static Tariff parseTariffSnapshot(DocumentSnapshot snapshot) {
        long carMillimes = 2000L;
        long motoMillimes = 1000L;

        if (snapshot != null && snapshot.exists()) {
            Long carM = snapshot.getLong("carHourlyRateMillimes");
            if (carM != null) {
                carMillimes = carM;
            } else {
                Double car = snapshot.getDouble("carHourlyRate");
                if (car == null) car = snapshot.getDouble("hourlyRate");
                if (car != null) {
                    carMillimes = Math.round(car * 1000.0);
                }
            }

            Long motoM = snapshot.getLong("motoHourlyRateMillimes");
            if (motoM != null) {
                motoMillimes = motoM;
            } else {
                Double moto = snapshot.getDouble("motoHourlyRate");
                if (moto != null) {
                    motoMillimes = Math.round(moto * 1000.0);
                }
            }
        }

        return new Tariff(carMillimes, motoMillimes, true);
    }

    private synchronized void updateTariff(Tariff newTariff) {
        this.currentTariff = newTariff;
        for (TariffObserver observer : new HashSet<>(observers)) {
            observer.onTariffUpdated(newTariff);
        }
    }
}
