
package com.mamun.ayna;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class HomeActivity extends Activity {

    private LinearLayout feedLayout;
    private EditText postInput;

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        return button;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 250));

        // Top header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));
        header.setBackgroundColor(Color.WHITE);

        TextView logo = text("আয়না", 28,
                Color.rgb(23, 105, 170));
        logo.setTypeface(null, Typeface.BOLD);

        TextView appName = text("  AYNA Social", 19,
                Color.DKGRAY);
        appName.setTypeface(null, Typeface.BOLD);

        header.addView(logo);
        header.addView(appName);
        root.addView(header);

        // Scrollable home content
        ScrollView scroll = new ScrollView(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));

        // Create post box
        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(14), dp(14), dp(14), dp(14));
        composer.setBackground(shape(Color.WHITE, 14));

        TextView composerTitle = text("আপনার মনের কথা লিখুন",
                17, Color.DKGRAY);
        composerTitle.setTypeface(null, Typeface.BOLD);

        postInput = new EditText(this);
        postInput.setHint("আজ আপনি কী ভাবছেন?");
        postInput.setMinLines(2);
        postInput.setGravity(Gravity.TOP);
        postInput.setBackground(shape(
                Color.rgb(245, 247, 250), 10));
        postInput.setPadding(dp(12), dp(10),
                dp(12), dp(10));

        Button postButton = button("Post");
        postButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(23, 105, 170)));
        postButton.setTextColor(Color.WHITE);

        Button photoButton = button("ছবি");
        Button videoButton = button("ভিডিও");

        LinearLayout mediaButtons = new LinearLayout(this);
        mediaButtons.setGravity(Gravity.CENTER_VERTICAL);

        mediaButtons.addView(photoButton,
                new LinearLayout.LayoutParams(0,
                        dp(48), 1));
        mediaButtons.addView(videoButton,
                new LinearLayout.LayoutParams(0,
                        dp(48), 1));

        composer.addView(composerTitle);
        composer.addView(postInput);
        composer.addView(mediaButtons);
        composer.addView(postButton);

        content.addView(composer);

        // Feed title
        TextView feedTitle = text("নিউজ ফিড", 20,
                Color.rgb(23, 105, 170));
        feedTitle.setTypeface(null, Typeface.BOLD);
        feedTitle.setPadding(0, dp(20), 0, dp(10));

        content.addView(feedTitle);

        feedLayout = new LinearLayout(this);
        feedLayout.setOrientation(LinearLayout.VERTICAL);

        TextView emptyFeed = text(
                "এখনো কোনো পোস্ট নেই।\nআপনার প্রথম পোস্টটি শেয়ার করুন!",
                15, Color.GRAY);
        emptyFeed.setGravity(Gravity.CENTER);
        emptyFeed.setPadding(dp(12), dp(30),
                dp(12), dp(30));

        feedLayout.addView(emptyFeed);
        content.addView(feedLayout);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                -1, 0, 1));

        // Post action
        postButton.setOnClickListener(v -> {
            String message = postInput.getText()
                    .toString().trim();

            if (message.isEmpty()) {
                postInput.setError("কিছু লিখুন");
                return;
            }

            addPost(message);
            postInput.setText("");
            Toast.makeText(this, "পোস্ট যোগ হয়েছে",
                    Toast.LENGTH_SHORT).show();
        });

        photoButton.setOnClickListener(v ->
                Toast.makeText(this,
                        "ছবি পোস্টের সুবিধা পরের ধাপে যুক্ত হবে",
                        Toast.LENGTH_SHORT).show());

        videoButton.setOnClickListener(v ->
                Toast.makeText(this,
                        "ভিডিও পোস্টের সুবিধা পরের ধাপে যুক্ত হবে",
                        Toast.LENGTH_SHORT).show());

        // Bottom navigation
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setBackgroundColor(Color.WHITE);

        String[] items = {
                "Home", "Search", "Notifications", "Profile"
        };

        for (String item : items) {
            Button navButton = button(item);
            navButton.setTextSize(11);

            navButton.setOnClickListener(v ->
                    Toast.makeText(this,
                            item + " বিভাগ পরে তৈরি হবে",
                            Toast.LENGTH_SHORT).show());

            navigation.addView(navButton,
                    new LinearLayout.LayoutParams(
                            0, dp(55), 1));
        }

        root.addView(navigation);

        setContentView(root);
    }

    private void addPost(String message) {
        if (feedLayout.getChildCount() == 1) {
            feedLayout.removeAllViews();
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14),
                dp(14), dp(14));
        card.setBackground(shape(Color.WHITE, 12));

        TextView author = text("AYNA User", 16,
                Color.rgb(23, 105, 170));
        author.setTypeface(null, Typeface.BOLD);

        TextView postText = text(message, 16,
                Color.DKGRAY);
        postText.setPadding(0, dp(12), 0, dp(12));

        TextView footer = text("Like     Comment     Share",
                14, Color.GRAY);

        card.addView(author);
        card.addView(postText);
        card.addView(footer);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2);
        params.bottomMargin = dp(10);

        feedLayout.addView(card, 0, params);
    }
}
