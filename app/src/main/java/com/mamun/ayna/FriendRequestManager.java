
package com.mamun.ayna;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FriendRequestManager {

    public interface Callback {
        void onSuccess(String message);
        void onFailure(String error);
    }

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final Set<String> processing = new HashSet<>();

    public FriendRequestManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    private String getCurrentUserId() {
        return auth.getCurrentUser() == null
                ? null : auth.getCurrentUser().getUid();
    }

    // 1. Send Friend Request
    public void sendFriendRequest(
            String targetUserId, Callback callback) {

        String uid = getCurrentUserId();

        if (uid == null || targetUserId == null
                || targetUserId.trim().isEmpty()
                || uid.equals(targetUserId)) {
            callback.onFailure("Invalid user.");
            return;
        }

        if (!processing.add(targetUserId)) return;

        DocumentReference incoming = db.collection("Users")
                .document(uid)
                .collection("FriendRequests")
                .document(targetUserId);

        DocumentReference outgoing = db.collection("Users")
                .document(targetUserId)
                .collection("FriendRequests")
                .document(uid);

        DocumentReference friendship = db.collection("Users")
                .document(uid)
                .collection("Friends")
                .document(targetUserId);

        incoming.get().addOnSuccessListener(incomingDoc -> {

            if (incomingDoc.exists()) {
                finish(targetUserId);
                callback.onFailure(
                        "This user already sent you a request.");
                return;
            }

            outgoing.get().addOnSuccessListener(outgoingDoc -> {

                if (outgoingDoc.exists()) {
                    finish(targetUserId);
                    callback.onFailure(
                            "Friend request already sent.");
                    return;
                }

                friendship.get().addOnSuccessListener(friendDoc -> {

                    if (friendDoc.exists()) {
                        finish(targetUserId);
                        callback.onFailure(
                                "You are already friends.");
                        return;
                    }

                    Map<String, Object> request =
                            new HashMap<>();

                    request.put("senderId", uid);
                    request.put("status", "pending");

                    outgoing.set(request)
                            .addOnSuccessListener(unused -> {
                                finish(targetUserId);
                                callback.onSuccess(
                                        "Friend Request Sent Successfully");
                            })
                            .addOnFailureListener(e -> {
                                finish(targetUserId);
                                callback.onFailure(e.getMessage());
                            });

                }).addOnFailureListener(e -> {
                    finish(targetUserId);
                    callback.onFailure(e.getMessage());
                });

            }).addOnFailureListener(e -> {
                finish(targetUserId);
                callback.onFailure(e.getMessage());
            });

        }).addOnFailureListener(e -> {
            finish(targetUserId);
            callback.onFailure(e.getMessage());
        });
    }

    // 2. Accept Friend Request
    public void acceptFriendRequest(
            String senderId, Callback callback) {

        String uid = getCurrentUserId();

        if (uid == null || senderId == null
                || senderId.trim().isEmpty()
                || uid.equals(senderId)) {
            callback.onFailure("Invalid request.");
            return;
        }

        if (!processing.add(senderId)) return;

        DocumentReference requestRef = db.collection("Users")
                .document(uid)
                .collection("FriendRequests")
                .document(senderId);

        requestRef.get().addOnSuccessListener(snapshot -> {

            if (!snapshot.exists()
                    || !"pending".equals(
                    snapshot.getString("status"))) {

                finish(senderId);
                callback.onFailure("Request not found.");
                return;
            }

            WriteBatch batch = db.batch();

            Map<String, Object> myFriend =
                    new HashMap<>();
            myFriend.put("friendId", senderId);
            myFriend.put("timestamp",
                    FieldValue.serverTimestamp());

            Map<String, Object> senderFriend =
                    new HashMap<>();
            senderFriend.put("friendId", uid);
            senderFriend.put("timestamp",
                    FieldValue.serverTimestamp());

            batch.set(
                    db.collection("Users").document(uid)
                            .collection("Friends").document(senderId),
                    myFriend
            );

            batch.set(
                    db.collection("Users").document(senderId)
                            .collection("Friends").document(uid),
                    senderFriend
            );

            batch.delete(requestRef);

            batch.commit()
                    .addOnSuccessListener(unused -> {
                        finish(senderId);
                        callback.onSuccess(
                                "Friend Request Accepted.");
                    })
                    .addOnFailureListener(e -> {
                        finish(senderId);
                        callback.onFailure(e.getMessage());
                    });

        }).addOnFailureListener(e -> {
            finish(senderId);
            callback.onFailure(e.getMessage());
        });
    }

    // 3. Reject Friend Request
    public void rejectFriendRequest(
            String senderId, Callback callback) {

        String uid = getCurrentUserId();

        if (uid == null || senderId == null
                || senderId.trim().isEmpty()
                || uid.equals(senderId)) {
            callback.onFailure("Invalid request.");
            return;
        }

        if (!processing.add(senderId)) return;

        DocumentReference requestRef = db.collection("Users")
                .document(uid)
                .collection("FriendRequests")
                .document(senderId);

        requestRef.get().addOnSuccessListener(snapshot -> {

            if (!snapshot.exists()) {
                finish(senderId);
                callback.onFailure("Request not found.");
                return;
            }

            requestRef.delete()
                    .addOnSuccessListener(unused -> {
                        finish(senderId);
                        callback.onSuccess(
                                "Friend Request Rejected.");
                    })
                    .addOnFailureListener(e -> {
                        finish(senderId);
                        callback.onFailure(e.getMessage());
                    });

        }).addOnFailureListener(e -> {
            finish(senderId);
            callback.onFailure(e.getMessage());
        });
    }

    private void finish(String userId) {
        processing.remove(userId);
    }
}
