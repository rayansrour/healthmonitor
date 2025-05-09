package com.example.health;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;

import androidx.appcompat.app.AppCompatActivity;

import com.example.health.databinding.ActivityForgotPasswordBinding;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding binding;
    private FirebaseAuth auth = FirebaseAuth.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.emailEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.emailEditText.setError(null);
            }
        });

        binding.resetPasswordButton.setOnClickListener(v -> {
            String email = binding.emailEditText.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                binding.emailEditText.setError("Email is required");
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.emailEditText.setError("Valid email required");
                return;
            }

            sendPasswordResetEmail(email);
        });
    }

    private void sendPasswordResetEmail(String email) {
        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        binding.emailEditText.setError("Reset email sent to " + email);
                    } else {
                        binding.emailEditText.setError("Failed to send reset email");
                    }
                });
    }
}
