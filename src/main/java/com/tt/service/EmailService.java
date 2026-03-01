package com.tt.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    // In-memory OTP store: email -> {otp, expiry}
    // For production you could persist this in DB/Redis, but in-memory is fine for low traffic
    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    private static final int OTP_EXPIRY_MINUTES = 10;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ── Generate & send OTP ───────────────────────────────────────────────────

    public String generateAndSendOtp(String toEmail, String purpose) {
        String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        otpStore.put(toEmail.toLowerCase(), new OtpEntry(otp, LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES)));

        String subject, body;
        if ("PASSWORD_RESET".equals(purpose)) {
            subject = "🏓 TT Platform — Password Reset OTP";
            body = "Your password reset OTP is: " + otp + "\n\n"
                    + "This code expires in " + OTP_EXPIRY_MINUTES + " minutes.\n"
                    + "If you didn't request this, ignore this email.";
        } else {
            subject = "🏓 TT Platform — Verification Code";
            body = "Your verification code is: " + otp + "\n\n"
                    + "This code expires in " + OTP_EXPIRY_MINUTES + " minutes.";
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (Exception e) {
            // Log but don't expose internal mail errors to client
            System.err.println("[EmailService] Failed to send email to " + toEmail + ": " + e.getMessage());
            throw new RuntimeException("Failed to send email. Please check the address and try again.");
        }

        return otp; // returned for logging/testing; not sent to client
    }

    // ── Verify OTP ────────────────────────────────────────────────────────────

    public boolean verifyOtp(String email, String otp) {
        OtpEntry entry = otpStore.get(email.toLowerCase());
        if (entry == null) return false;
        if (LocalDateTime.now().isAfter(entry.expiry)) {
            otpStore.remove(email.toLowerCase());
            return false;
        }
        if (!entry.otp.equals(otp)) return false;
        otpStore.remove(email.toLowerCase()); // one-time use
        // Mark as confirmed so resetPassword can proceed
        otpStore.put("CONFIRMED_" + email.toLowerCase(),
                new OtpEntry("OK", LocalDateTime.now().plusMinutes(15)));
        return true;
    }

    // ── Check if OTP was verified (for final reset step) ─────────────────────

    public boolean verifyConfirmedReset(String email) {
        OtpEntry entry = otpStore.get("CONFIRMED_" + email.toLowerCase());
        if (entry == null) return false;
        if (LocalDateTime.now().isAfter(entry.expiry)) {
            otpStore.remove("CONFIRMED_" + email.toLowerCase());
            return false;
        }
        otpStore.remove("CONFIRMED_" + email.toLowerCase()); // consume
        return true;
    }

    // ── Inner record ─────────────────────────────────────────────────────────

    private static class OtpEntry {
        final String otp;
        final LocalDateTime expiry;
        OtpEntry(String otp, LocalDateTime expiry) { this.otp = otp; this.expiry = expiry; }
    }
}