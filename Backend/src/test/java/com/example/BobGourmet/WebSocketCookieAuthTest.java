package com.example.BobGourmet;

import com.example.BobGourmet.Config.CookieHandshakeInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for CookieHandshakeInterceptor
 * Tests the cookie extraction and validation logic without requiring full Spring context
 */
public class WebSocketCookieAuthTest {

    private CookieHandshakeInterceptor handshakeInterceptor;
    private WebSocketHandler mockHandler;
    private ServerHttpResponse mockResponse;

    @BeforeEach
    void setUp() {
        handshakeInterceptor = new CookieHandshakeInterceptor();
        mockHandler = mock(WebSocketHandler.class);
        mockResponse = mock(ServerHttpResponse.class);
    }

    @Test
    @DisplayName("유효한 JWT 쿠키로 WebSocket 핸드셰이크 성공")
    void testValidJwtCookieHandshake() throws Exception {
        // Given
        String validToken = "valid.jwt.token";

        // Create mock HTTP request with JWT cookie
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setCookies(new Cookie("jwt-token", validToken));
        
        ServerHttpRequest request = new ServletServerHttpRequest(mockRequest);
        Map<String, Object> attributes = new HashMap<>();

        // When
        boolean result = handshakeInterceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

        // Then
        assertTrue(result, "Handshake should succeed with valid JWT cookie");
        assertEquals(validToken, attributes.get("jwt-token"), "JWT token should be stored in session attributes");
    }

    @Test
    @DisplayName("잘못된 JWT 쿠키로 WebSocket 핸드셰이크")
    void testInvalidJwtCookieHandshake() throws Exception {
        // Given
        String invalidToken = "invalid-token";

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setCookies(new Cookie("jwt-token", invalidToken));
        
        ServerHttpRequest request = new ServletServerHttpRequest(mockRequest);
        Map<String, Object> attributes = new HashMap<>();

        // When
        boolean result = handshakeInterceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

        // Then
        assertTrue(result, "Handshake should succeed but token validation happens in channel interceptor");
        assertEquals(invalidToken, attributes.get("jwt-token"), "Invalid token should still be stored for later validation");
    }

    @Test
    @DisplayName("JWT 쿠키 없이 WebSocket 핸드셰이크 실패")
    void testMissingJwtCookieHandshake() throws Exception {
        // Given
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        // No cookies set
        
        ServerHttpRequest request = new ServletServerHttpRequest(mockRequest);
        Map<String, Object> attributes = new HashMap<>();

        // When
        boolean result = handshakeInterceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

        // Then
        assertFalse(result, "Handshake should fail without JWT cookie");
        assertNull(attributes.get("jwt-token"), "No JWT token should be stored");
    }

    @Test
    @DisplayName("만료된 JWT 쿠키로 WebSocket 핸드셰이크")
    void testExpiredJwtCookieHandshake() throws Exception {
        // Given
        // Use an obviously expired/invalid token format
        String expiredToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImlhdCI6MTAwMDAwMDAwMCwiZXhwIjoxMDAwMDAwMDAwfQ.invalid-signature";

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setCookies(new Cookie("jwt-token", expiredToken));
        
        ServerHttpRequest request = new ServletServerHttpRequest(mockRequest);
        Map<String, Object> attributes = new HashMap<>();

        // When
        boolean result = handshakeInterceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

        // Then
        assertTrue(result, "Handshake should succeed but token validation happens in channel interceptor");
        assertEquals(expiredToken, attributes.get("jwt-token"), "Expired token should be stored for later validation");
    }

    @Test
    @DisplayName("다른 이름의 쿠키는 무시됨")
    void testWrongCookieNameIgnored() throws Exception {
        // Given
        String validToken = "valid.jwt.token";

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setCookies(
            new Cookie("other-cookie", "some-value"),
            new Cookie("auth-token", validToken), // Wrong name
            new Cookie("session-id", "session123")
        );
        
        ServerHttpRequest request = new ServletServerHttpRequest(mockRequest);
        Map<String, Object> attributes = new HashMap<>();

        // When
        boolean result = handshakeInterceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

        // Then
        assertFalse(result, "Handshake should fail when JWT cookie name is wrong");
        assertNull(attributes.get("jwt-token"), "No JWT token should be stored");
    }

    @Test
    @DisplayName("여러 쿠키 중 올바른 JWT 쿠키 선택")
    void testCorrectCookieSelectedFromMultiple() throws Exception {
        // Given
        String validToken = "valid.jwt.token";

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setCookies(
            new Cookie("session-id", "session123"),
            new Cookie("jwt-token", validToken), // Correct one
            new Cookie("other-token", "other-value")
        );
        
        ServerHttpRequest request = new ServletServerHttpRequest(mockRequest);
        Map<String, Object> attributes = new HashMap<>();

        // When
        boolean result = handshakeInterceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

        // Then
        assertTrue(result, "Handshake should succeed with correct JWT cookie");
        assertEquals(validToken, attributes.get("jwt-token"), "Correct JWT token should be stored");
    }
}