package com.slimene.easypark;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.slimene.easypark.repository.Tariff;
import com.slimene.easypark.repository.TariffRepository;
import com.slimene.easypark.utils.MoneyFormat;
import com.slimene.easypark.utils.NavigationHelper;
import com.slimene.easypark.utils.RoleGuard;

import java.util.Locale;

public class ClientHomeActivity extends AppCompatActivity {

    private TextView tvWelcome, tvBalance, tvCarSpots, tvCarRate, tvMotoSpots, tvMotoRate;
    private View cardActiveBanner;
    private TextView tvBannerSpot, tvBannerTimer;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ListenerRegistration userListener;
    private ListenerRegistration spotsListener;
    private ListenerRegistration activeSpotListener;

    private ParkingSpot activeSpot;
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private final Runnable bannerRunnable = new Runnable() {
        @Override
        public void run() {
            updateBannerTimer();
            bannerHandler.postDelayed(this, 1000);
        }
    };

    private final TariffRepository.TariffObserver tariffObserver = new TariffRepository.TariffObserver() {
        @Override
        public void onTariffUpdated(Tariff tariff) {
            if (tvCarRate != null) tvCarRate.setText(MoneyFormat.format(tariff.getCarHourlyRateMillimes()) + " / h");
            if (tvMotoRate != null) tvMotoRate.setText(MoneyFormat.format(tariff.getMotoHourlyRateMillimes()) + " / h");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_home);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        RoleGuard.require(this, "client");

        // Bind UI elements
        tvWelcome = findViewById(R.id.tvWelcome);
        tvBalance = findViewById(R.id.tvBalance);
        tvCarSpots = findViewById(R.id.tvCarSpots);
        tvCarRate = findViewById(R.id.tvCarRate);
        tvMotoSpots = findViewById(R.id.tvMotoSpots);
        tvMotoRate = findViewById(R.id.tvMotoRate);

        cardActiveBanner = findViewById(R.id.cardActiveBanner);
        tvBannerSpot = findViewById(R.id.tvBannerSpot);
        tvBannerTimer = findViewById(R.id.tvBannerTimer);

        Button btnQuickRecharge = findViewById(R.id.btnQuickRecharge);
        Button btnSelectSpot = findViewById(R.id.btnSelectSpot);
        Button btnActiveReservation = findViewById(R.id.btnActiveReservation);
        ImageButton btnProfile = findViewById(R.id.btnProfile);

        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> startActivity(new Intent(ClientHomeActivity.this, ProfileActivity.class)));
        }

        NavigationHelper.setupBottomNavigation(this, R.id.nav_home, false);

        btnQuickRecharge.setOnClickListener(v -> startActivity(new Intent(ClientHomeActivity.this, ClientWalletActivity.class).putExtra("is_detail", true)));
        btnSelectSpot.setOnClickListener(v -> startActivity(new Intent(ClientHomeActivity.this, ClientParkingMapActivity.class)));

        View.OnClickListener openReservationListener = v -> startActivity(new Intent(ClientHomeActivity.this, ClientReservationActivity.class));
        btnActiveReservation.setOnClickListener(openReservationListener);
        if (cardActiveBanner != null) {
            cardActiveBanner.setOnClickListener(openReservationListener);
        }

        setupRealtimeListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        TariffRepository.getInstance().addObserver(tariffObserver);
        bannerHandler.post(bannerRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        TariffRepository.getInstance().removeObserver(tariffObserver);
        bannerHandler.removeCallbacks(bannerRunnable);
    }

    private void setupRealtimeListeners() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        // 1. User listener for balance and name
        userListener = db.collection("users").document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        String fullName = snapshot.getString("fullName");
                        Double balance = snapshot.getDouble("balance");

                        if (fullName != null && !fullName.isEmpty()) {
                            tvWelcome.setText("Bienvenue, " + fullName + " !");
                        }
                        double currentBalance = balance != null ? balance : 0.0;
                        long balanceMillimes = Math.round(currentBalance * 1000.0);
                        tvBalance.setText(MoneyFormat.format(balanceMillimes));
                    }
                });

        // 2. Free spots count
        spotsListener = db.collection("spots")
                .whereEqualTo("status", "libre")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) return;
                    if (querySnapshot != null) {
                        int carCount = 0;
                        int motoCount = 0;

                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String type = doc.getString("type");
                            if ("moto".equalsIgnoreCase(type)) {
                                motoCount++;
                            } else {
                                carCount++;
                            }
                        }

                        tvCarSpots.setText(carCount + " place" + (carCount > 1 ? "s" : ""));
                        tvMotoSpots.setText(motoCount + " place" + (motoCount > 1 ? "s" : ""));
                    }
                });

        // 3. Active spot reservation/session listener
        activeSpotListener = db.collection("spots")
                .whereEqualTo("clientId", uid)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null || querySnapshot == null || querySnapshot.isEmpty()) {
                        activeSpot = null;
                        if (cardActiveBanner != null) cardActiveBanner.setVisibility(View.GONE);
                        return;
                    }

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        ParkingSpot spot = doc.toObject(ParkingSpot.class);
                        if (spot != null && !"libre".equalsIgnoreCase(spot.getStatus())) {
                            activeSpot = spot;
                            if (cardActiveBanner != null) cardActiveBanner.setVisibility(View.VISIBLE);
                            String statusLabel = "reservee".equalsIgnoreCase(spot.getStatus()) ? "Réservation en cours" : "Stationnement en cours";
                            tvBannerSpot.setText("Place " + spot.getSpotId() + " • " + statusLabel);
                            updateBannerTimer();
                            return;
                        }
                    }

                    activeSpot = null;
                    if (cardActiveBanner != null) cardActiveBanner.setVisibility(View.GONE);
                });
    }

    private void updateBannerTimer() {
        if (activeSpot == null || tvBannerTimer == null) return;

        String status = activeSpot.getStatus() != null ? activeSpot.getStatus() : "";
        if ("reservee".equalsIgnoreCase(status)) {
            long reservedAt = activeSpot.getReservedAt();
            long maxDurationMillis = AppConfig.MAX_RESERVATION_MINUTES * 60 * 1000L;
            long remainingMillis = (reservedAt + maxDurationMillis) - System.currentTimeMillis();

            if (remainingMillis <= 0) {
                tvBannerTimer.setText("Expirée");
            } else {
                long minutes = (remainingMillis / 1000) / 60;
                long seconds = (remainingMillis / 1000) % 60;
                tvBannerTimer.setText(String.format(Locale.getDefault(), "Temps restant : %02d:%02d", minutes, seconds));
            }
        } else if ("occupee".equalsIgnoreCase(status)) {
            long startTime = activeSpot.getStartTime();
            if (startTime <= 0) startTime = System.currentTimeMillis();

            long elapsedMillis = Math.max(0, System.currentTimeMillis() - startTime);
            long totalSeconds = elapsedMillis / 1000;
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;

            if (hours > 0) {
                tvBannerTimer.setText(String.format(Locale.getDefault(), "Durée : %02d:%02d:%02d", hours, minutes, seconds));
            } else {
                tvBannerTimer.setText(String.format(Locale.getDefault(), "Durée : %02d:%02d", minutes, seconds));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null) userListener.remove();
        if (spotsListener != null) spotsListener.remove();
        if (activeSpotListener != null) activeSpotListener.remove();
    }
}
