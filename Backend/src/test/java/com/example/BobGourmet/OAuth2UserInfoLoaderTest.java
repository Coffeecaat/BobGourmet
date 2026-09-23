package com.example.BobGourmet;

import com.example.BobGourmet.Service.Auth.GoogleOidcUserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import java.util.Map;
import java.util.List;
import java.util.Set;
import com.example.BobGourmet.DTO.AuthDTO.GoogleUserInfo;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Service.Auth.OAuth2UserService;
import com.example.BobGourmet.utils.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class OAuth2UserInfoLoaderTest {
    private static final String USER_INFO_URI = "https://provider.example/userinfo";

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private OAuth2UserService users;

    private GoogleOidcUserService loader;
    private OidcUserRequest request;
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        loader = new GoogleOidcUserService(users);
        DefaultOAuth2UserService userInfoLoader = new DefaultOAuth2UserService();
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        // Run the real loadUser method; replace only its external HTTP boundary.
        userInfoLoader.setRestOperations(restTemplate);
        loader.setOauth2UserService(userInfoLoader);
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app.example/login/oauth2/code/google")
                .authorizationUri("https://provider.example/authorize")
                .tokenUri("https://provider.example/token")
                .userInfoUri(USER_INFO_URI).userNameAttributeName("sub")
                .scope("openid", "profile", "email").build();
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "test-access-token", Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T01:00:00Z"), Set.of("openid", "profile", "email"));
        OidcIdToken idToken = new OidcIdToken("test-id-token", Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T01:00:00Z"),
                Map.of("sub", "google123", "iss", "https://provider.example", "aud", List.of("test-client")));
        request = new OidcUserRequest(registration, token, idToken);
    }

    @Test
    @DisplayName("실제 OAuth 사용자 정보 로더가 회원 서비스에 사용자 속성을 전달")
    void loadsProviderUserAndDelegatesUserCreation() {
        expectUserInfo();
        User savedUser = new User("test", "test@example.com", "Test", "google", "google123");
        when(users.findOrCreateUser(any(GoogleUserInfo.class))).thenReturn(savedUser);

        OAuth2User result = loader.loadUser(request);

        assertEquals("google123", result.getName());
        assertEquals("test@example.com", result.getAttribute("email"));
        assertEquals("Test User", result.getAttribute("name"));
        assertEquals("Test", result.getAttribute("given_name"));
        verifyUserInfoMapping();
        server.verify();
        verifyNoInteractions(userRepository, jwtProvider);
    }

    @Test
    @DisplayName("실제 OAuth 사용자 정보 로더가 회원 처리 예외를 인증 예외로 변환")
    void convertsUserCreationFailureToAuthenticationFailure() {
        expectUserInfo();
        RuntimeException failure = new IllegalStateException("Database error");
        when(users.findOrCreateUser(any(GoogleUserInfo.class))).thenThrow(failure);

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class,
                () -> loader.loadUser(request));

        assertEquals("user_creation_failed", exception.getError().getErrorCode());
        assertSame(failure, exception.getCause());
        verifyUserInfoMapping();
        server.verify();
        verifyNoInteractions(userRepository, jwtProvider);
    }

    private void expectUserInfo() {
        server.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-access-token"))
                .andRespond(withSuccess("""
                        {"sub":"google123","email":"test@example.com","name":"Test User","given_name":"Test","email_verified":true}
                        """, MediaType.APPLICATION_JSON));
    }

    private void verifyUserInfoMapping() {
        ArgumentCaptor<GoogleUserInfo> info = ArgumentCaptor.forClass(GoogleUserInfo.class);
        verify(users).findOrCreateUser(info.capture());
        assertEquals("google123", info.getValue().getSub());
        assertEquals("test@example.com", info.getValue().getEmail());
        assertTrue(info.getValue().getEmailVerified());
        assertEquals("Test User", info.getValue().getName());
        assertEquals("Test", info.getValue().getGivenName());
        verifyNoMoreInteractions(users);
    }
}
