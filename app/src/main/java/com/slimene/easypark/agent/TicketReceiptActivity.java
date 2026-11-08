package com.slimene.easypark.agent;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slimene.easypark.R;
import com.slimene.easypark.Ticket;
import com.slimene.easypark.utils.RoleGuard;
import com.slimene.easypark.utils.MoneyFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TicketReceiptActivity extends AppCompatActivity {

    private TextView tvTotalAmount, tvTicketId, tvSpotDetails, tvPlate, tvEntryTime, tvExitTime, tvBilledHours;
    private Button btnShare;
    private FirebaseFirestore db;
    private Ticket ticket;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ticket_receipt);

        RoleGuard.require(this, "admin", "agent");

        db = FirebaseFirestore.getInstance();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        tvTicketId = findViewById(R.id.tvTicketId);
        tvSpotDetails = findViewById(R.id.tvSpotDetails);
        tvPlate = findViewById(R.id.tvPlate);
        tvEntryTime = findViewById(R.id.tvEntryTime);
        tvExitTime = findViewById(R.id.tvExitTime);
        tvBilledHours = findViewById(R.id.tvBilledHours);
        btnShare = findViewById(R.id.btnShare);

        String ticketId = getIntent().getStringExtra("ticketId");
        if (ticketId == null) {
            finish();
            return;
        }

        loadTicket(ticketId);
        
        btnShare.setOnClickListener(v -> shareReceipt());
    }

    private void loadTicket(String ticketId) {
        db.collection("tickets").document(ticketId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        ticket = snapshot.toObject(Ticket.class);
                        if (ticket != null) {
                            bindUI();
                        } else {
                            finish();
                        }
                    } else {
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erreur: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void bindUI() {
        tvTicketId.setText(ticket.getTicketId());
        
        String spot = ticket.getSpotId() != null ? ticket.getSpotId() : "--";
        String vType = ticket.getVehicleType() != null ? ticket.getVehicleType().toUpperCase() : "VOITURE";
        tvSpotDetails.setText(spot + " (" + vType + ")");
        
        tvPlate.setText(!ticket.getClientPlate().isEmpty() ? ticket.getClientPlate() : "Inconnu");

        long amountMillimes = ticket.getAmountMillimes() != null ? ticket.getAmountMillimes() : Math.round(ticket.getTotalAmount() * 1000.0);
        tvTotalAmount.setText(MoneyFormat.format(amountMillimes));

        long entry = ticket.getEntryTime() != null ? ticket.getEntryTime() : ticket.getTimestamp() - (ticket.getDurationMinutes() * 60000L);
        long exit = ticket.getExitTime() != null ? ticket.getExitTime() : ticket.getTimestamp();

        tvEntryTime.setText(timeFormat.format(new Date(entry)));
        tvExitTime.setText(timeFormat.format(new Date(exit)));
        
        long billed = ticket.getBilledHours() != null ? ticket.getBilledHours() : (long) Math.ceil(ticket.getDurationMinutes() / 60.0);
        tvBilledHours.setText(billed + " h");
    }

    private void shareReceipt() {
        if (ticket == null) return;
        
        long amountMillimes = ticket.getAmountMillimes() != null ? ticket.getAmountMillimes() : Math.round(ticket.getTotalAmount() * 1000.0);
        String amountStr = MoneyFormat.format(amountMillimes);
        
        long entry = ticket.getEntryTime() != null ? ticket.getEntryTime() : ticket.getTimestamp() - (ticket.getDurationMinutes() * 60000L);
        long exit = ticket.getExitTime() != null ? ticket.getExitTime() : ticket.getTimestamp();
        long billed = ticket.getBilledHours() != null ? ticket.getBilledHours() : (long) Math.ceil(ticket.getDurationMinutes() / 60.0);

        String receiptText = "Reçu EasyPark\n" +
                "--------------------\n" +
                "ID: " + ticket.getTicketId() + "\n" +
                "Place: " + ticket.getSpotId() + "\n" +
                "Matricule: " + ticket.getClientPlate() + "\n" +
                "Entrée: " + timeFormat.format(new Date(entry)) + "\n" +
                "Sortie: " + timeFormat.format(new Date(exit)) + "\n" +
                "Durée facturée: " + billed + " h\n" +
                "--------------------\n" +
                "Montant Total: " + amountStr + "\n";

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, receiptText);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Partager le reçu");
        startActivity(shareIntent);
    }
}