
package com.mamun.ayna;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashSet;
import java.util.HashMap;
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

    private String currentUid() {
        return auth.getCurrentUser() == null
                ? null : auth.getCurrentUser().getUid();
    }

    private void finish(String id) {
        synchronized (processing) {
            processing.remove(id);
        }
    }

    private boolean start(String id) {
        synchronized (processing) {
            return processing.add(id);
        }
    }

    // 1. Send Friend Request
    public void sendFriendRequest(
            String targetId,
            String senderName,
            Callback callback) {

        String uid = currentUid();

        if (uid == null || targetId == null
                || targetId.trim().isEmpty()
                || uid.equals(targetId)) {
            callback.onFailure("Invalid user.");
            return;
        }

        if (!start(targetId)) return;

        DocumentReference incoming = db.collection("users")
                .document(uid)
                .collection("friendRequests")
                .document(targetId);

        DocumentReference outgoing = db.collection("users")
                .document(targetId)
                .collection("friendRequests")
                .document(uid);

        DocumentReference friendship = db.collection("users")
                .document(uid)
                .collection("friends")
                .document(targetId);

        incoming.get().addOnSuccessListener(inDoc -> {

            if (inDoc.exists()) {
                finish(targetId);
                callback.onFailure(
                        "এই ব্যক্তি ইতিমধ্যে আপনাকে Request পাঠিয়েছেন।");
                return;
            }

            outgoing.get().addOnSuccessListener(outDoc -> {

                if (outDoc.exists()) {
                    finish(targetId);
                    callback.onFailure(
                            "Friend Request ইতিমধ্যে পাঠানো হয়েছে।");
                    return;
                }

                friendship.get().addOnSuccessListener(friendDoc -> {

                    if (friendDoc.exists()) {
                        finish(targetId);
                        callback.onFailure(
                                "আপনারা ইতিমধ্যে Friends।");
                        return;
                    }

                    Map<String, Object> request = new HashMap<>();
                    request.put("fromUserId", uid);
                    request.put("fromName",
                            senderName == null ? "AYNA User" : senderName);
                    request.put("status", "pending");
                    request.put("createdAt",
                            FieldValue.serverTimestamp());

                    outgoing.set(request)
                            .addOnSuccessListener(v -> {
                                finish(targetId);
                                callback.onSuccess(
                                        "Friend Request পাঠানো হয়েছে।");
                            })
                            .addOnFailureListener(e -> {
                                finish(targetId);
                                callback.onFailure(e.getMessage());
                            });

                }).addOnFailureListener(e -> {
                    finish(targetId);
                    callback.onFailure(e.getMessage());
                });

            }).addOnFailureListener(e -> {
                finish(targetId);
                callback.onFailure(e.getMessage());
            });

        }).addOnFailureListener(e -> {
            finish(targetId);
            callback.onFailure(e.getMessage());
        });
    }

    // 2. Accept Friend Request
    public void acceptFriendRequest(
            String senderId, Callback callback) {

        String uid = currentUid();

        if (uid == null || senderId == null
                || senderId.trim().isEmpty()
                || uid.equals(senderId)) {
            callback.onFailure("Invalid request.");
            return;
        }

        if (!start(senderId)) return;

        DocumentReference request = db.collection("users")
                .document(uid)
                .collection("friendRequests")
                .document(senderId);

        request.get().addOnSuccessListener(snapshot -> {

            if (!snapshot.exists()
                    || !"pending".equals(
                    snapshot.getString("status"))) {
                finish(senderId);
                callback.onFailure("Pending request পাওয়া যায়নি।");
                return;
            }

            WriteBatch batch = db.batch();

            Map<String, Object> mine = new HashMap<>();
            mine.put("userId", senderId);
            mine.put("createdAt", FieldValue.serverTimestamp());

            Map<String, Object> theirs = new HashMap<>();
            theirs.put("userId", uid);
            theirs.put("createdAt", FieldValue.serverTimestamp());

            batch.set(
                    db.collection("users").document(uid)
                            .collection("friends").document(senderId),
                    mine);

            batch.set(
                    db.collection("users").document(senderId)
                            .collection("friends").document(uid),
                    theirs);

            batch.delete(request);

            batch.commit()
                    .addOnSuccessListener(v -> {
                        finish(senderId);
                        callback.onSuccess("Friend Request গ্রহণ করা হয়েছে।");
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

        String uid = currentUid();

        if (uid == null || senderId == null
                || senderId.trim().isEmpty()
                || uid.equals(senderId)) {
            callback.onFailure("Invalid request.");
            return;
        }

        if (!start(senderId)) return;

        DocumentReference request = db.collection("users")
                .document(uid)
                .collection("friendRequests")
                .document(senderId);

        request.get().addOnSuccessListener(snapshot -> {

            if (!snapshot.exists()
                    || !"pending".equals(
                    snapshot.getString("status"))) {
                finish(senderId);
                callback.onFailure("Pending request পাওয়া যায়নি।");
                return;
            }

            request.delete()
                    .addOnSuccessListener(v -> {
                        finish(senderId);
                        callback.onSuccess("Friend Request প্রত্যাখ্যান করা হয়েছে।");
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
}
