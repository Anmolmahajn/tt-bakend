package com.tt.service;

import com.tt.ai.RankingEngine;
import com.tt.dto.DTOs;
import com.tt.model.*;
import com.tt.notification.PushNotificationService;
import com.tt.repository.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class TournamentService {

    private static final Logger log = Logger.getLogger(TournamentService.class.getName());

    private final TournamentRepository tournamentRepo;
    private final TournamentMemberRepository memberRepo;
    private final TournamentDayRepository dayRepo;
    private final TeamRepository teamRepo;
    private final MatchRepository matchRepo;
    private final PlayerRepository playerRepo;
    private final ChatMessageRepository chatRepo;
    private final DailyRankingSnapshotRepository snapshotRepo;
    private final PasswordEncoder passwordEncoder;
    private final RankingEngine rankingEngine;
    private final PushNotificationService pushService;
    private final SimpMessagingTemplate ws;

    public TournamentService(TournamentRepository tournamentRepo, TournamentMemberRepository memberRepo,
                             TournamentDayRepository dayRepo, TeamRepository teamRepo, MatchRepository matchRepo,
                             PlayerRepository playerRepo, ChatMessageRepository chatRepo,
                             DailyRankingSnapshotRepository snapshotRepo, PasswordEncoder passwordEncoder,
                             RankingEngine rankingEngine, PushNotificationService pushService, SimpMessagingTemplate ws) {
        this.tournamentRepo = tournamentRepo; this.memberRepo = memberRepo; this.dayRepo = dayRepo;
        this.teamRepo = teamRepo; this.matchRepo = matchRepo; this.playerRepo = playerRepo;
        this.chatRepo = chatRepo; this.snapshotRepo = snapshotRepo; this.passwordEncoder = passwordEncoder;
        this.rankingEngine = rankingEngine; this.pushService = pushService; this.ws = ws;
    }

    // ── CREATE / JOIN ─────────────────────────────────────────────────────────

    @Transactional
    public Tournament createTournament(Player creator, String name, String password) {
        // Case-insensitive name check to prevent duplicate-name confusion
        if (tournamentRepo.existsByNameIgnoreCase(name.trim()))
            throw new RuntimeException("Tournament name already taken");
        Tournament t = new Tournament();
        t.setName(name.trim());
        t.setPassword(passwordEncoder.encode(password != null ? password : ""));
        t.setCreatedBy(creator);
        t.getAdmins().add(creator);
        // Generate unique 8-char invite code
        String inviteCode;
        do {
            inviteCode = java.util.UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase();
        } while (tournamentRepo.findByInviteCode(inviteCode).isPresent());
        t.setInviteCode(inviteCode);
        t = tournamentRepo.save(t);
        TournamentMember m = new TournamentMember();
        m.setTournament(t); m.setPlayer(creator); m.setGuest(false);
        memberRepo.save(m);
        creator.setTournamentsPlayed(creator.getTournamentsPlayed() + 1);
        playerRepo.save(creator);
        return t;
    }

    @Transactional
    public Long joinTournamentGetId(Player player, String name, String password) {
        Tournament t = tournamentRepo.findByNameIgnoreCase(name.trim())
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        String hash = t.getPassword();
        if (hash != null && !hash.isBlank()) {
            if (password == null || !passwordEncoder.matches(password, hash))
                throw new RuntimeException("Wrong password");
        }

        boolean alreadyMember = memberRepo.existsByTournamentIdAndPlayerId(t.getId(), player.getId());
        if (alreadyMember) throw new RuntimeException("Already in this tournament");

        TournamentMember m = new TournamentMember();
        m.setTournament(t); m.setPlayer(player); m.setGuest(false);
        memberRepo.save(m);
        player.setTournamentsPlayed(player.getTournamentsPlayed() + 1);
        playerRepo.save(player);
        postSystemMessage(t, player.getDisplayName() + " joined the group!", ChatMessage.MessageType.SYSTEM);
        broadcastTournament(t.getId());
        broadcastChat(t.getId());
        return t.getId(); // return ID directly — safe across transaction boundary
    }

    @Transactional
    public TournamentMember joinTournament(Player player, String name, String password) {
        Tournament t = tournamentRepo.findByNameIgnoreCase(name.trim())
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        String hash = t.getPassword();
        if (hash != null && !hash.isBlank()) {
            if (password == null || !passwordEncoder.matches(password, hash))
                throw new RuntimeException("Wrong password");
        }

        boolean alreadyMember = memberRepo.existsByTournamentIdAndPlayerId(t.getId(), player.getId());
        if (alreadyMember) throw new RuntimeException("Already in this tournament");

        TournamentMember m = new TournamentMember();
        m.setTournament(t); m.setPlayer(player); m.setGuest(false);
        memberRepo.save(m);
        player.setTournamentsPlayed(player.getTournamentsPlayed() + 1);
        playerRepo.save(player);
        postSystemMessage(t, player.getDisplayName() + " joined the group!", ChatMessage.MessageType.SYSTEM);
        broadcastTournament(t.getId());
        broadcastChat(t.getId());
        return m;
    }

    @Transactional
    public TournamentMember addGuest(Long tournamentId, Player admin, String guestName, String proficiency) {
        Tournament t = getTournament(tournamentId);
        assertAdmin(t, admin);
        if (guestName == null || guestName.isBlank()) throw new RuntimeException("Guest name required");
        if (memberRepo.existsByTournamentAndGuestName(t, guestName.trim()))
            throw new RuntimeException("Guest name already exists");
        TournamentMember m = new TournamentMember();
        m.setTournament(t); m.setGuestName(guestName.trim()); m.setGuest(true);
        if (proficiency != null && !proficiency.isBlank()) m.setGuestProficiency(proficiency);
        m = memberRepo.save(m);
        postSystemMessage(t, "Guest \"" + guestName + "\" added", ChatMessage.MessageType.SYSTEM);
        broadcastTournament(tournamentId);
        return m;
    }

    // ── REMOVE MEMBER ─────────────────────────────────────────────────────────
    @Transactional
    public void removeMember(Long tournamentId, Player admin, Long memberId) {
        Tournament t = getTournament(tournamentId);
        assertAdmin(t, admin);
        TournamentMember m = memberRepo.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        if (!m.getTournament().getId().equals(tournamentId))
            throw new RuntimeException("Member not in this tournament");
        String name = m.getDisplayName();

        List<TournamentDay> days = dayRepo.findByTournamentOrderByDayNumberAsc(t);

        for (TournamentDay day : days) {
            day.getPresentMembers().removeIf(pm -> pm.getId().equals(memberId));
            dayRepo.save(day);
            dayRepo.flush();

            List<Match> memberMatches = matchRepo.findByDayOrderByMatchNumberAsc(day)
                    .stream()
                    .filter(match ->
                            (match.getMember1() != null && match.getMember1().getId().equals(memberId)) ||
                                    (match.getMember2() != null && match.getMember2().getId().equals(memberId)))
                    .collect(Collectors.toList());

            for (Match match : memberMatches) {
                if (match.getWinner() != null && match.getWinner().getId().equals(memberId)) {
                    match.setWinner(null);
                    matchRepo.save(match);
                }
            }
            matchRepo.flush();
            matchRepo.deleteAll(memberMatches);
            matchRepo.flush();

            List<Team> teamsInDay = teamRepo.findByDayOrderByMatchesWonDesc(day);
            for (Team team : teamsInDay) {
                boolean changed = team.getMembers().removeIf(tm -> tm.getId().equals(memberId));
                if (changed) teamRepo.save(team);
            }
            teamRepo.flush();
        }

        matchRepo.nullifyWinnerIfMember(memberId);
        matchRepo.flush();
        snapshotRepo.deleteByMemberId(memberId);
        snapshotRepo.flush();
        memberRepo.delete(m);
        memberRepo.flush();

        postSystemMessage(t, name + " was removed from the group", ChatMessage.MessageType.SYSTEM);
        broadcastTournament(tournamentId);
    }

    // ── RENAME / CHANGE PASSWORD ──────────────────────────────────────────────

    @Transactional
    public void renameTournament(Long tournamentId, Player admin, String newName) {
        Tournament t = getTournament(tournamentId);
        if (!t.getCreatedBy().getId().equals(admin.getId()))
            throw new RuntimeException("Only creator can rename");
        if (tournamentRepo.existsByNameIgnoreCase(newName.trim()))
            throw new RuntimeException("Name already taken");
        t.setName(newName.trim());
        tournamentRepo.save(t);
        broadcastTournament(tournamentId);
    }

    @Transactional
    public void changeTournamentPassword(Long tournamentId, Player admin, String newPassword) {
        Tournament t = getTournament(tournamentId);
        if (!t.getCreatedBy().getId().equals(admin.getId()))
            throw new RuntimeException("Only creator can change password");
        t.setPassword(passwordEncoder.encode(newPassword != null ? newPassword : ""));
        tournamentRepo.save(t);
    }

    // ── UPDATE PROFICIENCY ────────────────────────────────────────────────────

    @Transactional
    public void updateMemberProficiency(Long tournamentId, Player admin, Long memberId, String proficiency) {
        Tournament t = getTournament(tournamentId);
        assertAdmin(t, admin);
        TournamentMember m = memberRepo.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        if (m.isGuest()) {
            m.setGuestProficiency(proficiency);
            memberRepo.save(m);
        } else if (m.getPlayer() != null) {
            m.getPlayer().setProficiency(proficiency);
            playerRepo.save(m.getPlayer());
        }
        broadcastTournament(tournamentId);
    }

    // ── DELETE TOURNAMENT ─────────────────────────────────────────────────────

    @Transactional
    public void deleteTournament(Long tournamentId, Player admin) {
        Tournament t = getTournament(tournamentId);
        if (!t.getCreatedBy().getId().equals(admin.getId()))
            throw new RuntimeException("Only the creator can delete this tournament");

        List<TournamentMember> members = memberRepo.findByTournamentOrderByCurrentRankAsc(t);
        List<TournamentDay> days = dayRepo.findByTournamentOrderByDayNumberAsc(t);

        for (TournamentDay day : days) {
            day.getPresentMembers().clear();
            dayRepo.save(day);
            dayRepo.flush();
            List<Match> matches = matchRepo.findByDayOrderByMatchNumberAsc(day);
            matchRepo.deleteAll(matches);
            matchRepo.flush();
            teamRepo.deleteAll(teamRepo.findByDayOrderByMatchesWonDesc(day));
            teamRepo.flush();
            snapshotRepo.deleteByDay(day);
        }
        dayRepo.deleteAll(days);
        chatRepo.deleteByTournament(t);
        for (TournamentMember mem : members) snapshotRepo.deleteByMemberId(mem.getId());
        memberRepo.deleteAll(members);
        tournamentRepo.delete(t);
    }

    // ── CUSTOM RANKINGS ───────────────────────────────────────────────────────

    @Transactional
    public void setCustomRankings(Long tournamentId, Player admin, List<DTOs.CustomRankUpdate> updates) {
        Tournament t = getTournament(tournamentId);
        assertAdmin(t, admin);
        for (DTOs.CustomRankUpdate u : updates) {
            memberRepo.findById(u.getMemberId()).ifPresent(m -> {
                if (m.getTournament().getId().equals(tournamentId)) {
                    m.setCurrentRank(u.getRank());
                    memberRepo.save(m);
                }
            });
        }
        postSystemMessage(t, "Admin updated player rankings", ChatMessage.MessageType.SYSTEM);
        broadcastTournament(tournamentId);
    }

    // ── ADMIN MANAGEMENT ──────────────────────────────────────────────────────

    @Transactional
    public void promoteAdmin(Long tournamentId, Player requester, Long targetPlayerId) {
        Tournament t = getTournament(tournamentId);
        assertAdmin(t, requester);
        Player target = playerRepo.findById(targetPlayerId)
                .orElseThrow(() -> new RuntimeException("Player not found"));
        if (!memberRepo.existsByTournamentIdAndPlayerId(t.getId(), target.getId()))
            throw new RuntimeException("Player is not a member");
        if (t.getAdmins().stream().noneMatch(a -> a.getId().equals(target.getId()))) {
            t.getAdmins().add(target);
            tournamentRepo.save(t);
            postSystemMessage(t, target.getDisplayName() + " is now an admin", ChatMessage.MessageType.SYSTEM);
        }
    }

    @Transactional
    public void removeAdmin(Long tournamentId, Player requester, Long targetPlayerId) {
        Tournament t = getTournament(tournamentId);
        if (!t.getCreatedBy().getId().equals(requester.getId()))
            throw new RuntimeException("Only creator can remove admins");
        if (targetPlayerId.equals(t.getCreatedBy().getId()))
            throw new RuntimeException("Cannot remove creator from admins");
        Player target = playerRepo.findById(targetPlayerId)
                .orElseThrow(() -> new RuntimeException("Player not found"));
        t.getAdmins().removeIf(a -> a.getId().equals(target.getId()));
        tournamentRepo.save(t);
    }

    // ── START DAY ─────────────────────────────────────────────────────────────

    @Transactional
    public TournamentDay startDay(Long tournamentId, Player admin, DTOs.StartDayRequest req) {
        Tournament t = getTournament(tournamentId);
        assertAdmin(t, admin);

        dayRepo.findFirstByTournamentAndStatusOrderByDayNumberDesc(t, TournamentDay.DayStatus.IN_PROGRESS)
                .ifPresent(existing -> {
                    List<Match> leftover = matchRepo.findByDayOrderByMatchNumberAsc(existing).stream()
                            .filter(m -> m.getStatus() != Match.MatchStatus.COMPLETED)
                            .collect(Collectors.toList());
                    leftover.forEach(m -> { m.setWinner(null); matchRepo.save(m); });
                    matchRepo.flush();
                    matchRepo.deleteAll(leftover);
                    matchRepo.flush();
                    teamRepo.findByDayOrderByMatchesWonDesc(existing).forEach(team -> {
                        team.getMembers().clear(); teamRepo.save(team);
                    });
                    teamRepo.flush();
                    teamRepo.deleteAll(teamRepo.findByDayOrderByMatchesWonDesc(existing));
                    teamRepo.flush();
                    existing.getPresentMembers().clear();
                    existing.setStatus(TournamentDay.DayStatus.ENDED);
                    existing.setEndedAt(LocalDateTime.now());
                    dayRepo.save(existing);
                    dayRepo.flush();
                });

        List<TournamentMember> present = req.getPresentMemberIds().stream()
                .map(id -> memberRepo.findById(id)
                        .orElseThrow(() -> new RuntimeException("Member not found: " + id)))
                .collect(Collectors.toList());
        if (present.size() < 2) throw new RuntimeException("Need at least 2 present players");
        Integer maxDay = dayRepo.findMaxDayNumber(t);
        int dayNumber = (maxDay == null ? 0 : maxDay) + 1;
        TournamentDay.MatchFormat fmt = TournamentDay.MatchFormat.valueOf(req.getMatchFormat());
        TournamentDay day = new TournamentDay();
        day.setTournament(t); day.setDayNumber(dayNumber);
        day.setStatus(TournamentDay.DayStatus.IN_PROGRESS);
        day.setMatchFormat(fmt); day.setNumberOfTeams(req.getNumberOfTeams());
        day.setPlayersPerTeam(req.getPlayersPerTeam());
        day.setPresentMembers(new ArrayList<>(present));
        day.setStartedAt(LocalDateTime.now());
        day = dayRepo.save(day);
        present.forEach(m -> { m.setDaysPlayed(m.getDaysPlayed() + 1); memberRepo.save(m); });
        generateDayTeamsAndMatches(t, day, present, fmt, req.getNumberOfTeams(), req.getPlayersPerTeam());
        pushService.notifyDayStarted(t, dayNumber);
        postSystemMessage(t, "Day " + dayNumber + " started! " + present.size() + " players present.",
                ChatMessage.MessageType.DAY_STARTED);
        broadcastTournament(tournamentId);
        return day;
    }

    // ── ADD PLAYER MID-DAY ────────────────────────────────────────────────────

    @Transactional
    public TournamentDay addPlayerMidDay(Long tournamentId, Player admin, Long newMemberId) {
        Tournament t = getTournament(tournamentId);
        assertAdmin(t, admin);
        TournamentDay day = dayRepo.findFirstByTournamentAndStatusOrderByDayNumberDesc(
                        t, TournamentDay.DayStatus.IN_PROGRESS)
                .orElseThrow(() -> new RuntimeException("No active day"));

        TournamentMember newMember = memberRepo.findById(newMemberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        if (!newMember.getTournament().getId().equals(tournamentId))
            throw new RuntimeException("Member not in this tournament");

        boolean alreadyPresent = day.getPresentMembers().stream()
                .anyMatch(m -> m.getId().equals(newMemberId));
        if (alreadyPresent) throw new RuntimeException("Player already in today's session");

        List<Match> allMatches = matchRepo.findByDayOrderByMatchNumberAsc(day);
        int completedCount = (int) allMatches.stream()
                .filter(m -> m.getStatus() == Match.MatchStatus.COMPLETED).count();

        List<Match> toDelete = allMatches.stream()
                .filter(m -> m.getStatus() != Match.MatchStatus.COMPLETED)
                .collect(Collectors.toList());
        for (Match match : toDelete) { match.setWinner(null); matchRepo.save(match); }
        matchRepo.flush();
        matchRepo.deleteAll(toDelete);
        matchRepo.flush();

        List<Team> teams = teamRepo.findByDayOrderByMatchesWonDesc(day);
        for (Team team : teams) { team.getMembers().clear(); teamRepo.save(team); }
        teamRepo.flush();
        teamRepo.deleteAll(teams);
        teamRepo.flush();

        List<TournamentMember> newPresent = new ArrayList<>(day.getPresentMembers());
        newPresent.add(newMember);
        day.setPresentMembers(newPresent);
        dayRepo.save(day);

        newMember.setDaysPlayed(newMember.getDaysPlayed() + 1);
        memberRepo.save(newMember);

        generateDayTeamsAndMatchesFrom(t, day, newPresent, day.getMatchFormat(),
                day.getNumberOfTeams(), day.getPlayersPerTeam(), completedCount + 1);

        postSystemMessage(t, newMember.getDisplayName() + " joined mid-day! Schedule regenerated.",
                ChatMessage.MessageType.SYSTEM);
        broadcastTournament(tournamentId);
        return day;
    }

    // ── REMOVE PLAYER MID-DAY ─────────────────────────────────────────────────

    @Transactional
    public TournamentDay removePlayerMidDay(Long tournamentId, Player admin, Long memberId) {
        Tournament t = getTournament(tournamentId);
        assertAdmin(t, admin);
        TournamentDay day = dayRepo.findFirstByTournamentAndStatusOrderByDayNumberDesc(
                        t, TournamentDay.DayStatus.IN_PROGRESS)
                .orElseThrow(() -> new RuntimeException("No active day"));

        TournamentMember leavingMember = memberRepo.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        boolean isPresent = day.getPresentMembers().stream().anyMatch(m -> m.getId().equals(memberId));
        if (!isPresent) throw new RuntimeException("Player is not in today's session");

        List<Match> allMatches = matchRepo.findByDayOrderByMatchNumberAsc(day);
        int completedCount = (int) allMatches.stream()
                .filter(m -> m.getStatus() == Match.MatchStatus.COMPLETED).count();

        List<Match> toDelete = allMatches.stream()
                .filter(m -> m.getStatus() != Match.MatchStatus.COMPLETED)
                .collect(Collectors.toList());
        for (Match match : toDelete) { match.setWinner(null); matchRepo.save(match); }
        matchRepo.flush();
        matchRepo.deleteAll(toDelete);
        matchRepo.flush();

        List<Team> teams = teamRepo.findByDayOrderByMatchesWonDesc(day);
        for (Team team : teams) { team.getMembers().clear(); teamRepo.save(team); }
        teamRepo.flush();
        teamRepo.deleteAll(teams);
        teamRepo.flush();

        List<TournamentMember> newPresent = day.getPresentMembers().stream()
                .filter(m -> !m.getId().equals(memberId))
                .collect(Collectors.toList());
        day.setPresentMembers(newPresent);
        dayRepo.save(day);

        leavingMember.setDaysPlayed(Math.max(0, leavingMember.getDaysPlayed() - 1));
        memberRepo.save(leavingMember);

        if (newPresent.size() >= 2) {
            generateDayTeamsAndMatchesFrom(t, day, newPresent, day.getMatchFormat(),
                    day.getNumberOfTeams(), day.getPlayersPerTeam(), completedCount + 1);
        }

        postSystemMessage(t, leavingMember.getDisplayName() + " left mid-day. Schedule regenerated.",
                ChatMessage.MessageType.SYSTEM);
        broadcastTournament(tournamentId);
        return day;
    }

    // ── RESTART MATCHMAKING ───────────────────────────────────────────────────

    @Transactional
    public TournamentDay restartMatchmaking(Long tournamentId, Player admin, DTOs.StartDayRequest req) {
        Tournament t = getTournament(tournamentId);
        assertAdmin(t, admin);
        TournamentDay day = dayRepo.findFirstByTournamentAndStatusOrderByDayNumberDesc(
                        t, TournamentDay.DayStatus.IN_PROGRESS)
                .orElseThrow(() -> new RuntimeException("No active day"));

        List<Match> allMatches = matchRepo.findByDayOrderByMatchNumberAsc(day);
        int completedCount = (int) allMatches.stream()
                .filter(m -> m.getStatus() == Match.MatchStatus.COMPLETED).count();

        List<Match> toDelete = allMatches.stream()
                .filter(m -> m.getStatus() != Match.MatchStatus.COMPLETED)
                .collect(Collectors.toList());
        for (Match match : toDelete) { match.setWinner(null); matchRepo.save(match); }
        matchRepo.flush();
        matchRepo.deleteAll(toDelete);
        matchRepo.flush();

        List<Team> teams = teamRepo.findByDayOrderByMatchesWonDesc(day);
        for (Team team : teams) { team.getMembers().clear(); teamRepo.save(team); }
        teamRepo.flush();
        teamRepo.deleteAll(teams);
        teamRepo.flush();

        List<TournamentMember> newPresent = req.getPresentMemberIds().stream()
                .map(id -> memberRepo.findById(id)
                        .orElseThrow(() -> new RuntimeException("Member not found: " + id)))
                .collect(Collectors.toList());
        day.setPresentMembers(newPresent);

        TournamentDay.MatchFormat fmt = TournamentDay.MatchFormat.valueOf(req.getMatchFormat());
        day.setMatchFormat(fmt); day.setNumberOfTeams(req.getNumberOfTeams());
        day.setPlayersPerTeam(req.getPlayersPerTeam());
        dayRepo.save(day);

        generateDayTeamsAndMatchesFrom(t, day, newPresent, fmt,
                req.getNumberOfTeams(), req.getPlayersPerTeam(), completedCount + 1);

        postSystemMessage(t, "Matchmaking restarted for Day " + day.getDayNumber(),
                ChatMessage.MessageType.SYSTEM);
        broadcastTournament(tournamentId);
        return day;
    }

    // ── MATCH GENERATION ──────────────────────────────────────────────────────

    private void generateDayTeamsAndMatches(Tournament t, TournamentDay day, List<TournamentMember> present,
                                            TournamentDay.MatchFormat fmt, int numTeams, int perTeam) {
        generateDayTeamsAndMatchesFrom(t, day, present, fmt, numTeams, perTeam, 1);
    }

    private void generateDayTeamsAndMatchesFrom(Tournament t, TournamentDay day, List<TournamentMember> present,
                                                TournamentDay.MatchFormat fmt, int numTeams, int perTeam, int startMatchNum) {
        if (fmt == TournamentDay.MatchFormat.FREE_FOR_ALL) {
            generateScheduledFreeForAll(t, day, present, startMatchNum);
            return;
        }

        if (fmt == TournamentDay.MatchFormat.TEAM_2V2 || perTeam == 2) {
            generateBalanced2v2(t, day, present, startMatchNum);
            return;
        }

        List<List<TournamentMember>> teamGroups = (fmt == TournamentDay.MatchFormat.CUSTOM_TEAMS)
                ? rankingEngine.buildCustomTeams(present, numTeams, perTeam)
                : rankingEngine.balanceTeams(present, numTeams);
        String[] names = {"Team Alpha","Team Beta","Team Gamma","Team Delta",
                "Team Epsilon","Team Zeta","Team Eta","Team Theta"};
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < teamGroups.size(); i++) {
            List<TournamentMember> grp = teamGroups.get(i);
            if (grp.isEmpty()) continue;
            double avg = grp.stream().mapToInt(m -> m.getCurrentRank() == 0 ? 999 : m.getCurrentRank())
                    .average().orElse(0);
            Team team = new Team();
            team.setName(names[Math.min(i, names.length - 1)]);
            team.setTournament(t); team.setDay(day); team.setMembers(grp); team.setAvgRank(avg);
            teams.add(teamRepo.save(team));
        }
        int matchNum = startMatchNum;
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++)
            for (int j = i + 1; j < teams.size(); j++)
                for (TournamentMember m1 : teams.get(i).getMembers())
                    for (TournamentMember m2 : teams.get(j).getMembers())
                        matches.add(buildMatch(t, day, m1, m2, teams.get(i), teams.get(j), matchNum++, present.size()));
        if (!matches.isEmpty()) activateFirst(matches.get(0));
    }

    private void generateBalanced2v2(Tournament t, TournamentDay day, List<TournamentMember> present, int startMatchNum) {
        int n = present.size();
        if (n < 4) {
            generateScheduledFreeForAll(t, day, present, startMatchNum);
            return;
        }

        Map<String,Integer> profLevel = Map.of(
                "Beginner",0,"Intermediate",1,"Advanced",2,"Expert",3,"Professional",4);
        List<TournamentMember> sorted = new ArrayList<>(present);
        sorted.sort(Comparator.comparingDouble(m -> {
            int rank = m.getCurrentRank() == 0 ? n : m.getCurrentRank();
            String p = m.isGuest() ? m.getGuestProficiency()
                    : (m.getPlayer() != null ? m.getPlayer().getProficiency() : null);
            int prof = profLevel.getOrDefault(p, 1);
            return rank - prof * 3.0;
        }));

        int numPairs = n / 2;
        List<TournamentMember[]> pairs = new ArrayList<>();
        for (int i = 0; i < numPairs; i++) {
            TournamentMember stronger = sorted.get(i);
            TournamentMember weaker = sorted.get(n - 1 - i);
            if (stronger.getId().equals(weaker.getId())) {
                if (i > 0) pairs.get(i - 1)[1] = weaker;
                break;
            }
            pairs.add(new TournamentMember[]{stronger, weaker});
        }
        if (n % 2 != 0 && sorted.size() > numPairs * 2) {
            TournamentMember solo = sorted.get(numPairs);
            pairs.add(new TournamentMember[]{solo, solo});
        }

        String[] teamNames = {"Team Alpha","Team Beta","Team Gamma","Team Delta",
                "Team Epsilon","Team Zeta","Team Eta","Team Theta"};
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            TournamentMember[] pair = pairs.get(i);
            List<TournamentMember> members = new ArrayList<>();
            members.add(pair[0]);
            if (pair[1] != null && !pair[1].getId().equals(pair[0].getId())) members.add(pair[1]);
            Team team = new Team();
            team.setName(teamNames[Math.min(i, teamNames.length - 1)]);
            team.setTournament(t); team.setDay(day); team.setMembers(members);
            double avg = members.stream().mapToInt(m -> m.getCurrentRank() == 0 ? n : m.getCurrentRank())
                    .average().orElse(0);
            team.setAvgRank(avg);
            teams.add(teamRepo.save(team));
        }

        int numT = teams.size();
        List<int[]> pairMatchups = new ArrayList<>();
        for (int i = 0; i < numT; i++)
            for (int j = i + 1; j < numT; j++)
                pairMatchups.add(new int[]{i, j});

        List<int[]> scheduled = new ArrayList<>();
        boolean[] used = new boolean[pairMatchups.size()];
        int[] waitSince = new int[numT];
        Arrays.fill(waitSince, -1);
        int slot = 0;
        while (scheduled.size() < pairMatchups.size()) {
            int bestIdx = -1; long bestWait = -1;
            for (int k = 0; k < pairMatchups.size(); k++) {
                if (used[k]) continue;
                int p1 = pairMatchups.get(k)[0], p2 = pairMatchups.get(k)[1];
                long w1 = waitSince[p1] == -1 ? slot + numT : slot - waitSince[p1];
                long w2 = waitSince[p2] == -1 ? slot + numT : slot - waitSince[p2];
                if (w1 + w2 > bestWait) { bestWait = w1 + w2; bestIdx = k; }
            }
            if (bestIdx < 0) break;
            scheduled.add(pairMatchups.get(bestIdx)); used[bestIdx] = true;
            waitSince[pairMatchups.get(bestIdx)[0]] = slot;
            waitSince[pairMatchups.get(bestIdx)[1]] = slot;
            slot++;
        }

        List<Match> matches = new ArrayList<>();
        int matchNum = startMatchNum;
        for (int[] sch : scheduled) {
            Team teamA = teams.get(sch[0]);
            Team teamB = teams.get(sch[1]);
            TournamentMember rep1 = teamA.getMembers().get(0);
            TournamentMember rep2 = teamB.getMembers().get(0);
            matches.add(buildMatch(t, day, rep1, rep2, teamA, teamB, matchNum++, present.size()));
        }
        if (!matches.isEmpty()) activateFirst(matches.get(0));
    }

    private void generateScheduledFreeForAll(Tournament t, TournamentDay day, List<TournamentMember> present, int startMatchNum) {
        int n = present.size();
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                pairs.add(new int[]{i, j});
        List<int[]> scheduled = new ArrayList<>();
        boolean[] used = new boolean[pairs.size()];
        int[] waitSince = new int[n];
        Arrays.fill(waitSince, -1);
        int slot = 0;
        while (scheduled.size() < pairs.size()) {
            int bestIdx = -1; long bestWait = -1;
            for (int k = 0; k < pairs.size(); k++) {
                if (used[k]) continue;
                int p1 = pairs.get(k)[0], p2 = pairs.get(k)[1];
                long w1 = waitSince[p1] == -1 ? slot + n : slot - waitSince[p1];
                long w2 = waitSince[p2] == -1 ? slot + n : slot - waitSince[p2];
                long combined = w1 + w2;
                if (combined > bestWait) { bestWait = combined; bestIdx = k; }
            }
            if (bestIdx < 0) break;
            scheduled.add(pairs.get(bestIdx)); used[bestIdx] = true;
            waitSince[pairs.get(bestIdx)[0]] = slot; waitSince[pairs.get(bestIdx)[1]] = slot;
            slot++;
        }
        List<Match> matches = new ArrayList<>();
        int matchNum = startMatchNum;
        for (int[] pair : scheduled)
            matches.add(buildMatch(t, day, present.get(pair[0]), present.get(pair[1]),
                    null, null, matchNum++, n));
        if (!matches.isEmpty()) activateFirst(matches.get(0));
    }

    private void activateFirst(Match m) {
        m.setStatus(Match.MatchStatus.IN_PROGRESS);
        m.setStartedAt(LocalDateTime.now());
        matchRepo.save(m);
    }

    private Match buildMatch(Tournament t, TournamentDay day, TournamentMember m1, TournamentMember m2,
                             Team team1, Team team2, int num, int total) {
        int r1 = m1.getCurrentRank() == 0 ? total : m1.getCurrentRank();
        int r2 = m2.getCurrentRank() == 0 ? total : m2.getCurrentRank();
        double profBoost = getProficiencyBoost(m1, m2);
        double prob1 = rankingEngine.predictWinProb(r1, r2, total);
        prob1 = Math.max(0.05, Math.min(0.95, prob1 + profBoost * 0.1));
        Match m = new Match();
        m.setTournament(t); m.setDay(day);
        m.setMember1(m1); m.setMember2(m2);
        m.setTeam1(team1); m.setTeam2(team2);
        m.setMatchNumber(num);
        m.setMember1WinProb(Math.round(prob1 * 1000.0) / 10.0);
        m.setMember2WinProb(Math.round((1 - prob1) * 1000.0) / 10.0);
        m.setPredictedWinner(prob1 >= 0.5 ? m1.getDisplayName() : m2.getDisplayName());
        return matchRepo.save(m);
    }

    private double getProficiencyBoost(TournamentMember m1, TournamentMember m2) {
        Map<String,Integer> lvl = Map.of("Beginner",0,"Intermediate",1,"Advanced",2,"Expert",3,"Professional",4);
        String p1 = m1.isGuest() ? m1.getGuestProficiency()
                : (m1.getPlayer() != null ? m1.getPlayer().getProficiency() : null);
        String p2 = m2.isGuest() ? m2.getGuestProficiency()
                : (m2.getPlayer() != null ? m2.getPlayer().getProficiency() : null);
        if (p1 == null || p2 == null) return 0;
        return (lvl.getOrDefault(p1, 1) - lvl.getOrDefault(p2, 1)) / 4.0;
    }

    // ── CHALLENGE MATCH ───────────────────────────────────────────────────────
    // Creates a match between two members regardless of whether a day is active.
    // If a day is IN_PROGRESS the match is appended to it.
    // Otherwise a persistent "Challenge" day (dayNumber = -1) is used as a bucket.

    @Transactional
    public DTOs.MatchResponse createChallengeMatch(Long tournamentId, Player requester,
                                                   Long member1Id, Long member2Id) {
        Tournament t = getTournament(tournamentId);
        boolean isMember = memberRepo.existsByTournamentIdAndPlayerId(tournamentId, requester.getId());
        if (!isMember && !isAdmin(t, requester))
            throw new RuntimeException("Not a member of this tournament");

        TournamentMember m1 = memberRepo.findById(member1Id)
                .orElseThrow(() -> new RuntimeException("Member not found: " + member1Id));
        TournamentMember m2 = memberRepo.findById(member2Id)
                .orElseThrow(() -> new RuntimeException("Member not found: " + member2Id));
        if (!m1.getTournament().getId().equals(tournamentId) || !m2.getTournament().getId().equals(tournamentId))
            throw new RuntimeException("Members must be in this tournament");

        // Use the active day if one exists; otherwise use/create the challenge-bucket day (dayNumber = -1)
        TournamentDay day = dayRepo
                .findFirstByTournamentAndStatusOrderByDayNumberDesc(t, TournamentDay.DayStatus.IN_PROGRESS)
                .orElseGet(() -> getOrCreateChallengeBucketDay(t));

        // Append after the last existing match in this day
        List<Match> existing = matchRepo.findByDayOrderByMatchNumberAsc(day);
        int nextNum = existing.stream().mapToInt(Match::getMatchNumber).max().orElse(0) + 1;
        int totalPlayers = day.getPresentMembers().isEmpty()
                ? (int) memberRepo.countByTournament(t) : day.getPresentMembers().size();

        Match match = buildMatch(t, day, m1, m2, null, null, nextNum, totalPlayers);
        match.setStatus(Match.MatchStatus.IN_PROGRESS);
        match.setStartedAt(LocalDateTime.now());
        match = matchRepo.save(match);

        // Add both players to day's present list if not already there (challenge bucket only)
        if (day.getDayNumber() == -1) {
            List<TournamentMember> present = new ArrayList<>(day.getPresentMembers());
            if (present.stream().noneMatch(p -> p.getId().equals(member1Id))) present.add(m1);
            if (present.stream().noneMatch(p -> p.getId().equals(member2Id))) present.add(m2);
            day.setPresentMembers(present);
            dayRepo.save(day);
        }

        postSystemMessage(t,
                "⚔️ Challenge Match: " + m1.getDisplayName() + " vs " + m2.getDisplayName() + " — Challenge accepted!",
                ChatMessage.MessageType.SYSTEM);
        try { pushService.notifyMatchResult(t,
                "⚔️ " + m1.getDisplayName() + " vs " + m2.getDisplayName() + " — challenge match is ON!"); }
        catch (Exception ignored) {}
        broadcastTournament(tournamentId);
        broadcastChat(tournamentId);

        DTOs.MatchResponse mr = new DTOs.MatchResponse();
        mr.id = match.getId(); mr.matchNumber = match.getMatchNumber();
        mr.member1Id = m1.getId(); mr.member2Id = m2.getId();
        mr.member1Name = m1.getDisplayName(); mr.member2Name = m2.getDisplayName();
        mr.status = match.getStatus().name();
        mr.member1WinProb = match.getMember1WinProb();
        mr.member2WinProb = match.getMember2WinProb();
        return mr;
    }

    /** Returns existing challenge-bucket day or creates one (dayNumber = -1, always IN_PROGRESS). */
    private TournamentDay getOrCreateChallengeBucketDay(Tournament t) {
        return dayRepo.findFirstByTournamentAndDayNumberOrderByIdDesc(t, -1)
                .orElseGet(() -> {
                    TournamentDay d = new TournamentDay();
                    d.setTournament(t);
                    d.setDayNumber(-1);
                    d.setStatus(TournamentDay.DayStatus.IN_PROGRESS);
                    d.setMatchFormat(TournamentDay.MatchFormat.FREE_FOR_ALL);
                    d.setStartedAt(LocalDateTime.now());
                    d.setPresentMembers(new ArrayList<>());
                    return dayRepo.save(d);
                });
    }

    // ── SUBMIT RESULT ─────────────────────────────────────────────────────────

    @Transactional
    public Match submitResult(Long matchId, Player submitter, int score1, int score2) {
        Match m = matchRepo.findById(matchId).orElseThrow(() -> new RuntimeException("Match not found"));
        Tournament t = m.getTournament();
        // Use ID-based check to avoid lazy loading issues
        boolean isMember = memberRepo.existsByTournamentIdAndPlayerId(t.getId(), submitter.getId());
        boolean isAdminPlayer = isAdmin(t, submitter);
        if (!isMember && !isAdminPlayer) throw new RuntimeException("Not a member of this tournament");
        if (m.getStatus() == Match.MatchStatus.COMPLETED) throw new RuntimeException("Match already completed");

        m.setMember1Score(score1); m.setMember2Score(score2);
        m.setStatus(Match.MatchStatus.COMPLETED); m.setCompletedAt(LocalDateTime.now());

        boolean team1wins = score1 >= score2;
        TournamentMember winner = team1wins ? m.getMember1() : m.getMember2();
        TournamentMember loser  = team1wins ? m.getMember2() : m.getMember1();
        m.setWinner(winner);
        int ws = team1wins ? score1 : score2, ls = team1wins ? score2 : score1;

        List<TournamentMember> winningMembers = new ArrayList<>();
        List<TournamentMember> losingMembers = new ArrayList<>();
        boolean is2v2 = m.getTeam1() != null && m.getTeam2() != null
                && m.getTeam1().getMembers().size() > 1;

        if (is2v2) {
            Team winTeam = team1wins ? m.getTeam1() : m.getTeam2();
            Team loseTeam = team1wins ? m.getTeam2() : m.getTeam1();
            winningMembers.addAll(winTeam.getMembers());
            losingMembers.addAll(loseTeam.getMembers());
        } else {
            winningMembers.add(winner);
            losingMembers.add(loser);
        }

        for (TournamentMember wm : winningMembers) {
            wm.setTotalMatchesWon(wm.getTotalMatchesWon() + 1);
            wm.setTotalMatchesPlayed(wm.getTotalMatchesPlayed() + 1);
            wm.setTotalPointsScored(wm.getTotalPointsScored() + ws);
            wm.setTotalPointsConceded(wm.getTotalPointsConceded() + ls);
            // Update Elo + win streak (only for 1v1, not 2v2 teams)
            if (!is2v2) {
                boolean milestone = rankingEngine.updateWinStreak(wm, true);
                if (milestone && !wm.isGuest() && wm.getPlayer() != null) {
                    try { pushService.notifyMatchResult(t,
                            "🔥 " + wm.getDisplayName() + " is on a " + wm.getCurrentWinStreak() + "-match win streak!"); }
                    catch (Exception ignored) {}
                }
            }
            memberRepo.save(wm);
            if (!wm.isGuest() && wm.getPlayer() != null) {
                Player p = wm.getPlayer();
                p.setTotalMatchesWon(p.getTotalMatchesWon() + 1);
                p.setTotalMatchesPlayed(p.getTotalMatchesPlayed() + 1);
                playerRepo.save(p);
            }
        }

        for (TournamentMember lm : losingMembers) {
            lm.setTotalMatchesLost(lm.getTotalMatchesLost() + 1);
            lm.setTotalMatchesPlayed(lm.getTotalMatchesPlayed() + 1);
            lm.setTotalPointsScored(lm.getTotalPointsScored() + ls);
            lm.setTotalPointsConceded(lm.getTotalPointsConceded() + ws);
            if (!is2v2) rankingEngine.updateWinStreak(lm, false);
            memberRepo.save(lm);
            if (!lm.isGuest() && lm.getPlayer() != null) {
                Player p = lm.getPlayer();
                p.setTotalMatchesLost(p.getTotalMatchesLost() + 1);
                p.setTotalMatchesPlayed(p.getTotalMatchesPlayed() + 1);
                playerRepo.save(p);
            }
        }

        // Elo update (1v1 only — use first member of each side for 2v2)
        TournamentMember eloWinner = winningMembers.isEmpty() ? null : winningMembers.get(0);
        TournamentMember eloLoser  = losingMembers.isEmpty()  ? null : losingMembers.get(0);
        if (eloWinner != null && eloLoser != null) {
            rankingEngine.updateElo(eloWinner, eloLoser);
            memberRepo.save(eloWinner);
            memberRepo.save(eloLoser);
        }

        if (m.getTeam1() != null) {
            Team wt = team1wins ? m.getTeam1() : m.getTeam2();
            Team lt = team1wins ? m.getTeam2() : m.getTeam1();
            wt.setMatchesWon(wt.getMatchesWon() + 1);
            lt.setMatchesLost(lt.getMatchesLost() + 1);
            teamRepo.save(wt); teamRepo.save(lt);
        }

        Match saved = matchRepo.save(m);

        String side1Name = is2v2
                ? m.getTeam1().getMembers().stream().map(TournamentMember::getDisplayName).collect(Collectors.joining(" & "))
                : m.getMember1().getDisplayName();
        String side2Name = is2v2
                ? m.getTeam2().getMembers().stream().map(TournamentMember::getDisplayName).collect(Collectors.joining(" & "))
                : m.getMember2().getDisplayName();
        String winnerName = is2v2
                ? (team1wins ? side1Name : side2Name)
                : winner.getDisplayName();

        postSystemMessage(t, String.format("Match #%d: %s %d-%d %s — %s wins!",
                        m.getMatchNumber(), side1Name, score1, score2, side2Name, winnerName),
                ChatMessage.MessageType.MATCH_RESULT);
        pushService.notifyMatchResult(t, String.format("%s %d-%d %s — %s wins!",
                side1Name, score1, score2, side2Name, winnerName));
        activateNextMatch(m.getDay(), m.getMatchNumber());
        broadcastDay(t.getId(), m.getDay().getId());
        broadcastChat(t.getId());
        return saved;
    }

    private void activateNextMatch(TournamentDay day, int currentNum) {
        matchRepo.findByDayOrderByMatchNumberAsc(day).stream()
                .filter(mx -> mx.getStatus() == Match.MatchStatus.SCHEDULED
                        && mx.getMatchNumber() == currentNum + 1)
                .findFirst().ifPresent(next -> {
                    next.setStatus(Match.MatchStatus.IN_PROGRESS);
                    next.setStartedAt(LocalDateTime.now());
                    matchRepo.save(next);
                });
    }

    // ── END DAY ───────────────────────────────────────────────────────────────

    @Transactional
    public List<DTOs.DayRankEntry> endDay(Long tournamentId, Player admin) {
        Tournament t = getTournament(tournamentId);
        assertAdmin(t, admin);
        TournamentDay day = dayRepo.findFirstByTournamentAndStatusOrderByDayNumberDesc(
                        t, TournamentDay.DayStatus.IN_PROGRESS)
                .orElseThrow(() -> new RuntimeException("No day in progress"));

        matchRepo.findByDayOrderByMatchNumberAsc(day).stream()
                .filter(m -> m.getStatus() != Match.MatchStatus.COMPLETED)
                .forEach(m -> { m.setStatus(Match.MatchStatus.COMPLETED);
                    m.setCompletedAt(LocalDateTime.now()); matchRepo.save(m); });

        day.setStatus(TournamentDay.DayStatus.ENDED);
        day.setEndedAt(LocalDateTime.now());
        if (day.getStartedAt() != null)
            day.setTimerSeconds(ChronoUnit.SECONDS.between(day.getStartedAt(), day.getEndedAt()));
        dayRepo.save(day);

        List<Match> dayMatches = matchRepo.findByDayOrderByMatchNumberAsc(day);
        Map<Long, int[]> stats = new HashMap<>();
        day.getPresentMembers().forEach(mem -> stats.put(mem.getId(), new int[]{0, 0, 0, 0}));
        for (Match m : dayMatches) {
            if (m.getStatus() != Match.MatchStatus.COMPLETED || m.getWinner() == null) continue;
            if (m.getMember1() == null || m.getMember2() == null) continue;
            int[] s1 = stats.get(m.getMember1().getId());
            int[] s2 = stats.get(m.getMember2().getId());
            boolean m1won = m.getWinner().getId().equals(m.getMember1().getId());
            if (s1 != null) { s1[1]++; if (m1won) s1[0]++; s1[2] += m.getMember1Score(); s1[3] += m.getMember2Score(); }
            if (s2 != null) { s2[1]++; if (!m1won) s2[0]++; s2[2] += m.getMember2Score(); s2[3] += m.getMember1Score(); }
        }

        List<TournamentMember> allMembers = memberRepo.findByTournamentOrderByCurrentRankAsc(t);
        Map<Long, Integer> oldRanks = new HashMap<>();
        allMembers.forEach(m -> oldRanks.put(m.getId(), m.getCurrentRank()));
        List<TournamentMember> reranked = rankingEngine.recomputeRanksWithProficiency(allMembers);
        reranked.forEach(memberRepo::save);

        Long mvpId = stats.entrySet().stream()
                .filter(e -> e.getValue()[1] > 0)
                .max(Comparator.comparingDouble(e -> {
                    int[] s = e.getValue();
                    return rankingEngine.computeDayScore(s[0], s[1], s[2], s[3]);
                }))
                .map(Map.Entry::getKey).orElse(null);

        if (mvpId == null && !day.getPresentMembers().isEmpty()) {
            mvpId = day.getPresentMembers().get(0).getId();
        }

        List<DTOs.DayRankEntry> entries = new ArrayList<>();
        for (TournamentMember mem : reranked) {
            int[] st = stats.getOrDefault(mem.getId(), new int[]{0, 0, 0, 0});
            int oldRank = oldRanks.getOrDefault(mem.getId(), mem.getCurrentRank());
            int rankChange = oldRank == 0 ? 0 : oldRank - mem.getCurrentRank();
            boolean isMvp = mem.getId().equals(mvpId);
            if (isMvp) { mem.setMvpCount(mem.getMvpCount() + 1); memberRepo.save(mem); }
            DailyRankingSnapshot snap = new DailyRankingSnapshot();
            snap.setTournament(t); snap.setMember(mem); snap.setDay(day);
            snap.setRank(mem.getCurrentRank());
            snap.setMatchesWonToday(st[0]); snap.setMatchesPlayedToday(st[1]);
            snap.setPointsScoredToday(st[2]); snap.setPointsConcededToday(st[3]);
            snap.setMvp(isMvp);
            snapshotRepo.save(snap);
            DTOs.DayRankEntry e = new DTOs.DayRankEntry();
            e.rank = mem.getCurrentRank(); e.memberId = mem.getId();
            e.displayName = mem.getDisplayName(); e.isGuest = mem.isGuest();
            e.matchesPlayed = st[1]; e.matchesWon = st[0]; e.matchesLost = st[1] - st[0];
            e.pointsScored = st[2]; e.pointsConceded = st[3];
            e.dayScore = rankingEngine.computeDayScore(st[0], st[1], st[2], st[3]);
            e.rankChange = rankChange; e.isMvp = isMvp;
            e.mvpCount = mem.getMvpCount();
            e.proficiency = mem.isGuest() ? mem.getGuestProficiency()
                    : (mem.getPlayer() != null ? mem.getPlayer().getProficiency() : null);
            entries.add(e);
        }
        // Post DAY_ENDED and MVP system messages to chat
        String mvpDisplayName = entries.stream()
                .filter(e -> e.isMvp).findFirst()
                .map(e -> e.displayName).orElse(null);
        String dayEndMsg = "Day " + day.getDayNumber() + " ended! "
                + day.getPresentMembers().size() + " players · "
                + entries.stream().mapToInt(e -> e.matchesPlayed).sum() / 2 + " matches played.";
        postSystemMessage(t, dayEndMsg, ChatMessage.MessageType.DAY_ENDED);
        if (mvpDisplayName != null) {
            postSystemMessage(t, "🏆 MVP: " + mvpDisplayName + " — Best performance of the day!",
                    ChatMessage.MessageType.SYSTEM);
        }
        broadcastTournament(tournamentId);
        broadcastChat(tournamentId);

        // Increment sessionStreak for all present members
        for (TournamentMember mem : day.getPresentMembers()) {
            mem.setSessionStreak(mem.getSessionStreak() + 1);
            // Session streak milestones: 5, 10, 20, 30 sessions
            int ss = mem.getSessionStreak();
            if ((ss == 5 || ss == 10 || ss == 20 || ss == 30) && !mem.isGuest() && mem.getPlayer() != null) {
                try { pushService.notifyMatchResult(t,
                        "📅 " + mem.getDisplayName() + " has attended " + ss + " sessions in a row! 🏓"); }
                catch (Exception ignored) {}
            }
            memberRepo.save(mem);
        }

        // Post an MVP poll so members can vote (chat message with options)
        if (!day.getPresentMembers().isEmpty()) {
            String pollOptions = day.getPresentMembers().stream()
                    .filter(m -> m.getTotalMatchesPlayed() > 0)
                    .map(m -> "• " + m.getDisplayName())
                    .limit(8)
                    .collect(Collectors.joining("\n"));
            if (!pollOptions.isBlank()) {
                postSystemMessage(t,
                        "🗳️ MVP POLL — Vote in chat! Who deserves MVP today?\n" + pollOptions
                                + "\nReply with a player's name to cast your vote!",
                        ChatMessage.MessageType.SYSTEM);
                broadcastChat(tournamentId);
            }
        }

        return entries;
    }

    // ── HEAD-TO-HEAD ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DTOs.HeadToHeadResponse getHeadToHead(Long tournamentId, Long member1Id, Long member2Id) {
        TournamentMember m1 = memberRepo.findById(member1Id)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        TournamentMember m2 = memberRepo.findById(member2Id)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        List<Match> h2h = matchRepo.findHeadToHead(member1Id, member2Id);
        int m1wins = 0, m2wins = 0, m1pts = 0, m2pts = 0;
        List<DTOs.HeadToHeadMatch> matchList = new ArrayList<>();
        for (Match match : h2h) {
            if (match.getStatus() != Match.MatchStatus.COMPLETED || match.getWinner() == null) continue;
            boolean isM1first = match.getMember1().getId().equals(member1Id);
            int s1 = isM1first ? match.getMember1Score() : match.getMember2Score();
            int s2 = isM1first ? match.getMember2Score() : match.getMember1Score();
            boolean m1won = match.getWinner().getId().equals(member1Id);
            if (m1won) m1wins++; else m2wins++;
            m1pts += s1; m2pts += s2;
            DTOs.HeadToHeadMatch hm = new DTOs.HeadToHeadMatch();
            hm.matchId = match.getId(); hm.dayNumber = match.getDay().getDayNumber();
            hm.member1Score = s1; hm.member2Score = s2;
            hm.winnerId = match.getWinner().getId(); hm.playedAt = match.getCompletedAt();
            matchList.add(hm);
        }
        DTOs.HeadToHeadResponse res = new DTOs.HeadToHeadResponse();
        res.member1Id = member1Id; res.member1Name = m1.getDisplayName();
        res.member2Id = member2Id; res.member2Name = m2.getDisplayName();
        res.member1Wins = m1wins; res.member2Wins = m2wins;
        res.member1PointsTotal = m1pts; res.member2PointsTotal = m2pts;
        res.totalMatches = matchList.size(); res.matches = matchList;
        int total = m1wins + m2wins;
        res.member1WinPct = total == 0 ? 50.0 : Math.round(m1wins * 1000.0 / total) / 10.0;
        res.member2WinPct = total == 0 ? 50.0 : Math.round(m2wins * 1000.0 / total) / 10.0;
        return res;
    }

    // ── MEMBER STATS ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DTOs.MemberStatsResponse getMemberStats(Long tournamentId, Long memberId) {
        TournamentMember member = memberRepo.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        List<DailyRankingSnapshot> snapshots = snapshotRepo.findByMemberOrderByDayAsc(member);

        List<Match> allMatches = matchRepo.findByMember(memberId);
        Map<Long, int[]> partnerStats = new HashMap<>();
        Map<Long, Integer> rivalGames = new HashMap<>();

        for (Match match : allMatches) {
            if (match.getStatus() != Match.MatchStatus.COMPLETED || match.getWinner() == null) continue;
            if (match.getMember1() == null || match.getMember2() == null) continue;
            boolean isM1 = match.getMember1().getId().equals(memberId);
            TournamentMember opponent = isM1 ? match.getMember2() : match.getMember1();
            if (opponent == null) continue;

            rivalGames.merge(opponent.getId(), 1, Integer::sum);

            if (match.getTeam1() != null && match.getTeam2() != null) {
                Team myTeam = isM1 ? match.getTeam1() : match.getTeam2();
                boolean won = match.getWinner().getId().equals(memberId);
                for (TournamentMember teammate : myTeam.getMembers()) {
                    if (teammate.getId().equals(memberId)) continue;
                    partnerStats.computeIfAbsent(teammate.getId(), k -> new int[]{0, 0});
                    partnerStats.get(teammate.getId())[1]++;
                    if (won) partnerStats.get(teammate.getId())[0]++;
                }
            }
        }

        String bestPartner = partnerStats.entrySet().stream()
                .filter(e -> e.getValue()[1] > 0)
                .max(Comparator.comparingDouble(e -> (double) e.getValue()[0] / e.getValue()[1]))
                .map(e -> memberRepo.findById(e.getKey()).map(TournamentMember::getDisplayName).orElse(null))
                .orElse(null);

        String bestRival = rivalGames.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> memberRepo.findById(e.getKey()).map(TournamentMember::getDisplayName).orElse(null))
                .orElse(null);

        DTOs.MemberStatsResponse res = new DTOs.MemberStatsResponse();
        res.memberId = memberId; res.displayName = member.getDisplayName();
        res.currentRank = member.getCurrentRank();
        res.totalMatchesPlayed = member.getTotalMatchesPlayed();
        res.totalMatchesWon = member.getTotalMatchesWon();
        res.totalMatchesLost = member.getTotalMatchesLost();
        res.winRate = Math.round(member.getWinRate() * 10.0) / 10.0;
        res.daysPlayed = member.getDaysPlayed();
        res.mvpCount = member.getMvpCount();
        res.proficiency = member.isGuest() ? member.getGuestProficiency()
                : (member.getPlayer() != null ? member.getPlayer().getProficiency() : null);
        res.bestPartnerName = bestPartner;
        res.bestRivalName = bestRival;
        res.dailyStats = snapshots.stream().map(s -> {
            DTOs.DailyStatEntry e = new DTOs.DailyStatEntry();
            e.dayNumber = s.getDay().getDayNumber(); e.rank = s.getRank();
            e.matchesWon = s.getMatchesWonToday(); e.matchesPlayed = s.getMatchesPlayedToday();
            e.pointsScored = s.getPointsScoredToday(); e.pointsConceded = s.getPointsConcededToday();
            e.dayScore = rankingEngine.computeDayScore(s.getMatchesWonToday(), s.getMatchesPlayedToday(),
                    s.getPointsScoredToday(), s.getPointsConcededToday());
            e.isMvp = s.isMvp();
            e.date = s.getDay().getStartedAt();
            return e;
        }).collect(Collectors.toList());
        return res;
    }

    // ── CHAT ──────────────────────────────────────────────────────────────────

    public DTOs.ChatMessageResponse sendMessage(Long tournamentId, Player sender, String content) {
        Tournament t = getTournament(tournamentId);
        if (!memberRepo.existsByTournamentIdAndPlayerId(t.getId(), sender.getId()) && !isAdmin(t, sender))
            throw new RuntimeException("Not a member of this tournament");
        ChatMessage msg = new ChatMessage();
        msg.setTournament(t); msg.setSender(sender);
        msg.setContent(content.trim()); msg.setType(ChatMessage.MessageType.TEXT);
        msg = chatRepo.save(msg);
        broadcastChat(tournamentId);
        pushService.notifyChat(t, sender.getDisplayName(), content.trim());
        return toChatResponse(msg);
    }

    public List<DTOs.ChatMessageResponse> getMessages(Long tournamentId, Player requester) {
        Tournament t = getTournament(tournamentId);
        return chatRepo.findByTournamentOrderBySentAtAsc(t).stream()
                .map(this::toChatResponse).collect(Collectors.toList());
    }

    // ── RANKINGS ─────────────────────────────────────────────────────────────

    public List<DTOs.RankingResponse> getRankings(Long tournamentId) {
        Tournament t = getTournament(tournamentId);
        List<TournamentMember> members = memberRepo.findRanked(tournamentId);
        Map<Long, Integer> prevRanks = new HashMap<>();
        dayRepo.findByTournamentOrderByDayNumberAsc(t).stream()
                .filter(d -> d.getStatus() == TournamentDay.DayStatus.ENDED)
                .reduce((a, b) -> b)
                .ifPresent(lastDay -> snapshotRepo.findByTournamentAndDayOrderByRankAsc(t, lastDay)
                        .forEach(s -> prevRanks.put(s.getMember().getId(), s.getRank())));
        List<DTOs.RankingResponse> result = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            TournamentMember m = members.get(i);
            int prev = prevRanks.getOrDefault(m.getId(), 0);
            int rankChange = prev == 0 ? 0 : prev - m.getCurrentRank();
            DTOs.RankingResponse r = new DTOs.RankingResponse();
            r.rank = m.getCurrentRank() == 0 ? i + 1 : m.getCurrentRank();
            r.memberId = m.getId(); r.displayName = m.getDisplayName(); r.isGuest = m.isGuest();
            r.totalMatchesWon = m.getTotalMatchesWon();
            r.totalMatchesPlayed = m.getTotalMatchesPlayed();
            r.totalMatchesLost = m.getTotalMatchesLost();
            r.winRate = Math.round(m.getWinRate() * 10.0) / 10.0;
            r.daysPlayed = m.getDaysPlayed(); r.rankChangeSinceYesterday = rankChange;
            r.mvpCount = m.getMvpCount();
            r.proficiency = m.isGuest() ? m.getGuestProficiency()
                    : (m.getPlayer() != null ? m.getPlayer().getProficiency() : null);
            r.eloRating = m.getEloRating();
            r.currentWinStreak = m.getCurrentWinStreak();
            r.bestWinStreak = m.getBestWinStreak();
            r.sessionStreak = m.getSessionStreak();
            result.add(r);
        }
        return result;
    }

    // ── FIX: getMyTournaments — avoid lazy loading Tournament.members ─────────
    // Original code called t.getMembers().size() which triggered lazy load outside session
    @Transactional(readOnly = true)
    public List<DTOs.TournamentSummaryResponse> getMyTournaments(Player player) {
        return tournamentRepo.findByMemberPlayer(player).stream().map(t -> {
            // Exclude challenge-bucket day (dayNumber = -1) from all calculations
            List<TournamentDay> days = dayRepo.findByTournamentOrderByDayNumberAsc(t).stream()
                    .filter(d -> d.getDayNumber() >= 0).collect(Collectors.toList());
            Optional<TournamentDay> lastDay = days.stream().reduce((a, b) -> b);
            int daysPlayed = (int) days.stream().filter(d -> d.getStatus() == TournamentDay.DayStatus.ENDED).count();
            long memberCount = memberRepo.countByTournament(t);
            DTOs.TournamentSummaryResponse s = new DTOs.TournamentSummaryResponse();
            s.id = t.getId(); s.name = t.getName(); s.memberCount = (int) memberCount; s.inviteCode = t.getInviteCode();
            s.adminCount = t.getAdmins().size(); s.daysPlayed = daysPlayed;
            s.isAdmin = isAdmin(t, player); s.isMember = true; s.createdAt = t.getCreatedAt();
            s.lastDayStatus = lastDay.map(d -> d.getStatus().name()).orElse("NO_DAYS");
            s.lastDayNumber = lastDay.map(TournamentDay::getDayNumber).orElse(0);
            return s;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DTOs.TournamentDetailResponse getTournamentDetail(Long tournamentId, Player requester) {
        Tournament t = getTournament(tournamentId);
        List<TournamentMember> members = memberRepo.findByTournamentOrderByCurrentRankAsc(t);
        // Exclude challenge-bucket day (dayNumber = -1) from history list
        List<TournamentDay> days = dayRepo.findByTournamentOrderByDayNumberAsc(t).stream()
                .filter(d -> d.getDayNumber() >= 0).collect(Collectors.toList());
        DTOs.TournamentDetailResponse res = new DTOs.TournamentDetailResponse();
        res.id = t.getId(); res.name = t.getName(); res.memberCount = members.size(); res.inviteCode = t.getInviteCode();
        res.members = members.stream().map(this::toMemberResponse).collect(Collectors.toList());
        res.admins = t.getAdmins().stream().map(a -> {
            DTOs.AdminResponse ar = new DTOs.AdminResponse();
            ar.playerId = a.getId(); ar.displayName = a.getDisplayName(); return ar;
        }).collect(Collectors.toList());
        res.days = days.stream().map(d -> toDayResponse(d, false)).collect(Collectors.toList());
        // currentDay: prefer a real IN_PROGRESS day (dayNumber >= 0) over the challenge bucket
        res.currentDay = dayRepo.findByTournamentOrderByDayNumberAsc(t).stream()
                .filter(d -> d.getDayNumber() >= 0 && d.getStatus() == TournamentDay.DayStatus.IN_PROGRESS)
                .reduce((a, b) -> b) // last one
                .map(d -> toDayResponse(d, true)).orElse(null);
        res.rankings = getRankings(tournamentId);
        res.createdAt = t.getCreatedAt(); res.isAdmin = isAdmin(t, requester);
        return res;
    }

    // ── TODAY (lightweight poll endpoint) ───────────────────────────────────────
    // Returns only current day + matches. Used by frontend polling every 8s.
    // Much faster than full getTournamentDetail which loads all history.
    @Transactional(readOnly = true)
    public DTOs.TodayResponse getTodayData(Long tournamentId, Player requester) {
        Tournament t = getTournament(tournamentId);
        DTOs.TodayResponse res = new DTOs.TodayResponse();
        res.isAdmin = isAdmin(t, requester);
        res.serverTime = System.currentTimeMillis();
        // Only return real days (not the challenge bucket) as the current day
        res.currentDay = dayRepo.findByTournamentOrderByDayNumberAsc(t).stream()
                .filter(d -> d.getDayNumber() >= 0 && d.getStatus() == TournamentDay.DayStatus.IN_PROGRESS)
                .reduce((a, b) -> b)
                .map(d -> toDayResponse(d, true))
                .orElse(null);
        return res;
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    private void postSystemMessage(Tournament t, String content, ChatMessage.MessageType type) {
        ChatMessage msg = new ChatMessage();
        msg.setTournament(t);
        // Use createdBy as sender — it's fine if null, toChatResponse handles null safely
        msg.setSender(t.getCreatedBy());
        msg.setContent(content); msg.setType(type);
        chatRepo.save(msg);
    }

    private DTOs.ChatMessageResponse toChatResponse(ChatMessage m) {
        DTOs.ChatMessageResponse r = new DTOs.ChatMessageResponse();
        r.id = m.getId();
        // Null-safe: system messages may have null sender if createdBy is not set
        r.senderId = m.getSender() != null ? m.getSender().getId() : 0L;
        r.senderName = m.getSender() != null ? m.getSender().getDisplayName() : "System";
        r.content = m.getContent(); r.type = m.getType().name(); r.sentAt = m.getSentAt();
        return r;
    }

    private DTOs.MemberResponse toMemberResponse(TournamentMember m) {
        DTOs.MemberResponse r = new DTOs.MemberResponse();
        r.id = m.getId();
        r.playerId = m.isGuest() ? null : (m.getPlayer() != null ? m.getPlayer().getId() : null);
        r.displayName = m.getDisplayName(); r.isGuest = m.isGuest();
        r.currentRank = m.getCurrentRank(); r.totalMatchesPlayed = m.getTotalMatchesPlayed();
        r.totalMatchesWon = m.getTotalMatchesWon(); r.totalMatchesLost = m.getTotalMatchesLost();
        r.winRate = Math.round(m.getWinRate() * 10.0) / 10.0; r.daysPlayed = m.getDaysPlayed();
        r.mvpCount = m.getMvpCount();
        r.proficiency = m.isGuest() ? m.getGuestProficiency()
                : (m.getPlayer() != null ? m.getPlayer().getProficiency() : null);
        r.eloRating = m.getEloRating();
        r.currentWinStreak = m.getCurrentWinStreak();
        r.bestWinStreak = m.getBestWinStreak();
        r.sessionStreak = m.getSessionStreak();
        return r;
    }

    private DTOs.DayResponse toDayResponse(TournamentDay day, boolean includeMatches) {
        List<Match> matches = matchRepo.findByDayOrderByMatchNumberAsc(day);
        List<Team> teams = teamRepo.findByDayOrderByMatchesWonDesc(day);
        long elapsed = 0;
        if (day.getStartedAt() != null && day.getStatus() == TournamentDay.DayStatus.IN_PROGRESS)
            elapsed = ChronoUnit.SECONDS.between(day.getStartedAt(), LocalDateTime.now());
        else if (day.getTimerSeconds() > 0) elapsed = day.getTimerSeconds();

        String mvpName = null;
        Long mvpMemberId = null;
        if (day.getStatus() == TournamentDay.DayStatus.ENDED) {
            Tournament t = day.getTournament();
            var mvpSnap = snapshotRepo.findByTournamentAndDayOrderByRankAsc(t, day).stream()
                    .filter(DailyRankingSnapshot::isMvp).findFirst().orElse(null);
            if (mvpSnap != null) {
                mvpName = mvpSnap.getMember().getDisplayName();
                mvpMemberId = mvpSnap.getMember().getId();
            }
        }

        DTOs.DayResponse r = new DTOs.DayResponse();
        r.id = day.getId(); r.dayNumber = day.getDayNumber(); r.status = day.getStatus().name();
        r.matchFormat = day.getMatchFormat().name(); r.numberOfTeams = day.getNumberOfTeams();
        r.presentMembers = day.getPresentMembers().stream().map(this::toMemberResponse).collect(Collectors.toList());
        r.teams = teams.stream().map(team -> {
            DTOs.TeamResponse tr = new DTOs.TeamResponse();
            tr.id = team.getId(); tr.name = team.getName();
            tr.matchesWon = team.getMatchesWon(); tr.matchesLost = team.getMatchesLost();
            tr.members = team.getMembers().stream().map(this::toMemberResponse).collect(Collectors.toList());
            return tr;
        }).collect(Collectors.toList());
        r.matches = includeMatches
                ? matches.stream().map(this::toMatchResponse).collect(Collectors.toList())
                : List.of();
        r.startedAt = day.getStartedAt(); r.endedAt = day.getEndedAt();
        r.timerSeconds = day.getTimerSeconds(); r.elapsedSeconds = elapsed;
        r.mvpName = mvpName; r.mvpMemberId = mvpMemberId;
        return r;
    }

    private DTOs.MatchResponse toMatchResponse(Match m) {
        DTOs.MatchResponse r = new DTOs.MatchResponse();
        r.id = m.getId(); r.matchNumber = m.getMatchNumber();
        r.member1Id = m.getMember1() != null ? m.getMember1().getId() : null;
        r.member2Id = m.getMember2() != null ? m.getMember2().getId() : null;
        r.team1Name = m.getTeam1() != null ? m.getTeam1().getName() : null;
        r.team2Name = m.getTeam2() != null ? m.getTeam2().getName() : null;

        boolean is2v2 = m.getTeam1() != null && m.getTeam2() != null
                && m.getTeam1().getMembers() != null && m.getTeam1().getMembers().size() > 1;
        if (is2v2) {
            r.team1Members = m.getTeam1().getMembers().stream()
                    .map(TournamentMember::getDisplayName).collect(Collectors.toList());
            r.team2Members = m.getTeam2().getMembers().stream()
                    .map(TournamentMember::getDisplayName).collect(Collectors.toList());
            r.member1Name = String.join(" & ", r.team1Members);
            r.member2Name = String.join(" & ", r.team2Members);
        } else {
            r.member1Name = m.getMember1() != null ? m.getMember1().getDisplayName() : "?";
            r.member2Name = m.getMember2() != null ? m.getMember2().getDisplayName() : "?";
            r.team1Members = r.member1Name.equals("?") ? List.of() : List.of(r.member1Name);
            r.team2Members = r.member2Name.equals("?") ? List.of() : List.of(r.member2Name);
        }

        r.member1Score = m.getMember1Score(); r.member2Score = m.getMember2Score();
        r.winnerId = m.getWinner() != null ? m.getWinner().getId() : null;
        r.winnerName = m.getWinner() != null ? m.getWinner().getDisplayName() : null;
        r.status = m.getStatus().name();
        r.member1WinProb = m.getMember1WinProb(); r.member2WinProb = m.getMember2WinProb();
        r.startedAt = m.getStartedAt(); r.completedAt = m.getCompletedAt();
        return r;
    }

    private void broadcastTournament(Long id) {
        try { ws.convertAndSend("/topic/tournament/" + id + "/update", System.currentTimeMillis()); }
        catch (Exception e) { log.severe("WS error: " + e.getMessage()); }
    }
    private void broadcastDay(Long tid, Long did) {
        try { ws.convertAndSend("/topic/tournament/" + tid + "/day/" + did, System.currentTimeMillis()); }
        catch (Exception e) { log.severe("WS error: " + e.getMessage()); }
    }
    private void broadcastChat(Long tid) {
        try { ws.convertAndSend("/topic/tournament/" + tid + "/chat", System.currentTimeMillis()); }
        catch (Exception e) { log.severe("WS error: " + e.getMessage()); }
    }

    // ── RSVP ──────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> sendRsvpCall(Long tournamentId, Player admin) {
        Tournament t = getTournament(tournamentId);
        assertAdmin(t, admin);
        postSystemMessage(t,
                "📋 RSVP: Are you coming to the next session? Reply YES or NO in chat, or tap the button below!",
                ChatMessage.MessageType.SYSTEM);
        try { pushService.notifyMatchResult(t, "📋 RSVP: Are you coming to the next session? Let your admin know!"); }
        catch (Exception ignored) {}
        broadcastChat(tournamentId);
        return Map.of("sent", true, "message", "RSVP notification sent to all members");
    }

    @Transactional
    public Map<String, Object> submitRsvp(Long tournamentId, Player player, boolean attending) {
        Tournament t = getTournament(tournamentId);
        boolean isMember = memberRepo.existsByTournamentIdAndPlayerId(tournamentId, player.getId());
        boolean isAdm = isAdmin(t, player);
        if (!isMember && !isAdm) throw new RuntimeException("Not a member of this tournament");
        // Find member by player ID safely (player is never null for registered users)
        TournamentMember mem = memberRepo.findRanked(tournamentId).stream()
                .filter(m -> m.getPlayer() != null && m.getPlayer().getId().equals(player.getId()))
                .findFirst().orElseThrow(() -> new RuntimeException("Member record not found"));
        String response = attending ? "✅ YES" : "❌ NO";
        postSystemMessage(t, mem.getDisplayName() + " replied " + response + " to the RSVP",
                ChatMessage.MessageType.SYSTEM);
        broadcastChat(tournamentId);
        return Map.of("attending", attending, "name", mem.getDisplayName());
    }

    // ── SESSION TEMPLATES ─────────────────────────────────────────────────────
    // Stored as a JSON chat system message with a special prefix — no new table needed.
    // Frontend saves to AsyncStorage; these endpoints are for sharing across devices.

    @Transactional
    public Map<String, Object> saveSessionTemplate(Long tournamentId, Player admin, DTOs.SessionTemplateRequest req) {
        Tournament t = getTournament(tournamentId);
        assertAdmin(t, admin);
        // Store template as a special SYSTEM message so it persists on backend
        String json = String.format(
                "__TEMPLATE__{\"name\":\"%s\",\"format\":\"%s\",\"nTeams\":%d,\"perTeam\":%d,\"savedBy\":\"%s\"}",
                req.getName().replace("\"",""), req.getMatchFormat(), req.getNumberOfTeams(),
                req.getPlayersPerTeam(), admin.getDisplayName());
        ChatMessage msg = new ChatMessage();
        msg.setTournament(t);
        msg.setSender(null);
        msg.setContent(json);
        msg.setType(ChatMessage.MessageType.SYSTEM);
        chatRepo.save(msg);
        return Map.of("saved", true, "templateName", req.getName());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSessionTemplates(Long tournamentId, Player player) {
        Tournament t = getTournament(tournamentId);
        return chatRepo.findByTournamentOrderBySentAtAsc(t).stream()
                .filter(m -> m.getContent() != null && m.getContent().startsWith("__TEMPLATE__"))
                .map(m -> {
                    try {
                        String json = m.getContent().substring("__TEMPLATE__".length());
                        // Parse manually (no Jackson dep assumed here — use simple parsing)
                        Map<String, Object> result = new java.util.LinkedHashMap<>();
                        json = json.replaceAll("[{}]", "");
                        for (String pair : json.split(",")) {
                            String[] kv = pair.split(":", 2);
                            if (kv.length == 2) {
                                result.put(kv[0].replace("\"","").trim(),
                                        kv[1].replace("\"","").trim());
                            }
                        }
                        result.put("savedAt", m.getSentAt() != null ? m.getSentAt().toString() : "");
                        return result;
                    } catch (Exception e) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── NOTIFICATION PREFERENCES ──────────────────────────────────────────────

    @Transactional
    public Map<String, Object> updateNotifPrefs(Long tournamentId, Player player, Map<String, Boolean> prefs) {
        Tournament t = getTournament(tournamentId);
        TournamentMember mem = memberRepo.findRanked(tournamentId).stream()
                .filter(m -> m.getPlayer() != null && m.getPlayer().getId().equals(player.getId()))
                .findFirst().orElseThrow(() -> new RuntimeException("Member not found"));
        // Build JSON string
        StringBuilder sb = new StringBuilder("{");
        prefs.forEach((k, v) -> sb.append("\"").append(k).append("\":").append(v).append(","));
        if (sb.charAt(sb.length() - 1) == ',') sb.setCharAt(sb.length() - 1, '}');
        else sb.append("}");
        mem.setNotifPrefs(sb.toString());
        memberRepo.save(mem);
        return Map.of("saved", true, "prefs", sb.toString());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getNotifPrefs(Long tournamentId, Player player) {
        TournamentMember mem = memberRepo.findRanked(tournamentId).stream()
                .filter(m -> m.getPlayer() != null && m.getPlayer().getId().equals(player.getId()))
                .findFirst().orElse(null);
        if (mem == null) return Map.of();
        String prefs = mem.getNotifPrefs();
        if (prefs == null || prefs.isBlank()) prefs = "{\"MATCH_RESULT\":true,\"CHALLENGE\":true,\"DAY_START\":true,\"MILESTONE\":true}";
        return Map.of("prefs", prefs);
    }


    // ── INVITE CODE JOIN ──────────────────────────────────────────────────────
    @Transactional
    public Long joinByInviteCode(Player player, String inviteCode, String password) {
        Tournament t = tournamentRepo.findByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> new RuntimeException("Invalid invite link"));

        String hash = t.getPassword();
        if (hash != null && !hash.isBlank()) {
            if (password == null || !passwordEncoder.matches(password, hash))
                throw new RuntimeException("Wrong password");
        }

        boolean alreadyMember = memberRepo.existsByTournamentIdAndPlayerId(t.getId(), player.getId());
        if (alreadyMember) throw new RuntimeException("Already in this tournament");

        TournamentMember m = new TournamentMember();
        m.setTournament(t); m.setPlayer(player); m.setGuest(false);
        memberRepo.save(m);
        player.setTournamentsPlayed(player.getTournamentsPlayed() + 1);
        playerRepo.save(player);
        postSystemMessage(t, player.getDisplayName() + " joined via invite link!", ChatMessage.MessageType.SYSTEM);
        broadcastTournament(t.getId());
        broadcastChat(t.getId());
        return t.getId();
    }

    // ── REGENERATE INVITE CODE (admin only) ───────────────────────────────────
    @Transactional
    public Map<String, Object> regenerateInviteCode(Long tournamentId, Player admin) {
        Tournament t = getTournament(tournamentId);
        assertAdmin(t, admin);
        String inviteCode;
        do {
            inviteCode = java.util.UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase();
        } while (tournamentRepo.findByInviteCode(inviteCode).isPresent());
        t.setInviteCode(inviteCode);
        tournamentRepo.save(t);
        return Map.of("inviteCode", inviteCode);
    }

    // ── SESSION POLL (in chat) ─────────────────────────────────────────────────
    // Polls are stored as chat messages with type POLL and JSON content
    // Format: {"question":"When to play?","options":["Mon 6pm","Tue 7pm"],"votes":{"0":[1,2],"1":[3]}}
    @Transactional
    public Map<String, Object> createSessionPoll(Long tournamentId, Player creator, String question, java.util.List<String> options) {
        Tournament t = getTournament(tournamentId);
        if (!memberRepo.existsByTournamentIdAndPlayerId(tournamentId, creator.getId()))
            throw new RuntimeException("Not a member");
        if (options == null || options.size() < 2 || options.size() > 6)
            throw new RuntimeException("Poll needs 2-6 options");
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode poll = mapper.createObjectNode();
            poll.put("question", question);
            ArrayNode optsNode = mapper.createArrayNode();
            for (String opt : options) optsNode.add(opt);
            poll.set("options", optsNode);
            poll.set("votes", mapper.createObjectNode());
            poll.put("creatorId", creator.getId());
            ChatMessage msg = new ChatMessage();
            msg.setTournament(t);
            msg.setSender(creator);
            msg.setContent(mapper.writeValueAsString(poll));
            msg.setType(ChatMessage.MessageType.POLL);
            chatRepo.save(msg);
            broadcastChat(tournamentId);
            return Map.of("success", true, "messageId", msg.getId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create poll: " + e.getMessage());
        }
    }


    @Transactional
    public Map<String, Object> votePoll(Long tournamentId, Long messageId, Player voter, int optionIndex) {
        getTournament(tournamentId); // validate exists
        if (!memberRepo.existsByTournamentIdAndPlayerId(tournamentId, voter.getId()))
            throw new RuntimeException("Not a member");

        ChatMessage msg = chatRepo.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Poll not found"));
        if (msg.getType() != ChatMessage.MessageType.POLL)
            throw new RuntimeException("Not a poll");

        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode poll = (ObjectNode) mapper.readTree(msg.getContent());
            ObjectNode votes = poll.has("votes") && !poll.get("votes").isNull()
                    ? (ObjectNode) poll.get("votes")
                    : mapper.createObjectNode();

            // Remove voter from all options (allows changing vote)
            Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> it = votes.fields();
            while (it.hasNext()) {
                ArrayNode arr = (ArrayNode) it.next().getValue();
                for (int i = arr.size() - 1; i >= 0; i--) {
                    if (arr.get(i).asLong() == voter.getId()) arr.remove(i);
                }
            }

            // Add vote to selected option
            String key = String.valueOf(optionIndex);
            if (!votes.has(key)) votes.set(key, mapper.createArrayNode());
            ((ArrayNode) votes.get(key)).add(voter.getId());
            poll.set("votes", votes);

            msg.setContent(mapper.writeValueAsString(poll));
            chatRepo.save(msg);
            broadcastChat(tournamentId);
            return Map.of("success", true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process vote: " + e.getMessage());
        }
    }

    // ── MATCH SCHEDULING ──────────────────────────────────────────────────────
    // Scheduling proposals stored as POLL-type chat messages with special format
    @Transactional
    public Map<String, Object> proposeMatchTime(Long tournamentId, Player proposer, Long targetMemberId, String proposedTime, String note) {
        Tournament t = getTournament(tournamentId);
        TournamentMember proposerMember = memberRepo.findRanked(tournamentId).stream()
                .filter(m -> m.getPlayer() != null && m.getPlayer().getId().equals(proposer.getId()))
                .findFirst().orElseThrow(() -> new RuntimeException("Not a member"));
        TournamentMember targetMember = memberRepo.findById(targetMemberId)
                .orElseThrow(() -> new RuntimeException("Target player not found"));
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode obj = mapper.createObjectNode();
            obj.put("type", "SCHEDULE");
            obj.put("proposerId", proposerMember.getId());
            obj.put("proposerName", proposer.getDisplayName());
            obj.put("targetId", targetMember.getId());
            obj.put("targetName", targetMember.getDisplayName());
            obj.put("time", proposedTime != null ? proposedTime : "");
            obj.put("note", note != null ? note : "");
            obj.put("status", "PENDING");
            ChatMessage msg = new ChatMessage();
            msg.setTournament(t);
            msg.setSender(proposer);
            msg.setContent(mapper.writeValueAsString(obj));
            msg.setType(ChatMessage.MessageType.SCHEDULE);
            chatRepo.save(msg);
            broadcastChat(tournamentId);
            return Map.of("success", true, "messageId", msg.getId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create schedule: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> respondToSchedule(Long tournamentId, Long messageId, Player responder, String action) {
        ChatMessage msg = chatRepo.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));
        if (msg.getType() != ChatMessage.MessageType.SCHEDULE)
            throw new RuntimeException("Not a schedule proposal");
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode obj = (ObjectNode) mapper.readTree(msg.getContent());
            obj.put("status", action);
            obj.put("responderId", responder.getId());
            msg.setContent(mapper.writeValueAsString(obj));
            chatRepo.save(msg);
            String systemMsg = "ACCEPT".equals(action)
                    ? "✅ " + responder.getDisplayName() + " accepted the match proposal!"
                    : "❌ " + responder.getDisplayName() + " declined the match proposal.";
            postSystemMessage(msg.getTournament(), systemMsg, ChatMessage.MessageType.SYSTEM);
            broadcastChat(tournamentId);
            return Map.of("success", true, "action", action);
        } catch (Exception e) {
            throw new RuntimeException("Failed to respond: " + e.getMessage());
        }
    }

    public Tournament getTournament(Long id) {
        return tournamentRepo.findById(id).orElseThrow(() -> new RuntimeException("Tournament not found"));
    }
    public boolean isAdmin(Tournament t, Player p) {
        return t.getAdmins().stream().anyMatch(a -> a.getId().equals(p.getId()));
    }
    private void assertAdmin(Tournament t, Player p) {
        if (!isAdmin(t, p)) throw new RuntimeException("Admin access required");
    }
    public Match getMatchById(Long id) {
        return matchRepo.findById(id).orElseThrow(() -> new RuntimeException("Match not found"));
    }
}
