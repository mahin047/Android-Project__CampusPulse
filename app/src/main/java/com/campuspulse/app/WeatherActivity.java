package com.campuspulse.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.campuspulse.app.databinding.ActivityWeatherBinding;
import com.campuspulse.app.databinding.ItemForecastBinding;

import java.util.Date;

public class WeatherActivity extends AppCompatActivity {

    private ActivityWeatherBinding b;
    private WeatherRepository repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityWeatherBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        repo = WeatherRepository.get(this);

        b.btnBack.setOnClickListener(v -> finish());
        b.btnRefresh.setOnClickListener(v -> {
            b.btnRefresh.animate().rotationBy(360f).setDuration(600).start();
            load(true);
        });
        b.btnRetry.setOnClickListener(v -> load(true));

        // 1) saved data সাথে সাথে, 2) তারপর network
        repo.loadCached(r -> { if (r != null) render(r); });
        load(false);
    }

    private void load(boolean force) {
        if (b.content.getVisibility() != View.VISIBLE) {
            b.errorGroup.setVisibility(View.GONE);
            b.loading.setVisibility(View.VISIBLE);
        }
        repo.refresh(force, this::render);
    }

    private void render(WeatherRepository.Result r) {
        if (isDestroyed()) return;
        b.loading.setVisibility(View.GONE);

        if (r.weather == null) {                         // কিছুই নেই: error state
            b.content.setVisibility(View.GONE);
            b.errorGroup.setVisibility(View.VISIBLE);
            b.tvErrorText.setText((r.error == null ? "No weather data" : r.error)
                    + ".\nCheck your internet and try again.");
            return;
        }

        b.errorGroup.setVisibility(View.GONE);
        b.content.setVisibility(View.VISIBLE);

        WeatherModel w = r.weather;
        b.tvEmoji.setText(WeatherModel.emoji(w.code));
        b.tvTemp.setText(Math.round(w.temp) + "°");
        b.tvDesc.setText(WeatherModel.describe(w.code));
        b.tvFeels.setText("Feels like " + Math.round(w.feels) + "°C");
        b.tvHumidity.setText(w.humidity >= 0 ? w.humidity + "%" : "–");
        b.tvWind.setText(Math.round(w.wind) + " km/h");

        b.forecastList.removeAllViews();
        for (int i = 0; i < w.days.size(); i++) {
            WeatherModel.Day d = w.days.get(i);
            ItemForecastBinding f = ItemForecastBinding.inflate(getLayoutInflater(), b.forecastList, false);
            f.tvDay.setText(WeatherModel.dayLabel(d.date, i));
            f.tvEmoji.setText(WeatherModel.emoji(d.code));
            f.tvRange.setText(Math.round(d.min) + "° / " + Math.round(d.max) + "°");
            b.forecastList.addView(f.getRoot());
        }

        String when = r.fetchedAt == 0L ? "earlier" : TimeUtil.ago(new Date(r.fetchedAt));
        if (r.error != null) {
            b.tvStatus.setText("⚠ " + r.error + ". Showing saved data from " + when + ".");
            b.tvStatus.setTextColor(Color.parseColor("#F5B942"));
        } else {
            b.tvStatus.setText("Updated " + when + (r.fromCache ? " (saved copy)" : ""));
            b.tvStatus.setTextColor(Color.parseColor("#2DD4BF"));
        }

        // status code ও header, syllabus এর জন্য দৃশ্যমান প্রমাণ
        b.tvApiInfo.setText(r.httpCode == 0 ? ""
                : "GET api.open-meteo.com  •  HTTP " + r.httpCode + "  •  "
                + r.contentType.split(";")[0] + "  •  " + r.millis + " ms");
    }
}