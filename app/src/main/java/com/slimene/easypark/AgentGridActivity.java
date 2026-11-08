package com.slimene.easypark;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.firebase.firestore.DocumentReference;
import com.slimene.easypark.AppConfig;
import com.slimene.easypark.utils.NavigationHelper;
import com.slimene.easypark.utils.RoleGuard;
import android.widget.Button;
import android.widget.ImageView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Locale;
import com.slimene.easypark.utils.StatusChipHelper;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import com.slimene.easypark.agent.ExitActivity;
import com.slimene.easypark.repository.SpotRepository;
import java.util.Objects;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AgentGridActivity extends AppCompatActivity {

    private AgentSpotAdapter adapter;
    private List<ParkingSpot> allSpotList;
    private List<ParkingSpot> filteredSpotList;

    private FirebaseFirestore db;
    private final SpotRepository spotRepository = new SpotRepository();
    private ListenerRegistration spotsListener;

    private Button btnTypeCar, btnTypeMoto;
    private String selectedParkType = "voiture";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agent_grid);

        FirebaseAuth        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        RoleGuard.require(this, "admin", "agent");



        RecyclerView rvAgentGrid = findViewById(R.id.rvAgentGrid);
        rvAgentGrid.setLayoutManager(new GridLayoutManager(this, 3));

        btnTypeCar = findViewById(R.id.btnTypeCar);
        btnTypeMoto = findViewById(R.id.btnTypeMoto);
        MaterialButtonToggleGroup toggleGroupType = findViewById(R.id.toggleGroupType);
        toggleGroupType.check(R.id.btnTypeCar);

        toggleGroupType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnTypeCar) {
                    selectedParkType = "voiture";
                } else if (checkedId == R.id.btnTypeMoto) {
                    selectedParkType = "moto";
                }
                filterSpots();
            }
        });

        ExtendedFloatingActionButton fabAddSpot = findViewById(R.id.fabAddSpot);
        fabAddSpot.setOnClickListener(v -> showAddSpotDialog());

        allSpotList = new ArrayList<>();
        filteredSpotList = new ArrayList<>();
        adapter = new AgentSpotAdapter(this::onAgentSpotClicked);
        rvAgentGrid.setAdapter(adapter);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        NavigationHelper.setupBottomNavigation(this, R.id.nav_admin_grid, true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        listenToSpots();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (spotsListener != null) {
            spotsListener.remove();
            spotsListener = null;
        }
    }

    // Removed setupBottomNavigation

    private void listenToSpots() {
        spotsListener = spotRepository.listenToSpots(new SpotRepository.SpotsCallback() {
            @Override
            public void onSpotsLoaded(List<ParkingSpot> spots) {
                allSpotList = spots;
                filterSpots();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(AgentGridActivity.this, "Erreur chargement: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterSpots() {
        filteredSpotList = new ArrayList<>();
        int carFree = 0, carTotal = 0;
        int motoFree = 0, motoTotal = 0;

        for (ParkingSpot spot : allSpotList) {
            String type = spot.getType() != null ? spot.getType() : "voiture";
            StatusChipHelper.StatusUi statusUi = StatusChipHelper.getStatusUi(this, spot);
            boolean isFree = "libre".equalsIgnoreCase(statusUi.effectiveStatus);

            if ("moto".equalsIgnoreCase(type)) {
                motoTotal++;
                if (isFree) motoFree++;
            } else {
                carTotal++;
                if (isFree) carFree++;
            }

            if (selectedParkType.equalsIgnoreCase(type)) {
                filteredSpotList.add(spot);
            }
        }

        if (btnTypeCar != null) {
            btnTypeCar.setText(String.format(Locale.getDefault(), "Voitures (%d/%d libres)", carFree, carTotal));
        }
        if (btnTypeMoto != null) {
            btnTypeMoto.setText(String.format(Locale.getDefault(), "Motos (%d/%d libres)", motoFree, motoTotal));
        }

        adapter.submitList(filteredSpotList);
    }

    private void showAddSpotDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_spot, null);
        dialog.setContentView(view);

        TextInputLayout tilSpotId = view.findViewById(R.id.tilSpotId);
        EditText etSpotId = view.findViewById(R.id.etSpotId);
        MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.toggleGroupSpotType);
        Button btnSubmit = view.findViewById(R.id.btnSubmitSpot);

        if ("moto".equalsIgnoreCase(selectedParkType)) {
            toggleGroup.check(R.id.btnSpotMoto);
        } else {
            toggleGroup.check(R.id.btnSpotVoiture);
        }

        btnSubmit.setOnClickListener(v -> {
            String spotId = etSpotId.getText().toString().trim().toUpperCase();
            String type = toggleGroup.getCheckedButtonId() == R.id.btnSpotMoto ? "moto" : "voiture";

            if (TextUtils.isEmpty(spotId)) {
                tilSpotId.setError("L'identifiant est obligatoire");
                return;
            }
            tilSpotId.setError(null);

            spotRepository.addSpot(spotId, type, new SpotRepository.ActionCallback() {
                @Override
                public void onSuccess() {
                    dialog.dismiss();
                    Snackbar.make(findViewById(android.R.id.content), "Place " + spotId + " créée !", Snackbar.LENGTH_SHORT).show();
                }

                @Override
                public void onError(Exception e) {
                    tilSpotId.setError(e.getMessage());
                }
            });
        });

        dialog.show();
    }

    private void onAgentSpotClicked(ParkingSpot spot) {
        String status = spot.getStatus() != null ? spot.getStatus() : "libre";

        if ("reservee".equalsIgnoreCase(status)) {
            long reservedAt = spot.getReservedAt();
            long minutesAgo = Math.max(0, (System.currentTimeMillis() - reservedAt) / (1000 * 60));
            String plate = !TextUtils.isEmpty(spot.getClientPlate()) ? spot.getClientPlate() : "Non spécifié";

            new AlertDialog.Builder(this)
                    .setTitle("Réservation - Place " + spot.getSpotId())
                    .setMessage("Matricule client : " + plate + "\n" +
                            "Réservée il y a : " + minutesAgo + " min")
                    .setPositiveButton("Confirmer l'arrivée (Occupée)", (dialog, which) -> confirmArrival(spot))
                    .setNegativeButton("Annuler la réservation (Libre)", (dialog, which) -> resetSpotToLibre(spot))
                    .setNeutralButton("Fermer", (dialog, which) -> dialog.dismiss())
                    .show();

        } else if ("occupee".equalsIgnoreCase(status)) {
            // Occupied spots open ExitActivity directly
            startActivity(new Intent(this, ExitActivity.class).putExtra("spotId", spot.getSpotId()));
        } else {
            // Free spot CRUD options
            showFreeSpotOptionsDialog(spot);
        }
    }

    private void showFreeSpotOptionsDialog(ParkingSpot spot) {
        String currentType = spot.getType() != null ? spot.getType() : "voiture";
        boolean isMoto = "moto".equalsIgnoreCase(currentType);
        String targetType = isMoto ? "voiture" : "moto";
        String changeLabel = "Changer le type en " + targetType.toUpperCase();

        String[] options = {changeLabel, "Supprimer cette place"};

        new AlertDialog.Builder(this)
                .setTitle("Place " + spot.getSpotId() + " (" + currentType.toUpperCase() + ") - Libre")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Change type
                        db.collection("spots").document(spot.getSpotId())
                                .update("type", targetType)
                                .addOnSuccessListener(aVoid -> Toast.makeText(AgentGridActivity.this, "Type modifié en " + targetType.toUpperCase(), Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(AgentGridActivity.this, "Erreur: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    } else if (which == 1) {
                        // Delete spot
                        confirmDeleteSpot(spot);
                    }
                })
                .setNegativeButton("Fermer", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void confirmDeleteSpot(ParkingSpot spot) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer la place " + spot.getSpotId())
                .setMessage("Voulez-vous vraiment supprimer définitivement cette place ?")
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    db.runTransaction((Transaction.Function<Void>) transaction -> {
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
                            throw new FirebaseFirestoreException("Impossible de supprimer une place non libre.", FirebaseFirestoreException.Code.ABORTED);
                        }

                        transaction.delete(spotRef);
                        return null;
                    }).addOnSuccessListener(aVoid -> Toast.makeText(AgentGridActivity.this, "Place " + spot.getSpotId() + " supprimée !", Toast.LENGTH_SHORT).show())
                      .addOnFailureListener(e -> Toast.makeText(AgentGridActivity.this, "Erreur suppression: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void confirmArrival(ParkingSpot spot) {
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference spotRef = db.collection("spots").document(spot.getSpotId());
            DocumentSnapshot spotSnap = transaction.get(spotRef);
            
            if (!"reservee".equals(spotSnap.getString("status"))) {
                throw new FirebaseFirestoreException("La place n'est pas en attente de réservation.", FirebaseFirestoreException.Code.ABORTED);
            }
            
            transaction.update(spotRef, "status", "occupee", "startTime", System.currentTimeMillis());
            return null;
        }).addOnSuccessListener(aVoid -> Toast.makeText(AgentGridActivity.this, "Le client est arrivé, place occupée !", Toast.LENGTH_SHORT).show())
          .addOnFailureListener(e -> Toast.makeText(AgentGridActivity.this, "Erreur: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void resetSpotToLibre(ParkingSpot spot) {
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference spotRef = db.collection("spots").document(spot.getSpotId());
            DocumentSnapshot spotSnap = transaction.get(spotRef);
            
            String status = spotSnap.getString("status");
            if ("libre".equals(status)) {
                return null;
            }
            
            transaction.update(spotRef, "status", "libre", "clientId", "", "clientPlate", "", "reservedAt", 0, "startTime", 0);
            return null;
        }).addOnSuccessListener(aVoid -> Toast.makeText(AgentGridActivity.this, "Place libérée.", Toast.LENGTH_SHORT).show())
          .addOnFailureListener(e -> Toast.makeText(AgentGridActivity.this, "Erreur: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (spotsListener != null) {
            spotsListener.remove();
        }
    }

    // RecyclerView Adapter for Agent Grid using ListAdapter
    private static class AgentSpotAdapter extends ListAdapter<ParkingSpot, AgentSpotAdapter.ViewHolder> {

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

        public AgentSpotAdapter(OnSpotClickListener listener) {
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

            boolean isMoto = "moto".equalsIgnoreCase(spot.getType());
            if (holder.ivSpotTypeIcon != null) {
                holder.ivSpotTypeIcon.setImageResource(isMoto ? R.drawable.ic_two_wheeler : R.drawable.ic_directions_car);
            }

            StatusChipHelper.StatusUi statusUi = StatusChipHelper.getStatusUi(holder.itemView.getContext(), spot);
            holder.tvSpotStatus.setText(statusUi.label);
            holder.tvSpotStatus.setTextColor(statusUi.textColor);
            holder.cardSpotStatus.setCardBackgroundColor(statusUi.containerColor);

            if (!TextUtils.isEmpty(spot.getClientPlate()) && !"libre".equalsIgnoreCase(statusUi.effectiveStatus)) {
                holder.tvClientPlate.setVisibility(View.VISIBLE);
                holder.tvClientPlate.setText(spot.getClientPlate());
            } else {
                holder.tvClientPlate.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> listener.onSpotClick(spot));
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CardView cardSpot, cardSpotStatus;
            TextView tvSpotId, tvSpotStatus, tvClientPlate;
            ImageView ivSpotTypeIcon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                cardSpot = itemView.findViewById(R.id.cardSpot);
                cardSpotStatus = itemView.findViewById(R.id.cardSpotStatus);
                tvSpotId = itemView.findViewById(R.id.tvSpotId);
                tvSpotStatus = itemView.findViewById(R.id.tvSpotStatus);
                tvClientPlate = itemView.findViewById(R.id.tvClientPlate);
                ivSpotTypeIcon = itemView.findViewById(R.id.ivSpotTypeIcon);
            }
        }
    }
}
