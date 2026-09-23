package com.example.BobGourmet.config;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@TestConfiguration(proxyBeanMethods = false)
@Import(AuthSecurityTestConfiguration.AuthenticationProbeController.class)
public class AuthSecurityTestConfiguration {
    public static final String PROTECTED_PATH = "/api/test/authentication";

    // Imported only by authentication tests, never discovered by application scanning.
    @TestComponent
    @RestController
    static class AuthenticationProbeController {
        @GetMapping(PROTECTED_PATH)
        Map<String, Object> principal(Authentication authentication) {
            return Map.of("name", authentication.getName(), "authenticated", authentication.isAuthenticated());
        }
    }
}
