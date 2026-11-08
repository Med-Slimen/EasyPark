package com.slimene.easypark.utils;

import android.content.Context;
import com.google.firebase.auth.FirebaseAuthException;
import com.slimene.easypark.R;

public class AuthExceptionHandler {

    public static String getErrorMessage(Context context, Exception exception) {
        if (exception instanceof FirebaseAuthException) {
            String errorCode = ((FirebaseAuthException) exception).getErrorCode();
            switch (errorCode) {
                case "ERROR_INVALID_EMAIL":
                    return context.getString(R.string.auth_invalid_email);
                case "ERROR_WRONG_PASSWORD":
                    return context.getString(R.string.auth_wrong_password);
                case "ERROR_USER_NOT_FOUND":
                    return context.getString(R.string.auth_user_not_found);
                case "ERROR_USER_DISABLED":
                    return context.getString(R.string.auth_user_disabled);
                case "ERROR_TOO_MANY_REQUESTS":
                    return context.getString(R.string.auth_too_many_requests);
                case "ERROR_EMAIL_ALREADY_IN_USE":
                    return context.getString(R.string.auth_email_in_use);
                case "ERROR_NETWORK_REQUEST_FAILED":
                    return context.getString(R.string.auth_network_error);
                case "ERROR_WEAK_PASSWORD":
                    return context.getString(R.string.auth_weak_password);
                case "ERROR_RECENT_LOGIN_REQUIRED":
                    return context.getString(R.string.auth_recent_login_required);
                default:
                    return context.getString(R.string.auth_unknown_error);
            }
        }
        return context.getString(R.string.auth_unknown_error);
    }
}
