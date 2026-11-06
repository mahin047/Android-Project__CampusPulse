package com.campuspulse.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FeedbackApi {

    private static final String ENDPOINT = "https://jsonplaceholder.typicode.com/posts";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void send(Context c, String message, CacheRepository.Callback<String> cb) {
        Context app = c.getApplicationContext();
        IO.execute(() -> {
            String result;
            if (!NetUtil.isOnline(app)) {
                result = "No internet. Please try again when you're online.";
            } else {
                try {
                    JSONObject json = new JSONObject();
                    json.put("title", "CampusPulse feedback");
                    json.put("body", message);
                    json.put("userId", 1);

                    ApiClient.ApiResponse r = ApiClient.postJson(ENDPOINT, json.toString());
                    if (r.code == 201) {   // 201 Created
                        String id = new JSONObject(r.body).optString("id", "?");
                        result = "Thank you! Server replied HTTP 201 Created (id " + id + ", "
                                + r.millis + " ms).";
                    } else {
                        result = "Server replied HTTP " + r.code + ". Please try again.";
                    }
                } catch (IOException | JSONException e) {
                    result = "Could not send. Check your internet.";
                }
            }
            final String res = result;
            MAIN.post(() -> cb.onResult(res));
        });
    }
}