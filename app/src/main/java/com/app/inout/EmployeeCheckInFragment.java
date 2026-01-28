package com.inout.app;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.inout.app.databinding.FragmentEmployeeCheckinBinding;
import com.inout.app.models.AttendanceRecord;
import com.inout.app.models.CompanyConfig;
import com.inout.app.models.User;
import com.inout.app.utils.BiometricHelper;
import com.inout.app.utils.LocationHelper;
import com.inout.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fragment where employees perform Check-In, Transit, and Check-Out.
 * UPDATED: Includes 3-Button Logic and Movement Logging.
 */
public class EmployeeCheckInFragment extends Fragment {

    private static final String TAG = "CheckInFrag";
    private FragmentEmployeeCheckinBinding binding;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private LocationHelper locationHelper;
    
    private User currentUser;
    private CompanyConfig assignedLocation;
    private AttendanceRecord todayRecord;

    // Action Constants
    private static final int ACTION_IN = 1;
    private static final int ACTION_TRANSIT = 2;
    private static final int ACTION_OUT = 3;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentEmployeeCheckinBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        locationHelper = new LocationHelper(requireContext());

        // Initial UI State: All disabled until data loads
        updateButtonState(false, false, false);

        loadUserDataAndStatus();

        binding.btnCheckIn.setOnClickListener(v -> initiateAction(ACTION_IN));
        binding.btnTransit.setOnClickListener(v -> initiateAction(ACTION_TRANSIT));
        binding.btnCheckOut.setOnClickListener(v -> initiateAction(ACTION_OUT));
    }

    private void updateButtonState(boolean in, boolean transit, boolean out) {
        binding.btnCheckIn.setEnabled(in);
        binding.btnTransit.setEnabled(transit);
        binding.btnCheckOut.setEnabled(out);
        
        // Visual opacity for disabled state
        binding.btnCheckIn.setAlpha(in ? 1.0f : 0.5f);
        binding.btnTransit.setAlpha(transit ? 1.0f : 0.5f);
        binding.btnCheckOut.setAlpha(out ? 1.0f : 0.5f);
    }

    /**
     * Fetches user profile to display real Name/ID and retrieve the office assignment ID.
     */
    private void loadUserDataAndStatus() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        
        db.collection("users").document(uid).addSnapshotListener((doc, error) -> {
            if (error != null) return;
            
            if (doc != null && doc.exists()) {
                currentUser = doc.toObject(User.class);
                
                if (currentUser != null) {
                    binding.tvEmployeeName.setText(currentUser.getName() != null ? currentUser.getName() : "Unknown User");
                    binding.tvEmployeeId.setText(currentUser.getEmployeeId() != null ? currentUser.getEmployeeId() : "Pending ID");

                    String locId = currentUser.getAssignedLocationId();
                    
                    if (locId != null && !locId.isEmpty()) {
                        fetchAssignedLocationDetails(locId);
                    } else {
                        binding.tvStatus.setText("Status: No workplace assigned by Admin.");
                        updateButtonState(false, false, false);
                    }
                    
                    loadTodayAttendance();
                }
            }
        });
    }

    private void fetchAssignedLocationDetails(String locId) {
        db.collection("locations").document(locId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                assignedLocation = doc.toObject(CompanyConfig.class);
                assignedLocation.setId(doc.getId()); // Ensure ID is set
                
                Log.d(TAG, "New Location Assigned: " + assignedLocation.getName());
                updateUIBasedOnStatus();
            } else {
                binding.tvStatus.setText("Status: Workplace record not found.");
            }
        }).addOnFailureListener(e -> binding.tvStatus.setText("Status: Error fetching location."));
    }

    private void loadTodayAttendance() {
        if (currentUser == null || currentUser.getEmployeeId() == null) return;
        
        String dateId = TimeUtils.getCurrentDateId();
        String recordId = currentUser.getEmployeeId() + "_" + dateId;

        db.collection("attendance").document(recordId).addSnapshotListener((snapshot, e) -> {
            if (snapshot != null && snapshot.exists()) {
                todayRecord = snapshot.toObject(AttendanceRecord.class);
            } else {
                todayRecord = null;
            }
            updateUIBasedOnStatus();
        });
    }

    /**
     * CRITICAL LOGIC: Determines which buttons are enabled based on state.
     */
    private void updateUIBasedOnStatus() {
        if (currentUser == null || assignedLocation == null) return;

        String locName = assignedLocation.getName();

        if (todayRecord == null) {
            // Case 1: Start of Day
            updateButtonState(true, false, false);
            binding.tvStatus.setText("Status: Ready to Check-In at " + locName);
            
        } else if (todayRecord.getCheckOutTime() == null || todayRecord.getCheckOutTime().isEmpty()) {
            // Case 2: Currently Checked In
            
            // TRANSIT LOGIC: Check if the Admin has changed the location since the last verification
            String lastLocId = todayRecord.getLastVerifiedLocationId();
            String currentLocId = assignedLocation.getId();
            
            boolean allowTransit = false;
            
            // If the assigned location is different from where they last checked in/transited -> Enable Transit
            if (lastLocId != null && !lastLocId.equals(currentLocId)) {
                allowTransit = true;
                binding.tvStatus.setText("Transit Required: Move to " + locName);
            } else {
                binding.tvStatus.setText("Status: Working at " + locName);
            }
            
            updateButtonState(false, allowTransit, true);
            
        } else {
            // Case 3: Shift Completed
            updateButtonState(false, false, false);
            binding.tvStatus.setText("Status: Shift Completed at " + todayRecord.getLocationName() + 
                    " (" + todayRecord.getTotalHours() + ")");
        }
    }

    private void initiateAction(int actionType) {
        if (assignedLocation == null) {
            Toast.makeText(getContext(), "Error: Office location not assigned.", Toast.LENGTH_LONG).show();
            return;
        }

        BiometricHelper.authenticate(requireActivity(), new BiometricHelper.BiometricCallback() {
            @Override
            public void onAuthenticationSuccess() {
                verifyLocationAndProceed(actionType);
            }

            @Override
            public void onAuthenticationError(String errorMsg) {
                Toast.makeText(getContext(), "Auth Error: " + errorMsg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationFailed() {
                Toast.makeText(getContext(), "Fingerprint not recognized.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void verifyLocationAndProceed(int actionType) {
        binding.progressBar.setVisibility(View.VISIBLE);
        
        locationHelper.getCurrentLocation(new LocationHelper.LocationResultCallback() {
            @Override
            public void onLocationResult(Location location) {
                binding.progressBar.setVisibility(View.GONE);
                
                if (location != null) {
                    boolean inRange = LocationHelper.isWithinRadius(
                            location.getLatitude(), location.getLongitude(),
                            assignedLocation.getLatitude(), assignedLocation.getLongitude(),
                            assignedLocation.getRadius());

                    if (inRange) {
                        float dist = LocationHelper.calculateDistance(
                                location.getLatitude(), location.getLongitude(),
                                assignedLocation.getLatitude(), assignedLocation.getLongitude());
                        
                        if (actionType == ACTION_IN) {
                            performCheckIn(location, dist);
                        } else if (actionType == ACTION_TRANSIT) {
                            performTransit(location, dist);
                        } else if (actionType == ACTION_OUT) {
                            performCheckOut(location);
                        }
                    } else {
                        String msg = "Denied: You are not at " + assignedLocation.getName() + " (Out of 100m range).";
                        Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onError(String errorMsg) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), "GPS Error: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performCheckIn(Location loc, float distance) {
        String dateId = TimeUtils.getCurrentDateId();
        String recordId = currentUser.getEmployeeId() + "_" + dateId;

        AttendanceRecord record = new AttendanceRecord(
                currentUser.getEmployeeId(), 
                currentUser.getName(), 
                dateId, 
                TimeUtils.getCurrentTimestamp());

        record.setRecordId(recordId);
        record.setCheckInTime(TimeUtils.getCurrentTime());
        record.setCheckInLat(loc.getLatitude());
        record.setCheckInLng(loc.getLongitude());
        record.setFingerprintVerified(true);
        record.setLocationVerified(true);
        record.setDistanceMeters(distance);
        record.setLocationName(assignedLocation.getName());
        
        // Initialize Movement Log with the first location
        List<String> moves = new ArrayList<>();
        moves.add(assignedLocation.getName());
        record.setMovementLog(moves);
        record.setLastVerifiedLocationId(assignedLocation.getId());

        db.collection("attendance").document(recordId).set(record)
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Check-In Success!", Toast.LENGTH_SHORT).show());
    }

    /**
     * NEW: TRANSIT LOGIC
     * Adds the new location to the movement log and updates the current verified location.
     */
    private void performTransit(Location loc, float distance) {
        if (todayRecord == null) return;

        // Add the new distance to the existing total
        float newTotalDist = todayRecord.getDistanceMeters() + distance;
        String newLocName = assignedLocation.getName();

        db.collection("attendance").document(todayRecord.getRecordId())
                .update(
                    "distanceMeters", newTotalDist,
                    "locationName", newLocName, // Update current location status
                    "lastVerifiedLocationId", assignedLocation.getId(),
                    "movementLog", FieldValue.arrayUnion(newLocName) // Append to history
                )
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Transit Verified: " + newLocName, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Transit Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void performCheckOut(Location loc) {
        if (todayRecord == null) return;

        String checkOutTime = TimeUtils.getCurrentTime();
        String totalHrs = TimeUtils.calculateDuration(todayRecord.getCheckInTime(), checkOutTime);

        db.collection("attendance").document(todayRecord.getRecordId())
                .update(
                        "checkOutTime", checkOutTime,
                        "checkOutLat", loc.getLatitude(),
                        "checkOutLng", loc.getLongitude(),
                        "totalHours", totalHrs
                )
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Check-Out Success!", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}