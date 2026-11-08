package com.slimene.easypark.agent;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.slimene.easypark.R;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import com.slimene.easypark.repository.TicketRepository;
import java.util.Objects;
import com.slimene.easypark.utils.DateUtils;
import com.slimene.easypark.utils.NavigationHelper;
import com.slimene.easypark.utils.RoleGuard;
import com.slimene.easypark.utils.MoneyFormat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.slimene.easypark.Ticket;

public class AgentHistoryActivity extends AppCompatActivity {

    private TextView tvTotalRevenue, tvFilteredTotal;
    private TabLayout tabLayoutFilter;
    private EditText etSearch;
    private RecyclerView rvHistory;
    private View emptyState;
    private View progressBar;

    private final TicketRepository ticketRepository = new TicketRepository();
    private DocumentSnapshot lastVisibleDoc = null;
    private boolean hasMore = false;
    private boolean isLoadingMore = false;
    private List<Ticket> allTickets = new ArrayList<>();
    private List<Ticket> filteredTickets = new ArrayList<>();
    private HistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agent_history);

        RoleGuard.require(this, "admin", "agent");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvTotalRevenue = findViewById(R.id.tvTotalRevenue);
        tvFilteredTotal = findViewById(R.id.tvFilteredTotal);
        tabLayoutFilter = findViewById(R.id.tabLayoutFilter);
        etSearch = findViewById(R.id.etSearch);
        rvHistory = findViewById(R.id.rvHistory);
        emptyState = findViewById(R.id.emptyState);
        progressBar = findViewById(R.id.progressBar);

        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        rvHistory.setAdapter(adapter);

        NavigationHelper.setupBottomNavigation(this, R.id.nav_admin_history, true);

        rvHistory.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && layoutManager.findLastCompletelyVisibleItemPosition() == filteredTickets.size() - 1) {
                    if (hasMore && !isLoadingMore) {
                        loadMoreTickets();
                    }
                }
            }
        });

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

        tabLayoutFilter.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                loadTickets();
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadTickets();
    }

    private void loadTickets() {
        progressBar.setVisibility(View.VISIBLE);
        int position = tabLayoutFilter.getSelectedTabPosition();

        long startTime = 0;
        long now = System.currentTimeMillis();

        if (position == 0) {
            startTime = DateUtils.getStartOfTodayMillis();
        } else if (position == 1) {
            startTime = now - (7 * 24 * 60 * 60 * 1000L);
        } else {
            startTime = 0;
        }

        lastVisibleDoc = null;
        isLoadingMore = true;

        ticketRepository.loadTicketsPage(startTime, null, new TicketRepository.TicketsPageCallback() {
            @Override
            public void onPageLoaded(List<Ticket> tickets, DocumentSnapshot lastDoc, boolean moreAvailable) {
                progressBar.setVisibility(View.GONE);
                isLoadingMore = false;
                allTickets = tickets;
                lastVisibleDoc = lastDoc;
                hasMore = moreAvailable;
                applyFilters();
            }

            @Override
            public void onError(Exception e) {
                progressBar.setVisibility(View.GONE);
                isLoadingMore = false;
                Toast.makeText(AgentHistoryActivity.this, "Erreur chargement: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMoreTickets() {
        if (lastVisibleDoc == null) return;
        isLoadingMore = true;
        progressBar.setVisibility(View.VISIBLE);

        int position = tabLayoutFilter.getSelectedTabPosition();
        long startTime = 0;
        long now = System.currentTimeMillis();

        if (position == 0) {
            startTime = DateUtils.getStartOfTodayMillis();
        } else if (position == 1) {
            startTime = now - (7 * 24 * 60 * 60 * 1000L);
        }

        ticketRepository.loadTicketsPage(startTime, lastVisibleDoc, new TicketRepository.TicketsPageCallback() {
            @Override
            public void onPageLoaded(List<Ticket> tickets, DocumentSnapshot lastDoc, boolean moreAvailable) {
                progressBar.setVisibility(View.GONE);
                isLoadingMore = false;
                allTickets.addAll(tickets);
                lastVisibleDoc = lastDoc;
                hasMore = moreAvailable;
                applyFilters();
            }

            @Override
            public void onError(Exception e) {
                progressBar.setVisibility(View.GONE);
                isLoadingMore = false;
            }
        });
    }

    private void applyFilters() {
        filteredTickets.clear();
        String query = etSearch.getText().toString().trim().toLowerCase();
        
        long totalMillimes = 0;

        for (Ticket t : allTickets) {
            String plate = t.getClientPlate().toLowerCase();
            if (plate.contains(query)) {
                filteredTickets.add(t);
                
                if (t.getAmountMillimes() != null) {
                    totalMillimes += t.getAmountMillimes();
                } else {
                    totalMillimes += Math.round(t.getTotalAmount() * 1000.0);
                }
            }
        }

        long totalPeriodMillimes = 0;
        for (Ticket t : allTickets) {
            long m = t.getAmountMillimes() != null ? t.getAmountMillimes() : Math.round(t.getTotalAmount() * 1000.0);
            totalPeriodMillimes += m;
        }
        tvTotalRevenue.setText(MoneyFormat.format(totalPeriodMillimes));

        tvFilteredTotal.setText("Total affiché : " + MoneyFormat.format(totalMillimes));

        if (filteredTickets.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvHistory.setVisibility(View.VISIBLE);
        }
        
        adapter.submitList(new ArrayList<>(filteredTickets));
    }

    private static final DiffUtil.ItemCallback<Ticket> DIFF_CALLBACK = new DiffUtil.ItemCallback<Ticket>() {
        @Override
        public boolean areItemsTheSame(@NonNull Ticket oldItem, @NonNull Ticket newItem) {
            return Objects.equals(oldItem.getTicketId(), newItem.getTicketId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Ticket oldItem, @NonNull Ticket newItem) {
            return Objects.equals(oldItem.getSpotId(), newItem.getSpotId()) &&
                   Objects.equals(oldItem.getClientPlate(), newItem.getClientPlate()) &&
                   Objects.equals(oldItem.getAmountMillimes(), newItem.getAmountMillimes());
        }
    };

    private class HistoryAdapter extends ListAdapter<Ticket, HistoryAdapter.ViewHolder> {
        private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        public HistoryAdapter() {
            super(DIFF_CALLBACK);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ticket, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Ticket t = getItem(position);
            
            String spot = t.getSpotId() != null ? t.getSpotId() : "--";
            String vType = t.getVehicleType() != null ? t.getVehicleType().toUpperCase() : "VOITURE";
            holder.tvTicketSpot.setText("Place " + spot + " (" + vType + ")");

            double amount = t.getAmountMillimes() != null ? t.getAmountMillimes() / 1000.0 : t.getTotalAmount();
            holder.tvTicketAmount.setText(String.format(Locale.getDefault(), "%.3f DT", amount));

            String plate = !t.getClientPlate().isEmpty() ? t.getClientPlate() : "Inconnu";
            holder.tvTicketPlate.setText("Matricule : " + plate);

            long duration = t.getDurationMinutes();
            holder.tvTicketDuration.setText("Durée : " + duration + " min");

            if (t.getTimestamp() > 0) {
                holder.tvTicketDate.setText(dateFormat.format(new Date(t.getTimestamp())));
            } else {
                holder.tvTicketDate.setText("--/--/----");
            }
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(AgentHistoryActivity.this, TicketReceiptActivity.class);
                intent.putExtra("ticketId", t.getTicketId());
                startActivity(intent);
            });
        }



        class ViewHolder extends RecyclerView.ViewHolder {
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