package com.mamun.ayna;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.*;

/**
 * AYNA Social - Home screen.
 * Firebase collections used:
 * users, posts, stories, notifications, friendRequests, reports.
 *
 * NOTE: This is a client-side implementation. Enforce admin permissions,
 * moderation, ownership and all writes with Firestore/Storage Security Rules
 * and trusted server-side code before production release.
 */
public class HomeActivity extends Activity {
    private static final int PICK_POST_MEDIA = 4101;
    private static final int PICK_STORY_MEDIA = 4102;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int BLUE = Color.rgb(23, 105, 170);

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private String uid, currentName = "AYNA User";
    private boolean isAdmin = false;

    private LinearLayout root, feed, storiesBox;
    private EditText postInput;
    private Uri selectedPostUri, selectedStoryUri;
    private String selectedPostType = "image", selectedStoryType = "image";
    private String pendingStoryText = "";
    private final Set<String> blocked = new HashSet<>();
    private ListenerRegistration feedListener, notificationListener, storyListener;
    private final Map<String, ListenerRegistration> likeListeners = new HashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        if (auth.getCurrentUser() == null) {
            toast("প্রথমে লগইন করুন");
            finish();
            return;
        }
        uid = auth.getCurrentUser().getUid();
        buildHome();
        loadProfile();
        loadBlockedUsers();
        listenNotifications();
        loadStories();
        loadFeed();
    }

    private void buildHome() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 251));

        LinearLayout header = row();
        header.setPadding(14, 12, 14, 12);
        TextView logo = text("আয়না  AYNA", 25, BLUE);
        logo.setTypeface(null, 1);
        header.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));
        addButton(header, "খুঁজুন", v -> searchUsers());
        addButton(header, "প্রোফাইল", v -> showProfile());
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        feed = new LinearLayout(this);
        feed.setOrientation(LinearLayout.VERTICAL);
        feed.setPadding(10, 6, 10, 12);
        scroll.addView(feed);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = row();
        nav.setPadding(3, 6, 3, 6);
        addButton(nav, "হোম", v -> { loadFeed(); loadStories(); });
        addButton(nav, "বন্ধু", v -> showFriends());
        addButton(nav, "বার্তা", v -> openActivity("ChatActivity"));
        addButton(nav, "নোটিফাই", v -> showNotifications());
        addButton(nav, "Creator", v -> openActivity("CreatorStudioActivity"));
        root.addView(nav);
        setContentView(root);
    }

    private void loadProfile() {
        db.collection("users").document(uid).get().addOnSuccessListener(d -> {
            if (!d.exists()) return;
            String n = d.getString("name");
            if (n != null && !n.trim().isEmpty()) currentName = n;
            Boolean a = d.getBoolean("isAdmin");
            isAdmin = a != null && a;
        });
    }

    private void showProfile() {
        db.collection("users").document(uid).get().addOnSuccessListener(d -> {
            String name = d.getString("name");
            String email = d.getString("email");
            String info = "নাম: " + (name == null ? currentName : name)
                    + "\nইমেইল: " + (email == null ? "" : email);
            AlertDialog.Builder b = new AlertDialog.Builder(this)
                    .setTitle("আমার প্রোফাইল")
                    .setMessage(info)
                    .setPositiveButton("বন্ধ", null)
                    .setNegativeButton("লগআউট", (x, w) -> logout());
            if (isAdmin) b.setNeutralButton("Admin Review", (x, w) -> showAdminReview());
            b.show();
        }).addOnFailureListener(e -> toast("প্রোফাইল লোড হয়নি"));
    }

    private void logout() {
        auth.signOut();
        Intent i = new Intent();
        try {
            i.setClass(this, Class.forName(getPackageName() + ".LoginActivity"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        } catch (Exception e) {
            finish();
        }
    }

    private void searchUsers() {
        EditText input = new EditText(this);
        input.setHint("নামের অংশ লিখুন");
        new AlertDialog.Builder(this).setTitle("ব্যবহারকারী খুঁজুন").setView(input)
                .setPositiveButton("খুঁজুন", (d, w) -> {
                    String q = input.getText().toString().trim();
                    if (q.isEmpty()) return;
                    db.collection("users").orderBy("name").startAt(q).endAt(q + "\uf8ff")
                            .limit(30).get().addOnSuccessListener(s -> {
                                ArrayList<String> labels = new ArrayList<>();
                                ArrayList<DocumentSnapshot> docs = new ArrayList<>();
                                for (DocumentSnapshot doc : s) {
                                    if (doc.getId().equals(uid)) continue;
                                    String n = doc.getString("name");
                                    labels.add((n == null ? "AYNA User" : n) + "  •  " + doc.getId());
                                    docs.add(doc);
                                }
                                if (docs.isEmpty()) { toast("কাউকে পাওয়া যায়নি"); return; }
                                new AlertDialog.Builder(this).setTitle("ফলাফল")
                                        .setItems(labels.toArray(new String[0]), (a, index) -> {
                                            DocumentSnapshot chosen = docs.get(index);
                                            showUserActions(chosen.getId(), chosen.getString("name"));
                                        }).show();
                            }).addOnFailureListener(e -> toast("Search ব্যর্থ হয়েছে"));
                }).setNegativeButton("বাতিল", null).show();
    }

    private void showUserActions(String target, String name) {
        String[] actions = {"Follow / Unfollow", "Friend Request", "Block User", "Report User"};
        new AlertDialog.Builder(this).setTitle(name == null ? "ব্যবহারকারী" : name)
                .setItems(actions, (d, which) -> {
                    if (which == 0) toggleFollow(target);
                    if (which == 1) sendFriendRequest(target);
                    if (which == 2) confirmBlock(target);
                    if (which == 3) reportUser(target);
                }).show();
    }

    private void buildComposer() {
        LinearLayout box = card();
        TextView heading = text("নতুন পোস্ট", 18, BLUE);
        box.addView(heading);
        postInput = new EditText(this);
        postInput.setHint("আপনার মনের কথা লিখুন...");
        postInput.setMinLines(2);
        postInput.setGravity(Gravity.TOP);
        box.addView(postInput, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = row();
        addButton(actions, "ছবি", v -> pickMedia("image", PICK_POST_MEDIA));
        addButton(actions, "ভিডিও", v -> pickMedia("video", PICK_POST_MEDIA));
        addButton(actions, "পোস্ট", v -> createPost());
        box.addView(actions);
        feed.addView(box);
    }

    private void createPost() {
        String body = postInput == null ? "" : postInput.getText().toString().trim();
        if (!isSafeContent(body)) { toast("এই লেখা প্রকাশ করা যাচ্ছে না"); return; }
        if (body.isEmpty() && selectedPostUri == null) { toast("লেখা বা ছবি/ভিডিও যোগ করুন"); return; }
        if (selectedPostUri == null) {
            publishPost(body, null, null);
        } else {
            uploadMedia(selectedPostUri, "posts/" + uid + "/" + UUID.randomUUID(),
                    url -> publishPost(body, url, selectedPostType));
        }
    }

    private void publishPost(String body, String mediaUrl, String mediaType) {
        Map<String, Object> p = new HashMap<>();
        p.put("userId", uid);
        p.put("userName", currentName);
        p.put("content", body);
        p.put("mediaUrl", mediaUrl);
        p.put("mediaType", mediaType);
        p.put("timestamp", FieldValue.serverTimestamp());
        p.put("likesCount", 0);
        p.put("commentsCount", 0);
        p.put("status", "active");
        db.collection("posts").add(p).addOnSuccessListener(r -> {
            if (postInput != null) postInput.setText("");
            selectedPostUri = null;
            toast("পোস্ট প্রকাশ হয়েছে");
        }).addOnFailureListener(e -> toast("পোস্ট প্রকাশ হয়নি"));
    }

    private void pickMedia(String type, int request) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType(type.equals("video") ? "video/*" : "image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        if (request == PICK_POST_MEDIA) selectedPostType = type;
        else selectedStoryType = type;
        startActivityForResult(Intent.createChooser(i, "মিডিয়া নির্বাচন"), request);
    }

    @Override protected void onActivityResult(int request, int result, @Nullable Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == PICK_POST_MEDIA) {
            selectedPostUri = data.getData();
            toast("মিডিয়া নির্বাচন হয়েছে");
        } else if (request == PICK_STORY_MEDIA) {
            selectedStoryUri = data.getData();
            uploadMedia(selectedStoryUri, "stories/" + uid + "/" + UUID.randomUUID(),
                    url -> publishStory(pendingStoryText, url, selectedStoryType));
        }
    }

    private interface UploadDone { void done(String url); }

    private void uploadMedia(Uri uri, String path, UploadDone done) {
        StorageReference ref = storage.getReference().child(path);
        ref.putFile(uri).continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException() != null
                    ? task.getException() : new Exception("Upload failed");
            return ref.getDownloadUrl();
        }).addOnSuccessListener(url -> done.done(url.toString()))
                .addOnFailureListener(e -> toast("মিডিয়া আপলোড ব্যর্থ"));
    }

    private void createStory() {
        EditText input = new EditText(this);
        input.setHint("আজকের গল্প লিখুন");
        new AlertDialog.Builder(this).setTitle("Text Story").setView(input)
                .setPositiveButton("প্রকাশ", (d, w) -> {
                    String s = input.getText().toString().trim();
                    if (s.isEmpty() || !isSafeContent(s)) { toast("গল্পের লেখা ঠিক করুন"); return; }
                    publishStory(s, null, "text");
                }).setNegativeButton("বাতিল", null).show();
    }

    private void showStoryMenu() {
        new AlertDialog.Builder(this).setTitle("Story তৈরি করুন")
                .setItems(new String[]{"Text Story", "Photo Story", "Video Story"},
                        (d, which) -> {
                            if (which == 0) createStory();
                            else {
                                selectedStoryUri = null;
                                pickMedia(which == 1 ? "image" : "video", PICK_STORY_MEDIA);
                            }
                        }).show();
    }

    private void publishStory(String body, String mediaUrl, String type) {
        Map<String, Object> s = new HashMap<>();
        s.put("userId", uid);
        s.put("userName", currentName);
        s.put("text", body);
        s.put("mediaUrl", mediaUrl);
        s.put("mediaType", type);
        s.put("createdAt", FieldValue.serverTimestamp());
        s.put("expiresAt", System.currentTimeMillis() + DAY_MS);
        db.collection("stories").add(s).addOnSuccessListener(r -> toast("Story প্রকাশ হয়েছে"))
                .addOnFailureListener(e -> toast("Story প্রকাশ হয়নি"));
    }

    private void loadStories() {
        if (storyListener != null) storyListener.remove();
        storyListener = db.collection("stories").orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(60).addSnapshotListener((snap, err) -> {
                    if (err != null || snap == null || feed == null) return;
                    if (storiesBox != null) feed.removeView(storiesBox);
                    storiesBox = card();
                    LinearLayout top = row();
                    top.addView(text("Stories", 18, BLUE), new LinearLayout.LayoutParams(0, -2, 1));
                    addButton(top, "+ Story", v -> showStoryMenu());
                    storiesBox.addView(top);
                    long now = System.currentTimeMillis();
                    for (DocumentSnapshot d : snap) {
                        Long expiry = d.getLong("expiresAt");
                        String author = d.getString("userId");
                        if (expiry != null && expiry < now) continue;
                        if (author != null && blocked.contains(author)) continue;
                        String name = d.getString("userName");
                        String body = d.getString("text");
                        String media = d.getString("mediaUrl");
                        String type = d.getString("mediaType");
                        TextView item = text((name == null ? "User" : name) + ": "
                                + (body == null ? "" : body), 14, Color.DKGRAY);
                        storiesBox.addView(item);
                        if (media != null && !media.isEmpty()) {
                            addButton(storiesBox, "মিডিয়া দেখুন", v -> openMedia(media, type));
                        }
                    }
                    feed.addView(storiesBox, Math.min(1, feed.getChildCount()));
                });
    }

    private void loadFeed() {
        if (feedListener != null) feedListener.remove();
        feedListener = db.collection("posts").orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50).addSnapshotListener((snap, err) -> {
                    if (err != null || snap == null || feed == null) return;
                    clearLikeListeners();
                    feed.removeAllViews();
                    buildComposer();
                    if (storiesBox != null) { feed.addView(storiesBox); }
                    for (DocumentSnapshot d : snap) {
                        String author = d.getString("userId");
                        if (author == null || blocked.contains(author)) continue;
                        if ("removed".equals(d.getString("status"))) continue;
                        addPostCard(d);
                    }
                });
    }

    private void addPostCard(DocumentSnapshot d) {
        String postId = d.getId();
        String authorId = d.getString("userId");
        String author = d.getString("userName");
        String content = d.getString("content");
        String media = d.getString("mediaUrl");
        String mediaType = d.getString("mediaType");

        LinearLayout box = card();
        TextView title = text(author == null ? "AYNA User" : author, 16, BLUE);
        title.setTypeface(null, 1);
        box.addView(title);
        if (content != null && !content.isEmpty()) box.addView(text(content, 16, Color.BLACK));
        if (media != null && !media.isEmpty()) {
            if ("image".equals(mediaType)) {
                ImageView image = new ImageView(this);
                image.setAdjustViewBounds(true);
                image.setMaxHeight(500);
                box.addView(image);
                Glide.with(this).load(media).into(image);
                image.setOnClickListener(v -> openMedia(media, mediaType));
            } else addButton(box, "ভিডিও দেখুন", v -> openMedia(media, mediaType));
        }

        LinearLayout actions = row();
        addButton(actions, "Like", v -> toggleLike(postId, authorId));
        addButton(actions, "Comment", v -> showComments(postId));
        addButton(actions, "Share", v -> sharePost(postId, content));
        addButton(actions, "Report", v -> reportPost(postId, authorId));
        box.addView(actions);
        feed.addView(box);
        listenLikes(postId, title);
    }

    private void listenLikes(String postId, TextView title) {
        ListenerRegistration old = likeListeners.remove(postId);
        if (old != null) old.remove();
        ListenerRegistration l = db.collection("posts").document(postId).collection("likes")
                .addSnapshotListener((s, e) -> {
                    if (e == null && s != null) title.setText(title.getText().toString().split("  •")[0]
                            + "  • " + s.size() + " likes");
                });
        likeListeners.put(postId, l);
    }

    private void toggleLike(String postId, String authorId) {
        DocumentReference like = db.collection("posts").document(postId)
                .collection("likes").document(uid);
        db.runTransaction(t -> {
            DocumentSnapshot s = t.get(like);
            if (s.exists()) t.delete(like);
            else t.set(like, Collections.singletonMap("createdAt", FieldValue.serverTimestamp()));
            return !s.exists();
        }).addOnSuccessListener(added -> {
            if (added && authorId != null && !authorId.equals(uid))
                sendNotification(authorId, "like", postId, currentName + " আপনার পোস্টে Like দিয়েছেন");
        }).addOnFailureListener(e -> toast("Like আপডেট হয়নি"));
    }

    private void showComments(String postId) {
        db.collection("posts").document(postId).collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING).limit(100).get()
                .addOnSuccessListener(s -> {
                    ArrayList<String> labels = new ArrayList<>();
                    ArrayList<String> ids = new ArrayList<>();
                    for (DocumentSnapshot d : s) {
                        String name = d.getString("userName");
                        String body = d.getString("text");
                        labels.add((name == null ? "User" : name) + ": " + (body == null ? "" : body));
                        ids.add(d.getId());
                    }
                    String[] items = labels.toArray(new String[0]);
                    new AlertDialog.Builder(this).setTitle("Comments")
                            .setItems(items, (a, index) -> promptReply(postId, ids.get(index)))
                            .setPositiveButton("নতুন Comment", (a, w) -> promptComment(postId))
                            .setNegativeButton("বন্ধ", null).show();
                }).addOnFailureListener(e -> toast("Comment লোড হয়নি"));
    }

    private void promptComment(String postId) {
        EditText input = new EditText(this);
        input.setHint("Comment লিখুন");
        new AlertDialog.Builder(this).setTitle("Comment").setView(input)
                .setPositiveButton("পাঠান", (d, w) -> addComment(postId, null,
                        input.getText().toString().trim()))
                .setNegativeButton("বাতিল", null).show();
    }

    private void promptReply(String postId, String commentId) {
        EditText input = new EditText(this);
        input.setHint("Reply লিখুন");
        new AlertDialog.Builder(this).setTitle("Reply").setView(input)
                .setPositiveButton("পাঠান", (d, w) -> addComment(postId, commentId,
                        input.getText().toString().trim()))
                .setNegativeButton("বাতিল", null).show();
    }

    private void addComment(String postId, @Nullable String parentId, String body) {
        if (body.isEmpty() || !isSafeContent(body)) { toast("Comment গ্রহণযোগ্য নয়"); return; }
        Map<String, Object> c = new HashMap<>();
        c.put("userId", uid);
        c.put("userName", currentName);
        c.put("text", body);
        c.put("parentCommentId", parentId);
        c.put("createdAt", FieldValue.serverTimestamp());
        db.collection("posts").document(postId).collection("comments").add(c)
                .addOnSuccessListener(r -> {
                    if (parentId == null) db.collection("posts").document(postId)
                            .update("commentsCount", FieldValue.increment(1));
                    toast(parentId == null ? "Comment যোগ হয়েছে" : "Reply যোগ হয়েছে");
                }).addOnFailureListener(e -> toast("Comment পাঠানো যায়নি"));
    }

    private void sharePost(String postId, String content) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, (content == null ? "" : content)
                + "\nAYNA Post ID: " + postId);
        startActivity(Intent.createChooser(send, "পোস্ট শেয়ার করুন"));
    }

    private void toggleFollow(String targetId) {
        DocumentReference me = db.collection("users").document(uid)
                .collection("following").document(targetId);
        DocumentReference them = db.collection("users").document(targetId)
                .collection("followers").document(uid);
        db.runTransaction(t -> {
            DocumentSnapshot s = t.get(me);
            if (s.exists()) { t.delete(me); t.delete(them); return false; }
            Map<String, Object> data = new HashMap<>();
            data.put("createdAt", FieldValue.serverTimestamp());
            t.set(me, data); t.set(them, data); return true;
        }).addOnSuccessListener(f -> toast(f ? "Follow করেছেন" : "Unfollow করেছেন"))
                .addOnFailureListener(e -> toast("Follow আপডেট হয়নি"));
    }

    private void sendFriendRequest(String targetId) {
        String requestId = uid + "_" + targetId;
        Map<String, Object> r = new HashMap<>();
        r.put("from", uid); r.put("to", targetId);
        r.put("status", "pending");
        r.put("createdAt", FieldValue.serverTimestamp());
        db.collection("friendRequests").document(requestId).set(r)
                .addOnSuccessListener(x -> {
                    sendNotification(targetId, "friend_request", requestId, currentName + " Friend Request পাঠিয়েছেন");
                    toast("Friend Request পাঠানো হয়েছে");
                }).addOnFailureListener(e -> toast("Request পাঠানো যায়নি"));
    }

    private void showFriends() {
        db.collection("friendRequests").whereEqualTo("to", uid).whereEqualTo("status", "pending")
                .get().addOnSuccessListener(s -> {
                    ArrayList<DocumentSnapshot> requests = new ArrayList<>();
                    ArrayList<String> labels = new ArrayList<>();
                    for (DocumentSnapshot d : s) {
                        requests.add(d);
                        labels.add("Friend request: " + d.getString("from"));
                    }
                    labels.add("আমার Friends তালিকা");
                    new AlertDialog.Builder(this).setTitle("Friends")
                            .setItems(labels.toArray(new String[0]), (a, index) -> {
                                if (index == requests.size()) loadFriends();
                                else showRequestActions(requests.get(index));
                            }).setNegativeButton("বন্ধ", null).show();
                }).addOnFailureListener(e -> toast("Friends লোড হয়নি"));
    }

    private void showRequestActions(DocumentSnapshot request) {
        new AlertDialog.Builder(this).setTitle("Friend Request")
                .setItems(new String[]{"Accept", "Reject"}, (d, which) -> {
                    String from = request.getString("from");
                    if (from == null) return;
                    if (which == 0) {
                        Map<String, Object> f = new HashMap<>();
                        f.put("createdAt", FieldValue.serverTimestamp());
                        db.collection("users").document(uid).collection("friends").document(from).set(f);
                        db.collection("users").document(from).collection("friends").document(uid).set(f);
                    }
                    request.getReference().delete();
                }).show();
    }

    private void loadFriends() {
        db.collection("users").document(uid).collection("friends").get()
                .addOnSuccessListener(s -> {
                    ArrayList<String> names = new ArrayList<>();
                    for (DocumentSnapshot d : s) names.add(d.getId());
                    new AlertDialog.Builder(this).setTitle("আমার Friends")
                            .setItems(names.toArray(new String[0]), null)
                            .setPositiveButton("বন্ধ", null).show();
                });
    }

    private void sendNotification(String target, String type, String refId, String message) {
        Map<String, Object> n = new HashMap<>();
        n.put("userId", target); n.put("fromUserId", uid);
        n.put("type", type); n.put("refId", refId);
        n.put("message", message); n.put("read", false);
        n.put("createdAt", FieldValue.serverTimestamp());
        db.collection("notifications").add(n);
    }

    private void listenNotifications() {
        notificationListener = db.collection("notifications").whereEqualTo("userId", uid)
                .whereEqualTo("read", false).addSnapshotListener((s, e) -> {
                    if (e == null && s != null && s.size() > 0) {
                        // Unread count is available here for a badge if desired.
                    }
                });
    }

    private void showNotifications() {
        db.collection("notifications").whereEqualTo("userId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(50).get()
                .addOnSuccessListener(s -> {
                    ArrayList<String> labels = new ArrayList<>();
                    for (DocumentSnapshot d : s) {
                        String m = d.getString("message");
                        labels.add(m == null ? "নতুন Notification" : m);
                        if (!Boolean.TRUE.equals(d.getBoolean("read")))
                            d.getReference().update("read", true);
                    }
                    new AlertDialog.Builder(this).setTitle("Notifications")
                            .setItems(labels.toArray(new String[0]), null)
                            .setPositiveButton("বন্ধ", null).show();
                }).addOnFailureListener(e -> toast("Notification লোড হয়নি"));
    }

    private void reportPost(String postId, String authorId) {
        EditText reason = new EditText(this);
        reason.setHint("Report করার কারণ");
        new AlertDialog.Builder(this).setTitle("Post Report").setView(reason)
                .setPositiveButton("জমা দিন", (d, w) -> {
                    String why = reason.getText().toString().trim();
                    if (why.isEmpty()) { toast("কারণ লিখুন"); return; }
                    Map<String, Object> r = new HashMap<>();
                    r.put("postId", postId); r.put("authorId", authorId);
                    r.put("reporterId", uid); r.put("reason", why);
                    r.put("status", "pending"); r.put("createdAt", FieldValue.serverTimestamp());
                    db.collection("reports").add(r).addOnSuccessListener(x -> toast("Report জমা হয়েছে"));
                }).setNegativeButton("বাতিল", null).show();
    }

    private void reportUser(String targetId) {
        Map<String, Object> r = new HashMap<>();
        r.put("targetUserId", targetId); r.put("reporterId", uid);
        r.put("reason", "User report"); r.put("status", "pending");
        r.put("createdAt", FieldValue.serverTimestamp());
        db.collection("reports").add(r).addOnSuccessListener(x -> toast("Report জমা হয়েছে"));
    }

    private void confirmBlock(String targetId) {
        new AlertDialog.Builder(this).setTitle("Block User?")
                .setMessage("এই ব্যবহারকারীর পোস্ট আপনার Feed-এ দেখানো হবে না।")
                .setPositiveButton("Block", (d, w) -> blockUser(targetId))
                .setNegativeButton("বাতিল", null).show();
    }

    private void blockUser(String targetId) {
        db.collection("users").document(uid).collection("blockedUsers").document(targetId)
                .set(Collections.singletonMap("createdAt", FieldValue.serverTimestamp()))
                .addOnSuccessListener(x -> {
                    blocked.add(targetId); loadFeed(); toast("ব্যবহারকারী Block হয়েছে");
                }).addOnFailureListener(e -> toast("Block করা যায়নি"));
    }

    private void loadBlockedUsers() {
        db.collection("users").document(uid).collection("blockedUsers").get()
                .addOnSuccessListener(s -> {
                    blocked.clear();
                    for (DocumentSnapshot d : s) blocked.add(d.getId());
                    loadFeed();
                    loadStories();
                });
    }

    private boolean isSafeContent(String value) {
        if (value == null) return true;
        String s = value.toLowerCase(Locale.ROOT);
        String[] blockedWords = {"খানকি", "মাগী", "হারামজাদা", "বোকাচোদা"};
        for (String word : blockedWords) if (s.contains(word)) return false;
        return s.length() <= 10000;
    }

    private void checkPostSafety(String postId, String content) {
        if (!isSafeContent(content)) {
            db.collection("posts").document(postId).update("status", "pending_review");
        }
    }

    private void showAdminReview() {
        if (!isAdmin) { toast("Admin অনুমতি নেই"); return; }
        db.collection("reports").whereEqualTo("status", "pending").limit(50).get()
                .addOnSuccessListener(s -> {
                    ArrayList<DocumentSnapshot> docs = new ArrayList<>();
                    ArrayList<String> labels = new ArrayList<>();
                    for (DocumentSnapshot d : s) {
                        docs.add(d);
                        labels.add("Post: " + d.getString("postId") + " | " + d.getString("reason"));
                    }
                    new AlertDialog.Builder(this).setTitle("Admin Review")
                            .setItems(labels.toArray(new String[0]), (d, index) -> adminReportActions(docs.get(index)))
                            .setPositiveButton("বন্ধ", null).show();
                }).addOnFailureListener(e -> toast("Reports লোড হয়নি"));
    }

    private void adminReportActions(DocumentSnapshot report) {
        if (!isAdmin) return;
        String postId = report.getString("postId");
        String authorId = report.getString("authorId");
        new AlertDialog.Builder(this).setTitle("Report Action")
                .setItems(new String[]{"Reviewed", "Remove Post", "Suspend Author", "Dismiss"},
                        (d, which) -> {
                            if (which == 1 && postId != null)
                                db.collection("posts").document(postId).update("status", "removed");
                            if (which == 2 && authorId != null)
                                db.collection("users").document(authorId).update("accountStatus", "suspended");
                            report.getReference().update("status", which == 3 ? "dismissed" : "reviewed");
                            toast("Admin action পাঠানো হয়েছে");
                        }).show();
    }

    private void openMedia(String url, String type) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.setDataAndType(Uri.parse(url), "video".equals(type) ? "video/*" : "image/*");
            startActivity(i);
        } catch (Exception e) { toast("মিডিয়া খোলা যায়নি"); }
    }

    private void openActivity(String className) {
        try {
            Intent i = new Intent();
            i.setClass(this, Class.forName(getPackageName() + "." + className));
            startActivity(i);
        } catch (Exception e) { toast(className + " এখনো যুক্ত করা হয়নি"); }
    }

    private void clearLikeListeners() {
        for (ListenerRegistration l : likeListeners.values()) l.remove();
        likeListeners.clear();
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(12, 10, 12, 10);
        l.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 5, 0, 8);
        l.setLayoutParams(p);
        return l;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(4, 5, 4, 5);
        return t;
    }

    private void addButton(LinearLayout parent, String label, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setOnClickListener(action);
        parent.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        if (feedListener != null) feedListener.remove();
        if (notificationListener != null) notificationListener.remove();
        if (storyListener != null) storyListener.remove();
        clearLikeListeners();
        super.onDestroy();
    }
}
