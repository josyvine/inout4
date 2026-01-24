package com.inout.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.inout.app.databinding.FragmentAdminEmployeesBinding;
import com.inout.app.models.User;
import com.inout.app.models.CompanyConfig;
import com.inout.app.adapters.EmployeeListAdapter;

import java.util.ArrayList;
import java.util.List;

public class AdminEmployeesFragment extends Fragment implements EmployeeListAdapter.OnEmployeeActionListener {

    private static final String TAG = "AdminEmployeesFrag";
    private FragmentAdminEmployeesBinding binding;
    private FirebaseFirestore db;
    private EmployeeListAdapter adapter;
    private List<User> employeeList;
    private List<CompanyConfig> locationList; // To store office locations for the dropdown

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminEmployeesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        employeeList = new ArrayList<>();
        locationList = new ArrayList<>();
        
        setupRecyclerView();
        listenForEmployees();
        fetchLocations(); // Load locations early so they are ready for the dialog
    }

    private void setupRecyclerView() {
        binding.recyclerViewEmployees.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new EmployeeListAdapter(getContext(), employeeList, this);
        binding.recyclerViewEmployees.setAdapter(adapter);
    }

    /**
     * Fetches all official locations saved by the admin.
     */
    private void fetchLocations() {
        db.collection("locations").get().addOnSuccessListener(queryDocumentSnapshots -> {
            locationList.clear();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                CompanyConfig loc = doc.toObject(CompanyConfig.class);
                if (loc != null) {
                    loc.setId(doc.getId()); // Store the document ID
                    locationList.add(loc);
                }
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Error fetching locations", e));
    }

    private void listenForEmployees() {
        binding.progressBar.setVisibility(View.VISIBLE);
        db.collection("users")
                .whereEqualTo("role", "employee")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        binding.progressBar.setVisibility(View.GONE);
                        if (error != null) {
                            Log.e(TAG, "Listen failed.", error);
                            return;
                        }

                        if (value != null) {
                            employeeList.clear();
                            for (DocumentSnapshot doc : value) {
                                User user = doc.toObject(User.class);
                                if (user != null) {
                                    user.setUid(doc.getId());
                                    employeeList.add(user);
                                }
                            }
                            adapter.notifyDataSetChanged();
                            binding.tvEmptyView.setVisibility(employeeList.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    }
                });
    }

    @Override
    public void onApproveClicked(User user) {
        if (locationList.isEmpty()) {
            Toast.makeText(getContext(), "Error: Please save an Office Location first!", Toast.LENGTH_LONG).show();
            return;
        }
        showApproveDialog(user);
    }

    @Override
    public void onDeleteClicked(User user) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove Employee")
                .setMessage("Are you sure you want to remove " + user.getName() + "?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    db.collection("users").document(user.getUid()).delete();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Updated Dialog to include a Location Selection dropdown.
     */
    private void showApproveDialog(User user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Approve " + user.getName());
        builder.setMessage("Assign ID and choose the workplace location:");

        // Layout to hold the ID input and the Location Spinner
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 20, 60, 20);

        final EditText inputId = new EditText(requireContext());
        inputId.setHint("Employee ID (e.g. EMP001)");
        inputId.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        layout.addView(inputId);

        // Spinner (Dropdown) for locations
        final Spinner spinner = new Spinner(requireContext());
        spinner.setPadding(0, 30, 0, 30);
        List<String> names = new ArrayList<>();
        for (CompanyConfig c : locationList) names.add(c.getName());
        
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinAdapter);
        layout.addView(spinner);

        builder.setView(layout);

        builder.setPositiveButton("Approve", (dialog, which) -> {
            String empId = inputId.getText().toString().trim();
            int selectedIndex = spinner.getSelectedItemPosition();
            if (!empId.isEmpty() && selectedIndex >= 0) {
                String locId = locationList.get(selectedIndex).getId();
                approveUserInFirestore(user, empId, locId);
            } else {
                Toast.makeText(getContext(), "Employee ID and Location are required!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void approveUserInFirestore(User user, String empId, String locId) {
        db.collection("users").document(user.getUid())
                .update("approved", true, 
                        "employeeId", empId, 
                        "assignedLocationId", locId) // CRITICAL FIX: Link the location to the user
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Employee Approved and Location Assigned!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}