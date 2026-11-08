package com.slimene.easypark.agent;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;
import com.slimene.easypark.AppConfig;
import com.slimene.easypark.utils.RoleGuard;
import com.slimene.easypark.ParkingSpot;
import com.slimene.easypark.R;
import com.slimene.easypark.Ticket;
import com.slimene.easypark.utils.BillingCalculator;
import com.slimene.easypark.utils.MoneyFormat;
import com.slimene.easypark.repository.StatsRepository;
import com.slimene.easypark.repository.Tariff;
import com.slimene.easypark.repository.TariffRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ExitActivity extends AppCompatActivity {

    private TextView tvPlate, tvSpotDetails, tvEntryTime, tvExitTime, tvDuration, tvBilledHours, tvRate, tvTotalAmount;
    private Button btnValidateExit, btnValidateExitWallet, btnCancelNoCharge;
    private ProgressBar progressBar;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ParkingSpot spot;

    private long entryMillis;
    private long exitMillis;
    private BillingCalculator.BillingResult billingResult;
    private Tariff currentTariff = Tariff.unloaded();
    private long activeRateMillimes;
    private String uid;

    private SimpleDateFormat timeFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exit);

        RoleGuard.require(this, "admin", "agent");

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "";

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvPlate = findViewById(R.id.tvPlate);
        tvSpotDetails = findViewById(R.id.tvSpotDetails);
        tvEntryTime = findViewById(R.id.tvEntryTime);
        tvExitTime = findViewById(R.id.tvExitTime);
        tvDuration = findViewById(R.id.tvDuration);
        tvBilledHours = findViewById(R.id.tvBilledHours);
        tvRate = findViewById(R.id.tvRate);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);

        btnValidateExit = findViewById(R.id.btnValidateExit);
        btnValidateExitWallet = findViewById(R.id.btnValidateExitWallet);
        btnCancelNoCharge = findViewById(R.id.btnCancelNoCharge);
        progressBar = findViewById(R.id.progressBar);

        String spotId = getIntent().getStringExtra("spotId");
        if (spotId == null) {
            finish();
            return;
        }

        loadData(spotId);
    }

    private final TariffRepository.TariffObserver tariffObserver = tariff -> {
        this.currentTariff = tariff;
        if (spot != null) {
            computeAndBindUI();
        }
    };

    private Handler tickerHandler;
    private Runnable tickerRunnable;

    @Override
    protected void onStart() {
        super.onStart();
        TariffRepository.getInstance().addObserver(tariffObserver);
        tickerHandler = new Handler(Looper.getMainLooper());
        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                if (spot != null) {
                    computeAndBindUI();
                }
                tickerHandler.postDelayed(this, 30000); // refresh every 30 seconds
            }
        };
        tickerHandler.post(tickerRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        TariffRepository.getInstance().removeObserver(tariffObserver);
        if (tickerHandler != null) {
            tickerHandler.removeCallbacks(tickerRunnable);
            tickerHandler = null;
        }
    }

    private void loadData(String spotId) {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("spots").document(spotId).get()
                .addOnSuccessListener(snapshot -> {
                    progressBar.setVisibility(View.GONE);
                    if (snapshot.exists()) {
                        spot = snapshot.toObject(ParkingSpot.class);
                        if (spot != null && "occupee".equalsIgnoreCase(spot.getStatus())) {
                            computeAndBindUI();
                            if (spot.getClientId() != null && !spot.getClientId().isEmpty()) {
                                checkClientWallet();
                            }
                        } else {
                            Toast.makeText(this, "La place n'est pas occupée.", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    } else {
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Erreur: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void computeAndBindUI() {
        tvPlate.setText(!spot.getClientPlate().isEmpty() ? spot.getClientPlate() : "Inconnu");
        String typeLabel = "moto".equalsIgnoreCase(spot.getType()) ? "Moto" : "Voiture";
        tvSpotDetails.setText("Place " + spot.getSpotId() + " • " + typeLabel);

        entryMillis = spot.getStartTime() > 0 ? spot.getStartTime() : spot.getReservedAt();
        if (entryMillis == 0) entryMillis = System.currentTimeMillis();
        exitMillis = System.currentTimeMillis();

        boolean isMoto = spot != null && "moto".equalsIgnoreCase(spot.getType());
        activeRateMillimes = isMoto ? currentTariff.getMotoHourlyRateMillimes() : currentTariff.getCarHourlyRateMillimes();

        billingResult = BillingCalculator.compute(entryMillis, exitMillis, activeRateMillimes, AppConfig.GRACE_PERIOD_MINUTES);

        tvEntryTime.setText(timeFormat.format(new Date(entryMillis)));
        tvExitTime.setText(timeFormat.format(new Date(exitMillis)));
        tvDuration.setText(billingResult.durationMinutes + " min");
        tvBilledHours.setText(billingResult.billedHours + " h");

        tvRate.setText(MoneyFormat.format(activeRateMillimes) + " / h");
        btnValidateExit.setEnabled(true);
        if (btnValidateExitWallet != null) btnValidateExitWallet.setEnabled(true);
        tvTotalAmount.setText(MoneyFormat.format(billingResult.amountMillimes));

        btnValidateExit.setOnClickListener(v -> handleExit("cash"));
        btnCancelNoCharge.setOnClickListener(v -> promptCancelReason());
    }

    private void checkClientWallet() {
        db.collection("users").document(spot.getClientId()).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Double bal = snapshot.getDouble("balance");
                        long currentBalMillimes = bal != null ? Math.round(bal * 1000.0) : 0;
                        if (currentBalMillimes >= billingResult.amountMillimes && billingResult.amountMillimes > 0) {
                            btnValidateExitWallet.setVisibility(View.VISIBLE);
                            btnValidateExitWallet.setOnClickListener(v -> handleExit("wallet"));
                        }
                    }
                });
    }

    private void handleExit(String paymentMethod) {
        btnValidateExit.setEnabled(false);
        if (btnValidateExitWallet != null) btnValidateExitWallet.setEnabled(false);
        btnCancelNoCharge.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        final long[] confirmedAmountHolder = new long[1];

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference spotRef = db.collection("spots").document(spot.getSpotId());
            DocumentSnapshot spotSnap = transaction.get(spotRef);

            if (!"occupee".equals(spotSnap.getString("status"))) {
                throw new FirebaseFirestoreException("La place n'est plus occupée.", FirebaseFirestoreException.Code.ABORTED);
            }
            
            // Recompute exact amount inside transaction
            long currentExitMillis = System.currentTimeMillis();
            BillingCalculator.BillingResult currentBilling = BillingCalculator.compute(entryMillis, currentExitMillis, activeRateMillimes, AppConfig.GRACE_PERIOD_MINUTES);

            if ("wallet".equals(paymentMethod) && spot.getClientId() != null && !spot.getClientId().isEmpty()) {
                DocumentReference userRef = db.collection("users").document(spot.getClientId());
                DocumentSnapshot userSnap = transaction.get(userRef);
                Double bal = userSnap.getDouble("balance");
                long currentBalMillimes = bal != null ? Math.round(bal * 1000.0) : 0;
                
                if (currentBalMillimes < currentBilling.amountMillimes) {
                    throw new FirebaseFirestoreException("Solde insuffisant.", FirebaseFirestoreException.Code.ABORTED);
                }
                
                double newBalance = (currentBalMillimes - currentBilling.amountMillimes) / 1000.0;
                transaction.update(userRef, "balance", newBalance, "activeSpotId", "");
            } else if (spot.getClientId() != null && !spot.getClientId().isEmpty()) {
                DocumentReference userRef = db.collection("users").document(spot.getClientId());
                transaction.update(userRef, "activeSpotId", "");
            }

            // Ticket creation
            String ticketId = db.collection("tickets").document().getId();
            DocumentReference ticketRef = db.collection("tickets").document(ticketId);
            Ticket ticket = new Ticket(
                ticketId, spot.getSpotId(), spot.getType(), spot.getClientId(), spot.getClientPlate(),
                "", "", entryMillis, currentExitMillis, currentBilling.billedHours, currentBilling.amountMillimes,
                uid, "agent_" + paymentMethod
            );
            transaction.set(ticketRef, ticket);

            // Free spot
            transaction.update(spotRef, "status", "libre", "clientId", "", "clientPlate", "", "reservedAt", 0, "startTime", 0);

            // Increment stats
            StatsRepository.incrementRevenueInTransaction(transaction, db, currentBilling.amountMillimes, currentExitMillis);
            confirmedAmountHolder[0] = currentBilling.amountMillimes;

            return null;
        }).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Sortie validée avec succès ! Montant confirmé : " + MoneyFormat.format(confirmedAmountHolder[0]), Toast.LENGTH_LONG).show();
            finish();
        }).addOnFailureListener(e -> {
            progressBar.setVisibility(View.GONE);
            btnValidateExit.setEnabled(true);
            if (btnValidateExitWallet != null) btnValidateExitWallet.setEnabled(true);
            btnCancelNoCharge.setEnabled(true);
            Toast.makeText(this, "Erreur: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void promptCancelReason() {
        EditText input = new EditText(this);
        input.setHint("Motif de l'annulation...");
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Annuler sans facturer")
                .setMessage("Veuillez indiquer le motif de cette annulation:")
                .setView(input)
                .setPositiveButton("Confirmer", (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Toast.makeText(this, "Le motif est obligatoire", Toast.LENGTH_SHORT).show();
                    } else {
                        executeCancelNoCharge(reason);
                    }
                })
                .setNegativeButton("Retour", null)
                .show();
    }

    private void executeCancelNoCharge(String reason) {
        btnValidateExit.setEnabled(false);
        if (btnValidateExitWallet != null) btnValidateExitWallet.setEnabled(false);
        btnCancelNoCharge.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference spotRef = db.collection("spots").document(spot.getSpotId());
            DocumentSnapshot spotSnap = transaction.get(spotRef);

            if (!"occupee".equals(spotSnap.getString("status"))) {
                throw new FirebaseFirestoreException("La place n'est plus occupée.", FirebaseFirestoreException.Code.ABORTED);
            }

            // Log event
            String eventId = db.collection("spot_events").document().getId();
            DocumentReference eventRef = db.collection("spot_events").document(eventId);
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("eventId", eventId);
            eventData.put("spotId", spot.getSpotId());
            eventData.put("action", "cancel_no_charge");
            eventData.put("reason", reason);
            eventData.put("plate", spot.getClientPlate());
            eventData.put("timestamp", System.currentTimeMillis());
            eventData.put("agentUid", uid);
            transaction.set(eventRef, eventData);

            // Free spot
            transaction.update(spotRef, "status", "libre", "clientId", "", "clientPlate", "", "reservedAt", 0, "startTime", 0);

            return null;
        }).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Sortie annulée, place libérée.", Toast.LENGTH_SHORT).show();
            finish();
        }).addOnFailureListener(e -> {
            progressBar.setVisibility(View.GONE);
            btnValidateExit.setEnabled(true);
            if (btnValidateExitWallet != null) btnValidateExitWallet.setEnabled(true);
            btnCancelNoCharge.setEnabled(true);
            Toast.makeText(this, "Erreur: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}