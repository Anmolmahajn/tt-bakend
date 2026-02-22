package com.tt.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_ranking_snapshots")
public class DailyRankingSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "member_id", nullable = false)
    private TournamentMember member;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "day_id", nullable = false)
    private TournamentDay day;
    private int rank;
    private int matchesWonToday;
    private int matchesPlayedToday;
    private int pointsScoredToday;
    private int pointsConcededToday;
    private boolean mvp = false;
    private LocalDateTime recordedAt = LocalDateTime.now();

    public DailyRankingSnapshot() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Tournament getTournament() { return tournament; }
    public void setTournament(Tournament tournament) { this.tournament = tournament; }
    public TournamentMember getMember() { return member; }
    public void setMember(TournamentMember member) { this.member = member; }
    public TournamentDay getDay() { return day; }
    public void setDay(TournamentDay day) { this.day = day; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public int getMatchesWonToday() { return matchesWonToday; }
    public void setMatchesWonToday(int v) { this.matchesWonToday = v; }
    public int getMatchesPlayedToday() { return matchesPlayedToday; }
    public void setMatchesPlayedToday(int v) { this.matchesPlayedToday = v; }
    public int getPointsScoredToday() { return pointsScoredToday; }
    public void setPointsScoredToday(int v) { this.pointsScoredToday = v; }
    public int getPointsConcededToday() { return pointsConcededToday; }
    public void setPointsConcededToday(int v) { this.pointsConcededToday = v; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
    public boolean isMvp() { return mvp; }
    public void setMvp(boolean mvp) { this.mvp = mvp; }

}
