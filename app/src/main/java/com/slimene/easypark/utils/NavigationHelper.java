package com.slimene.easypark.utils;

import android.content.Intent;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.slimene.easypark.AdminUsersActivity;
import com.slimene.easypark.AgentDashboardActivity;
import com.slimene.easypark.AgentGridActivity;
import com.slimene.easypark.ClientHistoryActivity;
import com.slimene.easypark.ClientHomeActivity;
import com.slimene.easypark.ClientVehiclesActivity;
import com.slimene.easypark.ClientWalletActivity;
import com.slimene.easypark.R;
import com.slimene.easypark.User;
import com.slimene.easypark.agent.AgentHistoryActivity;
import com.slimene.easypark.repository.SessionRepository;

public class NavigationHelper {

    public static void setupBottomNavigation(AppCompatActivity activity, int selectedItemId, boolean isAdmin) {
        BottomNavigationView bottomNav = activity.findViewById(R.id.bottomNavigation);
        if (bottomNav == null) return;

        // Hide Admin Users item if user is an agent (non-admin)
        User profile = SessionRepository.getInstance().getCurrentProfile();
        if (isAdmin && profile != null && "agent".equalsIgnoreCase(profile.getRole())) {
            MenuItem usersItem = bottomNav.getMenu().findItem(R.id.nav_admin_users);
            if (usersItem != null) {
                usersItem.setVisible(false);
            }
        }

        // Set selected item without triggering listener
        bottomNav.setOnItemSelectedListener(null);
        bottomNav.setSelectedItemId(selectedItemId);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedItemId) return true;

            Intent intent = null;
            if (isAdmin) {
                if (id == R.id.nav_admin_dash) {
                    intent = new Intent(activity, AgentDashboardActivity.class);
                } else if (id == R.id.nav_admin_grid) {
                    intent = new Intent(activity, AgentGridActivity.class);
                } else if (id == R.id.nav_admin_users) {
                    intent = new Intent(activity, AdminUsersActivity.class);
                } else if (id == R.id.nav_admin_history) {
                    intent = new Intent(activity, AgentHistoryActivity.class);
                }
            } else {
                if (id == R.id.nav_home) {
                    intent = new Intent(activity, ClientHomeActivity.class);
                } else if (id == R.id.nav_vehicles) {
                    intent = new Intent(activity, ClientVehiclesActivity.class);
                } else if (id == R.id.nav_wallet) {
                    intent = new Intent(activity, ClientWalletActivity.class);
                } else if (id == R.id.nav_history) {
                    intent = new Intent(activity, ClientHistoryActivity.class);
                }
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
            }
            return true;
        });

        // Setup back button behavior
        int homeId = isAdmin ? R.id.nav_admin_dash : R.id.nav_home;
        activity.getOnBackPressedDispatcher().addCallback(activity, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (selectedItemId != homeId) {
                    bottomNav.setSelectedItemId(homeId);
                } else {
                    activity.finishAffinity();
                }
            }
        });

        // Remove back arrow from toolbar for root navigation tabs
        MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationIcon(null);
        }
    }
}
