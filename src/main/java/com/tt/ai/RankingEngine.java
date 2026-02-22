package com.tt.ai;

import com.tt.model.TournamentMember;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RankingEngine {

    /**
     * Ranks members purely by wins/matches ratio (not win%), then uses AI-style
     * proficiency as tiebreaker. Members with more matches played at equal ratio
     * rank higher (more consistent). Zero-match members rank last.
     */
    public List<TournamentMember> recomputeRanksWithProficiency(List<TournamentMember> members) {
        Map<String,Integer> profLevel = Map.of(
                "Beginner",0,"Intermediate",1,"Advanced",2,"Expert",3,"Professional",4);

        // Sort: primary = wins/matches ratio desc, secondary = proficiency desc,
        //       tertiary = total matches played desc (activity), quaternary = name asc
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

        for (int i = 0; i < sorted.size(); i++)
            sorted.get(i).setCurrentRank(i + 1);
        return sorted;
    }

    public double computeDayScore(int won, int played, int scored, int conceded) {
        if (played == 0) return 0.0;
        double ratio = (double) won / played;
        double ptDiff = played == 0 ? 0 : (double)(scored - conceded) / (played * 11.0);
        return 0.70 * ratio + 0.30 * ((ptDiff + 1) / 2.0);
    }

    public double predictWinProb(int rank1, int rank2, int total) {
        double s1 = total - rank1 + 1, s2 = total - rank2 + 1;
        return s1 / (s1 + s2);
    }

    /** Snake-draft team balancing using composite score (rank + proficiency) */
    public List<List<TournamentMember>> balanceTeams(List<TournamentMember> members, int numTeams) {
        Map<String,Integer> profLevel = Map.of(
                "Beginner",0,"Intermediate",1,"Advanced",2,"Expert",3,"Professional",4);
        List<TournamentMember> sorted = members.stream()
                .sorted(Comparator.comparingDouble(m -> {
                    int rank = m.getCurrentRank() == 0 ? members.size() : m.getCurrentRank();
                    String p = m.isGuest() ? m.getGuestProficiency()
                            : (m.getPlayer() != null ? m.getPlayer().getProficiency() : null);
                    int prof = profLevel.getOrDefault(p, 1);
                    return rank - prof * 5.0; // lower = better
                }))
                .collect(Collectors.toList());
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
                                                         int numTeams, int perTeam) {
        return balanceTeams(members, numTeams);
    }
}
