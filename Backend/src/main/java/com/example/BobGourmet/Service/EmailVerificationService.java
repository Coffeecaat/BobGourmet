package com.example.BobGourmet.Service;

import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate and send email verification token
     */
    public void sendVerificationEmail(User user) {
        // Generate secure random token
        String verificationToken = generateVerificationToken();
        
        // Set token expiry (10 minutes from now - matching your email template)
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(10);
        
        // Update user with verification token
        user.setVerificationToken(verificationToken);
        user.setVerificationTokenExpiresAt(expiryTime);
        userRepository.save(user);
        
        // Send verification email
        emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), verificationToken);
        
        log.info("Verification email sent to user: {} ({})", user.getUsername(), user.getEmail());
    }

    /**
     * Verify email using token
     */
    public boolean verifyEmail(String token) {
        Optional<User> userOpt = userRepository.findByVerificationToken(token);
        
        if (userOpt.isEmpty()) {
            log.warn("Email verification failed: Invalid token");
            return false;
        }
        
        User user = userOpt.get();
        
        // Check if token has expired
        if (user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Email verification failed: Token expired for user {}", user.getUsername());
            return false;
        }
        
        // Verify email
        user.setEmailVerified(true);
        user.setVerificationToken(null); // Clear the token
        user.setVerificationTokenExpiresAt(null);
        userRepository.save(user);
        
        // Send welcome email
        emailService.sendWelcomeEmail(user.getEmail(), user.getUsername());
        
        log.info("Email verified successfully for user: {} ({})", user.getUsername(), user.getEmail());
        return true;
    }

    /**
     * Resend verification email for unverified user
     */
    public void resendVerificationEmail(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found with email: " + email);
        }
        
        User user = userOpt.get();
        
        if (user.isEmailVerified()) {
            throw new RuntimeException("Email is already verified");
        }
        
        sendVerificationEmail(user);
    }

    /**
     * Check if user has verified email and is allowed to login
     */
    public boolean isEmailVerified(User user) {
        // OAuth users are considered verified
        if (!"local".equals(user.getOauthProvider())) {
            return true;
        }
        
        // Local users must have verified email
        return user.isEmailVerified();
    }

    /**
     * Generate cryptographically secure verification token
     */
    private String generateVerificationToken() {
        byte[] tokenBytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * Clean up expired verification tokens (should be called periodically)
     */
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        userRepository.findByVerificationTokenExpiresAtBefore(now)
            .forEach(user -> {
                user.setVerificationToken(null);
                user.setVerificationTokenExpiresAt(null);
                userRepository.save(user);
                log.debug("Cleaned up expired token for user: {}", user.getUsername());
            });
    }
}