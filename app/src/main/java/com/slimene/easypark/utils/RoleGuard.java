package com.slimene.easypark.utils;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.slimene.easypark.R;
import com.slimene.easypark.User;
import com.slimene.easypark.repository.SessionRepository;

import java.util.List;

public class RoleGuard {

    public interface GuardCallback {
        void onGranted(User user);
    }

    public static void require(AppCompatActivity activity, String... allowedRoles) {
        require(activity, null, allowedRoles);
    }

    public static void require(AppCompatActivity activity, GuardCallback callback, String... allowedRoles) {
        final View rootContent = activity.findViewById(android.R.id.content);
        final View progressBar = activity.findViewById(R.id.progressBar);

        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        SessionRepository.getInstance().attach(activity, (user, isError, error) -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            if (user != null) {
                if (user.isSuspended()) {
                    Toast.makeText(activity, "Votre compte a été suspendu.", Toast.LENGTH_LONG).show();
                    SessionRepository.forceLogout(activity);
                    return;
                }

                String userRole = user.getRole();
                if (userRole == null || userRole.trim().isEmpty()) {
                    return;
                }

                boolean roleMatched = false;
                for (String allowed : allowedRoles) {
                    if (allowed != null && allowed.equalsIgnoreCase(userRole.trim())) {
                        roleMatched = true;
                        break;
                    }
                }

                if (!roleMatched) {
                    Toast.makeText(activity, "Accès non autorisé.", Toast.LENGTH_LONG).show();
                    SessionRepository.forceLogout(activity);
                    return;
                }

                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                if (callback != null) {
                    callback.onGranted(user);
                }
                return;
            }

            if (isError) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                if (rootContent != null) {
                    Snackbar.make(rootContent, "Problème de connexion réseau.", Snackbar.LENGTH_INDEFINITE)
                            .setAction("Réessayer", v -> require(activity, callback, allowedRoles))
                            .show();
                }
                return;
            }

            // User is null and no error -> signed out
            SessionRepository.forceLogout(activity);
        });
    }

    public static void forceLogout(Context context) {
        SessionRepository.forceLogout(context);
    }
}
