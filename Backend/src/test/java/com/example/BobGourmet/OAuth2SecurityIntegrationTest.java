package com.example.BobGourmet;

import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Service.OAuth2UserService;
import com.example.BobGourmet.utils.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
public class OAuth2SecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private OAuth2UserService oAuth2UserService;

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
    @DisplayName("OAuth2 로그인 엔드포인트 접근 가능")
    void testOAuth2LoginEndpoint_Accessible() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().exists("Location"));
    }

    @Test
    @DisplayName("보호된 리소스 - 인증 없이 접근 시 리다이렉트")
    void testProtectedResource_WithoutAuth_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/MatchRooms/test"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("OAuth2 로그인으로 보호된 리소스 접근 성공")
    void testProtectedResource_WithOAuth2Login_Success() throws Exception {
        // Given
        User mockUser = new User("testuser", "test@example.com", "Test User", "google", "google123");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(mockUser));

        // When & Then
        mockMvc.perform(get("/api/MatchRooms/test")
                        .with(oauth2Login()
                                .attributes(attrs -> {
                                    attrs.put("sub", "google123");
                                    attrs.put("email", "test@example.com");
                                    attrs.put("name", "Test User");
                                })
                        ))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("공개 엔드포인트는 인증 없이 접근 가능")
    void testPublicEndpoints_AccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed()); // POST만 허용되므로 405

        mockMvc.perform(get("/api/auth/register"))
                .andExpect(status().isMethodNotAllowed()); // POST만 허용되므로 405
    }

    @Test
    @DisplayName("WebSocket 엔드포인트는 인증 없이 접근 가능")
    void testWebSocketEndpoint_AccessibleWithoutAuth() throws Exception {
        // WebSocket handshake는 별도의 테스트가 필요하지만, 기본적으로 permitAll 확인
        mockMvc.perform(get("/ws-BobGourmet/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("JWT 토큰으로 보호된 리소스 접근 성공")
    @WithMockUser(username = "testuser")
    void testProtectedResource_WithJwtToken_Success() throws Exception {
        // Given
        User mockUser = new User("testuser", "test@example.com", "Test User", "local", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        
        String token = jwtProvider.generateToken("testuser");

        // When & Then
        mockMvc.perform(get("/api/MatchRooms/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("잘못된 JWT 토큰으로 접근 시 인증 실패")
    void testProtectedResource_WithInvalidToken_AuthenticationFailure() throws Exception {
        mockMvc.perform(get("/api/MatchRooms/test")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("CORS 헤더 확인")
    void testCorsHeaders_Present() throws Exception {
        mockMvc.perform(get("/api/auth/login")
                        .header("Origin", "https://bobgourmet-frontend-j5uigawfda-du.a.run.app"))
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Swagger UI는 개발 환경에서만 접근 가능")
    void testSwaggerUI_NotAccessibleInTest() throws Exception {
        // test 프로필에서는 Swagger가 비활성화되어야 함
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
    }
}