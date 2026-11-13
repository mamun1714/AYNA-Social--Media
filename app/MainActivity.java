package com.mamun.ayna;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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
        page.setBackgroundColor(0xFFF5F7FB);

        TextView logo = new TextView(this);
        logo.setText("AYNA");
        logo.setTextSize(36);
        logo.setTextColor(0xFF1769AA);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText(getString(R.string.welcome));
        subtitle.setTextSize(18);
        subtitle.setTextColor(0xFF263238);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 18, 0, 0);

        page.addView(logo);
        page.addView(subtitle);
        setContentView(page);

        // ৩ সেকেন্ড পর স্বয়ংক্রিয়ভাবে পরের পেজে (HomeActivity) যাওয়ার কোড
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // HomeActivity নামের পেজ না থাকলে আপনার পরবর্তী পেজের নাম এখানে দিতে পারেন
                Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                startActivity(intent);
                finish(); // স্প্ল্যাশ স্ক্রিন বন্ধ করে দেওয়া যাতে ব্যাক করলে আর এখানে না আসে
            }
        }, 3000); // 3000 মিলিছেকেন্ড = ৩ সেকেন্ড
    }
}
