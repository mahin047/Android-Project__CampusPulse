package com.campuspulse.app;

import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.campuspulse.app.databinding.ItemChatBinding;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

    public interface Listener { void onAction(ChatMessage m); }

    private final List<ChatMessage> items = new ArrayList<>();
    private final Listener listener;

    public ChatAdapter(Listener listener) {
        this.listener = listener;
    }

    public int add(ChatMessage m) {
        items.add(m);
        notifyItemInserted(items.size() - 1);
        return items.size() - 1;
    }

    public void removeLast() {
        if (items.isEmpty()) return;
        int i = items.size() - 1;
        items.remove(i);
        notifyItemRemoved(i);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemChatBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ChatMessage m = items.get(position);
        ItemChatBinding b = holder.binding;

        b.bubble.setText(m.text);
        b.bubble.setBackgroundResource(m.mine ? R.drawable.bg_bubble_user : R.drawable.bg_bubble_bot);
        b.bubble.setTextColor(Color.parseColor(m.mine ? "#06201E" : "#FFFFFF"));

        int gravity = m.mine ? Gravity.END : Gravity.START;
        LinearLayout.LayoutParams bubbleLp = (LinearLayout.LayoutParams) b.bubble.getLayoutParams();
        bubbleLp.gravity = gravity;
        b.bubble.setLayoutParams(bubbleLp);

        if (m.action != null && m.actionLabel != null) {
            b.btnAction.setVisibility(View.VISIBLE);
            b.btnAction.setText(m.actionLabel);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.btnAction.getLayoutParams();
            lp.gravity = Gravity.START;
            b.btnAction.setLayoutParams(lp);
            b.btnAction.setOnClickListener(v -> listener.onAction(m));
        } else {
            b.btnAction.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemChatBinding binding;

        VH(ItemChatBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}