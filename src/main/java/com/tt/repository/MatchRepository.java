package com.tt.repository;

import com.tt.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByDayOrderByMatchNumberAsc(TournamentDay day);

    List<Match> findByDayAndStatus(TournamentDay day, Match.MatchStatus status);

    @Modifying
    @Query("UPDATE Match m SET m.winner = NULL WHERE m.winner.id = :memberId")
    void nullifyWinnerIfMember(@Param("memberId") Long memberId);

    // NEW: nullify member1/member2 FKs so removeMember doesn't crash
    @Modifying
    @Query("UPDATE Match m SET m.member1 = NULL WHERE m.member1.id = :memberId")
    void nullifyMember1IfMember(@Param("memberId") Long memberId);

    @Modifying
    @Query("UPDATE Match m SET m.member2 = NULL WHERE m.member2.id = :memberId")
    void nullifyMember2IfMember(@Param("memberId") Long memberId);

    @Query("SELECT m FROM Match m WHERE " +
            "(m.member1.id = :id1 AND m.member2.id = :id2) OR " +
            "(m.member1.id = :id2 AND m.member2.id = :id1) " +
            "ORDER BY m.day.dayNumber ASC")
    List<Match> findHeadToHead(@Param("id1") Long member1Id, @Param("id2") Long member2Id);

    @Query("SELECT m FROM Match m WHERE m.member1.id = :memberId OR m.member2.id = :memberId")
    List<Match> findByMember(@Param("memberId") Long memberId);
}
