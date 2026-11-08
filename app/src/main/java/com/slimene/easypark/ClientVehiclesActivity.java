package com.slimene.easypark;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slimene.easypark.utils.NavigationHelper;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import java.util.Objects;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import android.widget.Button;
import android.widget.ImageView;

import com.slimene.easypark.utils.RoleGuard;

public class ClientVehiclesActivity extends AppCompatActivity {

    private RecyclerView rvVehicles;
    private TextView tvEmpty;
    private ProgressBar progressBar;

    private VehicleAdapter adapter;
    private List<Vehicle> vehicleList;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration vehiclesListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_vehicles);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        RoleGuard.require(this, "client");



        rvVehicles = findViewById(R.id.rvVehicles);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);
        ExtendedFloatingActionButton fabAddVehicle = findViewById(R.id.fabAddVehicle);
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);

        rvVehicles.setLayoutManager(new LinearLayoutManager(this));
        vehicleList = new ArrayList<>();
        adapter = new VehicleAdapter(this::deleteVehicle);
        rvVehicles.setAdapter(adapter);

        fabAddVehicle.setOnClickListener(v -> showAddVehicleDialog());

        NavigationHelper.setupBottomNavigation(this, R.id.nav_vehicles, false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        listenToVehicles();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (vehiclesListener != null) {
            vehiclesListener.remove();
            vehiclesListener = null;
        }
    }

    // Removed setupBottomNavigation

    private void listenToVehicles() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        vehiclesListener = db.collection("vehicles")
                .whereEqualTo("clientId", uid)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(ClientVehiclesActivity.this, "Erreur chargement: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (querySnapshot != null) {
                        vehicleList = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Vehicle v = doc.toObject(Vehicle.class);
                            if (v != null) {
                                vehicleList.add(v);
                            }
                        }

                        if (vehicleList.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvVehicles.setVisibility(View.GONE);
                        } else {
                            tvEmpty.setVisibility(View.GONE);
                            rvVehicles.setVisibility(View.VISIBLE);
                        }
                        adapter.submitList(vehicleList);
                    }
                });
    }

    private void showAddVehicleDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_vehicle, null);
        dialog.setContentView(view);

        TextInputLayout tilPlate = view.findViewById(R.id.tilPlate);
        EditText etPlate = view.findViewById(R.id.etPlate);
        EditText etBrand = view.findViewById(R.id.etBrand);
        MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.toggleGroupType);
        Button btnSubmit = view.findViewById(R.id.btnSubmitVehicle);

        btnSubmit.setOnClickListener(v -> {
            String plate = etPlate.getText().toString().trim().toUpperCase();
            String brand = etBrand.getText().toString().trim();
            String type = toggleGroup.getCheckedButtonId() == R.id.btnTypeMoto ? "moto" : "voiture";

            if (TextUtils.isEmpty(plate)) {
                tilPlate.setError("La matricule est obligatoire");
                return;
            }
            tilPlate.setError(null);

            saveVehicleToFirestore(plate, brand, type);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveVehicleToFirestore(String plate, String brand, String type) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        progressBar.setVisibility(View.VISIBLE);

        String vehicleId = db.collection("vehicles").document().getId();
        Vehicle vehicle = new Vehicle(vehicleId, uid, plate, brand, type);

        db.collection("vehicles").document(vehicleId)
                .set(vehicle)
                .addOnSuccessListener(aVoid -> {
                    // Brief 800ms loading delay
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(ClientVehiclesActivity.this, "Véhicule ajouté !", Toast.LENGTH_SHORT).show();
                    }, 800);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ClientVehiclesActivity.this, "Erreur d'ajout: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteVehicle(Vehicle vehicle) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer le véhicule")
                .setMessage("Voulez-vous vraiment supprimer le véhicule " + vehicle.getPlate() + " ?")
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    db.collection("vehicles").document(vehicle.getVehicleId())
                            .delete()
                            .addOnSuccessListener(aVoid -> Toast.makeText(ClientVehiclesActivity.this, "Véhicule supprimé !", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(ClientVehiclesActivity.this, "Erreur suppression: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (vehiclesListener != null) {
            vehiclesListener.remove();
        }
    }

    // RecyclerView Adapter for Vehicles using ListAdapter
    private static class VehicleAdapter extends ListAdapter<Vehicle, VehicleAdapter.ViewHolder> {

        private static final DiffUtil.ItemCallback<Vehicle> DIFF_CALLBACK = new DiffUtil.ItemCallback<Vehicle>() {
            @Override
            public boolean areItemsTheSame(@NonNull Vehicle oldItem, @NonNull Vehicle newItem) {
                return Objects.equals(oldItem.getVehicleId(), newItem.getVehicleId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull Vehicle oldItem, @NonNull Vehicle newItem) {
                return Objects.equals(oldItem.getPlate(), newItem.getPlate()) &&
                       Objects.equals(oldItem.getBrand(), newItem.getBrand()) &&
                       Objects.equals(oldItem.getType(), newItem.getType());
            }
        };

        interface OnDeleteClickListener {
            void onDeleteClick(Vehicle vehicle);
        }

        private final OnDeleteClickListener deleteListener;

        public VehicleAdapter(OnDeleteClickListener deleteListener) {
            super(DIFF_CALLBACK);
            this.deleteListener = deleteListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vehicle, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Vehicle v = getItem(position);

            holder.tvPlate.setText(v.getPlate() != null ? v.getPlate() : "--");
            holder.tvBrand.setText(v.getBrand() != null && !v.getBrand().isEmpty() ? v.getBrand() : "Modèle non spécifié");

            String type = v.getType() != null ? v.getType() : "voiture";
            boolean isMoto = "moto".equalsIgnoreCase(type);
            if (holder.ivVehicleType != null) {
                holder.ivVehicleType.setImageResource(isMoto ? R.drawable.ic_two_wheeler : R.drawable.ic_directions_car);
            }

            holder.btnDelete.setOnClickListener(view -> deleteListener.onDeleteClick(v));
        }



        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPlate, tvBrand, tvTypeBadge;
            ImageButton btnDelete;
            ImageView ivVehicleType;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvPlate = itemView.findViewById(R.id.tvPlate);
                tvBrand = itemView.findViewById(R.id.tvBrand);
                tvTypeBadge = itemView.findViewById(R.id.tvTypeBadge);
                btnDelete = itemView.findViewById(R.id.btnDeleteVehicle);
                ivVehicleType = itemView.findViewById(R.id.ivVehicleType);
            }
        }
    }
}
