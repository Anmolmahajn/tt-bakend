package com.tt.dto;

import java.time.LocalDateTime;
import java.util.List;

public class DTOs {

    // ── REQUESTS ──────────────────────────────────────────────────────────────

    public static class RegisterRequest {
        private String username, email, password, displayName, proficiency;
        public String getUsername() { return username; } public void setUsername(String v) { username=v; }
        public String getEmail() { return email; } public void setEmail(String v) { email=v; }
        public String getPassword() { return password; } public void setPassword(String v) { password=v; }
        public String getDisplayName() { return displayName; } public void setDisplayName(String v) { displayName=v; }
        public String getProficiency() { return proficiency; } public void setProficiency(String v) { proficiency=v; }
    }

    public static class LoginRequest {
        private String email, password;
        public String getEmail() { return email; } public void setEmail(String v) { email=v; }
        public String getPassword() { return password; } public void setPassword(String v) { password=v; }
    }

    public static class CreateTournamentRequest {
        private String name, password;
        public String getName() { return name; } public void setName(String v) { name=v; }
        public String getPassword() { return password; } public void setPassword(String v) { password=v; }
    }

    public static class JoinTournamentRequest {
        private String tournamentName, password;
        public String getTournamentName() { return tournamentName; } public void setTournamentName(String v) { tournamentName=v; }
        public String getPassword() { return password; } public void setPassword(String v) { password=v; }
    }

    public static class AddGuestRequest {
        private String guestName, proficiency;
        public String getGuestName() { return guestName; } public void setGuestName(String v) { guestName=v; }
        public String getProficiency() { return proficiency; } public void setProficiency(String v) { proficiency=v; }
    }

    public static class PromoteAdminRequest {
        private Long playerId;
        public Long getPlayerId() { return playerId; } public void setPlayerId(Long v) { playerId=v; }
    }

    public static class StartDayRequest {
        private List<Long> presentMemberIds; private String matchFormat;
        private int numberOfTeams; private int playersPerTeam;
        public List<Long> getPresentMemberIds() { return presentMemberIds; } public void setPresentMemberIds(List<Long> v) { presentMemberIds=v; }
        public String getMatchFormat() { return matchFormat; } public void setMatchFormat(String v) { matchFormat=v; }
        public int getNumberOfTeams() { return numberOfTeams; } public void setNumberOfTeams(int v) { numberOfTeams=v; }
        public int getPlayersPerTeam() { return playersPerTeam; } public void setPlayersPerTeam(int v) { playersPerTeam=v; }
    }

    public static class MatchResultRequest {
        private int member1Score, member2Score;
        public int getMember1Score() { return member1Score; } public void setMember1Score(int v) { member1Score=v; }
        public int getMember2Score() { return member2Score; } public void setMember2Score(int v) { member2Score=v; }
    }

    public static class SendMessageRequest {
        private String content;
        public String getContent() { return content; } public void setContent(String v) { content=v; }
    }

    public static class UpdateProfileRequest {
        private String displayName, avatarUrl;
        public String getDisplayName() { return displayName; } public void setDisplayName(String v) { displayName=v; }
        public String getAvatarUrl() { return avatarUrl; } public void setAvatarUrl(String v) { avatarUrl=v; }
    }

    public static class UpdateFcmTokenRequest {
        private String fcmToken;
        public String getFcmToken() { return fcmToken; }
        public void setFcmToken(String v) { fcmToken=v; }
    }

    public static class CustomRankingsRequest {
        private List<CustomRankUpdate> updates;
        public List<CustomRankUpdate> getUpdates() { return updates; } public void setUpdates(List<CustomRankUpdate> v) { updates=v; }
    }

    public static class CustomRankUpdate {
        private Long memberId; private int rank;
        public Long getMemberId() { return memberId; } public void setMemberId(Long v) { memberId=v; }
        public int getRank() { return rank; } public void setRank(int v) { rank=v; }
    }

    // ── RESPONSES ─────────────────────────────────────────────────────────────

    public static class AuthResponse {
        public String token, username, displayName, email, proficiency;
        public Long userId;
        public AuthResponse() {}
        public AuthResponse(String token, String username, String displayName,
                            String email, Long userId, String proficiency) {
            this.token=token; this.username=username; this.displayName=displayName;
            this.email=email; this.userId=userId; this.proficiency=proficiency;
        }
        public String getToken() { return token; }
        public String getUsername() { return username; }
        public String getDisplayName() { return displayName; }
        public String getEmail() { return email; }
        public Long getUserId() { return userId; }
        public String getProficiency() { return proficiency; }
    }

