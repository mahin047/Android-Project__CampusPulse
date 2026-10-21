package com.campuspulse.app;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campuspulse.app.databinding.ItemSosAlertBinding;

import java.util.Objects;

public class SosAlertAdapter extends ListAdapter<SosAlertModel, SosAlertAdapter.VH> {

    public interface Listener {
        void onRespond(SosAlertModel m);
        void onResolve(SosAlertModel m);
        void onMap(SosAlertModel m);
    }

    private static final DiffUtil.ItemCallback<SosAlertModel> DIFF =
            new DiffUtil.ItemCallback<SosAlertModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull SosAlertModel a, @NonNull SosAlertModel b) {
                    return Objects.equals(a.getId(), b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull SosAlertModel a, @NonNull SosAlertModel b) {
                    return Objects.equals(a.getStatus(), b.getStatus())
                            && Objects.equals(a.getLat(), b.getLat())
                            && Objects.equals(a.getLng(), b.getLng())
                            && Objects.equals(a.getUpdatedAtRaw(), b.getUpdatedAtRaw())
                            && Objects.equals(a.getHandledBy(), b.getHandledBy());
                }
            };

    private final Listener listener;

    public SosAlertAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemSosAlertBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        SosAlertModel m = getItem(position);
        ItemSosAlertBinding b = holder.binding;

        int sc = SosAlertModel.statusColor(m.getStatus());
        int tc = SosAlertModel.typeColor(m.getType());

        b.priorityBar.setBackgroundTintList(ColorStateList.valueOf(sc));
        b.tvType.setText(m.getType() == null ? "SOS" : m.getType().toUpperCase());
        b.tvType.setTextColor(tc);
        b.tvType.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(tc, 0x33)));
        b.tvStatus.setText(SosAlertModel.statusLabel(m.getStatus()));
        b.tvStatus.setTextColor(sc);
        b.tvStatus.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(sc, 0x33)));
        b.tvTime.setText(TimeUtil.ago(m.getCreatedDate()));

        String sid = m.getStudentId();
        b.tvWho.setText(m.getName() + (sid == null || sid.isEmpty() ? "" : "  •  " + sid));

        if (m.hasLocation()) {
            String acc = m.getAccuracy() == null ? "" : "  •  ±" + m.getAccuracy() + " m";
            b.tvLocation.setText("📍 Live location" + acc + "  •  updated "
                    + TimeUtil.ago(m.getUpdatedDate()));
        } else {
            b.tvLocation.setText("No location shared");
        }

        b.btnMap.setVisibility(m.hasLocation() ? View.VISIBLE : View.GONE);
        b.btnMap.setOnClickListener(v -> listener.onMap(m));

        boolean active = SosAlertModel.ACTIVE.equals(m.getStatus());
        boolean ack = SosAlertModel.ACK.equals(m.getStatus());
        b.btnAction.setVisibility(active || ack ? View.VISIBLE : View.GONE);
        b.btnAction.setText(active ? "I'm responding" : "Mark resolved");
        b.btnAction.setBackgroundTintList(ColorStateList.valueOf(
                android.graphics.Color.parseColor(active ? "#F5B942" : "#34D399")));
        b.btnAction.setOnClickListener(v -> {
            if (active) listener.onRespond(m);
            else listener.onResolve(m);
        });
        b.actionRow.setVisibility(m.hasLocation() || active || ack ? View.VISIBLE : View.GONE);
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemSosAlertBinding binding;

        VH(ItemSosAlertBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}