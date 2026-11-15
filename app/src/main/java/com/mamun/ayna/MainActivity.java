

package com.mamun.ayna;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(32, 32, 32, 32);
        page.setBackgroundColor(Color.rgb(245, 247, 251));

        TextView logo = new TextView(this);
        logo.setText("আয়না");
        logo.setTextSize(42);
        logo.setTextColor(Color.rgb(23, 105, 170));
        logo.setTypeface(null, Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("AYNA Social Media");
        subtitle.setTextSize(20);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);

        TextView welcome = new TextView(this);
        welcome.setText("Welcome to AYNA\nআপনার সামাজিক যোগাযোগের নতুন ঠিকানা");
        welcome.setTextSize(16);
        welcome.setTextColor(Color.GRAY);
        welcome.setGravity(Gravity.CENTER);
        welcome.setPadding(0, 24, 0, 24);

        page.addView(logo);
        page.addView(subtitle);
        page.addView(welcome);

        setContentView(page);
    }
}
