package com.tt.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "matches")
public class Match {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "day_id", nullable = false)
    private TournamentDay day;
    @ManyToOne @JoinColumn(name = "member1_id", nullable = false)
    private TournamentMember member1;
    @ManyToOne @JoinColumn(name = "member2_id", nullable = false)
    private TournamentMember member2;
    @ManyToOne @JoinColumn(name = "team1_id")
    private Team team1;
    @ManyToOne @JoinColumn(name = "team2_id")
    private Team team2;
    private int member1Score = 0;
    private int member2Score = 0;
    @ManyToOne @JoinColumn(name = "winner_id")
    private TournamentMember winner;
    @Enumerated(EnumType.STRING)
    private MatchStatus status = MatchStatus.SCHEDULED;
    private int matchNumber = 0;
    private double member1WinProb;
    private double member2WinProb;
    private String predictedWinner;
    private LocalDateTime scheduledAt = LocalDateTime.now();
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public Match() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Tournament getTournament() { return tournament; }
    public void setTournament(Tournament tournament) { this.tournament = tournament; }
    public TournamentDay getDay() { return day; }
    public void setDay(TournamentDay day) { this.day = day; }
    public TournamentMember getMember1() { return member1; }
    public void setMember1(TournamentMember member1) { this.member1 = member1; }
    public TournamentMember getMember2() { return member2; }
    public void setMember2(TournamentMember member2) { this.member2 = member2; }
    public Team getTeam1() { return team1; }
    public void setTeam1(Team team1) { this.team1 = team1; }
    public Team getTeam2() { return team2; }
    public void setTeam2(Team team2) { this.team2 = team2; }
    public int getMember1Score() { return member1Score; }
    public void setMember1Score(int v) { this.member1Score = v; }
    public int getMember2Score() { return member2Score; }
    public void setMember2Score(int v) { this.member2Score = v; }
    public TournamentMember getWinner() { return winner; }
    public void setWinner(TournamentMember winner) { this.winner = winner; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }
    public int getMatchNumber() { return matchNumber; }
    public void setMatchNumber(int matchNumber) { this.matchNumber = matchNumber; }
    public double getMember1WinProb() { return member1WinProb; }
    public void setMember1WinProb(double v) { this.member1WinProb = v; }
    public double getMember2WinProb() { return member2WinProb; }
    public void setMember2WinProb(double v) { this.member2WinProb = v; }
    public String getPredictedWinner() { return predictedWinner; }
    public void setPredictedWinner(String predictedWinner) { this.predictedWinner = predictedWinner; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime v) { this.scheduledAt = v; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public enum MatchStatus { SCHEDULED, IN_PROGRESS, COMPLETED }
}
