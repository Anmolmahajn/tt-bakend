package com.tt.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.tt.model.Match;
import com.tt.model.Player;
import com.tt.model.Tournament;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.logging.Logger;

@Service
public class PushNotificationService {

    private static final Logger logger = Logger.getLogger(PushNotificationService.class.getName());

    @Value("${firebase.config.path:serviceAccountKey.json}")
    private String firebaseConfigPath;

    private boolean firebaseEnabled = false;

    @PostConstruct
    public void init() {
        try {
            InputStream sa = getClass().getClassLoader().getResourceAsStream(firebaseConfigPath);
            if (sa == null) {
                logger.warning("Firebase config not found. Push notifications disabled.");
                return;
            }
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions opts = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(sa)).build();
                FirebaseApp.initializeApp(opts);
            }
            firebaseEnabled = true;
        } catch (Exception e) {
            logger.warning("Firebase init failed: " + e.getMessage());
        }
    }

    @Async
    public void sendToPlayer(Player player, String title, String body, String type) {
        if (!firebaseEnabled || player.getFcmToken() == null || player.getFcmToken().isBlank()) return;
        try {
            Message msg = Message.builder()
                .setToken(player.getFcmToken())
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putData("type", type)
                .build();
            FirebaseMessaging.getInstance().send(msg);
        } catch (Exception e) {
            logger.severe("Push failed for " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    public void notifyTournamentMembers(Tournament tournament, String title, String body, String type) {
        tournament.getMembers().stream()
            .filter(m -> !m.isGuest() && m.getPlayer() != null)
            .forEach(m -> sendToPlayer(m.getPlayer(), title, body, type));
    }

    public void notifyMatchStarted(Match match) {
        String body = String.format("Match #%d: %s vs %s is starting!",
            match.getMatchNumber(),
            match.getMember1().getDisplayName(),
            match.getMember2().getDisplayName());
        if (!match.getMember1().isGuest() && match.getMember1().getPlayer() != null)
            sendToPlayer(match.getMember1().getPlayer(), "🏓 Your match is starting!", body, "MATCH_STARTED");
        if (!match.getMember2().isGuest() && match.getMember2().getPlayer() != null)
            sendToPlayer(match.getMember2().getPlayer(), "🏓 Your match is starting!", body, "MATCH_STARTED");
    }

    public void notifyDayStarted(Tournament tournament, int dayNumber) {
        notifyTournamentMembers(tournament,
            "🏓 Day " + dayNumber + " Started!",
            tournament.getName() + " - Matches are beginning now!",
            "DAY_STARTED");
    }

    public void notifyDayEnded(Tournament tournament, int dayNumber) {
        notifyTournamentMembers(tournament,
            "📊 Day " + dayNumber + " Rankings Ready!",
            "Check your updated rankings for " + tournament.getName(),
            "DAY_ENDED");
    }
}
