package com.tt.service;

import com.tt.dto.DTOs;
import com.tt.model.Player;
import com.tt.model.TournamentMember;
import com.tt.repository.PlayerRepository;
import com.tt.repository.TournamentMemberRepository;
import com.tt.repository.TournamentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
public class PlayerService {

    private final PlayerRepository playerRepo;
    private final TournamentMemberRepository memberRepo;
    private final TournamentRepository tournamentRepo;
    private final PasswordEncoder passwordEncoder;

    public PlayerService(PlayerRepository playerRepo, TournamentMemberRepository memberRepo,
                         TournamentRepository tournamentRepo, PasswordEncoder passwordEncoder) {
        this.playerRepo = playerRepo;
        this.memberRepo = memberRepo;
        this.tournamentRepo = tournamentRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public DTOs.PlayerProfileResponse getProfile(Long playerId, Player requester) {
        Player p = playerRepo.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Player not found"));
        List<TournamentMember> memberships = memberRepo.findAll().stream()
                .filter(m -> !m.isGuest() && m.getPlayer() != null
                        && m.getPlayer().getId().equals(p.getId()))
                .toList();

        List<DTOs.TournamentStatsResponse> stats = new ArrayList<>();
        for (TournamentMember m : memberships) {
            int totalInTournament = memberRepo
                    .findByTournamentOrderByCurrentRankAsc(m.getTournament()).size();
            DTOs.TournamentStatsResponse s = new DTOs.TournamentStatsResponse();
            s.tournamentId   = m.getTournament().getId();
            s.tournamentName = m.getTournament().getName();
            s.currentRank    = m.getCurrentRank();
            s.totalMembersInTournament = totalInTournament;
            s.matchesWon     = m.getTotalMatchesWon();
            s.matchesPlayed  = m.getTotalMatchesPlayed();
            s.winRate        = m.getWinRate();
            s.daysPlayed     = m.getDaysPlayed();
            stats.add(s);
        }

        DTOs.PlayerProfileResponse res = new DTOs.PlayerProfileResponse();
        res.id                 = p.getId();
        res.username           = p.getUsername();
        res.displayName        = p.getDisplayName();
        res.email              = requester.getId().equals(p.getId()) ? p.getEmail() : null;
        res.avatarUrl          = p.getAvatarUrl();
        res.proficiency        = p.getProficiency();          // NEW: expose proficiency
        res.totalMatchesPlayed = p.getTotalMatchesPlayed();
        res.totalMatchesWon    = p.getTotalMatchesWon();
        res.totalMatchesLost   = p.getTotalMatchesLost();
        res.winRate            = p.getWinRate();
        res.tournamentsPlayed  = p.getTournamentsPlayed();
        res.tournamentsWon     = p.getTournamentsWon();
        res.createdAt          = p.getCreatedAt();
        res.tournamentStats    = stats;
        return res;
    }

    public List<DTOs.GlobalLeaderboardEntry> getGlobalLeaderboard() {
        List<Player> players = playerRepo.findGlobalLeaderboard();
        List<DTOs.GlobalLeaderboardEntry> result = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            DTOs.GlobalLeaderboardEntry e = new DTOs.GlobalLeaderboardEntry();
            e.rank                = i + 1;
            e.playerId            = p.getId();
            e.displayName         = p.getDisplayName();
            e.totalMatchesPlayed  = p.getTotalMatchesPlayed();
            e.totalMatchesWon     = p.getTotalMatchesWon();
            e.winRate             = p.getWinRate();
            e.tournamentsPlayed   = p.getTournamentsPlayed();
            e.tournamentsWon      = p.getTournamentsWon();
            result.add(e);
        }
        return result;
    }

    @Transactional
    public DTOs.PlayerProfileResponse updateProfile(Player player, DTOs.UpdateProfileRequest req) {
        if (req.getDisplayName() != null && !req.getDisplayName().isBlank())
            player.setDisplayName(req.getDisplayName().trim());
        if (req.getAvatarUrl() != null)
            player.setAvatarUrl(req.getAvatarUrl().trim());
        if (req.getProficiency() != null && !req.getProficiency().isBlank())
            player.setProficiency(req.getProficiency());
        playerRepo.save(player);
        return getProfile(player.getId(), player);
    }

    @Transactional
    public void changePassword(Player player, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, player.getPasswordHash()))
            throw new RuntimeException("Current password is incorrect");
        if (newPassword == null || newPassword.length() < 6)
            throw new RuntimeException("New password must be at least 6 characters");
        player.setPasswordHash(passwordEncoder.encode(newPassword));
        playerRepo.save(player);
    }

    @Transactional
    public void updateFcmToken(Player player, String token) {
        player.setFcmToken(token);
        playerRepo.save(player);
    }

    // FIX: was calling 5-arg constructor — now calls 6-arg with proficiency
    public DTOs.AuthResponse toAuthResponse(Player p, String token) {
        return new DTOs.AuthResponse(token, p.getUsername(), p.getDisplayName(),
                p.getEmail(), p.getId(), p.getProficiency());
    }
}
