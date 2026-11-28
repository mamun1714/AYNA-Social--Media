
package com.mamun.ayna;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

public class PayoutSetupActivity extends Activity {

    private EditText countryInput;
    private EditText currencyInput;
    private EditText bankNameInput;
    private EditText accountNameInput;
    private EditText accountNumberInput;
    private EditText swiftInput;
    private EditText routingInput;
    private EditText branchInput;

    private Button saveButton;
    private TextView statusText;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private final int BLUE = Color.rgb(23, 105, 170);
    private final int DARK = Color.rgb(35, 45, 60);
    private final int GRAY = Color.rgb(105, 115, 130);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this,
                    "Please login first",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        createScreen();
        loadExistingPayout();
    }

    private void createScreen() {

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(245, 247, 251));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));

        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("AYNA Creator Payout");
        title.setTextSize(25);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(BLUE);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(
                "Add your own country's bank account to receive " +
                "eligible creator earnings when international payouts " +
                "are supported."
        );
        subtitle.setTextSize(14);
        subtitle.setTextColor(GRAY);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle);

        addLabel(root, "Country");
        countryInput = addInput(root,
                "Example: Bangladesh, India, United States");

        addLabel(root, "Currency");
        currencyInput = addInput(root,
                "Example: BDT, INR, USD, GBP");

        addLabel(root, "Bank Name");
        bankNameInput = addInput(root,
                "Enter your bank name");

        addLabel(root, "Account Holder Name");
        accountNameInput = addInput(root,
                "Name as shown on bank account");

        addLabel(root, "Account Number / IBAN");
        accountNumberInput = addInput(root,
                "Enter account number or IBAN");

        accountNumberInput.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
        );

        addLabel(root, "SWIFT / BIC Code");
        swiftInput = addInput(root,
                "If required by your bank");

        addLabel(root, "Routing Number / IFSC Code");
        routingInput = addInput(root,
                "If applicable in your country");

        addLabel(root, "Branch Name");
        branchInput = addInput(root,
                "Optional");

        TextView notice = new TextView(this);
        notice.setText(
                "Important: Enter accurate bank information. " +
                "Your payout details must be verified. " +
                "Adding an account does not guarantee payment. " +
                "Actual payouts depend on AYNA's approved payment " +
                "partners and country availability."
        );
        notice.setTextSize(13);
        notice.setTextColor(DARK);
        notice.setPadding(dp(12), dp(12), dp(12), dp(12));
        notice.setBackgroundColor(Color.rgb(232, 240, 250));

        LinearLayout.LayoutParams noticeParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        noticeParams.setMargins(0, dp(18), 0, dp(15));
        root.addView(notice, noticeParams);

        statusText = new TextView(this);
        statusText.setText("Verification Status: Not submitted");
        statusText.setTextSize(14);
        statusText.setTextColor(GRAY);
        statusText.setPadding(0, dp(8), 0, dp(12));
        root.addView(statusText);

        saveButton = new Button(this);
        saveButton.setText("Save Payout Account");
        saveButton.setTextColor(Color.WHITE);
        saveButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(BLUE)
        );

        root.addView(saveButton);

        saveButton.setOnClickListener(v -> savePayoutAccount());

        setContentView(scrollView);
    }

    private void addLabel(LinearLayout root, String text) {

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(DARK);

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
            String hint
    ) {

        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                );

        root.addView(input, params);

        return input;
    }

    private void savePayoutAccount() {

        if (auth.getCurrentUser() == null) {
            showMessage("Please login first");
            return;
        }

        String country = getValue(countryInput);
        String currency = getValue(currencyInput);
        String bankName = getValue(bankNameInput);
        String accountName = getValue(accountNameInput);
        String accountNumber = getValue(accountNumberInput);
        String swiftCode = getValue(swiftInput);
        String routingCode = getValue(routingInput);
        String branchName = getValue(branchInput);

        if (country.isEmpty()) {
            countryInput.setError("Enter your country");
            return;
        }

        if (currency.isEmpty()) {
            currencyInput.setError("Enter your currency");
            return;
        }

        if (bankName.isEmpty()) {
            bankNameInput.setError("Enter bank name");
            return;
        }

        if (accountName.isEmpty()) {
            accountNameInput.setError("Enter account holder name");
            return;
        }

        if (accountNumber.isEmpty()) {
            accountNumberInput.setError(
                    "Enter account number or IBAN"
            );
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        saveButton.setEnabled(false);
        saveButton.setText("Saving...");

        Map<String, Object> payout = new HashMap<>();

        payout.put("method", "BANK_TRANSFER");
        payout.put("country", country);
        payout.put("currency", currency.toUpperCase());
        payout.put("bankName", bankName);
        payout.put("accountName", accountName);
        payout.put("accountNumber", accountNumber);
        payout.put("swiftCode", swiftCode);
        payout.put("routingCode", routingCode);
        payout.put("branchName", branchName);

        payout.put("verificationStatus", "PENDING");
        payout.put("payoutStatus", "NOT_ACTIVE");
        payout.put("updatedAt", FieldValue.serverTimestamp());

        DocumentReference payoutRef = db
                .collection("users")
                .document(uid)
                .collection("private")
                .document("payout");

        payoutRef.set(payout, SetOptions.merge())
                .addOnSuccessListener(unused -> {

                    saveButton.setEnabled(true);
                    saveButton.setText("Save Payout Account");

                    statusText.setText(
                            "Verification Status: PENDING"
                    );

                    showMessage(
                            "Payout details saved. Verification is pending."
                    );
                })
                .addOnFailureListener(error -> {

                    saveButton.setEnabled(true);
                    saveButton.setText("Save Payout Account");

                    showMessage(
                            "Could not save payout details: " +
                            error.getMessage()
                    );
                });
    }

    private void loadExistingPayout() {

        if (auth.getCurrentUser() == null) {
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("private")
                .document("payout")
                .get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()) {
                        return;
                    }

                    countryInput.setText(
                            document.getString("country")
                    );

                    currencyInput.setText(
                            document.getString("currency")
                    );

                    bankNameInput.setText(
                            document.getString("bankName")
                    );

                    accountNameInput.setText(
                            document.getString("accountName")
                    );

                    accountNumberInput.setText(
                            document.getString("accountNumber")
                    );

                    swiftInput.setText(
                            document.getString("swiftCode")
                    );

                    routingInput.setText(
                            document.getString("routingCode")
                    );

                    branchInput.setText(
                            document.getString("branchName")
                    );

                    String verification =
                            document.getString("verificationStatus");

                    if (verification != null) {
                        statusText.setText(
                                "Verification Status: " + verification
                        );
                    }
                })
                .addOnFailureListener(error ->
                        showMessage("Could not load payout details")
                );
    }

    private String getValue(EditText input) {
        return input.getText().toString().trim();
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
