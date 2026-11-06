package com.campuspulse.app;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ApiClient {

    public static class ApiResponse {
        public int code;
        public String body = "";
        public String contentType = "";
        public long millis;

        public boolean isSuccess() { return code >= 200 && code < 300; }
    }

    private static final int MAX_CHARS = 512 * 1024;

    public static ApiResponse get(String url) throws IOException {
        return request("GET", url, null);
    }

    public static ApiResponse postJson(String url, String json) throws IOException {
        return request("POST", url, json);
    }

    /** Blocking call: main thread এ কখনো ডেকো না। */
    private static ApiResponse request(String method, String urlString, String json) throws IOException {
        long start = SystemClock.elapsedRealtime();
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "CampusPulse/1.0 (Android)");

            if (json != null) {
                byte[] out = json.getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setFixedLengthStreamingMode(out.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(out);
                }
            }

            ApiResponse r = new ApiResponse();
            r.code = conn.getResponseCode();
            String ct = conn.getContentType();
            r.contentType = ct == null ? "" : ct;

            // 4xx/5xx হলে body থাকে error stream এ
            InputStream is = r.code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[4096];
                    int n;
                    while ((n = br.read(buf)) != -1 && sb.length() < MAX_CHARS) sb.append(buf, 0, n);
                    r.body = sb.toString();
                }
            }
            r.millis = SystemClock.elapsedRealtime() - start;
            return r;
        } finally {
            conn.disconnect();
        }
    }
}