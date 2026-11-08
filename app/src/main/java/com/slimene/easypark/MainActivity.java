package com.slimene.easypark;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.slimene.easypark.repository.SessionRepository;
import com.slimene.easypark.utils.AuthExceptionHandler;
import com.slimene.easypark.utils.ValidationUtils;

public class MainActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private TextInputLayout tilEmail, tilPassword;
    private Button btnLogin;
    private TextView tvRegisterLink, tvForgotPassword;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegisterLink = findViewById(R.id.tvRegisterLink);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        progressBar = findViewById(R.id.progressBar);

        btnLogin.setOnClickListener(v -> loginUser());
        tvRegisterLink.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, RegisterActivity.class)));
        tvForgotPassword.setOnClickListener(v -> resetPassword());

        if (mAuth.getCurrentUser() != null) {
            checkSessionAndNavigate();
        }
    }

    private void checkSessionAndNavigate() {
        setFormEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        SessionRepository.getInstance().attach(this, new SessionRepository.SessionCallback() {
            @Override
            public void onSessionUpdated(User user, boolean isError, Exception error) {
                if (isFinishing() || isDestroyed()) return;

                if (user != null) {
                    progressBar.setVisibility(View.GONE);
                    if (user.isSuspended()) {
                        setFormEnabled(true);
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Compte suspendu")
                                .setMessage("Votre compte a été suspendu par l'administrateur.")
                                .setPositiveButton("OK", (dialog, which) -> {
                                    dialog.dismiss();
                                    SessionRepository.forceLogout(MainActivity.this);
                                })
                                .show();
                        return;
                    }

                    Intent intent;
                    if ("admin".equalsIgnoreCase(user.getRole()) || "agent".equalsIgnoreCase(user.getRole())) {
                        intent = new Intent(MainActivity.this, AgentDashboardActivity.class);
                    } else {
                        intent = new Intent(MainActivity.this, ClientHomeActivity.class);
                    }
                    startActivity(intent);
                    finish();
                    return;
                }

                if (isError) {
                    progressBar.setVisibility(View.GONE);
                    View rootView = findViewById(android.R.id.content);
                    if (rootView != null) {
                        Snackbar.make(rootView, "Erreur de chargement du profil réseau.", Snackbar.LENGTH_INDEFINITE)
                                .setAction("Réessayer", v -> checkSessionAndNavigate())
                                .setAction("Se déconnecter", v -> {
                                    SessionRepository.forceLogout(MainActivity.this);
                                    setFormEnabled(true);
                                })
                                .show();
                    } else {
                        setFormEnabled(true);
                    }
                    return;
                }

                // If user is null and no error -> signed out / profile missing
                progressBar.setVisibility(View.GONE);
                setFormEnabled(true);
            }
        });
    }

    private void resetPassword() {
        String email = etEmail.getText().toString().trim();
        if (!ValidationUtils.validateEmail(tilEmail, etEmail)) {
            Toast.makeText(this, "Saisissez un email valide pour réinitialiser le mot de passe", Toast.LENGTH_SHORT).show();
            return;
        }

        setFormEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    setFormEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        Toast.makeText(MainActivity.this, "Email de réinitialisation envoyé.", Toast.LENGTH_LONG).show();
                    } else {
                        String errorMsg = AuthExceptionHandler.getErrorMessage(MainActivity.this, task.getException());
                        Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        boolean isEmailValid = ValidationUtils.validateEmail(tilEmail, etEmail);
        boolean isPasswordValid = ValidationUtils.validatePassword(tilPassword, etPassword);

        if (!isEmailValid || !isPasswordValid) {
            return;
        }

        setFormEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        checkSessionAndNavigate();
                    } else {
                        setFormEnabled(true);
                        progressBar.setVisibility(View.GONE);
                        String errorMsg = AuthExceptionHandler.getErrorMessage(MainActivity.this, task.getException());
                        Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setFormEnabled(boolean enabled) {
        btnLogin.setEnabled(enabled);
        etEmail.setEnabled(enabled);
        etPassword.setEnabled(enabled);
        tvRegisterLink.setEnabled(enabled);
        tvForgotPassword.setEnabled(enabled);
    }
}
