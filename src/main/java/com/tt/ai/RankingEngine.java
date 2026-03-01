package com.tt.ai;

import com.tt.model.TournamentMember;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RankingEngine {

    private static final int ELO_BASE   = 1200;
    private static final int ELO_K_NORMAL = 32;
    private static final int ELO_K_NEW    = 48; // higher K for first 10 matches

    public List<TournamentMember> recomputeRanksWithProficiency(List<TournamentMember> members) {
        Map<String,Integer> profLevel = Map.of(
                "Beginner",0,"Intermediate",1,"Advanced",2,"Expert",3,"Professional",4);
        List<TournamentMember> sorted = members.stream()
                .sorted(Comparator
                        .<TournamentMember>comparingDouble(m -> {
                            if (m.getTotalMatchesPlayed() == 0) return -1.0;
                            return -(double) m.getTotalMatchesWon() / m.getTotalMatchesPlayed();
                        })
                        .thenComparingInt(m -> {
                            String p = m.isGuest() ? m.getGuestProficiency()
                                    : (m.getPlayer() != null ? m.getPlayer().getProficiency() : null);
                            return -(profLevel.getOrDefault(p, 1));
                        })
                        .thenComparingInt(m -> -m.getTotalMatchesPlayed())
                        .thenComparing(TournamentMember::getDisplayName))
                .collect(Collectors.toList());
        for (int i = 0; i < sorted.size(); i++) sorted.get(i).setCurrentRank(i + 1);
        return sorted;
    }

    /** Update Elo ratings in-place for winner and loser. */
    public void updateElo(TournamentMember winner, TournamentMember loser) {
        int rW = winner.getEloRating(), rL = loser.getEloRating();
        double expW = 1.0 / (1.0 + Math.pow(10, (rL - rW) / 400.0));
        int kW = winner.getTotalMatchesPlayed() < 10 ? ELO_K_NEW : ELO_K_NORMAL;
        int kL = loser.getTotalMatchesPlayed()  < 10 ? ELO_K_NEW : ELO_K_NORMAL;
        winner.setEloRating(Math.max(800, (int) Math.round(rW + kW * (1.0 - expW))));
        loser.setEloRating (Math.max(800, (int) Math.round(rL + kL * (0.0 - (1.0 - expW)))));
    }

    /**
     * Update win streak. Returns true if a milestone was just reached (3/5/10/20 streak).
     */
    public boolean updateWinStreak(TournamentMember m, boolean won) {
        if (won) {
            m.setCurrentWinStreak(m.getCurrentWinStreak() + 1);
            if (m.getCurrentWinStreak() > m.getBestWinStreak())
                m.setBestWinStreak(m.getCurrentWinStreak());
            int s = m.getCurrentWinStreak();
            return s == 3 || s == 5 || s == 10 || s == 20;
        } else {
            m.setCurrentWinStreak(0);
            return false;
        }
    }

    public double computeDayScore(int won, int played, int scored, int conceded) {
        if (played == 0) return 0.0;
        double ratio = (double) won / played;
        double ptDiff = (double)(scored - conceded) / (played * 11.0);
        return 0.70 * ratio + 0.30 * ((ptDiff + 1) / 2.0);
    }

    public double predictWinProb(int rank1, int rank2, int total) {
        double s1 = total - rank1 + 1, s2 = total - rank2 + 1;
        return s1 / (s1 + s2);
    }

    public double predictWinProbElo(int elo1, int elo2) {
        return 1.0 / (1.0 + Math.pow(10, (elo2 - elo1) / 400.0));
    }

    public List<List<TournamentMember>> balanceTeams(List<TournamentMember> members, int numTeams) {
        Map<String,Integer> profLevel = Map.of(
                "Beginner",0,"Intermediate",1,"Advanced",2,"Expert",3,"Professional",4);
        List<TournamentMember> sorted = members.stream()
                .sorted(Comparator.comparingDouble(m -> {
                    String p = m.isGuest() ? m.getGuestProficiency()
                            : (m.getPlayer() != null ? m.getPlayer().getProficiency() : null);
                    return -(m.getEloRating() + profLevel.getOrDefault(p, 1) * 50.0);
                })).collect(Collectors.toList());
        List<List<TournamentMember>> teams = new ArrayList<>();
        for (int i = 0; i < numTeams; i++) teams.add(new ArrayList<>());
        for (int i = 0; i < sorted.size(); i++) {
            int round = i / numTeams;
            int idx = (round % 2 == 0) ? (i % numTeams) : (numTeams - 1 - i % numTeams);
            teams.get(idx).add(sorted.get(i));
        }
        return teams;
    }

    public List<List<TournamentMember>> buildCustomTeams(List<TournamentMember> members,
                                                         int numTeams, int perTeam) { return balanceTeams(members, numTeams); }
}
