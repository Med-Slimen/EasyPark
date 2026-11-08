package com.slimene.easypark;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slimene.easypark.utils.NavigationHelper;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import com.slimene.easypark.repository.UserRepository;
import java.util.Objects;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import android.widget.Button;
import com.google.android.material.textfield.TextInputLayout;
import com.slimene.easypark.utils.RoleGuard;

public class AdminUsersActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private ExtendedFloatingActionButton fabAddAdmin;

    private UserAdapter adapter;
    private List<User> allUserList;
    private List<User> filteredUserList;

    private FirebaseFirestore db;
    private final UserRepository userRepository = new UserRepository();
    private ListenerRegistration usersListener;

    private String selectedTabRole = "client"; // "client" or "admin"
    private String currentAdminUid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_users);

        FirebaseAuth        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        RoleGuard.require(this, "admin");

        if (mAuth.getCurrentUser() != null) {
            currentAdminUid = mAuth.getCurrentUser().getUid();
        }



        RecyclerView rvUsers = findViewById(R.id.rvUsers);
        progressBar = findViewById(R.id.progressBar);
        fabAddAdmin = findViewById(R.id.fabAddAdmin);

        MaterialButtonToggleGroup toggleGroupRole = findViewById(R.id.toggleGroupRole);
        toggleGroupRole.check(R.id.btnRoleClients);

        toggleGroupRole.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnRoleClients) {
                    selectedTabRole = "client";
                    fabAddAdmin.setVisibility(View.GONE);
                } else if (checkedId == R.id.btnRoleAdmins) {
                    selectedTabRole = "admin";
                    fabAddAdmin.setVisibility(View.VISIBLE);
                }
                filterUsers();
            }
        });

        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        allUserList = new ArrayList<>();
        filteredUserList = new ArrayList<>();
        adapter = new UserAdapter(currentAdminUid, this::onUserClicked);
        rvUsers.setAdapter(adapter);

        fabAddAdmin.setOnClickListener(v -> showAddAdminDialog());

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        NavigationHelper.setupBottomNavigation(this, R.id.nav_admin_users, true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        listenToUsers();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (usersListener != null) {
            usersListener.remove();
            usersListener = null;
        }
    }

    // Removed setupBottomNavigation

    private void listenToUsers() {
        progressBar.setVisibility(View.VISIBLE);
        userRepository.loadUsersByRole(selectedTabRole, "", new UserRepository.UsersCallback() {
            @Override
            public void onUsersLoaded(List<User> users) {
                progressBar.setVisibility(View.GONE);
                allUserList = users;
                filterUsers();
            }

            @Override
            public void onError(Exception e) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(AdminUsersActivity.this, "Erreur: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterUsers() {
        filteredUserList = new ArrayList<>();
        for (User u : allUserList) {
            String role = u.getRole() != null ? u.getRole() : "client";
            if ("admin".equalsIgnoreCase(selectedTabRole)) {
                if ("admin".equalsIgnoreCase(role) || "agent".equalsIgnoreCase(role)) {
                    filteredUserList.add(u);
                }
            } else {
                if (selectedTabRole.equalsIgnoreCase(role)) {
                    filteredUserList.add(u);
                }
            }
        }
        adapter.submitList(filteredUserList);
    }

    private void onUserClicked(User user) {
        if (user.getUid() != null && user.getUid().equals(currentAdminUid)) {
            Snackbar.make(findViewById(android.R.id.content), "Vous ne pouvez pas modifier votre propre compte !", Snackbar.LENGTH_SHORT).show();
            return;
        }

        String role = user.getRole() != null ? user.getRole() : "client";
        if ("admin".equalsIgnoreCase(role) || "agent".equalsIgnoreCase(role)) {
            Snackbar.make(findViewById(android.R.id.content), "Les comptes administrateurs sont protégés.", Snackbar.LENGTH_SHORT).show();
            return;
        }

        boolean isCurrentlySuspended = user.isSuspended();
        String actionTitle = isCurrentlySuspended ? "Réactiver ce compte" : "Suspendre ce compte";
        String message = "Voulez-vous vraiment " + (isCurrentlySuspended ? "réactiver" : "suspendre") + " le client " + user.getFullName() + " ?";

        new MaterialAlertDialogBuilder(this)
                .setTitle(actionTitle)
                .setMessage(message)
                .setPositiveButton("Confirmer", (dialog, which) -> toggleUserSuspension(user, !isCurrentlySuspended))
                .setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void toggleUserSuspension(User user, boolean newSuspendedState) {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("users").document(user.getUid())
                .update("suspended", newSuspendedState)
                .addOnSuccessListener(aVoid -> {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        progressBar.setVisibility(View.GONE);
                        String msg = newSuspendedState ? "Compte suspendu !" : "Compte réactivé !";
                        Toast.makeText(AdminUsersActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }, 800);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AdminUsersActivity.this, "Erreur: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showAddAdminDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_admin, null);
        dialog.setContentView(view);

        TextInputLayout tilName = view.findViewById(R.id.tilAdminName);
        TextInputLayout tilEmail = view.findViewById(R.id.tilAdminEmail);
        TextInputLayout tilPassword = view.findViewById(R.id.tilAdminPassword);

        EditText etName = view.findViewById(R.id.etAdminName);
        EditText etEmail = view.findViewById(R.id.etAdminEmail);
        EditText etPassword = view.findViewById(R.id.etAdminPassword);
        Button btnSubmit = view.findViewById(R.id.btnSubmitAdmin);

        btnSubmit.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                tilName.setError("Le nom est obligatoire");
                return;
            }
            tilName.setError(null);

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError("Format d'email invalide");
                return;
            }
            tilEmail.setError(null);

            if (password.length() < 6) {
                tilPassword.setError("Mot de passe de 6 caractères minimum");
                return;
            }
            tilPassword.setError(null);

            createNewAdminSecondaryAuth(name, email, password);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void createNewAdminSecondaryAuth(String name, String email, String password) {
        progressBar.setVisibility(View.VISIBLE);

        try {
            FirebaseApp secondaryApp;
            try {
                secondaryApp = FirebaseApp.getInstance("secondary");
            } catch (IllegalStateException e) {
                FirebaseOptions options = FirebaseApp.getInstance().getOptions();
                secondaryApp = FirebaseApp.initializeApp(this, options, "secondary");
            }

            FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);

            secondaryAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        if (authResult.getUser() != null) {
                            String newUid = authResult.getUser().getUid();
                            User newAdmin = new User(newUid, name, email, "admin", 0.0, false);

                            db.collection("users").document(newUid)
                                    .set(newAdmin)
                                    .addOnSuccessListener(aVoid -> {
                                        secondaryAuth.signOut();
                                        progressBar.setVisibility(View.GONE);
                                        Toast.makeText(AdminUsersActivity.this, "Nouvel administrateur créé !", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        authResult.getUser().delete().addOnCompleteListener(deleteTask -> {
                                            secondaryAuth.signOut();
                                            progressBar.setVisibility(View.GONE);
                                            Toast.makeText(AdminUsersActivity.this, "Erreur création compte administrateur. L'opération a été annulée.", Toast.LENGTH_LONG).show();
                                        });
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(AdminUsersActivity.this, "Erreur création compte: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Erreur: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usersListener != null) {
            usersListener.remove();
        }
    }

    // User Adapter using ListAdapter
    private static class UserAdapter extends ListAdapter<User, UserAdapter.ViewHolder> {

        private static final DiffUtil.ItemCallback<User> DIFF_CALLBACK = new DiffUtil.ItemCallback<User>() {
            @Override
            public boolean areItemsTheSame(@NonNull User oldItem, @NonNull User newItem) {
                return Objects.equals(oldItem.getUid(), newItem.getUid());
            }

            @Override
            public boolean areContentsTheSame(@NonNull User oldItem, @NonNull User newItem) {
                return Objects.equals(oldItem.getFullName(), newItem.getFullName()) &&
                       Objects.equals(oldItem.getRole(), newItem.getRole()) &&
                       oldItem.isSuspended() == newItem.isSuspended() &&
                       Double.compare(oldItem.getBalance(), newItem.getBalance()) == 0;
            }
        };

        interface OnUserClickListener {
            void onUserClick(User user);
        }

        private final String currentAdminUid;
        private final OnUserClickListener listener;

        public UserAdapter(String currentAdminUid, OnUserClickListener listener) {
            super(DIFF_CALLBACK);
            this.currentAdminUid = currentAdminUid;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User u = getItem(position);

            boolean isSelf = u.getUid() != null && u.getUid().equals(currentAdminUid);
            String nameText = u.getFullName() != null ? u.getFullName() : "Sans nom";
            if (isSelf) {
                nameText += " (Vous)";
            }
            holder.tvUserName.setText(nameText);

            holder.tvUserEmail.setText(u.getEmail() != null ? u.getEmail() : "--");
            holder.tvUserBalance.setText(String.format(Locale.getDefault(), "Solde : %.2f DT", u.getBalance()));

            String role = u.getRole() != null ? u.getRole().toUpperCase() : "CLIENT";
            holder.tvRoleBadge.setText(role);

            if (u.isSuspended()) {
                holder.tvStatusBadge.setText("Suspendu");
                holder.tvStatusBadge.setTextColor(0xFFDC2626); // Red
            } else {
                holder.tvStatusBadge.setText("Actif");
                holder.tvStatusBadge.setTextColor(0xFF16A34A); // Green
            }

            holder.itemView.setOnClickListener(v -> listener.onUserClick(u));
        }



        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvUserName, tvUserEmail, tvUserBalance, tvRoleBadge, tvStatusBadge;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvUserName = itemView.findViewById(R.id.tvUserName);
                tvUserEmail = itemView.findViewById(R.id.tvUserEmail);
                tvUserBalance = itemView.findViewById(R.id.tvUserBalance);
                tvRoleBadge = itemView.findViewById(R.id.tvRoleBadge);
                tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            }
        }
    }
}
