
package com.mamun.ayna;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.video.VideoCanvas;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LiveVideoActivity extends Activity {

    // Agora project configuration
    // Production-এ Token Server ব্যবহার করতে হবে।
    private static final String AGORA_APP_ID = "YOUR_AGORA_APP_ID";

    private static final String CHANNEL_NAME = "ayna_live";

    // Testing-এর জন্যও production live চালাবে না।
    // Server-generated temporary token এখানে ব্যবহার করতে হবে।
    private static final String AGORA_TOKEN = null;

    private RtcEngine rtcEngine;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private TextView statusText;
    private Button startButton;
    private Button joinButton;
    private Button leaveButton;

    private boolean joined = false;
    private boolean isBroadcaster = false;

    private String channelName = CHANNEL_NAME;

    private final IRtcEngineEventHandler eventHandler =
            new IRtcEngineEventHandler() {

        @Override
        public void onJoinChannelSuccess(
                String channel,
                int uid,
                int elapsed) {

            runOnUiThread(() -> {
                joined = true;
                statusText.setText(
                        isBroadcaster
                        ? "আপনি Live করছেন"
                        : "আপনি Live দেখছেন"
                );
            });
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() ->
                    statusText.setText("Live Viewer যুক্ত হয়েছে"));
        }

        @Override
        public void onUserOffline(
                int uid,
                int reason) {

            runOnUiThread(() ->
                    statusText.setText("একজন Viewer চলে গেছেন"));
        }

        @Override
        public void onError(int err) {
            runOnUiThread(() ->
                    statusText.setText("Agora Error: " + err));
        }

        @Override
        public void onLeaveChannel(
                RtcStats stats) {

            runOnUiThread(() -> {
                joined = false;
                statusText.setText("Live বন্ধ হয়েছে");
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        createInterface();
        initializeAgora();
    }

    private void createInterface() {

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(25, 25, 25, 25);
        layout.setBackgroundColor(Color.rgb(20, 20, 20));

        TextView title = new TextView(this);
        title.setText("AYNA LIVE VIDEO");
        title.setTextSize(25);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);

        statusText = new TextView(this);
        statusText.setText("Live প্রস্তুত হচ্ছে...");
        statusText.setTextSize(17);
        statusText.setTextColor(Color.WHITE);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(10, 25, 10, 25);

        startButton = createButton("Start Live");
        joinButton = createButton("Join Live");
        leaveButton = createButton("Leave Live");

        layout.addView(title);
        layout.addView(statusText);
        layout.addView(startButton);
        layout.addView(joinButton);
        layout.addView(leaveButton);

        startButton.setOnClickListener(v -> startLive());

        joinButton.setOnClickListener(v -> joinLive());

        leaveButton.setOnClickListener(v -> leaveLive());

        setContentView(layout);

        leaveButton.setEnabled(false);
    }

    private Button createButton(String text) {

        Button button = new Button(this);
        button.setText(text);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2);

        params.setMargins(0, 12, 0, 12);
        button.setLayoutParams(params);

        return button;
    }

    private void initializeAgora() {

        if (AGORA_APP_ID.equals("YOUR_AGORA_APP_ID")
                || AGORA_APP_ID.trim().isEmpty()) {

            statusText.setText(
                    "Agora App ID সেট করা হয়নি।"
            );

            startButton.setEnabled(false);
            joinButton.setEnabled(false);
            return;
        }

        try {

            rtcEngine = RtcEngine.create(
                    getBaseContext(),
                    AGORA_APP_ID,
                    eventHandler
            );

            rtcEngine.enableVideo();
            rtcEngine.enableAudio();

            statusText.setText(
                    "Agora প্রস্তুত। Live শুরু করুন।"
            );

        } catch (Exception e) {

            statusText.setText(
                    "Agora চালু করা যায়নি: "
                    + e.getMessage()
            );
        }
    }

    private void startLive() {

        if (!checkLogin()) return;

        if (!checkAgora()) return;

        if (joined) return;

        isBroadcaster = true;

        channelName = "ayna_live_" +
                auth.getCurrentUser().getUid();

        startFirebaseLiveRecord();

        joinAgoraChannel(true);
    }

    private void joinLive() {

        if (!checkLogin()) return;

        if (!checkAgora()) return;

        if (joined) return;

        isBroadcaster = false;

        joinAgoraChannel(false);
    }

    private void joinAgoraChannel(boolean broadcaster) {

        if (AGORA_TOKEN == null
                || AGORA_TOKEN.trim().isEmpty()) {

            statusText.setText(
                    "নিরাপদ Agora Token Server সংযোগ করতে হবে।"
            );

            return;
        }

        ChannelMediaOptions options =
                new ChannelMediaOptions();

        options.channelProfile =
                Constants.CHANNEL_PROFILE_LIVE_BROADCASTING;

        options.clientRoleType = broadcaster
                ? Constants.CLIENT_ROLE_BROADCASTER
                : Constants.CLIENT_ROLE_AUDIENCE;

        int result = rtcEngine.joinChannel(
                AGORA_TOKEN,
                channelName,
                0,
                options
        );

        if (result != 0) {
            statusText.setText(
                    "Live সংযোগ ব্যর্থ: " + result
            );
        } else {
            statusText.setText("Live সংযোগ হচ্ছে...");
        }
    }

    private void startFirebaseLiveRecord() {

        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> liveData =
                new HashMap<>();

        liveData.put("hostId", uid);
        liveData.put("channelName", channelName);
        liveData.put("status", "STARTING");
        liveData.put("createdAt",
                com.google.firebase.firestore.FieldValue
                        .serverTimestamp());

        db.collection("liveStreams")
                .document(channelName)
                .set(liveData)
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "Live তথ্য সংরক্ষণ ব্যর্থ",
                                Toast.LENGTH_SHORT
                        ).show());
    }

    private void leaveLive() {

        if (rtcEngine != null && joined) {
            rtcEngine.leaveChannel();
        }

        if (isBroadcaster && auth.getCurrentUser() != null) {

            db.collection("liveStreams")
                    .document(channelName)
                    .update(
                            "status", "ENDED",
                            "endedAt",
                            com.google.firebase.firestore.FieldValue
                                    .serverTimestamp()
                    );
        }

        joined = false;

        startButton.setEnabled(true);
        joinButton.setEnabled(true);
        leaveButton.setEnabled(false);

        statusText.setText("Live বন্ধ হয়েছে");
    }

    private boolean checkLogin() {

        if (auth.getCurrentUser() == null) {
            statusText.setText("প্রথমে Login করুন");
            return false;
        }

        return true;
    }

    private boolean checkAgora() {

        if (rtcEngine == null) {
            statusText.setText(
                    "Agora SDK প্রস্তুত নয়"
            );
            return false;
        }

        return true;
    }

    @Override
    protected void onDestroy() {

        if (joined) {
            leaveLive();
        }

        if (rtcEngine != null) {
            RtcEngine.destroy();
            rtcEngine = null;
        }

        super.onDestroy();
    }
}
