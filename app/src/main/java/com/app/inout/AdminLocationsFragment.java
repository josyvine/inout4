package com.inout.app;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.inout.app.databinding.FragmentAdminLocationsBinding;
import com.inout.app.models.CompanyConfig;
import com.inout.app.utils.LocationHelper;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class AdminLocationsFragment extends Fragment {

    private static final String TAG = "AdminLocationsFrag";
    private FragmentAdminLocationsBinding binding;
    private FirebaseFirestore db;
    private LocationHelper locationHelper;
    
    private double capturedLat = 0;
    private double capturedLng = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminLocationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        locationHelper = new LocationHelper(requireContext());

        setupClickListeners();
        listenForLocations();
    }

    private void setupClickListeners() {
        // NEW: Search Button Logic
        binding.btnSearchLocation.setOnClickListener(v -> {
            String address = binding.etSearchAddress.getText().toString().trim();
            if (!TextUtils.isEmpty(address)) {
                searchLocationByAddress(address);
            } else {
                Toast.makeText(getContext(), "Please enter an address to search", Toast.LENGTH_SHORT).show();
            }
        });

        // Original: Capture current GPS logic
        binding.btnCaptureGps.setOnClickListener(v -> captureCurrentLocation());

        // Save logic
        binding.btnSaveLocation.setOnClickListener(v -> saveLocationToFirestore());
    }

    /**
     * Uses Android Geocoder to find coordinates for a text address.
     */
    private void searchLocationByAddress(String addressString) {
        binding.progressBar.setVisibility(View.VISIBLE);
        
        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(addressString, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address result = addresses.get(0);
                
                capturedLat = result.getLatitude();
                capturedLng = result.getLongitude();

                // Auto-fill the UI
                String foundName = result.getFeatureName(); // e.g. "Canara Bank"
                binding.etLocationName.setText(foundName);
                
                binding.tvCapturedCoords.setText(String.format("Found: %s\nLat: %.6f | Lng: %.6f", 
                        result.getAddressLine(0), capturedLat, capturedLng));
                binding.tvCapturedCoords.setVisibility(View.VISIBLE);
                
                Toast.makeText(getContext(), "Location Found", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Address not found. Try adding city name.", Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoder error", e);
            Toast.makeText(getContext(), "Search error. Check internet connection.", Toast.LENGTH_SHORT).show();
        } finally {
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    private void captureCurrentLocation() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnCaptureGps.setEnabled(false);

        locationHelper.getCurrentLocation(new LocationHelper.LocationResultCallback() {
            @Override
            public void onLocationResult(Location location) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnCaptureGps.setEnabled(true);

                if (location != null) {
                    capturedLat = location.getLatitude();
                    capturedLng = location.getLongitude();
                    
                    binding.tvCapturedCoords.setText(String.format("Current GPS:\nLat: %.6f | Lng: %.6f", capturedLat, capturedLng));
                    binding.tvCapturedCoords.setVisibility(View.VISIBLE);
                    Toast.makeText(getContext(), "Current Location Captured", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String errorMsg) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnCaptureGps.setEnabled(true);
                Toast.makeText(getContext(), "GPS Error: " + errorMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void saveLocationToFirestore() {
        String locName = binding.etLocationName.getText().toString().trim();

        if (TextUtils.isEmpty(locName)) {
            binding.etLocationName.setError("Location Name is required");
            return;
        }

        if (capturedLat == 0 || capturedLng == 0) {
            Toast.makeText(getContext(), "Please find a location first", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        CompanyConfig config = new CompanyConfig(locName, capturedLat, capturedLng);

        db.collection("locations")
                .add(config)
                .addOnSuccessListener(documentReference -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Location Saved Successfully", Toast.LENGTH_SHORT).show();
                    clearInputs();
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void clearInputs() {
        binding.etLocationName.setText("");
        binding.etSearchAddress.setText("");
        binding.tvCapturedCoords.setText("");
        binding.tvCapturedCoords.setVisibility(View.GONE);
        capturedLat = 0;
        capturedLng = 0;
    }

    private void listenForLocations() {
        db.collection("locations")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        if (error != null) return;

                        if (value != null) {
                            StringBuilder sb = new StringBuilder("Saved Locations:\n");
                            for (DocumentSnapshot doc : value) {
                                CompanyConfig config = doc.toObject(CompanyConfig.class);
                                if (config != null) {
                                    sb.append("- ").append(config.getName()).append("\n");
                                }
                            }
                            binding.tvLocationList.setText(sb.toString());
                        }
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}