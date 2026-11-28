
package com.mamun.ayna;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

public class StarSenderActivity extends Activity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private EditText creatorUidInput;
    private EditText starAmountInput;

    private TextView balanceText;
    private TextView infoText;

    private Button sendButton;
    private Button refreshButton;

    private final int BLUE = Color.rgb(23, 105, 170);
    private final int DARK = Color.rgb(35, 45, 60);
    private final int GRAY = Color.rgb(105, 115, 130);

    private static final int MAX_STARS_PER_SEND = 100000;

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
        loadStarBalance();
    }

    private void createScreen() {

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(245, 247, 251));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(25));

        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("AYNA Star Sender");
        title.setTextSize(25);
        title.setTextColor(BLUE);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(
                "Send Stars to your favorite AYNA Creator."
        );
        subtitle.setTextSize(14);
        subtitle.setTextColor(GRAY);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle);

        balanceText = new TextView(this);
        balanceText.setText("Your Star Balance: Loading...");
        balanceText.setTextSize(19);
        balanceText.setTextColor(DARK);
        balanceText.setTypeface(null, Typeface.BOLD);
        balanceText.setPadding(0, dp(12), 0, dp(18));
        root.addView(balanceText);

        addLabel(root, "Creator User ID");
        creatorUidInput = addInput(
                root,
                "Enter Creator UID",
                InputType.TYPE_CLASS_TEXT
        );

        addLabel(root, "Number of Stars");
        starAmountInput = addInput(
                root,
                "Enter Star amount",
                InputType.TYPE_CLASS_NUMBER
        );

        infoText = new TextView(this);
        infoText.setText(
                "Star Revenue Distribution:\n" +
                "AYNA Platform: 30%\n" +
                "Creator: 70%"
        );
        infoText.setTextSize(15);
        infoText.setTextColor(DARK);
        infoText.setPadding(dp(12), dp(14), dp(12), dp(14));
        infoText.setBackgroundColor(Color.rgb(232, 240, 250));

        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        infoParams.setMargins(0, dp(18), 0, dp(15));
        root.addView(infoText, infoParams);

        sendButton = new Button(this);
        sendButton.setText("Send Stars");
        sendButton.setTextColor(Color.WHITE);
        sendButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(BLUE)
        );
        root.addView(sendButton);

        refreshButton = new Button(this);
        refreshButton.setText("Refresh Star Balance");
        root.addView(refreshButton);

        sendButton.setOnClickListener(v -> sendStars());

        refreshButton.setOnClickListener(v -> loadStarBalance());

        setContentView(scroll);
    }

    private void addLabel(LinearLayout root, String text) {

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14);
        label.setTextColor(DARK);
        label.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(0, dp(10), 0, dp(5));
        root.addView(label, params);
    }

    private EditText addInput(
            LinearLayout root,
            String hint,
            int inputType
    ) {

        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackgroundColor(Color.WHITE);

        root.addView(
                input,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                )
        );

        return input;
    }

    private DocumentReference getWalletReference(String uid) {

        return db.collection("users")
                .document(uid)
                .collection("private")
                .document("starWallet");
    }

    private DocumentReference getPlatformWalletReference() {

        return db.collection("platformWallets")
                .document("ayna");
    }

    private void loadStarBalance() {

        if (auth.getCurrentUser() == null) {
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        refreshButton.setEnabled(false);

        getWalletReference(uid).get()
                .addOnSuccessListener(document -> {

                    refreshButton.setEnabled(true);

                    long balance = getBalance(document);

                    balanceText.setText(
                            "Your Star Balance: " + balance + " Stars"
                    );
                })
                .addOnFailureListener(error -> {

                    refreshButton.setEnabled(true);

                    balanceText.setText(
                            "Could not load Star Balance"
                    );

                    showMessage("Balance loading failed.");
                });
    }

    private void sendStars() {

        if (auth.getCurrentUser() == null) {
            showMessage("Please login first");
            return;
        }

        String senderUid = auth.getCurrentUser().getUid();

        String creatorUid =
                creatorUidInput.getText().toString().trim();

        String amountText =
                starAmountInput.getText().toString().trim();

        if (creatorUid.isEmpty()) {
            creatorUidInput.setError("Enter Creator UID");
            return;
        }

        if (creatorUid.equals(senderUid)) {
            creatorUidInput.setError(
                    "You cannot send Stars to yourself"
            );
            return;
        }

        if (amountText.isEmpty()) {
            starAmountInput.setError("Enter Star amount");
            return;
        }

        final long stars;

        try {
            stars = Long.parseLong(amountText);
        } catch (NumberFormatException error) {
            starAmountInput.setError("Invalid Star amount");
            return;
        }

        if (stars <= 0 || stars > MAX_STARS_PER_SEND) {
            starAmountInput.setError(
                    "Enter between 1 and 100000 Stars"
            );
            return;
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("Confirm Star Sending")
                .setMessage(
                        "You are sending " + stars +
                        " Stars to Creator.\n\n" +
                        "AYNA share: " + calculatePlatformShare(stars) +
                        " Stars\n" +
                        "Creator share: " +
                        calculateCreatorShare(stars) + " Stars\n\n" +
                        "Continue?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (dialog, which) ->
                        performStarTransaction(senderUid, creatorUid, stars)
                )
                .show();
    }

    private void performStarTransaction(
            String senderUid,
            String creatorUid,
            long stars
    ) {

        sendButton.setEnabled(false);
        sendButton.setText("Sending...");

        DocumentReference senderWallet =
                getWalletReference(senderUid);

        DocumentReference creatorWallet =
                getWalletReference(creatorUid);

        DocumentReference platformWallet =
                getPlatformWalletReference();

        DocumentReference creatorUser =
                db.collection("users").document(creatorUid);

        DocumentReference transactionRecord =
                db.collection("starTransactions").document();

        long platformShare = calculatePlatformShare(stars);
        long creatorShare = calculateCreatorShare(stars);

        db.runTransaction((Transaction.Function<Void>) transaction -> {

            DocumentSnapshot senderDoc =
                    transaction.get(senderWallet);

            DocumentSnapshot creatorDoc =
                    transaction.get(creatorWallet);

            DocumentSnapshot platformDoc =
                    transaction.get(platformWallet);

            DocumentSnapshot creatorUserDoc =
                    transaction.get(creatorUser);

            if (!creatorUserDoc.exists()) {
                throw new IllegalStateException(
                        "Creator account not found."
                );
            }

            long senderBalance = getBalance(senderDoc);
            long creatorBalance = getBalance(creatorDoc);
            long platformBalance = getBalance(platformDoc);

            if (senderBalance < stars) {
                throw new IllegalStateException(
                        "Insufficient Star Balance."
                );
            }

            if (senderBalance < 0 ||
                    creatorBalance < 0 ||
                    platformBalance < 0) {
                throw new IllegalStateException(
                        "Invalid wallet balance."
                );
            }

            if (creatorBalance > Long.MAX_VALUE - creatorShare ||
                    platformBalance > Long.MAX_VALUE - platformShare) {
                throw new IllegalStateException(
                        "Wallet balance limit exceeded."
                );
            }

            Map<String, Object> senderUpdates =
                    new HashMap<>();

            senderUpdates.put(
                    "balanceStars",
                    senderBalance - stars
            );

            senderUpdates.put(
                    "updatedAt",
                    FieldValue.serverTimestamp()
            );

            Map<String, Object> creatorUpdates =
                    new HashMap<>();

            creatorUpdates.put(
                    "balanceStars",
                    creatorBalance + creatorShare
            );

            creatorUpdates.put(
                    "totalStarsReceived",
                    FieldValue.increment(creatorShare)
            );

            creatorUpdates.put(
                    "updatedAt",
                    FieldValue.serverTimestamp()
            );

            Map<String, Object> platformUpdates =
                    new HashMap<>();

            platformUpdates.put(
                    "balanceStars",
                    platformBalance + platformShare
            );

            platformUpdates.put(
                    "totalStarsReceived",
                    FieldValue.increment(platformShare)
            );

            platformUpdates.put(
                    "updatedAt",
                    FieldValue.serverTimestamp()
            );

            Map<String, Object> record =
                    new HashMap<>();

            record.put("senderUid", senderUid);
            record.put("creatorUid", creatorUid);
            record.put("sentStars", stars);
            record.put("platformShareStars", platformShare);
            record.put("creatorShareStars", creatorShare);
            record.put("status", "RECORDED");
            record.put("createdAt", FieldValue.serverTimestamp());

            transaction.set(senderWallet, senderUpdates);
            transaction.set(
                    creatorWallet,
                    creatorUpdates,
                    com.google.firebase.firestore.SetOptions.merge()
            );

            transaction.set(
                    platformWallet,
                    platformUpdates,
                    com.google.firebase.firestore.SetOptions.merge()
            );

            transaction.set(transactionRecord, record);

            return null;

        }).addOnSuccessListener(unused -> {

            sendButton.setEnabled(true);
            sendButton.setText("Send Stars");

            creatorUidInput.setText("");
            starAmountInput.setText("");

            showMessage("Stars sent successfully!");

            loadStarBalance();

        }).addOnFailureListener(error -> {

            sendButton.setEnabled(true);
            sendButton.setText("Send Stars");

            showMessage(
                    "Star sending failed: " +
                    error.getMessage()
            );
        });
    }

    private long calculatePlatformShare(long stars) {
        return (stars * 30L) / 100L;
    }

    private long calculateCreatorShare(long stars) {
        return stars - calculatePlatformShare(stars);
    }

    private long getBalance(DocumentSnapshot document) {

        if (document == null || !document.exists()) {
            return 0L;
        }

        Object value = document.get("balanceStars");

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return 0L;
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
