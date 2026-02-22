package com.tt.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tournament_days")
public class TournamentDay {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;
    @Column(nullable = false) private int dayNumber;
    @Enumerated(EnumType.STRING) private DayStatus status = DayStatus.SETUP;
    @Enumerated(EnumType.STRING) private MatchFormat matchFormat = MatchFormat.TEAM_VS_TEAM;
    private int numberOfTeams = 2;
    private int playersPerTeam = 0;
    @ManyToMany
    @JoinTable(name = "day_present_members",
        joinColumns = @JoinColumn(name = "day_id"),
        inverseJoinColumns = @JoinColumn(name = "member_id"))
    private List<TournamentMember> presentMembers = new ArrayList<>();
    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Team> teams = new ArrayList<>();
    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("matchNumber ASC")
    private List<Match> matches = new ArrayList<>();
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private long timerSeconds = 0;
    private LocalDateTime createdAt = LocalDateTime.now();

    public TournamentDay() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Tournament getTournament() { return tournament; }
    public void setTournament(Tournament t) { this.tournament = t; }
    public int getDayNumber() { return dayNumber; }
    public void setDayNumber(int dayNumber) { this.dayNumber = dayNumber; }
    public DayStatus getStatus() { return status; }
    public void setStatus(DayStatus status) { this.status = status; }
    public MatchFormat getMatchFormat() { return matchFormat; }
    public void setMatchFormat(MatchFormat matchFormat) { this.matchFormat = matchFormat; }
    public int getNumberOfTeams() { return numberOfTeams; }
    public void setNumberOfTeams(int numberOfTeams) { this.numberOfTeams = numberOfTeams; }
    public int getPlayersPerTeam() { return playersPerTeam; }
    public void setPlayersPerTeam(int playersPerTeam) { this.playersPerTeam = playersPerTeam; }
    public List<TournamentMember> getPresentMembers() { return presentMembers; }
    public void setPresentMembers(List<TournamentMember> presentMembers) { this.presentMembers = presentMembers; }
    public List<Team> getTeams() { return teams; }
    public void setTeams(List<Team> teams) { this.teams = teams; }
    public List<Match> getMatches() { return matches; }
    public void setMatches(List<Match> matches) { this.matches = matches; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public long getTimerSeconds() { return timerSeconds; }
    public void setTimerSeconds(long timerSeconds) { this.timerSeconds = timerSeconds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public enum DayStatus { SETUP, IN_PROGRESS, ENDED }
    public enum MatchFormat { TEAM_VS_TEAM, FREE_FOR_ALL, CUSTOM_TEAMS, BALANCED_TEAMS, TEAM_2V2 }
}
