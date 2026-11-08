package com.slimene.easypark.repository;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.slimene.easypark.Ticket;

import java.util.ArrayList;
import java.util.List;

public class TicketRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    public static final int PAGE_SIZE = 25;

    public interface TicketsPageCallback {
        void onPageLoaded(List<Ticket> tickets, DocumentSnapshot lastVisibleDoc, boolean hasMore);
        void onError(Exception e);
    }

    public void loadTicketsPage(long startTimeMillis, DocumentSnapshot startAfterDoc, TicketsPageCallback callback) {
        Query query = db.collection("tickets")
                .orderBy("timestamp", Query.Direction.DESCENDING);

        if (startTimeMillis > 0) {
            query = query.whereGreaterThanOrEqualTo("timestamp", startTimeMillis);
        }

        if (startAfterDoc != null) {
            query = query.startAfter(startAfterDoc);
        }

        query.limit(PAGE_SIZE).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<DocumentSnapshot> docs = queryDocumentSnapshots.getDocuments();
                    List<Ticket> tickets = new ArrayList<>();
                    for (DocumentSnapshot doc : docs) {
                        Ticket t = doc.toObject(Ticket.class);
                        if (t != null) {
                            tickets.add(t);
                        }
                    }

                    DocumentSnapshot lastDoc = docs.size() > 0 ? docs.get(docs.size() - 1) : null;
                    boolean hasMore = docs.size() == PAGE_SIZE;

                    callback.onPageLoaded(tickets, lastDoc, hasMore);
                })
                .addOnFailureListener(callback::onError);
    }
}
