package com.example.BobGourmet.Service.Email;

import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Service.Security.IPTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final RedisTemplate<String, String> redisTemplate;
    private final IPTrackingService ipTrackingService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final EmailDomainValidationService emailDomainValidationService;

    @Value("${app.email.pre-verification.token-key-prefix}")
    private String tokenKeyPrefix;
    
    @Value("${app.email.pre-verification.email-key-prefix}")
    private String emailKeyPrefix;
    
    @Value("${app.email.pre-verification.token-ttl-hours}")
    private long tokenTtlHours;
    
    @Value("${app.email.pre-verification.verified-ttl-hours}")
    private long verifiedTtlHours;

    /**
     * Generate and send email verification token
     */
    public void sendVerificationEmail(User user) {

        emailDomainValidationService.validateEmailDomain(user.getEmail());

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
     * Resend verification email for unverified user by username
     */
    public void resendVerificationByUsername(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found with username: " + username);
        }
        
        User user = userOpt.get();
        
        // Call existing resendVerificationEmail method with user's email
        resendVerificationEmail(user.getEmail());
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
    
    // ========== PRE-VERIFICATION METHODS ==========
    
    /**
     * Send pre-verification email for temporary email verification before signup
     */
    public void sendPreVerificationEmail(String email, String ipAddress) {
        // Check IP rate limit before sending email
        if (!ipTrackingService.isIPAllowedForSignup(ipAddress)) {
            ipTrackingService.recordAbusiveIPAttempt(ipAddress);
            throw new RuntimeException("Too many pre-verification requests from this IP. Please try again later.");
        }

        // Check if email was already used recently
        if (ipTrackingService.isEmailAlreadyUsed(email)) {
            ipTrackingService.recordEmailReuseAttempt(email, ipAddress);
            throw new RuntimeException("This email was recently used for verification. Please try again later.");
        }

        try {
            // Generate secure random token with collision detection
            String verificationToken = generateUniqueToken();
            String hashedEmail = hashEmail(email);

            // Store hashed email in Redis with TTL
            String tokenKey = tokenKeyPrefix + verificationToken;
            redisTemplate.opsForValue().set(tokenKey, hashedEmail, tokenTtlHours, TimeUnit.HOURS);

            // Send verification email
            emailService.sendPreVerificationEmail(email, verificationToken);

            // Record this attempt after successful email send
            ipTrackingService.recordAbusiveIPAttempt(ipAddress);

            log.info("Pre-verification email sent to: {}", hashedEmail);
        } catch (Exception e) {
            log.error("Failed to send pre-verification email to: {}", hashEmail(email), e);
            throw new RuntimeException("Failed to send verification email. Please try again later.", e);
        }
    }
    
    /**
     * Verify pre-verification token and mark email as pre-verified
     */
    public boolean verifyPreVerificationToken(String token) {
        try {
            String tokenKey = tokenKeyPrefix + token;
            String hashedEmail = redisTemplate.opsForValue().get(tokenKey);
            
            if (hashedEmail == null) {
                log.warn("Pre-verification failed: Invalid or expired token");
                return false;
            }
            
            // Mark email as pre-verified
            String emailKey = emailKeyPrefix + hashedEmail;
            redisTemplate.opsForValue().set(emailKey, "verified", verifiedTtlHours, TimeUnit.HOURS);
            
            // Delete the token as it's no longer needed
            redisTemplate.delete(tokenKey);
            
            log.info("Email pre-verified successfully: {}", hashedEmail);
            return true;
        } catch (Exception e) {
            log.error("Failed to verify pre-verification token", e);
            return false;
        }
    }
    
    /**
     * Check if email is pre-verified and ready for signup
     */
    public boolean isEmailPreVerified(String email) {
        try {
            String hashedEmail = hashEmail(email);
            String emailKey = emailKeyPrefix + hashedEmail;
            String verified = redisTemplate.opsForValue().get(emailKey);
            return "verified".equals(verified);
        } catch (Exception e) {
            log.error("Failed to check pre-verification status for email: {}", hashEmail(email), e);
            return false;
        }
    }
    
    /**
     * Remove pre-verification status after successful signup
     */
    public void clearPreVerification(String email) {
        try {
            String hashedEmail = hashEmail(email);
            String emailKey = emailKeyPrefix + hashedEmail;
            redisTemplate.delete(emailKey);
            log.debug("Cleared pre-verification for email: {}", hashedEmail);
        } catch (Exception e) {
            log.error("Failed to clear pre-verification for email: {}", hashEmail(email), e);
        }
    }
    
    /**
     * Generate unique token with collision detection
     */
    private String generateUniqueToken() {
        int maxAttempts = 5;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String token = generateVerificationToken();
            String tokenKey = tokenKeyPrefix + token;
            
            // Check if token already exists in Redis
            if (!redisTemplate.hasKey(tokenKey)) {
                return token;
            }
            
            log.warn("Token collision detected, generating new token (attempt {}/{})", attempt + 1, maxAttempts);
        }
        
        throw new RuntimeException("Failed to generate unique token after " + maxAttempts + " attempts");
    }
    
    /**
     * Hash email with SHA-256 + salt for privacy protection
     */
    private String hashEmail(String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Use a consistent salt based on email to ensure same hash for same email
            byte[] salt = (email + "pre_verification_salt").getBytes();
            digest.update(salt);
            
            byte[] hash = digest.digest(email.toLowerCase().trim().getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Hashing algorithm not available", e);
        }
    }
}