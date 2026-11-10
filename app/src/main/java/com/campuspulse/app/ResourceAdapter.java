package com.campuspulse.app;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campuspulse.app.databinding.ItemResourceBinding;

public class ResourceAdapter extends ListAdapter<StudyResource, ResourceAdapter.VH> {

    public interface Listener { void onResourceClick(StudyResource r); }

    private static final DiffUtil.ItemCallback<StudyResource> DIFF = new DiffUtil.ItemCallback<StudyResource>() {
        @Override
        public boolean areItemsTheSame(@NonNull StudyResource a, @NonNull StudyResource b) {
            return a.id.equals(b.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull StudyResource a, @NonNull StudyResource b) {
            return a.type.equals(b.type) && a.url.equals(b.url) && a.title.equals(b.title)
                    && a.courseCode.equals(b.courseCode) && a.courseTitle.equals(b.courseTitle)
                    && a.dept.equals(b.dept);
        }
    };

    private final Listener listener;

    public ResourceAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemResourceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        StudyResource r = getItem(position);
        ItemResourceBinding b = holder.binding;

        int color = StudyResource.typeColor(r.type);
        b.tvType.setText(r.type.toUpperCase());
        b.tvType.setTextColor(color);
        b.tvType.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x33)));
        b.tvTitle.setText(r.title);
        b.tvCourse.setText(r.courseCode + "  •  " + r.courseTitle);
        b.getRoot().setOnClickListener(v -> listener.onResourceClick(r));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemResourceBinding binding;

        VH(ItemResourceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}