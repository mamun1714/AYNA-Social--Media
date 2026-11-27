package com.mamun.ayna;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

public class CommentManager {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public CommentManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(Exception exception);
    }

    public interface CommentCallback {
        void onSuccess(String commentId);
        void onError(Exception exception);
    }

    public interface CommentListCallback {
        void onComment(DocumentSnapshot comment);
        void onError(Exception exception);
    }

    // নতুন Comment তৈরি
    public void addComment(
            String postId,
            String text,
            CommentCallback callback) {

        if (callback == null) return;

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError(new Exception("আগে Login করতে হবে"));
            return;
        }

        if (postId == null || postId.trim().isEmpty()
                || text == null || text.trim().isEmpty()) {
            callback.onError(new Exception("Comment খালি রাখা যাবে না"));
            return;
        }

        String uid = user.getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("text", text.trim());
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {

                    String name = userDoc.getString("name");

                    data.put(
                            "userName",
                            name == null ? "AYNA User" : name
                    );

                    db.collection("posts")
                            .document(postId)
                            .collection("comments")
                            .add(data)
                            .addOnSuccessListener(ref ->
                                    callback.onSuccess(ref.getId()))
                            .addOnFailureListener(callback::onError);

                })
                .addOnFailureListener(callback::onError);
    }

    // Comment Edit
    public void editComment(
            String postId,
            String commentId,
            String newText,
            ActionCallback callback) {

        if (callback == null) return;

        if (!isValid(postId) || !isValid(commentId)
                || !isValid(newText)) {
            callback.onError(new Exception("সঠিক তথ্য দিন"));
            return;
        }

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError(new Exception("আগে Login করতে হবে"));
            return;
        }

        DocumentReference ref = commentRef(postId, commentId);

        ref.get().addOnSuccessListener(snapshot -> {

            if (!snapshot.exists()) {
                callback.onError(new Exception("Comment পাওয়া যায়নি"));
                return;
            }

            if (!user.getUid().equals(snapshot.getString("userId"))) {
                callback.onError(new Exception("নিজের Comment ছাড়া Edit করা যাবে না"));
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("text", newText.trim());
            updates.put("updatedAt", FieldValue.serverTimestamp());

            ref.update(updates)
                    .addOnSuccessListener(v -> callback.onSuccess())
                    .addOnFailureListener(callback::onError);

        }).addOnFailureListener(callback::onError);
    }

    // Comment Delete
    public void deleteComment(
            String postId,
            String commentId,
            ActionCallback callback) {

        if (callback == null) return;

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError(new Exception("আগে Login করতে হবে"));
            return;
        }

        DocumentReference ref = commentRef(postId, commentId);

        ref.get().addOnSuccessListener(snapshot -> {

            if (!snapshot.exists()) {
                callback.onError(new Exception("Comment পাওয়া যায়নি"));
                return;
            }

            if (!user.getUid().equals(snapshot.getString("userId"))) {
                callback.onError(new Exception("নিজের Comment ছাড়া Delete করা যাবে না"));
                return;
            }

            // Reply-সহ Comment Delete
            ref.collection("replies")
                    .get()
                    .addOnSuccessListener(replies -> {

                        com.google.firebase.firestore.WriteBatch batch =
                                db.batch();

                        for (DocumentSnapshot reply : replies.getDocuments()) {
                            batch.delete(reply.getReference());
                        }

                        batch.delete(ref);

                        batch.commit()
                                .addOnSuccessListener(v -> callback.onSuccess())
                                .addOnFailureListener(callback::onError);

                    })
                    .addOnFailureListener(callback::onError);

        }).addOnFailureListener(callback::onError);
    }

    // Comment List Live Listener
    public ListenerRegistration listenComments(
            String postId,
            CommentListCallback callback) {

        if (!isValid(postId) || callback == null) return null;

        return db.collection("posts")
                .document(postId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error);
                        return;
                    }

                    if (snapshot == null) return;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        callback.onComment(doc);
                    }
                });
    }

    // Comment Reply তৈরি
    public void addReply(
            String postId,
            String commentId,
            String text,
            CommentCallback callback) {

        if (callback == null) return;

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError(new Exception("আগে Login করতে হবে"));
            return;
        }

        if (!isValid(postId) || !isValid(commentId)
                || !isValid(text)) {
            callback.onError(new Exception("Reply খালি রাখা যাবে না"));
            return;
        }

        String uid = user.getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("text", text.trim());
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {

                    String name = userDoc.getString("name");

                    data.put(
                            "userName",
                            name == null ? "AYNA User" : name
                    );

                    commentRef(postId, commentId)
                            .collection("replies")
                            .add(data)
                            .addOnSuccessListener(ref ->
                                    callback.onSuccess(ref.getId()))
                            .addOnFailureListener(callback::onError);

                })
                .addOnFailureListener(callback::onError);
    }

    // Reply Edit
    public void editReply(
            String postId,
            String commentId,
            String replyId,
            String newText,
            ActionCallback callback) {

        if (callback == null) return;

        if (!isValid(postId) || !isValid(commentId)
                || !isValid(replyId) || !isValid(newText)) {
            callback.onError(new Exception("সঠিক তথ্য দিন"));
            return;
        }

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError(new Exception("আগে Login করতে হবে"));
            return;
        }

        DocumentReference ref = replyRef(postId, commentId, replyId);

        ref.get().addOnSuccessListener(snapshot -> {

            if (!snapshot.exists()) {
                callback.onError(new Exception("Reply পাওয়া যায়নি"));
                return;
            }

            if (!user.getUid().equals(snapshot.getString("userId"))) {
                callback.onError(new Exception("নিজের Reply ছাড়া Edit করা যাবে না"));
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("text", newText.trim());
            updates.put("updatedAt", FieldValue.serverTimestamp());

            ref.update(updates)
                    .addOnSuccessListener(v -> callback.onSuccess())
                    .addOnFailureListener(callback::onError);

        }).addOnFailureListener(callback::onError);
    }

    // Reply Delete
    public void deleteReply(
            String postId,
            String commentId,
            String replyId,
            ActionCallback callback) {

        if (callback == null) return;

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError(new Exception("আগে Login করতে হবে"));
            return;
        }

        DocumentReference ref = replyRef(postId, commentId, replyId);

        ref.get().addOnSuccessListener(snapshot -> {

            if (!snapshot.exists()) {
                callback.onError(new Exception("Reply পাওয়া যায়নি"));
                return;
            }

            if (!user.getUid().equals(snapshot.getString("userId"))) {
                callback.onError(new Exception("নিজের Reply ছাড়া Delete করা যাবে না"));
                return;
            }

            ref.delete()
                    .addOnSuccessListener(v -> callback.onSuccess())
                    .addOnFailureListener(callback::onError);

        }).addOnFailureListener(callback::onError);
    }

    // Reply List Live Listener
    public ListenerRegistration listenReplies(
            String postId,
            String commentId,
            CommentListCallback callback) {

        if (!isValid(postId) || !isValid(commentId)
                || callback == null) return null;

        return commentRef(postId, commentId)
                .collection("replies")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error);
                        return;
                    }

                    if (snapshot == null) return;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        callback.onComment(doc);
                    }
                });
    }

    private DocumentReference commentRef(
            String postId,
            String commentId) {

        return db.collection("posts")
                .document(postId)
                .collection("comments")
                .document(commentId);
    }

    private DocumentReference replyRef(
            String postId,
            String commentId,
            String replyId) {

        return commentRef(postId, commentId)
                .collection("replies")
                .document(replyId);
    }

    private boolean isValid(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
