package com.slimene.easypark.agent;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.slimene.easypark.ParkingSpot;
import com.slimene.easypark.R;
import com.slimene.easypark.utils.BillingCalculator;
import com.slimene.easypark.utils.MoneyFormat;
import com.slimene.easypark.utils.StatusChipHelper;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import com.slimene.easypark.repository.Tariff;
import com.slimene.easypark.repository.TariffRepository;
import java.util.Objects;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.slimene.easypark.utils.RoleGuard;
import com.slimene.easypark.AppConfig;

public class PresentVehiclesActivity extends AppCompatActivity {

    private EditText etSearch;
    private ChipGroup chipGroupFilter;
    private RecyclerView rvPresent;
    private View emptyState;
    private View progressBar;

    private FirebaseFirestore db;
    private ListenerRegistration spotsListener;

    private List<ParkingSpot> allParkedSpots = new ArrayList<>();
    private List<ParkingSpot> filteredSpots = new ArrayList<>();
    private PresentAdapter adapter;

    private Tariff currentTariff = Tariff.unloaded();

    private Handler tickerHandler;
    private Runnable tickerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_present_vehicles);

        RoleGuard.require(this, "admin", "agent");

        db = FirebaseFirestore.getInstance();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etSearch = findViewById(R.id.etSearch);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);
        rvPresent = findViewById(R.id.rvPresent);
        emptyState = findViewById(R.id.emptyState);
        progressBar = findViewById(R.id.progressBar);

        rvPresent.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PresentAdapter();
        rvPresent.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
    }

    private final TariffRepository.TariffObserver tariffObserver = tariff -> {
        this.currentTariff = tariff;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        TariffRepository.getInstance().addObserver(tariffObserver);
        tickerHandler = new Handler(Looper.getMainLooper());
        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                adapter.notifyDataSetChanged();
                tickerHandler.postDelayed(this, 30000); // refresh every 30s
            }
        };
        tickerHandler.post(tickerRunnable);

        loadSpots();
    }

    @Override
    protected void onStop() {
        super.onStop();
        TariffRepository.getInstance().removeObserver(tariffObserver);
        if (spotsListener != null) {
            spotsListener.remove();
            spotsListener = null;
        }
        if (tickerHandler != null && tickerRunnable != null) {
            tickerHandler.removeCallbacks(tickerRunnable);
            tickerHandler = null;
        }
    }



    private void loadSpots() {
        progressBar.setVisibility(View.VISIBLE);
        spotsListener = db.collection("spots")
                .addSnapshotListener((snapshot, error) -> {
                    progressBar.setVisibility(View.GONE);
                    if (error != null) {
                        Toast.makeText(this, "Erreur: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snapshot != null) {
                        allParkedSpots.clear();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            ParkingSpot spot = doc.toObject(ParkingSpot.class);
                            if (spot != null) {
                                String status = spot.getStatus();
                                if ("occupee".equalsIgnoreCase(status) || "reservee".equalsIgnoreCase(status)) {
                                    allParkedSpots.add(spot);
                                }
                            }
                        }
                        applyFilters();
                    }
                });
    }

    private void applyFilters() {
        filteredSpots.clear();
        String query = etSearch.getText().toString().trim().toLowerCase();
        int checkedChip = chipGroupFilter.getCheckedChipId();

        for (ParkingSpot spot : allParkedSpots) {
            String plate = spot.getClientPlate().toLowerCase();
            if (!plate.contains(query)) continue;

            String type = spot.getType() != null ? spot.getType().toLowerCase() : "voiture";
            String status = spot.getStatus() != null ? spot.getStatus().toLowerCase() : "";

            boolean matchFilter = false;
            if (checkedChip == R.id.chipAll) {
                matchFilter = true;
            } else if (checkedChip == R.id.chipVoiture && "voiture".equals(type)) {
                matchFilter = true;
            } else if (checkedChip == R.id.chipMoto && "moto".equals(type)) {
                matchFilter = true;
            } else if (checkedChip == R.id.chipReservee && "reservee".equals(status)) {
                matchFilter = true;
            }

            if (matchFilter) {
                filteredSpots.add(spot);
            }
        }

        if (filteredSpots.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvPresent.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvPresent.setVisibility(View.VISIBLE);
        }
        adapter.submitList(new ArrayList<>(filteredSpots));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (spotsListener != null) spotsListener.remove();
        if (tickerHandler != null) tickerHandler.removeCallbacks(tickerRunnable);
    }

    private static final DiffUtil.ItemCallback<ParkingSpot> DIFF_CALLBACK = new DiffUtil.ItemCallback<ParkingSpot>() {
        @Override
        public boolean areItemsTheSame(@NonNull ParkingSpot oldItem, @NonNull ParkingSpot newItem) {
            return Objects.equals(oldItem.getSpotId(), newItem.getSpotId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ParkingSpot oldItem, @NonNull ParkingSpot newItem) {
            return Objects.equals(oldItem.getStatus(), newItem.getStatus()) &&
                   Objects.equals(oldItem.getClientPlate(), newItem.getClientPlate()) &&
                   oldItem.getStartTime() == newItem.getStartTime();
        }
    };

    private class PresentAdapter extends ListAdapter<ParkingSpot, PresentAdapter.ViewHolder> {
        private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        public PresentAdapter() {
            super(DIFF_CALLBACK);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_present_vehicle, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ParkingSpot spot = getItem(position);
            holder.tvPlate.setText(!spot.getClientPlate().isEmpty() ? spot.getClientPlate() : "Inconnu");

            StatusChipHelper.StatusUi statusUi = StatusChipHelper.getStatusUi(holder.itemView.getContext(), spot);
            holder.tvSpotStatus.setText(spot.getSpotId() + " • " + statusUi.label);
            holder.tvSpotStatus.setTextColor(statusUi.textColor);
            holder.cardSpotStatus.setCardBackgroundColor(statusUi.containerColor);

            boolean isMoto = "moto".equalsIgnoreCase(spot.getType());
            holder.ivType.setImageResource(isMoto ? R.drawable.ic_two_wheeler : R.drawable.ic_directions_car);

            long startTimeMillis = spot.getStartTime() > 0 ? spot.getStartTime() : spot.getReservedAt();
            if (startTimeMillis > 0) {
                holder.tvEntryTime.setText("Entrée: " + timeFormat.format(new Date(startTimeMillis)));
                long durationMillis = System.currentTimeMillis() - startTimeMillis;
                long durationMins = durationMillis / 60000;
                holder.tvDuration.setText(durationMins + " min");

                if ("occupee".equalsIgnoreCase(statusUi.effectiveStatus)) {
                    long activeRateMillimes = isMoto ? currentTariff.getMotoHourlyRateMillimes() : currentTariff.getCarHourlyRateMillimes();
                    BillingCalculator.BillingResult billing = BillingCalculator.compute(startTimeMillis, System.currentTimeMillis(), activeRateMillimes, AppConfig.GRACE_PERIOD_MINUTES);
                    holder.tvRunningCost.setText(MoneyFormat.format(billing.amountMillimes));
                } else {
                    holder.tvRunningCost.setText("-");
                }
            } else {
                holder.tvEntryTime.setText("Entrée: --");
                holder.tvDuration.setText("--");
                holder.tvRunningCost.setText("-");
            }

            holder.itemView.setOnClickListener(v -> {
                if ("occupee".equalsIgnoreCase(statusUi.effectiveStatus)) {
                    Intent intent = new Intent(PresentVehiclesActivity.this, ExitActivity.class);
                    intent.putExtra("spotId", spot.getSpotId());
                    startActivity(intent);
                } else {
                    Toast.makeText(PresentVehiclesActivity.this, "Seules les places occupées peuvent être traitées pour la sortie.", Toast.LENGTH_SHORT).show();
                }
            });
        }



        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPlate, tvSpotStatus, tvEntryTime, tvDuration, tvRunningCost;
            ImageView ivType;
            CardView cardSpotStatus;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvPlate = itemView.findViewById(R.id.tvPlate);
                tvSpotStatus = itemView.findViewById(R.id.tvSpotStatus);
                tvEntryTime = itemView.findViewById(R.id.tvEntryTime);
                tvDuration = itemView.findViewById(R.id.tvDuration);
                tvRunningCost = itemView.findViewById(R.id.tvRunningCost);
                ivType = itemView.findViewById(R.id.ivType);
                cardSpotStatus = itemView.findViewById(R.id.cardSpotStatus);
            }
        }
    }
}