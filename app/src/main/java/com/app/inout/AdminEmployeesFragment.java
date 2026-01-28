package com.inout.app;

import android.app.AlertDialog;
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

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.inout.app.databinding.FragmentAdminEmployeesBinding;
import com.inout.app.models.User;
import com.inout.app.models.CompanyConfig;
import com.inout.app.adapters.EmployeeListAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Updated Fragment to handle Multi-Selection, Bulk Deletion, 
 * and Individual/Bulk Location Assignment.
 * FIXED: Synchronized assignedLocationId key for database consistency.
 */
public class AdminEmployeesFragment extends Fragment implements EmployeeListAdapter.OnEmployeeActionListener {

    private static final String TAG = "AdminEmployeesFrag";
    private FragmentAdminEmployeesBinding binding;
    private FirebaseFirestore db;
    private EmployeeListAdapter adapter;
    private List<User> employeeList;
    private List<CompanyConfig> locationList; 

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
        fetchLocations(); 
    }

    private void setupRecyclerView() {
        binding.recyclerViewEmployees.setLayoutManager(new LinearLayoutManager(getContext()));
        // Initialize adapter with empty list and this fragment as the listener
        adapter = new EmployeeListAdapter(getContext(), employeeList, this);
        binding.recyclerViewEmployees.setAdapter(adapter);
    }

    private void fetchLocations() {
        db.collection("locations").addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e(TAG, "Failed to fetch locations", error);
                return;
            }
            if (value != null) {
                locationList.clear();
                for (DocumentSnapshot doc : value) {
                    CompanyConfig loc = doc.toObject(CompanyConfig.class);
                    if (loc != null) {
                        loc.setId(doc.getId()); // Store document ID for assignment
                        locationList.add(loc);
                    }
                }
            }
        });
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

    /**
     * Handles individual "Approve" button on the employee card.
     */
    @Override
    public void onApproveClicked(User user) {
        if (locationList.isEmpty()) {
            Toast.makeText(getContext(), "Please add an Office Location first!", Toast.LENGTH_LONG).show();
            return;
        }
        showIndividualApproveDialog(user);
    }

    private void showIndividualApproveDialog(User user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Approve " + user.getName());

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        final EditText inputId = new EditText(requireContext());
        inputId.setHint("Employee ID (e.g. EMP001)");
        inputId.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        if (user.getEmployeeId() != null) inputId.setText(user.getEmployeeId());
        layout.addView(inputId);

        final Spinner spinner = new Spinner(requireContext());
        spinner.setPadding(0, 40, 0, 40);
        List<String> names = new ArrayList<>();
        int currentSelection = 0;
        for (int i = 0; i < locationList.size(); i++) {
            names.add(locationList.get(i).getName());
            if (locationList.get(i).getId().equals(user.getAssignedLocationId())) {
                currentSelection = i;
            }
        }
        
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinAdapter);
        spinner.setSelection(currentSelection);
        layout.addView(spinner);

        builder.setView(layout);
        builder.setPositiveButton("Approve", (dialog, which) -> {
            String empId = inputId.getText().toString().trim();
            int selectedIndex = spinner.getSelectedItemPosition();
            if (!empId.isEmpty() && selectedIndex >= 0) {
                String locId = locationList.get(selectedIndex).getId();
                // WRITE: assignedLocationId using matching key
                db.collection("users").document(user.getUid())
                        .update("approved", true, 
                                "employeeId", empId, 
                                "assignedLocationId", locId)
                        .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Approved and Assigned!", Toast.LENGTH_SHORT).show());
            } else {
                Toast.makeText(getContext(), "ID and Location required!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Implements individual delete action.
     */
    @Override
    public void onDeleteClicked(User user) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove Employee")
                .setMessage("Delete " + user.getName() + "? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.collection("users").document(user.getUid()).delete()
                            .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Employee removed.", Toast.LENGTH_SHORT).show());
                }).setNegativeButton("Cancel", null).show();
    }

    /**
     * Triggered when Admin long-presses on a selection of employees.
     */
    @Override
    public void onBulkActionRequested(List<User> selectedUsers) {
        String[] options = {"Remove Selected Employees", "Add location from saved list"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Bulk Actions (" + selectedUsers.size() + " selected)");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                showBulkDeleteConfirmation(selectedUsers);
            } else if (which == 1) {
                showBulkLocationAssignment(selectedUsers);
            }
        });
        builder.show();
    }

    private void showBulkDeleteConfirmation(List<User> selectedUsers) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Confirm Removal")
                .setMessage("Are you sure you want to remove " + selectedUsers.size() + " employees?")
                .setPositiveButton("Remove All", (dialog, which) -> {
                    performBulkDelete(selectedUsers);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performBulkDelete(List<User> selectedUsers) {
        WriteBatch batch = db.batch();
        for (User user : selectedUsers) {
            batch.delete(db.collection("users").document(user.getUid()));
        }
        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(getContext(), "Selected employees removed.", Toast.LENGTH_SHORT).show();
            adapter.clearSelection();
        });
    }

    private void showBulkLocationAssignment(List<User> selectedUsers) {
        if (locationList.isEmpty()) {
            Toast.makeText(getContext(), "Add an Office Location first!", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Assign Location to " + selectedUsers.size() + " Users");

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        final Spinner spinner = new Spinner(requireContext());
        List<String> names = new ArrayList<>();
        for (CompanyConfig c : locationList) names.add(c.getName());
        
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinAdapter);
        layout.addView(spinner);

        builder.setView(layout);
        builder.setPositiveButton("Assign and Approve", (dialog, which) -> {
            int selectedIndex = spinner.getSelectedItemPosition();
            if (selectedIndex >= 0) {
                String locId = locationList.get(selectedIndex).getId();
                performBulkAssignment(selectedUsers, locId);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void performBulkAssignment(List<User> selectedUsers, String locId) {
        WriteBatch batch = db.batch();
        for (User user : selectedUsers) {
            // WRITE: assignedLocationId using matching key
            batch.update(db.collection("users").document(user.getUid()), 
                    "assignedLocationId", locId,
                    "approved", true);
        }
        
        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(getContext(), "Location assigned to selection.", Toast.LENGTH_SHORT).show();
            adapter.clearSelection();
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Bulk update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}