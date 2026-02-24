package com.tt.repository;

import com.tt.model.Player;
import com.tt.model.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {


    Optional<Tournament> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);

    @Query("SELECT DISTINCT t FROM Tournament t JOIN t.members m WHERE m.player = :player")
    List<Tournament> findByMemberPlayer(@Param("player") Player player);
}
