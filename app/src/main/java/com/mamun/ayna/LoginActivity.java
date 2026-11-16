
package com.mamun.ayna;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends Activity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private EditText nameField, emailField, passwordField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() != null) {
            openHome();
            return;
        }

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
        emailField.setInputType(33);

        passwordField = new EditText(this);
        passwordField.setHint("Password");
        passwordField.setInputType(129);

        Button loginButton = new Button(this);
        loginButton.setText("Login");

        Button registerButton = new Button(this);
        registerButton.setText("Create New Account");

        loginButton.setOnClickListener(v -> loginUser());
        registerButton.setOnClickListener(v -> registerUser());

        page.addView(title);
        page.addView(subtitle);
        page.addView(nameField);
        page.addView(emailField);
        page.addView(passwordField);
        page.addView(loginButton);
        page.addView(registerButton);

        setContentView(page);
    }

    private void loginUser() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email and password required",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        openHome();
                    } else {
                        Toast.makeText(this, "Login failed",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void registerUser() {
        String name = nameField.getText().toString().trim();
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Name, email and password required",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String uid = auth.getCurrentUser().getUid();

                        Map<String, Object> user = new HashMap<>();
                        user.put("uid", uid);
                        user.put("name", name);
                        user.put("email", email);
                        user.put("createdAt",
                                FieldValue.serverTimestamp());

                        db.collection("users")
                                .document(uid)
                                .set(user)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this,
                                            "Account created successfully",
                                            Toast.LENGTH_SHORT).show();
                                    openHome();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this,
                                            "Profile save failed: " +
                                            e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                });

                    } else {
                        Toast.makeText(this,
                                "Registration failed: " +
                                task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void openHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
