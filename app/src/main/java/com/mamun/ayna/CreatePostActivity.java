
package com.mamun.ayna;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreatePostActivity extends Activity {

    private static final int PICK_IMAGE = 101;
    private static final int PICK_VIDEO = 102;

    private EditText postText;
    private TextView selectedMedia;
    private Uri mediaUri;
    private String mediaType = "";

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private Button publishButton;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        createScreen();
    }

    private void createScreen() {

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundColor(0xFFF5F7FB);

        TextView title = new TextView(this);
        title.setText("Create AYNA Post");
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(0xFF1769AA);
        title.setPadding(0, 15, 0, 25);
        layout.addView(title);

        postText = new EditText(this);
        postText.setHint("What's on your mind?");
        postText.setTextSize(17);
        postText.setMinLines(4);
        postText.setGravity(Gravity.TOP);
        layout.addView(postText);

        Button imageButton = new Button(this);
        imageButton.setText("Select Photo");
        layout.addView(imageButton);

        imageButton.setOnClickListener(v -> selectMedia(PICK_IMAGE));

        Button videoButton = new Button(this);
        videoButton.setText("Select Video");
        layout.addView(videoButton);

        videoButton.setOnClickListener(v -> selectMedia(PICK_VIDEO));

        selectedMedia = new TextView(this);
        selectedMedia.setText("No media selected");
        selectedMedia.setTextSize(15);
        selectedMedia.setPadding(5, 15, 5, 15);
        layout.addView(selectedMedia);

        publishButton = new Button(this);
        publishButton.setText("Publish Post");
        layout.addView(publishButton);

        publishButton.setOnClickListener(v -> publishPost());

        setContentView(layout);
    }

    private void selectMedia(int type) {

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(type == PICK_IMAGE ? "image/*" : "video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(
                Intent.createChooser(intent, "Select Media"),
                type
        );
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {

            mediaUri = data.getData();

            if (requestCode == PICK_IMAGE) {
                mediaType = "image";
                selectedMedia.setText("Photo selected successfully");
            } else if (requestCode == PICK_VIDEO) {
                mediaType = "video";
                selectedMedia.setText("Video selected successfully");
            }
        }
    }

    private void publishPost() {

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this,
                    "Please login first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String text = postText.getText().toString().trim();

        if (text.isEmpty() && mediaUri == null) {
            Toast.makeText(this,
                    "Write something or select media",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        publishButton.setEnabled(false);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Publishing post...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        if (mediaUri != null) {
            uploadMedia(text);
        } else {
            savePost(text, "", "");
        }
    }

    private void uploadMedia(String text) {

        String uid = auth.getCurrentUser().getUid();

        String fileName = UUID.randomUUID().toString();

        StorageReference fileRef = storage
                .getReference()
                .child("post_media")
                .child(uid)
                .child(fileName);

        fileRef.putFile(mediaUri)
                .addOnSuccessListener(taskSnapshot ->
                        fileRef.getDownloadUrl()
                                .addOnSuccessListener(uri ->
                                        savePost(
                                                text,
                                                uri.toString(),
                                                mediaType
                                        )
                                )
                                .addOnFailureListener(e ->
                                        showError(e.getMessage())
                                )
                )
                .addOnFailureListener(e ->
                        showError(e.getMessage())
                );
    }

    private void savePost(
            String text,
            String mediaUrl,
            String type
    ) {

        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(userDocument -> {

                    String authorName = userDocument.getString("name");

                    if (authorName == null || authorName.trim().isEmpty()) {
                        authorName = "AYNA User";
                    }

                    Map<String, Object> post = new HashMap<>();

                    String postId = UUID.randomUUID().toString();

                    post.put("postId", postId);
                    post.put("uid", uid);
                    post.put("authorName", authorName);
                    post.put("text", text);
                    post.put("mediaUrl", mediaUrl);
                    post.put("mediaType", type);
                    post.put("createdAt", FieldValue.serverTimestamp());
                    post.put("likesCount", 0);
                    post.put("commentsCount", 0);

                    db.collection("posts")
                            .document(postId)
                            .set(post)
                            .addOnSuccessListener(unused -> {

                                finishProgress();

                                Toast.makeText(
                                        this,
                                        "Post published successfully!",
                                        Toast.LENGTH_LONG
                                ).show();

                                finish();
                            })
                            .addOnFailureListener(e ->
                                    showError(e.getMessage())
                            );
                })
                .addOnFailureListener(e ->
                        showError(e.getMessage())
                );
    }

    private void showError(String message) {

        finishProgress();

        Toast.makeText(
                this,
                "Error: " + message,
                Toast.LENGTH_LONG
        ).show();
    }

    private void finishProgress() {

        publishButton.setEnabled(true);

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
