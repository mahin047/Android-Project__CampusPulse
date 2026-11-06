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

import com.campuspulse.app.databinding.ItemPlaceBinding;

public class PlaceAdapter extends ListAdapter<CampusPlace, PlaceAdapter.VH> {

    public interface Listener { void onPlaceClick(CampusPlace p); }

    private static final DiffUtil.ItemCallback<CampusPlace> DIFF = new DiffUtil.ItemCallback<CampusPlace>() {
        @Override
        public boolean areItemsTheSame(@NonNull CampusPlace a, @NonNull CampusPlace b) {
            return a.id.equals(b.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull CampusPlace a, @NonNull CampusPlace b) {
            return a.name.equals(b.name) && a.where.equals(b.where) && a.hours.equals(b.hours);
        }
    };

    private final Listener listener;

    public PlaceAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemPlaceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CampusPlace p = getItem(position);
        ItemPlaceBinding b = holder.binding;

        int color = CampusPlace.categoryColor(p.category);
        b.tvCategory.setText(p.category.toUpperCase());
        b.tvCategory.setTextColor(color);
        b.tvCategory.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x33)));

        b.tvName.setText(p.name);
        b.tvWhere.setText(p.where);
        b.tvHours.setText(p.hours);
        b.tvHours.setVisibility(p.hours.isEmpty() ? View.GONE : View.VISIBLE);
        b.getRoot().setOnClickListener(v -> listener.onPlaceClick(p));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemPlaceBinding binding;

        VH(ItemPlaceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}