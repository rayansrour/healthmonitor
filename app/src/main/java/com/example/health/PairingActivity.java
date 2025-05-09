package com.example.health;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.health.databinding.ActivityPairingBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.HashMap;
import java.util.Map;

public class PairingActivity extends AppCompatActivity {

    private ActivityPairingBinding binding;
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String TAG = "PairingActivity";
    private SharedPreferences sharedPref;

    private final ActivityResultLauncher<ScanOptions> qrCodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    processScannedData(result.getContents());
                } else {
                    Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchQRScanner();
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPairingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        sharedPref = getSharedPreferences(SignInActivity.PREFS_NAME, MODE_PRIVATE);

        if (auth.getCurrentUser() == null) {
            redirectToSignIn();
            return;
        }

        setupViews();
    }

    private void setupViews() {
        binding.pairDeviceButton.setOnClickListener(v -> checkCameraPermissionAndLaunchScanner());
        binding.signOutButton.setOnClickListener(v -> signOut());
    }

    private void checkCameraPermissionAndLaunchScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchQRScanner();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Toast.makeText(this, "Camera needed to scan QR codes", Toast.LENGTH_LONG).show();
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchQRScanner() {
        try {
            ScanOptions options = new ScanOptions();
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            options.setPrompt("Scan HealthOS QR Code");
            options.setCameraId(0);
            options.setBeepEnabled(false);
            options.setTimeout(30000);
            qrCodeLauncher.launch(options);
        } catch (Exception e) {
            Toast.makeText(this, "Scanner error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Scanner failed", e);
        }
    }

    private void processScannedData(String scannedData) {
        if (!scannedData.startsWith("HEALTHOS-")) {
            Toast.makeText(this, "Invalid QR format", Toast.LENGTH_SHORT).show();
            return;
        }

        if (auth.getCurrentUser() == null) {
            redirectToSignIn();
            return;
        }

        String patientDocId = getIntent().getStringExtra("PATIENT_DOC_ID");
        if (patientDocId == null) {
            Toast.makeText(this, "Patient record missing", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.pairDeviceButton.setEnabled(false);
        Toast.makeText(this, "Pairing device...", Toast.LENGTH_SHORT).show();

        db.collection("patients").document(patientDocId)
                .update("pairingCode", scannedData)
                .addOnSuccessListener(aVoid ->
                        completePairingProcess(scannedData, auth.getCurrentUser().getEmail(), auth.getCurrentUser().getUid(), patientDocId))
                .addOnFailureListener(e -> handlePairingError("Failed to update patient: " + e.getMessage(), e));
    }

    private void completePairingProcess(String code, String email, String uid, String patientDocId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "completed");
        updates.put("userEmail", email);
        updates.put("userId", uid);
        updates.put("pairedAt", FieldValue.serverTimestamp());
        updates.put("setupComplete", true);

        db.collection("pairingCodes").document(code)
                .update(updates)
                .addOnSuccessListener(aVoid -> registerWearDevice(code, email, uid, patientDocId))
                .addOnFailureListener(e -> handlePairingError("Pairing update failed: " + e.getMessage(), e));
    }

    private void registerWearDevice(String code, String email, String uid, String patientDocId) {
        String deviceId = Build.MANUFACTURER + "_" + Build.MODEL;
        Map<String, Object> deviceData = new HashMap<>();
        deviceData.put("code", code);
        deviceData.put("userId", uid);
        deviceData.put("userEmail", email);
        deviceData.put("status", "active");
        deviceData.put("deviceId", deviceId);
        deviceData.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
        deviceData.put("osVersion", Build.VERSION.RELEASE);
        deviceData.put("pairedAt", FieldValue.serverTimestamp());
        deviceData.put("lastActive", FieldValue.serverTimestamp());

        db.collection("wearDevices").document(code)
                .set(deviceData)
                .addOnSuccessListener(aVoid -> {
                    savePairingStatus(code, deviceId, patientDocId);
                    navigateToMainActivity(email, code);
                })
                .addOnFailureListener(e -> handlePairingError("Device registration failed: " + e.getMessage(), e));
    }

    private void savePairingStatus(String code, String deviceId, String patientDocId) {
        SharedPreferences.Editor editor = getSharedPreferences(SignInActivity.PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean(SignInActivity.PREF_PAIRING_COMPLETE, true);
        editor.putString(SignInActivity.PREF_PAIRING_CODE, code);
        editor.putString(SignInActivity.PREF_DEVICE_ID, deviceId);
        editor.putString(SignInActivity.PREF_PATIENT_ID, patientDocId);
        editor.apply();
    }

    private void handlePairingError(String message, Exception e) {
        Log.e(TAG, message, e);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        binding.pairDeviceButton.setEnabled(true);
    }

    private void navigateToMainActivity(String email, String code) {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("USER_EMAIL", email);
            intent.putExtra("PAIRING_CODE", code);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Navigation to MainActivity failed", e);
            Toast.makeText(this, "Failed to open dashboard", Toast.LENGTH_SHORT).show();
            redirectToSignIn();
        }
    }

    private void redirectToSignIn() {
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void signOut() {
        getSharedPreferences(SignInActivity.PREFS_NAME, MODE_PRIVATE).edit().clear().apply();
        auth.signOut();
        redirectToSignIn();
    }
}
