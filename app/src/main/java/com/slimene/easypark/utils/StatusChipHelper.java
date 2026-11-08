package com.slimene.easypark.utils;

import android.content.Context;
import androidx.core.content.ContextCompat;
import com.slimene.easypark.AppConfig;
import com.slimene.easypark.ParkingSpot;
import com.slimene.easypark.R;

public class StatusChipHelper {

    public static class StatusUi {
        public final String label;
        public final int textColor;
        public final int containerColor;
        public final String effectiveStatus;

        public StatusUi(String label, int textColor, int containerColor, String effectiveStatus) {
            this.label = label;
            this.textColor = textColor;
            this.containerColor = containerColor;
            this.effectiveStatus = effectiveStatus;
        }
    }

    public static StatusUi getStatusUi(Context context, ParkingSpot spot) {
        String status = spot.getStatus() != null ? spot.getStatus() : "";

        if ("reservee".equalsIgnoreCase(status) || "réservée".equalsIgnoreCase(status)) {
            long maxDurationMillis = AppConfig.MAX_RESERVATION_MINUTES * 60 * 1000L;
            if (spot.getReservedAt() > 0 && System.currentTimeMillis() - spot.getReservedAt() > maxDurationMillis) {
                status = "libre";
            }
        }

        String label;
        int textColorRes;
        int containerColorRes;
        String effectiveStatus = status;

        switch (status.toLowerCase()) {
            case "libre":
                label = context.getString(R.string.status_libre);
                textColorRes = R.color.status_free_text;
                containerColorRes = R.color.status_free_container;
                break;
            case "reservee":
            case "réservée":
                label = context.getString(R.string.status_reservee);
                textColorRes = R.color.status_reserved_text;
                containerColorRes = R.color.status_reserved_container;
                break;
            case "occupee":
            case "occupée":
                label = context.getString(R.string.status_occupee);
                textColorRes = R.color.status_occupied_text;
                containerColorRes = R.color.status_occupied_container;
                break;
            default:
                label = context.getString(R.string.status_unknown);
                textColorRes = R.color.md_theme_light_onSurfaceVariant;
                containerColorRes = R.color.md_theme_light_surfaceVariant;
                break;
        }

        return new StatusUi(
            label,
            ContextCompat.getColor(context, textColorRes),
            ContextCompat.getColor(context, containerColorRes),
            effectiveStatus
        );
    }
}
