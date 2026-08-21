package com.tt.service;

import com.tt.model.Player;
import com.tt.repository.PlayerRepository;
import org.springframework.stereotype.Service;

/**
 * Registration, login, and password reset are now handled entirely by
 * Clerk (see ClerkAuthenticationConverter for auto-provisioning). This
 * service just resolves the authenticated principal back to a Player.
 */
@Service
public class AuthService {

    private final PlayerRepository playerRepo;

    public AuthService(PlayerRepository playerRepo) {
        this.playerRepo = playerRepo;
    }

    public Player getPlayer(String principalName) {
        Long id = Long.parseLong(principalName);
        return playerRepo.findById(id).orElseThrow(() -> new RuntimeException("Player not found"));
    }
}
