
package com.mamun.ayna;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportManager {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public ReportManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface ReportsCallback {
        void onLoaded(List<Map<String, Object>> reports);
        void onError(String error);
    }

    private String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private boolean valid(String value) {
        return value != null && !value.trim().isEmpty();
    }

    // Report a post
    public void reportPost(
            String postId,
            String postOwnerId,
            String reason,
            String details,
            ActionCallback callback) {

        submitReport(
                "post",
                postId,
                postOwnerId,
                reason,
                details,
                callback
        );
    }

    // Report a user
    public void reportUser(
            String reportedUserId,
            String reason,
            String details,
            ActionCallback callback) {

        submitReport(
                "user",
                reportedUserId,
                reportedUserId,
                reason,
                details,
                callback
        );
    }

    // Save report in Firestore
    private void submitReport(
            String targetType,
            String targetId,
            String targetOwnerId,
            String reason,
            String details,
            ActionCallback callback) {

        String reporterId = currentUid();

        if (!valid(reporterId)) {
            callback.onError("Please login first");
            return;
        }

        if (!valid(targetId) || !valid(reason)) {
            callback.onError("Report information is incomplete");
            return;
        }

        if (reporterId.equals(targetOwnerId)) {
            callback.onError("You cannot report yourself");
            return;
        }

        Map<String, Object> report = new HashMap<>();
        report.put("reporterId", reporterId);
        report.put("targetType", targetType);
        report.put("targetId", targetId);
        report.put("targetOwnerId", targetOwnerId);
        report.put("reason", reason.trim());
        report.put("details", details == null ? "" : details.trim());
        report.put("status", "pending");
        report.put("createdAt", new Date());

        db.collection("reports")
                .add(report)
                .addOnSuccessListener(documentReference ->
                        callback.onSuccess())
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage()));
    }

    // Listen to reports submitted by current user
    public ListenerRegistration listenMyReports(
            ReportsCallback callback) {

        String uid = currentUid();

        if (!valid(uid)) {
            callback.onError("Please login first");
            return null;
        }

        return db.collection("reports")
                .whereEqualTo("reporterId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
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

                            item.put("reportId", document.getId());
                            result.add(item);
                        }
                    }

                    callback.onLoaded(result);
                });
    }

    // Cancel a pending report submitted by current user
    public void cancelMyReport(
            String reportId,
            ActionCallback callback) {

        String uid = currentUid();

        if (!valid(uid) || !valid(reportId)) {
            callback.onError("Invalid report information");
            return;
        }

        db.collection("reports")
                .document(reportId)
                .get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()) {
                        callback.onError("Report not found");
                        return;
                    }

                    String reporterId =
                            document.getString("reporterId");

                    String status =
                            document.getString("status");

                    if (!uid.equals(reporterId)) {
                        callback.onError("Permission denied");
                        return;
                    }

                    if (!"pending".equals(status)) {
                        callback.onError(
                                "Only pending reports can be cancelled"
                        );
                        return;
                    }

                    document.getReference()
                            .delete()
                            .addOnSuccessListener(unused ->
                                    callback.onSuccess())
                            .addOnFailureListener(e ->
                                    callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage()));
    }

    // Stop report listener
    public void removeListener(ListenerRegistration listener) {
        if (listener != null) {
            listener.remove();
        }
    }
}
