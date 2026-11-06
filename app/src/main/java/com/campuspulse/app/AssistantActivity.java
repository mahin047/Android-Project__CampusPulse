package com.campuspulse.app;

import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.campuspulse.app.databinding.ActivityAssistantBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AssistantActivity extends AppCompatActivity implements ChatAdapter.Listener {

    private static final String[] SUGGESTIONS = {
            "Where is the library?", "Any event today?", "Latest notice",
            "How is the weather?", "Medical center hours", "Study notes", "Emergency help"};

    private ActivityAssistantBinding b;
    private ChatAdapter adapter;
    private AssistantEngine engine;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAssistantBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }
        engine = new AssistantEngine(this, u.getUid());

        adapter = new ChatAdapter(this);
        b.rvChat.setLayoutManager(new LinearLayoutManager(this));
        b.rvChat.setAdapter(adapter);

        b.btnBack.setOnClickListener(v -> finish());
        b.btnSend.setOnClickListener(v -> sendFromInput());
        b.etInput.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendFromInput();
                return true;
            }
            return false;
        });

        buildSuggestions();
        addBot("Hi! I'm the CampusPulse assistant 👋\nAsk me about places, events, notices or the weather. "
                + "Tap a suggestion below to start.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private void buildSuggestions() {
        for (String s : SUGGESTIONS) {
            TextView chip = new TextView(this);
            chip.setText(s);
            chip.setTextSize(12);
            chip.setTypeface(ResourcesCompat.getFont(this, R.font.poppins_bold));
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setTextColor(Color.parseColor("#C3D2EA"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> ask(s));
            b.suggestRow.addView(chip);
        }
    }

    private void sendFromInput() {
        String text = String.valueOf(b.etInput.getText()).trim();
        if (text.isEmpty()) return;
        b.etInput.setText("");
        ask(text);
    }

    private void ask(String text) {
        if (busy) return;
        busy = true;
        scrollTo(adapter.add(new ChatMessage(text, true, null, null)));
        scrollTo(adapter.add(new ChatMessage("typing…", false, null, null)));

        engine.answer(text, reply -> handler.postDelayed(() -> {   // একটু delay, chat এর অনুভূতির জন্য
            if (isDestroyed()) return;
            adapter.removeLast();
            scrollTo(adapter.add(new ChatMessage(reply.text, false, reply.actionLabel, reply.action)));
            busy = false;
        }, 450));
    }

    private void addBot(String text) {
        adapter.add(new ChatMessage(text, false, null, null));
    }

    private void scrollTo(int position) {
        b.rvChat.scrollToPosition(position);
    }

    @Override
    public void onAction(ChatMessage m) {
        try {
            startActivity(m.action);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No app found to open this.", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}