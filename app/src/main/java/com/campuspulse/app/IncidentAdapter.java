package com.campuspulse.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campuspulse.app.databinding.ItemIncidentBinding;

import java.util.Objects;

@SuppressWarnings("unused")
public class IncidentAdapter extends ListAdapter<IncidentModel, IncidentAdapter.VH> {

    public interface Listener {
        void onIncidentClick(IncidentModel m);
    }

    private static final DiffUtil.ItemCallback<IncidentModel> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull IncidentModel a, @NonNull IncidentModel b) {
                    return Objects.equals(a.getId(), b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull IncidentModel a, @NonNull IncidentModel b) {
                    return Objects.equals(a.getCategory(), b.getCategory())
                            && Objects.equals(a.getLocation(), b.getLocation())
                            && Objects.equals(a.getDescription(), b.getDescription())
                            && Objects.equals(a.getStatus(), b.getStatus())
                            && Objects.equals(a.getAdminNote(), b.getAdminNote())
                            && Objects.equals(a.getCreatedAt(), b.getCreatedAt());
                }
            };

    private final Listener listener;
    private boolean showReporter = false;

    public IncidentAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @SuppressWarnings("NotifyDataSetChanged")
    public void setShowReporter(boolean value) {
        if (showReporter != value) {
            showReporter = value;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemIncidentBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        IncidentModel m = getItem(position);
        ItemIncidentBinding b = holder.binding;

        int sc = IncidentModel.statusColor(m.getStatus());
        int cc = IncidentModel.categoryColor(m.getCategory());

        b.priorityBar.setBackgroundTintList(ColorStateList.valueOf(sc));

        b.tvCategory.setText(m.getCategory() == null ? "OTHER" : m.getCategory().toUpperCase());
        b.tvCategory.setTextColor(cc);
        b.tvCategory.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(cc, 0x33)));

        b.tvStatus.setText(IncidentModel.statusLabel(m.getStatus()));
        b.tvStatus.setTextColor(sc);
        b.tvStatus.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(sc, 0x33)));

        b.tvTime.setText(TimeUtil.ago(m.getCreatedAt()));
        b.tvLocation.setText(m.getLocation());
        b.tvDesc.setText(m.getDescription());

        if (showReporter) {
            String sid = m.getReporterStudentId();
            Context context = b.getRoot().getContext();
            b.tvReporter.setVisibility(View.VISIBLE);
            if (sid != null && !sid.trim().isEmpty()) {
                b.tvReporter.setText(context.getString(
                        R.string.reported_by_with_id, m.getReporterName(), sid));
            } else {
                b.tvReporter.setText(context.getString(
                        R.string.reported_by, m.getReporterName()));
            }
        } else {
            b.tvReporter.setVisibility(View.GONE);
        }

        b.getRoot().setOnClickListener(v -> listener.onIncidentClick(m));
    }

    public static class VH extends RecyclerView.ViewHolder {
        final ItemIncidentBinding binding;

        VH(ItemIncidentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}