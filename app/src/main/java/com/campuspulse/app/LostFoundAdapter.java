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

import com.campuspulse.app.databinding.ItemLostfoundBinding;

import java.util.Objects;

@SuppressWarnings("unused")
public class LostFoundAdapter extends ListAdapter<LostFoundModel, LostFoundAdapter.VH> {

    public interface Listener {
        void onItemClick(LostFoundModel item);
    }

    private static final DiffUtil.ItemCallback<LostFoundModel> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull LostFoundModel a, @NonNull LostFoundModel b) {
                    return Objects.equals(a.getId(), b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull LostFoundModel a, @NonNull LostFoundModel b) {
                    return Objects.equals(a.getItemName(), b.getItemName())
                            && Objects.equals(a.getLocation(), b.getLocation())
                            && Objects.equals(a.getType(), b.getType())
                            && Objects.equals(a.getCategory(), b.getCategory())
                            && a.isResolved() == b.isResolved()
                            && Objects.equals(a.getCreatedAt(), b.getCreatedAt());
                }
            };

    private final Listener listener;

    public LostFoundAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemLostfoundBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        LostFoundModel m = getItem(position);
        ItemLostfoundBinding b = holder.binding;

        int color = LostFoundModel.typeColor(m.getType());
        b.tvType.setText(m.isLost() ? R.string.lost : R.string.found);
        b.tvType.setTextColor(color);
        b.tvType.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x33)));

        b.tvResolved.setVisibility(m.isResolved() ? View.VISIBLE : View.GONE);
        b.tvResolved.setBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));

        b.tvAgo.setText(TimeUtil.ago(m.getCreatedAt()));
        b.tvTitle.setText(m.getItemName());
        b.tvLocation.setText(m.getLocation());
        b.tvMeta.setText(b.getRoot().getContext().getString(
                m.isLost() ? R.string.lost_on : R.string.found_on,
                TimeUtil.dateOnly(m.getEventDate()),
                m.getCategory()));

        b.getRoot().setAlpha(m.isResolved() ? 0.55f : 1f);
        b.getRoot().setOnClickListener(v -> listener.onItemClick(m));
    }

    public static class VH extends RecyclerView.ViewHolder {
        final ItemLostfoundBinding binding;

        VH(ItemLostfoundBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}