package com.mamun.ayna;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout; 
import android.widget.TextView;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.VideoView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class HomeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(32, 32, 32, 32);
        page.setBackgroundColor(0xFFFFFFFF);

        TextView title = new TextView(this);
        title.setText("Welcome to AYNA Home");
        title.setTextSize(24);
        title.setTextColor(0xFF1769AA);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        page.addView(title);
        setContentView(page);        clearLikeListeners();

        super.onDestroy();
    }
}
    }
}
