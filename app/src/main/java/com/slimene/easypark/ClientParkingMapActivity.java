package com.slimene.easypark;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.firebase.firestore.DocumentReference;
import com.slimene.easypark.AppConfig;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import com.slimene.easypark.repository.SpotRepository;
import com.slimene.easypark.utils.MoneyFormat;
import com.slimene.easypark.repository.Tariff;
import com.slimene.easypark.repository.TariffRepository;
import java.util.Objects;
import com.slimene.easypark.utils.RoleGuard;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.slimene.easypark.utils.StatusChipHelper;

public class ClientParkingMapActivity extends AppCompatActivity {

    private TextView tvHourlyRate;
    private ProgressBar progressBar;

    private SpotAdapter adapter;
    private List<ParkingSpot> allSpotList;
    private List<ParkingSpot> filteredSpotList;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private final SpotRepository spotRepository = new SpotRepository();
    private ListenerRegistration spotsListener;
    private ListenerRegistration userListener;

    private String selectedParkType = "voiture"; // "voiture" or "moto"
    private Tariff currentTariff = Tariff.unloaded();
    private double userBalance = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_parking_map);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        RoleGuard.require(this, "client");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvHourlyRate = findViewById(R.id.tvHourlyRate);
        RecyclerView rvSpots = findViewById(R.id.rvSpots);
        progressBar = findViewById(R.id.progressBar);

        MaterialButtonToggleGroup toggleGroupType = findViewById(R.id.toggleGroupType);
        toggleGroupType.check(R.id.btnTypeCar);

        toggleGroupType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnTypeCar) {
                    selectedParkType = "voiture";
                } else if (checkedId == R.id.btnTypeMoto) {
                    selectedParkType = "moto";
                }
                updateUIForSelectedType();
            }
        });

        rvSpots.setLayoutManager(new GridLayoutManager(this, 3));
        allSpotList = new ArrayList<>();
        filteredSpotList = new ArrayList<>();

        adapter = new SpotAdapter(this::onSpotClicked);
        rvSpots.setAdapter(adapter);
    }

    private final TariffRepository.TariffObserver tariffObserver = tariff -> {
        this.currentTariff = tariff;
        updateUIForSelectedType();
    };

    @Override
    protected void onStart() {
        super.onStart();
        TariffRepository.getInstance().addObserver(tariffObserver);
        listenToUserBalance();
        listenToSpots();
    }

    @Override
    protected void onStop() {
        super.onStop();
        TariffRepository.getInstance().removeObserver(tariffObserver);
        if (spotsListener != null) {
            spotsListener.remove();
            spotsListener = null;
        }
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }

    private void listenToUserBalance() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        userListener = db.collection("users").document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null && snapshot.exists()) {
                        Double bal = snapshot.getDouble("balance");
                        userBalance = bal != null ? bal : 0.0;
                    }
                });
    }



    private void listenToSpots() {
        spotsListener = spotRepository.listenToSpots(new SpotRepository.SpotsCallback() {
            @Override
            public void onSpotsLoaded(List<ParkingSpot> spots) {
                allSpotList = spots;
                filterSpots();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(ClientParkingMapActivity.this, "Erreur chargement places: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUIForSelectedType() {
        long activeRateMillimes = "moto".equalsIgnoreCase(selectedParkType) ? currentTariff.getMotoHourlyRateMillimes() : currentTariff.getCarHourlyRateMillimes();
        tvHourlyRate.setText("Tarif : " + MoneyFormat.format(activeRateMillimes) + " / h");
        filterSpots();
    }

    private void filterSpots() {
        filteredSpotList = new ArrayList<>();
        for (ParkingSpot spot : allSpotList) {
            String spotType = spot.getType() != null ? spot.getType() : "voiture";
            if (selectedParkType.equalsIgnoreCase(spotType)) {
                filteredSpotList.add(spot);
            }
        }
        adapter.submitList(filteredSpotList);
    }

    private void onSpotClicked(ParkingSpot spot) {
        StatusChipHelper.StatusUi statusUi = StatusChipHelper.getStatusUi(this, spot);
        String status = statusUi.effectiveStatus;

        if (!"libre".equalsIgnoreCase(status)) {
            Toast.makeText(this, "Cette place n'est pas disponible (" + status + ")", Toast.LENGTH_SHORT).show();
            return;
        }

        long activeHourlyRateMillimes = "moto".equalsIgnoreCase(selectedParkType) ? currentTariff.getMotoHourlyRateMillimes() : currentTariff.getCarHourlyRateMillimes();
        long minRequiredBalanceMillimes = activeHourlyRateMillimes; // At least 1 billed hour required
        long userBalanceMillimes = Math.round(userBalance * 1000.0);

        if (userBalanceMillimes < minRequiredBalanceMillimes) {
            String requiredStr = MoneyFormat.format(minRequiredBalanceMillimes);
            String msg = "Solde insuffisant. Vous devez disposer d'au moins " + requiredStr + " (1 heure de tarif) pour réserver cette place.";

            new AlertDialog.Builder(this)
                    .setTitle("Solde insuffisant")
                    .setMessage(msg)
                    .setPositiveButton("Recharger mon portefeuille", (dialog, which) -> {
                        startActivity(new Intent(ClientParkingMapActivity.this, ClientWalletActivity.class));
                    })
                    .setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss())
                    .show();
            return;
        }

        // Query user's saved vehicles for selectedParkType ("voiture" or "moto")
        queryVehiclesAndPromptReservation(spot);
    }

    private void queryVehiclesAndPromptReservation(ParkingSpot spot) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        progressBar.setVisibility(View.VISIBLE);

        db.collection("vehicles")
                .whereEqualTo("clientId", uid)
                .whereEqualTo("type", selectedParkType)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    progressBar.setVisibility(View.GONE);

                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        String vehicleTypeName = "moto".equalsIgnoreCase(selectedParkType) ? "moto" : "voiture";
                        new AlertDialog.Builder(ClientParkingMapActivity.this)
                                .setTitle("Aucun véhicule")
                                .setMessage("Vous n'avez aucune " + vehicleTypeName + " enregistrée. Veuillez ajouter un véhicule d'abord.")
                                .setPositiveButton("Ajouter un véhicule", (dialog, which) -> {
                                    startActivity(new Intent(ClientParkingMapActivity.this, ClientVehiclesActivity.class));
                                })
                                .setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss())
                                .show();
                        return;
                    }

                    List<Vehicle> matchingVehicles = new ArrayList<>();
                    List<String> displayStrings = new ArrayList<>();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Vehicle v = doc.toObject(Vehicle.class);
                        if (v != null) {
                            matchingVehicles.add(v);
                            String brand = v.getBrand() != null && !v.getBrand().isEmpty() ? v.getBrand() : "Véhicule";
                            displayStrings.add(brand + " - " + v.getPlate());
                        }
                    }

                    showVehicleSelectionDialog(spot, matchingVehicles, displayStrings);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ClientParkingMapActivity.this, "Erreur véhicules: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showVehicleSelectionDialog(ParkingSpot spot, List<Vehicle> vehicles, List<String> displayItems) {
        final int[] selectedIndex = {0};

        String[] itemsArray = displayItems.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Réserver la place " + spot.getSpotId())
                .setSingleChoiceItems(itemsArray, 0, (dialog, which) -> selectedIndex[0] = which)
                .setPositiveButton("Confirmer la réservation", (dialog, which) -> {
                    Vehicle selectedVehicle = vehicles.get(selectedIndex[0]);
                    executeReservation(spot, selectedVehicle.getPlate());
                })
                .setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void executeReservation(ParkingSpot spot, String selectedPlate) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        progressBar.setVisibility(View.VISIBLE);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference userRef = db.collection("users").document(uid);
            DocumentSnapshot userSnap = transaction.get(userRef);

            // Check if user already holds an active reservation/session
            String activeSpotId = userSnap.getString("activeSpotId");
            if (activeSpotId != null && !activeSpotId.isEmpty() && !activeSpotId.equalsIgnoreCase(spot.getSpotId())) {
                DocumentReference prevSpotRef = db.collection("spots").document(activeSpotId);
                DocumentSnapshot prevSpotSnap = transaction.get(prevSpotRef);
                if (prevSpotSnap.exists()) {
                    String prevStatus = prevSpotSnap.getString("status");
                    Long prevReservedAt = prevSpotSnap.getLong("reservedAt");
                    boolean prevExpired = false;
                    if ("reservee".equalsIgnoreCase(prevStatus) && prevReservedAt != null) {
                        if (System.currentTimeMillis() - prevReservedAt > AppConfig.MAX_RESERVATION_MINUTES * 60 * 1000L) {
                            prevExpired = true;
                        }
                    }
                    if (!prevExpired && ("reservee".equalsIgnoreCase(prevStatus) || "occupee".equalsIgnoreCase(prevStatus))) {
                        throw new FirebaseFirestoreException("Vous avez déjà une réservation ou stationnement actif (" + activeSpotId + ").", FirebaseFirestoreException.Code.ABORTED);
                    }
                }
            }

            DocumentReference spotRef = db.collection("spots").document(spot.getSpotId());
            DocumentSnapshot spotSnap = transaction.get(spotRef);

            String spotStatus = spotSnap.getString("status");
            Long reservedAt = spotSnap.getLong("reservedAt");
            boolean isExpired = false;

            if ("reservee".equalsIgnoreCase(spotStatus) && reservedAt != null) {
                long maxDurationMillis = AppConfig.MAX_RESERVATION_MINUTES * 60 * 1000L;
                if (reservedAt > 0 && System.currentTimeMillis() - reservedAt > maxDurationMillis) {
                    isExpired = true;
                }
            }

            if (!"libre".equalsIgnoreCase(spotStatus) && !isExpired) {
                throw new FirebaseFirestoreException("La place n'est plus disponible.", FirebaseFirestoreException.Code.ABORTED);
            }

            transaction.update(spotRef, "status", "reservee", "clientId", uid, "clientPlate", selectedPlate, "reservedAt", System.currentTimeMillis());
            transaction.update(userRef, "activeSpotId", spot.getSpotId());
            return null;
        }).addOnSuccessListener(aVoid -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(ClientParkingMapActivity.this, "Place " + spot.getSpotId() + " réservée !", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(ClientParkingMapActivity.this, ClientReservationActivity.class));
            finish();
        }).addOnFailureListener(e -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(ClientParkingMapActivity.this, "Échec réservation: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (spotsListener != null) spotsListener.remove();
        if (userListener != null) userListener.remove();
    }

    // RecyclerView Adapter for Spots using ListAdapter
    private static class SpotAdapter extends ListAdapter<ParkingSpot, SpotAdapter.ViewHolder> {

        private static final DiffUtil.ItemCallback<ParkingSpot> DIFF_CALLBACK = new DiffUtil.ItemCallback<ParkingSpot>() {
            @Override
            public boolean areItemsTheSame(@NonNull ParkingSpot oldItem, @NonNull ParkingSpot newItem) {
                return Objects.equals(oldItem.getSpotId(), newItem.getSpotId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull ParkingSpot oldItem, @NonNull ParkingSpot newItem) {
                return Objects.equals(oldItem.getStatus(), newItem.getStatus()) &&
                       Objects.equals(oldItem.getClientPlate(), newItem.getClientPlate()) &&
                       Objects.equals(oldItem.getType(), newItem.getType());
            }
        };

        interface OnSpotClickListener {
            void onSpotClick(ParkingSpot spot);
        }

        private final OnSpotClickListener listener;

        public SpotAdapter(OnSpotClickListener listener) {
            super(DIFF_CALLBACK);
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_parking_spot, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ParkingSpot spot = getItem(position);
            holder.tvSpotId.setText(spot.getSpotId());

            StatusChipHelper.StatusUi statusUi = StatusChipHelper.getStatusUi(holder.itemView.getContext(), spot);
            holder.tvSpotStatus.setText(statusUi.label);
            holder.tvSpotStatus.setTextColor(statusUi.textColor);
            holder.cardSpotStatus.setCardBackgroundColor(statusUi.containerColor);

            holder.itemView.setOnClickListener(v -> listener.onSpotClick(spot));
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CardView cardSpot, cardSpotStatus;
            TextView tvSpotId, tvSpotStatus;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                cardSpot = itemView.findViewById(R.id.cardSpot);
                cardSpotStatus = itemView.findViewById(R.id.cardSpotStatus);
                tvSpotId = itemView.findViewById(R.id.tvSpotId);
                tvSpotStatus = itemView.findViewById(R.id.tvSpotStatus);
            }
        }
    }
}