    public static class TournamentSummaryResponse {
        public Long id; public String name; public int memberCount, adminCount, daysPlayed;
        public boolean isAdmin, isMember; public LocalDateTime createdAt;
        public String lastDayStatus; public int lastDayNumber;
    }

    public static class TournamentDetailResponse {
        public Long id; public String name; public int memberCount;
        public List<MemberResponse> members; public List<AdminResponse> admins;
        public List<DayResponse> days; public DayResponse currentDay;
        public List<RankingResponse> rankings; public LocalDateTime createdAt; public boolean isAdmin;
    }

    // ── NEW: Lightweight response for Today tab polling ───────────────────────
    // Only returns current day + matches — much faster than full TournamentDetailResponse
    public static class TodayResponse {
        public DayResponse currentDay;   // null if no active day
        public boolean isAdmin;
        public long serverTime;          // ms since epoch
    }

    public static class MemberResponse {
        public Long id, playerId; public String displayName, proficiency;
        public boolean isGuest; public int currentRank, totalMatchesPlayed,
                totalMatchesWon, totalMatchesLost, daysPlayed, mvpCount; public double winRate;
    }

    public static class AdminResponse { public Long playerId; public String displayName; }

    public static class DayResponse {
        public Long id; public int dayNumber; public String status, matchFormat;
        public int numberOfTeams; public List<MemberResponse> presentMembers;
        public List<TeamResponse> teams; public List<MatchResponse> matches;
        public LocalDateTime startedAt, endedAt;
        public long timerSeconds, elapsedSeconds;
        public String mvpName; public Long mvpMemberId;
    }

    public static class TeamResponse {
        public Long id; public String name; public int matchesWon, matchesLost;
        public List<MemberResponse> members;
    }

    public static class MatchResponse {
        public Long id; public int matchNumber; public Long member1Id, member2Id;
        public String member1Name, member2Name, team1Name, team2Name;
        public List<String> team1Members, team2Members;
        public int member1Score, member2Score; public Long winnerId; public String winnerName;
        public String status; public double member1WinProb, member2WinProb;
        public LocalDateTime startedAt, completedAt;
    }

    public static class RankingResponse {
        public int rank, totalMatchesWon, totalMatchesPlayed, totalMatchesLost,
                daysPlayed, rankChangeSinceYesterday, mvpCount;
        public Long memberId; public String displayName, proficiency; public boolean isGuest; public double winRate;
    }

    public static class DayRankEntry {
        public int rank, matchesPlayed, matchesWon, matchesLost,
                pointsScored, pointsConceded, rankChange, mvpCount;
        public Long memberId; public String displayName, proficiency; public boolean isGuest, isMvp;
        public double dayScore;
    }

    public static class ChatMessageResponse {
        public Long id, senderId; public String senderName, content, type;
        public LocalDateTime sentAt;
    }

    public static class PlayerProfileResponse {
        public Long id; public String username, displayName, email, avatarUrl, proficiency;
        public int totalMatchesPlayed, totalMatchesWon, totalMatchesLost, tournamentsPlayed, tournamentsWon;
        public double winRate; public LocalDateTime createdAt;
        public List<TournamentStatsResponse> tournamentStats;
    }

    public static class TournamentStatsResponse {
        public Long tournamentId; public String tournamentName;
        public int currentRank, totalMembersInTournament, matchesWon, matchesPlayed, daysPlayed;
        public double winRate;
    }

    public static class GlobalLeaderboardEntry {
        public int rank; public Long playerId; public String displayName;
        public int totalMatchesPlayed, totalMatchesWon, tournamentsPlayed, tournamentsWon;
        public double winRate;
    }

    public static class HeadToHeadResponse {
        public Long member1Id, member2Id; public String member1Name, member2Name;
        public int member1Wins, member2Wins, member1PointsTotal, member2PointsTotal, totalMatches;
        public double member1WinPct, member2WinPct; public List<HeadToHeadMatch> matches;
    }

    public static class HeadToHeadMatch {
        public Long matchId, winnerId; public int dayNumber, member1Score, member2Score;
        public LocalDateTime playedAt;
    }

    public static class MemberStatsResponse {
        public Long memberId; public String displayName, proficiency, bestPartnerName, bestRivalName;
        public int currentRank, totalMatchesPlayed, totalMatchesWon, totalMatchesLost, daysPlayed, mvpCount;
        public double winRate; public List<DailyStatEntry> dailyStats;
    }

    public static class DailyStatEntry {
        public int dayNumber, rank, matchesWon, matchesPlayed, pointsScored, pointsConceded;
        public double dayScore; public boolean isMvp; public LocalDateTime date;
    }
}
