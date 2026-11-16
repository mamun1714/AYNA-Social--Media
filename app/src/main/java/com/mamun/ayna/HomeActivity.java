
package com.mamun.ayna;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

public class HomeActivity extends Activity {

    private LinearLayout feedLayout;
    private EditText postInput;
    private Button postButton;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration feedListener;

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

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        createScreen();
        loadPosts();
    }

    private void createScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 250));

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

        ScrollView scroll = new ScrollView(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(14), dp(14), dp(14), dp(14));
        composer.setBackground(shape(Color.WHITE, 14));

        TextView composerTitle = text(
                "আপনার মনের কথা লিখুন", 17, Color.DKGRAY);
        composerTitle.setTypeface(null, Typeface.BOLD);

        postInput = new EditText(this);
        postInput.setHint("আজ আপনি কী ভাবছেন?");
        postInput.setMinLines(2);
        postInput.setGravity(Gravity.TOP);
        postInput.setBackground(shape(
                Color.rgb(245, 247, 250), 10));
        postInput.setPadding(dp(12), dp(10),
                dp(12), dp(10));

        postButton = button("Post");
        postButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(23, 105, 170)));
        postButton.setTextColor(Color.WHITE);
        postButton.setOnClickListener(v -> publishPost());

        composer.addView(composerTitle);
        composer.addView(postInput);
        composer.addView(postButton);
        content.addView(composer);

        TextView feedTitle = text("নিউজ ফিড", 20,
                Color.rgb(23, 105, 170));
        feedTitle.setTypeface(null, Typeface.BOLD);
        feedTitle.setPadding(0, dp(20), 0, dp(10));
        content.addView(feedTitle);

        feedLayout = new LinearLayout(this);
        feedLayout.setOrientation(LinearLayout.VERTICAL);
        content.addView(feedLayout);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                -1, 0, 1));

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

    private void publishPost() {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            showMessage("প্রথমে লগইন করুন");
            return;
        }

        String message = postInput.getText()
                .toString().trim();

        if (message.isEmpty()) {
            postInput.setError("কিছু লিখুন");
            return;
        }

        postButton.setEnabled(false);

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(profile -> {
                    String name = profile.getString("name");

                    if (name == null || name.trim().isEmpty()) {
                        name = "AYNA User";
                    }

                    savePost(user, name, message);
                })
                .addOnFailureListener(error -> {
                    postButton.setEnabled(true);
                    showMessage("প্রোফাইল পাওয়া যায়নি: "
                            + error.getMessage());
                });
    }

    private void savePost(FirebaseUser user,
                          String name, String message) {

        DocumentReference postRef =
                db.collection("posts").document();

        Map<String, Object> post = new HashMap<>();
        post.put("postId", postRef.getId());
        post.put("uid", user.getUid());
        post.put("authorName", name);
        post.put("text", message);
        post.put("createdAt", FieldValue.serverTimestamp());
        post.put("likesCount", 0);
        post.put("commentsCount", 0);

        postRef.set(post)
                .addOnSuccessListener(unused -> {
                    postInput.setText("");
                    postButton.setEnabled(true);
                    showMessage("পোস্ট সফলভাবে সেভ হয়েছে");
                })
                .addOnFailureListener(error -> {
                    postButton.setEnabled(true);
                    showMessage("পোস্ট সেভ হয়নি: "
                            + error.getMessage());
                });
    }

    private void loadPosts() {
        feedListener = db.collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        showMessage("ফিড লোড হয়নি: "
                                + error.getMessage());
                        return;
                    }

                    feedLayout.removeAllViews();

                    if (snapshot == null || snapshot.isEmpty()) {
                        TextView empty = text(
                                "এখনো কোনো পোস্ট নেই।\nআপনার প্রথম পোস্টটি শেয়ার করুন!",
                                15, Color.GRAY);
                        empty.setGravity(Gravity.CENTER);
                        empty.setPadding(dp(12), dp(30),
                                dp(12), dp(30));
                        feedLayout.addView(empty);
                        return;
                    }

                    for (DocumentSnapshot document :
                            snapshot.getDocuments()) {

                        String name = document.getString("authorName");
                        String message = document.getString("text");

                        if (name == null) name = "AYNA User";
                        if (message == null) message = "";

                        addPostCard(name, message);
                    }
                });
    }

    private void addPostCard(String name, String message) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14),
                dp(14), dp(14));
        card.setBackground(shape(Color.WHITE, 12));

        TextView author = text(name, 16,
                Color.rgb(23, 105, 170));
        author.setTypeface(null, Typeface.BOLD);

        TextView postText = text(message, 16, Color.DKGRAY);
        postText.setPadding(0, dp(12), 0, dp(12));

        TextView footer = text(
                "Like     Comment     Share", 14, Color.GRAY);

        card.addView(author);
        card.addView(postText);
        card.addView(footer);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);

        feedLayout.addView(card, params);
    }

    private void showMessage(String message) {
        Toast.makeText(this, message,
                Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (feedListener != null) {
            feedListener.remove();
        }
        super.onDestroy();
    }
}
