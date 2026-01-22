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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.inout.app.databinding.FragmentEmployeeCheckinBinding;
import com.inout.app.models.AttendanceRecord;
import com.inout.app.models.CompanyConfig;
import com.inout.app.models.User;
import com.inout.app.utils.BiometricHelper;
import com.inout.app.utils.LocationHelper;
import com.inout.app.utils.TimeUtils;

/**
 * Fragment where employees perform Check-In and Check-Out.
 * Strictly enforces: Fingerprint Success + GPS within 100m.
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

        loadUserDataAndStatus();

        binding.btnCheckIn.setOnClickListener(v -> initiateAction(true));
        binding.btnCheckOut.setOnClickListener(v -> initiateAction(false));
    }

    private void loadUserDataAndStatus() {
        String uid = mAuth.getCurrentUser().getUid();
        
        // 1. Get User Profile to find assigned location
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            currentUser = doc.toObject(User.class);
            if (currentUser != null && currentUser.getAssignedLocationId() != null) {
                fetchAssignedLocation(currentUser.getAssignedLocationId());
            }
            // 2. Check if already checked in today
            loadTodayAttendance();
        });
    }

    private void fetchAssignedLocation(String locId) {
        db.collection("locations").document(locId).get().addOnSuccessListener(doc -> {
            assignedLocation = doc.toObject(CompanyConfig.class);
        });
    }

    private void loadTodayAttendance() {
        if (currentUser == null) return;
        
        String dateId = TimeUtils.getCurrentDateId();
        // Record ID is employeeId_dateId
        String recordId = currentUser.getEmployeeId() + "_" + dateId;

        db.collection("attendance").document(recordId).addSnapshotListener((snapshot, e) -> {
            if (snapshot != null && snapshot.exists()) {
                todayRecord = snapshot.toObject(AttendanceRecord.class);
                updateUIBasedOnStatus();
            } else {
                todayRecord = null;
                updateUIBasedOnStatus();
            }
        });
    }

    private void updateUIBasedOnStatus() {
        if (todayRecord == null) {
            // Not checked in yet
            binding.btnCheckIn.setEnabled(true);
            binding.btnCheckOut.setEnabled(false);
            binding.tvStatus.setText("Status: Not Checked In");
        } else if (todayRecord.getCheckOutTime() == null) {
            // Checked in but not out
            binding.btnCheckIn.setEnabled(false);
            binding.btnCheckOut.setEnabled(true);
            binding.tvStatus.setText("Status: Checked In at " + todayRecord.getCheckInTime());
        } else {
            // Both done
            binding.btnCheckIn.setEnabled(false);
            binding.btnCheckOut.setEnabled(false);
            binding.tvStatus.setText("Status: Completed for Today (" + todayRecord.getTotalHours() + ")");
        }
    }

    /**
     * Starts the verification sequence.
     * @param isCheckIn True for Check-In, False for Check-Out
     */
    private void initiateAction(boolean isCheckIn) {
        if (assignedLocation == null) {
            Toast.makeText(getContext(), "Error: No office location assigned to you.", Toast.LENGTH_LONG).show();
            return;
        }

        // STEP 1: Biometric Verification
        BiometricHelper.authenticate(requireActivity(), new BiometricHelper.BiometricCallback() {
            @Override
            public void onAuthenticationSuccess() {
                // STEP 2: Location Verification
                verifyLocationAndProceed(isCheckIn);
            }

            @Override
            public void onAuthenticationError(String errorMsg) {
                Toast.makeText(getContext(), "Biometric Error: " + errorMsg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationFailed() {
                Toast.makeText(getContext(), "Fingerprint not recognized.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void verifyLocationAndProceed(boolean isCheckIn) {
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
                        if (isCheckIn) performCheckIn(location);
                        else performCheckOut(location);
                    } else {
                        Toast.makeText(getContext(), "Access Denied: You are not within the office radius.", Toast.LENGTH_LONG).show();
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

    private void performCheckIn(Location loc) {
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

        db.collection("attendance").document(recordId).set(record)
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Check-In Successful!", Toast.LENGTH_SHORT).show());
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
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Check-Out Successful!", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}