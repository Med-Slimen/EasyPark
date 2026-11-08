package com.slimene.easypark;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.slimene.easypark.utils.CardValidator;
import com.slimene.easypark.utils.FakePaymentGateway;
import com.slimene.easypark.utils.MoneyFormat;
import com.slimene.easypark.utils.NavigationHelper;
import com.slimene.easypark.utils.PaymentGateway;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import java.util.Objects;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.slimene.easypark.utils.RoleGuard;

public class ClientWalletActivity extends AppCompatActivity {

    private TextView tvCurrentBalance, tvEmptyTransactions;
    private TextInputLayout tilCardholderName, tilCardNumber, tilExpiryDate, tilCvv, tilAmount;
    private EditText etCardholderName, etCardNumber, etExpiryDate, etCvv, etAmount;
    private ChipGroup chipGroupAmounts;
    private Chip chip10, chip20, chip50;
    private Button btnRecharge;
    private ProgressBar progressBar;
    private RecyclerView rvTransactions;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration balanceListener;
    private ListenerRegistration topUpsListener;
    private ListenerRegistration ticketsListener;

    private PaymentGateway paymentGateway = new FakePaymentGateway();
    private List<WalletTxItem> transactionItems = new ArrayList<>();
    private TxAdapter adapter;

    private List<TopUp> loadedTopUps = new ArrayList<>();
    private List<Ticket> loadedTickets = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_wallet);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        RoleGuard.require(this, "client");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);

        // Bind views
        tvCurrentBalance = findViewById(R.id.tvCurrentBalance);
        tvEmptyTransactions = findViewById(R.id.tvEmptyTransactions);
        
        tilCardholderName = findViewById(R.id.tilCardholderName);
        tilCardNumber = findViewById(R.id.tilCardNumber);
        tilExpiryDate = findViewById(R.id.tilExpiryDate);
        tilCvv = findViewById(R.id.tilCvv);
        tilAmount = findViewById(R.id.tilAmount);

        etCardholderName = findViewById(R.id.etCardholderName);
        etCardNumber = findViewById(R.id.etCardNumber);
        etExpiryDate = findViewById(R.id.etExpiryDate);
        etCvv = findViewById(R.id.etCvv);
        etAmount = findViewById(R.id.etAmount);

        chipGroupAmounts = findViewById(R.id.chipGroupAmounts);
        chip10 = findViewById(R.id.chip10);
        chip20 = findViewById(R.id.chip20);
        chip50 = findViewById(R.id.chip50);

        btnRecharge = findViewById(R.id.btnRecharge);
        progressBar = findViewById(R.id.progressBar);
        rvTransactions = findViewById(R.id.rvTransactions);

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TxAdapter();
        rvTransactions.setAdapter(adapter);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        
        boolean isDetail = getIntent().getBooleanExtra("is_detail", false);
        if (isDetail) {
            bottomNav.setVisibility(View.GONE);
            toolbar.setNavigationOnClickListener(v -> finish());
        } else {
            bottomNav.setVisibility(View.VISIBLE);
            NavigationHelper.setupBottomNavigation(this, R.id.nav_wallet, false);
        }

        setupFormattersAndChips();
        btnRecharge.setOnClickListener(v -> performRecharge());
    }

    @Override
    protected void onStart() {
        super.onStart();
        listenCurrentBalance();
        listenTransactions();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (balanceListener != null) {
            balanceListener.remove();
            balanceListener = null;
        }
        if (topUpsListener != null) {
            topUpsListener.remove();
            topUpsListener = null;
        }
        if (ticketsListener != null) {
            ticketsListener.remove();
            ticketsListener = null;
        }
    }

    private void setupFormattersAndChips() {
        // Auto-space card number every 4 digits
        etCardNumber.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String clean = s.toString().replaceAll("\\s+", "");
                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < clean.length(); i++) {
                    if (i > 0 && i % 4 == 0) {
                        formatted.append(" ");
                    }
                    formatted.append(clean.charAt(i));
                }

                s.replace(0, s.length(), formatted.toString());
                isFormatting = false;
            }
        });

        // Auto-slash expiry date MM/YY
        etExpiryDate.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String clean = s.toString().replaceAll("[^\\d]", "");
                if (clean.length() >= 2) {
                    String mm = clean.substring(0, 2);
                    String yy = clean.substring(2);
                    String formatted = mm + "/" + yy;
                    s.replace(0, s.length(), formatted);
                } else {
                    s.replace(0, s.length(), clean);
                }
                isFormatting = false;
            }
        });

        // Quick amount chips
        chipGroupAmounts.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip10)) {
                etAmount.setText("10");
            } else if (checkedIds.contains(R.id.chip20)) {
                etAmount.setText("20");
            } else if (checkedIds.contains(R.id.chip50)) {
                etAmount.setText("50");
            }
        });
    }

    private void listenCurrentBalance() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        balanceListener = db.collection("users").document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        Double balance = snapshot.getDouble("balance");
                        double currentBalance = balance != null ? balance : 0.0;
                        tvCurrentBalance.setText(MoneyFormat.format(Math.round(currentBalance * 1000.0)));
                    }
                });
    }

    private void listenTransactions() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        topUpsListener = db.collection("top_ups").whereEqualTo("clientId", uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null) {
                        loadedTopUps.clear();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            TopUp t = doc.toObject(TopUp.class);
                            if (t != null) loadedTopUps.add(t);
                        }
                        mergeAndRenderTransactions();
                    }
                });

        ticketsListener = db.collection("tickets").whereEqualTo("clientId", uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null) {
                        loadedTickets.clear();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Ticket t = doc.toObject(Ticket.class);
                            if (t != null) loadedTickets.add(t);
                        }
                        mergeAndRenderTransactions();
                    }
                });
    }

    private void mergeAndRenderTransactions() {
        transactionItems.clear();

        for (TopUp tu : loadedTopUps) {
            String card4 = tu.getCardLast4() != null ? tu.getCardLast4() : "••••";
            long amountMillimes = Math.round(tu.getAmount() * 1000.0);
            transactionItems.add(new WalletTxItem("Recharge (Carte •••• " + card4 + ")", tu.getTimestamp(), amountMillimes, true));
        }

        for (Ticket tk : loadedTickets) {
            String source = tk.getSource() != null ? tk.getSource() : "";
            // Filter: Cash tickets paid to an agent (e.g. agent_cash) must not appear as wallet debits!
            boolean isWalletPayment = "client_wallet".equalsIgnoreCase(source) || "wallet".equalsIgnoreCase(source) || "agent_wallet".equalsIgnoreCase(source);
            if (!isWalletPayment && !source.isEmpty()) {
                continue;
            }

            long amountMillimes = tk.getAmountMillimes() != null ? tk.getAmountMillimes() : Math.round(tk.getTotalAmount() * 1000.0);
            String spot = tk.getSpotId() != null ? tk.getSpotId() : "--";
            transactionItems.add(new WalletTxItem("Paiement Stationnement " + spot, tk.getTimestamp(), amountMillimes, false));
        }

        Collections.sort(transactionItems, (a, b) -> Long.compare(b.timestamp, a.timestamp));

        if (transactionItems.isEmpty()) {
            tvEmptyTransactions.setVisibility(View.VISIBLE);
            rvTransactions.setVisibility(View.GONE);
        } else {
            tvEmptyTransactions.setVisibility(View.GONE);
            rvTransactions.setVisibility(View.VISIBLE);
        }
        adapter.submitList(new ArrayList<>(transactionItems));
    }

    private void performRecharge() {
        String name = etCardholderName.getText().toString().trim();
        String cardNumber = etCardNumber.getText().toString().trim();
        String expiry = etExpiryDate.getText().toString().trim();
        String cvv = etCvv.getText().toString().trim();
        String amountStr = etAmount.getText().toString().trim();

        tilCardholderName.setError(null);
        tilCardNumber.setError(null);
        tilExpiryDate.setError(null);
        tilCvv.setError(null);
        tilAmount.setError(null);

        if (name.isEmpty()) {
            tilCardholderName.setError("Veuillez entrer le nom du titulaire");
            return;
        }

        String cleanCard = cardNumber.replaceAll("\\s+", "");
        if (!CardValidator.isValidLuhn(cleanCard)) {
            tilCardNumber.setError("Numéro de carte invalide (Échec Luhn)");
            return;
        }

        if (!CardValidator.isValidExpiry(expiry)) {
            tilExpiryDate.setError("Date d'expiration invalide ou expirée");
            return;
        }

        if (!CardValidator.isValidCvv(cleanCard, cvv)) {
            tilCvv.setError("CVV invalide");
            return;
        }

        if (amountStr.isEmpty()) {
            tilAmount.setError("Veuillez entrer le montant à recharger");
            return;
        }

        double amount;
        try {
            amount = MoneyFormat.parseDT(amountStr) / 1000.0;
        } catch (Exception e) {
            tilAmount.setError("Montant invalide");
            return;
        }

        if (amount < 1.0) {
            tilAmount.setError("Le montant minimum de recharge est de 1.000 DT");
            return;
        }

        if (amount > 500.0) {
            tilAmount.setError("Le montant maximum de recharge est de 500.000 DT");
            return;
        }

        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        btnRecharge.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        paymentGateway.processPayment(name, cleanCard, expiry, cvv, amount, new PaymentGateway.PaymentCallback() {
            @Override
            public void onSuccess() {
                // Execute WriteBatch for atomic update of balance & top-up record
                WriteBatch batch = db.batch();

                String topUpId = db.collection("top_ups").document().getId();
                DocumentReference topUpRef = db.collection("top_ups").document(topUpId);

                String cardLast4 = cleanCard.length() >= 4 ? cleanCard.substring(cleanCard.length() - 4) : cleanCard;
                TopUp topUp = new TopUp(topUpId, uid, amount, cardLast4, System.currentTimeMillis());
                batch.set(topUpRef, topUp);

                DocumentReference userRef = db.collection("users").document(uid);
                batch.update(userRef, "balance", FieldValue.increment(amount));

                batch.commit().addOnSuccessListener(aVoid -> {
                    btnRecharge.setEnabled(true);
                    progressBar.setVisibility(View.GONE);

                    etCardholderName.setText("");
                    etCardNumber.setText("");
                    etExpiryDate.setText("");
                    etCvv.setText("");
                    etAmount.setText("");
                    chipGroupAmounts.clearCheck();

                    boolean detail = getIntent().getBooleanExtra("is_detail", false);
                    if (detail) {
                        Toast.makeText(ClientWalletActivity.this, "Recharge effectuée avec succès !", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Snackbar.make(findViewById(android.R.id.content), "Recharge de " + MoneyFormat.format(Math.round(amount * 1000.0)) + " effectuée !", Snackbar.LENGTH_SHORT).show();
                    }
                }).addOnFailureListener(e -> {
                    btnRecharge.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ClientWalletActivity.this, "Erreur enregistrement: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                btnRecharge.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ClientWalletActivity.this, "Échec paiement: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (balanceListener != null) balanceListener.remove();
        if (topUpsListener != null) topUpsListener.remove();
        if (ticketsListener != null) ticketsListener.remove();
    }

    private static class WalletTxItem {
        String title;
        long timestamp;
        long amountMillimes;
        boolean isPositive;

        WalletTxItem(String title, long timestamp, long amountMillimes, boolean isPositive) {
            this.title = title;
            this.timestamp = timestamp;
            this.amountMillimes = amountMillimes;
            this.isPositive = isPositive;
        }
    }

    private static final DiffUtil.ItemCallback<WalletTxItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<WalletTxItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull WalletTxItem oldItem, @NonNull WalletTxItem newItem) {
            return oldItem.timestamp == newItem.timestamp && Objects.equals(oldItem.title, newItem.title);
        }

        @Override
        public boolean areContentsTheSame(@NonNull WalletTxItem oldItem, @NonNull WalletTxItem newItem) {
            return oldItem.amountMillimes == newItem.amountMillimes &&
                   oldItem.isPositive == newItem.isPositive;
        }
    };

    private class TxAdapter extends ListAdapter<WalletTxItem, TxAdapter.ViewHolder> {
        private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        public TxAdapter() {
            super(DIFF_CALLBACK);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_topup, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WalletTxItem item = getItem(position);

            holder.tvTxTitle.setText(item.title);
            holder.tvTxDate.setText(dateFormat.format(new Date(item.timestamp)));

            if (item.isPositive) {
                holder.tvTxAmount.setText("+ " + MoneyFormat.format(item.amountMillimes));
                holder.tvTxAmount.setTextColor(0xFF16A34A); // Green
                holder.ivTxIcon.setImageResource(R.drawable.ic_account_balance_wallet);
            } else {
                holder.tvTxAmount.setText("- " + MoneyFormat.format(item.amountMillimes));
                holder.tvTxAmount.setTextColor(0xFFB91C1C); // Red
                holder.ivTxIcon.setImageResource(R.drawable.ic_receipt_long);
            }
        }



        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTxTitle, tvTxDate, tvTxAmount;
            ImageView ivTxIcon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTxTitle = itemView.findViewById(R.id.tvTxTitle);
                tvTxDate = itemView.findViewById(R.id.tvTxDate);
                tvTxAmount = itemView.findViewById(R.id.tvTxAmount);
                ivTxIcon = itemView.findViewById(R.id.ivTxIcon);
            }
        }
    }
}
