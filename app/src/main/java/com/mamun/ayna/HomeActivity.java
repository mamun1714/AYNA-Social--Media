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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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

    private LinearLayout root, feedLayout, storyLayout;
    private EditText postInput;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration feedListener;
    private ListenerRegistration notificationListener;

    private final List<ListenerRegistration> listeners = new ArrayList<>();
    private final List<String> blockedUsers = new ArrayList<>();

    private String myUid;
    private String myName = "AYNA User";

    private final int BLUE = Color.rgb(23, 105, 170);
    private final int BG = Color.rgb(245, 247, 251);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            finish();
            return;
        }

        myUid = user.getUid();

        loadMyProfile();
        loadBlockedUsers();
        createScreen();
        loadStories();
        loadPosts();
        listenNotifications();
    }

    private void createScreen() {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setPadding(15, 12, 15, 12);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.WHITE);

        TextView logo = new TextView(this);
        logo.setText("আয়না AYNA");
        logo.setTextSize(23);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setTextColor(BLUE);

        header.addView(logo, new LinearLayout.LayoutParams(
                0, -2, 1
        ));

        Button search = makeButton("Search");
        search.setOnClickListener(v -> showSearch());
        header.addView(search);

        Button profile = makeButton("Profile");
        profile.setOnClickListener(v -> showProfile(myUid));
        header.addView(profile);

        root.addView(header);

        storyLayout = new LinearLayout(this);
        storyLayout.setPadding(8, 8, 8, 8);
        storyLayout.setOrientation(LinearLayout.HORIZONTAL);
        storyLayout.setBackgroundColor(Color.WHITE);

        root.addView(storyLayout);

        postInput = new EditText(this);
        postInput.setHint("আপনার মনের কথা লিখুন...");
        postInput.setMinLines(2);
        postInput.setPadding(12, 10, 12, 10);

        root.addView(postInput);

        LinearLayout postActions = new LinearLayout(this);
        postActions.setGravity(Gravity.CENTER);

        Button postButton = makeButton("Post");
        postButton.setOnClickListener(v -> publishPost());
        postActions.addView(postButton);

        Button mediaButton = makeButton("Photo / Video");
        mediaButton.setOnClickListener(v -> openCreatePost());
        postActions.addView(mediaButton);

        Button storyButton = makeButton("Story");
        storyButton.setOnClickListener(v -> addStory());
        postActions.addView(storyButton);

        root.addView(postActions);

        ScrollView scroll = new ScrollView(this);
        feedLayout = new LinearLayout(this);
        feedLayout.setOrientation(LinearLayout.VERTICAL);
        feedLayout.setPadding(8, 8, 8, 8);

        scroll.addView(feedLayout);
        root.addView(scroll, new LinearLayout.LayoutParams(
                -1, 0, 1
        ));

        createBottomNavigation();

        setContentView(root);
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        return button;
    }

    private TextView makeText(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.BLACK);
        view.setPadding(8, 6, 8, 6);
        return view;
    }

    private void loadMyProfile() {
        db.collection("users").document(myUid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        if (name != null && !name.isEmpty()) {
                            myName = name;
                        }
                    }
                });
    }

    // 1. Text Post

    private void publishPost() {

        String text = postInput.getText().toString().trim();

        if (text.isEmpty()) {
            showMessage("কিছু লিখুন");
            return;
        }

        if (!isSafeContent(text)) {
            showMessage("এই লেখাটি প্রকাশ করা যাচ্ছে না");
            return;
        }

        Map<String, Object> post = new HashMap<>();
        post.put("uid", myUid);
        post.put("authorName", myName);
        post.put("text", text);
        post.put("mediaUrl", "");
        post.put("mediaType", "text");
        post.put("createdAt", FieldValue.serverTimestamp());
        post.put("likesCount", 0);
        post.put("commentsCount", 0);

        db.collection("posts").add(post)
                .addOnSuccessListener(ref -> {
                    postInput.setText("");
                    showMessage("পোস্ট প্রকাশ হয়েছে");
                })
                .addOnFailureListener(e ->
                        showMessage("পোস্ট করা যায়নি"));
    }

    // 2. News Feed

    private void loadPosts() {

        if (feedListener != null) {
            feedListener.remove();
        }

        feedListener = db.collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) return;

                    feedLayout.removeAllViews();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        String uid = doc.getString("uid");

                        if (uid == null || blockedUsers.contains(uid)) {
                            continue;
                        }

                        addPostCard(doc);
                    }
                });
    }

    private void addPostCard(DocumentSnapshot doc) {

        String postId = doc.getId();
        String uid = doc.getString("uid");
        String author = doc.getString("authorName");
        String text = doc.getString("text");
        String mediaUrl = doc.getString("mediaUrl");
        String mediaType = doc.getString("mediaType");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(12, 12, 12, 12);
        card.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 0, 0, 10);
        card.setLayoutParams(cardParams);

        TextView authorView = makeText(
                author == null ? "AYNA User" : author, 17);
        authorView.setTypeface(null, Typeface.BOLD);
        authorView.setOnClickListener(v -> showProfile(uid));
        card.addView(authorView);

        if (text != null && !text.isEmpty()) {
            card.addView(makeText(text, 15));
        }

        if (mediaUrl != null && !mediaUrl.isEmpty()) {

            if ("image".equals(mediaType)) {

                ImageView image = new ImageView(this);
                image.setAdjustViewBounds(true);

                card.addView(image);

                Glide.with(this)
                        .load(mediaUrl)
                        .into(image);

            } else if ("video".equals(mediaType)) {

                TextView videoLink = makeText("ভিডিও দেখুন", 16);
                videoLink.setTextColor(BLUE);

                videoLink.setOnClickListener(v -> {
                    Intent intent = new Intent(
                            Intent.ACTION_VIEW, Uri.parse(mediaUrl));
                    intent.setDataAndType(
                            Uri.parse(mediaUrl), "video/*");
                    startActivity(intent);
                });

                card.addView(videoLink);
            }
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);

        Button like = makeButton("Like");
        like.setOnClickListener(v -> toggleLike(postId));

        Button comment = makeButton("Comment");
        comment.setOnClickListener(v -> showComments(postId));

        Button share = makeButton("Share");
        share.setOnClickListener(v -> sharePost(text));

        actions.addView(like);
        actions.addView(comment);
        actions.addView(share);

        card.addView(actions);

        Button report = makeButton("Report");
        report.setOnClickListener(v -> reportPost(postId, uid));
        card.addView(report);

        Button block = makeButton("Block User");
        block.setOnClickListener(v -> blockUser(uid));
        card.addView(block);

        feedLayout.addView(card);

        listenLikeCount(postId, like);
    }

    // 3. Like / Unlike

    private void toggleLike(String postId) {

        DocumentReference likeRef = db.collection("posts")
                .document(postId)
                .collection("likes")
                .document(myUid);

        likeRef.get().addOnSuccessListener(doc -> {

            if (doc.exists()) {
                likeRef.delete();
            } else {
                Map<String, Object> data = new HashMap<>();
                data.put("uid", myUid);
                data.put("createdAt", FieldValue.serverTimestamp());
                likeRef.set(data);
            }
        });
    }

    private void listenLikeCount(String postId, Button button) {

        ListenerRegistration registration =
                db.collection("posts")
                        .document(postId)
                        .collection("likes")
                        .addSnapshotListener((snapshot, error) -> {

                            if (snapshot == null) return;

                            button.setText("Like " +
                                    snapshot.size());
                        });

        listeners.add(registration);
    }

    // 4. Comment এবং 5. Reply

    private void showComments(String postId) {

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        EditText input = new EditText(this);
        input.setHint("মন্তব্য লিখুন");

        Button send = makeButton("Send");

        layout.addView(input);
        layout.addView(send);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Comments")
                .setView(layout)
                .setNegativeButton("Close", null)
                .create();

        send.setOnClickListener(v -> {

            String comment = input.getText().toString().trim();

            if (comment.isEmpty()) return;

            if (!isSafeContent(comment)) {
                showMessage("মন্তব্যটি প্রকাশ করা যাচ্ছে না");
                return;
            }

            addComment(postId, comment, "");
            dialog.dismiss();
        });

        dialog.show();

        db.collection("posts")
                .document(postId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(snapshot -> {

                    for (DocumentSnapshot doc :
                            snapshot.getDocuments()) {

                        String name = doc.getString("authorName");
                        String message = doc.getString("text");
                        String commentId = doc.getId();

                        TextView item = makeText(
                                (name == null ? "User" : name)
                                        + ": "
                                        + (message == null ? "" : message),
                                14);

                        item.setOnClickListener(v ->
                                showReplyDialog(postId, commentId));

                        layout.addView(item, 0);
                    }
                });
    }

    private void addComment(
            String postId, String text, String parentId) {

        Map<String, Object> data = new HashMap<>();
        data.put("uid", myUid);
        data.put("authorName", myName);
        data.put("text", text);
        data.put("parentCommentId", parentId);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("posts")
                .document(postId)
                .collection("comments")
                .add(data)
                .addOnSuccessListener(ref -> {

                    if (parentId.isEmpty()) {
                        db.collection("posts")
                                .document(postId)
                                .update("commentsCount",
                                        FieldValue.increment(1));
                    }

                    showMessage("মন্তব্য প্রকাশ হয়েছে");
                });
    }

    private void showReplyDialog(
            String postId, String commentId) {

        EditText input = new EditText(this);
        input.setHint("Reply লিখুন");

        new AlertDialog.Builder(this)
                .setTitle("Reply")
                .setView(input)
                .setPositiveButton("Send", (d, w) -> {

                    String reply = input.getText().toString().trim();

                    if (!reply.isEmpty() && isSafeContent(reply)) {
                        addComment(postId, reply, commentId);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // 6. Share

    private void sharePost(String text) {

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT,
                text == null ? "AYNA Social" : text);

        startActivity(Intent.createChooser(intent, "Share"));
    }

    // 7. Search

    private void showSearch() {

        EditText input = new EditText(this);
        input.setHint("নাম লিখে Search করুন");

        new AlertDialog.Builder(this)
                .setTitle("Search Users")
                .setView(input)
                .setPositiveButton("Search", (d, w) ->
                        searchUsers(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void searchUsers(String name) {

        if (name.isEmpty()) return;

        db.collection("users")
                .orderBy("name")
                .startAt(name)
                .endAt(name + "\uf8ff")
                .limit(20)
                .get()
                .addOnSuccessListener(snapshot -> {

                    LinearLayout results = new LinearLayout(this);
                    results.setOrientation(LinearLayout.VERTICAL);

                    for (DocumentSnapshot doc :
                            snapshot.getDocuments()) {

                        String uid = doc.getId();
                        String username = doc.getString("name");

                        if (uid.equals(myUid)) continue;

                        TextView item = makeText(
                                username == null ? "User" : username, 17);

                        item.setOnClickListener(v ->
                                showProfile(uid));

                        results.addView(item);
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Search Results")
                            .setView(results)
                            .setPositiveButton("Close", null)
                            .show();
                });
    }

    // 8. Profile

    private void showProfile(String uid) {

        if (uid == null) return;

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    String name = doc.getString("name");
                    String email = doc.getString("email");

                    LinearLayout layout = new LinearLayout(this);
                    layout.setOrientation(LinearLayout.VERTICAL);

                    layout.addView(makeText(
                            "নাম: " + (name == null ? "" : name), 18));

                    layout.addView(makeText(
                            "Email: " + (email == null ? "" : email), 14));

                    if (!uid.equals(myUid)) {

                        Button follow = makeButton("Follow / Unfollow");
                        follow.setOnClickListener(v ->
                                toggleFollow(uid));

                        layout.addView(follow);
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Profile")
                            .setView(layout)
                            .setPositiveButton("Close", null)
                            .show();
                });
    }

    // 9. Follow / Unfollow

    private void toggleFollow(String targetUid) {

        if (targetUid == null || targetUid.equals(myUid)) return;

        DocumentReference following = db.collection("users")
                .document(myUid)
                .collection("following")
                .document(targetUid);

        DocumentReference follower = db.collection("users")
                .document(targetUid)
                .collection("followers")
                .document(myUid);

        following.get().addOnSuccessListener(doc -> {

            if (doc.exists()) {

                following.delete();
                follower.delete();

            } else {

                Map<String, Object> data = new HashMap<>();
                data.put("uid", targetUid);
                data.put("createdAt", FieldValue.serverTimestamp());

                db.runBatch(batch -> {
                    batch.set(following, data);
                    batch.set(follower, data);
                });

                sendNotification(targetUid, "আপনাকে Follow করেছেন");
            }
        });
    }

    // 10. Friends

    private void showFriends() {

        db.collection("users")
                .document(myUid)
                .collection("following")
                .get()
                .addOnSuccessListener(snapshot -> {

                    LinearLayout layout = new LinearLayout(this);
                    layout.setOrientation(LinearLayout.VERTICAL);

                    for (DocumentSnapshot doc :
                            snapshot.getDocuments()) {

                        String uid = doc.getId();

                        TextView item = makeText(uid, 15);
                        item.setOnClickListener(v -> showProfile(uid));

                        layout.addView(item);
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Following")
                            .setView(layout)
                            .setPositiveButton("Close", null)
                            .show();
                });
    }

    // 11. Story

    private void loadStories() {

        storyLayout.removeAllViews();

        Button add = makeButton("+ Story");
        add.setOnClickListener(v -> addStory());
        storyLayout.addView(add);

        db.collection("stories")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(snapshot -> {

                    for (DocumentSnapshot doc :
                            snapshot.getDocuments()) {

                        String name = doc.getString("authorName");
                        Button story = makeButton(
                                name == null ? "Story" : name);

                        story.setOnClickListener(v -> {
                            String text = doc.getString("text");

                            new AlertDialog.Builder(this)
                                    .setTitle("Story")
                                    .setMessage(text == null ? "" : text)
                                    .setPositiveButton("Close", null)
                                    .show();
                        });

                        storyLayout.addView(story);
                    }
                });
    }

    private void addStory() {

        EditText input = new EditText(this);
        input.setHint("Story লিখুন");

        new AlertDialog.Builder(this)
                .setTitle("Create Story")
                .setView(input)
                .setPositiveButton("Publish", (d, w) -> {

                    String text = input.getText().toString().trim();

                    if (text.isEmpty() || !isSafeContent(text)) return;

                    Map<String, Object> data = new HashMap<>();
                    data.put("uid", myUid);
                    data.put("authorName", myName);
                    data.put("text", text);
                    data.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("stories").add(data)
                            .addOnSuccessListener(ref -> {
                                showMessage("Story প্রকাশ হয়েছে");
                                loadStories();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // 12. Notifications

    private void sendNotification(String targetUid, String message) {

        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        data.put("fromUid", myUid);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("read", false);

        db.collection("users")
                .document(targetUid)
                .collection("notifications")
                .add(data);
    }

    private void listenNotifications() {

        notificationListener = db.collection("users")
                .document(myUid)
                .collection("notifications")
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) return;

                    // নতুন নোটিফিকেশন পাওয়া গেলে এখানে ডেটা আপডেট হয়।
                });
    }

    private void showNotifications() {

        db.collection("users")
                .document(myUid)
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snapshot -> {

                    LinearLayout layout = new LinearLayout(this);
                    layout.setOrientation(LinearLayout.VERTICAL);

                    for (DocumentSnapshot doc :
                            snapshot.getDocuments()) {

                        String message = doc.getString("message");
                        layout.addView(makeText(
                                message == null ? "Notification" : message,
                                15));
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Notifications")
                            .setView(layout)
                            .setPositiveButton("Close", null)
                            .show();
                });
    }

    // 13. Report

    private void reportPost(String postId, String targetUid) {

        EditText reason = new EditText(this);
        reason.setHint("Report করার কারণ");

        new AlertDialog.Builder(this)
                .setTitle("Report Post")
                .setView(reason)
                .setPositiveButton("Submit", (d, w) -> {

                    Map<String, Object> data = new HashMap<>();
                    data.put("reporterUid", myUid);
                    data.put("targetUid", targetUid);
                    data.put("targetId", postId);
                    data.put("targetType", "post");
                    data.put("reason", reason.getText().toString());
                    data.put("status", "pending");
                    data.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("reports").add(data)
                            .addOnSuccessListener(ref ->
                                    showMessage("Report জমা হয়েছে"));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // 14. Block

    private void blockUser(String targetUid) {

        if (targetUid == null || targetUid.equals(myUid)) return;

        new AlertDialog.Builder(this)
                .setTitle("Block User?")
                .setMessage("এই ব্যবহারকারীকে Block করবেন?")
                .setPositiveButton("Block", (d, w) -> {

                    Map<String, Object> data = new HashMap<>();
                    data.put("uid", targetUid);
                    data.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("users")
                            .document(myUid)
                            .collection("blocked")
                            .document(targetUid)
                            .set(data)
                            .addOnSuccessListener(ref -> {
                                showMessage("ব্যবহারকারী Block হয়েছে");
                                loadBlockedUsers();
                                loadPosts();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadBlockedUsers() {

        db.collection("users")
                .document(myUid)
                .collection("blocked")
                .get()
                .addOnSuccessListener(snapshot -> {

                    blockedUsers.clear();

                    for (DocumentSnapshot doc :
                            snapshot.getDocuments()) {
                        blockedUsers.add(doc.getId());
                    }
                });
    }

    // 15. Basic Content Safety

    private boolean isSafeContent(String text) {

        if (text == null || text.trim().isEmpty()) return false;

        String value = text.toLowerCase();

        String[] blockedWords = {
                "নোংরা গালি",
                "অশ্লীল শব্দ"
        };

        for (String word : blockedWords) {
            if (value.contains(word)) return false;
        }

        return true;
    }

    // 16. Admin Review

    private void showAdminReview() {

        db.collection("users")
                .document(myUid)
                .get()
                .addOnSuccessListener(user -> {

                    Boolean admin = user.getBoolean("isAdmin");

                    if (admin == null || !admin) {
                        showMessage("শুধু Admin এই অংশ দেখতে পারবেন");
                        return;
                    }

                    db.collection("reports")
                            .whereEqualTo("status", "pending")
                            .get()
                            .addOnSuccessListener(snapshot -> {

                                LinearLayout layout =
                                        new LinearLayout(this);

                                layout.setOrientation(
                                        LinearLayout.VERTICAL);

                                for (DocumentSnapshot report :
                                        snapshot.getDocuments()) {

                                    String reason =
                                            report.getString("reason");

                                    TextView item = makeText(
                                            reason == null
                                                    ? "Report"
                                                    : reason, 15);

                                    layout.addView(item);

                                    Button review = makeButton("Mark Reviewed");

                                    review.setOnClickListener(v ->
                                            report.getReference().update(
                                                    "status", "reviewed"));

                                    layout.addView(review);
                                }

                                new AlertDialog.Builder(this)
                                        .setTitle("Admin Review")
                                        .setView(layout)
                                        .setPositiveButton("Close", null)
                                        .show();
                            });
                });
    }

    // 17. Bottom Navigation

    private void createBottomNavigation() {

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.WHITE);

        Button home = makeButton("Home");
        home.setOnClickListener(v -> loadPosts());

        Button friends = makeButton("Friends");
        friends.setOnClickListener(v -> showFriends());

        Button messages = makeButton("Messages");
        messages.setOnClickListener(v -> openChat());

        Button notifications = makeButton("Alerts");
        notifications.setOnClickListener(v -> showNotifications());

        Button creator = makeButton("Creator");
        creator.setOnClickListener(v -> openCreatorStudio());

        nav.addView(home);
        nav.addView(friends);
        nav.addView(messages);
        nav.addView(notifications);
        nav.addView(creator);

        root.addView(nav);
    }

    // Separate Activities

    private void openCreatePost() {
        try {
            startActivity(new Intent(this, CreatePostActivity.class));
        } catch (Exception e) {
            showMessage("CreatePostActivity এখনো তৈরি করা হয়নি");
        }
    }

    private void openChat() {
        try {
            startActivity(new Intent(this, ChatActivity.class));
        } catch (Exception e) {
            showMessage("ChatActivity এখনো তৈরি করা হয়নি");
        }
    }

    private void openCreatorStudio() {
        try {
            startActivity(new Intent(this, CreatorStudioActivity.class));
        } catch (Exception e) {
            showMessage("CreatorStudioActivity এখনো তৈরি করা হয়নি");
        }
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {

        if (feedListener != null) {
            feedListener.remove();
        }

        if (notificationListener != null) {
            notificationListener.remove();
        }

        for (ListenerRegistration listener : listeners) {
            listener.remove();
        }

        listeners.clear();

        super.onDestroy();
    }
}
