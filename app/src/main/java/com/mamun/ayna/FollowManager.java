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

public class FollowManager {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public FollowManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public interface ActionCallback {
        void onSuccess(boolean following);
        void onError(Exception exception);
    }

    public interface StatusCallback {
        void onResult(boolean following);
        void onError(Exception exception);
    }

    public interface CountCallback {
        void onCountChanged(long count);
        void onError(Exception exception);
    }

    // Follow অথবা Unfollow
    public void toggleFollow(
            String targetUserId,
            ActionCallback callback) {

        if (callback == null) return;

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError(
                    new IllegalStateException("আগে Login করতে হবে")
            );
            return;
        }

        String currentUid = user.getUid();

        if (targetUserId == null || targetUserId.trim().isEmpty()) {
            callback.onError(
                    new IllegalArgumentException("User ID পাওয়া যায়নি")
            );
            return;
        }

        if (currentUid.equals(targetUserId)) {
            callback.onError(
                    new IllegalArgumentException("নিজেকে Follow করা যাবে না")
            );
            return;
        }

        DocumentReference followingRef =
                db.collection("users")
                        .document(currentUid)
                        .collection("following")
                        .document(targetUserId);

        DocumentReference followerRef =
                db.collection("users")
                        .document(targetUserId)
                        .collection("followers")
                        .document(currentUid);

        DocumentReference currentUserRef =
                db.collection("users").document(currentUid);

        DocumentReference targetUserRef =
                db.collection("users").document(targetUserId);

        db.runTransaction(transaction -> {

            DocumentSnapshot targetSnapshot =
                    transaction.get(targetUserRef);

            DocumentSnapshot followingSnapshot =
                    transaction.get(followingRef);

            DocumentSnapshot followerSnapshot =
                    transaction.get(followerRef);

            if (!targetSnapshot.exists()) {
                throw new IllegalStateException("ব্যবহারকারী পাওয়া যায়নি");
            }

            if (followingSnapshot.exists()
                    || followerSnapshot.exists()) {

                transaction.delete(followingRef);
                transaction.delete(followerRef);

                transaction.update(
                        currentUserRef,
                        "followingCount",
                        FieldValue.increment(-1)
                );

                transaction.update(
                        targetUserRef,
                        "followersCount",
                        FieldValue.increment(-1)
                );

                return false;

            } else {

                Map<String, Object> followData =
                        new HashMap<>();

                followData.put("userId", currentUid);
                followData.put(
                        "createdAt",
                        FieldValue.serverTimestamp()
                );

                Map<String, Object> followerData =
                        new HashMap<>();

                followerData.put("userId", targetUserId);
                followerData.put(
                        "createdAt",
                        FieldValue.serverTimestamp()
                );

                transaction.set(followingRef, followData);
                transaction.set(followerRef, followerData);

                transaction.update(
                        currentUserRef,
                        "followingCount",
                        FieldValue.increment(1)
                );

                transaction.update(
                        targetUserRef,
                        "followersCount",
                        FieldValue.increment(1)
                );

                return true;
            }

        }).addOnSuccessListener(callback::onSuccess)
          .addOnFailureListener(callback::onError);
    }

    // Follow Status পরীক্ষা
    public void checkFollowing(
            String targetUserId,
            StatusCallback callback) {

        if (callback == null) return;

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError(
                    new IllegalStateException("আগে Login করতে হবে")
            );
            return;
        }

        if (targetUserId == null || targetUserId.trim().isEmpty()) {
            callback.onError(
                    new IllegalArgumentException("User ID পাওয়া যায়নি")
            );
            return;
        }

        if (user.getUid().equals(targetUserId)) {
            callback.onResult(false);
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .collection("following")
                .document(targetUserId)
                .get()
                .addOnSuccessListener(snapshot ->
                        callback.onResult(snapshot.exists())
                )
                .addOnFailureListener(callback::onError);
    }

    // Follower Count Live Update
    public ListenerRegistration listenFollowerCount(
            String userId,
            CountCallback callback) {

        if (!isValid(userId) || callback == null) {
            return null;
        }

        return db.collection("users")
                .document(userId)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error);
                        return;
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        callback.onError(
                                new IllegalStateException("User পাওয়া যায়নি")
                        );
                        return;
                    }

                    Long count = snapshot.getLong("followersCount");

                    callback.onCountChanged(
                            count == null ? 0 : count
                    );
                });
    }

    // Following Count Live Update
    public ListenerRegistration listenFollowingCount(
            String userId,
            CountCallback callback) {

        if (!isValid(userId) || callback == null) {
            return null;
        }

        return db.collection("users")
                .document(userId)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error);
                        return;
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        callback.onError(
                                new IllegalStateException("User পাওয়া যায়নি")
                        );
                        return;
                    }

                    Long count = snapshot.getLong("followingCount");

                    callback.onCountChanged(
                            count == null ? 0 : count
                    );
                });
    }

    // Follower List
    public ListenerRegistration listenFollowers(
            String userId,
            UserListCallback callback) {

        if (!isValid(userId) || callback == null) {
            return null;
        }

        return db.collection("users")
                .document(userId)
                .collection("followers")
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error);
                        return;
                    }

                    if (snapshot == null) return;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        callback.onUser(doc);
                    }
                });
    }

    // Following List
    public ListenerRegistration listenFollowing(
            String userId,
            UserListCallback callback) {

        if (!isValid(userId) || callback == null) {
            return null;
        }

        return db.collection("users")
                .document(userId)
                .collection("following")
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error);
                        return;
                    }

                    if (snapshot == null) return;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        callback.onUser(doc);
                    }
                });
    }

    public interface UserListCallback {
        void onUser(DocumentSnapshot user);
        void onError(Exception exception);
    }

    private boolean isValid(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
