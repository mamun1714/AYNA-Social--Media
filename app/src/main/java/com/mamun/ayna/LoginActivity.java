    
package com.mamun.ayna;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;
import android.util.Patterns;
import android.text.InputType;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends Activity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private EditText nameField, emailField, passwordField;
    private Button loginButton, registerButton, forgotButton;
    private Button saveProfileButton;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        createScreen();

        if (auth.getCurrentUser() != null) {
            checkUserProfile();
        }
    }

    private void createScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(32, 32, 32, 32);
        page.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("আয়না");
        title.setTextSize(34);
        title.setTextColor(Color.rgb(23, 105, 170));
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("AYNA Social Media");
        subtitle.setTextSize(18);
        subtitle.setGravity(Gravity.CENTER);

        nameField = new EditText(this);
        nameField.setHint("Your Full Name");

        emailField = new EditText(this);
        emailField.setHint("Email Address");
        emailField.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        passwordField = new EditText(this);
        passwordField.setHint("Password");
        passwordField.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        loginButton = new Button(this);
        loginButton.setText("Login");

        registerButton = new Button(this);
        registerButton.setText("Create New Account");

        forgotButton = new Button(this);
        forgotButton.setText("Forgot Password?");

        saveProfileButton = new Button(this);
        saveProfileButton.setText("Complete Profile");
        saveProfileButton.setVisibility(View.GONE);

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);

        loginButton.setOnClickListener(v -> loginUser());
        registerButton.setOnClickListener(v -> registerUser());
        forgotButton.setOnClickListener(v -> resetPassword());
        saveProfileButton.setOnClickListener(v -> saveProfile());

        page.addView(title);
        page.addView(subtitle);
        page.addView(statusText);
        page.addView(nameField);
        page.addView(emailField);
        page.addView(passwordField);
        page.addView(loginButton);
        page.addView(registerButton);
        page.addView(forgotButton);
        page.addView(saveProfileButton);

        setContentView(page);
    }

    private boolean validEmail(String email) {
        return !email.isEmpty() &&
            Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void loginUser() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();

        if (!validEmail(email)) {
            emailField.setError("Enter a valid email address");
            return;
        }

        if (password.isEmpty()) {
            passwordField.setError("Password required");
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    checkUserProfile();
                } else {
                    showMessage("Login failed: " +
                        task.getException().getMessage());
                }
            });
    }

    private void registerUser() {
        String name = nameField.getText().toString().trim();
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();

        if (name.isEmpty()) {
            nameField.setError("Full name required");
            return;
        }

        if (!validEmail(email)) {
            emailField.setError("Enter a valid email address");
            return;
        }

        if (password.length() < 6) {
            passwordField.setError(
                "Password must be at least 6 characters"
            );
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    saveProfile();
                } else {
                    showMessage("Registration failed: " +
                        task.getException().getMessage());
                }
            });
    }

    private void resetPassword() {
        String email = emailField.getText().toString().trim();

        if (!validEmail(email)) {
            emailField.setError(
                "Enter your registered email address"
            );
            return;
        }

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    showMessage(
                        "Password reset link sent to your email"
                    );
                } else {
                    showMessage("Reset failed: " +
                        task.getException().getMessage());
                }
            });
    }

    private void checkUserProfile() {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        db.collection("users")
            .document(currentUser.getUid())
            .get()
            .addOnSuccessListener(document -> {
                if (document.exists()) {
                    openHome();
                } else {
                    showProfileCompletion();
                }
            })
            .addOnFailureListener(e -> {
                showMessage("Profile check failed: " +
                    e.getMessage());
            });
    }

    private void showProfileCompletion() {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) return;

        statusText.setText(
            "Complete your profile to continue"
        );

        emailField.setText(currentUser.getEmail());
        emailField.setEnabled(false);
        passwordField.setVisibility(View.GONE);

        loginButton.setVisibility(View.GONE);
        registerButton.setVisibility(View.GONE);
        forgotButton.setVisibility(View.GONE);
        saveProfileButton.setVisibility(View.VISIBLE);
    }

    private void saveProfile() {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            showMessage("Please login first");
            return;
        }

        String name = nameField.getText().toString().trim();

        if (name.isEmpty()) {
            nameField.setError("Full name required");
            return;
        }

        Map<String, Object> user = new HashMap<>();
        user.put("uid", currentUser.getUid());
        user.put("name", name);
        user.put("email", currentUser.getEmail());
        user.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users")
            .document(currentUser.getUid())
            .set(user)
            .addOnSuccessListener(unused -> {
                showMessage("Profile saved successfully");
                openHome();
            })
            .addOnFailureListener(e -> {
                showMessage("Profile save failed: " +
                    e.getMessage());
            });
    }

    private void showMessage(String message) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show();
    }

    private void openHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
