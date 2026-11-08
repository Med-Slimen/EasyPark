package com.slimene.easypark;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.slimene.easypark.utils.NavigationHelper;
import com.slimene.easypark.agent.AddVehicleEntryActivity;
import com.slimene.easypark.agent.PresentVehiclesActivity;
import com.slimene.easypark.agent.AgentHistoryActivity;
import android.widget.GridLayout;
import androidx.cardview.widget.CardView;
import com.slimene.easypark.repository.StatsRepository;
import com.slimene.easypark.repository.SpotRepository;
import com.slimene.easypark.repository.Tariff;
import com.slimene.easypark.repository.TariffRepository;
import com.slimene.easypark.utils.MoneyFormat;
import com.slimene.easypark.utils.RoleGuard;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AgentDashboardActivity extends AppCompatActivity {

    private TextView tvTotalRevenue, tvTotalParkedNow, tvGreeting, tvDate;
    private TextView tvCarParkedNow, tvCarFreeCount;
    private TextView tvMotoParkedNow, tvMotoFreeCount;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private LinearProgressIndicator progressCar, progressMoto;
    private final StatsRepository statsRepository = new StatsRepository();
    private final SpotRepository spotRepository = new SpotRepository();
    private ListenerRegistration spotsListener;
    private ListenerRegistration revenueListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agent_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        RoleGuard.require(this, "admin", "agent");

        tvTotalRevenue = findViewById(R.id.tvTotalRevenue);
        tvTotalParkedNow = findViewById(R.id.tvTotalParkedNow);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvDate = findViewById(R.id.tvDate);

        tvCarParkedNow = findViewById(R.id.tvCarParkedNow);
        tvCarFreeCount = findViewById(R.id.tvCarFreeCount);

        tvMotoParkedNow = findViewById(R.id.tvMotoParkedNow);
        tvMotoFreeCount = findViewById(R.id.tvMotoFreeCount);

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE d MMMM", Locale.FRENCH);
        String todayStr = dateFormat.format(new Date());
        if (!todayStr.isEmpty()) {
            todayStr = Character.toUpperCase(todayStr.charAt(0)) + todayStr.substring(1);
        }
        tvDate.setText(todayStr);

        if (mAuth.getCurrentUser() != null) {
            db.collection("users").document(mAuth.getCurrentUser().getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String name = doc.getString("fullName");
                            if (name != null && !name.isEmpty()) {
                                String firstName = name.split(" ")[0];
                                tvGreeting.setText("Bonjour, " + firstName);
                            }
                        }
                    });
        }

        progressCar = findViewById(R.id.progressCar);
        progressMoto = findViewById(R.id.progressMoto);
        
        ImageButton btnProfile = findViewById(R.id.btnProfile);
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        NavigationHelper.setupBottomNavigation(this, R.id.nav_admin_dash, true);

        CardView cardAddVehicle = findViewById(R.id.cardAddVehicle);
        CardView cardPresentVehicles = findViewById(R.id.cardPresentVehicles);
        CardView cardQuickExit = findViewById(R.id.cardQuickExit);
        CardView cardHistory = findViewById(R.id.cardHistory);

        cardAddVehicle.setOnClickListener(v -> startActivity(new Intent(this, AddVehicleEntryActivity.class)));
        cardPresentVehicles.setOnClickListener(v -> startActivity(new Intent(this, PresentVehiclesActivity.class)));
        
        // For quick exit, launch PresentVehiclesActivity and tell it to focus search, or launch a quick plate dialog.
        // For simplicity, launch PresentVehiclesActivity and it can act as the exit hub.
        cardQuickExit.setOnClickListener(v -> startActivity(new Intent(this, PresentVehiclesActivity.class)));
        cardHistory.setOnClickListener(v -> startActivity(new Intent(this, AgentHistoryActivity.class)));
    }

    private final TariffRepository.TariffObserver tariffObserver = new TariffRepository.TariffObserver() {
        @Override
        public void onTariffUpdated(Tariff tariff) {}
    };

    @Override
    protected void onStart() {
        super.onStart();
        TariffRepository.getInstance().addObserver(tariffObserver);
        listenToSpotsStats();
        listenToRevenue();
    }

    @Override
    protected void onStop() {
        super.onStop();
        TariffRepository.getInstance().removeObserver(tariffObserver);
        if (spotsListener != null) {
            spotsListener.remove();
            spotsListener = null;
        }
        if (revenueListener != null) {
            revenueListener.remove();
            revenueListener = null;
        }
    }

    // Removed setupBottomNavigation

    private void listenToSpotsStats() {
        spotsListener = db.collection("spots")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) return;
                    if (querySnapshot != null) {
                        int carLibre = 0, carReservee = 0, carOccupee = 0;
                        int motoLibre = 0, motoReservee = 0, motoOccupee = 0;

                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String type = doc.getString("type");
                            String status = doc.getString("status");

                            boolean isMoto = "moto".equalsIgnoreCase(type);

                            if ("libre".equalsIgnoreCase(status)) {
                                if (isMoto) motoLibre++; else carLibre++;
                            } else if ("reservee".equalsIgnoreCase(status)) {
                                if (isMoto) motoReservee++; else carReservee++;
                            } else if ("occupee".equalsIgnoreCase(status)) {
                                if (isMoto) motoOccupee++; else carOccupee++;
                            }
                        }

                        int carTotal = carLibre + carReservee + carOccupee;
                        int motoTotal = motoLibre + motoReservee + motoOccupee;
                        
                        int carParked = carReservee + carOccupee;
                        int motoParked = motoReservee + motoOccupee;
                        int totalParked = carParked + motoParked;

                        tvTotalParkedNow.setText(totalParked + " véhicule" + (totalParked > 1 ? "s" : "") + " garé" + (totalParked > 1 ? "s" : "") + " actuellement");

                        tvCarParkedNow.setText(carParked + " garées");
                        tvCarFreeCount.setText(carLibre + " libres • " + carReservee + " rés. • " + carOccupee + " occ.");
                        
                        tvMotoParkedNow.setText(motoParked + " garées");
                        tvMotoFreeCount.setText(motoLibre + " libres • " + motoReservee + " rés. • " + motoOccupee + " occ.");
                        
                        if (carTotal > 0) {
                            progressCar.setProgress((int) ((carParked / (float) carTotal) * 100));
                        }
                        if (motoTotal > 0) {
                            progressMoto.setProgress((int) ((motoParked / (float) motoTotal) * 100));
                        }
                    }
                });
    }

    private void listenToRevenue() {
        revenueListener = statsRepository.listenTodayRevenue((totalDinars, totalMillimes) -> {
            tvTotalRevenue.setText(MoneyFormat.format(totalMillimes));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (spotsListener != null) spotsListener.remove();
        if (revenueListener != null) revenueListener.remove();
    }
}
