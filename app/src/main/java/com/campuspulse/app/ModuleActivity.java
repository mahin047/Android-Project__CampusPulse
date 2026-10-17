package com.campuspulse.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.campuspulse.app.databinding.ActivityModuleBinding;

public class ModuleActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_NOTE = "extra_note";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityModuleBinding binding = ActivityModuleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // data received from the previous Activity
        binding.tvModuleTitle.setText(getIntent().getStringExtra(EXTRA_TITLE));
        binding.tvModuleNote.setText(getIntent().getStringExtra(EXTRA_NOTE));

        binding.btnBack.setOnClickListener(v -> finish());
    }
}