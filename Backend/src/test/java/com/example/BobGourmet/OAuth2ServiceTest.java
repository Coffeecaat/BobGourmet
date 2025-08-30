package com.example.BobGourmet;

import com.example.BobGourmet.Config.SecurityConfig;
import com.example.BobGourmet.DTO.AuthDTO.GoogleUserInfo;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Exception.UserAlreadyExistsException;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Service.OAuth2UserService;
import com.example.BobGourmet.utils.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OAuth2ServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private OAuth2UserRequest userRequest;

    @InjectMocks
    private OAuth2UserService oAuth2UserService;

    private SecurityConfig securityConfig;
    private DefaultOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        // Create SecurityConfig with mocked dependencies
        securityConfig = new SecurityConfig(userRepository, jwtProvider, oAuth2UserService);
        customOAuth2UserService = securityConfig.customOAuth2UserService();
    }

    private OAuth2User createMockOAuth2User(String email, String googleId, String name, String givenName) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", email);
        attributes.put("sub", googleId);
        attributes.put("name", name);
        attributes.put("given_name", givenName);
        
        return new DefaultOAuth2User(
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            "sub"
        );
    }

    @Test
    @DisplayName("OAuth2UserService loadUser - 성공적인 새 사용자 생성")
    void testLoadUser_NewUserCreation_Success() throws Exception {
        // Given
        String email = "test@example.com";
        String googleId = "google123";
        String name = "Test User";
        String givenName = "Test";

        GoogleUserInfo expectedUserInfo = GoogleUserInfo.builder()
                .sub(googleId)
                .email(email)
                .name(name)
                .givenName(givenName)
                .build();

        User expectedUser = new User("test", email, "Test User", "google", googleId);
        
        // Mock OAuth2UserService behavior
        when(oAuth2UserService.findOrCreateUser(any(GoogleUserInfo.class))).thenReturn(expectedUser);

        // Mock the parent loadUser call by creating a spy
        DefaultOAuth2UserService spyService = spy(customOAuth2UserService);
        OAuth2User mockOAuth2User = createMockOAuth2User(email, googleId, name, givenName);
        doReturn(mockOAuth2User).when(spyService).loadUser(any(OAuth2UserRequest.class));

        // When
        OAuth2User result = spyService.loadUser(userRequest);

        // Then
        assertNotNull(result);
        assertEquals(email, result.getAttribute("email"));
        assertEquals(googleId, result.getAttribute("sub"));
        assertEquals(name, result.getAttribute("name"));
        assertEquals(givenName, result.getAttribute("given_name"));
        
        verify(oAuth2UserService, times(1)).findOrCreateUser(any(GoogleUserInfo.class));
    }

    @Test
    @DisplayName("OAuth2UserService.findOrCreateUser - 새 사용자 생성")
    void testFindOrCreateUser_NewUser_Success() {
        // Given
        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder()
                .sub("google123")
                .email("newuser@example.com")
                .name("New User")
                .givenName("New")
                .build();

        // Mock repository responses - no existing user
        when(userRepository.findByOauthProviderAndOauthId("google", "google123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("newuser@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString()))
                .thenReturn(false);
        
        User savedUser = new User("new", "newuser@example.com", "New User", "google", "google123");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // When
        User result = oAuth2UserService.findOrCreateUser(googleUserInfo);

        // Then
        assertNotNull(result);
        assertEquals("new", result.getUsername());
        assertEquals("newuser@example.com", result.getEmail());
        assertEquals("New User", result.getNickname());
        assertEquals("google", result.getOauthProvider());
        assertEquals("google123", result.getOauthId());
        
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("OAuth2UserService.findOrCreateUser - 기존 구글 사용자 반환")
    void testFindOrCreateUser_ExistingGoogleUser_Success() {
        // Given
        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder()
                .sub("google123")
                .email("existing@example.com")
                .name("Existing User")
                .build();

        User existingUser = new User("existing", "existing@example.com", "Existing User", "google", "google123");
        when(userRepository.findByOauthProviderAndOauthId("google", "google123"))
                .thenReturn(Optional.of(existingUser));

        // When
        User result = oAuth2UserService.findOrCreateUser(googleUserInfo);

        // Then
        assertNotNull(result);
        assertEquals(existingUser, result);
        assertEquals("existing", result.getUsername());
        assertEquals("google", result.getOauthProvider());
        
        // Should not create new user
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("OAuth2UserService.findOrCreateUser - 로컬 계정과 이메일 중복 시 예외")
    void testFindOrCreateUser_EmailConflictWithLocalAccount_ThrowsException() {
        // Given
        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder()
                .sub("google123")
                .email("conflict@example.com")
                .name("Conflict User")
                .build();

        // No Google OAuth user found
        when(userRepository.findByOauthProviderAndOauthId("google", "google123"))
                .thenReturn(Optional.empty());
        
        // But email exists with local account
        User localUser = new User("localuser", "conflict@example.com", "Local User", "local", null);
        when(userRepository.findByEmail("conflict@example.com"))
                .thenReturn(Optional.of(localUser));

        // When & Then
        assertThrows(UserAlreadyExistsException.class, () -> {
            oAuth2UserService.findOrCreateUser(googleUserInfo);
        });
        
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("OAuth2UserService.findOrCreateUser - 다른 OAuth 제공자와 이메일 중복 시 예외")
    void testFindOrCreateUser_EmailConflictWithOtherProvider_ThrowsException() {
        // Given
        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder()
                .sub("google123")
                .email("conflict@example.com")
                .name("Conflict User")
                .build();

        // No Google OAuth user found
        when(userRepository.findByOauthProviderAndOauthId("google", "google123"))
                .thenReturn(Optional.empty());
        
        // But email exists with different OAuth provider
        User facebookUser = new User("fbuser", "conflict@example.com", "Facebook User", "facebook", "fb123");
        when(userRepository.findByEmail("conflict@example.com"))
                .thenReturn(Optional.of(facebookUser));

        // When & Then
        assertThrows(UserAlreadyExistsException.class, () -> {
            oAuth2UserService.findOrCreateUser(googleUserInfo);
        });
        
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("OAuth2UserService.findOrCreateUser - 사용자명 중복 시 숫자 추가")
    void testFindOrCreateUser_UsernameConflict_AddsNumber() {
        // Given
        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder()
                .sub("google123")
                .email("test@example.com")
                .name("Test User")
                .givenName("Test")
                .build();

        // No existing OAuth or email user
        when(userRepository.findByOauthProviderAndOauthId("google", "google123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.empty());
        
        // Username conflicts
        when(userRepository.existsByUsername("test"))
                .thenReturn(true);
        when(userRepository.existsByUsername("test1"))
                .thenReturn(false);
        
        User savedUser = new User("test1", "test@example.com", "Test User", "google", "google123");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // When
        User result = oAuth2UserService.findOrCreateUser(googleUserInfo);

        // Then
        assertNotNull(result);
        assertEquals("test1", result.getUsername());
        
        verify(userRepository).existsByUsername("test");
        verify(userRepository).existsByUsername("test1");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("OAuth2UserService loadUser - 사용자 처리 중 예외 발생 시 OAuth2AuthenticationException")
    void testLoadUser_UserProcessingError_ThrowsOAuth2AuthenticationException() throws Exception {
        // Given
        String email = "error@example.com";
        String googleId = "google123";
        
        // Mock OAuth2UserService to throw exception
        when(oAuth2UserService.findOrCreateUser(any(GoogleUserInfo.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Mock the parent loadUser call
        DefaultOAuth2UserService spyService = spy(customOAuth2UserService);
        OAuth2User mockOAuth2User = createMockOAuth2User(email, googleId, "Test", "Test");
        doReturn(mockOAuth2User).when(spyService).loadUser(any(OAuth2UserRequest.class));

        // When & Then
        assertThrows(OAuth2AuthenticationException.class, () -> {
            spyService.loadUser(userRequest);
        });
        
        verify(oAuth2UserService, times(1)).findOrCreateUser(any(GoogleUserInfo.class));
    }

    @Test
    @DisplayName("GoogleUserInfo 빌더 패턴 테스트")
    void testGoogleUserInfo_Builder() {
        // Given
        String sub = "google123";
        String email = "test@example.com";
        String name = "Test User";
        String givenName = "Test";
        String familyName = "User";
        String picture = "https://example.com/picture.jpg";

        // When
        GoogleUserInfo userInfo = GoogleUserInfo.builder()
                .sub(sub)
                .email(email)
                .name(name)
                .givenName(givenName)
                .familyName(familyName)
                .picture(picture)
                .build();

        // Then
        assertNotNull(userInfo);
        assertEquals(sub, userInfo.getSub());
        assertEquals(email, userInfo.getEmail());
        assertEquals(name, userInfo.getName());
        assertEquals(givenName, userInfo.getGivenName());
        assertEquals(familyName, userInfo.getFamilyName());
        assertEquals(picture, userInfo.getPicture());
    }

    @Test
    @DisplayName("사용자 엔티티 OAuth 생성자 테스트")
    void testUser_OAuthConstructor() {
        // Given
        String username = "testuser";
        String email = "test@example.com";
        String nickname = "Test User";
        String oauthProvider = "google";
        String oauthId = "google123";

        // When
        User user = new User(username, email, nickname, oauthProvider, oauthId);

        // Then
        assertNotNull(user);
        assertEquals(username, user.getUsername());
        assertEquals(email, user.getEmail());
        assertEquals(nickname, user.getNickname());
        assertEquals(oauthProvider, user.getOauthProvider());
        assertEquals(oauthId, user.getOauthId());
        assertNull(user.getPassword()); // OAuth users should have null password
    }
}