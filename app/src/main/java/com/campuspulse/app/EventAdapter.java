package com.campuspulse.app;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campuspulse.app.databinding.ItemEventBinding;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EventAdapter extends ListAdapter<EventModel, EventAdapter.EventViewHolder> {

    public interface Listener {
        void onEventClick(EventModel e);
        void onEventLongClick(EventModel e);
    }

    private static final DiffUtil.ItemCallback<EventModel> DIFF = new DiffUtil.ItemCallback<EventModel>() {
        @Override
        public boolean areItemsTheSame(@NonNull EventModel a, @NonNull EventModel b) {
            return Objects.equals(a.getId(), b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull EventModel a, @NonNull EventModel b) {
            return Objects.equals(a.getTitle(), b.getTitle())
                    && Objects.equals(a.getVenue(), b.getVenue())
                    && Objects.equals(a.getCategory(), b.getCategory())
                    && Objects.equals(a.getDateTime(), b.getDateTime())
                    && a.getCapacity() == b.getCapacity()
                    && a.getRegisteredCount() == b.getRegisteredCount();
        }
    };

    private final Listener listener;
    private Set<String> registered = new HashSet<>();

    public EventAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setRegistered(Set<String> ids) {
        registered = new HashSet<>(ids);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEventBinding b = ItemEventBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new EventViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        EventModel e = getItem(position);
        ItemEventBinding b = holder.binding;

        int color = EventModel.categoryColor(e.getCategory());
        int soft = ColorUtils.setAlphaComponent(color, 0x33);

        b.dateBlock.setBackgroundTintList(ColorStateList.valueOf(soft));
        b.tvDay.setText(TimeUtil.day(e.getDateTime()));
        b.tvDay.setTextColor(color);
        b.tvMonth.setText(TimeUtil.month(e.getDateTime()));
        b.tvMonth.setTextColor(color);

        b.tvCategory.setText(e.getCategory() == null ? "OTHER" : e.getCategory().toUpperCase());
        b.tvCategory.setTextColor(color);
        b.tvCategory.setBackgroundTintList(ColorStateList.valueOf(soft));

        b.tvTitle.setText(e.getTitle());
        b.tvVenue.setText(e.getVenue());
        b.tvTime.setText(TimeUtil.clock(e.getDateTime()));

        // seat bar (hidden when capacity is unlimited)
        if (e.getCapacity() > 0) {
            b.progressSeats.setVisibility(View.VISIBLE);
            b.progressSeats.setMax(e.getCapacity());
            b.progressSeats.setProgress(Math.min(e.getRegisteredCount(), e.getCapacity()));
            b.progressSeats.setIndicatorColor(e.isFull() ? Color.parseColor("#FF5A5F") : color);
        } else {
            b.progressSeats.setVisibility(View.INVISIBLE);
        }

        // status pill
        String status;
        int sc;
        if (registered.contains(e.getId())) {
            status = "REGISTERED ✓";
            sc = Color.parseColor("#34D399");
        } else if (e.isPast()) {
            status = "ENDED";
            sc = Color.parseColor("#9DB2D1");
        } else if (e.isFull()) {
            status = "FULL";
            sc = Color.parseColor("#FF5A5F");
        } else {
            status = e.getRegisteredCount() + " going";
            sc = Color.parseColor("#2DD4BF");
        }
        b.tvStatus.setText(status);
        b.tvStatus.setTextColor(sc);
        b.tvStatus.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(sc, 0x33)));

        b.getRoot().setOnClickListener(v -> listener.onEventClick(e));
        b.getRoot().setOnLongClickListener(v -> {
            listener.onEventLongClick(e);
            return true;
        });
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        final ItemEventBinding binding;

        EventViewHolder(ItemEventBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}