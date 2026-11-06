package com.campuspulse.app;

import android.content.Intent;

public class ChatMessage {
    public final String text;
    public final boolean mine;
    public final String actionLabel;
    public final Intent action;

    public ChatMessage(String text, boolean mine, String actionLabel, Intent action) {
        this.text = text;
        this.mine = mine;
        this.actionLabel = actionLabel;
        this.action = action;
    }
}