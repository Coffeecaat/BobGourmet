package com.example.BobGourmet.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
@Slf4j
public class JwtProvider {

    private final SecretKey key;
    private final long jwtExpirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        this.jwtExpirationMs =expirationMs;
    }

    public String generateToken(String username){
        Map<String, Object> claims = new HashMap<>();
        return doGenerateToken(claims, username);
    }

    public String doGenerateToken(Map<String,Object> claims, String subject){
        return Jwts.builder()
                .claims(claims)
                .subject(subject) // 사용자 식별값(아이디)
                .issuedAt(new Date(System.currentTimeMillis())) // 토큰 발급 시간
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs)) // 만료 시간
                .signWith(key) // 서명
                .compact();
    }


    public boolean validateToken(String token){
        try{
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException e){
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
    public boolean validateToken(String token, String username){
        final String usernameFromToken = getUsernameFromToken(token);
        return (usernameFromToken.equals(username) && !isTokenExpired(token));
    }

    public String getUsernameFromToken(String token){
        return getClaimFromToken(token, Claims::getSubject);
    }

    private Boolean isTokenExpired(String token){
        try {
            final Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());
        }catch(JwtException e){
            log.error("Token expiration check failed: {}", e.getMessage());
            return true; // considering given token as expired
        }
    }

    public Date getExpirationDateFromToken(String token){
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver){
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token){
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        }catch(JwtException e){
            log.error("Failed to parse JWT claims: {}", e.getMessage());
            throw e; // let upper methods to take care
        }
    }

}
