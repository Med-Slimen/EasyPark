package com.slimene.easypark;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class LayoutSmokeTest {

    private Context context;
    private LayoutInflater inflater;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        inflater = LayoutInflater.from(context);
    }

    @Test
    public void testActivityMainLayoutIds() {
        View view = inflater.inflate(R.layout.activity_main, null);
        assertNotNull(view.findViewById(R.id.etEmail));
        assertNotNull(view.findViewById(R.id.etPassword));
        assertNotNull(view.findViewById(R.id.tilEmail));
        assertNotNull(view.findViewById(R.id.tilPassword));
        assertNotNull(view.findViewById(R.id.btnLogin));
        assertNotNull(view.findViewById(R.id.tvRegisterLink));
        assertNotNull(view.findViewById(R.id.tvForgotPassword));
        assertNotNull(view.findViewById(R.id.progressBar));
    }

    @Test
    public void testActivityRegisterLayoutIds() {
        View view = inflater.inflate(R.layout.activity_register, null);
        assertNotNull(view.findViewById(R.id.etFullName));
        assertNotNull(view.findViewById(R.id.etEmail));
        assertNotNull(view.findViewById(R.id.etPassword));
        assertNotNull(view.findViewById(R.id.etConfirmPassword));
        assertNotNull(view.findViewById(R.id.btnRegister));
        assertNotNull(view.findViewById(R.id.progressBar));
    }

    @Test
    public void testActivityClientHomeLayoutIds() {
        View view = inflater.inflate(R.layout.activity_client_home, null);
        assertNotNull(view.findViewById(R.id.tvWelcome));
        assertNotNull(view.findViewById(R.id.tvBalance));
        assertNotNull(view.findViewById(R.id.tvCarSpots));
        assertNotNull(view.findViewById(R.id.tvCarRate));
        assertNotNull(view.findViewById(R.id.tvMotoSpots));
        assertNotNull(view.findViewById(R.id.tvMotoRate));
        assertNotNull(view.findViewById(R.id.btnQuickRecharge));
        assertNotNull(view.findViewById(R.id.btnSelectSpot));
        assertNotNull(view.findViewById(R.id.btnActiveReservation));
        assertNotNull(view.findViewById(R.id.btnProfile));
        assertNotNull(view.findViewById(R.id.bottomNavigation));
    }

    @Test
    public void testActivityAgentDashboardLayoutIds() {
        View view = inflater.inflate(R.layout.activity_agent_dashboard, null);
        assertNotNull(view.findViewById(R.id.tvTotalRevenue));
        assertNotNull(view.findViewById(R.id.tvTotalParkedNow));
        assertNotNull(view.findViewById(R.id.tvGreeting));
        assertNotNull(view.findViewById(R.id.tvDate));
        assertNotNull(view.findViewById(R.id.tvCarParkedNow));
        assertNotNull(view.findViewById(R.id.tvCarFreeCount));
        assertNotNull(view.findViewById(R.id.tvMotoParkedNow));
        assertNotNull(view.findViewById(R.id.tvMotoFreeCount));
        assertNotNull(view.findViewById(R.id.progressCar));
        assertNotNull(view.findViewById(R.id.progressMoto));
        assertNotNull(view.findViewById(R.id.btnProfile));
        assertNotNull(view.findViewById(R.id.bottomNavigation));
        assertNotNull(view.findViewById(R.id.cardAddVehicle));
        assertNotNull(view.findViewById(R.id.cardPresentVehicles));
        assertNotNull(view.findViewById(R.id.cardQuickExit));
        assertNotNull(view.findViewById(R.id.cardHistory));
    }

    @Test
    public void testActivityAgentGridLayoutIds() {
        View view = inflater.inflate(R.layout.activity_agent_grid, null);
        assertNotNull(view.findViewById(R.id.rvAgentGrid));
        assertNotNull(view.findViewById(R.id.toggleGroupType));
        assertNotNull(view.findViewById(R.id.fabAddSpot));
        assertNotNull(view.findViewById(R.id.bottomNavigation));
    }

    @Test
    public void testActivityAdminUsersLayoutIds() {
        View view = inflater.inflate(R.layout.activity_admin_users, null);
        assertNotNull(view.findViewById(R.id.rvUsers));
        assertNotNull(view.findViewById(R.id.progressBar));
        assertNotNull(view.findViewById(R.id.fabAddAdmin));
        assertNotNull(view.findViewById(R.id.toggleGroupRole));
        assertNotNull(view.findViewById(R.id.bottomNavigation));
    }

    @Test
    public void testActivityClientHistoryLayoutIds() {
        View view = inflater.inflate(R.layout.activity_client_history, null);
        assertNotNull(view.findViewById(R.id.rvHistory));
        assertNotNull(view.findViewById(R.id.tvEmptyHistory));
        assertNotNull(view.findViewById(R.id.bottomNavigation));
    }

    @Test
    public void testActivityClientVehiclesLayoutIds() {
        View view = inflater.inflate(R.layout.activity_client_vehicles, null);
        assertNotNull(view.findViewById(R.id.rvVehicles));
        assertNotNull(view.findViewById(R.id.tvEmpty));
        assertNotNull(view.findViewById(R.id.progressBar));
        assertNotNull(view.findViewById(R.id.fabAddVehicle));
        assertNotNull(view.findViewById(R.id.bottomNavigation));
    }

    @Test
    public void testActivityClientWalletLayoutIds() {
        View view = inflater.inflate(R.layout.activity_client_wallet, null);
        assertNotNull(view.findViewById(R.id.toolbar));
        assertNotNull(view.findViewById(R.id.tvCurrentBalance));
        assertNotNull(view.findViewById(R.id.tvEmptyTransactions));
        assertNotNull(view.findViewById(R.id.etCardholderName));
        assertNotNull(view.findViewById(R.id.etCardNumber));
        assertNotNull(view.findViewById(R.id.etExpiryDate));
        assertNotNull(view.findViewById(R.id.etCvv));
        assertNotNull(view.findViewById(R.id.etAmount));
        assertNotNull(view.findViewById(R.id.btnRecharge));
        assertNotNull(view.findViewById(R.id.progressBar));
        assertNotNull(view.findViewById(R.id.rvTransactions));
        assertNotNull(view.findViewById(R.id.bottomNavigation));
    }

    @Test
    public void testActivityClientParkingMapLayoutIds() {
        View view = inflater.inflate(R.layout.activity_client_parking_map, null);
        assertNotNull(view.findViewById(R.id.toolbar));
        assertNotNull(view.findViewById(R.id.tvHourlyRate));
        assertNotNull(view.findViewById(R.id.rvSpots));
        assertNotNull(view.findViewById(R.id.progressBar));
        assertNotNull(view.findViewById(R.id.toggleGroupType));
    }

    @Test
    public void testActivityClientReservationLayoutIds() {
        View view = inflater.inflate(R.layout.activity_client_reservation, null);
        assertNotNull(view.findViewById(R.id.toolbar));
        assertNotNull(view.findViewById(R.id.tvNoReservation));
        assertNotNull(view.findViewById(R.id.cardActive));
        assertNotNull(view.findViewById(R.id.tvSpotTitle));
        assertNotNull(view.findViewById(R.id.tvStatusMessage));
        assertNotNull(view.findViewById(R.id.tvTimerLabel));
        assertNotNull(view.findViewById(R.id.tvTimerValue));
        assertNotNull(view.findViewById(R.id.tvEstimatedCost));
        assertNotNull(view.findViewById(R.id.tvMinDurationWarning));
        assertNotNull(view.findViewById(R.id.btnCancelReservation));
        assertNotNull(view.findViewById(R.id.btnPayAndExit));
        assertNotNull(view.findViewById(R.id.progressBar));
    }

    @Test
    public void testActivityProfileLayoutIds() {
        View view = inflater.inflate(R.layout.activity_profile, null);
        assertNotNull(view.findViewById(R.id.toolbar));
        assertNotNull(view.findViewById(R.id.tvEmail));
        assertNotNull(view.findViewById(R.id.tvRoleBadge));
        assertNotNull(view.findViewById(R.id.tvAvatarInitials));
        assertNotNull(view.findViewById(R.id.etFullName));
        assertNotNull(view.findViewById(R.id.etNewPassword));
        assertNotNull(view.findViewById(R.id.btnSaveProfile));
        assertNotNull(view.findViewById(R.id.btnUpdatePassword));
        assertNotNull(view.findViewById(R.id.btnSendResetEmail));
        assertNotNull(view.findViewById(R.id.btnLogout));
        assertNotNull(view.findViewById(R.id.progressBar));
    }

    @Test
    public void testActivityAddVehicleEntryLayoutIds() {
        View view = inflater.inflate(R.layout.activity_add_vehicle_entry, null);
        assertNotNull(view.findViewById(R.id.toolbar));
        assertNotNull(view.findViewById(R.id.etPlate));
        assertNotNull(view.findViewById(R.id.toggleGroupType));
        assertNotNull(view.findViewById(R.id.actvSpot));
        assertNotNull(view.findViewById(R.id.btnConfirm));
        assertNotNull(view.findViewById(R.id.progressBar));
    }

    @Test
    public void testActivityPresentVehiclesLayoutIds() {
        View view = inflater.inflate(R.layout.activity_present_vehicles, null);
        assertNotNull(view.findViewById(R.id.toolbar));
        assertNotNull(view.findViewById(R.id.etSearch));
        assertNotNull(view.findViewById(R.id.chipGroupFilter));
        assertNotNull(view.findViewById(R.id.rvPresent));
        assertNotNull(view.findViewById(R.id.emptyState));
        assertNotNull(view.findViewById(R.id.progressBar));
    }

    @Test
    public void testActivityExitLayoutIds() {
        View view = inflater.inflate(R.layout.activity_exit, null);
        assertNotNull(view.findViewById(R.id.toolbar));
        assertNotNull(view.findViewById(R.id.tvPlate));
        assertNotNull(view.findViewById(R.id.tvSpotDetails));
        assertNotNull(view.findViewById(R.id.tvEntryTime));
        assertNotNull(view.findViewById(R.id.tvExitTime));
        assertNotNull(view.findViewById(R.id.tvDuration));
        assertNotNull(view.findViewById(R.id.tvBilledHours));
        assertNotNull(view.findViewById(R.id.tvRate));
        assertNotNull(view.findViewById(R.id.tvTotalAmount));
        assertNotNull(view.findViewById(R.id.btnValidateExit));
        assertNotNull(view.findViewById(R.id.btnValidateExitWallet));
        assertNotNull(view.findViewById(R.id.btnCancelNoCharge));
        assertNotNull(view.findViewById(R.id.progressBar));
    }

    @Test
    public void testActivityAgentHistoryLayoutIds() {
        View view = inflater.inflate(R.layout.activity_agent_history, null);
        assertNotNull(view.findViewById(R.id.toolbar));
        assertNotNull(view.findViewById(R.id.tvTotalRevenue));
        assertNotNull(view.findViewById(R.id.tabLayoutFilter));
        assertNotNull(view.findViewById(R.id.etSearch));
        assertNotNull(view.findViewById(R.id.rvHistory));
        assertNotNull(view.findViewById(R.id.emptyState));
        assertNotNull(view.findViewById(R.id.progressBar));
    }

    @Test
    public void testActivityTicketReceiptLayoutIds() {
        View view = inflater.inflate(R.layout.activity_ticket_receipt, null);
        assertNotNull(view.findViewById(R.id.toolbar));
        assertNotNull(view.findViewById(R.id.tvTotalAmount));
        assertNotNull(view.findViewById(R.id.tvTicketId));
        assertNotNull(view.findViewById(R.id.tvSpotDetails));
        assertNotNull(view.findViewById(R.id.tvPlate));
        assertNotNull(view.findViewById(R.id.tvEntryTime));
        assertNotNull(view.findViewById(R.id.tvExitTime));
        assertNotNull(view.findViewById(R.id.tvBilledHours));
        assertNotNull(view.findViewById(R.id.btnShare));
    }
}
