package com.slimene.easypark.repository;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;
import com.slimene.easypark.Ticket;
import com.slimene.easypark.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatsRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public static String getTodayKey() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
        return sdf.format(new Date());
    }

    public static String getDayKey(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
        return sdf.format(new Date(timestamp));
    }

    public static void incrementRevenueInTransaction(Transaction transaction, FirebaseFirestore db, long amountMillimes, long timestamp) {
        double amountDinars = amountMillimes / 1000.0;
        
        DocumentReference totalRef = db.collection("stats").document("total");
        transaction.set(totalRef, createIncrementMap(amountDinars, amountMillimes), SetOptions.merge());

        String dayKey = getDayKey(timestamp);
        DocumentReference dailyRef = db.collection("stats").document("daily_" + dayKey);
        transaction.set(dailyRef, createIncrementMap(amountDinars, amountMillimes), SetOptions.merge());
    }

    private static Map<String, Object> createIncrementMap(double amountDinars, long amountMillimes) {
        Map<String, Object> map = new HashMap<>();
        map.put("totalRevenue", FieldValue.increment(amountDinars));
        map.put("totalRevenueMillimes", FieldValue.increment(amountMillimes));
        map.put("ticketCount", FieldValue.increment(1));
        return map;
    }

    public interface RevenueCallback {
        void onRevenueLoaded(double totalDinars, long totalMillimes);
    }

    public interface ErrorCallback {
        void onError(Exception e);
    }

    public ListenerRegistration listenTodayRevenue(RevenueCallback callback) {
        String dayKey = getTodayKey();
        return db.collection("stats").document("daily_" + dayKey)
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null && snapshot.exists()) {
                        Double dinars = snapshot.getDouble("totalRevenue");
                        Long millimes = snapshot.getLong("totalRevenueMillimes");
                        double d = dinars != null ? dinars : 0.0;
                        long m = millimes != null ? millimes : Math.round(d * 1000.0);
                        if (m > 0 || d > 0) {
                            callback.onRevenueLoaded(d, m);
                            return;
                        }
                    }
                    // Fallback: query tickets created/closed today (timestamp >= start of today)
                    long startOfToday = DateUtils.getStartOfTodayMillis();
                    db.collection("tickets")
                            .whereGreaterThanOrEqualTo("timestamp", startOfToday)
                            .get()
                            .addOnSuccessListener(querySnapshot -> {
                                long sumMillimes = 0;
                                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                    Ticket t = doc.toObject(Ticket.class);
                                    if (t != null) {
                                        long m = t.getAmountMillimes() != null ? t.getAmountMillimes() : Math.round(t.getTotalAmount() * 1000.0);
                                        sumMillimes += m;
                                    }
                                }
                                callback.onRevenueLoaded(sumMillimes / 1000.0, sumMillimes);
                            })
                            .addOnFailureListener(e -> callback.onRevenueLoaded(0.0, 0L));
                });
    }

    public interface BackfillCallback {
        void onSuccess(int ticketsProcessed);
        void onFailure(Exception e);
    }

    public void backfillStats(BackfillCallback callback) {
        db.collection("tickets").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<DocumentSnapshot> docs = queryDocumentSnapshots.getDocuments();
                    Map<String, Long> dailyMillimes = new HashMap<>();
                    long totalMillimes = 0;
                    int count = 0;

                    for (DocumentSnapshot doc : docs) {
                        Ticket t = doc.toObject(Ticket.class);
                        if (t != null) {
                            long m = t.getAmountMillimes() != null ? t.getAmountMillimes() : Math.round(t.getTotalAmount() * 1000.0);
                            long time = t.getTimestamp() > 0 ? t.getTimestamp() : System.currentTimeMillis();
                            String dayKey = getDayKey(time);

                            totalMillimes += m;
                            Long currentM = dailyMillimes.get(dayKey);
                            long prev = currentM != null ? currentM : 0L;
                            dailyMillimes.put(dayKey, prev + m);
                            count++;
                        }
                    }

                    List<Map.Entry<String, Map<String, Object>>> operations = new ArrayList<>();

                    // Total stats
                    Map<String, Object> totalData = new HashMap<>();
                    totalData.put("totalRevenue", totalMillimes / 1000.0);
                    totalData.put("totalRevenueMillimes", totalMillimes);
                    totalData.put("ticketCount", count);
                    operations.add(new AbstractMap.SimpleEntry<>("total", totalData));

                    // Daily stats
                    for (Map.Entry<String, Long> entry : dailyMillimes.entrySet()) {
                        Map<String, Object> dailyData = new HashMap<>();
                        dailyData.put("totalRevenue", entry.getValue() / 1000.0);
                        dailyData.put("totalRevenueMillimes", entry.getValue());
                        operations.add(new AbstractMap.SimpleEntry<>("daily_" + entry.getKey(), dailyData));
                    }

                    final int finalCount = count;
                    commitStatsBatches(operations, 0, 400, () -> callback.onSuccess(finalCount), callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    private interface InternalCallback {
        void onSuccess();
    }

    private void commitStatsBatches(List<Map.Entry<String, Map<String, Object>>> ops, int startIndex, int batchSize, InternalCallback onSuccess, ErrorCallback onError) {
        if (startIndex >= ops.size()) {
            onSuccess.onSuccess();
            return;
        }
        int endIndex = Math.min(startIndex + batchSize, ops.size());
        WriteBatch batch = db.batch();
        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<String, Map<String, Object>> entry = ops.get(i);
            DocumentReference ref = db.collection("stats").document(entry.getKey());
            batch.set(ref, entry.getValue(), SetOptions.merge());
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> commitStatsBatches(ops, endIndex, batchSize, onSuccess, onError))
                .addOnFailureListener(onError::onError);
    }
}
