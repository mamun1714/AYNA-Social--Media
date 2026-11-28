
package com.mamun.ayna;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MonetizationAdminActivity extends Activity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private LinearLayout root;
    private TextView statusText;
    private Button refreshButton;

    private final int BLUE = Color.rgb(23, 105, 170);
    private final int DARK = Color.rgb(35, 45, 60);
    private final int GRAY = Color.rgb(105, 115, 130);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            showMessage("Please login first");
            finish();
            return;
        }

        createScreen();
        checkAdminAccess();
    }

    private void createScreen() {

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(245, 247, 251));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(25));

        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("AYNA Monetization Admin");
        title.setTextSize(23);
        title.setTextColor(BLUE);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(
                "Review creator applications, earnings and payout accounts."
        );
        subtitle.setTextSize(14);
        subtitle.setTextColor(GRAY);
        subtitle.setPadding(0, dp(8), 0, dp(15));
        root.addView(subtitle);

        statusText = new TextView(this);
        statusText.setText("Checking administrator access...");
        statusText.setTextSize(14);
        statusText.setTextColor(DARK);
        root.addView(statusText);

        refreshButton = new Button(this);
        refreshButton.setText("Refresh Applications");
        refreshButton.setOnClickListener(v -> loadApplications());
        root.addView(refreshButton);

        setContentView(scroll);
    }

    private void checkAdminAccess() {

        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {

                    String role = document.getString("role");

                    if (!"admin".equalsIgnoreCase(
                            role == null ? "" : role.trim())) {

                        statusText.setText(
                                "Access denied. Administrator permission required."
                        );

                        refreshButton.setEnabled(false);
                        showMessage("You are not an administrator.");
                        return;
                    }

                    statusText.setText("Administrator access verified.");
                    loadApplications();
                })
                .addOnFailureListener(error -> {

                    statusText.setText(
                            "Could not verify administrator access."
                    );

                    refreshButton.setEnabled(false);
                    showMessage("Admin verification failed.");
                });
    }

    private void loadApplications() {

        if (auth.getCurrentUser() == null) {
            return;
        }

        refreshButton.setEnabled(false);
        statusText.setText("Loading pending applications...");

        root.removeViews(3, root.getChildCount() - 3);

        db.collection("monetizationApplications")
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(snapshot -> {

                    refreshButton.setEnabled(true);

                    if (snapshot.isEmpty()) {
                        statusText.setText(
                                "No pending monetization applications."
                        );
                        return;
                    }

                    statusText.setText(
                            "Pending applications: " + snapshot.size()
                    );

                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        addApplicationItem(document);
                    }
                })
                .addOnFailureListener(error -> {

                    refreshButton.setEnabled(true);
                    statusText.setText("Could not load applications.");

                    showMessage(
                            "Application loading failed: " +
                            error.getMessage()
                    );
                });
    }

    private void addApplicationItem(DocumentSnapshot document) {

        String creatorUid = document.getId();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(0, dp(8), 0, dp(8));

        TextView creator = new TextView(this);
        creator.setText("Creator UID: " + creatorUid);
        creator.setTextColor(DARK);
        creator.setTextSize(14);
        creator.setTypeface(null, Typeface.BOLD);
        card.addView(creator);

        String followers = String.valueOf(
                document.get("followersAtApplication") == null
                        ? 0
                        : document.get("followersAtApplication")
        );

        String accountAge = String.valueOf(
                document.get("accountAgeDays") == null
                        ? 0
                        : document.get("accountAgeDays")
        );

        TextView info = new TextView(this);
        info.setText(
                "Followers at application: " + followers +
                "\nAccount age: " + accountAge + " days" +
                "\nStatus: " + document.getString("status")
        );
        info.setTextColor(GRAY);
        info.setTextSize(13);
        info.setPadding(0, dp(7), 0, dp(7));
        card.addView(info);

        Button reviewButton = new Button(this);
        reviewButton.setText("Review Creator");
        reviewButton.setOnClickListener(v ->
                showCreatorDetails(creatorUid)
        );

        card.addView(reviewButton);
        root.addView(card, cardParams);
    }

    private void showCreatorDetails(String creatorUid) {

        statusText.setText("Loading creator information...");

        DocumentReference earningRef = db
                .collection("creatorMonetization")
                .document(creatorUid);

        DocumentReference payoutRef = db
                .collection("users")
                .document(creatorUid)
                .collection("private")
                .document("payout");

        earningRef.get().addOnSuccessListener(earning -> {

            payoutRef.get().addOnSuccessListener(payout -> {

                String details =
                        "Creator UID: " + creatorUid +
                        "\n\n--- EARNINGS ---" +
                        "\nVideo Earnings: " +
                        valueOf(earning, "videoEarnings") +
                        "\nReels Earnings: " +
                        valueOf(earning, "reelsEarnings") +
                        "\nPhoto Earnings: " +
                        valueOf(earning, "photoEarnings") +
                        "\nText Earnings: " +
                        valueOf(earning, "textEarnings") +
                        "\nEstimated Earnings: " +
                        valueOf(earning, "estimatedEarnings") +
                        "\nTotal Earnings: " +
                        valueOf(earning, "totalEarnings") +
                        "\nTotal Views: " +
                        valueOf(earning, "totalViews") +
                        "\nWatch Time (minutes): " +
                        valueOf(earning, "watchTimeMinutes") +
                        "\nEngagement: " +
                        valueOf(earning, "engagement") +
                        "\n\n--- MONETIZATION ---" +
                        "\nApplication Status: " +
                        valueOf(earning, "applicationStatus") +
                        "\nContent Policy: " +
                        valueOf(earning, "contentPolicyStatus") +
                        "\nPayment History Summary: " +
                        valueOf(earning, "paymentHistorySummary") +
                        "\n\n--- BANK PAYOUT ---" +
                        "\nBank: " +
                        valueOf(payout, "bankName") +
                        "\nCountry: " +
                        valueOf(payout, "country") +
                        "\nCurrency: " +
                        valueOf(payout, "currency") +
                        "\nAccount Holder: " +
                        valueOf(payout, "accountName") +
                        "\nAccount Number: " +
                        maskAccount(
                                valueOf(payout, "accountNumber")
                        ) +
                        "\nVerification: " +
                        valueOf(payout, "verificationStatus") +
                        "\nPayout Status: " +
                        valueOf(payout, "payoutStatus");

                statusText.setText("Creator details loaded.");

                showReviewDialog(
                        creatorUid,
                        details,
                        payout.exists()
                );

            }).addOnFailureListener(error ->
                    showMessage("Could not load payout information.")
            );

        }).addOnFailureListener(error ->
                showMessage("Could not load creator earnings.")
        );
    }

    private void showReviewDialog(
            String creatorUid,
            String details,
            boolean hasPayout
    ) {

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);

        builder.setTitle("Creator Review");
        builder.setMessage(details);

        builder.setPositiveButton("Approve Monetization",
                (dialog, which) ->
                        updateApplication(creatorUid, "approved")
        );

        builder.setNegativeButton("Reject Monetization",
                (dialog, which) ->
                        updateApplication(creatorUid, "rejected")
        );

        builder.setNeutralButton("More Options",
                (dialog, which) ->
                        showPayoutOptions(creatorUid, hasPayout)
        );

        builder.show();
    }

    private void showPayoutOptions(
            String creatorUid,
            boolean hasPayout
    ) {

        if (!hasPayout) {
            showMessage("Creator has not submitted a bank account.");
            return;
        }

        String[] options = {
                "Approve Bank Verification",
                "Reject Bank Verification",
                "Refresh Creator Details"
        };

        new AlertDialog.Builder(this)
                .setTitle("Payout Review")
                .setItems(options, (dialog, which) -> {

                    if (which == 0) {
                        updatePayout(creatorUid, true);
                    } else if (which == 1) {
                        updatePayout(creatorUid, false);
                    } else {
                        showCreatorDetails(creatorUid);
                    }
                })
                .show();
    }

    private void updateApplication(
            String creatorUid,
            String newStatus
    ) {

        String adminUid = auth.getCurrentUser().getUid();

        Map<String, Object> updates = new HashMap<>();

        updates.put("status", newStatus);
        updates.put("reviewedBy", adminUid);
        updates.put("reviewedAt", FieldValue.serverTimestamp());

        db.collection("monetizationApplications")
                .document(creatorUid)
                .update(updates)
                .addOnSuccessListener(unused -> {

                    Map<String, Object> creatorUpdates =
                            new HashMap<>();

                    creatorUpdates.put(
                            "applicationStatus",
                            newStatus
                    );

                    db.collection("creatorMonetization")
                            .document(creatorUid)
                            .set(
                                    creatorUpdates,
                                    com.google.firebase.firestore.SetOptions.merge()
                            )
                            .addOnSuccessListener(done -> {

                                showMessage(
                                        "Application " + newStatus + "."
                                );

                                loadApplications();
                            })
                            .addOnFailureListener(error ->
                                    showMessage(
                                            "Application updated, but creator status update failed."
                                    )
                            );
                })
                .addOnFailureListener(error ->
                        showMessage(
                                "Could not update application: " +
                                error.getMessage()
                        )
                );
    }

    private void updatePayout(
            String creatorUid,
            boolean approved
    ) {

        String adminUid = auth.getCurrentUser().getUid();

        Map<String, Object> updates = new HashMap<>();

        updates.put(
                "verificationStatus",
                approved ? "VERIFIED" : "REJECTED"
        );

        updates.put(
                "payoutStatus",
                approved ? "ACTIVE" : "NOT_ACTIVE"
        );

        updates.put("reviewedBy", adminUid);
        updates.put("reviewedAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(creatorUid)
                .collection("private")
                .document("payout")
                .update(updates)
                .addOnSuccessListener(unused -> {

                    showMessage(
                            approved
                                    ? "Bank verification approved."
                                    : "Bank verification rejected."
                    );

                    showCreatorDetails(creatorUid);
                })
                .addOnFailureListener(error ->
                        showMessage(
                                "Payout update failed: " +
                                error.getMessage()
                        )
                );
    }

    private String valueOf(
            DocumentSnapshot document,
            String field
    ) {

        if (document == null || !document.exists()) {
            return "Not available";
        }

        Object value = document.get(field);

        return value == null
                ? "Not available"
                : String.valueOf(value);
    }

    private String maskAccount(String account) {

        if (account == null ||
                account.equals("Not available") ||
                account.length() <= 4) {
            return "Hidden";
        }

        return "******" +
                account.substring(account.length() - 4);
    }

    private int dp(int value) {
        return (int) (
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    private void showMessage(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }
}
