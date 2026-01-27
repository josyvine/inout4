package com.inout.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.inout.app.models.CompanyConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Professional Adapter for managing interactive Office Locations.
 * Features: Single tap to select/deselect, Long press to trigger bulk actions.
 */
public class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.LocationViewHolder> {

    private final List<CompanyConfig> locationList;
    private final OnLocationActionListener listener;
    
    // Stores the Document IDs of selected locations for multi-deletion
    private final Set<String> selectedLocationIds = new HashSet<>();

    public interface OnLocationActionListener {
        // Triggered when items are selected and a long press occurs
        void onDeleteRequested(List<CompanyConfig> selectedLocations);
    }

    public LocationAdapter(List<CompanyConfig> locationList, OnLocationActionListener listener) {
        this.locationList = locationList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_location, parent, false);
        return new LocationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        CompanyConfig location = locationList.get(position);

        holder.tvName.setText(location.getName());

        // Visual feedback: Show checkmark and overlay if the item is selected
        if (selectedLocationIds.contains(location.getId())) {
            holder.ivCheck.setVisibility(View.VISIBLE);
            holder.viewOverlay.setVisibility(View.VISIBLE);
        } else {
            holder.ivCheck.setVisibility(View.GONE);
            holder.viewOverlay.setVisibility(View.GONE);
        }

        // SINGLE TAP logic: Toggle selection and update UI
        holder.itemView.setOnClickListener(v -> {
            toggleSelection(location.getId());
        });

        // LONG PRESS logic: Trigger the delete pop-up for all selected items
        holder.itemView.setOnLongClickListener(v -> {
            if (!selectedLocationIds.isEmpty()) {
                // Ensure the long-pressed item is included in the selection
                if (!selectedLocationIds.contains(location.getId())) {
                    toggleSelection(location.getId());
                }
                
                // Notify the fragment to show the Delete Confirmation
                if (listener != null) {
                    listener.onDeleteRequested(getSelectedLocations());
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Adds or removes a location ID from the selection set.
     */
    private void toggleSelection(String locationId) {
        if (selectedLocationIds.contains(locationId)) {
            selectedLocationIds.remove(locationId);
        } else {
            selectedLocationIds.add(locationId);
        }
        notifyDataSetChanged();
    }

    /**
     * Converts the set of selected IDs back into a list of Location objects.
     */
    public List<CompanyConfig> getSelectedLocations() {
        List<CompanyConfig> selected = new ArrayList<>();
        for (CompanyConfig loc : locationList) {
            if (selectedLocationIds.contains(loc.getId())) {
                selected.add(loc);
            }
        }
        return selected;
    }

    public void clearSelection() {
        selectedLocationIds.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return locationList.size();
    }

    static class LocationViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        ImageView ivCheck;
        View viewOverlay;

        public LocationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_location_name);
            ivCheck = itemView.findViewById(R.id.iv_selected);
            viewOverlay = itemView.findViewById(R.id.view_selected_overlay);
        }
    }
}