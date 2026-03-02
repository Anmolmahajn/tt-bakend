package com.tt.controller;

import com.tt.dto.DTOs;
import com.tt.model.*;
import com.tt.repository.PlayerRepository;
import com.tt.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MainController {

    private final AuthService authService;
    private final TournamentService tournamentService;
    private final PlayerService playerService;
    private final PlayerRepository playerRepo;
    private final EmailService emailService;

    public MainController(AuthService authService, TournamentService tournamentService,
                          PlayerService playerService, PlayerRepository playerRepo,
                          EmailService emailService) {
        this.authService = authService; this.tournamentService = tournamentService;
        this.playerService = playerService; this.playerRepo = playerRepo;
        this.emailService = emailService;
    }

    private Player me(UserDetails ud) { return authService.getPlayer(ud.getUsername()); }

    // ── HEALTH ────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String,String>> health() {
        return ResponseEntity.ok(Map.of("status","ok","time",String.valueOf(System.currentTimeMillis())));
    }

    // ── AUTH ──────────────────────────────────────────────────────────────────

    @PostMapping("/auth/register")
    public ResponseEntity<DTOs.AuthResponse> register(@RequestBody DTOs.RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<DTOs.AuthResponse> login(@RequestBody DTOs.LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    // ── FORGOT PASSWORD ───────────────────────────────────────────────────────
    // Step 1: send OTP to email
    @PostMapping("/auth/forgot-password")
    public ResponseEntity<Map<String,String>> forgotPassword(@RequestBody Map<String,String> body) {
        authService.sendForgotPasswordOtp(body.getOrDefault("email",""));
        // Always return success to prevent email enumeration
        return ResponseEntity.ok(Map.of("message","If that email exists, an OTP has been sent."));
    }

    // Step 2: verify OTP
    @PostMapping("/auth/verify-otp")
    public ResponseEntity<Map<String,String>> verifyOtp(@RequestBody Map<String,String> body) {
        boolean ok = authService.verifyForgotPasswordOtp(
                body.getOrDefault("email",""), body.getOrDefault("otp",""));
        if (!ok) throw new RuntimeException("Invalid or expired OTP. Please try again.");
        return ResponseEntity.ok(Map.of("verified","true"));
    }

    // Step 3: set new password
    @PostMapping("/auth/reset-password")
    public ResponseEntity<DTOs.AuthResponse> resetPassword(@RequestBody Map<String,String> body) {
        return ResponseEntity.ok(authService.resetPassword(
                body.getOrDefault("email",""),
                body.getOrDefault("otp",""),
                body.getOrDefault("newPassword","")));
    }

    // OAuth2 error fallback (redirected here on social login failure)
    @GetMapping("/auth/oauth2-error")
    public ResponseEntity<Map<String,String>> oauth2Error() {
        return ResponseEntity.badRequest().body(Map.of("message","Social login failed. Please try again or use email login."));
    }

    // ── TEST EMAIL (remove after confirming email works) ──────────────────────
    // Visit: https://tt-bakend.onrender.com/api/test-email?to=youremail@gmail.com
    @GetMapping("/test-email")
    public ResponseEntity<Map<String,Object>> testEmail(@RequestParam String to) {
        Map<String,Object> result = new java.util.LinkedHashMap<>();
        result.put("to", to);
        result.put("MAIL_USERNAME", System.getenv("MAIL_USERNAME"));
        result.put("MAIL_PASSWORD_SET", System.getenv("MAIL_PASSWORD") != null ? "YES length=" + System.getenv("MAIL_PASSWORD").length() : "NO");
        result.put("MAIL_HOST", System.getenv("SPRING_MAIL_HOST") != null ? System.getenv("SPRING_MAIL_HOST") : "smtp.gmail.com(default)");
        try {
            emailService.generateAndSendOtp(to, "PASSWORD_RESET");
            result.put("status", "SUCCESS - email sent");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
            result.put("cause", e.getCause() != null ? e.getCause().getMessage() : "none");
            result.put("rootCause", getRootCause(e));
            return ResponseEntity.status(500).body(result);
        }
    }

    private String getRootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    // ── TOURNAMENTS ───────────────────────────────────────────────────────────

    @GetMapping("/tournaments")
    public ResponseEntity<List<DTOs.TournamentSummaryResponse>> myTournaments(
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.getMyTournaments(me(ud)));
    }

    @PostMapping("/tournaments")
    public ResponseEntity<DTOs.TournamentDetailResponse> create(
            @RequestBody DTOs.CreateTournamentRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        Player p = me(ud);
        tournamentService.createTournament(p, req.getName(), req.getPassword());
        Tournament t = tournamentService.getTournament(
                tournamentService.getMyTournaments(p).stream()
                        .filter(s -> s.name.equals(req.getName())).findFirst().get().id);
        return ResponseEntity.ok(tournamentService.getTournamentDetail(t.getId(), p));
    }

    @PostMapping("/tournaments/join")
    public ResponseEntity<DTOs.TournamentDetailResponse> join(
            @RequestBody DTOs.JoinTournamentRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        Player p = me(ud);
        Long tournamentId = tournamentService.joinTournamentGetId(p, req.getTournamentName(), req.getPassword());
        return ResponseEntity.ok(tournamentService.getTournamentDetail(tournamentId, p));
    }

    @GetMapping("/tournaments/{id}")
    public ResponseEntity<DTOs.TournamentDetailResponse> getTournament(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.getTournamentDetail(id, me(ud)));
    }

    @DeleteMapping("/tournaments/{id}")
    public ResponseEntity<Void> deleteTournament(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        tournamentService.deleteTournament(id, me(ud));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/tournaments/{id}/name")
    public ResponseEntity<Void> renameTournament(
            @PathVariable Long id, @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails ud) {
        tournamentService.renameTournament(id, me(ud), body.get("name"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/tournaments/{id}/password")
    public ResponseEntity<Void> changeTournamentPassword(
            @PathVariable Long id, @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails ud) {
        tournamentService.changeTournamentPassword(id, me(ud), body.get("password"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tournaments/{id}/guests")
    public ResponseEntity<DTOs.MemberResponse> addGuest(
            @PathVariable Long id, @RequestBody DTOs.AddGuestRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        TournamentMember guest = tournamentService.addGuest(id, me(ud), req.getGuestName(), req.getProficiency());
        DTOs.MemberResponse r = new DTOs.MemberResponse();
        r.id = guest.getId(); r.displayName = guest.getGuestName(); r.isGuest = true;
        r.proficiency = guest.getGuestProficiency();
        return ResponseEntity.ok(r);
    }

    @DeleteMapping("/tournaments/{id}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long id, @PathVariable Long memberId,
            @AuthenticationPrincipal UserDetails ud) {
        tournamentService.removeMember(id, me(ud), memberId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/tournaments/{id}/members/{memberId}/proficiency")
    public ResponseEntity<Void> updateMemberProficiency(
            @PathVariable Long id, @PathVariable Long memberId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails ud) {
        tournamentService.updateMemberProficiency(id, me(ud), memberId, body.get("proficiency"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tournaments/{id}/admins")
    public ResponseEntity<Void> promoteAdmin(
            @PathVariable Long id, @RequestBody DTOs.PromoteAdminRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        tournamentService.promoteAdmin(id, me(ud), req.getPlayerId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/tournaments/{id}/admins/{playerId}")
    public ResponseEntity<Void> removeAdmin(
            @PathVariable Long id, @PathVariable Long playerId,
            @AuthenticationPrincipal UserDetails ud) {
        tournamentService.removeAdmin(id, me(ud), playerId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tournaments/{id}/rankings/custom")
    public ResponseEntity<Void> setCustomRankings(
            @PathVariable Long id, @RequestBody DTOs.CustomRankingsRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        tournamentService.setCustomRankings(id, me(ud), req.getUpdates());
        return ResponseEntity.ok().build();
    }

    // ── DAYS ──────────────────────────────────────────────────────────────────

    @PostMapping("/tournaments/{id}/days")
    public ResponseEntity<DTOs.TournamentDetailResponse> startDay(
            @PathVariable Long id, @RequestBody DTOs.StartDayRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        Player p = me(ud);
        tournamentService.startDay(id, p, req);
        return ResponseEntity.ok(tournamentService.getTournamentDetail(id, p));
    }

    @PostMapping("/tournaments/{id}/days/end")
    public ResponseEntity<List<DTOs.DayRankEntry>> endDay(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.endDay(id, me(ud)));
    }

    @PostMapping("/tournaments/{id}/days/restart-matchmaking")
    public ResponseEntity<DTOs.TournamentDetailResponse> restartMatchmaking(
            @PathVariable Long id, @RequestBody DTOs.StartDayRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        Player p = me(ud);
        tournamentService.restartMatchmaking(id, p, req);
        return ResponseEntity.ok(tournamentService.getTournamentDetail(id, p));
    }

    @PostMapping("/tournaments/{id}/days/add-player/{memberId}")
    public ResponseEntity<DTOs.TournamentDetailResponse> addPlayerMidDay(
            @PathVariable Long id, @PathVariable Long memberId,
            @AuthenticationPrincipal UserDetails ud) {
        Player p = me(ud);
        tournamentService.addPlayerMidDay(id, p, memberId);
        return ResponseEntity.ok(tournamentService.getTournamentDetail(id, p));
    }

    @PostMapping("/tournaments/{id}/days/remove-player/{memberId}")
    public ResponseEntity<DTOs.TournamentDetailResponse> removePlayerMidDay(
            @PathVariable Long id, @PathVariable Long memberId,
            @AuthenticationPrincipal UserDetails ud) {
        Player p = me(ud);
        tournamentService.removePlayerMidDay(id, p, memberId);
        return ResponseEntity.ok(tournamentService.getTournamentDetail(id, p));
    }

    @PostMapping("/tournaments/{id}/days/challenge-match")
    public ResponseEntity<DTOs.MatchResponse> createChallengeMatch(
            @PathVariable Long id, @RequestBody Map<String, Long> body,
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.createChallengeMatch(
                id, me(ud), body.get("member1Id"), body.get("member2Id")));
    }

    // Lightweight poll endpoint — only current day + matches, no full history load
    @GetMapping("/tournaments/{id}/today")
    public ResponseEntity<DTOs.TodayResponse> getTodayData(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.getTodayData(id, me(ud)));
    }

    @GetMapping("/tournaments/{id}/rankings")
    public ResponseEntity<List<DTOs.RankingResponse>> getRankings(@PathVariable Long id) {
        return ResponseEntity.ok(tournamentService.getRankings(id));
    }

    // ── MATCHES ───────────────────────────────────────────────────────────────

    @PostMapping("/matches/{id}/result")
    public ResponseEntity<DTOs.MatchResponse> submitResult(
            @PathVariable Long id, @RequestBody DTOs.MatchResultRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        tournamentService.submitResult(id, me(ud), req.getMember1Score(), req.getMember2Score());
        Match m = tournamentService.getMatchById(id);
        DTOs.MatchResponse mr = new DTOs.MatchResponse();
        mr.id = m.getId(); mr.status = m.getStatus().name();
        mr.member1Score = m.getMember1Score(); mr.member2Score = m.getMember2Score();
        mr.winnerId = m.getWinner() != null ? m.getWinner().getId() : null;
        mr.winnerName = m.getWinner() != null ? m.getWinner().getDisplayName() : null;
        return ResponseEntity.ok(mr);
    }

    // ── CHAT ──────────────────────────────────────────────────────────────────

    @PostMapping("/tournaments/{id}/chat")
    public ResponseEntity<DTOs.ChatMessageResponse> sendMessage(
            @PathVariable Long id, @RequestBody DTOs.SendMessageRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.sendMessage(id, me(ud), req.getContent()));
    }

    @GetMapping("/tournaments/{id}/chat")
    public ResponseEntity<List<DTOs.ChatMessageResponse>> getMessages(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.getMessages(id, me(ud)));
    }

    // ── STATS & HEAD-TO-HEAD ──────────────────────────────────────────────────

    @GetMapping("/tournaments/{id}/members/{memberId}/stats")
    public ResponseEntity<DTOs.MemberStatsResponse> getMemberStats(
            @PathVariable Long id, @PathVariable Long memberId,
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.getMemberStats(id, memberId));
    }

    @GetMapping("/tournaments/{id}/head-to-head")
    public ResponseEntity<DTOs.HeadToHeadResponse> headToHead(
            @PathVariable Long id,
            @RequestParam Long member1Id, @RequestParam Long member2Id,
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.getHeadToHead(id, member1Id, member2Id));
    }

    // ── PLAYERS ───────────────────────────────────────────────────────────────

    @GetMapping("/players/me")
    public ResponseEntity<DTOs.PlayerProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserDetails ud) {
        Player p = me(ud);
        return ResponseEntity.ok(playerService.getProfile(p.getId(), p));
    }

    @GetMapping("/players/{id}")
    public ResponseEntity<DTOs.PlayerProfileResponse> getProfile(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(playerService.getProfile(id, me(ud)));
    }

    @PutMapping("/players/me")
    public ResponseEntity<DTOs.PlayerProfileResponse> updateProfile(
            @RequestBody DTOs.UpdateProfileRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(playerService.updateProfile(me(ud), req));
    }

    @PostMapping("/players/me/password")
    public ResponseEntity<Void> changePassword(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails ud) {
        playerService.changePassword(me(ud), body.get("currentPassword"), body.get("newPassword"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/players/me/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @RequestBody DTOs.UpdateFcmTokenRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        playerService.updateFcmToken(me(ud), req.getFcmToken());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/players/search")
    public ResponseEntity<List<DTOs.PlayerProfileResponse>> searchPlayers(
            @RequestParam String q, @AuthenticationPrincipal UserDetails ud) {
        Player me = me(ud);
        List<DTOs.PlayerProfileResponse> results = playerRepo.findAll().stream()
                .filter(p -> p.getUsername().toLowerCase().contains(q.toLowerCase()) ||
                        p.getDisplayName().toLowerCase().contains(q.toLowerCase()))
                .map(p -> playerService.getProfile(p.getId(), me))
                .collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<DTOs.GlobalLeaderboardEntry>> leaderboard() {
        return ResponseEntity.ok(playerService.getGlobalLeaderboard());
    }

    // ── RSVP ──────────────────────────────────────────────────────────────────

    @PostMapping("/tournaments/{id}/rsvp/send")
    public ResponseEntity<Map<String, Object>> sendRsvp(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.sendRsvpCall(id, me(ud)));
    }

    @PostMapping("/tournaments/{id}/rsvp")
    public ResponseEntity<Map<String, Object>> submitRsvp(
            @PathVariable Long id, @RequestBody DTOs.RsvpRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.submitRsvp(id, me(ud), req.isAttending()));
    }

    // ── SESSION TEMPLATES ─────────────────────────────────────────────────────

    @PostMapping("/tournaments/{id}/templates")
    public ResponseEntity<Map<String, Object>> saveTemplate(
            @PathVariable Long id, @RequestBody DTOs.SessionTemplateRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.saveSessionTemplate(id, me(ud), req));
    }

    @GetMapping("/tournaments/{id}/templates")
    public ResponseEntity<List<Map<String, Object>>> getTemplates(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.getSessionTemplates(id, me(ud)));
    }

    // ── NOTIFICATION PREFERENCES ──────────────────────────────────────────────

    @PutMapping("/tournaments/{id}/notif-prefs")
    public ResponseEntity<Map<String, Object>> updateNotifPrefs(
            @PathVariable Long id, @RequestBody Map<String, Boolean> prefs,
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.updateNotifPrefs(id, me(ud), prefs));
    }

    @GetMapping("/tournaments/{id}/notif-prefs")
    public ResponseEntity<Map<String, Object>> getNotifPrefs(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(tournamentService.getNotifPrefs(id, me(ud)));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleError(RuntimeException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "An error occurred";
        return ResponseEntity.badRequest().body(Map.of("message", msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "Server error";
        // Log it but don't expose internal stack traces
        System.err.println("[ERROR] " + e.getClass().getSimpleName() + ": " + msg);
        return ResponseEntity.internalServerError().body(Map.of("message", msg));
    }
}