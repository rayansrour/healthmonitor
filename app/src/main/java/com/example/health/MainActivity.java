package com.example.health;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.OnApplyWindowInsetsListener;

import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private String userEmail;
    private static final String TAG = "MainActivity";
    private SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up edge-to-edge layout
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_main);

        // Initialize Firebase authentication
        sharedPref = getSharedPreferences(SignInActivity.PREFS_NAME, MODE_PRIVATE);
        auth = FirebaseAuth.getInstance();

        // Get user email from intent or Firebase Auth
        userEmail = getIntent().getStringExtra("USER_EMAIL");
        if (userEmail == null || userEmail.isEmpty()) {
            if (auth.getCurrentUser() != null) {
                userEmail = auth.getCurrentUser().getEmail();
            }
        }

        if (userEmail == null || userEmail.isEmpty()) {
            Log.e(TAG, "No user email found");
            redirectToSignIn();
            return;
        }

        // Set up the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.app_name));
        }

        // Adjust the padding to account for system bars (e.g., status bar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                // Get system bar insets (top, left, right, bottom)
                v.setPadding(insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                        insets.getInsets(WindowInsetsCompat.Type.systemBars()).top,
                        insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                        insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
                return insets;
            }
        });

        // Check if the user is authenticated
        if (auth.getCurrentUser() == null) {
            Log.w(TAG, "User not authenticated");
            redirectToSignIn();
        }

        // Set button listeners
        findViewById(R.id.btnHeartRate).setOnClickListener(v -> navigateToHeartRate());
        findViewById(R.id.btnLocation).setOnClickListener(v -> navigateToLocation());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sign_out) {
            signOut();
            return true;
        } else if (id == R.id.action_profile) {
            navigateToProfile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void signOut() {
        try {
            auth.signOut();
            sharedPref.edit().clear().apply();
            redirectToSignIn();
        } catch (Exception e) {
            Log.e(TAG, "Sign out failed", e);
            Toast.makeText(this, "Sign out failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToProfile() {
        try {
            Intent intent = new Intent(this, PatientInfoActivity.class);
            intent.putExtra("USER_EMAIL", userEmail);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Navigation to profile failed", e);
            Toast.makeText(this, "Cannot open profile", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToHeartRate() {
        try {
            // Retrieve pairing information from SharedPreferences
            String pairingCode = sharedPref.getString(SignInActivity.PREF_PAIRING_CODE, null);
            String deviceId = sharedPref.getString(SignInActivity.PREF_DEVICE_ID, null);
            String patientDocId = sharedPref.getString(SignInActivity.PREF_PATIENT_ID, null);

            // Debug log to check retrieved values
            Log.d(TAG, "Pairing Code: " + pairingCode);
            Log.d(TAG, "Device ID: " + deviceId);
            Log.d(TAG, "Patient Doc ID: " + patientDocId);

            // Check if pairing information is valid
            if (pairingCode == null || deviceId == null || patientDocId == null) {
                Toast.makeText(this, "Device not paired. Please pair your watch first.", Toast.LENGTH_LONG).show();
                return;
            }

            // Navigate to HeartRateActivity
            Intent intent = new Intent(this, HeartRateActivity.class);
            intent.putExtra("USER_EMAIL", userEmail);
            intent.putExtra("PAIRING_CODE", pairingCode);
            intent.putExtra("WEAR_DEVICE_ID", deviceId);
            intent.putExtra("PATIENT_DOC_ID", patientDocId);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Navigation to heart rate failed", e);
            Toast.makeText(this, "Cannot open heart rate", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToLocation() {
        try {
            Intent intent = new Intent(this, LocationActivity.class);
            intent.putExtra("USER_EMAIL", userEmail);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Navigation to location failed", e);
            Toast.makeText(this, "Cannot open location", Toast.LENGTH_SHORT).show();
        }
    }

    private void redirectToSignIn() {
        try {
            Intent intent = new Intent(this, SignInActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Redirection to sign in failed", e);
        }
    }
}

