package com.mamun.ayna;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends Activity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private String myUid;
    private String myName = "AYNA User";

    private LinearLayout rootLayout;
    private LinearLayout storyLayout;
    private LinearLayout feedLayout;
    private TextView notificationText;

    private final Set<String> blockedUsers = new HashSet<>();
    private final List<ListenerRegistration> likeListeners = new ArrayList<>();

    private ListenerRegistration feedListener;
    private ListenerRegistration notificationListener;

    private boolean blockedUsersLoaded = false;

    private final int BLUE = Color.rgb(23, 105, 170);
    private final int DARK = Color.rgb(35, 40, 45);
    private final int LIGHT = Color.rgb(245, 247, 250);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "আগে লগইন করুন", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        myUid = currentUser.getUid();

        createScreen();
        loadMyProfile();
        loadBlockedUsers();
        listenNotifications();
    }

    private TextView makeText(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setPadding(8, 8, 8, 8);
        return text;
    }

    private Button makeButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private void createScreen() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(LIGHT);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(12, 8, 12, 8);
        header.setBackgroundColor(Color.WHITE);

        TextView logo = makeText("AYNA", 26, BLUE);
        logo.setTypeface(null, Typeface.BOLD);
        header.addView(logo, new LinearLayout.LayoutParams(
                0, -2, 1
        ));

        Button searchButton = makeButton("Search");
        searchButton.setOnClickListener(v -> searchUsers());
        header.addView(searchButton);

        Button profileButton = makeButton("Profile");
        profileButton.setOnClickListener(v -> showProfile(myUid));
        header.addView(profileButton);

        rootLayout.addView(header);

        notificationText = makeText("Notifications", 13, DARK);
        notificationText.setPadding(14, 6, 14, 6);
        rootLayout.addView(notificationText);

        ScrollView mainScroll = new ScrollView(this);
        LinearLayout mainContent = new LinearLayout(this);
        mainContent.setOrientation(LinearLayout.VERTICAL);

        TextView storyTitle = makeText("Stories", 18, DARK);
        storyTitle.setTypeface(null, Typeface.BOLD);
        mainContent.addView(storyTitle);

        storyLayout = new LinearLayout(this);
        storyLayout.setOrientation(LinearLayout.HORIZONTAL);
        storyLayout.setPadding(8, 4, 8, 8);
        mainContent.addView(storyLayout);

        Button addStoryButton = makeButton("+ Add Story");
        addStoryButton.setOnClickListener(v -> addStory());
        mainContent.addView(addStoryButton);

        TextView postTitle = makeText("Create a post", 18, DARK);
        postTitle.setTypeface(null, Typeface.BOLD);
        mainContent.addView(postTitle);

        EditText postInput = new EditText(this);
        postInput.setHint("What's on your mind?");
        postInput.setMinLines(2);
        postInput.setGravity(Gravity.TOP);
        mainContent.addView(postInput);

        Button publishButton = makeButton("Post");
        publishButton.setOnClickListener(v -> {
            String content = postInput.getText().toString().trim();

            if (content.isEmpty()) {
                Toast.makeText(this, "কিছু লিখুন", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isSafeContent(content)) {
                Toast.makeText(this, "এই লেখা পোস্ট করা যাবে না", Toast.LENGTH_LONG).show();
                return;
            }

            publishPost(content);
            postInput.setText("");
        });
        mainContent.addView(publishButton);

        Button mediaButton = makeButton("Photo / Video");
        mediaButton.setOnClickListener(v -> openCreatePost());
        mainContent.addView(mediaButton);

        TextView feedTitle = makeText("News Feed", 20, DARK);
        feedTitle.setTypeface(null, Typeface.BOLD);
        mainContent.addView(feedTitle);

        feedLayout = new LinearLayout(this);
        feedLayout.setOrientation(LinearLayout.VERTICAL);
        mainContent.addView(feedLayout);

        mainScroll.addView(mainContent);

        rootLayout.addView(mainScroll, new LinearLayout.LayoutParams(
                -1, 0, 1
        ));

        createBottomNavigation();
        setContentView(rootLayout);
    }

    private void createBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.WHITE);

        Button home = makeButton("Home");
        home.setOnClickListener(v -> {
            if (feedListener == null) loadPosts();
        });

        Button friends = makeButton("Friends");
        friends.setOnClickListener(v -> showFriends());

        Button messages = makeButton("Messages");
        messages.setOnClickListener(v -> openChat());

        Button alerts = makeButton("Alerts");
        alerts.setOnClickListener(v -> showNotifications());

        Button creator = makeButton("Creator");
        creator.setOnClickListener(v -> openCreatorStudio());

        nav.addView(home, new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(friends, new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(messages, new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(alerts, new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(creator, new LinearLayout.LayoutParams(0, -2, 1));

        rootLayout.addView(nav);
    }

    private void loadMyProfile() {
        db.collection("users").document(myUid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String name = document.getString("name");
                        if (name != null && !name.trim().isEmpty()) {
                            myName = name;
                        }
                    }
                });
    }

    private void loadBlockedUsers() {
        db.collection("users").document(myUid)
                .collection("blocked")
                .get()
                .addOnSuccessListener(snapshot -> {
                    blockedUsers.clear();

                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        blockedUsers.add(document.getId());
                    }

                    blockedUsersLoaded = true;
                    loadPosts();
                    loadStories();
                })
                .addOnFailureListener(error -> {
                    blockedUsersLoaded = true;
                    loadPosts();
                    loadStories();
                    showError("Blocked users লোড করা যায়নি");
                });
    }

    private void publishPost(String content) {
        Map<String, Object> post = new HashMap<>();
        post.put("uid", myUid);
        post.put("authorName", myName);
        post.put("text", content);
        post.put("mediaUrl", "");
        post.put("mediaType", "text");
        post.put("createdAt", FieldValue.serverTimestamp());
        post.put("likesCount", 0);
        post.put("commentsCount", 0);

        db.collection("posts")
                .add(post)
                .addOnSuccessListener(reference ->
                        Toast.makeText(this, "Post published", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(error -> showError("Post করা যায়নি"));
    }

    private void loadPosts() {
        if (!blockedUsersLoaded) return;

        if (feedListener != null) {
            feedListener.remove();
        }

        feedListener = db.collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        showError("Feed লোড করা যায়নি");
                        return;
                    }

                    if (snapshot == null) return;

                    feedLayout.removeAllViews();

                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        String uid = document.getString("uid");

                        if (uid == null || blockedUsers.contains(uid)) {
                            continue;
                        }

                        addPostCard(document);
                    }
                });
    }

    private void addPostCard(DocumentSnapshot document) {
        String postId = document.getId();
        String uid = document.getString("uid");
        String author = document.getString("authorName");
        String content = document.getString("text");
        String mediaUrl = document.getString("mediaUrl");
        String mediaType = document.getString("mediaType");

        if (author == null) author = "AYNA User";
        if (content == null) content = "";
        if (mediaUrl == null) mediaUrl = "";
        if (mediaType == null) mediaType = "text";

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(12, 10, 12, 10);
        card.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(6, 6, 6, 8);
        card.setLayoutParams(cardParams);

        TextView authorText = makeText(author, 17, BLUE);
        authorText.setTypeface(null, Typeface.BOLD);
        authorText.setOnClickListener(v -> {
            if (uid != null) showProfile(uid);
        });
        card.addView(authorText);

        TextView postText = makeText(content, 15, DARK);
        card.addView(postText);

        if (!mediaUrl.isEmpty()) {
            if ("image".equals(mediaType)) {
                ImageView image = new ImageView(this);
                image.setAdjustViewBounds(true);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);

                card.addView(image, new LinearLayout.LayoutParams(
                        -1, 350
                ));

                Glide.with(this).load(mediaUrl).into(image);

            } else if ("video".equals(mediaType)) {
                Button videoButton = makeButton("Play Video");
                videoButton.setOnClickListener(v -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.parse(mediaUrl), "video/*");
                        startActivity(intent);
                    } catch (Exception e) {
                        showError("ভিডিও চালানো যাচ্ছে না");
                    }
                });
                card.addView(videoButton);
            }
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button like = makeButton("Like");
        like.setOnClickListener(v -> toggleLike(postId));

        Button comment = makeButton("Comment");
        comment.setOnClickListener(v -> showComments(postId));

        Button share = makeButton("Share");
        share.setOnClickListener(v -> sharePost(content));

        actions.addView(like, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(comment, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(share, new LinearLayout.LayoutParams(0, -2, 1));

        card.addView(actions);

        TextView likeCount = makeText("Likes: 0", 13, DARK);
        card.addView(likeCount);
        listenLikeCount(postId, likeCount);

        LinearLayout moreActions = new LinearLayout(this);

        Button report = makeButton("Report");
        report.setOnClickListener(v -> reportPost(postId, uid));

        Button block = makeButton("Block");
        block.setOnClickListener(v -> {
            if (uid != null && !uid.equals(myUid)) {
                blockUser(uid);
            }
        });

        moreActions.addView(report);
        moreActions.addView(block);
        card.addView(moreActions);

        feedLayout.addView(card);
    }

    private void toggleLike(String postId) {
        db.collection("posts").document(postId)
                .collection("likes").document(myUid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        document.getReference().delete()
                                .addOnFailureListener(error ->
                                        showError("Unlike করা যায়নি"));
                    } else {
                        Map<String, Object> like = new HashMap<>();
                        like.put("uid", myUid);
                        like.put("createdAt", FieldValue.serverTimestamp());

                        db.collection("posts").document(postId)
                                .collection("likes").document(myUid)
                                .set(like)
                                .addOnFailureListener(error ->
                                        showError("Like করা যায়নি"));
                    }
                })
                .addOnFailureListener(error -> showError("Like যাচাই করা যায়নি"));
    }

    private void listenLikeCount(String postId, TextView countText) {
        ListenerRegistration registration =
                db.collection("posts").document(postId)
                        .collection("likes")
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null || snapshot == null) return;
                            countText.setText("Likes: " + snapshot.size());
                        });

        likeListeners.add(registration);
    }

    private void showComments(String postId) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(12, 8, 12, 8);

        ScrollView scroll = new ScrollView(this);
        LinearLayout commentsLayout = new LinearLayout(this);
        commentsLayout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(commentsLayout);

        layout.addView(scroll, new LinearLayout.LayoutParams(
                -1, 0, 1
        ));

        EditText input = new EditText(this);
        input.setHint("Write a comment...");
        layout.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Comments")
                .setView(layout)
                .setNegativeButton("Close", null)
                .create();

        Button send = makeButton("Send");
        layout.addView(send);

        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();

            if (text.isEmpty()) return;

            if (!isSafeContent(text)) {
                Toast.makeText(this, "এই মন্তব্য পাঠানো যাবে না",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            addComment(postId, text, "");
            input.setText("");
        });

        db.collection("posts").document(postId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(snapshot -> {
                    commentsLayout.removeAllViews();

                    Map<String, List<DocumentSnapshot>> replies = new HashMap<>();

                    for (DocumentSnapshot item : snapshot.getDocuments()) {
                        String parent = item.getString("parentCommentId");

                        if (parent != null && !parent.isEmpty()) {
                            if (!replies.containsKey(parent)) {
                                replies.put(parent, new ArrayList<>());
                            }
                            replies.get(parent).add(item);
                        }
                    }

                    for (DocumentSnapshot item : snapshot.getDocuments()) {
                        String parent = item.getString("parentCommentId");

                        if (parent != null && !parent.isEmpty()) continue;

                        addCommentView(
                                commentsLayout,
                                postId,
                                item,
                                false
                        );

                        List<DocumentSnapshot> childReplies =
                                replies.get(item.getId());

                        if (childReplies != null) {
                            for (DocumentSnapshot reply : childReplies) {
                                addCommentView(
                                        commentsLayout,
                                        postId,
                                        reply,
                                        true
                                );
                            }
                        }
                    }
                })
                .addOnFailureListener(error ->
                        showError("Comments লোড করা যায়নি"));

        dialog.show();
    }

    private void addCommentView(
            LinearLayout parent,
            String postId,
            DocumentSnapshot item,
            boolean isReply
    ) {
        String author = item.getString("authorName");
        String text = item.getString("text");

        if (author == null) author = "User";
        if (text == null) text = "";

        TextView commentText = makeText(
                (isReply ? "     ↳ " : "") + author + ": " + text,
                14,
                DARK
        );

        parent.addView(commentText);

        if (!isReply) {
            Button replyButton = makeButton("Reply");
            replyButton.setOnClickListener(v ->
                    showReplyDialog(postId, item.getId()));
            parent.addView(replyButton);
        }
    }

    private void addComment(String postId, String text, String parentId) {
        Map<String, Object> comment = new HashMap<>();
        comment.put("uid", myUid);
        comment.put("authorName", myName);
        comment.put("text", text);
        comment.put("parentCommentId", parentId);
        comment.put("createdAt", FieldValue.serverTimestamp());

        db.collection("posts").document(postId)
                .collection("comments")
                .add(comment)
                .addOnSuccessListener(reference -> {
                    if (parentId == null || parentId.isEmpty()) {
                        db.collection("posts").document(postId)
                                .update("commentsCount", FieldValue.increment(1));
                    }

                    Toast.makeText(this, "Comment sent",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error ->
                        showError("Comment পাঠানো যায়নি"));
    }

    private void showReplyDialog(String postId, String commentId) {
        EditText input = new EditText(this);
        input.setHint("Write a reply");

        new AlertDialog.Builder(this)
                .setTitle("Reply")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (dialog, which) -> {
                    String text = input.getText().toString().trim();

                    if (text.isEmpty()) return;

                    if (!isSafeContent(text)) {
                        showError("এই উত্তর পাঠানো যাবে না");
                        return;
                    }

                    addComment(postId, text, commentId);
                })
                .show();
    }

    private void sharePost(String content) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, content);

        try {
            startActivity(Intent.createChooser(intent, "Share post"));
        } catch (Exception e) {
            showError("Share করা যাচ্ছে না");
        }
    }

    private void searchUsers() {
        EditText input = new EditText(this);
        input.setHint("Search user name");

        new AlertDialog.Builder(this)
                .setTitle("Search Users")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Search", (dialog, which) -> {
                    String name = input.getText().toString().trim();

                    if (name.isEmpty()) return;

                    db.collection("users")
                            .orderBy("name")
                            .startAt(name)
                            .endAt(name + "\uf8ff")
                            .limit(30)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                LinearLayout results = new LinearLayout(this);
                                results.setOrientation(LinearLayout.VERTICAL);

                                for (DocumentSnapshot user : snapshot.getDocuments()) {
                                    String uid = user.getId();
                                    String userName = user.getString("name");

                                    if (userName == null) userName = "User";

                                    if (blockedUsers.contains(uid)) continue;

                                    Button item = makeButton(userName);
                                    item.setOnClickListener(v -> showProfile(uid));
                                    results.addView(item);
                                }

                                new AlertDialog.Builder(this)
                                        .setTitle("Search Results")
                                        .setView(results)
                                        .setPositiveButton("Close", null)
                                        .show();
                            })
                            .addOnFailureListener(error ->
                                    showError("Search করা যায়নি"));
                })
                .show();
    }

    private void showProfile(String uid) {
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        showError("Profile পাওয়া যায়নি");
                        return;
                    }

                    String name = document.getString("name");
                    String email = document.getString("email");

                    if (name == null) name = "AYNA User";
                    if (email == null) email = "";

                    LinearLayout layout = new LinearLayout(this);
                    layout.setPadding(16, 12, 16, 12);
                    layout.setOrientation(LinearLayout.VERTICAL);

                    layout.addView(makeText("Name: " + name, 17, DARK));
                    layout.addView(makeText("Email: " + email, 14, DARK));

                    AlertDialog dialog = new AlertDialog.Builder(this)
                            .setTitle("Profile")
                            .setView(layout)
                            .setNegativeButton("Close", null)
                            .create();

                    if (!uid.equals(myUid)) {
                        Button follow = makeButton("Follow / Unfollow");
                        layout.addView(follow);
                        follow.setOnClickListener(v -> toggleFollow(uid));
                    }

                    Object adminValue = document.get("isAdmin");
                    if (Boolean.TRUE.equals(adminValue)) {
                        Button admin = makeButton("Admin Review");
                        layout.addView(admin);
                        admin.setOnClickListener(v -> {
                            dialog.dismiss();
                            showAdminReview();
                        });
                    }

                    dialog.show();
                })
                .addOnFailureListener(error -> showError("Profile লোড করা যায়নি"));
    }

    private void toggleFollow(String targetUid) {
        if (targetUid.equals(myUid)) return;

        db.collection("users").document(myUid)
                .collection("following").document(targetUid)
                .get()
                .addOnSuccessListener(document -> {
                    boolean alreadyFollowing = document.exists();

                    db.runBatch(batch -> {
                        if (alreadyFollowing) {
                            batch.delete(
                                    db.collection("users").document(myUid)
                                            .collection("following").document(targetUid)
                            );

                            batch.delete(
                                    db.collection("users").document(targetUid)
                                            .collection("followers").document(myUid)
                            );
                        } else {
                            Map<String, Object> data = new HashMap<>();
                            data.put("uid", targetUid);
                            data.put("createdAt", FieldValue.serverTimestamp());

                            batch.set(
                                    db.collection("users").document(myUid)
                                            .collection("following").document(targetUid),
                                    data
                            );

                            batch.set(
                                    db.collection("users").document(targetUid)
                                            .collection("followers").document(myUid),
                                    data
                            );
                        }

                        return null;
                    }).addOnSuccessListener(unused ->
                            Toast.makeText(this,
                                    alreadyFollowing ? "Unfollow করা হয়েছে" : "Follow করা হয়েছে",
                                    Toast.LENGTH_SHORT).show()
                    ).addOnFailureListener(error ->
                            showError("Follow পরিবর্তন করা যায়নি"));
                })
                .addOnFailureListener(error -> showError("Follow যাচাই করা যায়নি"));
    }

    private void showFriends() {
        db.collection("users").document(myUid)
                .collection("following")
                .get()
                .addOnSuccessListener(snapshot -> {
                    LinearLayout layout = new LinearLayout(this);
                    layout.setOrientation(LinearLayout.VERTICAL);

                    for (DocumentSnapshot item : snapshot.getDocuments()) {
                        String uid = item.getId();

                        Button button = makeButton(uid);
                        button.setOnClickListener(v -> showProfile(uid));
                        layout.addView(button);
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Following")
                            .setView(layout)
                            .setPositiveButton("Close", null)
                            .show();
                })
                .addOnFailureListener(error -> showError("Friends লোড করা যায়নি"));
    }

    private void loadStories() {
        db.collection("stories")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(snapshot -> {
                    storyLayout.removeAllViews();

                    for (DocumentSnapshot item : snapshot.getDocuments()) {
                        String uid = item.getString("uid");
                        String text = item.getString("text");

                        if (uid == null || blockedUsers.contains(uid)) continue;
                        if (text == null) text = "Story";

                        Button story = makeButton(text);
                        story.setOnClickListener(v ->
                                new AlertDialog.Builder(this)
                                        .setTitle("Story")
                                        .setMessage(text)
                                        .setPositiveButton("Close", null)
                                        .show());

                        storyLayout.addView(story);
                    }
                });
    }

    private void addStory() {
        EditText input = new EditText(this);
        input.setHint("Write your story");

        new AlertDialog.Builder(this)
                .setTitle("Add Story")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Publish", (dialog, which) -> {
                    String text = input.getText().toString().trim();

                    if (text.isEmpty()) return;

                    if (!isSafeContent(text)) {
                        showError("এই Story প্রকাশ করা যাবে না");
                        return;
                    }

                    Map<String, Object> story = new HashMap<>();
                    story.put("uid", myUid);
                    story.put("authorName", myName);
                    story.put("text", text);
                    story.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("stories")
                            .add(story)
                            .addOnSuccessListener(reference -> loadStories())
                            .addOnFailureListener(error -> showError("Story প্রকাশ করা যায়নি"));
                })
                .show();
    }

    private void listenNotifications() {
        notificationListener = db.collection("users")
                .document(myUid)
                .collection("notifications")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;

                    int unread = 0;

                    for (DocumentSnapshot item : snapshot.getDocuments()) {
                        Boolean read = item.getBoolean("read");
                        if (!Boolean.TRUE.equals(read)) unread++;
                    }

                    notificationText.setText("Notifications: " + unread);
                });
    }

    private void sendNotification(
            String targetUid,
            String message,
            String type
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        data.put("type", type);
        data.put("fromUid", myUid);
        data.put("read", false);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users").document(targetUid)
                .collection("notifications")
                .add(data);
    }

    private void showNotifications() {
        db.collection("users").document(myUid)
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snapshot -> {
                    LinearLayout layout = new LinearLayout(this);
                    layout.setOrientation(LinearLayout.VERTICAL);

                    for (DocumentSnapshot item : snapshot.getDocuments()) {
                        String message = item.getString("message");
                        if (message == null) message = "Notification";

                        TextView text = makeText(message, 15, DARK);
                        layout.addView(text);

                        Boolean read = item.getBoolean("read");

                        if (!Boolean.TRUE.equals(read)) {
                            item.getReference().update("read", true);
                        }
                    }

                    ScrollView scroll = new ScrollView(this);
                    scroll.addView(layout);

                    new AlertDialog.Builder(this)
                            .setTitle("Notifications")
                            .setView(scroll)
                            .setPositiveButton("Close", null)
                            .show();
                })
                .addOnFailureListener(error ->
                        showError("Notifications লোড করা যায়নি"));
    }

    private void reportPost(String postId, String targetUid) {
        EditText input = new EditText(this);
        input.setHint("Report reason");

        new AlertDialog.Builder(this)
                .setTitle("Report Post")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Submit", (dialog, which) -> {
                    String reason = input.getText().toString().trim();

                    if (reason.isEmpty()) {
                        showError("কারণ লিখুন");
                        return;
                    }

                    Map<String, Object> report = new HashMap<>();
                    report.put("postId", postId);
                    report.put("reportedUid", targetUid);
                    report.put("reporterUid", myUid);
                    report.put("reason", reason);
                    report.put("status", "pending");
                    report.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("reports")
                            .add(report)
                            .addOnSuccessListener(reference ->
                                    Toast.makeText(this, "Report submitted",
                                            Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(error ->
                                    showError("Report পাঠানো যায়নি"));
                })
                .show();
    }

    private void blockUser(String targetUid) {
        if (targetUid == null || targetUid.equals(myUid)) return;

        Map<String, Object> data = new HashMap<>();
        data.put("uid", targetUid);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users").document(myUid)
                .collection("blocked").document(targetUid)
                .set(data)
                .addOnSuccessListener(unused -> {
                    blockedUsers.add(targetUid);
                    loadPosts();
                    loadStories();

                    Toast.makeText(this, "User blocked",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> showError("Block করা যায়নি"));
    }

    private boolean isSafeContent(String text) {
        if (text == null || text.trim().isEmpty()) return false;

        String lower = text.toLowerCase();

        // এটি শুধু সাধারণ শব্দ-ভিত্তিক প্রাথমিক ফিল্টার।
        // পূর্ণাঙ্গ Content Moderation নয়।
        String[] blockedWords = {
                "নোংরা গালি",
                "অশ্লীল শব্দ"
        };

        for (String word : blockedWords) {
            if (lower.contains(word.toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    private void showAdminReview() {
        db.collection("users").document(myUid)
                .get()
                .addOnSuccessListener(user -> {
                    if (!Boolean.TRUE.equals(user.getBoolean("isAdmin"))) {
                        showError("Admin permission নেই");
                        return;
                    }

                    db.collection("reports")
                            .whereEqualTo("status", "pending")
                            .limit(50)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                LinearLayout layout = new LinearLayout(this);
                                layout.setOrientation(LinearLayout.VERTICAL);

                                for (DocumentSnapshot report : snapshot.getDocuments()) {
                                    String reason = report.getString("reason");
                                    String postId = report.getString("postId");

                                    if (reason == null) reason = "No reason";

                                    TextView details = makeText(
                                            "Post: " + postId + "\nReason: " + reason,
                                            14,
                                            DARK
                                    );
                                    layout.addView(details);

                                    Button reviewed = makeButton("Mark Reviewed");
                                    reviewed.setOnClickListener(v ->
                                            report.getReference()
                                                    .update("status", "reviewed")
                                                    .addOnSuccessListener(unused -> {
                                                        layout.removeView(details);
                                                        layout.removeView(reviewed);
                                                    })
                                                    .addOnFailureListener(error ->
                                                            showError("Update করা যায়নি"))
                                    );
                                    layout.addView(reviewed);
                                }

                                ScrollView scroll = new ScrollView(this);
                                scroll.addView(layout);

                                new AlertDialog.Builder(this)
                                        .setTitle("Admin Review")
                                        .setView(scroll)
                                        .setPositiveButton("Close", null)
                                        .show();
                            })
                            .addOnFailureListener(error ->
                                    showError("Reports লোড করা যায়নি"));
                })
                .addOnFailureListener(error -> showError("Admin যাচাই করা যায়নি"));
    }

    private void openCreatePost() {
        try {
            startActivity(new Intent(this, CreatePostActivity.class));
        } catch (Exception e) {
            showError("CreatePostActivity পাওয়া যায়নি");
        }
    }

    private void openChat() {
        try {
            startActivity(new Intent(this, ChatActivity.class));
        } catch (Exception e) {
            showError("ChatActivity পাওয়া যায়নি");
        }
    }

    private void openCreatorStudio() {
        try {
            startActivity(new Intent(this, CreatorStudioActivity.class));
        } catch (Exception e) {
            showError("CreatorStudioActivity পাওয়া যায়নি");
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (feedListener != null) {
            feedListener.remove();
            feedListener = null;
        }

        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }

        for (ListenerRegistration listener : likeListeners) {
            if (listener != null) listener.remove();
        }

        likeListeners.clear();
    }
}
