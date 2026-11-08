package com.slimene.easypark.repository;

import android.content.Context;
import android.content.Intent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.slimene.easypark.MainActivity;
import com.slimene.easypark.User;

import java.util.HashSet;
import java.util.Set;

public class SessionRepository {
    private static SessionRepository instance;

    public interface SessionCallback {
        void onSessionUpdated(User user, boolean isError, Exception error);
    }

    private ListenerRegistration userListener;
    private User currentProfile;
    private boolean loaded = false;
    private boolean hasError = false;
    private Exception lastError;
    private String activeUid;

    private final Set<SessionCallback> callbacks = new HashSet<>();

    private SessionRepository() {}

    public static synchronized SessionRepository getInstance() {
        if (instance == null) {
            instance = new SessionRepository();
        }
        return instance;
    }

    public synchronized void attach(Context context, SessionCallback callback) {
        if (callback != null) {
            callbacks.add(callback);
        }

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            clearSession();
            if (callback != null) {
                callback.onSessionUpdated(null, false, null);
            }
            return;
        }

        String uid = firebaseUser.getUid();
        if (!uid.equals(activeUid) || userListener == null) {
            activeUid = uid;
            startUserListener(context, uid);
        }

        if (callback != null) {
            callback.onSessionUpdated(currentProfile, hasError, lastError);
        }
    }

    public synchronized void detach(SessionCallback callback) {
        if (callback != null) {
            callbacks.remove(callback);
        }
    }

    private void startUserListener(Context context, String uid) {
        if (userListener != null) {
            userListener.remove();
        }

        userListener = FirebaseFirestore.getInstance().collection("users").document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        hasError = true;
                        lastError = error;
                        notifyCallbacks();
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        try {
                            User user = snapshot.toObject(User.class);
                            if (user != null) {
                                user.setUid(snapshot.getId());
                                if (user.isSuspended()) {
                                    forceLogout(context);
                                    return;
                                }
                                this.currentProfile = user;
                                this.loaded = true;
                                this.hasError = false;
                                this.lastError = null;
                                notifyCallbacks();
                                return;
                            }
                        } catch (Exception parseException) {
                            hasError = true;
                            lastError = parseException;
                            notifyCallbacks();
                            return;
                        }
                    }

                    if (snapshot != null && !snapshot.exists()) {
                        this.currentProfile = null;
                        this.loaded = true;
                        this.hasError = false;
                        this.lastError = null;
                        notifyCallbacks();
                        forceLogout(context);
                    }
                });
    }

    private synchronized void notifyCallbacks() {
        for (SessionCallback cb : new HashSet<>(callbacks)) {
            cb.onSessionUpdated(currentProfile, hasError, lastError);
        }
    }

    public static void forceLogout(Context context) {
        getInstance().clearSession();
        FirebaseAuth.getInstance().signOut();

        if (context != null && !(context instanceof MainActivity)) {
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
        }
    }

    public synchronized void clearSession() {
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
        activeUid = null;
        currentProfile = null;
        loaded = false;
        hasError = false;
        lastError = null;
        callbacks.clear();
    }

    public User getCurrentProfile() {
        return currentProfile;
    }

    public boolean isLoaded() {
        return loaded;
    }
}
