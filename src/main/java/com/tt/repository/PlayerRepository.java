package com.tt.repository;

import com.tt.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
    Optional<Player> findByEmail(String email);
    Optional<Player> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    @Query("SELECT p FROM Player p ORDER BY p.totalMatchesWon DESC, p.totalMatchesPlayed ASC")
    List<Player> findGlobalLeaderboard();
}
