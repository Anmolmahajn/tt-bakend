package com.tt.service;

import com.tt.dto.DTOs;
import com.tt.model.Player;
import com.tt.repository.PlayerRepository;
import com.tt.security.JwtUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final PlayerRepository playerRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthService(PlayerRepository playerRepo, PasswordEncoder passwordEncoder, JwtUtils jwtUtils) {
        this.playerRepo = playerRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    @Transactional
    public DTOs.AuthResponse register(DTOs.RegisterRequest req) {
        if (playerRepo.existsByEmail(req.getEmail()))
            throw new RuntimeException("Email already registered");
        if (playerRepo.existsByUsername(req.getUsername()))
            throw new RuntimeException("Username already taken");
        Player p = new Player();
        p.setUsername(req.getUsername().toLowerCase().trim());
        p.setEmail(req.getEmail().toLowerCase().trim());
        p.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        p.setDisplayName(req.getDisplayName() != null ? req.getDisplayName() : req.getUsername());

        // NEW: save proficiency from registration form
        if (req.getProficiency() != null && !req.getProficiency().isBlank())
            p.setProficiency(req.getProficiency());

        p = playerRepo.save(p);
        String token = jwtUtils.generateToken(p.getId(), p.getUsername());

        // NEW: include proficiency in response so frontend can display it
        return new DTOs.AuthResponse(token, p.getUsername(), p.getDisplayName(),
                p.getEmail(), p.getId(), p.getProficiency());
    }

    public DTOs.AuthResponse login(DTOs.LoginRequest req) {
        Player p = playerRepo.findByEmail(req.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));
        if (!passwordEncoder.matches(req.getPassword(), p.getPasswordHash()))
            throw new RuntimeException("Invalid email or password");
        String token = jwtUtils.generateToken(p.getId(), p.getUsername());

        // NEW: include proficiency in login response
        return new DTOs.AuthResponse(token, p.getUsername(), p.getDisplayName(),
                p.getEmail(), p.getId(), p.getProficiency());
    }

    public Player getPlayer(String principalName) {
        Long id = Long.parseLong(principalName);
        return playerRepo.findById(id).orElseThrow(() -> new RuntimeException("Player not found"));
    }
}
