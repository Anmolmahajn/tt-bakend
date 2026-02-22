package com.tt.repository;

import com.tt.model.Tournament;
import com.tt.model.TournamentDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentDayRepository extends JpaRepository<TournamentDay, Long> {
    List<TournamentDay> findByTournamentOrderByDayNumberAsc(Tournament tournament);
    Optional<TournamentDay> findByTournamentAndDayNumber(Tournament tournament, int dayNumber);
    @Query("SELECT MAX(d.dayNumber) FROM TournamentDay d WHERE d.tournament = :t")
    Integer findMaxDayNumber(@Param("t") Tournament tournament);
    Optional<TournamentDay> findFirstByTournamentAndStatusOrderByDayNumberDesc(Tournament tournament, TournamentDay.DayStatus status);
}
