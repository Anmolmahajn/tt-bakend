package com.tt.security;

import com.tt.model.Player;
import com.tt.repository.PlayerRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final PlayerRepository playerRepo;
    private final JwtUtils jwtUtils;

    public OAuth2SuccessHandler(PlayerRepository playerRepo, JwtUtils jwtUtils) {
        this.playerRepo = playerRepo;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        // Extract email — all three providers expose it
        String email = oauth2User.getAttribute("email");
        if (email == null) {
            // GitHub sometimes hides email — fall back to login
            String login = oauth2User.getAttribute("login");
            email = (login != null ? login : "user") + "@github.noemail";
        }
        email = email.toLowerCase().trim();

        String name = oauth2User.getAttribute("name");
        if (name == null || name.isBlank()) name = email.split("@")[0];

        final String finalEmail = email;
        final String finalName  = name;

        // Find or auto-create player
        Player player = playerRepo.findByEmail(finalEmail).orElseGet(() -> {
            Player p = new Player();
            p.setEmail(finalEmail);
            // Generate a unique username: first part of email + random suffix
            String base = finalEmail.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
            String username = base;
            int suffix = 1;
            while (playerRepo.existsByUsername(username)) {
                username = base + suffix++;
            }
            p.setUsername(username);
            p.setDisplayName(finalName);
            p.setPasswordHash(""); // OAuth users have no password
            p.setProficiency("Intermediate");
            return playerRepo.save(p);
        });

        String token = jwtUtils.generateToken(player.getId(), player.getUsername());

        // Redirect back to the mobile app via deep link
        String redirectUrl = "ttplatform://auth"
                + "?token=" + encode(token)
                + "&userId=" + player.getId()
                + "&username=" + encode(player.getUsername())
                + "&email=" + encode(player.getEmail())
                + "&displayName=" + encode(player.getDisplayName())
                + "&proficiency=" + encode(player.getProficiency() != null ? player.getProficiency() : "Intermediate");

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private String encode(String s) {
        return URLEncoder.encode(s != null ? s : "", StandardCharsets.UTF_8);
    }
}
