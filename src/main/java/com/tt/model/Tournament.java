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

    @Column(nullable = false, unique = true)
    private String name;

    private String password;

    private LocalDateTime createdAt = LocalDateTime.now();

    // Unique invite code for sharing — generated on tournament creation
    @Column(unique = true)
    private String inviteCode;

    // EAGER — always load with Tournament so isAdmin() works outside @Transactional
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "tournament_admins",
            joinColumns = @JoinColumn(name = "tournament_id"),
            inverseJoinColumns = @JoinColumn(name = "player_id")
    )
    private List<Player> admins = new ArrayList<>();

    // Creator — EAGER so we can always post system messages
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_id")
    private Player createdBy;

    public Tournament() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public List<Player> getAdmins() { return admins; }
    public void setAdmins(List<Player> admins) { this.admins = admins; }

    public Player getCreatedBy() { return createdBy; }
    public void setCreatedBy(Player createdBy) { this.createdBy = createdBy; }
}
