package com.inout.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.inout.app.models.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter to handle Multi-Selection, Bulk Actions, and Individual Approvals.
 */
public class EmployeeListAdapter extends RecyclerView.Adapter<EmployeeListAdapter.EmployeeViewHolder> {

    private final Context context;
    private final List<User> employeeList;
    private final OnEmployeeActionListener listener;
    
    private final Set<String> selectedUserIds = new HashSet<>();

    public interface OnEmployeeActionListener {
        void onApproveClicked(User user);
        void onDeleteClicked(User user);
        void onBulkActionRequested(List<User> selectedUsers);
    }

    public EmployeeListAdapter(Context context, List<User> employeeList, OnEmployeeActionListener listener) {
        this.context = context;
        this.employeeList = employeeList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public EmployeeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_employee, parent, false);
        return new EmployeeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EmployeeViewHolder holder, int position) {
        User user = employeeList.get(position);

        holder.tvName.setText(user.getName());
        holder.tvPhone.setText(user.getPhone() != null ? user.getPhone() : "No Phone");
        
        if (user.isApproved()) {
            String idSuffix = (user.getEmployeeId() != null) ? " (" + user.getEmployeeId() + ")" : "";
            holder.tvStatus.setText("Status: Approved" + idSuffix);
            holder.tvStatus.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
            holder.btnApprove.setVisibility(View.GONE);
        } else {
            holder.tvStatus.setText("Status: Pending Approval");
            holder.tvStatus.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
            holder.btnApprove.setVisibility(View.VISIBLE);
        }
        
        if (selectedUserIds.contains(user.getUid())) {
            holder.viewOverlay.setVisibility(View.VISIBLE);
            holder.ivCheck.setVisibility(View.VISIBLE);
        } else {
            holder.viewOverlay.setVisibility(View.GONE);
            holder.ivCheck.setVisibility(View.GONE);
        }

        holder.ivProfile.setImageResource(R.drawable.inout); 

        // Individual Approve Button Click
        holder.btnApprove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onApproveClicked(user);
            }
        });

        // Toggle Selection on tap
        holder.itemView.setOnClickListener(v -> {
            toggleSelection(user.getUid());
        });

        // Handle Individual Options or Bulk Action on long press
        holder.itemView.setOnLongClickListener(v -> {
            if (selectedUserIds.isEmpty()) {
                if (listener != null) {
                    listener.onDeleteClicked(user);
                }
            } else {
                if (!selectedUserIds.contains(user.getUid())) {
                    toggleSelection(user.getUid());
                }
                if (listener != null) {
                    listener.onBulkActionRequested(getSelectedUsers());
                }
            }
            return true;
        });
    }

    private void toggleSelection(String uid) {
        if (selectedUserIds.contains(uid)) {
            selectedUserIds.remove(uid);
        } else {
            selectedUserIds.add(uid);
        }
        notifyDataSetChanged();
    }

    public List<User> getSelectedUsers() {
        List<User> selectedUsers = new ArrayList<>();
        for (User user : employeeList) {
            if (selectedUserIds.contains(user.getUid())) {
                selectedUsers.add(user);
            }
        }
        return selectedUsers;
    }

    public void clearSelection() {
        selectedUserIds.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return employeeList.size();
    }

    static class EmployeeViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProfile, ivCheck;
        TextView tvName, tvPhone, tvStatus;
        View viewOverlay;
        Button btnApprove;

        public EmployeeViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfile = itemView.findViewById(R.id.iv_employee_photo);
            ivCheck = itemView.findViewById(R.id.iv_select_check);
            tvName = itemView.findViewById(R.id.tv_employee_name);
            tvPhone = itemView.findViewById(R.id.tv_employee_phone);
            tvStatus = itemView.findViewById(R.id.tv_employee_status);
            viewOverlay = itemView.findViewById(R.id.view_selected_overlay);
            btnApprove = itemView.findViewById(R.id.btn_approve_employee);
        }
    }
}