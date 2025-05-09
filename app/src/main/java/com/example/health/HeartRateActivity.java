package com.example.health;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class HeartRateActivity extends AppCompatActivity {

    private TextView tvHeartRate;
    private Button btnMeasure;
    private boolean isMeasuring = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String wearDeviceId;
    private String pairingCode;
    private String patientDocId;
    private String userEmail;
    private SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate);

        sharedPref = getSharedPreferences(SignInActivity.PREFS_NAME, MODE_PRIVATE);

        // Retrieve intent extras or shared preferences if null
        userEmail = getIntent().getStringExtra("USER_EMAIL");
        if (userEmail == null) {
            userEmail = sharedPref.getString(SignInActivity.PREF_USER_EMAIL, null);
        }

        pairingCode = getIntent().getStringExtra("PAIRING_CODE");
        if (pairingCode == null) {
            pairingCode = sharedPref.getString(SignInActivity.PREF_PAIRING_CODE, null);
        }

        wearDeviceId = getIntent().getStringExtra("WEAR_DEVICE_ID");
        if (wearDeviceId == null) {
            wearDeviceId = sharedPref.getString(SignInActivity.PREF_DEVICE_ID, null);
        }

        patientDocId = getIntent().getStringExtra("PATIENT_DOC_ID");
        if (patientDocId == null) {
            patientDocId = sharedPref.getString(SignInActivity.PREF_PATIENT_ID, null);
        }

        // Check if essential data is available
        if (wearDeviceId == null || pairingCode == null || patientDocId == null) {
            Toast.makeText(this, "Device not paired or missing data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        initializeViews();
        setupButtonListeners();
        setupHeartRateListener();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.heart_rate);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initializeViews() {
        tvHeartRate = findViewById(R.id.tvHeartRateValue);
        btnMeasure = findViewById(R.id.btnMeasure);
    }

    private void setupButtonListeners() {
        btnMeasure.setOnClickListener(v -> {
            if (isMeasuring) {
                stopMeasurement();
            } else {
                startMeasurement();
            }
        });

        findViewById(R.id.btnHistory).setOnClickListener(v -> navigateToHistory());
    }

    private void startMeasurement() {
        if (wearDeviceId == null) return;

        isMeasuring = true;
        btnMeasure.setText(R.string.stop_measurement);

        Map<String, Object> updates = new HashMap<>();
        updates.put("command", "start_measurement");
        updates.put("status", "measuring");
        updates.put("lastUpdated", FieldValue.serverTimestamp());

        db.collection("wearDevices").document(wearDeviceId)
                .update(updates)
                .addOnFailureListener(e -> {
                    Log.e("HeartRateActivity", "Start measurement failed", e);
                    stopMeasurement();
                    Toast.makeText(this, "Failed to communicate with device", Toast.LENGTH_SHORT).show();
                });
    }

    private void stopMeasurement() {
        isMeasuring = false;
        btnMeasure.setText(R.string.measure);

        if (wearDeviceId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("command", "stop_measurement");
        updates.put("status", "idle");
        updates.put("lastUpdated", FieldValue.serverTimestamp());

        db.collection("wearDevices").document(wearDeviceId)
                .update(updates)
                .addOnFailureListener(e -> Log.e("HeartRateActivity", "Stop measurement failed", e));
    }

    private void setupHeartRateListener() {
        if (wearDeviceId == null) return;

        db.collection("wearDevices").document(wearDeviceId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e("HeartRateActivity", "Listener error", e);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        handleHeartRateUpdate(snapshot);
                        handleMeasurementStatus(snapshot);
                    }
                });
    }

    private void handleHeartRateUpdate(DocumentSnapshot doc) {
        Long hr = doc.getLong("heartRate");
        if (hr != null) {
            int heartRate = hr.intValue();
            updateHeartRateDisplay(heartRate);
            saveHeartRateMeasurement(heartRate);
        }
    }

    private void handleMeasurementStatus(DocumentSnapshot doc) {
        String status = doc.getString("status");
        if ("measuring".equals(status)) {
            if (!isMeasuring) {
                isMeasuring = true;
                btnMeasure.setText(R.string.stop_measurement);
            }
        } else {
            if (isMeasuring) {
                isMeasuring = false;
                btnMeasure.setText(R.string.measure);
            }
        }
    }

    private void saveHeartRateMeasurement(int heartRate) {
        if (patientDocId == null || userEmail == null) return;

        Map<String, Object> measurementData = new HashMap<>();
        measurementData.put("patientId", patientDocId);
        measurementData.put("userEmail", userEmail);
        measurementData.put("heartRate", heartRate);
        measurementData.put("timestamp", Timestamp.now());
        measurementData.put("deviceId", wearDeviceId);
        measurementData.put("source", "wear_os_device");

        db.collection("heartRateMeasurements")
                .add(measurementData)
                .addOnFailureListener(e -> Log.e("HeartRateActivity", "Save failed", e));
    }

    private void updateHeartRateDisplay(int heartRate) {
        tvHeartRate.setText(String.valueOf(heartRate));
        tvHeartRate.setTextColor(getHeartRateColor(heartRate));
    }

    private int getHeartRateColor(int heartRate) {
        if (heartRate < 60) {
            return ContextCompat.getColor(this, R.color.heart_rate_low);
        } else if (heartRate > 100) {
            return ContextCompat.getColor(this, R.color.heart_rate_high);
        } else {
            return ContextCompat.getColor(this, R.color.heart_rate_normal);
        }
    }

    private void navigateToHistory() {
        try {
            Intent intent = new Intent(this, HeartRateHistoryActivity.class);
            intent.putExtra("PATIENT_DOC_ID", patientDocId);
            intent.putExtra("USER_EMAIL", userEmail);
            intent.putExtra("PAIRING_CODE", pairingCode);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot show history", Toast.LENGTH_SHORT).show();
            Log.e("HeartRateActivity", "History navigation failed", e);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        stopMeasurement();
    }
}
