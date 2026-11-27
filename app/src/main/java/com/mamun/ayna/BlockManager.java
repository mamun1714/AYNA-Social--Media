
package com.mamun.ayna;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockManager {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public BlockManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface StatusCallback {
        void onResult(boolean isBlocked);
        void onError(String error);
    }

    public interface BlockListCallback {
        void onLoaded(List<Map<String, Object>> users);
        void onError(String error);
    }

    private String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private boolean isValid(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private com.google.firebase.firestore.CollectionReference getBlockCollection(
            String uid) {

        return db.collection("users")
                .document(uid)
                .collection("blockedUsers");
    }

    // Block a user
    public void blockUser(
            String userId,
            ActionCallback callback) {

        String currentUid = getCurrentUid();

        if (!isValid(currentUid)) {
            callback.onError("Please login first");
            return;
        }

        if (!isValid(userId)) {
            callback.onError("Invalid user ID");
            return;
        }

        if (currentUid.equals(userId)) {
            callback.onError("You cannot block yourself");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("blockedAt", new Date());

        getBlockCollection(currentUid)
                .document(userId)
                .set(data)
                .addOnSuccessListener(unused ->
                        callback.onSuccess())
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage()));
    }

    // Unblock a user
    public void unblockUser(
            String userId,
            ActionCallback callback) {

        String currentUid = getCurrentUid();

        if (!isValid(currentUid) || !isValid(userId)) {
            callback.onError("Invalid user information");
            return;
        }

        getBlockCollection(currentUid)
                .document(userId)
                .delete()
                .addOnSuccessListener(unused ->
                        callback.onSuccess())
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage()));
    }

    // Check whether a user is blocked by current user
    public void isUserBlocked(
            String userId,
            StatusCallback callback) {

        String currentUid = getCurrentUid();

        if (!isValid(currentUid) || !isValid(userId)) {
            callback.onError("Invalid user information");
            return;
        }

        getBlockCollection(currentUid)
                .document(userId)
                .get()
                .addOnSuccessListener(document ->
                        callback.onResult(document.exists()))
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage()));
    }

    // Listen to current user's blocked users
    public ListenerRegistration listenBlockedUsers(
            BlockListCallback callback) {

        String currentUid = getCurrentUid();

        if (!isValid(currentUid)) {
            callback.onError("Please login first");
            return null;
        }

        return getBlockCollection(currentUid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }

                    List<Map<String, Object>> result =
                            new ArrayList<>();

                    if (snapshot != null) {
                        for (DocumentSnapshot document :
                                snapshot.getDocuments()) {

                            Map<String, Object> item =
                                    new HashMap<>(document.getData());

                            item.put("blockedUserId", document.getId());
                            result.add(item);
                        }
                    }

                    callback.onLoaded(result);
                });
    }

    // Check whether another user has blocked current user
    public void isBlockedByUser(
            String otherUserId,
            StatusCallback callback) {

        String currentUid = getCurrentUid();

        if (!isValid(currentUid) || !isValid(otherUserId)) {
            callback.onError("Invalid user information");
            return;
        }

        getBlockCollection(otherUserId)
                .document(currentUid)
                .get()
                .addOnSuccessListener(document ->
                        callback.onResult(document.exists()))
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage()));
    }

    // Stop live blocked users listener
    public void removeListener(ListenerRegistration listener) {
        if (listener != null) {
            listener.remove();
        }
    }
}
