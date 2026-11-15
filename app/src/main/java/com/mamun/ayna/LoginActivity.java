package com.mamun.ayna;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.graphics.Color;

public class LoginActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(32, 32, 32, 32);
        page.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("AYNA Login");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(23, 105, 170));
        title.setGravity(Gravity.CENTER);

        EditText email = new EditText(this);
        email.setHint("Email Address");
        email.setInputType(33);

        EditText password = new EditText(this);
        password.setHint("Password");
        password.setInputType(129);

        Button loginButton = new Button(this);
        loginButton.setText("Login");

        Button registerButton = new Button(this);
        registerButton.setText("Create New Account");

        page.addView(title);
        page.addView(email);
        page.addView(password);
        page.addView(loginButton);
        page.addView(registerButton);

        setContentView(page);
    }
}
