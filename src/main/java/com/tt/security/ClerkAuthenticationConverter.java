package com.tt.security;

import com.tt.model.Player;
import com.tt.repository.PlayerRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Runs once Spring's resource-server filter has already verified the Clerk
 * JWT's signature/expiry/issuer against Clerk's JWKS. Its only job is to map
 * the token's `sub` (Clerk user id) to a local Player, auto-creating one on
 * first sign-in — the equivalent of syncUser.js's "find or create" flow.
 *
 * The resulting principal's username is the numeric Player id (as a String),
 * matching the shape MainController's `me(UserDetails ud)` already expects,
 * so no controller code has to change.
 */
@Component
public class ClerkAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final PlayerRepository playerRepo;
    private final ClerkUserService clerkUserService;

    public ClerkAuthenticationConverter(PlayerRepository playerRepo, ClerkUserService clerkUserService) {
        this.playerRepo = playerRepo;
        this.clerkUserService = clerkUserService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String clerkId = jwt.getSubject();

        Player player = playerRepo.findByClerkId(clerkId)
                .orElseGet(() -> provisionPlayer(clerkId));

        User principal = new User(
                String.valueOf(player.getId()),
                "",
                List.of(new SimpleGrantedAuthority("ROLE_PLAYER"))
        );

        return new UsernamePasswordAuthenticationToken(principal, jwt, principal.getAuthorities());
    }

    @Transactional
    protected Player provisionPlayer(String clerkId) {
        // Double-check inside the transaction in case of a race on first sign-in
        return playerRepo.findByClerkId(clerkId).orElseGet(() -> {
            ClerkUserService.ClerkProfile profile = clerkUserService.fetchProfile(clerkId);

            String email = profile.email() != null ? profile.email() : clerkId + "@no-email.clerk";
            String displayName = ((profile.firstName() != null ? profile.firstName() : "") + " "
                    + (profile.lastName() != null ? profile.lastName() : "")).trim();
            if (displayName.isBlank()) displayName = email.split("@")[0];

            String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
            if (base.isBlank()) base = "player";
            String username = base;
            int suffix = 1;
            while (playerRepo.existsByUsername(username)) {
                username = base + suffix++;
            }

            Player p = new Player();
            p.setClerkId(clerkId);
            p.setEmail(email);
            p.setUsername(username);
            p.setDisplayName(displayName);
            p.setAvatarUrl(profile.imageUrl());
            p.setProficiency("Intermediate");
            return playerRepo.save(p);
        });
    }
}
