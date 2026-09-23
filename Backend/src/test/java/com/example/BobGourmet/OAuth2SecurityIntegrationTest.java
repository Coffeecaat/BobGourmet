package com.example.BobGourmet;

import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Service.Auth.OAuth2UserService;
import com.example.BobGourmet.Service.Room.MatchroomService;
import com.example.BobGourmet.config.AuthSecurityTestConfiguration;
import com.example.BobGourmet.utils.JwtProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;


import java.util.Optional;

import static com.example.BobGourmet.config.AuthSecurityTestConfiguration.PROTECTED_PATH;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "cors.allowed-origins=http://localhost:5173",
        "oauth.frontend.base-url=http://localhost:5173",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Import(AuthSecurityTestConfiguration.class)
public class OAuth2SecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private OAuth2UserService oAuth2UserService;

    // Exclude the room scheduler's Redis access from this security-only test.
    @MockitoBean
    private MatchroomService matchroomService;

    @Autowired
    private JwtProvider jwtProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("OAuth2 로그인 엔드포인트 접근 가능")
    void testOAuth2LoginEndpoint_Accessible() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        startsWith("https://accounts.google.com/o/oauth2/v2/auth?")))
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("client_id=test-client-id")))
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("state=")))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("보호된 리소스 - 인증 없이 접근 시 리다이렉트")
    void testProtectedResource_WithoutAuth_RedirectsToLogin() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());
        verifyNoInteractions(userRepository, oAuth2UserService);
    }

    @Test
    @DisplayName("이미 인증된 OAuth2 사용자의 보호된 리소스 접근 허용")
    void testProtectedResource_WithOAuth2Login_Success() throws Exception {
        // oauth2Login supplies an authenticated principal; it does not exercise Google's callback.
        mockMvc.perform(get(PROTECTED_PATH)
                        .with(oauth2Login()
                                .attributes(attrs -> {
                                    attrs.put("sub", "google123");
                                    attrs.put("email", "test@example.com");
                                    attrs.put("name", "Test User");
                                })
                        ))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername("google123"))
                .andExpect(jsonPath("$.name").value("google123"))
                .andExpect(jsonPath("$.authenticated").value(true));
        verifyNoInteractions(userRepository, oAuth2UserService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/login", "/api/auth/register"})
    @DisplayName("공개 POST 엔드포인트의 GET 요청은 인증 리다이렉트 대신 405 반환")
    void testPublicEndpoints_RejectUnsupportedMethod(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(unauthenticated());
        verifyNoInteractions(userRepository, oAuth2UserService);
    }

    @Test
    @DisplayName("WebSocket 엔드포인트는 인증 없이 접근 가능")
    void testWebSocketEndpoint_AccessibleWithoutAuth() throws Exception {
        // WebSocket handshake는 별도의 테스트가 필요하지만, 기본적으로 permitAll 확인
        mockMvc.perform(get("/ws-BobGourmet/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("JWT 쿠키로 보호된 리소스 접근 성공")
    void testProtectedResource_WithJwtCookie_Success() throws Exception {
        // Given
        User mockUser = new User("testuser", "test@example.com", "Test User", "local", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        
        String token = jwtProvider.generateToken("testuser");

        // When & Then - Use cookie instead of Authorization header
        mockMvc.perform(get(PROTECTED_PATH)
                        .cookie(new Cookie("jwt-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("testuser"))
                .andExpect(jsonPath("$.authenticated").value(true));
        verify(userRepository).findByUsername("testuser");
        verifyNoInteractions(oAuth2UserService);
    }

    @Test
    @DisplayName("잘못된 JWT 쿠키로 접근 시 인증 실패")
    void testProtectedResource_WithInvalidCookie_AuthenticationFailure() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH)
                        .cookie(new Cookie("jwt-token", "invalid-token")))
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());
        verifyNoInteractions(userRepository, oAuth2UserService);
    }

    @Test
    @DisplayName("허용된 Origin의 CORS 사전 요청 허용")
    void testCorsHeaders_Present() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Content-Type")));
    }

    @Test
    @DisplayName("허용되지 않은 Origin의 CORS 사전 요청 거절")
    void testCorsHeaders_UntrustedOriginRejected() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        verifyNoInteractions(userRepository, oAuth2UserService);
    }

    @Test
    @DisplayName("명시적으로 활성화된 Swagger UI는 인증 없이 접근 가능")
    void testSwaggerUI_AccessibleWhenEnabled() throws Exception {
        // This tests permitAll, not a deployment policy limiting Swagger to a profile.
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("swagger-ui")))
                .andExpect(unauthenticated());
    }
}
