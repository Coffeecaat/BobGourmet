package com.example.BobGourmet.Service.Security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class IPTrackingService {

    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${app.security.ip-tracking.redis-key-prefix}")
    private String ipKeyPrefix;
    
    @Value("${app.security.ip-tracking.email-key-prefix}")
    private String emailKeyPrefix;
    
    @Value("${app.security.ip-tracking.max-attempts-per-ip}")
    private int maxAttemptsPerIp;
    
    @Value("${app.security.ip-tracking.tracking-duration-hours}")
    private long trackingDurationHours;
    
    @Value("${app.security.ip-tracking.email-block-duration-days}")
    private long emailBlockDurationDays;
    
    /**
     * Check if IP address is allowed to signup (not exceeded limit)
     */
    public boolean isIPAllowedForSignup(String ipAddress) {
        String key = buildIPKey(ipAddress);
        String attempts = redisTemplate.opsForValue().get(key);
        
        if (attempts == null) {
            return true; // First attempt from this IP
        }
        
        int attemptCount = Integer.parseInt(attempts);
        
        if (attemptCount >= maxAttemptsPerIp) {
            log.warn("IP {} has exceeded signup limit: {} attempts (max: {})", 
                    hashIP(ipAddress), attemptCount, maxAttemptsPerIp);
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if email has already been used for signup
     * Includes TTL check for proper expiration handling
     */
    public boolean isEmailAlreadyUsed(String email) {
        String key = buildEmailKey(email);
        String exists = redisTemplate.opsForValue().get(key);
        
        if (exists == null) {
            return false;
        }
        
        // Check TTL - if key exists but has no expiration, it's problematic
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl <= 0) {
            log.warn("Email tracking key has no expiration, removing for email: {}", hashEmail(email));
            redisTemplate.delete(key);
            return false;
        }
        
        log.debug("Email {} is blocked, TTL: {} seconds", hashEmail(email), ttl);
        return true;
    }
    
    /**
     * Record abusive IP attempt when IP limit is exceeded
     */
    public void recordAbusiveIPAttempt(String ipAddress) {
        recordIPAttempt(ipAddress);
        log.warn("Recorded abusive IP attempt from: {}", hashIP(ipAddress));
    }
    
    /**
     * Record email reuse abuse attempt
     */
    public void recordEmailReuseAttempt(String email, String ipAddress) {
        recordIPAttempt(ipAddress);
        recordEmailUsage(email);
        log.warn("Recorded email reuse abuse attempt - Email: {} from IP: {}", 
                hashEmail(email), hashIP(ipAddress));
    }
    
    /**
     * Get remaining signup attempts for an IP(admin function as well)
     */
    public int getRemainingAttempts(String ipAddress) {
        String key = buildIPKey(ipAddress);
        String attempts = redisTemplate.opsForValue().get(key);
        
        if (attempts == null) {
            return maxAttemptsPerIp;
        }
        
        int usedAttempts = Integer.parseInt(attempts);
        return Math.max(0, maxAttemptsPerIp - usedAttempts);
    }
    
    /**
     * Clear tracking data for an IP (admin function)
     * This should be called from an admin endpoint
     */
    public void clearIPTracking(String ipAddress) {
        String key = buildIPKey(ipAddress);
        redisTemplate.delete(key);
        log.info("Admin cleared signup tracking for IP: {}", hashIP(ipAddress));
    }
    
    // Clear email blocking (admin function)
    public void clearEmailBlocking(String email) {
        String key = buildEmailKey(email);
        redisTemplate.delete(key);
        log.info("Admin cleared email blocking for: {}", hashEmail(email));
    }


    
    // Private helper methods
    
    // Record IP attempt using atomic increment to prevent race conditions
    private void recordIPAttempt(String ipAddress) {
        String key = buildIPKey(ipAddress);
        
        // Atomic increment - prevents race conditions
        Long attemptCount = redisTemplate.opsForValue().increment(key);
        
        // Set expiration only on first attempt
        if (attemptCount == 1)   {
            redisTemplate.expire(key, Duration.ofHours(trackingDurationHours));
        }
    }
    
    private void recordEmailUsage(String email) {
        String key = buildEmailKey(email);
        redisTemplate.opsForValue().set(key, "blocked", Duration.ofDays(emailBlockDurationDays));
    }
    
    // Build IP key with hashed IP for privacy
    private String buildIPKey(String ipAddress) {
        return ipKeyPrefix + hashIP(ipAddress);
    }
    
    // Build email key with hashed email for privacy
    private String buildEmailKey(String email) {
        return emailKeyPrefix + hashEmail(email);
    }
    
    // Hash IP address for privacy compliance (GDPR)
    private String hashIP(String ipAddress) {
        return hashString(ipAddress + ":ip");
    }
    
    // Hash email for privacy compliance (GDPR)
    private String hashEmail(String email) {
        return hashString(email.toLowerCase() + ":email");
    }
    
    // SHA-256 hash utility for privacy
    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString().substring(0, 16); // Use first 16 chars for shorter keys
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Critical security error: SHA-256 not available",e);
        }
    }

    // Extract and normalize client IP address from request. ForwardedHeaderFilter handles proxy headers automatically
    public String getClientIpAddress(HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        return normalizeIpAddress(ipAddress);
    }

    //Normalize IP address for consistent hashing (handles IPv6 compression)
    private String normalizeIpAddress(String ipAddress) {
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            return addr.getHostAddress(); // Returns normalized form
        } catch (UnknownHostException e) {
            log.warn("Failed to normalize IP address: {}, using as-is", ipAddress);
            return ipAddress; // Return as-is if normalization fails
        }
    }
}