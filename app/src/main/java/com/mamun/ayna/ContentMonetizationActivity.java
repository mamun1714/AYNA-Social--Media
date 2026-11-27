
package com.mamun.ayna;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ContentMonetizationActivity extends Activity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private LinearLayout root;

    private TextView followerValue;
    private TextView followerProgress;
    private TextView accountAgeValue;
    private TextView daysRemaining;
    private TextView eligibilityValue;
    private TextView applicationValue;

    private TextView videoEarnings;
    private TextView reelsEarnings;
    private TextView photoEarnings;
    private TextView textEarnings;
    private TextView estimatedEarnings;
    private TextView totalEarnings;

    private TextView viewsValue;
    private TextView watchTimeValue;
    private TextView engagementValue;
    private TextView contentPolicyValue;
    private TextView payoutStatusValue;

    private TextView paymentHistoryValue;

    private long followers = 0;
    private long accountDays = 0;

    private boolean followerRequirementMet = false;
    private boolean ageRequirementMet = false;

    private final int BLUE = Color.rgb(23, 105, 170);
    private final int DARK = Color.rgb(35, 45, 60);
    private final int GRAY = Color.rgb(110, 120, 135);
    private final int LIGHT = Color.rgb(245, 247, 251);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        buildDashboard();
        loadDashboard();
    }

    private void buildDashboard() {

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(LIGHT);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(25));

        scroll.addView(root);
        setContentView(scroll);

        addTitle("AYNA Creator Monetization");
        addSubtitle("Creator Earnings & Monetization Dashboard");

        addSection("MONETIZATION ELIGIBILITY");

        followerValue = addCard(
                "Total Followers", "Loading..."
        );

        followerProgress = addCard(
                "Follower Requirement", "0 / 1,000"
        );

        accountAgeValue = addCard(
                "Account Age", "Loading..."
        );

        daysRemaining = addCard(
                "180 Days Requirement", "Loading..."
        );

        eligibilityValue = addCard(
                "Eligibility Status", "Checking..."
        );

        Button applyButton = addButton("Apply for Monetization");
        applyButton.setOnClickListener(v -> applyForMonetization());

        root.addView(applyButton);

        applicationValue = addCard(
                "Application Status", "Not submitted"
        );

        addSection("CREATOR EARNINGS");

        videoEarnings = addCard("Video Earnings", "Not available");
        reelsEarnings = addCard("Reels Earnings", "Not available");
        photoEarnings = addCard("Photo Earnings", "Not available");
        textEarnings = addCard("Text Post Earnings", "Not available");

        estimatedEarnings = addCard(
                "Estimated Earnings", "Not available"
        );

        totalEarnings = addCard(
                "Total Confirmed Earnings", "Not available"
        );

        addSection("CREATOR PERFORMANCE");

        viewsValue = addCard("Total Views", "Not available");
        watchTimeValue = addCard("Watch Time", "Not available");
        engagementValue = addCard("Engagement", "Not available");

        addCard(
                "Likes, Comments & Shares",
                "Performance data will appear here"
        );

        addCard(
                "Content-wise Earnings",
                "Content earnings report will appear here"
        );

        addSection("PAYOUT METHODS");

        addSubtitle("Choose your payout account");

        Button bkashButton = addButton("Add / Update bKash");
        bkashButton.setOnClickListener(v ->
                showPayoutDialog("bKash"));

        root.addView(bkashButton);

        Button nagadButton = addButton("Add / Update Nagad");
        nagadButton.setOnClickListener(v ->
                showPayoutDialog("Nagad"));

        root.addView(nagadButton);

        Button rocketButton = addButton("Add / Update Rocket");
        rocketButton.setOnClickListener(v ->
                showPayoutDialog("Rocket"));

        root.addView(rocketButton);

        payoutStatusValue = addCard(
                "Payout Account Status", "Not configured"
        );

        addSection("PAYMENT HISTORY");

        paymentHistoryValue = addCard(
                "Payment Records", "No payment records available"
        );

        addSection("CONTENT POLICY");

        contentPolicyValue = addCard(
                "Content Policy Status", "Not verified"
        );

        addCard(
                "Original Content",
                "Original content verification"
        );

        addCard(
                "Copyright Status",
                "Copyright review status"
        );

        addCard(
                "Community Guidelines",
                "Community Guidelines compliance"
        );

        addCard(
                "Monetization Restriction",
                "Restriction information"
        );

        addCard(
                "Appeal Status",
                "No appeal information available"
        );

        Button refreshButton = addButton("Refresh Dashboard");
        refreshButton.setOnClickListener(v -> loadDashboard());

        root.addView(refreshButton);

        addSubtitle(
                "AYNA Monetization requires eligibility verification. " +
                "Displayed earnings and payment information must come " +
                "from verified backend records."
        );
    }

    private void addTitle(String title) {

        TextView text = new TextView(this);
        text.setText(title);
        text.setTextSize(23);
        text.setTextColor(DARK);
        text.setTypeface(null, Typeface.BOLD);

        root.addView(text);
    }

    private void addSubtitle(String value) {

        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(13);
        text.setTextColor(GRAY);
        text.setPadding(0, dp(7), 0, dp(12));

        root.addView(text);
    }

    private void addSection(String title) {

        TextView text = new TextView(this);
        text.setText(title);
        text.setTextSize(17);
        text.setTextColor(DARK);
        text.setTypeface(null, Typeface.BOLD);
        text.setPadding(0, dp(20), 0, dp(9));

        root.addView(text);
    }

    private TextView addCard(String title, String value) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(9);
        card.setLayoutParams(params);

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextSize(13);
        label.setTextColor(GRAY);

        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(18);
        result.setTextColor(BLUE);
        result.setTypeface(null, Typeface.BOLD);
        result.setPadding(0, dp(6), 0, 0);

        card.addView(label);
        card.addView(result);
        root.addView(card);

        return result;
    }

    private Button addButton(String title) {

        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);

        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(BLUE)
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.topMargin = dp(5);
        params.bottomMargin = dp(7);

        button.setLayoutParams(params);

        return button;
    }

    private void loadDashboard() {

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            Toast.makeText(
                    this,
                    "Please login first",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String uid = user.getUid();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()) {
                        eligibilityValue.setText("User profile not found");
                        return;
                    }

                    loadEligibility(document, user);
                    loadCreatorMonetization(uid);
                    loadPayout(uid);
                })
                .addOnFailureListener(e ->
                        showError("Could not load creator profile"));
    }

    private void loadEligibility(
            DocumentSnapshot document,
            FirebaseUser user) {

        Long count = document.getLong("followersCount");

        followers = count == null ? 0 : count;

        followerValue.setText(
                String.valueOf(followers)
        );

        followerProgress.setText(
                Math.min(followers, 1000) + " / 1,000"
        );

        followerRequirementMet = followers >= 1000;

        long creationTime = 0;

        if (user.getMetadata() != null) {
            creationTime = user.getMetadata()
                    .getCreationTimestamp();
        }

        if (creationTime > 0) {

            long now = System.currentTimeMillis();

            accountDays = TimeUnit.MILLISECONDS.toDays(
                    now - creationTime
            );

            accountAgeValue.setText(
                    accountDays + " days"
            );

            long remaining = Math.max(0, 180 - accountDays);

            daysRemaining.setText(
                    remaining == 0
                            ? "180 days completed"
                            : remaining + " days remaining"
            );

            ageRequirementMet = accountDays >= 180;

        } else {

            accountAgeValue.setText("Date unavailable");
            daysRemaining.setText("Cannot verify account age");
            ageRequirementMet = false;
        }

        if (followerRequirementMet && ageRequirementMet) {

            eligibilityValue.setText(
                    "Requirements completed - Awaiting review"
            );

        } else {

            eligibilityValue.setText(
                    "Not yet eligible"
            );
        }
    }

    private void loadCreatorMonetization(String uid) {

        db.collection("creatorMonetization")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()) {
                        setUnavailableEarnings();
                        applicationValue.setText("Not submitted");
                        contentPolicyValue.setText("Not verified");
                        return;
                    }

                    videoEarnings.setText(
                            money(document.getDouble("videoEarnings"))
                    );

                    reelsEarnings.setText(
                            money(document.getDouble("reelsEarnings"))
                    );

                    photoEarnings.setText(
                            money(document.getDouble("photoEarnings"))
                    );

                    textEarnings.setText(
                            money(document.getDouble("textEarnings"))
                    );

                    estimatedEarnings.setText(
                            money(document.getDouble("estimatedEarnings"))
                    );

                    totalEarnings.setText(
                            money(document.getDouble("totalEarnings"))
                    );

                    viewsValue.setText(
                            number(document.getLong("totalViews"))
                    );

                    watchTimeValue.setText(
                            number(document.getLong("watchTimeMinutes"))
                            + " minutes"
                    );

                    engagementValue.setText(
                            number(document.getLong("engagement"))
                    );

                    String application =
                            document.getString("applicationStatus");

                    applicationValue.setText(
                            application == null
                                    ? "Not submitted"
                                    : application
                    );

                    String policy =
                            document.getString("contentPolicyStatus");

                    contentPolicyValue.setText(
                            policy == null
                                    ? "Not verified"
                                    : policy
                    );

                    String paymentHistory =
                            document.getString("paymentHistorySummary");

                    paymentHistoryValue.setText(
                            paymentHistory == null
                                    ? "No payment records available"
                                    : paymentHistory
                    );
                })
                .addOnFailureListener(e ->
                        showError("Could not load earnings"));
    }

    private void setUnavailableEarnings() {

        videoEarnings.setText("Not available");
        reelsEarnings.setText("Not available");
        photoEarnings.setText("Not available");
        textEarnings.setText("Not available");
        estimatedEarnings.setText("Not available");
        totalEarnings.setText("Not available");
        viewsValue.setText("Not available");
        watchTimeValue.setText("Not available");
        engagementValue.setText("Not available");
    }

    private void applyForMonetization() {

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            showError("Please login first");
            return;
        }

        if (!followerRequirementMet || !ageRequirementMet) {

            Toast.makeText(
                    this,
                    "You need 1,000 followers and 180 days",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String uid = user.getUid();

        Map<String, Object> application = new HashMap<>();

        application.put("userId", uid);
        application.put("followersAtApplication", followers);
        application.put("accountAgeDays", accountDays);
        application.put("status", "pending");
        application.put("createdAt", FieldValue.serverTimestamp());

        db.collection("monetizationApplications")
                .document(uid)
                .set(application)
                .addOnSuccessListener(unused -> {

                    applicationValue.setText("Pending Review");

                    Toast.makeText(
                            this,
                            "Application submitted",
                            Toast.LENGTH_LONG
                    ).show();
                })
                .addOnFailureListener(e ->
                        showError("Application submission failed"));
    }

    private void showPayoutDialog(String method) {

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            showError("Please login first");
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(5));

        EditText accountName = new EditText(this);
        accountName.setHint("Account holder name");
        layout.addView(accountName);

        EditText accountNumber = new EditText(this);
        accountNumber.setHint(method + " account number");
        accountNumber.setInputType(3);
        layout.addView(accountNumber);

        new AlertDialog.Builder(this)
                .setTitle(method + " Payout Account")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {

                    String name = accountName.getText()
                            .toString().trim();

                    String number = accountNumber.getText()
                            .toString().trim();

                    if (name.isEmpty() || number.isEmpty()) {
                        showError("Enter account name and number");
                        return;
                    }

                    savePayout(method, name, number);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void savePayout(
            String method,
            String name,
            String number) {

        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> payout = new HashMap<>();

        payout.put("method", method);
        payout.put("accountName", name);
        payout.put("accountNumber", number);
        payout.put("verificationStatus", "pending");
        payout.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(uid)
                .collection("private")
                .document("payout")
                .set(payout)
                .addOnSuccessListener(unused -> {

                    payoutStatusValue.setText(
                            method + " - Verification Pending"
                    );

                    Toast.makeText(
                            this,
                            "Payout account saved",
                            Toast.LENGTH_LONG
                    ).show();
                })
                .addOnFailureListener(e ->
                        showError("Could not save payout account"));
    }

    private void loadPayout(String uid) {

        db.collection("users")
                .document(uid)
                .collection("private")
                .document("payout")
                .get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()) {
                        payoutStatusValue.setText("Not configured");
                        return;
                    }

                    String method = document.getString("method");
                    String status = document.getString("verificationStatus");

                    payoutStatusValue.setText(
                            (method == null ? "" : method)
                            + " - "
                            + (status == null ? "Pending" : status)
                    );
                });
    }

    private String money(Double value) {

        if (value == null) {
            return "Not available";
        }

        return String.format(
                Locale.US,
                "৳%.2f",
                value
        );
    }

    private String number(Long value) {

        return value == null
                ? "Not available"
                : String.valueOf(value);
    }

    private void showError(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private int dp(int value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }
}
