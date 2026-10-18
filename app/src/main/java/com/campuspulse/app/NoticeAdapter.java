package com.campuspulse.app;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campuspulse.app.databinding.ItemNoticeBinding;

import java.util.Objects;

public class NoticeAdapter extends ListAdapter<NoticeModel, NoticeAdapter.NoticeViewHolder> {

    public interface Listener {
        void onNoticeClick(NoticeModel notice);
        void onNoticeLongClick(NoticeModel notice);
    }

    private static final DiffUtil.ItemCallback<NoticeModel> DIFF = new DiffUtil.ItemCallback<NoticeModel>() {
        @Override
        public boolean areItemsTheSame(@NonNull NoticeModel a, @NonNull NoticeModel b) {
            return Objects.equals(a.getId(), b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull NoticeModel a, @NonNull NoticeModel b) {
            return Objects.equals(a.getTitle(), b.getTitle())
                    && Objects.equals(a.getBody(), b.getBody())
                    && Objects.equals(a.getCategory(), b.getCategory())
                    && Objects.equals(a.getPriority(), b.getPriority())
                    && Objects.equals(a.getCreatedAt(), b.getCreatedAt());
        }
    };

    private final Listener listener;

    public NoticeAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public NoticeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNoticeBinding b = ItemNoticeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new NoticeViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull NoticeViewHolder holder, int position) {
        NoticeModel n = getItem(position);
        ItemNoticeBinding b = holder.binding;

        int color = NoticeModel.priorityColor(n.getPriority());
        b.priorityBar.setBackgroundTintList(ColorStateList.valueOf(color));
        b.tvCategory.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x33)));
        b.tvCategory.setTextColor(color);
        b.tvCategory.setText(NoticeModel.badgeText(n));

        b.tvTitle.setText(n.getTitle());
        b.tvPreview.setText(n.getBody());
        b.tvTime.setText(TimeUtil.listTime(n.getCreatedAt()));

        b.getRoot().setOnClickListener(v -> listener.onNoticeClick(n));
        b.getRoot().setOnLongClickListener(v -> {
            listener.onNoticeLongClick(n);
            return true;
        });
    }

    static class NoticeViewHolder extends RecyclerView.ViewHolder {
        final ItemNoticeBinding binding;

        NoticeViewHolder(ItemNoticeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}