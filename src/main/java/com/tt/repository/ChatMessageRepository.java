package com.tt.repository;

import com.tt.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByTournamentOrderBySentAtAsc(Tournament tournament);

    @Modifying
    @Query("DELETE FROM ChatMessage c WHERE c.tournament = :t")
    void deleteByTournament(@Param("t") Tournament t);
}
