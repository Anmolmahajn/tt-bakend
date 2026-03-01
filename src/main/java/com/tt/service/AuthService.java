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
    private final EmailService emailService;

    public AuthService(PlayerRepository playerRepo, PasswordEncoder passwordEncoder,
                       JwtUtils jwtUtils, EmailService emailService) {
        this.playerRepo = playerRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.emailService = emailService;
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

        if (req.getProficiency() != null && !req.getProficiency().isBlank())
            p.setProficiency(req.getProficiency());

        p = playerRepo.save(p);
        String token = jwtUtils.generateToken(p.getId(), p.getUsername());
        return new DTOs.AuthResponse(token, p.getUsername(), p.getDisplayName(),
                p.getEmail(), p.getId(), p.getProficiency());
    }

    public DTOs.AuthResponse login(DTOs.LoginRequest req) {
        Player p = playerRepo.findByEmail(req.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));
        // OAuth users have empty passwordHash — don't allow password login for them
        if (p.getPasswordHash() == null || p.getPasswordHash().isBlank())
            throw new RuntimeException("This account uses social login. Please sign in with Google/GitHub/LinkedIn.");
        if (!passwordEncoder.matches(req.getPassword(), p.getPasswordHash()))
            throw new RuntimeException("Invalid email or password");
        String token = jwtUtils.generateToken(p.getId(), p.getUsername());
        return new DTOs.AuthResponse(token, p.getUsername(), p.getDisplayName(),
                p.getEmail(), p.getId(), p.getProficiency());
    }

    // ── Forgot Password — Step 1: send OTP ───────────────────────────────────

    public void sendForgotPasswordOtp(String email) {
        email = email.toLowerCase().trim();
        // Always return success even if email not found — prevents email enumeration
        if (!playerRepo.existsByEmail(email)) return;
        Player p = playerRepo.findByEmail(email).get();
        if (p.getPasswordHash() == null || p.getPasswordHash().isBlank())
            throw new RuntimeException("This account uses social login and has no password to reset.");
        emailService.generateAndSendOtp(email, "PASSWORD_RESET");
    }

    // ── Forgot Password — Step 2: verify OTP ─────────────────────────────────

    public boolean verifyForgotPasswordOtp(String email, String otp) {
        return emailService.verifyOtp(email.toLowerCase().trim(), otp);
    }

    // ── Forgot Password — Step 3: set new password ────────────────────────────

    @Transactional
    public DTOs.AuthResponse resetPassword(String email, String otp, String newPassword) {
        email = email.toLowerCase().trim();
        // Re-verify OTP one final time (verifyOtp consumes it, so we need a separate "confirmed" flow)
        // We use a second OTP store key with prefix "CONFIRMED_" set after step 2
        if (!emailService.verifyConfirmedReset(email))
            throw new RuntimeException("OTP not verified. Please complete the verification step first.");
        if (newPassword == null || newPassword.length() < 6)
            throw new RuntimeException("Password must be at least 6 characters.");
        Player p = playerRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Account not found."));
        p.setPasswordHash(passwordEncoder.encode(newPassword));
        playerRepo.save(p);
        String token = jwtUtils.generateToken(p.getId(), p.getUsername());
        return new DTOs.AuthResponse(token, p.getUsername(), p.getDisplayName(),
                p.getEmail(), p.getId(), p.getProficiency());
    }

    public Player getPlayer(String principalName) {
        Long id = Long.parseLong(principalName);
        return playerRepo.findById(id).orElseThrow(() -> new RuntimeException("Player not found"));
    }
}
