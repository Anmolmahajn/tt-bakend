package com.tt.repository;

import com.tt.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DailyRankingSnapshotRepository extends JpaRepository<DailyRankingSnapshot, Long> {

    List<DailyRankingSnapshot> findByTournamentAndDayOrderByRankAsc(Tournament t, TournamentDay d);
    List<DailyRankingSnapshot> findByMemberOrderByDayAsc(TournamentMember member);

    // FIX: delete all snapshots for a member before deleting member (FK constraint)
    @Modifying
    @Query("DELETE FROM DailyRankingSnapshot s WHERE s.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);

    // FIX: delete all snapshots for a day before deleting day
    @Modifying
    @Query("DELETE FROM DailyRankingSnapshot s WHERE s.day = :day")
    void deleteByDay(@Param("day") TournamentDay day);
}
