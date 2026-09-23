package com.example.BobGourmet.Service.Email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmailDomainValidationService {

    private final Set<String> allowedDomains;

    public EmailDomainValidationService(
            @Value("${app.email.allowed-domains:gmail.com,naver.com,daum.net,kakao.com,nate.com,hanmail.net,yahoo.com,hotmail.com,outlook.com}") 
            String allowedDomainsStr) {
        
        this.allowedDomains = Arrays.stream(allowedDomainsStr.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        
        log.info("Email domain validation initialized with allowed domains: {}", allowedDomains);
    }

    /**
     * Validates if the email domain is allowed
     */
    public boolean isEmailDomainAllowed(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        String domain = extractDomain(email);
        if (domain == null) {
            return false;
        }

        boolean isAllowed = allowedDomains.contains(domain.toLowerCase());
        
        if (!isAllowed) {
            log.warn("Email domain '{}' is not in the allowed domains list", domain);
        }
        
        return isAllowed;
    }

    /**
     * Gets the list of allowed domains for display purposes
     */
    public Set<String> getAllowedDomains() {
        return Set.copyOf(allowedDomains);
    }

    /**
     * Validates email and throws exception with helpful message if invalid
     */
    public void validateEmailDomain(String email) {
        if (!isEmailDomainAllowed(email)) {
            String domain = extractDomain(email);
            String message = String.format(
                "이메일 도메인 '%s'는 허용되지 않습니다. 허용된 도메인: %s | " +
                "Email domain '%s' is not allowed. Allowed domains: %s",
                domain != null ? domain : "invalid",
                String.join(", ", allowedDomains),
                domain != null ? domain : "invalid", 
                String.join(", ", allowedDomains)
            );
            throw new RuntimeException(message);
        }
    }

    /**
     * Extracts domain from email address
     */
    private String extractDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return null;
        }
        
        return parts[1].trim();
    }
}