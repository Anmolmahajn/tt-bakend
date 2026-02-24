package com.tt.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tournaments")
public class Tournament {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true) private String name;
    @Column(nullable = false) private String passwordHash;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "created_by_id")
    private Player createdBy;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "tournament_admins",
            joinColumns = @JoinColumn(name = "tournament_id"),
            inverseJoinColumns = @JoinColumn(name = "player_id"))
    private List<Player> admins = new ArrayList<>();
    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TournamentMember> members = new ArrayList<>();
    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    private List<TournamentDay> days = new ArrayList<>();
    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sentAt ASC")
    private List<ChatMessage> chatMessages = new ArrayList<>();
    private LocalDateTime createdAt = LocalDateTime.now();

    public Tournament() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Player getCreatedBy() { return createdBy; }
    public void setCreatedBy(Player createdBy) { this.createdBy = createdBy; }
    public List<Player> getAdmins() { return admins; }
    public void setAdmins(List<Player> admins) { this.admins = admins; }
    public List<TournamentMember> getMembers() { return members; }
    public void setMembers(List<TournamentMember> members) { this.members = members; }
    public List<TournamentDay> getDays() { return days; }
    public void setDays(List<TournamentDay> days) { this.days = days; }
    public List<ChatMessage> getChatMessages() { return chatMessages; }
    public void setChatMessages(List<ChatMessage> chatMessages) { this.chatMessages = chatMessages; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
