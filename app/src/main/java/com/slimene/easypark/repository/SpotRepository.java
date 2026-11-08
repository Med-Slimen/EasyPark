package com.slimene.easypark.repository;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;
import com.slimene.easypark.AppConfig;
import com.slimene.easypark.ParkingSpot;
import com.slimene.easypark.utils.NaturalOrderComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpotRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface SpotsCallback {
        void onSpotsLoaded(List<ParkingSpot> spots);
        void onError(Exception e);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public ListenerRegistration listenToSpots(SpotsCallback callback) {
        // Load all spots with plain snapshot listener (no orderBy), then sort client-side using NaturalOrderComparator
        return db.collection("spots")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snapshot != null) {
                        List<ParkingSpot> list = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            ParkingSpot spot = doc.toObject(ParkingSpot.class);
                            if (spot != null) {
                                list.add(spot);
                            }
                        }
                        Collections.sort(list, new NaturalOrderComparator());
                        callback.onSpotsLoaded(list);
                    }
                });
    }

    public void addSpot(String spotId, String type, ActionCallback callback) {
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference spotRef = db.collection("spots").document(spotId);
            DocumentSnapshot spotSnap = transaction.get(spotRef);

            if (spotSnap.exists()) {
                throw new FirebaseFirestoreException("La place " + spotId + " existe déjà.", FirebaseFirestoreException.Code.ALREADY_EXISTS);
            }

            ParkingSpot newSpot = new ParkingSpot(spotId, type, "libre", "", "", 0, 0);
            transaction.set(spotRef, newSpot);
            return null;
        }).addOnSuccessListener(aVoid -> callback.onSuccess())
          .addOnFailureListener(callback::onError);
    }

    public void deleteSpot(String spotId, ActionCallback callback) {
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference spotRef = db.collection("spots").document(spotId);
            DocumentSnapshot spotSnap = transaction.get(spotRef);

            String status = spotSnap.getString("status");
            Long reservedAt = spotSnap.getLong("reservedAt");
            boolean isExpired = false;

            if ("reservee".equalsIgnoreCase(status) && reservedAt != null) {
                long maxDurationMillis = AppConfig.MAX_RESERVATION_MINUTES * 60 * 1000L;
                if (reservedAt > 0 && System.currentTimeMillis() - reservedAt > maxDurationMillis) {
                    isExpired = true;
                }
            }

            if (!"libre".equalsIgnoreCase(status) && !isExpired) {
                throw new FirebaseFirestoreException("Impossible de supprimer une place non libre.", FirebaseFirestoreException.Code.ABORTED);
            }

            transaction.delete(spotRef);
            return null;
        }).addOnSuccessListener(aVoid -> callback.onSuccess())
          .addOnFailureListener(callback::onError);
    }

    public void backfillSortKeys(ActionCallback callback) {
        db.collection("spots").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<DocumentSnapshot> docs = queryDocumentSnapshots.getDocuments();
                    if (docs.isEmpty()) {
                        callback.onSuccess();
                        return;
                    }
                    commitBackfillBatch(docs, 0, 400, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    private void commitBackfillBatch(List<DocumentSnapshot> docs, int startIndex, int batchSize, ActionCallback callback) {
        if (startIndex >= docs.size()) {
            callback.onSuccess();
            return;
        }
        int endIndex = Math.min(startIndex + batchSize, docs.size());
        WriteBatch batch = db.batch();
        for (int i = startIndex; i < endIndex; i++) {
            DocumentSnapshot doc = docs.get(i);
            String spotId = doc.getId();
            String sortKey = ParkingSpot.generateSortKey(spotId);
            Map<String, Object> map = new HashMap<>();
            map.put("sortKey", sortKey);
            batch.set(doc.getReference(), map, SetOptions.merge());
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> commitBackfillBatch(docs, endIndex, batchSize, callback))
                .addOnFailureListener(callback::onError);
    }
}
