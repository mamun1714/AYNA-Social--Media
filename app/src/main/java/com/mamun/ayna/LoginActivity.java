package com.mamun.ayna;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    
    private LinearLayout root, body;
    private EditText emailBox, passBox, nameBox, postBox;
    private Uri selectedMedia;
    private String currentScreen = "";
    private static final int PICK_MEDIA = 100;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        if (auth.getCurrentUser() == null) showAuth(); else showHome();
    }

    private void msg(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) { 
        return (int)(v * getResources().getDisplayMetrics().density); 
    }

    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this); 
        t.setText(s); 
        t.setTextSize(size);
        t.setTextColor(Color.rgb(32,42,55)); 
        t.setPadding(dp(8), dp(7), dp(8), dp(7));
        if (bold) t.setTypeface(null, 1); 
        return t;
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this); 
        e.setHint(hint); 
        e.setTextSize(16);
        e.setSingleLine(false); 
        e.setPadding(dp(12), dp(8), dp(12), dp(8)); 
        return e;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this); 
        b.setText(label);
        b.setOnClickListener(v -> action.run()); 
        return b;
    }

    private void base(String title) {
        root = new LinearLayout(this); 
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245,247,250));
        
        TextView heading = text(title, 23, true); 
        heading.setTextColor(Color.rgb(23,105,170));
        heading.setGravity(Gravity.CENTER_VERTICAL); 
        heading.setPadding(dp(16), dp(12), dp(12), dp(12));
        root.addView(heading);
        
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this); 
        body.setOrientation(LinearLayout.VERTICAL); 
        body.setPadding(dp(12), dp(6), dp(12), dp(18));
        scroll.addView(body); 
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        
        setContentView(root);
    }

    private void showAuth() {
        base("AYNA — আয়না");
        body.addView(text("স্বাগতম! অ্যাকাউন্টে প্রবেশ করুন অথবা নতুন অ্যাকাউন্ট খুলুন।", 16, false));
        emailBox = edit("ইমেইল"); 
        body.addView(emailBox);
        passBox = edit("পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)"); 
        passBox.setInputType(129); 
        body.addView(passBox);
        nameBox = edit("নাম (নতুন অ্যাকাউন্টের জন্য)"); 
        body.addView(nameBox);
        body.addView(button("লগইন", this::login));
        body.addView(button("নতুন অ্যাকাউন্ট তৈরি", this::register));
        body.addView(text("নোট: বর্তমানে ইমেইল/পাসওয়ার্ড লগইন সক্রিয়।", 13, false));
    }

    private void login() {
        String e = emailBox.getText().toString().trim(), p = passBox.getText().toString();
        if (e.isEmpty() || p.isEmpty()) { msg("ইমেইল ও পাসওয়ার্ড দিন"); return; }
        auth.signInWithEmailAndPassword(e, p).addOnSuccessListener(x -> showHome())
            .addOnFailureListener(x -> msg("লগইন হয়নি: " + x.getMessage()));
    }

    private void register() {
        String n = nameBox.getText().toString().trim(), e = emailBox.getText().toString().trim(), p = passBox.getText().toString();
        if (n.isEmpty() || e.isEmpty() || p.length() < 6) { msg("নাম, সঠিক ইমেইল ও কমপক্ষে ৬ অক্ষরের পাসওয়ার্ড দিন"); return; }
        auth.createUserWithEmailAndPassword(e, p).addOnSuccessListener(r -> {
            String uid = r.getUser().getUid();
            Map<String, Object> u = new HashMap<>(); 
            u.put("uid", uid); 
            u.put("name", n); 
            u.put("email", e);
            u.put("bio", ""); 
            u.put("photoUrl", ""); 
            u.put("createdAt", FieldValue.serverTimestamp());
            db.collection("Users").document(uid).set(u).addOnSuccessListener(v -> showHome())
                .addOnFailureListener(err -> msg("প্রোফাইল সংরক্ষণ হয়নি: " + err.getMessage()));
        }).addOnFailureListener(x -> msg("রেজিস্ট্রেশন হয়নি: " + x.getMessage()));
    }

    private void showHome() {
        base("AYNA");
        LinearLayout nav = new LinearLayout(this); 
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.addView(button("ফিড", () -> showFeed()), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(button("পোস্ট", () -> showCreate()), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(button("প্রোফাইল", () -> showProfile()), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(button("বন্ধু", () -> showFriends()), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(button("Creator", () -> showCreator()), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(nav, 1);
        showFeed();
    }

    private void showFeed() {
        currentScreen = "feed"; 
        body.removeAllViews();
        body.addView(text("সবার সাম্প্রতিক পোস্ট", 19, true));
        body.addView(button("＋ নতুন পোস্ট তৈরি করুন", this::showCreate));
        db.collection("Posts").orderBy("timestamp", Query.Direction.DESCENDING).limit(50)
          .get().addOnSuccessListener(s -> { 
              body.removeViews(2, Math.max(0, body.getChildCount() - 2));
              if (s.isEmpty()) body.addView(text("এখনো কোনো পোস্ট নেই। প্রথম পোস্টটি তুমি করো!", 15, false));
              for (DocumentSnapshot d : s.getDocuments()) addPostCard(d);
          }).addOnFailureListener(e -> msg("ফিড লোড হয়নি: " + e.getMessage()));
    }

    private void addPostCard(DocumentSnapshot d) {
        LinearLayout card = new LinearLayout(this); 
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8)); 
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); 
        cp.setMargins(0, dp(7), 0, dp(7));
        body.addView(card, cp);
        
        String uid = d.getString("userId"), name = d.getString("userName"), content = d.getString("postText"), media = d.getString("mediaUrl");
        card.addView(text(name == null ? "AYNA user" : name, 16, true));
        if (content != null && !content.isEmpty()) card.addView(text(content, 16, false));
        if (media != null && !media.isEmpty()) card.addView(text("মিডিয়া সংযুক্ত: " + media, 12, false));
        
        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("লাইক", () -> like(d)));
        actions.addView(button("কমেন্ট", () -> comment(d)));
        actions.addView(button("শেয়ার", () -> share(content == null ? "AYNA পোস্ট" : content)));
        card.addView(actions);
        
        if (auth.getCurrentUser() != null && auth.getCurrentUser().getUid().equals(uid))
            card.addView(button("পোস্ট মুছুন", () -> deletePost(d)));
    }

    private void like(DocumentSnapshot post) {
        String uid = auth.getCurrentUser().getUid();
        DocumentReference like = post.getReference().collection("Likes").document(uid);
        db.runTransaction(t -> {
            DocumentSnapshot s = t.get(like); 
            if (!s.exists()) {
                t.set(like, Collections.singletonMap("uid", uid));
                t.update(post.getReference(), "likeCount", FieldValue.increment(1));
            } 
            return null;
        }).addOnSuccessListener(v -> msg("লাইক হয়েছে")).addOnFailureListener(e -> msg("লাইক হয়নি: " + e.getMessage()));
    }

    private void comment(DocumentSnapshot post) {
        EditText input = edit("কমেন্ট লিখুন");
        new AlertDialog.Builder(this).setTitle("কমেন্ট").setView(input)
          .setPositiveButton("পাঠান", (a, b) -> {
            String value = input.getText().toString().trim(); 
            if (value.isEmpty()) return;
            FirebaseUser u = auth.getCurrentUser();
            Map<String, Object> c = new HashMap<>();
            c.put("userId", u.getUid());
            c.put("userName", u.getEmail());
            c.put("text", value);
            c.put("timestamp", FieldValue.serverTimestamp());
            post.getReference().collection("Comments").add(c)
                .addOnSuccessListener(x -> msg("কমেন্ট যোগ হয়েছে"))
                .addOnFailureListener(x -> msg("কমেন্ট হয়নি"));
          }).setNegativeButton("বাতিল", null).show();
    }

    private void deletePost(DocumentSnapshot d) {
        new AlertDialog.Builder(this).setMessage("পোস্টটি মুছে ফেলবেন?")
          .setPositiveButton("মুছুন", (a, b) -> d.getReference().delete()
              .addOnSuccessListener(x -> showFeed())
              .addOnFailureListener(x -> msg("মুছতে সমস্যা")))
          .setNegativeButton("না", null).show();
    }

    private void share(String value) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, value);
        startActivity(Intent.createChooser(i, "শেয়ার করুন"));
    }

    private void showCreate() {
        currentScreen = "create"; 
        body.removeAllViews();
        body.addView(text("নতুন পোস্ট", 20, true));
        postBox = edit("তোমার মনের কথা লিখো..."); 
        body.addView(postBox);
        
        body.addView(button("গ্যালারি থেকে ছবি/ভিডিও বাছাই", () -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
            startActivityForResult(i, PICK_MEDIA);
        }));
        
        TextView selected = text("কোনো মিডিয়া বাছাই করা হয়নি", 13, false);
        body.addView(selected);
        body.addView(button("পোস্ট প্রকাশ করুন", () -> publishPost()));
        body.addView(button("ফিডে ফিরে যান", () -> showFeed()));
    }

    // --- মিডিয়া আপলোড এবং পোস্ট সেভ করার লজিক ---
    private void publishPost() {
        String content = postBox.getText().toString().trim();
        if (content.isEmpty() && selectedMedia == null) {
            msg("কিছু লিখুন অথবা মিডিয়া যুক্ত করুন");
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            msg("ইউজার পাওয়া যায়নি");
            return;
        }

        if (selectedMedia != null) {
            StorageReference ref = storage.getReference().child("posts/" + System.currentTimeMillis() + ".jpg");
            ref.putFile(selectedMedia).addOnSuccessListener(taskSnapshot -> 
                ref.getDownloadUrl().addOnSuccessListener(uri -> savePostToFirestore(content, uri.toString()))
            ).addOnFailureListener(e -> msg("মিডিয়া আপলোড ব্যর্থ: " + e.getMessage()));
        } else {
            savePostToFirestore(content, "");
        }
    }

    private void savePostToFirestore(String content, String mediaUrl) {
        FirebaseUser user = auth.getCurrentUser();
        Map<String, Object> post = new HashMap<>();
        post.put("userId", user.getUid());
        post.put("userName", user.getDisplayName() != null && !user.getDisplayName().isEmpty() ? user.getDisplayName() : user.getEmail());
        post.put("postText", content);
        post.put("mediaUrl", mediaUrl);
        post.put("timestamp", FieldValue.serverTimestamp());
        post.put("likeCount", 0);

        db.collection("Posts").add(post).addOnSuccessListener(ref -> {
            msg("পোস্ট সফলভাবে প্রকাশিত হয়েছে!");
            selectedMedia = null;
            showFeed();
        }).addOnFailureListener(e -> msg("পোস্ট প্রকাশ হয়নি: " + e.getMessage()));
    }

    // --- প্রোফাইল পেজ লজিক ---
    private void showProfile() {
        currentScreen = "profile";
        body.removeAllViews();
        body.addView(text("আমার প্রোফাইল", 20, true));

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            body.addView(text("ইমেইল: " + user.getEmail(), 16, false));

            db.collection("Users").document(user.getUid()).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String name = doc.getString("name");
                    String bio = doc.getString("bio");
                    body.addView(text("নাম: " + (name != null ? name : "দেওয়া হয়নি"), 16, false));
                    body.addView(text("বায়ো: " + (bio != null && !bio.isEmpty() ? bio : "কোনো বায়ো নেই"), 16, false));
                }
            });

            body.addView(button("লগআউট", () -> {
                auth.signOut();
                showAuth();
            }));
        }
    }

    private void showFriends() {
        currentScreen = "friends";
        body.removeAllViews();
        body.addView(text("বন্ধু তালিকা (শীঘ্রই আসছে)", 18, true));
    }

    private void showCreator() {
        currentScreen = "creator";
        body.removeAllViews();
        body.addView(text("ক্রিয়েটর ড্যাশবোর্ড", 18, true));
    }

    @Override 
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_MEDIA && result == RESULT_OK && data != null) {
            selectedMedia = data.getData();
            msg("মিডিয়া বাছাই হয়েছে");
        }
    }
}
