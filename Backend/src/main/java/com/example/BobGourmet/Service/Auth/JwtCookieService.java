package com.example.BobGourmet.Service.Auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtCookieService {
    private final int maxAgeSeconds;

    public JwtCookieService(@Value("${jwt.expiration}") long expirationMillis) {
        this.maxAgeSeconds = Math.toIntExact(expirationMillis / 1000);
    }

    public void issue(HttpServletResponse response, String token) {
        write(response, token, maxAgeSeconds);
    }

    public void clear(HttpServletResponse response) {
        write(response, "", 0);
    }

    private void write(HttpServletResponse response, String value, int maxAge) {
        Cookie cookie = new Cookie("jwt-token", value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        // Preserve the existing same-site/proxy deployment contract for local and Google login.
        cookie.setAttribute("SameSite", "Strict");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }
}
