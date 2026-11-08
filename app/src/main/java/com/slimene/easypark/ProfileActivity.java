package com.slimene.easypark;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import android.widget.EditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;

import com.slimene.easypark.repository.SpotRepository;
import com.slimene.easypark.repository.StatsRepository;
import com.slimene.easypark.utils.RoleGuard;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvEmail, tvAvatarInitials;
    private Chip tvRoleBadge;
    private EditText etFullName, etNewPassword;
    private Button btnSaveProfile, btnUpdatePassword;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String userEmailStr = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        RoleGuard.require(this, "admin", "agent", "client");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvEmail = findViewById(R.id.tvEmail);
        tvRoleBadge = findViewById(R.id.tvRoleBadge);
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        etFullName = findViewById(R.id.etFullName);
        etNewPassword = findViewById(R.id.etNewPassword);

        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnUpdatePassword = findViewById(R.id.btnUpdatePassword);
        Button btnSendResetEmail = findViewById(R.id.btnSendResetEmail);
        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnRunDataSync = findViewById(R.id.btnRunDataSync);
        progressBar = findViewById(R.id.progressBar);

        btnSaveProfile.setOnClickListener(v -> saveProfileChanges());
        btnUpdatePassword.setOnClickListener(v -> updatePassword());
        btnSendResetEmail.setOnClickListener(v -> sendResetEmail());
        btnLogout.setOnClickListener(v -> RoleGuard.forceLogout(this));
        if (btnRunDataSync != null) {
            btnRunDataSync.setOnClickListener(v -> runDataSync());
        }

        loadUserProfile();
    }

    private void loadUserProfile() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        progressBar.setVisibility(View.VISIBLE);

        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    progressBar.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        String fullName = documentSnapshot.getString("fullName");
                        String email = documentSnapshot.getString("email");
                        String role = documentSnapshot.getString("role");

                        if (fullName != null) {
                            etFullName.setText(fullName);
                            updateInitials(fullName);
                        }

                        userEmailStr = email != null ? email : (mAuth.getCurrentUser().getEmail() != null ? mAuth.getCurrentUser().getEmail() : "");
                        tvEmail.setText(userEmailStr);

                        View cardMaintenanceSection = findViewById(R.id.cardMaintenanceSection);
                        if ("admin".equalsIgnoreCase(role)) {
                            tvRoleBadge.setText("Administrateur");
                            if (cardMaintenanceSection != null) cardMaintenanceSection.setVisibility(View.VISIBLE);
                        } else if ("agent".equalsIgnoreCase(role)) {
                            tvRoleBadge.setText("Agent");
                            if (cardMaintenanceSection != null) cardMaintenanceSection.setVisibility(View.VISIBLE);
                        } else {
                            tvRoleBadge.setText("Client");
                            if (cardMaintenanceSection != null) cardMaintenanceSection.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ProfileActivity.this, "Erreur chargement profil: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updateInitials(String name) {
        if (name == null || name.trim().isEmpty()) {
            tvAvatarInitials.setText("EP");
            return;
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            String initials = (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
            tvAvatarInitials.setText(initials);
        } else if (parts.length == 1 && !parts[0].isEmpty()) {
            tvAvatarInitials.setText(parts[0].substring(0, 1).toUpperCase());
        }
    }

    private void runDataSync() {
        progressBar.setVisibility(View.VISIBLE);
        StatsRepository statsRepo = new StatsRepository();
        SpotRepository spotRepo = new SpotRepository();

        statsRepo.backfillStats(new StatsRepository.BackfillCallback() {
            @Override
            public void onSuccess(int ticketsProcessed) {
                spotRepo.backfillSortKeys(new SpotRepository.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        progressBar.setVisibility(View.GONE);
                        Snackbar.make(findViewById(android.R.id.content), "Données mises à jour (" + ticketsProcessed + " tickets)", Snackbar.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(Exception e) {
                        progressBar.setVisibility(View.GONE);
                        Snackbar.make(findViewById(android.R.id.content), "Erreur places: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                progressBar.setVisibility(View.GONE);
                Snackbar.make(findViewById(android.R.id.content), "Erreur stats: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void saveProfileChanges() {
        String fullName = etFullName.getText().toString().trim();
        if (TextUtils.isEmpty(fullName)) {
            etFullName.setError("Veuillez entrer votre nom complet");
            return;
        }

        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        progressBar.setVisibility(View.VISIBLE);
        btnSaveProfile.setEnabled(false);

        db.collection("users").document(uid)
                .update("fullName", fullName)
                .addOnSuccessListener(aVoid -> {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        progressBar.setVisibility(View.GONE);
                        btnSaveProfile.setEnabled(true);
                        Toast.makeText(ProfileActivity.this, "Profil mis à jour avec succès !", Toast.LENGTH_SHORT).show();
                    }, 800);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnSaveProfile.setEnabled(true);
                    Toast.makeText(ProfileActivity.this, "Erreur mise à jour: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updatePassword() {
        String newPassword = etNewPassword.getText().toString().trim();
        if (TextUtils.isEmpty(newPassword) || newPassword.length() < 6) {
            etNewPassword.setError("Le mot de passe doit contenir au moins 6 caractères");
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        progressBar.setVisibility(View.VISIBLE);
        btnUpdatePassword.setEnabled(false);

        user.updatePassword(newPassword)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    btnUpdatePassword.setEnabled(true);
                    etNewPassword.setText("");
                    Toast.makeText(ProfileActivity.this, "Mot de passe mis à jour avec succès !", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnUpdatePassword.setEnabled(true);
                    if (e instanceof FirebaseAuthRecentLoginRequiredException) {
                      promptReauthAndRetryUpdate(newPassword);
                  } else {
                      Toast.makeText(ProfileActivity.this, "Erreur mise à jour mot de passe: " + e.getMessage(), Toast.LENGTH_LONG).show();
                  }
                });
    }

    private void sendResetEmail() {
        if (TextUtils.isEmpty(userEmailStr)) {
            Toast.makeText(this, "Email introuvable", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        mAuth.sendPasswordResetEmail(userEmailStr)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ProfileActivity.this, "Email de réinitialisation envoyé à " + userEmailStr, Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ProfileActivity.this, "Erreur d'envoi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void promptReauthAndRetryUpdate(String newPassword) {
        EditText input = new EditText(this);
        input.setHint("Mot de passe actuel");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Réauthentification requise")
                .setMessage("Veuillez saisir votre mot de passe actuel pour continuer.")
                .setView(input)
                .setPositiveButton("Confirmer", (dialog, which) -> {
                    String oldPassword = input.getText().toString().trim();
                    if (!oldPassword.isEmpty() && mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getEmail() != null) {
                        AuthCredential credential = EmailAuthProvider.getCredential(mAuth.getCurrentUser().getEmail(), oldPassword);
                        mAuth.getCurrentUser().reauthenticate(credential)
                                .addOnSuccessListener(aVoid -> {
                                    mAuth.getCurrentUser().updatePassword(newPassword).addOnSuccessListener(a -> {
                                        Toast.makeText(ProfileActivity.this, "Mot de passe mis à jour avec succès !", Toast.LENGTH_SHORT).show();
                                    }).addOnFailureListener(e -> Toast.makeText(ProfileActivity.this, "Erreur: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                })
                                .addOnFailureListener(e -> Toast.makeText(ProfileActivity.this, "Échec de réauthentification: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }
}
