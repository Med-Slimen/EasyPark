package com.slimene.easypark.repository;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.slimene.easypark.User;

import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    public static final int PAGE_SIZE = 25;

    public interface UsersCallback {
        void onUsersLoaded(List<User> users);
        void onError(Exception e);
    }

    public void loadUsersByRole(String role, String searchQuery, UsersCallback callback) {
        Query query = db.collection("users").limit(100); // query users

        if (role != null && !role.isEmpty()) {
            query = query.whereEqualTo("role", role);
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<User> result = new ArrayList<>();
                    String cleanQuery = searchQuery != null ? searchQuery.trim().toLowerCase() : "";

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            if (cleanQuery.isEmpty()) {
                                result.add(user);
                            } else {
                                String name = user.getFullName() != null ? user.getFullName().toLowerCase() : "";
                                String email = user.getEmail() != null ? user.getEmail().toLowerCase() : "";
                                if (name.contains(cleanQuery) || email.contains(cleanQuery)) {
                                    result.add(user);
                                }
                            }
                        }
                    }
                    callback.onUsersLoaded(result);
                })
                .addOnFailureListener(callback::onError);
    }
}
