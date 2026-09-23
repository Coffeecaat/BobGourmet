package com.example.BobGourmet;

import com.example.BobGourmet.DTO.AuthDTO.LoginRequest;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Service.Email.EmailVerificationService;
import com.example.BobGourmet.utils.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.http.Cookie;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
public class CookieAuthenticationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("로그인 성공 시 HttpOnly 쿠키 설정")
    void testLogin_Success_SetsHttpOnlyCookie() throws Exception {
        // Given
        String username = "testuser";
        String password = "password123";
        String email = "test@example.com";
        
        User mockUser = new User(username, email, passwordEncoder.encode(password), "Test User");
        mockUser.setEmailVerified(true); // Ensure email is verified
        
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(mockUser));
        when(emailVerificationService.isEmailVerified(any(User.class))).thenReturn(true);
        
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("jwt-token"))
                .andExpect(cookie().httpOnly("jwt-token", true))
                .andExpect(cookie().secure("jwt-token", true))
                .andExpect(cookie().path("jwt-token", "/"))
                .andExpect(cookie().maxAge("jwt-token", 24 * 60 * 60)) // 24 hours
                .andReturn();

        // Verify response body doesn't contain token
        String responseBody = result.getResponse().getContentAsString();
        assertTrue(responseBody.contains("\"accessToken\":null") || !responseBody.contains("accessToken"));
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인 시 쿠키 설정 안됨")
    void testLogin_WrongPassword_NoCookieSet() throws Exception {
        // Given
        String username = "testuser";
        String correctPassword = "password123";
        String wrongPassword = "wrongpassword";
        String email = "test@example.com";
        
        User mockUser = new User(username, email, passwordEncoder.encode(correctPassword), "Test User");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(mockUser));
        
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(wrongPassword);

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(cookie().doesNotExist("jwt-token"));
    }

    @Test
    @DisplayName("JWT 쿠키로 보호된 리소스 접근 성공")
    void testProtectedResource_WithValidCookie_Success() throws Exception {
        // Given
        String username = "testuser";
        String email = "test@example.com";
        String password = "password123";
        
        User mockUser = new User(username, email,password, "Test User");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(mockUser));
        
        String validToken = jwtProvider.generateToken(username);

        // When & Then
        mockMvc.perform(get("/api/MatchRooms")
                        .cookie(new Cookie("jwt-token", validToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("잘못된 JWT 쿠키로 보호된 리소스 접근 실패")
    void testProtectedResource_WithInvalidCookie_Failure() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/MatchRooms")
                        .cookie(new Cookie("jwt-token", "invalid-token")))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("JWT 쿠키 없이 보호된 리소스 접근 실패")
    void testProtectedResource_WithoutCookie_Failure() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/MatchRooms"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("로그아웃 시 쿠키 삭제")
    void testLogout_ClearsCookie() throws Exception {
        // Given
        String username = "testuser";
        String email = "test@example.com";
        String password = "password123";
        
        User mockUser = new User(username, email,password, "Test User");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(mockUser));
        
        String validToken = jwtProvider.generateToken(username);

        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("jwt-token", validToken)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("jwt-token"))
                .andExpect(cookie().value("jwt-token", "")) // Empty value
                .andExpect(cookie().maxAge("jwt-token", 0)); // Expired immediately
    }

    @Test
    @DisplayName("이메일 인증되지 않은 사용자 로그인 실패")
    void testLogin_UnverifiedEmail_Failure() throws Exception {
        // Given
        String username = "testuser";
        String password = "password123";
        String email = "test@example.com";
        
        User mockUser = new User(username, email, passwordEncoder.encode(password), "Test User");
        mockUser.setEmailVerified(false); // Email not verified
        
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(mockUser));
        when(emailVerificationService.isEmailVerified(any(User.class))).thenReturn(false);
        
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(cookie().doesNotExist("jwt-token"));
    }
}