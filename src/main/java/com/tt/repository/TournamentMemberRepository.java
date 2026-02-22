package com.tt.repository;

import com.tt.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TournamentMemberRepository extends JpaRepository<TournamentMember, Long> {

    List<TournamentMember> findByTournamentOrderByCurrentRankAsc(Tournament tournament);
    boolean existsByTournamentAndPlayer(Tournament t, Player p);
    boolean existsByTournamentAndGuestName(Tournament t, String guestName);

    @Query("SELECT m FROM TournamentMember m WHERE m.tournament.id = :tid ORDER BY " +
            "CASE WHEN m.currentRank = 0 THEN 99999 ELSE m.currentRank END ASC")
    List<TournamentMember> findRanked(@Param("tid") Long tournamentId);

    @Modifying
    @Query("DELETE FROM TournamentMember m WHERE m.tournament = :t")
    void deleteByTournament(@Param("t") Tournament t);
}
