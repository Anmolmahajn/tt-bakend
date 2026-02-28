package com.tt.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

/**
 * FREE AI — no paid API key needed.
 * Option 1 (Groq, recommended): https://console.groq.com — free, fast, no credit card
 * Option 2 (Ollama, local):     https://ollama.ai — runs on your PC, fully offline
 * Option 3 (Gemini free tier):  https://aistudio.google.com/app/apikey
 *
 * Set in application.properties:
 *   ai.provider=groq          (or: ollama / gemini / none)
 *   ai.api.key=gsk_xxxx       (Groq key — leave blank for Ollama/none)
 *   ai.ollama.url=http://localhost:11434   (if using Ollama)
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiController {

    @Value("${ai.provider:none}")
    private String provider;

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String SYSTEM_PROMPT =
            "You are an expert table tennis coach and tournament analyst. " +
                    "You have deep knowledge of table tennis tactics, training methods, rankings, team strategy, " +
                    "player psychology, and match preparation. " +
                    "Always give specific, actionable advice. Be encouraging but honest. " +
                    "Use bullet points for lists. Keep responses focused and under 200 words unless a detailed " +
                    "analysis is explicitly requested. Emoji use is welcome. " +
                    "Refer to players by name when data is provided.";

    // ── GENERAL ASK ──────────────────────────────────────────────────────────

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "").trim();
        if (prompt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Empty prompt"));
        try {
            String response = switch (provider.toLowerCase()) {
                case "groq"   -> callGroq(prompt, SYSTEM_PROMPT, 350);
                case "ollama" -> callOllama(prompt);
                case "gemini" -> callGemini(prompt, SYSTEM_PROMPT);
                default       -> smartFallback(prompt);
            };
            return ResponseEntity.ok(Map.of("response", response));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("response", smartFallback(prompt)));
        }
    }

    // ── DEEP PLAYER ANALYSIS ─────────────────────────────────────────────────

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, Object> body) {
        String playerName  = String.valueOf(body.getOrDefault("playerName", "Player"));
        int    rank        = ((Number) body.getOrDefault("rank", 0)).intValue();
        int    wins        = ((Number) body.getOrDefault("wins", 0)).intValue();
        int    played      = ((Number) body.getOrDefault("played", 0)).intValue();
        int    mvps        = ((Number) body.getOrDefault("mvps", 0)).intValue();
        String proficiency = String.valueOf(body.getOrDefault("proficiency", "Intermediate"));
        String partner     = String.valueOf(body.getOrDefault("bestPartner", "none"));
        String rival       = String.valueOf(body.getOrDefault("bestRival", "none"));
        int    daysPlayed  = ((Number) body.getOrDefault("daysPlayed", 0)).intValue();
        String recentTrend = String.valueOf(body.getOrDefault("recentTrend", "stable"));

        double winRate = played > 0 ? (wins * 100.0 / played) : 0;

        String prompt = String.format(
                "Perform a comprehensive analysis of table tennis player %s.\n" +
                        "Stats: Rank #%d | Win rate %.1f%% (%d wins / %d matches) | " +
                        "%d sessions played | %d MVPs | Skill: %s.\n" +
                        "Best partner: %s. Toughest rival: %s. Recent form: %s.\n\n" +
                        "Provide a structured analysis with these 5 sections:\n" +
                        "1. STRENGTHS (what they're doing well based on stats)\n" +
                        "2. AREAS TO IMPROVE (specific weaknesses to address)\n" +
                        "3. TACTICAL ADVICE (2-3 match tactics to try)\n" +
                        "4. THIS WEEK'S GOALS (2 concrete, measurable targets)\n" +
                        "5. RANK PREDICTION (where they'll be in 5 sessions if they follow your advice)\n" +
                        "Be specific to their level (%s). Use player's name. Keep each section to 2-3 bullet points.",
                playerName, rank, winRate, wins, played, daysPlayed, mvps, proficiency,
                partner, rival, recentTrend, proficiency
        );

        try {
            String response = switch (provider.toLowerCase()) {
                case "groq"   -> callGroq(prompt, SYSTEM_PROMPT, 600);
                case "ollama" -> callOllama(prompt);
                case "gemini" -> callGemini(prompt, SYSTEM_PROMPT);
                default       -> deepAnalysisFallback(playerName, rank, (int) winRate, proficiency, mvps, recentTrend);
            };
            return ResponseEntity.ok(Map.of("response", response));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("response",
                    deepAnalysisFallback(playerName, rank, (int) winRate, proficiency, mvps, recentTrend)));
        }
    }

    // ── GROQ ─────────────────────────────────────────────────────────────────
    private String callGroq(String prompt, String systemPrompt, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) return smartFallback(prompt);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Authorization", "Bearer " + apiKey);
        Map<String, Object> req = Map.of(
                "model", "llama3-8b-8192",
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", prompt)
                )
        );
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "https://api.groq.com/openai/v1/chat/completions",
                new HttpEntity<>(req, h), Map.class);
        List<Map<String,Object>> choices = (List<Map<String,Object>>) resp.getBody().get("choices");
        Map<String,Object> msg = (Map<String,Object>) choices.get(0).get("message");
        return (String) msg.get("content");
    }

    // ── OLLAMA ───────────────────────────────────────────────────────────────
    private String callOllama(String prompt) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> req = Map.of(
                "model", "llama3",
                "prompt", SYSTEM_PROMPT + "\n\n" + prompt,
                "stream", false
        );
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                ollamaUrl + "/api/generate",
                new HttpEntity<>(req, h), Map.class);
        return (String) resp.getBody().get("response");
    }

    // ── GEMINI ───────────────────────────────────────────────────────────────
    private String callGemini(String prompt, String systemPrompt) {
        if (apiKey == null || apiKey.isBlank()) return smartFallback(prompt);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        Map<String,Object> part    = Map.of("text", systemPrompt + "\n\n" + prompt);
        Map<String,Object> content = Map.of("parts", List.of(part));
        Map<String,Object> req     = Map.of("contents", List.of(content));
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
        ResponseEntity<Map> resp = restTemplate.postForEntity(url, new HttpEntity<>(req, h), Map.class);
        List<Map<String,Object>> candidates = (List<Map<String,Object>>) resp.getBody().get("candidates");
        Map<String,Object> c    = (Map<String,Object>) candidates.get(0).get("content");
        List<Map<String,Object>> parts = (List<Map<String,Object>>) c.get("parts");
        return (String) parts.get(0).get("text");
    }

    // ── SMART FALLBACK ────────────────────────────────────────────────────────
    private String smartFallback(String prompt) {
        String lower = prompt.toLowerCase();
        if (lower.contains("team") || lower.contains("split") || lower.contains("balance") || lower.contains("partner"))
            return "⚖️ Team Balance\n\n• Pair Rank #1 with #4, #2 with #3 for equal-strength teams\n• For 2v2: one experienced + one developing player per team\n• Avoid stacking all top-ranks — close matches keep everyone engaged\n• Use 'AI Team Suggestion' in Start Day to auto-generate balanced teams";
        if (lower.contains("mvp") || lower.contains("best player") || lower.contains("top"))
            return "🏆 MVP Prediction\n\n• MVP winners typically have 70%+ win rate in that session\n• Watch for players on win streaks — momentum matters in table tennis\n• Dominant wins (large point margins) indicate peak form, not just victories\n• The player facing toughest opponents and still winning is often the true MVP";
        if (lower.contains("rank") || lower.contains("improve") || lower.contains("better") || lower.contains("progress"))
            return "📈 Ranking Strategy\n\n• Win ratio (wins÷matches) is the #1 ranking factor — quality over quantity\n• Show up every session — absences hurt more than losses\n• Challenge players ranked 1-2 spots above you for maximum rank gains\n• Reducing unforced errors improves rank faster than learning new shots";
        if (lower.contains("weak") || lower.contains("struggle") || lower.contains("losing") || lower.contains("lose"))
            return "🎯 Overcoming a Losing Streak\n\n• Identify if losses come from serves, returns, or mid-rally errors\n• Practice the third-ball attack — the most decisive sequence in table tennis\n• Against stronger opponents, play consistent and patient — wait for their errors\n• Mental reset between points is as important as technique";
        if (lower.contains("strategy") || lower.contains("tactic") || lower.contains("beat") || lower.contains("vs") || lower.contains("versus"))
            return "♟️ Match Tactics\n\n• Against aggressive players: heavy backspin returns disrupt their timing\n• Against defensive players: vary pace unpredictably — they thrive on consistency\n• In 2v2: decide before each point who takes the middle ball\n• Serve variation is the most underused weapon at club level";
        if (lower.contains("session") || lower.contains("day plan") || lower.contains("warm") || lower.contains("prepare"))
            return "📋 Session Preparation\n\n• Warm up with 5 mins of cross-table forehand/backhand exchanges\n• Practice 3-5 serves before your first competitive match\n• Aim for 8-12 matches per session for meaningful ranking data\n• End with a review: one thing that worked, one thing to fix next time";
        if (lower.contains("predict") || lower.contains("who will win") || lower.contains("favourite") || lower.contains("odds"))
            return "🔮 Match Prediction\n\n• Win probability uses rank difference + skill level bonus\n• Higher-ranked players win ~65-70% against the next rank down\n• Upsets most often happen when the lower-ranked player has more recent match volume\n• Check the % shown next to each match on the Today tab";
        if (lower.contains("coach") || lower.contains("tip") || lower.contains("advice") || lower.contains("help"))
            return "💡 Coaching Insight\n\n• The 80/20 rule: 80% of your wins come from 20% of your shots — master those first\n• Footwork beats technique — the best shot from the wrong position still loses\n• Study your best rival's patterns — identify what they do that you don't counter well\n• Use Head-to-Head stats to spot patterns across your match history";
        return "🤖 AI Insight\n\n• Track your wins-per-match ratio across sessions — it's the truest improvement metric\n• Consistency beats brilliance: regular attendance ranks you higher than sporadic peaks\n• Use Head-to-Head stats to prepare specific tactics against your regular opponents\n• The Full Analytics dashboard shows your performance trend over time";
    }

    private String deepAnalysisFallback(String name, int rank, int winRate,
                                        String prof, int mvps, String trend) {
        String trendLine = switch (trend) {
            case "improving" -> "📈 Trend is positive — keep the momentum!";
            case "declining" -> "⚠️ Recent dip detected — refocus on fundamentals.";
            default          -> "➡️ Performance is stable — time to push to the next level.";
        };
        return String.format(
                "💪 STRENGTHS\n• %s holds Rank #%d with %d%% win rate — %s\n" +
                        "• %d MVP award(s) shows ability to peak when it counts\n" +
                        "• %s skill level means solid foundational technique is in place\n\n" +
                        "⚠️ AREAS TO IMPROVE\n• At %d%% win rate, there is clear room to push toward 70%%+\n" +
                        "• Serve variation: most club-level players under-use spin serves\n" +
                        "• Mental consistency between sets — staying composed after losing a point\n\n" +
                        "🏓 TACTICAL ADVICE\n• Use the short game more — push-push-attack is safer than open rallies\n" +
                        "• Against higher-ranked opponents, play longer rallies and wait for errors\n" +
                        "• In doubles: communicate who takes the middle ball before each point\n\n" +
                        "🎯 THIS WEEK'S GOALS\n• Achieve %d%% win rate across the next 2 sessions\n" +
                        "• Attempt a new serve variation in every single match\n\n" +
                        "🔮 RANK PREDICTION\n• With consistent attendance, Rank #%d is achievable in 5 sessions\n• %s",
                name, rank, winRate,
                winRate >= 60 ? "solid competitive performance!" : "plenty of upside remaining",
                mvps, prof, winRate,
                Math.min(winRate + 10, 75),
                Math.max(1, rank - 2), trendLine
        );
    }
}
