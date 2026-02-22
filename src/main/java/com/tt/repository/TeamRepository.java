package com.tt.repository;

import com.tt.model.Team;
import com.tt.model.TournamentDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByDayOrderByMatchesWonDesc(TournamentDay day);
}
