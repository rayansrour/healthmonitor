package com.example.health;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.health.databinding.ActivitySignInBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Timer;
import java.util.TimerTask;

public class SignInActivity extends AppCompatActivity {

    private ActivitySignInBinding binding;
    private FirebaseAuth auth;
    private SharedPreferences sharedPref;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private Timer emailVerificationTimer;

    public static final String PREFS_NAME = "AuthPrefs";
    public static final String PREF_STAY_CONNECTED = "stay_connected";
    public static final String PREF_PAIRING_COMPLETE = "pairing_complete";
    public static final String PREF_USER_EMAIL = "user_email";
    public static final String PREF_PAIRING_CODE = "pairing_code";
    public static final String PREF_DEVICE_ID = "wear_device_id";
    public static final String PREF_PATIENT_ID = "patient_doc_id";
    private static final String TAG = "SignInActivity";
    private static final long VERIFICATION_CHECK_INTERVAL = 5000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (sharedPref.getBoolean(PREF_STAY_CONNECTED, false) && auth.getCurrentUser() != null) {
            String email = auth.getCurrentUser().getEmail();
            if (email != null) {
                checkEmailVerificationWithReload(email);
                return;
            }
        }

        setupViews();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (emailVerificationTimer != null) {
            emailVerificationTimer.cancel();
        }
    }

    private void setupViews() {
        binding.rememberMeCheckBox.setChecked(sharedPref.getBoolean(PREF_STAY_CONNECTED, false));

        binding.signInButton.setOnClickListener(v -> {
            String email = binding.emailEditText.getText().toString().trim().toLowerCase();
            String password = binding.passwordEditText.getText().toString().trim();

            if (validateInput(email, password)) {
                signInUser(email, password);
            }
        });

        binding.signUpTextView.setOnClickListener(v -> startActivity(new Intent(this, SignUpActivity.class)));

        binding.forgotPasswordTextView.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
    }

    private boolean validateInput(String email, String password) {
        boolean isValid = true;
        binding.emailEditText.setError(null);
        binding.passwordEditText.setError(null);

        if (TextUtils.isEmpty(email)) {
            binding.emailEditText.setError("Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailEditText.setError("Valid email required");
            isValid = false;
        }

        if (TextUtils.isEmpty(password)) {
            binding.passwordEditText.setError("Password is required");
            isValid = false;
        } else if (password.length() < 8) {
            binding.passwordEditText.setError("Password must be at least 8 characters");
            isValid = false;
        }

        return isValid;
    }

    private void signInUser(String email, String password) {
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                checkEmailVerificationWithReload(email);
            } else {
                checkEmailToUidMapping(email);
            }
        });
    }

    private void checkEmailVerificationWithReload(String email) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        currentUser.reload().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (currentUser.isEmailVerified()) {
                    updateFirestoreVerificationStatus(email);
                } else {
                    showVerificationReminder(email);
                    startEmailVerificationPolling(email);
                }
            } else {
                Log.e(TAG, "Error reloading user", task.getException());
                showVerificationReminder(email);
            }
        });
    }

    private void updateFirestoreVerificationStatus(String email) {
        db.collection("users").document(email)
                .update("isVerified", true)
                .addOnSuccessListener(unused -> completeSignInProcess(email))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating verification status", e);
                    completeSignInProcess(email);
                });
    }

    private void startEmailVerificationPolling(String email) {
        if (emailVerificationTimer != null) emailVerificationTimer.cancel();
        emailVerificationTimer = new Timer();
        emailVerificationTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    user.reload().addOnCompleteListener(task -> {
                        if (task.isSuccessful() && user.isEmailVerified()) {
                            runOnUiThread(() -> {
                                updateFirestoreVerificationStatus(email);
                                emailVerificationTimer.cancel();
                            });
                        }
                    });
                }
            }
        }, VERIFICATION_CHECK_INTERVAL, VERIFICATION_CHECK_INTERVAL);
    }

    private void showVerificationReminder(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Email Verification Required")
                .setMessage("Please verify your email address (" + email + ") to continue. We've sent you a verification email. Check your spam folder if you don't see it.")
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    auth.signOut();
                })
                .setNeutralButton("Resend Verification", (dialog, which) -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        user.sendEmailVerification().addOnCompleteListener(task -> {
                            String message = task.isSuccessful() ? "Verification email sent!" : "Failed to send verification email";
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                        });
                    }
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    private void checkEmailToUidMapping(String email) {
        db.collection("userEmails").document(email)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String uid = document.getString("uid");
                        if (uid != null) {
                            binding.passwordEditText.setError("Wrong password");
                        } else {
                            binding.emailEditText.setError("Account not properly configured");
                        }
                    } else {
                        binding.emailEditText.setError("Incorrect Account or Password");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking userEmails collection", e);
                    binding.emailEditText.setError("Error checking account");
                });
    }

    private void completeSignInProcess(String email) {
        sharedPref.edit()
                .putBoolean(PREF_STAY_CONNECTED, binding.rememberMeCheckBox.isChecked())
                .putString(PREF_USER_EMAIL, email)
                .apply();
        checkUserSetupStatus(email);
    }

    private void checkUserSetupStatus(String email) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            redirectToSignIn();
            return;
        }
        String userId = user.getUid();

        db.collection("patients")
                .whereEqualTo("userId", userId)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        navigateToPatientInfoActivity(email);
                    } else {
                        String patientDocId = querySnapshot.getDocuments().get(0).getId();
                        checkDevicePairingStatus(email, patientDocId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking patient records", e);
                    navigateToPatientInfoActivity(email);
                });
    }

    private void checkDevicePairingStatus(String email, String patientDocId) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            redirectToSignIn();
            return;
        }
        String userId = user.getUid();

        db.collection("wearDevices")
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "active")
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        if (patientDocId != null) {
                            navigateToPairingActivity(email, patientDocId);
                        } else {
                            navigateToPatientInfoActivity(email);
                        }
                    } else {
                        sharedPref.edit().putBoolean(PREF_PAIRING_COMPLETE, true).apply();
                        navigateToMainActivity(email);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking wear device records", e);
                    if (patientDocId != null) {
                        navigateToPairingActivity(email, patientDocId);
                    } else {
                        navigateToPatientInfoActivity(email);
                    }
                });
    }

    private void navigateToMainActivity(String email) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(PREF_USER_EMAIL, email);
        startActivity(intent);
        finish();
    }

    private void navigateToPairingActivity(String email, String patientDocId) {
        sharedPref.edit().putString(PREF_PATIENT_ID, patientDocId).apply();
        Intent intent = new Intent(this, PairingActivity.class);
        intent.putExtra(PREF_USER_EMAIL, email);
        startActivity(intent);
        finish();
    }

    private void navigateToPatientInfoActivity(String email) {
        Intent intent = new Intent(this, PatientInfoActivity.class);
        intent.putExtra(PREF_USER_EMAIL, email);
        startActivity(intent);
        finish();
    }

    private void redirectToSignIn() {
        Intent intent = new Intent(this, SignInActivity.class);
        startActivity(intent);
        finish();
    }
}
