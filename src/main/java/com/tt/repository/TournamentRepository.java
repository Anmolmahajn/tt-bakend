package com.tt.repository;

import com.tt.model.Player;
import com.tt.model.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    Optional<Tournament> findByName(String name);
    boolean existsByName(String name);

    Optional<Tournament> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);

    @Query("SELECT DISTINCT m.tournament FROM TournamentMember m WHERE m.player = :player AND m.isGuest = false")
    List<Tournament> findByMemberPlayer(@Param("player") Player player);
}
