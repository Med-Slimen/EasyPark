package com.slimene.easypark.utils;

import android.util.Patterns;
import android.widget.EditText;
import com.google.android.material.textfield.TextInputLayout;

public class ValidationUtils {

    public static boolean validateEmail(TextInputLayout layout, EditText editText) {
        String email = editText.getText().toString().trim();
        if (email.isEmpty()) {
            layout.setError("Veuillez saisir votre email");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layout.setError("Format d'email invalide");
            return false;
        }
        layout.setError(null);
        return true;
    }

    public static boolean validatePassword(TextInputLayout layout, EditText editText) {
        String password = editText.getText().toString().trim();
        if (password.isEmpty()) {
            layout.setError("Veuillez saisir votre mot de passe");
            return false;
        }
        if (password.length() < 6) {
            layout.setError("Le mot de passe doit contenir au moins 6 caractères");
            return false;
        }
        layout.setError(null);
        return true;
    }

    public static boolean validateNotEmpty(TextInputLayout layout, EditText editText, String errorMessage) {
        String text = editText.getText().toString().trim();
        if (text.isEmpty()) {
            layout.setError(errorMessage);
            return false;
        }
        layout.setError(null);
        return true;
    }
}
