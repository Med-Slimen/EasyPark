package com.slimene.easypark;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import android.view.LayoutInflater;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import android.widget.ProgressBar;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Transaction;
import com.slimene.easypark.utils.BillingCalculator;
import com.slimene.easypark.utils.MoneyFormat;
import com.slimene.easypark.repository.StatsRepository;
import com.slimene.easypark.utils.RoleGuard;
import com.slimene.easypark.repository.Tariff;
import com.slimene.easypark.repository.TariffRepository;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ClientReservationActivity extends AppCompatActivity {

    private TextView tvNoReservation, tvSpotTitle, tvStatusMessage, tvTimerLabel, tvTimerValue, tvEstimatedCost, tvMinDurationWarning;
    private View cardActive;
    private Button btnCancelReservation, btnPayAndExit;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ListenerRegistration reservationListener;

    private ParkingSpot currentSpot;
    private Tariff currentTariff = Tariff.unloaded();
    private long userBalanceMillimes = 0;
    private String clientName = "";
    private String clientEmail = "";

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimerAndCostUI();
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_reservation);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        RoleGuard.require(this, "client");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvNoReservation = findViewById(R.id.tvNoReservation);
        cardActive = findViewById(R.id.cardActive);
        tvSpotTitle = findViewById(R.id.tvSpotTitle);
        tvStatusMessage = findViewById(R.id.tvStatusMessage);
        tvTimerLabel = findViewById(R.id.tvTimerLabel);
        tvTimerValue = findViewById(R.id.tvTimerValue);
        tvEstimatedCost = findViewById(R.id.tvEstimatedCost);
        tvMinDurationWarning = findViewById(R.id.tvMinDurationWarning);

        btnCancelReservation = findViewById(R.id.btnCancelReservation);
        btnPayAndExit = findViewById(R.id.btnPayAndExit);
        progressBar = findViewById(R.id.progressBar);

        btnCancelReservation.setOnClickListener(v -> cancelReservation());
        btnPayAndExit.setOnClickListener(v -> handlePayAndExit());

        listenToUserReservation();
        timerHandler.post(timerRunnable);
    }

    private final TariffRepository.TariffObserver tariffObserver = new TariffRepository.TariffObserver() {
        @Override
        public void onTariffUpdated(Tariff tariff) {
            currentTariff = tariff;
            updateTimerAndCostUI();
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        TariffRepository.getInstance().addObserver(tariffObserver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        TariffRepository.getInstance().removeObserver(tariffObserver);
    }



    private void listenToUserReservation() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        // Listen for balance and user details
        db.collection("users").document(uid).addSnapshotListener((snapshot, error) -> {
            if (snapshot != null && snapshot.exists()) {
                Double bal = snapshot.getDouble("balance");
                userBalanceMillimes = bal != null ? Math.round(bal * 1000.0) : 0;
                String name = snapshot.getString("fullName");
                clientName = name != null ? name : "";
                String email = snapshot.getString("email");
                clientEmail = email != null ? email : "";
            }
        });

        // Query active spot where clientId == uid
        reservationListener = db.collection("spots")
                .whereEqualTo("clientId", uid)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(ClientReservationActivity.this, "Erreur: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        currentSpot = doc.toObject(ParkingSpot.class);
                        if (currentSpot != null) {
                            showActiveReservationUI(currentSpot);
                            return;
                        }
                    }

                    currentSpot = null;
                    showNoReservationUI();
                });
    }

    private void showNoReservationUI() {
        tvNoReservation.setVisibility(View.VISIBLE);
        cardActive.setVisibility(View.GONE);
        btnCancelReservation.setVisibility(View.GONE);
        btnPayAndExit.setVisibility(View.GONE);
        tvMinDurationWarning.setVisibility(View.GONE);
    }

    private void showActiveReservationUI(ParkingSpot spot) {
        tvNoReservation.setVisibility(View.GONE);
        cardActive.setVisibility(View.VISIBLE);
        tvSpotTitle.setText("Place : " + spot.getSpotId());

        String status = spot.getStatus() != null ? spot.getStatus() : "";

        if ("reservee".equalsIgnoreCase(status)) {
            tvStatusMessage.setText("En attente de confirmation de l'agent...");
            tvTimerLabel.setText("Temps restant pour arriver :");
            tvEstimatedCost.setVisibility(View.GONE);
            tvMinDurationWarning.setVisibility(View.GONE);
            btnCancelReservation.setVisibility(View.VISIBLE);
            btnPayAndExit.setVisibility(View.GONE);
        } else if ("occupee".equalsIgnoreCase(status)) {
            tvStatusMessage.setText("Stationnement en cours (Occupée)");
            tvTimerLabel.setText("Durée de stationnement :");
            tvEstimatedCost.setVisibility(View.VISIBLE);
            btnCancelReservation.setVisibility(View.GONE);
            btnPayAndExit.setVisibility(View.VISIBLE);
        } else {
            showNoReservationUI();
        }
    }

    private void updateTimerAndCostUI() {
        if (currentSpot == null) return;

        String status = currentSpot.getStatus() != null ? currentSpot.getStatus() : "";

        if ("reservee".equalsIgnoreCase(status)) {
            long reservedAt = currentSpot.getReservedAt();
            long maxDurationMillis = AppConfig.MAX_RESERVATION_MINUTES * 60 * 1000L;
            long remainingMillis = (reservedAt + maxDurationMillis) - System.currentTimeMillis();

            if (remainingMillis <= 0) {
                tvStatusMessage.setText("Réservation expirée");
                tvTimerValue.setText("00:00 (Expirée)");
            } else {
                tvStatusMessage.setText("En attente de confirmation de l'agent...");
                long minutes = (remainingMillis / 1000) / 60;
                long seconds = (remainingMillis / 1000) % 60;
                tvTimerValue.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
            }
        } else if ("occupee".equalsIgnoreCase(status)) {
            long startTime = currentSpot.getStartTime();
            if (startTime <= 0) startTime = System.currentTimeMillis();

            long elapsedMillis = Math.max(0, System.currentTimeMillis() - startTime);
            long totalSeconds = elapsedMillis / 1000;
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;

            if (hours > 0) {
                tvTimerValue.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
            } else {
                tvTimerValue.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
            }

            long elapsedMinutes = totalSeconds / 60;

            String spotType = currentSpot.getType() != null ? currentSpot.getType() : "voiture";
            long activeHourlyRateMillimes = "moto".equalsIgnoreCase(spotType) ? currentTariff.getMotoHourlyRateMillimes() : currentTariff.getCarHourlyRateMillimes();

            BillingCalculator.BillingResult billing = BillingCalculator.compute(startTime, System.currentTimeMillis(), activeHourlyRateMillimes, AppConfig.GRACE_PERIOD_MINUTES);

            tvEstimatedCost.setText("Coût estimé : " + MoneyFormat.format(billing.amountMillimes));

            if (billing.durationMinutes < AppConfig.MIN_PARKING_MINUTES) {
                long remaining = AppConfig.MIN_PARKING_MINUTES - billing.durationMinutes;
                btnPayAndExit.setEnabled(false);
                tvMinDurationWarning.setVisibility(View.VISIBLE);
                tvMinDurationWarning.setText(String.format(Locale.getDefault(),
                        "Durée minimale de stationnement : %d min (Encore %d min avant de pouvoir sortir).",
                        AppConfig.MIN_PARKING_MINUTES, remaining));
            } else {
                btnPayAndExit.setEnabled(true);
                tvMinDurationWarning.setVisibility(View.GONE);
            }
        }
    }

    private void cancelReservation() {
        if (currentSpot == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        new AlertDialog.Builder(this)
                .setTitle("Annuler la réservation ?")
                .setMessage("Êtes-vous sûr de vouloir annuler votre réservation pour la place " + currentSpot.getSpotId() + " ?")
                .setPositiveButton("Oui, annuler", (dialog, which) -> {
                    db.runTransaction((Transaction.Function<Void>) transaction -> {
                        DocumentReference userRef = db.collection("users").document(uid);
                        DocumentReference spotRef = db.collection("spots").document(currentSpot.getSpotId());
                        DocumentSnapshot spotSnap = transaction.get(spotRef);
                        
                        if (!uid.equals(spotSnap.getString("clientId")) || !"reservee".equals(spotSnap.getString("status"))) {
                            throw new FirebaseFirestoreException("Réservation invalide.", FirebaseFirestoreException.Code.ABORTED);
                        }
                        
                        transaction.update(spotRef, "status", "libre", "clientId", "", "clientPlate", "", "reservedAt", 0, "startTime", 0);
                        transaction.update(userRef, "activeSpotId", "");
                        return null;
                    })
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(ClientReservationActivity.this, "Réservation annulée", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(ClientReservationActivity.this, "Erreur d'annulation: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Non", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void handlePayAndExit() {
        if (currentSpot == null) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pay_and_exit, null);
        TextView tvDialogDuration = dialogView.findViewById(R.id.tvDialogDuration);
        TextView tvDialogAmount = dialogView.findViewById(R.id.tvDialogAmount);
        TextView tvDialogBalance = dialogView.findViewById(R.id.tvDialogBalance);

        final Handler dialogHandler = new Handler(Looper.getMainLooper());
        final Runnable dialogRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentSpot != null) {
                    long startTime = currentSpot.getStartTime();
                    if (startTime <= 0) startTime = System.currentTimeMillis();

                    String spotType = currentSpot.getType() != null ? currentSpot.getType() : "voiture";
                    long activeHourlyRateMillimes = "moto".equalsIgnoreCase(spotType) ? currentTariff.getMotoHourlyRateMillimes() : currentTariff.getCarHourlyRateMillimes();

                    BillingCalculator.BillingResult billing = BillingCalculator.compute(startTime, System.currentTimeMillis(), activeHourlyRateMillimes, AppConfig.GRACE_PERIOD_MINUTES);

                    tvDialogDuration.setText(String.format(Locale.getDefault(), "Durée réelle : %d min (Facturée : %d h)", billing.durationMinutes, billing.billedHours));
                    tvDialogAmount.setText("Montant à payer : " + MoneyFormat.format(billing.amountMillimes));
                    tvDialogBalance.setText("Solde disponible : " + MoneyFormat.format(userBalanceMillimes));

                    dialogHandler.postDelayed(this, 1000);
                }
            }
        };

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("Confirmer le paiement", null)
                .setNegativeButton("Annuler", (d, which) -> d.dismiss())
                .create();

        dialog.setOnDismissListener(d -> dialogHandler.removeCallbacks(dialogRunnable));

        dialog.setOnShowListener(d -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                // Stop dialog refresh loop
                dialogHandler.removeCallbacks(dialogRunnable);
                dialog.dismiss();

                // Recalculate final cost one last time directly from startTime
                if (currentSpot == null) return;
                long startTime = currentSpot.getStartTime();
                if (startTime <= 0) startTime = System.currentTimeMillis();

                String spotType = currentSpot.getType() != null ? currentSpot.getType() : "voiture";
                long activeHourlyRateMillimes = "moto".equalsIgnoreCase(spotType) ? currentTariff.getMotoHourlyRateMillimes() : currentTariff.getCarHourlyRateMillimes();

                BillingCalculator.BillingResult billing = BillingCalculator.compute(startTime, System.currentTimeMillis(), activeHourlyRateMillimes, AppConfig.GRACE_PERIOD_MINUTES);

                if (userBalanceMillimes < billing.amountMillimes) {
                    Toast.makeText(ClientReservationActivity.this, "Solde insuffisant ! Veuillez recharger votre portefeuille.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(ClientReservationActivity.this, ClientWalletActivity.class));
                } else {
                    processPaymentAndRelease(startTime, billing, spotType, clientName, clientEmail);
                }
            });
        });

        dialog.show();
        dialogHandler.post(dialogRunnable);
    }

    private void processPaymentAndRelease(long entryTime, BillingCalculator.BillingResult billing, String vehicleType, String name, String email) {
        if (mAuth.getCurrentUser() == null || currentSpot == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        btnPayAndExit.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference userRef = db.collection("users").document(uid);
            DocumentReference spotRef = db.collection("spots").document(currentSpot.getSpotId());
            
            DocumentSnapshot userSnap = transaction.get(userRef);
            DocumentSnapshot spotSnap = transaction.get(spotRef);
            
            Double bal = userSnap.getDouble("balance");
            long currentBalMillimes = bal != null ? Math.round(bal * 1000.0) : 0;
            
            if (!uid.equals(spotSnap.getString("clientId")) || !"occupee".equals(spotSnap.getString("status"))) {
                throw new FirebaseFirestoreException("Stationnement invalide.", FirebaseFirestoreException.Code.ABORTED);
            }
            
            if (currentBalMillimes < billing.amountMillimes) {
                throw new FirebaseFirestoreException("Solde insuffisant", FirebaseFirestoreException.Code.ABORTED);
            }
            
            // Deduct balance
            double newBalanceDinars = (currentBalMillimes - billing.amountMillimes) / 1000.0;
            transaction.update(userRef, "balance", newBalanceDinars, "activeSpotId", "");
            
            // Generate ticket
            String ticketId = db.collection("tickets").document().getId();
            DocumentReference ticketRef = db.collection("tickets").document(ticketId);
            Ticket ticket = new Ticket(
                ticketId, currentSpot.getSpotId(), vehicleType, uid, currentSpot.getClientPlate(),
                name, email, entryTime, System.currentTimeMillis(), billing.billedHours, billing.amountMillimes,
                uid, "client"
            );
            transaction.set(ticketRef, ticket);
            
            // Reset spot
            transaction.update(spotRef, "status", "libre", "clientId", "", "clientPlate", "", "reservedAt", 0, "startTime", 0);
            
            // Increment stats
            StatsRepository.incrementRevenueInTransaction(transaction, db, billing.amountMillimes, System.currentTimeMillis());

            return null;
        }).addOnSuccessListener(aVoid -> {
            btnPayAndExit.setEnabled(true);
            progressBar.setVisibility(View.GONE);
            Toast.makeText(ClientReservationActivity.this, "Paiement réussi !", Toast.LENGTH_SHORT).show();
            finish();
        }).addOnFailureListener(e -> {
            btnPayAndExit.setEnabled(true);
            progressBar.setVisibility(View.GONE);
            if (e.getMessage() != null && e.getMessage().contains("Solde insuffisant")) {
                Toast.makeText(ClientReservationActivity.this, "Solde insuffisant ! Veuillez recharger.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(ClientReservationActivity.this, ClientWalletActivity.class));
            } else {
                Toast.makeText(ClientReservationActivity.this, "Erreur de paiement: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        if (reservationListener != null) reservationListener.remove();
    }
}
