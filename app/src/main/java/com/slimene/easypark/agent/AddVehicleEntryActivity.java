package com.slimene.easypark.agent;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.slimene.easypark.utils.RoleGuard;
import com.slimene.easypark.ParkingSpot;
import com.slimene.easypark.R;

import java.util.ArrayList;
import java.util.List;

public class AddVehicleEntryActivity extends AppCompatActivity {

    private EditText etPlate;
    private MaterialButtonToggleGroup toggleGroupType;
    private AutoCompleteTextView actvSpot;
    private Button btnConfirm;
    private ProgressBar progressBar;

    private FirebaseFirestore db;
    private List<ParkingSpot> availableSpots = new ArrayList<>();
    private ArrayAdapter<String> spotAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_vehicle_entry);

        RoleGuard.require(this, "admin", "agent");

        db = FirebaseFirestore.getInstance();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etPlate = findViewById(R.id.etPlate);
        toggleGroupType = findViewById(R.id.toggleGroupType);
        actvSpot = findViewById(R.id.actvSpot);
        btnConfirm = findViewById(R.id.btnConfirm);
        progressBar = findViewById(R.id.progressBar);

        spotAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        actvSpot.setAdapter(spotAdapter);

        toggleGroupType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                loadFreeSpots(getSelectedType());
            }
        });

        btnConfirm.setOnClickListener(v -> handleConfirm());

        // Load initially for "voiture"
        loadFreeSpots("voiture");
    }

    private String getSelectedType() {
        if (toggleGroupType.getCheckedButtonId() == R.id.btnTypeMoto) {
            return "moto";
        }
        return "voiture";
    }

    private void loadFreeSpots(String type) {
        actvSpot.setText("");
        db.collection("spots")
                .whereEqualTo("type", type)
                .whereEqualTo("status", "libre")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    availableSpots.clear();
                    List<String> spotNames = new ArrayList<>();
                    spotNames.add("Auto-assigner la première libre");

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        ParkingSpot spot = doc.toObject(ParkingSpot.class);
                        if (spot != null) {
                            availableSpots.add(spot);
                            spotNames.add(spot.getSpotId());
                        }
                    }

                    spotAdapter.clear();
                    spotAdapter.addAll(spotNames);
                    spotAdapter.notifyDataSetChanged();

                    if (!spotNames.isEmpty()) {
                        actvSpot.setText(spotNames.get(0), false);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Erreur de chargement des places", Toast.LENGTH_SHORT).show());
    }

    private void handleConfirm() {
        String plate = etPlate.getText().toString().trim().toUpperCase();
        if (TextUtils.isEmpty(plate)) {
            etPlate.setError("Veuillez saisir la matricule");
            return;
        }
        // Normalize plate: remove extra spaces
        plate = plate.replaceAll("\\s+", " ");

        String selectedSpotText = actvSpot.getText().toString();
        if (TextUtils.isEmpty(selectedSpotText)) {
            Toast.makeText(this, "Aucune place disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalPlate = plate;
        final String type = getSelectedType();
        
        btnConfirm.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        // 1. Check if plate is already parked (status == occupee or reservee and plate matches)
        // We can't do complex OR queries easily without multiple queries, but let's query spots where clientPlate == finalPlate
        db.collection("spots").whereEqualTo("clientPlate", finalPlate).get().addOnSuccessListener(snapshot -> {
            boolean alreadyParked = false;
            for (DocumentSnapshot doc : snapshot) {
                String status = doc.getString("status");
                if ("occupee".equals(status) || "reservee".equals(status)) {
                    alreadyParked = true;
                    break;
                }
            }

            if (alreadyParked) {
                progressBar.setVisibility(View.GONE);
                btnConfirm.setEnabled(true);
                Toast.makeText(this, "Ce véhicule est déjà dans le parking.", Toast.LENGTH_LONG).show();
                return;
            }

            // Determine spotId
            String targetSpotId = null;
            if (selectedSpotText.equals("Auto-assigner la première libre") && !availableSpots.isEmpty()) {
                targetSpotId = availableSpots.get(0).getSpotId();
            } else if (!selectedSpotText.equals("Auto-assigner la première libre")) {
                targetSpotId = selectedSpotText;
            }

            if (targetSpotId == null) {
                progressBar.setVisibility(View.GONE);
                btnConfirm.setEnabled(true);
                Toast.makeText(this, "Impossible d'assigner une place.", Toast.LENGTH_SHORT).show();
                return;
            }

            final String spotId = targetSpotId;
            
            // Transaction to assign
            db.runTransaction((Transaction.Function<Void>) transaction -> {
                DocumentReference spotRef = db.collection("spots").document(spotId);
                DocumentSnapshot spotSnap = transaction.get(spotRef);
                
                if (!"libre".equals(spotSnap.getString("status"))) {
                    throw new FirebaseFirestoreException("La place " + spotId + " n'est plus libre.", FirebaseFirestoreException.Code.ABORTED);
                }
                
                transaction.update(spotRef, "status", "occupee", "clientPlate", finalPlate, "startTime", System.currentTimeMillis());
                return null;
            }).addOnSuccessListener(aVoid -> {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Entrée validée sur la place " + spotId, Toast.LENGTH_SHORT).show();
                finish();
            }).addOnFailureListener(e -> {
                progressBar.setVisibility(View.GONE);
                btnConfirm.setEnabled(true);
                Toast.makeText(this, "Erreur: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                loadFreeSpots(type); // reload spots
            });

        }).addOnFailureListener(e -> {
            progressBar.setVisibility(View.GONE);
            btnConfirm.setEnabled(true);
            Toast.makeText(this, "Erreur vérification: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }
}