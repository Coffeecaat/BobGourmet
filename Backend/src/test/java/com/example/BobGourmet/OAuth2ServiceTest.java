package com.example.BobGourmet;

import com.example.BobGourmet.DTO.AuthDTO.GoogleUserInfo;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Exception.UserAlreadyExistsException;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Service.Auth.OAuth2UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import com.example.BobGourmet.Exception.OAuth2Exception;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OAuth2ServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OAuth2UserService oAuth2UserService;

    @Test
    @DisplayName("OAuth2UserService.findOrCreateUser - 새 사용자 생성")
    void testFindOrCreateUser_NewUser_Success() {
        // Given
        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder().emailVerified(true)
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
        
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = oAuth2UserService.findOrCreateUser(googleUserInfo);

        // Then
        assertNotNull(result);
        assertEquals("new", result.getUsername());
        assertEquals("newuser@example.com", result.getEmail());
        assertEquals("New", result.getNickname());
        assertEquals("google", result.getOauthProvider());
        assertEquals("google123", result.getOauthId());
        
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertSame(result, savedUser.getValue());
        assertTrue(savedUser.getValue().isEmailVerified());
        assertNull(savedUser.getValue().getPassword());
    }

    @Test
    @DisplayName("OAuth2UserService.findOrCreateUser - 기존 구글 사용자 반환")
    void testFindOrCreateUser_ExistingGoogleUser_Success() {
        // Given
        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder().emailVerified(true)
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
        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder().emailVerified(true)
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
        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder().emailVerified(true)
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
        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder().emailVerified(true)
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
        
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = oAuth2UserService.findOrCreateUser(googleUserInfo);

        // Then
        assertNotNull(result);
        assertEquals("test1", result.getUsername());
        
        verify(userRepository).existsByUsername("test");
        verify(userRepository).existsByUsername("test1");
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals("test1", savedUser.getValue().getUsername());
        assertSame(result, savedUser.getValue());
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
        GoogleUserInfo userInfo = GoogleUserInfo.builder().emailVerified(true)
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

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void rejectsUnverifiedOrMissingVerificationBeforeRepositoryAccess(Boolean verified) {
        GoogleUserInfo info = GoogleUserInfo.builder().sub("google123").email("test@example.com")
                .emailVerified(verified).build();
        assertThrows(OAuth2Exception.class, () -> oAuth2UserService.findOrCreateUser(info));
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsMissingSubject(String subject) {
        GoogleUserInfo info = GoogleUserInfo.builder().sub(subject).email("test@example.com")
                .emailVerified(true).build();
        assertThrows(OAuth2Exception.class, () -> oAuth2UserService.findOrCreateUser(info));
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsMissingEmail(String email) {
        GoogleUserInfo info = GoogleUserInfo.builder().sub("google123").email(email)
                .emailVerified(true).build();
        assertThrows(OAuth2Exception.class, () -> oAuth2UserService.findOrCreateUser(info));
        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsSameEmailWithDifferentGoogleSubject() {
        GoogleUserInfo info = GoogleUserInfo.builder().sub("new-google-id").email("same@example.com")
                .emailVerified(true).build();
        when(userRepository.findByEmail("same@example.com")).thenReturn(Optional.of(
                new User("existing", "same@example.com", "Existing", "google", "old-google-id")));
        assertThrows(UserAlreadyExistsException.class, () -> oAuth2UserService.findOrCreateUser(info));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void acceptsExistingSubjectEvenWhenGoogleEmailChangesWithoutLinkingAnotherAccount() {
        GoogleUserInfo info = GoogleUserInfo.builder().sub("google123").email("changed@example.com")
                .emailVerified(true).build();
        User existing = new User("existing", "original@example.com", "Existing", "google", "google123");
        when(userRepository.findByOauthProviderAndOauthId("google", "google123")).thenReturn(Optional.of(existing));
        assertSame(existing, oAuth2UserService.findOrCreateUser(info));
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
}
