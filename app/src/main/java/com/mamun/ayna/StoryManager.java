
package com.mamun.ayna;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StoryManager {

    private static final long STORY_DURATION =
            24L * 60L * 60L * 1000L;

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public StoryManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface StoryCallback {
        void onLoaded(List<Map<String, Object>> stories);
        void onError(String error);
    }

    public interface ViewersCallback {
        void onLoaded(List<Map<String, Object>> viewers);
        void onError(String error);
    }

    private String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private boolean isValid(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private DocumentReference getStoryReference(String storyId) {
        return db.collection("stories").document(storyId);
    }

    // Create text, photo or video story
    public void createStory(
            String userName,
            String text,
            String mediaUrl,
            String mediaType,
            ActionCallback callback) {

        String uid = getCurrentUid();

        if (!isValid(uid)) {
            callback.onError("Please login first");
            return;
        }

        String type = mediaType == null
                ? "text" : mediaType.trim().toLowerCase();

        if (!type.equals("text")
                && !type.equals("image")
                && !type.equals("video")) {
            callback.onError("Invalid story type");
            return;
        }

        String body = text == null ? "" : text.trim();

        if (body.isEmpty() && !isValid(mediaUrl)) {
            callback.onError("Add text or photo/video");
            return;
        }

        if (!type.equals("text") && !isValid(mediaUrl)) {
            callback.onError("Media URL is missing");
            return;
        }

        Map<String, Object> story = new HashMap<>();
        story.put("userId", uid);
        story.put("userName",
                isValid(userName) ? userName.trim() : "AYNA User");
        story.put("text", body);
        story.put("mediaUrl", mediaUrl);
        story.put("mediaType", type);
        story.put("createdAt", FieldValue.serverTimestamp());
        story.put("expiresAt",
                System.currentTimeMillis() + STORY_DURATION);
        story.put("viewCount", 0);

        db.collection("stories")
                .add(story)
                .addOnSuccessListener(ref -> callback.onSuccess())
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage()));
    }

    // Listen to all active stories
    public ListenerRegistration listenStories(
            StoryCallback callback) {

        String uid = getCurrentUid();

        if (!isValid(uid)) {
            callback.onError("Please login first");
            return null;
        }

        return db.collection("stories")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }

                    List<Map<String, Object>> result =
                            new ArrayList<>();

                    long now = System.currentTimeMillis();

                    if (snapshot != null) {
                        for (DocumentSnapshot document :
                                snapshot.getDocuments()) {

                            Long expiry = document.getLong("expiresAt");

                            if (expiry != null && expiry <= now) {
                                continue;
                            }

                            Map<String, Object> item =
                                    new HashMap<>(document.getData());

                            item.put("storyId", document.getId());
                            result.add(item);
                        }
                    }

                    callback.onLoaded(result);
                });
    }

    // Listen to stories created by a particular user
    public ListenerRegistration listenUserStories(
            String userId,
            StoryCallback callback) {

        if (!isValid(userId)) {
            callback.onError("Invalid user ID");
            return null;
        }

        return db.collection("stories")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }

                    List<Map<String, Object>> result =
                            new ArrayList<>();

                    long now = System.currentTimeMillis();

                    if (snapshot != null) {
                        for (DocumentSnapshot document :
                                snapshot.getDocuments()) {

                            Long expiry = document.getLong("expiresAt");

                            if (expiry != null && expiry <= now) {
                                continue;
                            }

                            Map<String, Object> item =
                                    new HashMap<>(document.getData());

                            item.put("storyId", document.getId());
                            result.add(item);
                        }
                    }

                    callback.onLoaded(result);
                });
    }

    // Record a story view only once per user
    public void viewStory(
            String storyId,
            ActionCallback callback) {

        String uid = getCurrentUid();

        if (!isValid(uid) || !isValid(storyId)) {
            callback.onError("Invalid user or story ID");
            return;
        }

        DocumentReference storyRef =
                getStoryReference(storyId);

        DocumentReference viewRef =
                storyRef.collection("views").document(uid);

        db.runTransaction(transaction -> {

            DocumentSnapshot story = transaction.get(storyRef);

            if (!story.exists()) {
                throw new IllegalStateException("Story not found");
            }

            Long expiry = story.getLong("expiresAt");

            if (expiry != null
                    && expiry <= System.currentTimeMillis()) {
                throw new IllegalStateException("Story has expired");
            }

            String ownerId = story.getString("userId");

            if (uid.equals(ownerId)) {
                return false;
            }

            DocumentSnapshot existingView =
                    transaction.get(viewRef);

            if (existingView.exists()) {
                return false;
            }

            Map<String, Object> viewData = new HashMap<>();
            viewData.put("userId", uid);
            viewData.put("viewedAt",
                    FieldValue.serverTimestamp());

            transaction.set(viewRef, viewData);
            transaction.update(
                    storyRef,
                    "viewCount",
                    FieldValue.increment(1)
            );

            return true;

        }).addOnSuccessListener(added ->
                callback.onSuccess())
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage()));
    }

    // Listen to story viewers
    public ListenerRegistration listenStoryViewers(
            String storyId,
            ViewersCallback callback) {

        String uid = getCurrentUid();

        if (!isValid(uid) || !isValid(storyId)) {
            callback.onError("Invalid user or story ID");
            return null;
        }

        DocumentReference storyRef =
                getStoryReference(storyId);

        return storyRef.get().addOnSuccessListener(story -> {

            if (!story.exists()) {
                callback.onError("Story not found");
                return;
            }

            if (!uid.equals(story.getString("userId"))) {
                callback.onError(
                        "Only the story owner can see viewers"
                );
                return;
            }

            storyRef.collection("views")
                    .orderBy("viewedAt", Query.Direction.DESCENDING)
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

                                item.put("viewerId", document.getId());
                                result.add(item);
                            }
                        }

                        callback.onLoaded(result);
                    });

        }).addOnFailureListener(e ->
                callback.onError(e.getMessage()))
                .getResult() == null ? null : null;
    }

    // Delete a story (only its owner)
    public void deleteStory(
            String storyId,
            ActionCallback callback) {

        String uid = getCurrentUid();

        if (!isValid(uid) || !isValid(storyId)) {
            callback.onError("Invalid user or story ID");
            return;
        }

        DocumentReference storyRef =
                getStoryReference(storyId);

        storyRef.get().addOnSuccessListener(document -> {

            if (!document.exists()) {
                callback.onError("Story not found");
                return;
            }

            String ownerId = document.getString("userId");

            if (!uid.equals(ownerId)) {
                callback.onError(
                        "You can only delete your own story"
                );
                return;
            }

            storyRef.delete()
                    .addOnSuccessListener(unused ->
                            callback.onSuccess())
                    .addOnFailureListener(e ->
                            callback.onError(e.getMessage()));

        }).addOnFailureListener(e ->
                callback.onError(e.getMessage()));
    }

    // Stop a live listener
    public void removeListener(ListenerRegistration listener) {
        if (listener != null) {
            listener.remove();
        }
    }
}
