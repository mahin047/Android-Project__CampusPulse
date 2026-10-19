package com.campuspulse.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.campuspulse.app.databinding.ActivityReportIncidentBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportIncidentActivity extends AppCompatActivity {

    private static final String LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION;

    private ActivityReportIncidentBinding binding;
    private FirebaseFirestore db;
    private String uid, myName = "Student", myStudentId = "";

    private String photoB64 = null;       // compressed photo, base64
    private Double lat = null, lng = null;

    // heavy work (decoding and compressing the photo) runs off the main thread
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    // Android Photo Picker: needs NO storage permission
    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) processPhoto(uri);
            });

    // Runtime permission request for location
    private final ActivityResultLauncher<String> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    fetchLocation();
                } else {
                    binding.switchLocation.setChecked(false);
                    Toast.makeText(this, "Location permission denied. The report will be sent without it.",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReportIncidentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }
        uid = u.getUid();

        binding.actCategory.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, IncidentModel.CATEGORIES));
        binding.actCategory.setText(IncidentModel.CATEGORIES[0], false);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.photoEmpty.setOnClickListener(v -> pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        binding.btnRemovePhoto.setOnClickListener(v -> clearPhoto());
        binding.switchLocation.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                requestLocation();
            } else {
                lat = null;
                lng = null;
                binding.tvLocationStatus.setText("Off. Only used if you turn it on.");
            }
        });
        binding.btnSubmit.setOnClickListener(v -> submit());

        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            String n = doc.getString("name");
            String s = doc.getString("studentId");
            if (n != null && !n.trim().isEmpty()) myName = n.trim();
            if (s != null) myStudentId = s;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    // ---------- photo (background thread) ----------
    private void processPhoto(Uri uri) {
        binding.photoEmpty.setVisibility(View.INVISIBLE);
        binding.photoLoading.setVisibility(View.VISIBLE);

        io.execute(() -> {
            try {
                byte[] data = ImageUtil.compress(getContentResolver(), uri);
                String b64 = ImageUtil.toBase64(data);
                Bitmap preview = BitmapFactory.decodeByteArray(data, 0, data.length);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    photoB64 = b64;
                    binding.ivPhoto.setImageBitmap(preview);
                    binding.photoLoading.setVisibility(View.GONE);
                    binding.photoPreview.setVisibility(View.VISIBLE);
                    binding.photoEmpty.setVisibility(View.GONE);
                });
            } catch (IOException | RuntimeException ex) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    binding.photoLoading.setVisibility(View.GONE);
                    binding.photoEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Could not read that image. Try another one.",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void clearPhoto() {
        photoB64 = null;
        binding.ivPhoto.setImageDrawable(null);
        binding.photoPreview.setVisibility(View.GONE);
        binding.photoEmpty.setVisibility(View.VISIBLE);
    }

    // ---------- location: runtime permission flow ----------
    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION)
                == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, LOCATION_PERMISSION)) {
            // the user denied once: explain why before asking again
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Share your approximate location?")
                    .setMessage("It helps the administration find the problem faster. "
                            + "Only a rough position (about 100 m) is attached, and only to this report.")
                    .setNegativeButton("Not now", (d, w) -> binding.switchLocation.setChecked(false))
                    .setPositiveButton("Continue", (d, w) -> locationPermission.launch(LOCATION_PERMISSION))
                    .setOnCancelListener(d -> binding.switchLocation.setChecked(false))
                    .show();
        } else {
            locationPermission.launch(LOCATION_PERMISSION);
        }
    }

    @SuppressLint("MissingPermission") // permission is checked before this is called
    private void fetchLocation() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location best = null;
        if (lm != null) {
            // coarse permission allows the network and passive providers
            for (String provider : new String[]{LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER}) {
                try {
                    if (!lm.isProviderEnabled(provider)) continue;
                    Location l = lm.getLastKnownLocation(provider);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (SecurityException | IllegalArgumentException ignored) { }
            }
        }
        if (best == null) {
            binding.switchLocation.setChecked(false);
            binding.tvLocationStatus.setText("Couldn't get your location right now.");
            Toast.makeText(this, "Location not available. The report will be sent without it.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        // round to 3 decimals (about 110 m): approximate on purpose
        lat = Math.round(best.getLatitude() * 1000d) / 1000d;
        lng = Math.round(best.getLongitude() * 1000d) / 1000d;
        binding.tvLocationStatus.setText("Approximate location attached ✓");
    }

    // ---------- validate + send ----------
    private void submit() {
        String category = String.valueOf(binding.actCategory.getText()).trim();
        String location = String.valueOf(binding.etLocation.getText()).trim();
        String desc = String.valueOf(binding.etDesc.getText()).trim();

        binding.tilLocation.setError(null);
        binding.tilDesc.setError(null);

        if (location.length() < 2) { binding.tilLocation.setError("Tell us where it is"); return; }
        if (desc.length() < 10) { binding.tilDesc.setError("Describe the problem (at least 10 characters)"); return; }

        binding.btnSubmit.setEnabled(false);
        binding.btnSubmit.setText("Sending...");

        DocumentReference ref = db.collection("incidents").document();

        Map<String, Object> inc = new HashMap<>();
        inc.put("category", category);
        inc.put("location", location);
        inc.put("description", desc);
        inc.put("status", IncidentModel.PENDING);
        inc.put("reporterUid", uid);
        inc.put("reporterName", myName);
        inc.put("reporterStudentId", myStudentId);
        inc.put("hasPhoto", photoB64 != null);
        inc.put("adminNote", "");
        inc.put("createdAt", FieldValue.serverTimestamp());
        inc.put("updatedAt", FieldValue.serverTimestamp());
        if (lat != null && lng != null) {
            inc.put("lat", lat);
            inc.put("lng", lng);
        }

        WriteBatch batch = db.batch();
        batch.set(ref, inc);
        if (photoB64 != null) {
            Map<String, Object> att = new HashMap<>();
            att.put("data", photoB64);
            att.put("reporterUid", uid);
            batch.set(ref.collection("attachments").document("photo"), att);
        }

        if (NetUtil.isOnline(this)) {
            batch.commit()
                    .addOnSuccessListener(v -> {
                        Toast.makeText(this, "Report sent. We'll update you here.", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        binding.btnSubmit.setEnabled(true);
                        binding.btnSubmit.setText("Send report");
                        Toast.makeText(this, "Could not send the report. Try again.",
                                Toast.LENGTH_LONG).show();
                    });
        } else {
            // Offline: Firestore keeps the write in its local queue and sends it when the
            // network returns. The commit task only completes after the server confirms,
            // so we do not wait for it here.
            batch.commit();
            Toast.makeText(this, "No internet. Your report is saved and will be sent automatically.",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }
}