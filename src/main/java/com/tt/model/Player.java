package com.tt.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "players")
public class Player {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true) private String username;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false) private String passwordHash;
    @Column(nullable = false) private String displayName;
    private String avatarUrl;
    private String fcmToken;
    private int totalMatchesPlayed = 0;
    private int totalMatchesWon = 0;
    private int totalMatchesLost = 0;
    private int totalPointsScored = 0;
    private int totalPointsConceded = 0;
    private int tournamentsPlayed = 0;
    private int tournamentsWon = 0;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastActiveAt;
    private String proficiency = "Intermediate";
    public String getProficiency() { return proficiency; }
    public void setProficiency(String v) { this.proficiency = v; }

    public Player() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
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
    public int getTournamentsPlayed() { return tournamentsPlayed; }
    public void setTournamentsPlayed(int v) { this.tournamentsPlayed = v; }
    public int getTournamentsWon() { return tournamentsWon; }
    public void setTournamentsWon(int v) { this.tournamentsWon = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime v) { this.lastActiveAt = v; }
    public double getWinRate() {
        return totalMatchesPlayed == 0 ? 0.0 : (double) totalMatchesWon / totalMatchesPlayed * 100;
    }
}
