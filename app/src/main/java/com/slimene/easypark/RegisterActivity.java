package com.slimene.easypark;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.textfield.TextInputLayout;
import com.slimene.easypark.utils.AuthExceptionHandler;
import com.slimene.easypark.utils.ValidationUtils;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPassword, etConfirmPassword;
    private TextInputLayout tilFullName, tilEmail, tilPassword, tilConfirmPassword;
    private Button btnRegister;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        tilFullName = findViewById(R.id.tilFullName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBar);

        btnRegister.setOnClickListener(v -> registerClient());
    }

    private void registerClient() {
        String fullName = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        boolean isNameValid = ValidationUtils.validateNotEmpty(tilFullName, etFullName, "Veuillez saisir votre nom complet");
        boolean isEmailValid = ValidationUtils.validateEmail(tilEmail, etEmail);
        boolean isPasswordValid = ValidationUtils.validatePassword(tilPassword, etPassword);

        if (!isNameValid || !isEmailValid || !isPasswordValid) {
            return;
        }

        if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError("Les mots de passe ne correspondent pas");
            return;
        } else {
            tilConfirmPassword.setError(null);
        }

        setLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        String uid = mAuth.getCurrentUser().getUid();

                        // Default user: role = "client", balance = 0.0, suspended = false
                        User user = new User(uid, fullName, email, "client", 0.0, false);

                        db.collection("users").document(uid)
                                .set(user)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(RegisterActivity.this, "Inscription réussie !", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(RegisterActivity.this, ClientHomeActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                })
                                .addOnFailureListener(e -> {
                                    // Fix orphan account
                                    mAuth.getCurrentUser().delete().addOnCompleteListener(deleteTask -> {
                                        setLoading(false);
                                        mAuth.signOut();
                                        Toast.makeText(RegisterActivity.this, "Erreur création compte, veuillez réessayer.", Toast.LENGTH_LONG).show();
                                    });
                                });
                    } else {
                        setLoading(false);
                        String errorMsg = AuthExceptionHandler.getErrorMessage(RegisterActivity.this, task.getException());
                        Toast.makeText(RegisterActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            btnRegister.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            btnRegister.setEnabled(true);
        }
    }
}
