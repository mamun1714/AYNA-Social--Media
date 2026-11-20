
package com.mamun.ayna;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import android.content.res.ColorStateList;

import com.bumptech.glide.Glide;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeActivity extends Activity {

    private LinearLayout feedLayout;
    private EditText postInput;
    private Button postButton;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private ListenerRegistration feedListener;

    private final List<ListenerRegistration> likeListeners =
            new ArrayList<>();

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

    private TextView text(String value, int size) {
        return text(value, size, Color.DKGRAY);
    }

    private Button button(String value) {
        Button btn = new Button(this);
        btn.setText(value);
        btn.setTextSize(14);
        return btn;
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

        // AYNA HEADER

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

        // SCROLL VIEW

        ScrollView scroll = new ScrollView(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));

        // POST COMPOSER

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(14), dp(14), dp(14), dp(14));
        composer.setBackground(shape(Color.WHITE, 14));

        TextView title = text(
                "আপনার মনের কথা লিখুন", 17, Color.DKGRAY);
        title.setTypeface(null, Typeface.BOLD);

        postInput = new EditText(this);
        postInput.setHint("আজ আপনি কী ভাবছেন?");
        postInput.setMinLines(2);
        postInput.setGravity(Gravity.TOP);
        postInput.setPadding(dp(12), dp(10), dp(12), dp(10));

        postButton = button("Post");
        postButton.setTextColor(Color.WHITE);
        postButton.setBackgroundTintList(
                ColorStateList.valueOf(Color.rgb(23, 105, 170)));

        postButton.setOnClickListener(v -> publishPost());

        Button mediaButton = button("ছবি / ভিডিও পোস্ট করুন");
        mediaButton.setTextColor(Color.WHITE);
        mediaButton.setBackgroundTintList(
                ColorStateList.valueOf(Color.rgb(23, 105, 170)));

        mediaButton.setOnClickListener(v -> {

            if (auth.getCurrentUser() == null) {
                showMessage("প্রথমে লগইন করুন");
                return;
            }

            Intent intent = new Intent(
                    HomeActivity.this,
                    CreatePostActivity.class);

            startActivity(intent);
        });

        composer.addView(title);
        composer.addView(postInput);
        composer.addView(postButton);
        composer.addView(mediaButton);

        content.addView(composer);

        // NEWS FEED

        TextView feedTitle = text(
                "নিউজ ফিড", 20, Color.rgb(23, 105, 170));

        feedTitle.setTypeface(null, Typeface.BOLD);
        feedTitle.setPadding(0, dp(20), 0, dp(10));

        content.addView(feedTitle);

        feedLayout = new LinearLayout(this);
        feedLayout.setOrientation(LinearLayout.VERTICAL);

        content.addView(feedLayout);

        scroll.addView(content);

        root.addView(scroll,
                new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
    }

    // PUBLISH POST

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
                    showMessage("প্রোফাইল পাওয়া যায়নি");
                });
    }

    // 1. SAVE POST

    private void savePost(
            FirebaseUser user,
            String name,
            String message) {

        String postId = db.collection("posts")
                .document().getId();

        Map<String, Object> post = new HashMap<>();

        post.put("postId", postId);
        post.put("uid", user.getUid());
        post.put("authorName", name);
        post.put("text", message);
        post.put("mediaUrl", "");
        post.put("mediaType", "text");
        post.put("createdAt", FieldValue.serverTimestamp());
        post.put("likesCount", 0);
        post.put("commentsCount", 0);

        db.collection("posts")
                .document(postId)
                .set(post)
                .addOnSuccessListener(unused -> {
                    postInput.setText("");
                    postButton.setEnabled(true);
                    showMessage("পোস্ট প্রকাশ হয়েছে");
                })
                .addOnFailureListener(error -> {
                    postButton.setEnabled(true);
                    showMessage("পোস্ট করা যায়নি: "
                            + error.getMessage());
                });
    }

    // CLEAR LIKE LISTENERS

    private void clearLikeListeners() {

        for (ListenerRegistration listener : likeListeners) {
            listener.remove();
        }

        likeListeners.clear();
    }

    // 2. LOAD POSTS

    private void loadPosts() {

        if (feedListener != null) {
            feedListener.remove();
        }

        feedListener = db.collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        showMessage("ফিড লোড হয়নি: "
                                + error.getMessage());
                        return;
                    }

                    if (snapshot == null || feedLayout == null) {
                        return;
                    }

                    clearLikeListeners();
                    feedLayout.removeAllViews();

                    if (snapshot.isEmpty()) {

                        TextView empty = text(
                                "এখনো কোনো পোস্ট নেই",
                                16, Color.GRAY);

                        empty.setGravity(Gravity.CENTER);
                        empty.setPadding(
                                dp(12), dp(30), dp(12), dp(30));

                        feedLayout.addView(empty);
                        return;
                    }

                    for (DocumentSnapshot document :
                            snapshot.getDocuments()) {

                        String postId = document.getId();

                        String name =
                                document.getString("authorName");

                        String message =
                                document.getString("text");

                        String mediaUrl =
                                document.getString("mediaUrl");

                        String mediaType =
                                document.getString("mediaType");

                        if (name == null) name = "AYNA User";
                        if (message == null) message = "";
                        if (mediaUrl == null) mediaUrl = "";
                        if (mediaType == null) mediaType = "text";

                        addPostCard(
                                postId,
                                name,
                                message,
                                mediaUrl,
                                mediaType
                        );
                    }
                });
    }

    // 3. ADD POST CARD

    private void addPostCard(
            String postId,
            String name,
            String message,
            String mediaUrl,
            String mediaType) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(shape(Color.WHITE, 12));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, dp(6), 0, dp(10));
        card.setLayoutParams(params);

        TextView author = text(name, 17);
        author.setTypeface(null, Typeface.BOLD);

        card.addView(author);

        if (!message.trim().isEmpty()) {
            TextView postText = text(message, 16);
            postText.setPadding(0, dp(8), 0, dp(8));
            card.addView(postText);
        }

        // PHOTO DISPLAY

        if (!mediaUrl.isEmpty() && "image".equals(mediaType)) {

            ImageView image = new ImageView(this);

            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);

            card.addView(image);

            Glide.with(this)
                    .load(mediaUrl)
                    .into(image);
        }

        // VIDEO DISPLAY

        if (!mediaUrl.isEmpty() && "video".equals(mediaType)) {

            VideoView video = new VideoView(this);

            video.setVideoURI(Uri.parse(mediaUrl));

            MediaController controller =
                    new MediaController(this);

            controller.setAnchorView(video);
            video.setMediaController(controller);

            card.addView(video);
        }

        // POST ACTIONS

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button likeButton = button("👍 Like");
        TextView likeCount = text("0 Likes", 13);
        Button commentButton = button("Comment");
        Button shareButton = button("Share");

        actions.addView(likeButton);
        actions.addView(likeCount);
        actions.addView(commentButton);
        actions.addView(shareButton);

        card.addView(actions);

        setupLike(postId, likeButton, likeCount);

        commentButton.setOnClickListener(v ->
                showComments(postId));

        shareButton.setOnClickListener(v ->
                sharePost(name, message, mediaUrl));

        feedLayout.addView(card);
    }

    // LIKE / UNLIKE

    private void setupLike(
            String postId,
            Button likeButton,
            TextView likeCount) {

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            likeButton.setEnabled(false);
            return;
        }

        DocumentReference myLikeRef =
                db.collection("posts")
                        .document(postId)
                        .collection("likes")
                        .document(user.getUid());

        ListenerRegistration listener =
                db.collection("posts")
                        .document(postId)
                        .collection("likes")
                        .addSnapshotListener((snapshot, error) -> {

                            if (error != null || snapshot == null) {
                                return;
                            }

                            int count = snapshot.size();
                            boolean liked = false;

                            for (DocumentSnapshot doc :
                                    snapshot.getDocuments()) {

                                if (doc.getId().equals(user.getUid())) {
                                    liked = true;
                                    break;
                                }
                            }

                            likeButton.setText(
                                    liked ? "👍 Liked" : "👍 Like");

                            likeCount.setText(count + " Likes");
                        });

        likeListeners.add(listener);

        likeButton.setOnClickListener(v -> {

            likeButton.setEnabled(false);

            myLikeRef.get()
                    .addOnSuccessListener(document -> {

                        if (document.exists()) {

                            myLikeRef.delete()
                                    .addOnSuccessListener(unused ->
                                            likeButton.setEnabled(true))
                                    .addOnFailureListener(error -> {
                                        likeButton.setEnabled(true);
                                        showMessage("Like সরানো যায়নি");
                                    });

                        } else {

                            Map<String, Object> like =
                                    new HashMap<>();

                            like.put("uid", user.getUid());
                            like.put("createdAt",
                                    FieldValue.serverTimestamp());

                            myLikeRef.set(like)
                                    .addOnSuccessListener(unused ->
                                            likeButton.setEnabled(true))
                                    .addOnFailureListener(error -> {
                                        likeButton.setEnabled(true);
                                        showMessage("Like দেওয়া যায়নি");
                                    });
                        }
                    })
                    .addOnFailureListener(error -> {
                        likeButton.setEnabled(true);
                        showMessage("Like পরীক্ষা করা যায়নি");
                    });
        });
    }

    // COMMENTS

    private void showComments(String postId) {

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(10), dp(16), dp(10));

        ScrollView scroll = new ScrollView(this);

        LinearLayout commentList = new LinearLayout(this);
        commentList.setOrientation(LinearLayout.VERTICAL);

        scroll.addView(commentList);

        EditText input = new EditText(this);
        input.setHint("আপনার মন্তব্য লিখুন");

        Button send = button("মন্তব্য পাঠান");

        layout.addView(scroll,
                new LinearLayout.LayoutParams(-1, dp(220)));

        layout.addView(input);
        layout.addView(send);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("মন্তব্য")
                .setView(layout)
                .setNegativeButton("বন্ধ", null)
                .create();

        db.collection("posts")
                .document(postId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(snapshot -> {

                    commentList.removeAllViews();

                    if (snapshot.isEmpty()) {
                        commentList.addView(text(
                                "এখনো কোনো মন্তব্য নেই",
                                14, Color.GRAY));
                    }

                    for (DocumentSnapshot doc :
                            snapshot.getDocuments()) {

                        String author = doc.getString("authorName");
                        String message = doc.getString("text");

                        if (author == null) author = "AYNA User";
                        if (message == null) message = "";

                        TextView comment = text(
                                author + "\n" + message,
                                14, Color.DKGRAY);

                        comment.setPadding(
                                0, dp(8), 0, dp(8));

                        commentList.addView(comment);
                    }
                })
                .addOnFailureListener(error ->
                        showMessage("মন্তব্য লোড হয়নি"));

        send.setOnClickListener(v -> {

            FirebaseUser user = auth.getCurrentUser();

            if (user == null) {
                showMessage("প্রথমে লগইন করুন");
                return;
            }

            String message = input.getText()
                    .toString().trim();

            if (message.isEmpty()) {
                input.setError("মন্তব্য লিখুন");
                return;
            }

            send.setEnabled(false);

            db.collection("users")
                    .document(user.getUid())
                    .get()
                    .addOnSuccessListener(profile -> {

                        String name = profile.getString("name");

                        if (name == null || name.trim().isEmpty()) {
                            name = "AYNA User";
                        }

                        Map<String, Object> comment =
                                new HashMap<>();

                        comment.put("uid", user.getUid());
                        comment.put("authorName", name);
                        comment.put("text", message);
                        comment.put("createdAt",
                                FieldValue.serverTimestamp());

                        DocumentReference postRef =
                                db.collection("posts").document(postId);

                        postRef.collection("comments")
                                .add(comment)
                                .addOnSuccessListener(ref -> {

                                    postRef.update(
                                            "commentsCount",
                                            FieldValue.increment(1));

                                    showMessage("মন্তব্য পাঠানো হয়েছে");
                                    dialog.dismiss();
                                })
                                .addOnFailureListener(error -> {
                                    send.setEnabled(true);
                                    showMessage("মন্তব্য পাঠানো যায়নি");
                                });
                    })
                    .addOnFailureListener(error -> {
                        send.setEnabled(true);
                        showMessage("প্রোফাইল পাওয়া যায়নি");
                    });
        });

        dialog.show();
    }

    // 4. SHARE POST

    private void sharePost(
            String name,
            String message,
            String mediaUrl) {

        String shareText = name + "\n\n" + message;

        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            shareText += "\n\n" + mediaUrl;
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);

        try {
            startActivity(Intent.createChooser(
                    intent, "AYNA পোস্ট শেয়ার করুন"));
        } catch (Exception error) {
            showMessage("শেয়ার করা যায়নি");
        }
    }

    // MESSAGE

    private void showMessage(String message) {

        Toast.makeText(this,
                message, Toast.LENGTH_LONG).show();
    }

    // CLEANUP

    @Override
    protected void onDestroy() {

        if (feedListener != null) {
            feedListener.remove();
        }

        clearLikeListeners();

        super.onDestroy();
    }
}
