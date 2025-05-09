package com.example.health;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.health.databinding.ActivitySignUpBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {

    private ActivitySignUpBinding binding;
    private FirebaseAuth auth;
    private SharedPreferences sharedPref;
    private FirebaseFirestore db;

    public static final String PREFS_NAME = "HealthAppPrefs";
    public static final String PREF_STAY_CONNECTED = "stay_connected";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        setupDateOfBirthInput();
        setupViews();
    }

    private void setupDateOfBirthInput() {
        binding.dobEditText.addTextChangedListener(new TextWatcher() {
            private String current = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equals(current)) {
                    String userInput = s.toString().replaceAll("\\D", "");
                    if (userInput.length() > 8) userInput = userInput.substring(0, 8);

                    StringBuilder formatted = new StringBuilder();
                    if (userInput.length() >= 2) {
                        formatted.append(userInput.substring(0, 2)).append("/");
                        if (userInput.length() >= 4) {
                            formatted.append(userInput.substring(2, 4)).append("/");
                            if (userInput.length() > 4) {
                                formatted.append(userInput.substring(4));
                            }
                        } else {
                            formatted.append(userInput.substring(2));
                        }
                    } else {
                        formatted.append(userInput);
                    }

                    current = formatted.toString();
                    binding.dobEditText.removeTextChangedListener(this);
                    binding.dobEditText.setText(current);
                    binding.dobEditText.setSelection(current.length());
                    binding.dobEditText.addTextChangedListener(this);
                }
            }
        });
    }

    private void setupViews() {
        binding.signUpButton.setOnClickListener(v -> {
            String email = binding.emailEditText.getText().toString().trim();
            String password = binding.passwordEditText.getText().toString().trim();
            String confirmPassword = binding.confirmPasswordEditText.getText().toString().trim();
            String firstName = binding.firstNameEditText.getText().toString().trim();
            String lastName = binding.lastNameEditText.getText().toString().trim();
            String dob = binding.dobEditText.getText().toString().trim();
            String phone = binding.phoneEditText.getText().toString().trim();

            if (validateInput(email, password, confirmPassword, firstName, lastName, dob, phone)) {
                createUser(email, password, firstName, lastName, dob, phone);
            }
        });

        binding.signInTextView.setOnClickListener(v -> {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
        });
    }

    private boolean validateInput(String email, String password, String confirmPassword,
                                  String firstName, String lastName, String dob, String phone) {
        boolean isValid = true;

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

        if (TextUtils.isEmpty(confirmPassword)) {
            binding.confirmPasswordEditText.setError("Please confirm your password");
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            showPasswordMismatchDialog();
            isValid = false;
        }

        if (TextUtils.isEmpty(firstName)) {
            binding.firstNameEditText.setError("First name is required");
            isValid = false;
        }

        if (TextUtils.isEmpty(lastName)) {
            binding.lastNameEditText.setError("Last name is required");
            isValid = false;
        }

        if (TextUtils.isEmpty(dob)) {
            binding.dobEditText.setError("Date of birth is required");
            isValid = false;
        } else if (dob.length() < 10) {
            binding.dobEditText.setError("Use MM/DD/YYYY format");
            isValid = false;
        } else if (!isValidDate(dob)) {
            binding.dobEditText.setError("Invalid date");
            isValid = false;
        }

        if (TextUtils.isEmpty(phone)) {
            binding.phoneEditText.setError("Phone is required");
            isValid = false;
        }

        return isValid;
    }

    private boolean isValidDate(String dateString) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
            sdf.setLenient(false);
            return sdf.parse(dateString) != null;
        } catch (ParseException e) {
            return false;
        }
    }

    private void showPasswordMismatchDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Password Mismatch")
                .setMessage("The passwords you entered don't match. Please try again.")
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    binding.passwordEditText.getText().clear();
                    binding.confirmPasswordEditText.getText().clear();
                    binding.passwordEditText.requestFocus();
                })
                .setCancelable(false)
                .show();
    }

    private void createUser(String email, String password, String firstName, String lastName,
                            String dob, String phone) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        sharedPref.edit().putBoolean(PREF_STAY_CONNECTED, binding.rememberMeCheckBox.isChecked()).apply();

                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            user.sendEmailVerification()
                                    .addOnCompleteListener(verificationTask -> {
                                        if (verificationTask.isSuccessful()) {
                                            saveUserToFirestore(email, firstName, lastName, dob, phone, false);
                                            showVerificationDialog(email);
                                        } else {
                                            binding.emailEditText.setError("Failed to send verification email");
                                            user.delete();
                                        }
                                    });
                        }
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Sign up failed";
                        if (errorMessage != null && errorMessage.contains("email address is already in use")) {
                            binding.emailEditText.setError("Email already in use");
                        } else {
                            binding.emailEditText.setError("Sign up failed");
                        }
                    }
                });
    }

    private void showVerificationDialog(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Verify Your Email")
                .setMessage("A verification email has been sent to " + email + ". Please verify your email to continue using the app. Check your spam folder if you don't see the email.")
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    startActivity(new Intent(this, SignInActivity.class));
                    finish();
                })
                .setNeutralButton("Resend Email", (dialog, which) -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        user.sendEmailVerification()
                                .addOnCompleteListener(resendTask -> {
                                    if (resendTask.isSuccessful()) {
                                        Toast.makeText(this, "Verification email resent!", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(this, "Failed to resend verification email", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    private void saveUserToFirestore(String email, String firstName, String lastName,
                                     String dob, String phone, boolean isVerified) {
        FirebaseUser user = auth.getCurrentUser();

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("email", email);
        userMap.put("firstName", firstName);
        userMap.put("lastName", lastName);
        userMap.put("dob", dob);
        userMap.put("phone", phone);
        userMap.put("uid", user != null ? user.getUid() : null);
        userMap.put("isVerified", isVerified);
        userMap.put("createdAt", System.currentTimeMillis());

        db.collection("users")
                .document(email)
                .set(userMap)
                .addOnFailureListener(e -> {
                    binding.emailEditText.setError("Failed to create account: " + e.getMessage());
                    if (user != null) user.delete();
                });
    }
}
