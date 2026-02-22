package com.tt.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.tt.model.*;
import com.tt.repository.PlayerRepository;
import com.tt.repository.TournamentMemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class PushNotificationService {

    private static final Logger log = Logger.getLogger(PushNotificationService.class.getName());

    private final TournamentMemberRepository memberRepo;
    private final PlayerRepository playerRepo;
    private boolean fcmReady = false;

    // Set FIREBASE_SERVICE_ACCOUNT env var to your Firebase service account JSON (minified)
    @Value("${FIREBASE_SERVICE_ACCOUNT:}")
    private String serviceAccountJson;

    public PushNotificationService(TournamentMemberRepository memberRepo, PlayerRepository playerRepo) {
        this.memberRepo = memberRepo;
        this.playerRepo = playerRepo;
    }

    @PostConstruct
    public void init() {
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            log.warning("FCM: FIREBASE_SERVICE_ACCOUNT not set — push notifications disabled");
            return;
        }
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream is = new ByteArrayInputStream(serviceAccountJson.getBytes());
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(is))
                        .build();
                FirebaseApp.initializeApp(options);
            }
            fcmReady = true;
            log.info("FCM: Firebase initialized successfully ✓");
        } catch (Exception e) {
            log.severe("FCM: Failed to initialize Firebase — " + e.getMessage());
        }
    }

    // ── NOTIFY DAY STARTED ────────────────────────────────────────────────────

    public void notifyDayStarted(Tournament t, int dayNumber) {
        String title = "🏓 Day " + dayNumber + " Started!";
        String body = t.getName() + " — Get ready to play!";
        sendToTournament(t, title, body, "DAY_STARTED");
    }

    // ── NOTIFY DAY ENDED ──────────────────────────────────────────────────────

    public void notifyDayEnded(Tournament t, int dayNumber) {
        String title = "🏁 Day " + dayNumber + " Ended!";
        String body = t.getName() + " — Check the final rankings!";
        sendToTournament(t, title, body, "DAY_ENDED");
    }

    // ── NOTIFY MVP ────────────────────────────────────────────────────────────

    public void notifyMvp(Tournament t, String mvpName, int dayNumber) {
        String title = "🏆 MVP: " + mvpName + "!";
        String body = "Best player of Day " + dayNumber + " in " + t.getName();
        sendToTournament(t, title, body, "MVP");
    }



    // ── NOTIFY CHAT MESSAGE ───────────────────────────────────────────────────

    public void notifyChat(Tournament t, String senderName, String message) {
        // Don't notify for system messages
        String title = "💬 " + senderName + " in " + t.getName();
        String body = message.length() > 80 ? message.substring(0, 77) + "..." : message;
        sendToTournament(t, title, body, "CHAT");
    }

    // ── NOTIFY MATCH RESULT ───────────────────────────────────────────────────

    public void notifyMatchResult(Tournament t, String result) {
        sendToTournament(t, "✅ Match Result", result, "MATCH_RESULT");
    }

    // ── SEND TO ALL TOURNAMENT MEMBERS ───────────────────────────────────────

    private void sendToTournament(Tournament t, String title, String body, String type) {
        if (!fcmReady) return;
        // Get all FCM tokens for non-guest members in this tournament
        List<String> tokens = memberRepo.findByTournamentOrderByCurrentRankAsc(t).stream()
                .filter(m -> !m.isGuest() && m.getPlayer() != null)
                .map(m -> m.getPlayer().getFcmToken())
                .filter(token -> token != null && !token.isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (tokens.isEmpty()) return;

        // Send in batches of 500 (FCM limit)
        for (int i = 0; i < tokens.size(); i += 500) {
            List<String> batch = tokens.subList(i, Math.min(i + 500, tokens.size()));
            try {
                MulticastMessage message = MulticastMessage.builder()
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putData("type", type)
                        .putData("tournamentId", String.valueOf(t.getId()))
                        .addAllTokens(batch)
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setSound("default")
                                        .build())
                                .build())
                        .build();
                BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
                log.info("FCM: Sent '" + title + "' to " + response.getSuccessCount() + "/" + batch.size() + " devices");
            } catch (Exception e) {
                log.severe("FCM: Failed to send notification — " + e.getMessage());
            }
        }
    }
}
