package com.tt.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tournament_members")
public class TournamentMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "player_id")
    private Player player;
    private String guestName;
    private boolean isGuest = false;
    private int currentRank = 0;
    private int totalMatchesPlayed = 0;
    private int totalMatchesWon = 0;
    private int totalMatchesLost = 0;
    private int totalPointsScored = 0;
    private int totalPointsConceded = 0;
    private int daysPlayed = 0;
    private LocalDateTime joinedAt = LocalDateTime.now();
    // NEW: proficiency for guest players (registered players use Player.proficiency)
    private String guestProficiency = "Intermediate";
    // NEW: total MVP awards won across all days
    private int mvpCount = 0;
    private int eloRating = 1200;
    private int currentWinStreak = 0;
    private int bestWinStreak = 0;
    private int sessionStreak = 0;
    @Column(length = 512)
    private String notifPrefs = "{\"MATCH_RESULT\":true,\"CHALLENGE\":true,\"DAY_START\":true,\"MILESTONE\":true}";






    public TournamentMember() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Tournament getTournament() { return tournament; }
    public void setTournament(Tournament tournament) { this.tournament = tournament; }
    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }
    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public boolean isGuest() { return isGuest; }
    public void setGuest(boolean isGuest) { this.isGuest = isGuest; }
    public int getCurrentRank() { return currentRank; }
    public void setCurrentRank(int currentRank) { this.currentRank = currentRank; }
    public int getTotalMatchesPlayed() { return totalMatchesPlayed; }
    public void setTotalMatchesPlayed(int v) { this.totalMatchesPlayed = v; }
    public int getTotalMatchesWon() { return totalMatchesWon; }
    public void setTotalMatchesWon(int v) { this.totalMatchesWon = v; }
    public int getTotalMatchesLost() { return totalMatchesLost; }
    public void setTotalMatchesLost(int v) { this.totalMatchesLost = v; }
    public int getTotalPointsScored() { return totalPointsScored; }
    public void setTotalPointsScored(int v) { this.totalPointsScored = v; }
    public int getTotalPointsConceded() { return totalPointsConceded; }
    public void setTotalPointsConceded(int v) { this.totalPointsConceded = v; }
    public int getDaysPlayed() { return daysPlayed; }
    public void setDaysPlayed(int v) { this.daysPlayed = v; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime v) { this.joinedAt = v; }
    public String getGuestProficiency() { return guestProficiency; }
    public void setGuestProficiency(String v) { this.guestProficiency = v; }
    public int getMvpCount() { return mvpCount; }
    public void setMvpCount(int v) { this.mvpCount = v; }
    public String getDisplayName() {
        return isGuest ? guestName : (player != null ? player.getDisplayName() : "Unknown");
    }
    public double getWinRate() {
        return totalMatchesPlayed == 0 ? 0.0 : (double) totalMatchesWon / totalMatchesPlayed * 100;
    }
    public int getEloRating() { return eloRating; }
    public void setEloRating(int v) { this.eloRating = v; }

    public int getCurrentWinStreak() { return currentWinStreak; }
    public void setCurrentWinStreak(int v) { this.currentWinStreak = v; }

    public int getBestWinStreak() { return bestWinStreak; }
    public void setBestWinStreak(int v) { this.bestWinStreak = v; }

    public int getSessionStreak() { return sessionStreak; }
    public void setSessionStreak(int v) { this.sessionStreak = v; }

    public String getNotifPrefs() { return notifPrefs; }
    public void setNotifPrefs(String v) { this.notifPrefs = v; }
}
