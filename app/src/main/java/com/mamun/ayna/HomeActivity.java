package com.mamun.ayna;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.*;

public class HomeActivity extends Activity {
    private static final int PICK_POST_MEDIA = 4101;
    private static final int PICK_STORY_MEDIA = 4102;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private LinearLayout root, feed;
    private String uid, currentName = "AYNA User";
    private boolean isAdmin = false;
    private final Set<String> blocked = new HashSet<>();
    private ListenerRegistration feedListener, notificationListener;
    private final Map<String, ListenerRegistration> likeListeners = new HashMap<>();
    private Uri pendingStoryUri;
    private String pendingStoryType = "image";
    private final int BLUE = Color.rgb(23, 105, 170);

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        if (auth.getCurrentUser() == null) { finish(); return; }
        uid = auth.getCurrentUser().getUid();
        buildHome();
        loadProfileName();
        loadBlockedUsers();
        listenNotifications();
        loadFeed();
    }

    // 1. Header and AYNA logo
    private void buildHome() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245,247,250));
        setContentView(root);

        LinearLayout header = row();
        header.setPadding(16, 12, 12, 12);
        TextView logo = text("আয়না", 27, BLUE);
        logo.setTypeface(null, 1);
        header.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));
        addButton(header, "Search", v -> showSearch());
        addButton(header, "Profile", v -> showProfile(uid));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        feed = new LinearLayout(this);
        feed.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(feed);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // 18. Bottom navigation
        LinearLayout nav = row();
        nav.setGravity(Gravity.CENTER);
        addButton(nav, "Home", v -> loadFeed());
        addButton(nav, "Friends", v -> showFriends());
        addButton(nav, "Messages", v -> openActivity("ChatActivity"));
        addButton(nav, "Alerts", v -> showNotifications());
        addButton(nav, "Creator", v -> openActivity("CreatorStudioActivity"));
        root.addView(nav);
    }

    // 2. Search
    private void showSearch() {
        EditText input = new EditText(this);
        input.setHint("নাম দিয়ে খুঁজুন");
        new AlertDialog.Builder(this).setTitle("Search people").setView(input)
            .setPositiveButton("Search", (d,w) -> {
                String q = input.getText().toString().trim();
                if (q.isEmpty()) return;
                db.collection("users").orderBy("name")
                    .startAt(q).endAt(q + "\uf8ff").limit(30).get()
                    .addOnSuccessListener(s -> {
                        ArrayList<String> names = new ArrayList<>();
                        ArrayList<String> ids = new ArrayList<>();
                        for (DocumentSnapshot doc : s) {
                            if (!doc.getId().equals(uid)) {
                                names.add(doc.getString("name") == null ? "User" : doc.getString("name"));
                                ids.add(doc.getId());
                            }
                        }
                        if (names.isEmpty()) { toast("কাউকে পাওয়া যায়নি"); return; }
                        new AlertDialog.Builder(this).setTitle("Search results")
                            .setItems(names.toArray(new String[0]), (x,i) -> showProfile(ids.get(i))).show();
                    }).addOnFailureListener(e -> toast(e.getMessage()));
            }).setNegativeButton("Cancel", null).show();
    }

    // 3. Profile
    private void loadProfileName() {
        db.collection("users").document(uid).get().addOnSuccessListener(d -> {
            if (d.exists()) {
                if (d.getString("name") != null) currentName = d.getString("name");
                isAdmin = Boolean.TRUE.equals(d.getBoolean("isAdmin"));
            }
        });
    }

    private void showProfile(String userId) {
        db.collection("users").document(userId).get().addOnSuccessListener(d -> {
            if (!d.exists()) { toast("প্রোফাইল পাওয়া যায়নি"); return; }
            String name = d.getString("name") == null ? "AYNA User" : d.getString("name");
            String email = d.getString("email") == null ? "" : d.getString("email");
            AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(name).setMessage(email);
            if (!userId.equals(uid)) {
                b.setPositiveButton("Follow / Unfollow", (x,w) -> toggleFollow(userId));
                b.setNeutralButton("Block", (x,w) -> confirmBlock(userId));
            } else {
                b.setPositiveButton("Logout", (x,w) -> {
                    FirebaseAuth.getInstance().signOut(); finish();
                });
                if (isAdmin || Boolean.TRUE.equals(d.getBoolean("isAdmin")))
                    b.setNeutralButton("Admin Review", (x,w) -> showAdminReview());
            }
            b.setNegativeButton("Close", null).show();
        }).addOnFailureListener(e -> toast(e.getMessage()));
    }

    // 4. Text, photo and video stories; expiry is checked when reading.
    private void showStories() {
        EditText story = new EditText(this);
        story.setHint("আজকের গল্প লিখুন (ঐচ্ছিক)");
        new AlertDialog.Builder(this).setTitle("Create story").setView(story)
            .setItems(new String[]{"Text story", "Photo story", "Video story"}, (dialog, which) -> {
                if (which == 0) publishStory(story.getText().toString().trim(), "", "text");
                else {
                    pendingStoryType = which == 1 ? "image" : "video";
                    Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                    pick.setType(which == 1 ? "image/*" : "video/*");
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(Intent.createChooser(pick, "Select story media"), PICK_STORY_MEDIA);
                }
            }).setNegativeButton("Cancel", null).show();
    }

    private void publishStory(String body, String mediaUrl, String type) {
        if (TextUtils.isEmpty(body) && TextUtils.isEmpty(mediaUrl)) {
            toast("গল্পের লেখা বা ছবি/ভিডিও দিন"); return;
        }
        Map<String,Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("userName", currentName);
        data.put("text", body);
        data.put("mediaUrl", mediaUrl);
        data.put("mediaType", type);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("expiresAt", System.currentTimeMillis() + DAY_MS);
        db.collection("stories").add(data)
            .addOnSuccessListener(r -> { toast("Story published"); loadFeed(); })
            .addOnFailureListener(e -> toast(e.getMessage()));
    }

    private void loadStories() {
        db.collection("stories").orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
            .get().addOnSuccessListener(s -> {
                if (feed == null || s.isEmpty()) return;
                LinearLayout box = card();
                box.addView(text("Stories",18,BLUE));
                int count = 0;
                for (DocumentSnapshot d : s) {
                    Long exp = d.getLong("expiresAt");
                    if (exp != null && exp < System.currentTimeMillis()) continue;
                    String name = d.getString("userName");
                    String body = d.getString("text");
                    String url = d.getString("mediaUrl");
                    String type = d.getString("mediaType");
                    box.addView(text((name == null ? "User" : name) + ": " +
                        (body == null ? "" : body),15,Color.DKGRAY));
                    if (!TextUtils.isEmpty(url)) {
                        if ("video".equals(type)) {
                            addButton(box, "Play story video", v -> openMedia(url));
                        } else {
                            ImageView image = new ImageView(this);
                            image.setAdjustViewBounds(true);
                            box.addView(image);
                            Glide.with(this).load(url).into(image);
                        }
                    }
                    count++;
                }
                if (count > 0 && feed.getChildCount() > 0) feed.addView(box, 1);
            }).addOnFailureListener(e -> toast(e.getMessage()));
    }

    // 5. News feed
    private void loadFeed() {
        if (feedListener != null) { feedListener.remove(); feedListener = null; }
        if (feed == null) return;
        feed.removeAllViews();
        LinearLayout composer = card();
        composer.addView(text("What's on your mind?",17,BLUE));
        EditText postText = new EditText(this);
        postText.setHint("আপনার পোস্ট লিখুন...");
        composer.addView(postText);
        addButton(composer, "Publish text", v -> createTextPost(postText.getText().toString()));
        LinearLayout media = row();
        addButton(media, "Photo", v -> pickPostMedia("image/*"));
        addButton(media, "Video", v -> pickPostMedia("video/*"));
        addButton(media, "Story", v -> showStories());
        composer.addView(media);
        feed.addView(composer);

        feedListener = db.collection("posts").orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50).addSnapshotListener((snap,error) -> {
                if (error != null || snap == null) {
                    if(error!=null) toast(error.getMessage());
                    return;
                }
                while (feed.getChildCount() > 1) feed.removeViewAt(1);
                for (DocumentSnapshot doc : snap.getDocuments()) {
                    String author = doc.getString("userId");
                    if (author != null && blocked.contains(author)) continue;
                    addPostCard(doc);
                }
                loadStories();
            });
    }

    // 6. Text/photo/video post
    private void createTextPost(String value) {
        String body = value == null ? "" : value.trim();
        if (body.isEmpty()) { toast("পোস্ট লিখুন"); return; }
        if (!isSafeContent(body)) { toast("এই লেখা প্রকাশ করা যাচ্ছে না"); return; }
        Map<String,Object> post = new HashMap<>();
        post.put("userId", uid); post.put("userName", currentName);
        post.put("text", body); post.put("mediaUrl", "");
        post.put("mediaType", "text"); post.put("createdAt", FieldValue.serverTimestamp());
        post.put("likesCount", 0L); post.put("commentsCount", 0L);
        db.collection("posts").add(post).addOnSuccessListener(r -> toast("Post published"))
            .addOnFailureListener(e -> toast(e.getMessage()));
    }

    private void pickPostMedia(String mime) {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType(mime);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(pick, "Select media"), PICK_POST_MEDIA);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_POST_MEDIA) uploadMedia(uri, "posts", null);
        else if (requestCode == PICK_STORY_MEDIA) uploadMedia(uri, "stories", pendingStoryType);
    }

    private void uploadMedia(Uri uri, String folder, @Nullable String storyType) {
        String key = UUID.randomUUID().toString();
        StorageReference ref = storage.getReference().child(folder + "/" + uid + "/" + key);
        Toast.makeText(this, "আপলোড হচ্ছে...", Toast.LENGTH_SHORT).show();
        ref.putFile(uri).continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return ref.getDownloadUrl();
        }).addOnSuccessListener(download -> {
            String url = download.toString();
            if ("stories".equals(folder)) {
                publishStory("", url, storyType == null ? "image" : storyType);
            } else {
                String mime = getContentResolver().getType(uri);
                String type = mime != null && mime.startsWith("video") ? "video" : "image";
                Map<String,Object> p = new HashMap<>();
                p.put("userId", uid); p.put("userName", currentName);
                p.put("text", ""); p.put("mediaUrl", url); p.put("mediaType", type);
                p.put("createdAt", FieldValue.serverTimestamp());
                p.put("likesCount", 0L); p.put("commentsCount", 0L);
                db.collection("posts").add(p)
                    .addOnSuccessListener(v -> toast("Post published"))
                    .addOnFailureListener(e -> toast(e.getMessage()));
            }
        }).addOnFailureListener(e -> toast("Upload failed: " + e.getMessage()));
    }

    // 7. Like + notification
    private void toggleLike(String postId, String authorId) {
        DocumentReference like = db.collection("posts").document(postId)
            .collection("likes").document(uid);
        like.get().addOnSuccessListener(d -> {
            if (d.exists()) {
                like.delete().addOnFailureListener(e -> toast(e.getMessage()));
            } else {
                Map<String,Object> m = new HashMap<>();
                m.put("userId", uid); m.put("createdAt", FieldValue.serverTimestamp());
                like.set(m).addOnSuccessListener(v -> {
                    if (authorId != null && !authorId.equals(uid))
                        sendNotification(authorId, "like", currentName + " আপনার পোস্টে Like দিয়েছেন", postId);
                }).addOnFailureListener(e -> toast(e.getMessage()));
            }
        }).addOnFailureListener(e -> toast(e.getMessage()));
    }

    // 8. Comment and 9. Reply: select any comment from the list.
    private void showComments(String postId, String postAuthorId) {
        db.collection("posts").document(postId).collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING).limit(100).get()
            .addOnSuccessListener(s -> {
                ArrayList<DocumentSnapshot> comments = new ArrayList<>();
                ArrayList<String> labels = new ArrayList<>();
                for (DocumentSnapshot c : s) {
                    comments.add(c);
                    String prefix = TextUtils.isEmpty(c.getString("parentCommentId")) ? "" : "  ↳ ";
                    labels.add(prefix + safe(c.getString("userName"), "User") + ": " +
                        safe(c.getString("text"), ""));
                }
                String[] menu = new String[labels.size() + 1];
                for (int i=0;i<labels.size();i++) menu[i] = labels.get(i);
                menu[menu.length-1] = "নতুন মন্তব্য লিখুন";
                new AlertDialog.Builder(this).setTitle("Comments")
                    .setItems(menu, (dialog, which) -> {
                        if (which == comments.size()) promptComment(postId, postAuthorId, null);
                        else {
                            DocumentSnapshot selected = comments.get(which);
                            new AlertDialog.Builder(this).setTitle("Comment options")
                                .setItems(new String[]{"Reply", "Close"}, (a,b) -> {
                                    if (b == 0) promptComment(postId, postAuthorId, selected.getId());
                                }).show();
                        }
                    }).setNegativeButton("Close", null).show();
            }).addOnFailureListener(e -> toast(e.getMessage()));
    }

    private void promptComment(String postId, String postAuthorId, @Nullable String parentId) {
        EditText input = new EditText(this);
        input.setHint(parentId == null ? "মন্তব্য লিখুন" : "Reply লিখুন");
        new AlertDialog.Builder(this).setTitle(parentId == null ? "Comment" : "Reply")
            .setView(input).setPositiveButton("Send", (d,w) ->
                addComment(postId, postAuthorId, input.getText().toString(), parentId))
            .setNegativeButton("Cancel", null).show();
    }

    private void addComment(String postId, String postAuthorId, String value, @Nullable String parentId) {
        String body = value == null ? "" : value.trim();
        if (body.isEmpty() || !isSafeContent(body)) {
            toast("মন্তব্যটি খালি বা অনুপযুক্ত"); return;
        }
        Map<String,Object> c = new HashMap<>();
        c.put("userId", uid); c.put("userName", currentName); c.put("text", body);
        c.put("parentCommentId", parentId == null ? "" : parentId);
        c.put("createdAt", FieldValue.serverTimestamp());
        db.collection("posts").document(postId).collection("comments").add(c)
            .addOnSuccessListener(r -> {
                if (parentId == null)
                    db.collection("posts").document(postId).update("commentsCount", FieldValue.increment(1));
                if (postAuthorId != null && !postAuthorId.equals(uid))
                    sendNotification(postAuthorId, parentId == null ? "comment" : "reply",
                        currentName + (parentId == null ? " আপনার পোস্টে Comment করেছেন" : " আপনার পোস্টে Reply করেছেন"), postId);
                toast(parentId == null ? "Comment added" : "Reply added");
            }).addOnFailureListener(e -> toast(e.getMessage()));
    }

    // 10. Share
    private void sharePost(DocumentSnapshot post) {
        String body = post.getString("text");
        String media = post.getString("mediaUrl");
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, "AYNA post: " + safe(body, "") +
            (TextUtils.isEmpty(media) ? "" : "\n" + media));
        startActivity(Intent.createChooser(send, "Share post"));
    }

    // 11. Follow / Unfollow
    private void toggleFollow(String target) {
        DocumentReference following = db.collection("users").document(uid)
            .collection("following").document(target);
        DocumentReference follower = db.collection("users").document(target)
            .collection("followers").document(uid);
        following.get().addOnSuccessListener(d -> {
            WriteBatch batch = db.batch();
            if (d.exists()) { batch.delete(following); batch.delete(follower); }
            else {
                Map<String,Object> m = new HashMap<>();
                m.put("userId", target); m.put("createdAt", FieldValue.serverTimestamp());
                batch.set(following,m); batch.set(follower,m);
            }
            batch.commit().addOnSuccessListener(v -> {
                if (!d.exists()) sendNotification(target, "follow",
                    currentName + " আপনাকে Follow করেছেন", "");
                toast("Follow status updated");
            }).addOnFailureListener(e -> toast(e.getMessage()));
        }).addOnFailureListener(e -> toast(e.getMessage()));
    }

    // 12. Friends: friend requests and accepted friends.
    private void showFriends() {
        new AlertDialog.Builder(this).setTitle("Friends")
            .setItems(new String[]{"Friend requests", "My friends", "Find people"}, (d,w) -> {
                if (w == 0) showFriendRequests();
                else if (w == 1) showAcceptedFriends();
                else showSearch();
            }).show();
    }

    private void sendFriendRequest(String target) {
        if (target.equals(uid)) return;
        DocumentReference req = db.collection("users").document(target)
            .collection("friendRequests").document(uid);
        Map<String,Object> m = new HashMap<>();
        m.put("fromUserId", uid); m.put("fromName", currentName);
        m.put("status", "pending"); m.put("createdAt", FieldValue.serverTimestamp());
        req.set(m).addOnSuccessListener(v -> {
            sendNotification(target, "friend_request", currentName + " আপনাকে Friend Request পাঠিয়েছেন", "");
            toast("Friend request sent");
        }).addOnFailureListener(e -> toast(e.getMessage()));
    }

    private void showFriendRequests() {
        db.collection("users").document(uid).collection("friendRequests")
            .whereEqualTo("status","pending").limit(50).get()
            .addOnSuccessListener(s -> {
                if (s.isEmpty()) { toast("কোনো Friend Request নেই"); return; }
                ArrayList<String> ids = new ArrayList<>();
                ArrayList<String> names = new ArrayList<>();
                for (DocumentSnapshot d : s) {
                    ids.add(d.getId());
                    names.add(safe(d.getString("fromName"), d.getId()));
                }
                new AlertDialog.Builder(this).setTitle("Friend requests")
                    .setItems(names.toArray(new String[0]), (a,b) -> handleFriendRequest(ids.get(b)))
                    .setNegativeButton("Close",null).show();
            }).addOnFailureListener(e -> toast(e.getMessage()));
    }

    private void handleFriendRequest(String sender) {
        new AlertDialog.Builder(this).setTitle("Friend request")
            .setItems(new String[]{"Accept", "Reject"}, (d,w) -> {
                DocumentReference incoming = db.collection("users").document(uid)
                    .collection("friendRequests").document(sender);
                if (w == 0) {
                    WriteBatch batch = db.batch();
                    Map<String,Object> f = new HashMap<>();
                    f.put("userId", sender); f.put("createdAt", FieldValue.serverTimestamp());
                    Map<String,Object> back = new HashMap<>();
                    back.put("userId", uid); back.put("createdAt", FieldValue.serverTimestamp());
                    batch.set(db.collection("users").document(uid).collection("friends").document(sender), f);
                    batch.set(db.collection("users").document(sender).collection("friends").document(uid), back);
                    batch.delete(incoming);
                    batch.commit().addOnSuccessListener(v -> {
                        sendNotification(sender, "friend_accept", currentName + " আপনার Friend Request গ্রহণ করেছেন", "");
                        toast("Friend added");
                    }).addOnFailureListener(e -> toast(e.getMessage()));
                } else {
                    incoming.delete().addOnSuccessListener(v -> toast("Request rejected"))
                        .addOnFailureListener(e -> toast(e.getMessage()));
                }
            }).show();
    }

    private void showAcceptedFriends() {
        db.collection("users").document(uid).collection("friends").limit(100).get()
            .addOnSuccessListener(s -> {
                ArrayList<String> ids = new ArrayList<>();
                ArrayList<String> names = new ArrayList<>();
                for (DocumentSnapshot d : s) { ids.add(d.getId()); names.add(d.getId()); }
                if (ids.isEmpty()) { toast("এখনো কোনো Friend নেই"); return; }
                for (int i=0;i<ids.size();i++) {
                    final int index=i;
                    db.collection("users").document(ids.get(i)).get().addOnSuccessListener(u -> {
                        names.set(index, safe(u.getString("name"), ids.get(index)));
                        if (index == ids.size()-1) { /* names are shown below after reads */ }
                    });
                }
                showFriendNamesWhenLoaded(ids, names);
            }).addOnFailureListener(e -> toast(e.getMessage()));
    }

    private void showFriendNamesWhenLoaded(ArrayList<String> ids, ArrayList<String> names) {
        // Read each profile in one pass and show after all reads complete.
        final int[] remaining = {ids.size()};
        for (int i=0;i<ids.size();i++) {
            final int index=i;
            db.collection("users").document(ids.get(i)).get().addOnSuccessListener(u -> {
                names.set(index, safe(u.getString("name"), ids.get(index)));
                if (--remaining[0] == 0)
                    new AlertDialog.Builder(this).setTitle("My friends")
                        .setItems(names.toArray(new String[0]), (d,w) -> showProfile(ids.get(w)))
                        .setPositiveButton("Close",null).show();
            }).addOnFailureListener(e -> {
                if (--remaining[0] == 0)
                    new AlertDialog.Builder(this).setTitle("My friends")
                        .setItems(names.toArray(new String[0]), (d,w) -> showProfile(ids.get(w)))
                        .setPositiveButton("Close",null).show();
            });
        }
    }

    // 13. Notifications
    private void listenNotifications() {
        notificationListener = db.collection("users").document(uid).collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener((s,e) -> {
                if (e != null) return;
                // Live listener keeps notification updates subscribed; list opens on demand.
            });
    }

    private void sendNotification(String to, String type, String message, String postId) {
        if (to == null || to.equals(uid)) return;
        Map<String,Object> n = new HashMap<>();
        n.put("type",type); n.put("message",message); n.put("fromUserId",uid);
        n.put("postId",postId == null ? "" : postId);
        n.put("read",false); n.put("createdAt",FieldValue.serverTimestamp());
        db.collection("users").document(to).collection("notifications").add(n);
    }

    private void showNotifications() {
        db.collection("users").document(uid).collection("notifications")
            .orderBy("createdAt",Query.Direction.DESCENDING).limit(50).get()
            .addOnSuccessListener(s -> {
                ArrayList<DocumentReference> unread = new ArrayList<>();
                StringBuilder b = new StringBuilder();
                for (DocumentSnapshot d : s) {
                    b.append(safe(d.getString("message"), "Notification")).append("\n");
                    if (!Boolean.TRUE.equals(d.getBoolean("read"))) unread.add(d.getReference());
                }
                WriteBatch batch = db.batch();
                for (DocumentReference r : unread) batch.update(r, "read", true);
                if (!unread.isEmpty()) batch.commit();
                new AlertDialog.Builder(this).setTitle("Notifications")
                    .setMessage(b.length()==0 ? "কোনো notification নেই" : b.toString())
                    .setPositiveButton("Close",null).show();
            }).addOnFailureListener(e -> toast(e.getMessage()));
    }

    // 14. Report
    private void reportPost(String postId, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) { toast("Report-এর কারণ লিখুন"); return; }
        Map<String,Object> r = new HashMap<>();
        r.put("postId",postId); r.put("reporterId",uid); r.put("reason",why);
        r.put("status","pending"); r.put("createdAt",FieldValue.serverTimestamp());
        db.collection("reports").add(r).addOnSuccessListener(x -> toast("Report submitted"))
            .addOnFailureListener(e -> toast(e.getMessage()));
    }

    // 15. Block
    private void loadBlockedUsers() {
        db.collection("users").document(uid).collection("blocked").get()
            .addOnSuccessListener(s -> {
                blocked.clear();
                for (DocumentSnapshot d : s) blocked.add(d.getId());
                loadFeed();
            });
    }

    private void confirmBlock(String target) {
        new AlertDialog.Builder(this).setMessage("এই ব্যবহারকারীকে Block করবেন?")
            .setPositiveButton("Block", (d,w) -> blockUser(target))
            .setNegativeButton("Cancel",null).show();
    }

    private void blockUser(String target) {
        Map<String,Object> m = new HashMap<>();
        m.put("userId",target); m.put("createdAt",FieldValue.serverTimestamp());
        db.collection("users").document(uid).collection("blocked").document(target).set(m)
            .addOnSuccessListener(v -> {
                blocked.add(target);
                db.collection("users").document(uid).collection("following").document(target).delete();
                db.collection("users").document(target).collection("followers").document(uid).delete();
                toast("User blocked"); loadFeed();
            }).addOnFailureListener(e -> toast(e.getMessage()));
    }

    // 16. Basic client-side safety check; real moderation needs trusted backend review.
    private boolean isSafeContent(String value) {
        String t = value.toLowerCase(Locale.ROOT);
        String[] blockedWords = {"নোংরা গালি","অশ্লীল শব্দ","খানকির পোলা","মাদারচোদ"};
        for (String word : blockedWords) if (t.contains(word)) return false;
        return true;
    }

    // 17. Admin review. Admin privileges must also be enforced by Firestore Security Rules/backend.
    private void showAdminReview() {
        if (!isAdmin) { toast("Admin permission নেই"); return; }
        db.collection("reports").whereEqualTo("status","pending").limit(50).get()
            .addOnSuccessListener(s -> {
                if (s.isEmpty()) { toast("Pending report নেই"); return; }
                ArrayList<String> labels = new ArrayList<>();
                ArrayList<DocumentSnapshot> reports = new ArrayList<>();
                for (DocumentSnapshot d : s) {
                    reports.add(d);
                    labels.add("Post: " + safe(d.getString("postId"), "?") +
                        " | " + safe(d.getString("reason"), "No reason"));
                }
                new AlertDialog.Builder(this).setTitle("Pending reports")
                    .setItems(labels.toArray(new String[0]),(d,w) -> adminReportActions(reports.get(w)))
                    .setNegativeButton("Close",null).show();
            }).addOnFailureListener(e -> toast(e.getMessage()));
    }

    private void adminReportActions(DocumentSnapshot report) {
        String postId = report.getString("postId");
        String reporterId = report.getString("reporterId");
        new AlertDialog.Builder(this).setTitle("Admin action")
            .setItems(new String[]{"Mark reviewed", "Remove reported post", "Suspend reported post author", "Dismiss"},
                (d,w) -> {
                    if (w == 0 || w == 3) {
                        report.getReference().update("status", w == 0 ? "reviewed" : "dismissed")
                            .addOnSuccessListener(v -> toast("Report updated"))
                            .addOnFailureListener(e -> toast(e.getMessage()));
                    } else if (TextUtils.isEmpty(postId)) {
                        toast("Post ID পাওয়া যায়নি");
                    } else {
                        db.collection("posts").document(postId).get().addOnSuccessListener(post -> {
                            String authorId = post.getString("userId");
                            if (w == 1) {
                                db.collection("posts").document(postId).delete()
                                    .addOnSuccessListener(v -> report.getReference().update("status","action_taken")
                                        .addOnSuccessListener(x -> toast("Post removed")))
                                    .addOnFailureListener(e -> toast(e.getMessage()));
                            } else if (!TextUtils.isEmpty(authorId)) {
                                db.collection("users").document(authorId)
                                    .update("accountStatus","suspended", "suspendedAt",FieldValue.serverTimestamp())
                                    .addOnSuccessListener(v -> report.getReference().update("status","action_taken")
                                        .addOnSuccessListener(x -> toast("User suspended")))
                                    .addOnFailureListener(e -> toast(e.getMessage()));
                            } else toast("Post author পাওয়া যায়নি");
                        }).addOnFailureListener(e -> toast(e.getMessage()));
                    }
                }).show();
    }

    private void addPostCard(DocumentSnapshot doc) {
        String postId = doc.getId();
        String authorId = doc.getString("userId");
        String author = doc.getString("userName");
        String body = doc.getString("text");
        LinearLayout box = card();
        TextView name = text(author == null ? "AYNA User" : author,16,BLUE);
        name.setTypeface(null,1);
        name.setOnClickListener(v -> { if(authorId!=null) showProfile(authorId); });
        box.addView(name);
        if (!TextUtils.isEmpty(body)) box.addView(text(body,16,Color.DKGRAY));
        String mediaUrl = doc.getString("mediaUrl");
        String mediaType = doc.getString("mediaType");
        if (!TextUtils.isEmpty(mediaUrl) && mediaUrl.startsWith("http")) {
            if ("video".equals(mediaType)) {
                addButton(box,"Play video",v -> openMedia(mediaUrl));
            } else {
                ImageView image = new ImageView(this);
                image.setAdjustViewBounds(true);
                box.addView(image);
                Glide.with(this).load(mediaUrl).into(image);
            }
        }
        LinearLayout actions = row();
        addButton(actions,"Like",v -> toggleLike(postId, authorId));
        addButton(actions,"Comment",v -> showComments(postId, authorId));
        addButton(actions,"Share",v -> sharePost(doc));
        addButton(actions,"Report",v -> {
            EditText reason=new EditText(this); reason.setHint("Report reason");
            new AlertDialog.Builder(this).setTitle("Report post").setView(reason)
                .setPositiveButton("Submit",(d,w)->reportPost(postId,reason.getText().toString()))
                .setNegativeButton("Cancel",null).show();
        });
        box.addView(actions);
        TextView count=text("Likes: loading",13,Color.GRAY);
        box.addView(count);
        ListenerRegistration old=likeListeners.remove(postId);
        if(old!=null) old.remove();
        likeListeners.put(postId,db.collection("posts").document(postId).collection("likes")
            .addSnapshotListener((s,e)->{ if(s!=null && !isFinishing()) count.setText("Likes: "+s.size()); }));
        feed.addView(box);
    }

    private void openMedia(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception e) { toast("Media খোলা যাচ্ছে না"); }
    }

    private String safe(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private LinearLayout row() {
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(14,12,14,12);
        l.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(8,6,8,6);
        l.setLayoutParams(p);
        return l;
    }

    private TextView text(String value,int size,int color) {
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(5,6,5,6);
        return t;
    }

    private void addButton(LinearLayout parent,String label,View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams params;
        if (parent.getOrientation() == LinearLayout.HORIZONTAL)
            params = new LinearLayout.LayoutParams(0, -2, 1f);
        else
            params = new LinearLayout.LayoutParams(-1, -2);
        parent.addView(b, params);
    }

    private void openActivity(String name) {
        try {
            startActivity(new Intent(this,Class.forName(getPackageName()+"."+name)));
        } catch(Exception e) { toast(name+" এখনো তৈরি হয়নি"); }
    }

    private void toast(String message) {
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if(feedListener!=null) feedListener.remove();
        if(notificationListener!=null) notificationListener.remove();
        for(ListenerRegistration l:likeListeners.values()) l.remove();
        likeListeners.clear();
    }
}
