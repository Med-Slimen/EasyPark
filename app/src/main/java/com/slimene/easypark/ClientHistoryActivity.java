package com.slimene.easypark;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slimene.easypark.utils.NavigationHelper;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import java.util.Objects;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.Collections;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.slimene.easypark.utils.RoleGuard;

public class ClientHistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private View emptyState;
    private View progressBar;

    private ClientTicketAdapter adapter;
    private List<Ticket> ticketList;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration ticketsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_history);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        RoleGuard.require(this, "client");



        rvHistory = findViewById(R.id.rvHistory);
        emptyState = findViewById(R.id.emptyState);
        progressBar = findViewById(R.id.progressBar);

        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        ticketList = new ArrayList<>();
        adapter = new ClientTicketAdapter();
        rvHistory.setAdapter(adapter);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        NavigationHelper.setupBottomNavigation(this, R.id.nav_history, false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        listenToTickets();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (ticketsListener != null) {
            ticketsListener.remove();
            ticketsListener = null;
        }
    }

    // Removed setupBottomNavigation

    private void listenToTickets() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        ticketsListener = db.collection("tickets")
                .whereEqualTo("clientId", uid)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (error != null) {
                        Toast.makeText(ClientHistoryActivity.this, "Erreur chargement: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (querySnapshot != null) {
                        ticketList = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Ticket t = doc.toObject(Ticket.class);
                            if (t != null) {
                                ticketList.add(t);
                            }
                        }

                        Collections.sort(ticketList, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                        if (ticketList.isEmpty()) {
                            if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                            rvHistory.setVisibility(View.GONE);
                        } else {
                            if (emptyState != null) emptyState.setVisibility(View.GONE);
                            rvHistory.setVisibility(View.VISIBLE);
                        }
                        adapter.submitList(ticketList);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ticketsListener != null) {
            ticketsListener.remove();
        }
    }

    // Client Ticket RecyclerView Adapter using ListAdapter
    private static class ClientTicketAdapter extends ListAdapter<Ticket, ClientTicketAdapter.ViewHolder> {

        private static final DiffUtil.ItemCallback<Ticket> DIFF_CALLBACK = new DiffUtil.ItemCallback<Ticket>() {
            @Override
            public boolean areItemsTheSame(@NonNull Ticket oldItem, @NonNull Ticket newItem) {
                return Objects.equals(oldItem.getTicketId(), newItem.getTicketId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull Ticket oldItem, @NonNull Ticket newItem) {
                return Objects.equals(oldItem.getSpotId(), newItem.getSpotId()) &&
                       Objects.equals(oldItem.getAmountMillimes(), newItem.getAmountMillimes());
            }
        };

        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        public ClientTicketAdapter() {
            super(DIFF_CALLBACK);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ticket, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Ticket ticket = getItem(position);

            String spot = ticket.getSpotId() != null ? ticket.getSpotId() : "--";
            String vehicleType = ticket.getVehicleType() != null ? ticket.getVehicleType().toUpperCase() : "VOITURE";
            Context ctx = holder.itemView.getContext();
            holder.tvTicketSpot.setText(ctx.getString(R.string.spot_format, spot + " (" + vehicleType + ")"));

            holder.tvTicketAmount.setText(ctx.getString(R.string.currency_format, ticket.getTotalAmount()));

            String plate = !ticket.getClientPlate().isEmpty() ? ticket.getClientPlate() : "Inconnu";
            holder.tvTicketPlate.setText(ctx.getString(R.string.plate_format, plate));

            holder.tvTicketDuration.setText(ctx.getString(R.string.duration_format, String.valueOf(ticket.getDurationMinutes())));

            if (ticket.getTimestamp() > 0) {
                holder.tvTicketDate.setText(dateFormat.format(new Date(ticket.getTimestamp())));
            } else {
                holder.tvTicketDate.setText("--/--/----");
            }
        }



        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTicketSpot, tvTicketAmount, tvTicketPlate, tvTicketDuration, tvTicketDate;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTicketSpot = itemView.findViewById(R.id.tvTicketSpot);
                tvTicketAmount = itemView.findViewById(R.id.tvTicketAmount);
                tvTicketPlate = itemView.findViewById(R.id.tvTicketPlate);
                tvTicketDuration = itemView.findViewById(R.id.tvTicketDuration);
                tvTicketDate = itemView.findViewById(R.id.tvTicketDate);
            }
        }
    }
}
