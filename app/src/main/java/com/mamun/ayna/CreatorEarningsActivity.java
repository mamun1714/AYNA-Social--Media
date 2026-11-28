
package com.mamun.ayna;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ProgressBar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class CreatorEarningsActivity extends Activity {

    private LinearLayout mainLayout;
    private ProgressBar progressBar;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private final int BLUE = Color.rgb(23, 105, 170);
    private final int DARK = Color.rgb(35, 45, 55);
    private final int GREEN = Color.rgb(30, 140, 80);
    private final int GRAY = Color.rgb(110, 110, 110);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        createDashboard();
        loadEarnings();
    }

    private void createDashboard() {

        ScrollView scrollView = new ScrollView(this);

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 24, 20, 24);
        mainLayout.setBackgroundColor(Color.rgb(245, 247, 250));

        TextView title = new TextView(this);
        title.setText("AYNA Creator Earnings");
        title.setTextSize(24);
        title.setTextColor(BLUE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 10, 0, 20);
        title.setTypeface(null, 1);

        mainLayout.addView(title);

        progressBar = new ProgressBar(this);
        mainLayout.addView(progressBar);

        scrollView.addView(mainLayout);
        setContentView(scrollView);
    }

    private void loadEarnings() {

        if (auth.getCurrentUser() == null) {
            showMessage("প্রথমে লগইন করতে হবে।");
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        db.collection("creatorMonetization")
                .document(uid)
                .get()
                .addOnSuccessListener(this::showEarnings)
                .addOnFailureListener(e ->
                        showMessage("আয়ের তথ্য লোড করা যায়নি: "
                                + e.getMessage())
                );
    }

    private void showEarnings(DocumentSnapshot doc) {

        progressBar.setVisibility(View.GONE);

        if (!doc.exists()) {
            showMessage("এখনো Creator Earnings-এর কোনো তথ্য পাওয়া যায়নি।");
            return;
        }

        addSection("আয়ের সারসংক্ষেপ");

        addCard("মোট আয়",
                formatMoney(doc.get("totalEarnings")),
                GREEN);

        addCard("আনুমানিক আয়",
                formatMoney(doc.get("estimatedEarnings")),
                BLUE);

        addSection("কনটেন্ট অনুযায়ী আয়");

        addCard("ভিডিও আয়",
                formatMoney(doc.get("videoEarnings")),
                DARK);

        addCard("রিলস আয়",
                formatMoney(doc.get("reelsEarnings")),
                DARK);

        addCard("ছবি থেকে আয়",
                formatMoney(doc.get("photoEarnings")),
                DARK);

        addCard("টেক্সট পোস্ট থেকে আয়",
                formatMoney(doc.get("textEarnings")),
                DARK);

        addSection("পারফরম্যান্স");

        addCard("মোট Views",
                formatNumber(doc.get("totalViews")),
                BLUE);

        addCard("Watch Time",
                formatNumber(doc.get("watchTimeMinutes"))
                        + " মিনিট",
                BLUE);

        addCard("Engagement",
                formatNumber(doc.get("engagement")),
                BLUE);

        addSection("Monetization Status");

        addCard("আবেদনের অবস্থা",
                getText(doc, "applicationStatus"),
                DARK);

        addCard("Content Policy Status",
                getText(doc, "contentPolicyStatus"),
                DARK);

        addSection("Payment Information");

        addCard("Payment History",
                getText(doc, "paymentHistorySummary"),
                DARK);

        addNote(
                "AYNA Commission: 30%\n"
                + "Creator Share: 70%\n\n"
                + "এই Dashboard-এ Firebase থেকে পাওয়া তথ্য "
                + "দেখানো হচ্ছে। প্রকৃত পাওনা, কমিশন ও "
                + "উত্তোলনযোগ্য অর্থ নির্ধারণের জন্য "
                + "নিরাপদ Backend-এর হিসাব প্রয়োজন।"
        );
    }

    private void addSection(String heading) {

        TextView text = new TextView(this);
        text.setText(heading);
        text.setTextSize(19);
        text.setTextColor(BLUE);
        text.setTypeface(null, 1);
        text.setPadding(0, 22, 0, 10);

        mainLayout.addView(text);
    }

    private void addCard(String heading,
                         String value,
                         int valueColor) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(18, 15, 18, 15);
        card.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(0, 5, 0, 5);
        card.setLayoutParams(params);

        TextView label = new TextView(this);
        label.setText(heading);
        label.setTextSize(14);
        label.setTextColor(GRAY);

        TextView amount = new TextView(this);
        amount.setText(value);
        amount.setTextSize(21);
        amount.setTextColor(valueColor);
        amount.setTypeface(null, 1);
        amount.setPadding(0, 7, 0, 0);

        card.addView(label);
        card.addView(amount);

        mainLayout.addView(card);
    }

    private void addNote(String message) {

        TextView note = new TextView(this);
        note.setText(message);
        note.setTextSize(14);
        note.setTextColor(DARK);
        note.setPadding(15, 18, 15, 18);
        note.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(0, 20, 0, 10);
        note.setLayoutParams(params);

        mainLayout.addView(note);
    }

    private void showMessage(String message) {

        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }

        TextView text = new TextView(this);
        text.setText(message);
        text.setTextSize(16);
        text.setTextColor(DARK);
        text.setPadding(15, 20, 15, 20);

        mainLayout.addView(text);
    }

    private String formatMoney(Object value) {

        if (!(value instanceof Number)) {
            return "৳ 0.00";
        }

        double amount = ((Number) value).doubleValue();

        return String.format(
                Locale.US,
                "৳ %.2f",
                amount
        );
    }

    private String formatNumber(Object value) {

        if (!(value instanceof Number)) {
            return "0";
        }

        return String.valueOf(((Number) value).longValue());
    }

    private String getText(DocumentSnapshot doc,
                           String field) {

        Object value = doc.get(field);

        if (value == null) {
            return "তথ্য পাওয়া যায়নি";
        }

        return String.valueOf(value);
    }
}
