package com.tt.repository;

import com.tt.model.Player;
import com.tt.model.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {
    Optional<Tournament> findByName(String name);
    boolean existsByName(String name);
    @Query("SELECT DISTINCT t FROM Tournament t JOIN t.members m WHERE m.player = :player ORDER BY t.createdAt DESC")
    List<Tournament> findByMemberPlayer(@Param("player") Player player);
}
