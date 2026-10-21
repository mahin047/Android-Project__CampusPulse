package com.campuspulse.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.location.LocationManagerCompat;

import com.campuspulse.app.databinding.ActivitySosBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SosActivity extends AppCompatActivity implements SosButtonView.OnSosListener {

    private static final String FINE = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String COARSE = Manifest.permission.ACCESS_COARSE_LOCATION;

    private ActivitySosBinding b;
    private String uid, myName = "Student", myStudentId = "";
    private String selectedType = SosAlertModel.TYPES[0];
    private final List<TextView> chips = new ArrayList<>();

    private String currentId = null;          // active alert id
    private DatabaseReference alertRef;
    private ValueEventListener alertListener;

    private SosTrackingService service;
    private boolean bound = false, receiverOn = false;

    // runtime permission: location + notifications
    private final ActivityResultLauncher<String[]> permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasLocationPermission()) {
                    startTracking();
                } else {
                    Toast.makeText(this, "Alert sent without live location (permission denied).",
                            Toast.LENGTH_LONG).show();
                    b.tvLive.setText("Location is not being shared (permission denied).");
                }
            });

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((SosTrackingService.LocalBinder) binder).getService();
            service.setUpdateListener((loc, n) -> showLive(loc, n));
            showLive(service.getLastLocation(), service.getSentCount());
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    // Service → Activity: SOS শেষ হলে broadcast আসে
    private final BroadcastReceiver endedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { unbindFromService(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySosBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }
        uid = u.getUid();

        b.btnBack.setOnClickListener(v -> finish());
        b.sosButton.setOnSosListener(this);
        b.btnSafe.setOnClickListener(v -> confirmCancel());
        b.btnCall.setOnClickListener(v -> callEmergency());

        buildChips();

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String n = doc.getString("name");
                    String s = doc.getString("studentId");
                    if (n != null && !n.trim().isEmpty()) myName = n.trim();
                    if (s != null) myStudentId = s;
                });
    }

    // ---------- lifecycle: listener, receiver, binding ----------
    @Override
    protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(this, endedReceiver,
                new IntentFilter(SosTrackingService.ACTION_ENDED), ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverOn = true;

        currentId = SosPrefs.get(this, uid);
        if (currentId != null) {
            attachWatcher(currentId);
            if (hasLocationPermission()) startTracking(); // idempotent: চলমান থাকলে শুধু bind হয়
        } else {
            showIdle();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachWatcher();
        unbindFromService();
        if (receiverOn) {
            unregisterReceiver(endedReceiver);
            receiverOn = false;
        }
    }

    // ---------- trigger ----------
    @Override
    public void onSosTriggered() {
        if (currentId != null) return;
        if (!NetUtil.isOnline(this)) {
            Toast.makeText(this, "No internet. The alert will be sent when you reconnect. Call 999 now!",
                    Toast.LENGTH_LONG).show();
        }
        sendAlert();
    }

    @Override
    public void onReleasedEarly() {
        Toast.makeText(this, "Keep holding the button for 1.5 seconds.", Toast.LENGTH_SHORT).show();
    }

    private void sendAlert() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("sos_alerts").push();
        final String id = ref.getKey();

        Map<String, Object> m = new HashMap<>();
        m.put("uid", uid);
        m.put("name", myName);
        m.put("studentId", myStudentId);
        m.put("type", selectedType);
        m.put("status", SosAlertModel.ACTIVE);
        m.put("createdAt", ServerValue.TIMESTAMP);

        currentId = id;
        SosPrefs.set(this, uid, id);

        ref.setValue(m).addOnFailureListener(e -> {
            if (id.equals(currentId)) {
                currentId = null;
                SosPrefs.clear(this);
                detachWatcher();
                showIdle();
                Toast.makeText(this, "Could not send the SOS. Call 999 now!", Toast.LENGTH_LONG).show();
            }
        });

        attachWatcher(id);              // local write সাথে সাথে UI বদলে দেয়
        startTrackingWithPermission();  // alert যাওয়ার পরে permission চাওয়া হয়
    }

    // ---------- permissions + service ----------
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, FINE) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, COARSE) == PackageManager.PERMISSION_GRANTED;
    }

    private void startTrackingWithPermission() {
        List<String> need = new ArrayList<>();
        if (!hasLocationPermission()) { need.add(FINE); need.add(COARSE); }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (need.isEmpty()) startTracking();
        else permLauncher.launch(need.toArray(new String[0]));
    }

    private void startTracking() {
        if (currentId == null || !hasLocationPermission()) return;

        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm != null && !LocationManagerCompat.isLocationEnabled(lm)) {
            Toast.makeText(this, "Location is switched off on your phone. Turn it on to share your position.",
                    Toast.LENGTH_LONG).show();
        }

        Intent i = new Intent(this, SosTrackingService.class)
                .setAction(SosTrackingService.ACTION_START)
                .putExtra(SosTrackingService.EXTRA_ALERT_ID, currentId);
        ContextCompat.startForegroundService(this, i); // started + foreground

        if (!bound) {
            bound = bindService(new Intent(this, SosTrackingService.class), conn, Context.BIND_AUTO_CREATE);
        }
    }

    private void unbindFromService() {
        if (bound) {
            try { unbindService(conn); } catch (IllegalArgumentException ignored) { }
            bound = false;
        }
        service = null;
    }

    // ---------- watch the alert (live status from admin) ----------
    private void attachWatcher(String id) {
        detachWatcher();
        alertRef = FirebaseDatabase.getInstance().getReference("sos_alerts").child(id);
        alertListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) {
                if (!s.exists()) { finishAlert(null); return; }
                SosAlertModel m = SosAlertModel.from(s);
                if (!m.isActive()) { finishAlert(m); return; }
                renderActive(m);
            }

            @Override public void onCancelled(@NonNull DatabaseError e) { finishAlert(null); }
        };
        alertRef.addValueEventListener(alertListener);
    }

    private void detachWatcher() {
        if (alertRef != null && alertListener != null) alertRef.removeEventListener(alertListener);
        alertRef = null;
        alertListener = null;
    }

    private void finishAlert(SosAlertModel m) {
        detachWatcher();
        SosPrefs.clear(this);
        currentId = null;
        unbindFromService();
        stopService(new Intent(this, SosTrackingService.class));
        if (m != null && SosAlertModel.RESOLVED.equals(m.getStatus())) {
            Toast.makeText(this, "Campus security marked your alert as resolved. Stay safe!",
                    Toast.LENGTH_LONG).show();
        } else if (m != null && SosAlertModel.CANCELLED.equals(m.getStatus())) {
            Toast.makeText(this, "Alert cancelled. Glad you're safe.", Toast.LENGTH_SHORT).show();
        }
        showIdle();
    }

    // ---------- UI ----------
    private void showIdle() {
        b.idleGroup.setVisibility(View.VISIBLE);
        b.activeGroup.setVisibility(View.GONE);
        styleChips();
    }

    private void renderActive(SosAlertModel m) {
        b.idleGroup.setVisibility(View.GONE);
        b.activeGroup.setVisibility(View.VISIBLE);

        boolean ack = SosAlertModel.ACK.equals(m.getStatus());
        int color = SosAlertModel.statusColor(m.getStatus());
        b.tvStatus.setText(ack ? "HELP IS ON THE WAY" : "SOS ACTIVE");
        b.tvStatus.setTextColor(color);
        b.tvStatus.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x33)));

        b.tvActiveTitle.setText(ack ? "Help is on the way" : "Alert sent");
        b.tvActiveSub.setText(ack
                ? (m.getHandledBy() == null ? "Campus security" : m.getHandledBy())
                + " is responding. Stay safe and keep your phone with you."
                : "Campus security can see your alert. Stay where you are if it is safe.");
        b.tvType.setText("Type: " + m.getType() + "  •  sent "
                + TimeUtil.ago(m.getCreatedDate()));

        if (service == null) {
            b.tvLive.setText(hasLocationPermission()
                    ? "Starting live location…"
                    : "Location is not being shared (permission denied).");
        }
    }

    private void showLive(Location loc, int count) {
        if (loc == null) {
            b.tvLive.setText("Waiting for a location fix…");
            return;
        }
        b.tvLive.setText("📍 Sharing live location  •  ±" + Math.round(loc.getAccuracy())
                + " m  •  " + count + " updates sent");
    }

    // ---------- type chips ----------
    private void buildChips() {
        for (String name : SosAlertModel.TYPES) {
            TextView chip = new TextView(this);
            chip.setText(name);
            chip.setTextSize(13);
            chip.setTypeface(ResourcesCompat.getFont(this, R.font.poppins_bold));
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                selectedType = name;
                styleChips();
            });
            chips.add(chip);
            b.chipRow.addView(chip);
        }
        styleChips();
    }

    private void styleChips() {
        for (TextView chip : chips) {
            boolean sel = chip.getText().toString().equals(selectedType);
            chip.setBackgroundResource(sel ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(sel ? Color.parseColor("#06201E") : Color.parseColor("#C3D2EA"));
        }
    }

    // ---------- cancel + 999 ----------
    private void confirmCancel() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cancel the alert?")
                .setMessage("Only cancel if you are safe. Campus security will be told it was cancelled.")
                .setNegativeButton("Keep it active", null)
                .setPositiveButton("I'm safe", (d, w) -> {
                    if (currentId == null) return;
                    FirebaseDatabase.getInstance().getReference("sos_alerts").child(currentId)
                            .child("status").setValue(SosAlertModel.CANCELLED)
                            .addOnFailureListener(e -> Toast.makeText(this,
                                    "Could not cancel. Check your internet.", Toast.LENGTH_LONG).show());
                })
                .show();
    }

    private void callEmergency() {
        // ACTION_DIAL: dialer খোলে, CALL_PHONE permission লাগে না
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:999")));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}