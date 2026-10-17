package com.campuspulse.app;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.campuspulse.app.databinding.ItemModuleBinding;

import java.util.List;

public class ModuleAdapter extends RecyclerView.Adapter<ModuleAdapter.ModuleViewHolder> {

    public interface OnModuleClickListener {
        void onModuleClick(ModuleItem item);
    }

    private final List<ModuleItem> items;
    private final OnModuleClickListener listener;

    public ModuleAdapter(List<ModuleItem> items, OnModuleClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ModuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemModuleBinding b = ItemModuleBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ModuleViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull ModuleViewHolder holder, int position) {
        ModuleItem item = items.get(position);
        ItemModuleBinding b = holder.binding;

        b.ivIcon.setImageResource(item.getIconRes());
        b.tvTitle.setText(item.getTitle());
        b.tvSubtitle.setText(item.getSubtitle());
        b.getRoot().setBackgroundResource(item.getBgRes());
        b.getRoot().setOnClickListener(v -> listener.onModuleClick(item));

        // press effect (false returns so the click still works)
        b.getRoot().setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            return false;
        });

        // staggered entrance
        b.getRoot().setAlpha(0f);
        b.getRoot().setTranslationY(50f);
        b.getRoot().animate().alpha(1f).translationY(0f)
                .setStartDelay(450L + position * 80L).setDuration(450)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ModuleViewHolder extends RecyclerView.ViewHolder {
        final ItemModuleBinding binding;

        ModuleViewHolder(ItemModuleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}