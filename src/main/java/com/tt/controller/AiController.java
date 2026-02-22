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

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "").trim();
        if (prompt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Empty prompt"));

        try {
            String response = switch (provider.toLowerCase()) {
                case "groq"   -> callGroq(prompt);
                case "ollama" -> callOllama(prompt);
                case "gemini" -> callGemini(prompt);
                default       -> smartFallback(prompt);
            };
            return ResponseEntity.ok(Map.of("response", response));
        } catch (Exception e) {
            // Always return something useful — never show "AI unavailable"
            return ResponseEntity.ok(Map.of("response", smartFallback(prompt)));
        }
    }

    // ── GROQ (free tier, fast llama3) ─────────────────────────────────────────
    private String callGroq(String prompt) {
        if (apiKey == null || apiKey.isBlank()) return smartFallback(prompt);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Authorization", "Bearer " + apiKey);
        Map<String, Object> req = Map.of(
                "model", "llama3-8b-8192",
                "max_tokens", 300,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a table tennis coach assistant. Be concise, practical, encouraging."),
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

    // ── OLLAMA (local, free, private) ─────────────────────────────────────────
    private String callOllama(String prompt) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> req = Map.of(
                "model", "llama3",
                "prompt", "You are a table tennis coach assistant. Be concise. " + prompt,
                "stream", false
        );
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                ollamaUrl + "/api/generate",
                new HttpEntity<>(req, h), Map.class);
        return (String) resp.getBody().get("response");
    }

    // ── GOOGLE GEMINI (free tier) ──────────────────────────────────────────────
    private String callGemini(String prompt) {
        if (apiKey == null || apiKey.isBlank()) return smartFallback(prompt);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        Map<String,Object> part = Map.of("text", "Table tennis coach: " + prompt);
        Map<String,Object> content = Map.of("parts", List.of(part));
        Map<String,Object> req = Map.of("contents", List.of(content));
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
        ResponseEntity<Map> resp = restTemplate.postForEntity(url, new HttpEntity<>(req, h), Map.class);
        List<Map<String,Object>> candidates = (List<Map<String,Object>>) resp.getBody().get("candidates");
        Map<String,Object> c = (Map<String,Object>) candidates.get(0).get("content");
        List<Map<String,Object>> parts = (List<Map<String,Object>>) c.get("parts");
        return (String) parts.get(0).get("text");
    }

    // ── SMART FALLBACK — always works, no API needed ───────────────────────────
    private String smartFallback(String prompt) {
        String lower = prompt.toLowerCase();
        if (lower.contains("team") || lower.contains("split") || lower.contains("partner"))
            return "🏓 Team Tip: Balance by rank — pair Rank #1 with #4, and #2 with #3 for even competition. For 2v2: spread skill levels so each table has one strong and one developing player.";
        if (lower.contains("mvp") || lower.contains("best player") || lower.contains("top"))
            return "🏆 MVP Insight: The best player wins consistently across all matches, not just occasionally. Look for someone with 70%+ win ratio across multiple sessions.";
        if (lower.contains("rank") || lower.contains("improve") || lower.contains("better"))
            return "📈 Rank Tip: Win ratio per session matters most. Show up consistently — missing sessions hurts ranking more than losing matches.";
        if (lower.contains("coach") || lower.contains("tip") || lower.contains("advice"))
            return "💡 Coach Tip: In table tennis, reducing unforced errors improves your win ratio faster than learning new shots. Master consistency before aggression.";
        if (lower.contains("schedule") || lower.contains("match") || lower.contains("rest"))
            return "⏱ Schedule Tip: The scheduler ensures no player rests too long. Players with longest wait time are prioritised for the next match.";
        return "🤖 AI Analysis: Track your wins-per-match ratio across sessions — it's the most accurate measure of improvement in table tennis. Aim for consistent improvement over time.";
    }
}
