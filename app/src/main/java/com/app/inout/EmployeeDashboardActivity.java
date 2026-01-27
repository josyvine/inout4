package com.inout.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.inout.app.databinding.ActivityEmployeeDashboardBinding;
import com.inout.app.models.User;
import com.inout.app.utils.EncryptionHelper;

/**
 * Main dashboard for Employees.
 * Handles navigation between Check-In/Out and Attendance History.
 * Monitors Admin Approval status and Profile completeness.
 */
public class EmployeeDashboardActivity extends AppCompatActivity {

    private ActivityEmployeeDashboardBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEmployeeDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        setSupportActionBar(binding.toolbar);

        // Setup Navigation Component
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_employee);
        
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            
            // Destinations that are considered "Top Level"
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_employee_checkin, 
                    R.id.nav_employee_history)
                    .build();

            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
            NavigationUI.setupWithNavController(binding.navView, navController);
        }

        checkUserProfileAndStatus();
    }

    /**
     * Verifies if the user is approved and if their profile (photo/phone) is set up.
     */
    private void checkUserProfileAndStatus() {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) return;

        db.collection("users").document(firebaseUser.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) return;

                    if (snapshot != null && snapshot.exists()) {
                        User user = snapshot.toObject(User.class);
                        if (user != null) {
                            // 1. Check if basic profile data is missing
                            if (user.getPhone() == null || user.getPhone().isEmpty() || 
                                user.getPhotoUrl() == null || user.getPhotoUrl().isEmpty()) {
                                
                                Toast.makeText(this, "Please complete your profile first.", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(this, EmployeeProfileActivity.class));
                                // We don't finish() here so they can come back after saving
                                return;
                            }

                            // 2. Check for Admin Approval
                            if (!user.isApproved()) {
                                showWaitingOverlay(true);
                            } else {
                                showWaitingOverlay(false);
                            }
                        }
                    }
                });
    }

    /**
     * Shows or hides an overlay that blocks interaction until the admin approves the account.
     */
    private void showWaitingOverlay(boolean show) {
        if (show) {
            binding.layoutWaitingApproval.setVisibility(View.VISIBLE);
            binding.navView.setVisibility(View.GONE);
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("Pending Approval");
        } else {
            binding.layoutWaitingApproval.setVisibility(View.GONE);
            binding.navView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.employee_top_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit_profile) {
            startActivity(new Intent(this, EmployeeProfileActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            logout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * UPDATED: Full Logout Logic.
     * 1. Signs out of Firebase.
     * 2. Clears the "Employee" role from local storage.
     * 3. Returns to the absolute landing page (Splash/Role Selection).
     */
    private void logout() {
        // Sign out from Firebase
        mAuth.signOut();

        // Clear the stored Role (Employee) so they must declare it again
        EncryptionHelper.getInstance(this).clearUserRole();

        // Return to SplashActivity and clear the activity history stack
        Intent intent = new Intent(this, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}