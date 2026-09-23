package com.example.BobGourmet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// Local HTTP boundary only: Spring still performs the code exchange, JWKS and OIDC validation.
public class FakeGoogleOidcProvider implements AutoCloseable {
    public enum Scenario {
        NORMAL, TOKEN_ERROR, USERINFO_ERROR, UNVERIFIED_EMAIL, BAD_SIGNATURE,
        BAD_AUDIENCE, BAD_ISSUER, EXPIRED_TOKEN, BAD_NONCE, SUBJECT_MISMATCH
    }

    private final HttpServer server;
    private final RSAKey signingKey;
    private final RSAKey wrongKey;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile Scenario scenario = Scenario.NORMAL;
    private volatile String nonce;
    public final AtomicInteger tokenRequests = new AtomicInteger();
    public final AtomicInteger userInfoRequests = new AtomicInteger();

    public FakeGoogleOidcProvider() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        wrongKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> respond(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString()));
        server.createContext("/token", this::token);
        server.createContext("/userinfo", this::userInfo);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reset() {
        scenario = Scenario.NORMAL;
        nonce = null;
        tokenRequests.set(0);
        userInfoRequests.set(0);
    }

    public void setScenario(Scenario scenario) {
        this.scenario = scenario;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    private void token(HttpExchange exchange) throws IOException {
        tokenRequests.incrementAndGet();
        String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!"POST".equals(exchange.getRequestMethod()) || !form.contains("code=test-code")
                || !form.contains("grant_type=authorization_code")) {
            respond(exchange, 400, "{\"error\":\"invalid_request\"}");
            return;
        }
        if (scenario == Scenario.TOKEN_ERROR) {
            respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(scenario == Scenario.BAD_ISSUER ? "https://untrusted.example" : baseUrl()).subject("google-sub")
                    .audience(scenario == Scenario.BAD_AUDIENCE ? "wrong-client" : "test-client")
                    .issueTime(Date.from(now.minusSeconds(3600)))
                    .expirationTime(Date.from(scenario == Scenario.EXPIRED_TOKEN ? now.minusSeconds(600) : now.plusSeconds(300)))
                    .claim("nonce", scenario == Scenario.BAD_NONCE ? "wrong-nonce" : nonce)
                    .claim("email", "alice@example.com")
                    .claim("email_verified", scenario != Scenario.UNVERIFIED_EMAIL)
                    .claim("name", "Alice Example").claim("given_name", "Alice").build();
            SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
            token.sign(new RSASSASigner(scenario == Scenario.BAD_SIGNATURE ? wrongKey : signingKey));
            respond(exchange, 200, mapper.writeValueAsString(Map.of("access_token", "test-access-token",
                    "token_type", "Bearer", "expires_in", 300, "scope", "openid profile email", "id_token", token.serialize())));
        } catch (Exception exception) {
            respond(exchange, 500, "{\"error\":\"server_error\"}");
        }
    }

    private void userInfo(HttpExchange exchange) throws IOException {
        userInfoRequests.incrementAndGet();
        if (scenario == Scenario.USERINFO_ERROR || !"Bearer test-access-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            respond(exchange, 401, "{\"error\":\"invalid_token\"}");
            return;
        }
        respond(exchange, 200, mapper.writeValueAsString(Map.of(
                "sub", scenario == Scenario.SUBJECT_MISMATCH ? "different-sub" : "google-sub",
                "email", "alice@example.com", "email_verified", scenario != Scenario.UNVERIFIED_EMAIL,
                "name", "Alice Example", "given_name", "Alice")));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (exchange) {
            exchange.getResponseBody().write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
