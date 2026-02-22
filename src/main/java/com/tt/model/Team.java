package com.tt.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "teams")
public class Team {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String name;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tournament_id")
    private Tournament tournament;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "day_id")
    private TournamentDay day;
    @ManyToMany
    @JoinTable(name = "team_members",
        joinColumns = @JoinColumn(name = "team_id"),
        inverseJoinColumns = @JoinColumn(name = "member_id"))
    private List<TournamentMember> members = new ArrayList<>();
    private int matchesWon = 0;
    private int matchesLost = 0;
    private double avgRank = 0;

    public Team() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Tournament getTournament() { return tournament; }
    public void setTournament(Tournament tournament) { this.tournament = tournament; }
    public TournamentDay getDay() { return day; }
    public void setDay(TournamentDay day) { this.day = day; }
    public List<TournamentMember> getMembers() { return members; }
    public void setMembers(List<TournamentMember> members) { this.members = members; }
    public int getMatchesWon() { return matchesWon; }
    public void setMatchesWon(int matchesWon) { this.matchesWon = matchesWon; }
    public int getMatchesLost() { return matchesLost; }
    public void setMatchesLost(int matchesLost) { this.matchesLost = matchesLost; }
    public double getAvgRank() { return avgRank; }
    public void setAvgRank(double avgRank) { this.avgRank = avgRank; }
}
