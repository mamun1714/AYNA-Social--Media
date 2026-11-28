
package com.mamun.ayna;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReelsActivity extends Activity {

    private FrameLayout root;
    private PlayerView playerView;
    private ExoPlayer player;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private ListenerRegistration reelsListener;

    private final List<Reel> reels = new ArrayList<>();

    private int currentIndex = 0;
    private boolean isLiked = false;
    private boolean isLoading = false;

    private TextView creatorText;
    private TextView captionText;
    private TextView likeText;
    private TextView commentText;
    private TextView viewText;
    private TextView statusText;
    private TextView playText;
    private TextView previousText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        createInterface();
        loadReels();
    }

    private void createInterface() {

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setUseController(true);

        root.addView(playerView,
                new FrameLayout.LayoutParams(-1, -1));

        LinearLayout information = new LinearLayout(this);
        information.setOrientation(LinearLayout.VERTICAL);
        information.setPadding(20, 15, 20, 25);

        creatorText = createText("@Creator", 19);
        captionText = createText("", 15);
        likeText = createText("♡ 0 Like", 17);
        commentText = createText("Comments: 0", 15);
        viewText = createText("Views: 0", 15);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);

        previousText = createText("Previous", 15);
        playText = createText("Next Reel", 15);

        navigation.addView(previousText,
                new LinearLayout.LayoutParams(0, -2, 1));

        navigation.addView(playText,
                new LinearLayout.LayoutParams(0, -2, 1));

        information.addView(creatorText);
        information.addView(captionText);
        information.addView(likeText);
        information.addView(commentText);
        information.addView(viewText);
        information.addView(navigation);

        likeText.setOnClickListener(v -> toggleLike());

        playText.setOnClickListener(v -> nextReel());

        previousText.setOnClickListener(v -> previousReel());

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        -1, -2, Gravity.BOTTOM);

        root.addView(information, params);

        statusText = createText("Loading Reels...", 17);
        statusText.setGravity(Gravity.CENTER);

        root.addView(statusText,
                new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
    }

    private TextView createText(String value, int size) {

        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.WHITE);
        text.setPadding(8, 9, 8, 9);

        return text;
    }

    private void loadReels() {

        isLoading = true;

        reelsListener = db.collection("reels")
                .orderBy("createdAt",
                        Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((snapshot, error) -> {

                    isLoading = false;

                    if (error != null) {
                        showStatus("Reels লোড হয়নি: "
                                + error.getMessage());
                        return;
                    }

                    if (snapshot == null) return;

                    String currentId = null;

                    if (!reels.isEmpty()
                            && currentIndex < reels.size()) {
                        currentId = reels.get(currentIndex).id;
                    }

                    reels.clear();

                    for (DocumentSnapshot doc :
                            snapshot.getDocuments()) {

                        String url = doc.getString("videoUrl");

                        if (url == null || url.trim().isEmpty()) {
                            continue;
                        }

                        Reel reel = new Reel();

                        reel.id = doc.getId();
                        reel.userId = doc.getString("userId");
                        reel.userName = doc.getString("userName");
                        reel.caption = doc.getString("caption");
                        reel.videoUrl = url;

                        Long likes = doc.getLong("likesCount");
                        Long comments = doc.getLong("commentsCount");
                        Long views = doc.getLong("viewsCount");

                        reel.likes = likes == null ? 0 : likes;
                        reel.comments = comments == null ? 0 : comments;
                        reel.views = views == null ? 0 : views;

                        reels.add(reel);
                    }

                    if (reels.isEmpty()) {
                        releasePlayer();
                        showStatus("এখনো কোনো Reels পাওয়া যায়নি।");
                        return;
                    }

                    currentIndex = 0;

                    if (currentId != null) {
                        for (int i = 0; i < reels.size(); i++) {
                            if (reels.get(i).id.equals(currentId)) {
                                currentIndex = i;
                                break;
                            }
                        }
                    }

                    statusText.setVisibility(View.GONE);
                    showReel();
                });
    }

    private void showReel() {

        if (currentIndex < 0 || currentIndex >= reels.size()) {
            return;
        }

        Reel reel = reels.get(currentIndex);

        releasePlayer();

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        MediaItem item = MediaItem.fromUri(reel.videoUrl);

        player.setMediaItem(item);
        player.prepare();
        player.play();

        creatorText.setText("@"
                + (reel.userName == null
                ? "Creator" : reel.userName));

        captionText.setText(
                reel.caption == null ? "" : reel.caption);

        likeText.setText("♡ " + reel.likes + " Like");
        commentText.setText("Comments: " + reel.comments);
        viewText.setText("Views: " + reel.views);

        checkLikeStatus(reel);
        countView(reel);
    }

    private void countView(Reel reel) {

        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("viewedAt", FieldValue.serverTimestamp());

        db.collection("reels")
                .document(reel.id)
                .collection("views")
                .document(uid)
                .set(data)
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "View সংরক্ষণ হয়নি",
                                Toast.LENGTH_SHORT).show());
    }

    private void checkLikeStatus(Reel reel) {

        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();

        db.collection("reels")
                .document(reel.id)
                .collection("likes")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    if (currentIndex >= reels.size()
                            || !reels.get(currentIndex).id.equals(reel.id)) {
                        return;
                    }

                    isLiked = doc.exists();

                    likeText.setText(
                            (isLiked ? "♥ " : "♡ ")
                                    + reel.likes + " Like");
                });
    }

    private void toggleLike() {

        if (auth.getCurrentUser() == null) {
            showStatus("Like করতে Login করুন।");
            return;
        }

        if (currentIndex >= reels.size()) return;

        Reel reel = reels.get(currentIndex);

        String uid = auth.getCurrentUser().getUid();

        com.google.firebase.firestore.DocumentReference likeRef =
                db.collection("reels")
                        .document(reel.id)
                        .collection("likes")
                        .document(uid);

        db.runTransaction(transaction -> {

            DocumentSnapshot snapshot = transaction.get(likeRef);

            if (snapshot.exists()) {
                transaction.delete(likeRef);
            } else {
                Map<String, Object> data = new HashMap<>();
                data.put("userId", uid);
                data.put("createdAt", FieldValue.serverTimestamp());

                transaction.set(likeRef, data);
            }

            return !snapshot.exists();

        }).addOnSuccessListener(liked -> {

            isLiked = liked;

            if (liked) {
                reel.likes++;
            } else {
                reel.likes = Math.max(0, reel.likes - 1);
            }

            likeText.setText(
                    (isLiked ? "♥ " : "♡ ")
                            + reel.likes + " Like");

        }).addOnFailureListener(e ->
                showStatus("Like আপডেট হয়নি: "
                        + e.getMessage()));
    }

    private void nextReel() {

        if (currentIndex + 1 < reels.size()) {
            currentIndex++;
            showReel();
        } else {
            showStatus("আর কোনো Reels নেই।");
        }
    }

    private void previousReel() {

        if (currentIndex > 0) {
            currentIndex--;
            showReel();
        }
    }

    private void showStatus(String message) {

        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
    }

    private void releasePlayer() {

        if (player != null) {
            player.release();
            player = null;
        }

        if (playerView != null) {
            playerView.setPlayer(null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (reelsListener != null) {
            reelsListener.remove();
            reelsListener = null;
        }

        releasePlayer();
    }

    private static class Reel {

        String id;
        String userId;
        String userName;
        String caption;
        String videoUrl;

        long likes;
        long comments;
        long views;
    }
}
