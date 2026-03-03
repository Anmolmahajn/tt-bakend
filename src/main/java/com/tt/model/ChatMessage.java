package com.tt.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    // nullable = true so system messages (no sender) work
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = true)
    private Player sender;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    private MessageType type = MessageType.TEXT;

    private LocalDateTime sentAt = LocalDateTime.now();

    // reactions stored as JSON string e.g. {"🏓":[1,2,3],"🔥":[4]}
    @Column(columnDefinition = "TEXT")
    private String reactions;

    public enum MessageType {
        TEXT, SYSTEM, MATCH_RESULT, DAY_STARTED, DAY_ENDED, CHALLENGE, POLL, SCHEDULE
    }

    public ChatMessage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Tournament getTournament() { return tournament; }
    public void setTournament(Tournament tournament) { this.tournament = tournament; }

    public Player getSender() { return sender; }
    public void setSender(Player sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public String getReactions() { return reactions; }
    public void setReactions(String reactions) { this.reactions = reactions; }
}
