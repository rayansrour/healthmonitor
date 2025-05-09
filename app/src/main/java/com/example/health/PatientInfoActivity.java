package com.example.health;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.health.databinding.ActivityPatientInfoBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

public class PatientInfoActivity extends AppCompatActivity {

    private ActivityPatientInfoBinding binding;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseAuth auth = FirebaseAuth.getInstance();
    private String userEmail;
    private SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPatientInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userEmail = getIntent().getStringExtra("USER_EMAIL");
        if (userEmail == null) {
            finish();
            return;
        }

        sharedPref = getSharedPreferences(SignInActivity.PREFS_NAME, MODE_PRIVATE);
        setupDateOfBirthInput();
        setupViews();
    }

    private void setupDateOfBirthInput() {
        binding.dobEditText.addTextChangedListener(new TextWatcher() {
            private String current = "";
            private boolean isFormatting = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // No action needed
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isFormatting) {
                    isFormatting = true;
                    String userInput = s.toString().replaceAll("\\D", "");
                    StringBuilder formatted = new StringBuilder();

                    if (userInput.length() >= 2) {
                        formatted.append(userInput.substring(0, 2)).append("/");
                        if (userInput.length() >= 4) {
                            formatted.append(userInput.substring(2, 4)).append("/");
                            if (userInput.length() > 4) {
                                formatted.append(userInput.substring(4, Math.min(8, userInput.length())));
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
                    isFormatting = false;
                }
            }
        });
    }

    private void setupViews() {
        binding.saveButton.setOnClickListener(v -> savePatientInfo());

        binding.backButton.setOnClickListener(v -> {
            Log.d("PatientInfoActivity", "Back button clicked - signing out and navigating to SignInActivity");
            navigateToSignIn();
        });
    }

    private void navigateToSignIn() {
        try {
            // Sign out from Firebase
            auth.signOut();

            // Clear the stay connected preference
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean(SignInActivity.PREF_STAY_CONNECTED, false);
            editor.apply();

            Intent intent = new Intent(this, SignInActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finishAffinity(); // Clear all activities in the stack
        } catch (Exception e) {
            Log.e("PatientInfoActivity", "Error navigating to SignInActivity", e);
            Toast.makeText(this, "Error signing out. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("SetTextI18n")
    private void savePatientInfo() {
        String firstName = binding.firstNameEditText.getText().toString().trim();
        String lastName = binding.lastNameEditText.getText().toString().trim();
        String dob = binding.dobEditText.getText().toString().trim();
        String weightStr = binding.weightEditText.getText().toString().trim();
        String heightStr = binding.heightEditText.getText().toString().trim();

        if (!validateInput(firstName, lastName, dob, weightStr, heightStr)) {
            return;
        }

        try {
            binding.saveButton.setEnabled(false);
            binding.saveButton.setText("Saving...");

            double weight = Double.parseDouble(weightStr);
            double height = Double.parseDouble(heightStr);
            Date parsedDob = new SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(dob);
            if (parsedDob == null) {
                parsedDob = new Date();
            }

            String userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;

            if (userId == null) {
                Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
                binding.saveButton.setEnabled(true);
                binding.saveButton.setText("Save");
                return;
            }

            // Create patient data
            java.util.Map<String, Object> patientData = new java.util.HashMap<>();
            patientData.put("firstName", firstName);
            patientData.put("lastName", lastName);
            patientData.put("dob", parsedDob);
            patientData.put("weight", weight);
            patientData.put("height", height);
            patientData.put("userId", userId);
            patientData.put("email", userEmail);
            patientData.put("createdAt", FieldValue.serverTimestamp());

            db.collection("patients").add(patientData)
                    .addOnSuccessListener(docRef -> {
                        Intent intent = new Intent(this, PairingActivity.class);
                        intent.putExtra("PATIENT_DOC_ID", docRef.getId());
                        intent.putExtra("USER_EMAIL", userEmail);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        binding.saveButton.setEnabled(true);
                        binding.saveButton.setText("Save");
                        Toast.makeText(this, "Failed to save patient data", Toast.LENGTH_SHORT).show();
                    });
        } catch (Exception e) {
            binding.saveButton.setEnabled(true);
            binding.saveButton.setText("Save");
            Toast.makeText(this, "Invalid data format", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean validateInput(String firstName, String lastName, String dob, String weightStr, String heightStr) {
        boolean isValid = true;

        if (firstName.isEmpty()) {
            binding.firstNameEditText.setError("Required");
            isValid = false;
        }

        if (lastName.isEmpty()) {
            binding.lastNameEditText.setError("Required");
            isValid = false;
        }

        if (dob.length() < 10 || !isValidDate(dob)) {
            binding.dobEditText.setError("Use DD/MM/YYYY");
            isValid = false;
        }

        Pattern numberPattern = Pattern.compile("^\\d*\\.?\\d+$");

        if (weightStr.isEmpty() || !numberPattern.matcher(weightStr).matches()) {
            binding.weightEditText.setError("Invalid number");
            isValid = false;
        }

        if (heightStr.isEmpty() || !numberPattern.matcher(heightStr).matches()) {
            binding.heightEditText.setError("Invalid number");
            isValid = false;
        }

        return isValid;
    }

    private boolean isValidDate(String dateString) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            sdf.setLenient(false);
            return sdf.parse(dateString) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
