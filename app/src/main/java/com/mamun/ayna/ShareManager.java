package com.mamun.ayna;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ShareManager {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public ShareManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public interface ShareCallback {
        void onSuccess(String shareLink);
        void onError(Exception exception);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(Exception exception);
    }

    // Share Link তৈরি
    public String createShareLink(String postId) {

        if (postId == null || postId.trim().isEmpty()) {
            return null;
        }

        return "https://ayna.app/post/" + postId;
    }

    // Share Count এবং Share History সংরক্ষণ
    public void sharePost(
            String postId,
            ShareCallback callback) {

        if (callback == null) return;

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError(
                    new IllegalStateException("আগে Login করতে হবে")
            );
            return;
        }

        if (postId == null || postId.trim().isEmpty()) {
            callback.onError(
                    new IllegalArgumentException("Post ID পাওয়া যায়নি")
            );
            return;
        }

        String uid = user.getUid();

        DocumentReference postRef =
                db.collection("posts").document(postId);

        DocumentReference historyRef =
                db.collection("users")
                        .document(uid)
                        .collection("shareHistory")
                        .document();

        db.runTransaction(transaction -> {

            if (!transaction.get(postRef).exists()) {
                throw new IllegalStateException("Post পাওয়া যায়নি");
            }

            Map<String, Object> history = new HashMap<>();
            history.put("postId", postId);
            history.put("userId", uid);
            history.put("createdAt", FieldValue.serverTimestamp());

            transaction.set(historyRef, history);

            transaction.update(
                    postRef,
                    "sharesCount",
                    FieldValue.increment(1)
            );

            return null;

        }).addOnSuccessListener(result -> {
            callback.onSuccess(createShareLink(postId));
        }).addOnFailureListener(callback::onError);
    }

    // Share Link কপি বা অন্য অ্যাপে পাঠানোর জন্য পাওয়া
    public void getShareLink(
            String postId,
            ShareCallback callback) {

        if (callback == null) return;

        String link = createShareLink(postId);

        if (link == null) {
            callback.onError(
                    new IllegalArgumentException("সঠিক Post ID দিন")
            );
            return;
        }

        callback.onSuccess(link);
    }

    // Share Count Live Update
    public com.google.firebase.firestore.ListenerRegistration
    listenShareCount(
            String postId,
            ShareCountCallback callback) {

        if (callback == null || postId == null
                || postId.trim().isEmpty()) {
            return null;
        }

        return db.collection("posts")
                .document(postId)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error);
                        return;
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        callback.onError(
                                new IllegalStateException("Post পাওয়া যায়নি")
                        );
                        return;
                    }

                    Long count = snapshot.getLong("sharesCount");

                    callback.onCountChanged(
                            count == null ? 0 : count
                    );
                });
    }

    public interface ShareCountCallback {
        void onCountChanged(long count);
        void onError(Exception exception);
    }

    // Share History সংরক্ষণ
    public void saveShareHistory(
            String postId,
            ActionCallback callback) {

        if (callback == null) return;

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError(
                    new IllegalStateException("আগে Login করতে হবে")
            );
            return;
        }

        if (postId == null || postId.trim().isEmpty()) {
            callback.onError(
                    new IllegalArgumentException("Post ID পাওয়া যায়নি")
            );
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("postId", postId);
        data.put("userId", user.getUid());
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(user.getUid())
                .collection("shareHistory")
                .add(data)
                .addOnSuccessListener(ref -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    // Share History Delete
    public void deleteShareHistory(
            String historyId,
            ActionCallback callback) {

        if (callback == null) return;

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError(
                    new IllegalStateException("আগে Login করতে হবে")
            );
            return;
        }

        if (historyId == null || historyId.trim().isEmpty()) {
            callback.onError(
                    new IllegalArgumentException("History ID পাওয়া যায়নি")
            );
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .collection("shareHistory")
                .document(historyId)
                .delete()
                .addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }
}
