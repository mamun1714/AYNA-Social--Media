package com.mamun.ayna;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class LikeManager {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public LikeManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public interface LikeCallback {
        void onSuccess(boolean liked);
        void onError(Exception exception);
    }

    public interface LikeStatusCallback {
        void onResult(boolean liked);
        void onError(Exception exception);
    }

    public interface LikeCountCallback {
        void onCountChanged(long count);
        void onError(Exception exception);
    }

    // Like অথবা Unlike
    public void toggleLike(
            String postId,
            LikeCallback callback) {

        if (callback == null) return;

        if (postId == null || postId.trim().isEmpty()) {
            callback.onError(
                    new IllegalArgumentException("Post ID পাওয়া যায়নি")
            );
            return;
        }

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError(
                    new IllegalStateException("আগে Login করতে হবে")
            );
            return;
        }

        String uid = user.getUid();

        DocumentReference postRef =
                db.collection("posts").document(postId);

        DocumentReference likeRef =
                postRef.collection("likes").document(uid);

        db.runTransaction(transaction -> {

            DocumentSnapshot postSnapshot =
                    transaction.get(postRef);

            DocumentSnapshot likeSnapshot =
                    transaction.get(likeRef);

            if (!postSnapshot.exists()) {
                throw new IllegalStateException(
                        "Post পাওয়া যায়নি"
                );
            }

            if (likeSnapshot.exists()) {

                transaction.delete(likeRef);

                transaction.update(
                        postRef,
                        "likesCount",
                        FieldValue.increment(-1)
                );

                return false;

            } else {

                Map<String, Object> likeData =
                        new HashMap<>();

                likeData.put("userId", uid);
                likeData.put(
                        "createdAt",
                        FieldValue.serverTimestamp()
                );

                transaction.set(likeRef, likeData);

                transaction.update(
                        postRef,
                        "likesCount",
                        FieldValue.increment(1)
                );

                return true;
            }

        }).addOnSuccessListener(callback::onSuccess)
          .addOnFailureListener(callback::onError);
    }

    // ব্যবহারকারী Like করেছে কি না পরীক্ষা
    public void checkLiked(
            String postId,
            LikeStatusCallback callback) {

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

        db.collection("posts")
                .document(postId)
                .collection("likes")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(snapshot ->
                        callback.onResult(snapshot.exists())
                )
                .addOnFailureListener(callback::onError);
    }

    // Like Count পরিবর্তন হলে তাৎক্ষণিক জানা
    public ListenerRegistration listenLikeCount(
            String postId,
            LikeCountCallback callback) {

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
                                new IllegalStateException(
                                        "Post পাওয়া যায়নি"
                                )
                        );
                        return;
                    }

                    Long count = snapshot.getLong("likesCount");

                    callback.onCountChanged(
                            count == null ? 0 : count
                    );
                });
    }
}
