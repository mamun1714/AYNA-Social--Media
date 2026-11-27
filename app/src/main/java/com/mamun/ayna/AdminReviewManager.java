
package com.mamun.ayna;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminReviewManager {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public AdminReviewManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public interface ReviewCallback {
        void onLoaded(List<Map<String, Object>> reports);
        void onError(String error);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String error);
    }

    private String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private boolean isValid(String value) {
        return value != null && !value.trim().isEmpty();
    }

    // Listen to pending reports for admin review
    public ListenerRegistration listenPendingReports(
            ReviewCallback callback) {

        String uid = getCurrentUid();

        if (!isValid(uid)) {
            callback.onError("Please login first");
            return null;
        }

        return db.collection("reports")
                .whereEqualTo("status", "pending")
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

    // Listen to reports by target type: post or user
    public ListenerRegistration listenReportsByType(
            String targetType,
            ReviewCallback callback) {

        String uid = getCurrentUid();

        if (!isValid(uid)) {
            callback.onError("Please login first");
            return null;
        }

        if (!"post".equals(targetType)
                && !"user".equals(targetType)) {
            callback.onError("Invalid report type");
            return null;
        }

        return db.collection("reports")
                .whereEqualTo("status", "pending")
                .whereEqualTo("targetType", targetType)
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

    // Approve a report
    public void approveReport(
            String reportId,
            String adminNote,
            ActionCallback callback) {

        updateReportStatus(
                reportId,
                "approved",
                adminNote,
                callback
        );
    }

    // Reject a report
    public void rejectReport(
            String reportId,
            String adminNote,
            ActionCallback callback) {

        updateReportStatus(
                reportId,
                "rejected",
                adminNote,
                callback
        );
    }

    // Update report review status
    private void updateReportStatus(
            String reportId,
            String newStatus,
            String adminNote,
            ActionCallback callback) {

        String adminId = getCurrentUid();

        if (!isValid(adminId) || !isValid(reportId)) {
            callback.onError("Invalid admin or report ID");
            return;
        }

        if (!"approved".equals(newStatus)
                && !"rejected".equals(newStatus)) {
            callback.onError("Invalid review status");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        updates.put("adminNote",
                adminNote == null ? "" : adminNote.trim());
        updates.put("reviewedBy", adminId);
        updates.put("reviewedAt",
                com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("reports")
                .document(reportId)
                .update(updates)
                .addOnSuccessListener(unused ->
                        callback.onSuccess())
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage()));
    }

    // Stop live review listener
    public void removeListener(ListenerRegistration listener) {
        if (listener != null) {
            listener.remove();
        }
    }
}
