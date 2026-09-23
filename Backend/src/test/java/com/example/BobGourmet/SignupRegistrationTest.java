package com.example.BobGourmet;

import com.example.BobGourmet.Controller.AuthController;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Exception.GlobalExceptionHandler;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Service.Auth.LoginService;
import com.example.BobGourmet.Service.Auth.SignupService;
import com.example.BobGourmet.Service.Email.EmailDomainValidationService;
import com.example.BobGourmet.Service.Email.EmailService;
import com.example.BobGourmet.Service.Email.EmailVerificationService;
import com.example.BobGourmet.Service.Security.IPTrackingService;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.spring6.fallback.FallbackDecorators;
import io.github.resilience4j.spring6.fallback.FallbackExecutor;
import io.github.resilience4j.spring6.ratelimiter.configure.RateLimiterAspect;
import io.github.resilience4j.spring6.ratelimiter.configure.RateLimiterConfigurationProperties;
import io.github.resilience4j.spring6.spelresolver.SpelResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SignupRegistrationTest {
    private static final String EMAIL = "alice@gmail.com";
    private static final String PAYLOAD = """
            {"username":"alice","email":"alice@gmail.com","password":"password123","nickname":"Alice"}
            """;

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final IPTrackingService ipTracking = mock(IPTrackingService.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> redis = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        when(redis.opsForValue()).thenReturn(values);
        when(ipTracking.getClientIpAddress(any())).thenReturn("127.0.0.1");
        when(ipTracking.isIPAllowedForSignup("127.0.0.1")).thenReturn(true);
        when(passwords.encode("password123")).thenReturn("encoded-password");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmailDomainValidationService domains = new EmailDomainValidationService("gmail.com");
        // Real verification and signup services; only external stores and collaborators are mocked.
        EmailVerificationService verification = new EmailVerificationService(
                users, mock(EmailService.class), redis, ipTracking, domains);
        ReflectionTestUtils.setField(verification, "emailKeyPrefix", "test:verified:");
        SignupService signup = new SignupService(users, passwords, verification, domains, ipTracking);
        AuthController controller = new AuthController(mock(LoginService.class), signup,
                verification, ipTracking);

        // Exercise the real @RateLimiter advice so domain failures cannot silently become HTTP 429.
        RateLimiterConfig config = RateLimiterConfig.custom().limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofDays(1)).timeoutDuration(Duration.ZERO).build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        SpelResolver resolver = (method, arguments, expression) -> expression;
        FallbackExecutor fallback = new FallbackExecutor(resolver, new FallbackDecorators(List.of()));
        RateLimiterAspect aspect = new RateLimiterAspect(registry,
                new RateLimiterConfigurationProperties(), List.of(), fallback, resolver);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(controller);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(aspect);
        AuthController proxiedController = proxyFactory.getProxy();
        mvc = MockMvcBuilders.standaloneSetup(proxiedController)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "pending"})
    void rejectsAbsentExpiredOrNonVerifiedStateBeforeSaving(String state) throws Exception {
        // Redis returns null for both a missing key and an expired key.
        when(values.get(anyString())).thenReturn(state);

        mvc.perform(registration(PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email Verification Required"))
                .andExpect(jsonPath("$.message").value("Please verify your email before signing up."));

        verifyNoInteractions(users);
        verify(passwords, never()).encode(anyString());
        verify(redis, never()).delete(anyString());
    }

    @Test
    void savesVerifiedUserOnceAndClearsProofOnlyAfterSuccessfulSave() throws Exception {
        when(values.get(anyString())).thenReturn("verified");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertThat(user.isEmailVerified()).isTrue();
            verify(redis, never()).delete(anyString());
            return user;
        });

        mvc.perform(registration(PAYLOAD)).andExpect(status().isCreated());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getPassword()).isEqualTo("encoded-password");
        ArgumentCaptor<String> proofKey = ArgumentCaptor.forClass(String.class);
        verify(values).get(proofKey.capture());
        verify(redis).delete(proofKey.getValue());
    }

    @Test
    void verificationStoreFailureDoesNotAllowSignup() throws Exception {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("test failure"));

        mvc.perform(registration(PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email Verification Required"));

        verifyNoInteractions(users);
        verify(passwords, never()).encode(anyString());
        verify(redis, never()).delete(anyString());
    }

    @Test
    void failedSaveDoesNotConsumeVerificationOrBecomeRateLimitError() throws Exception {
        when(values.get(anyString())).thenReturn("verified");
        when(users.save(any(User.class))).thenThrow(new IllegalStateException("test save failed"));

        mvc.perform(registration(PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Runtime Error"));

        verify(redis, never()).delete(anyString());
    }

    @Test
    void exhaustedRateLimitStillReturns429WithoutCallingServiceAgain() throws Exception {
        mvc.perform(registration(PAYLOAD)).andExpect(status().isBadRequest());
        mvc.perform(registration(PAYLOAD)).andExpect(status().isTooManyRequests());

        verify(values, times(1)).get(anyString());
        verifyNoInteractions(users);
    }

    @Test
    void invalidEmailIsRejectedBeforeServiceInvocation() throws Exception {
        mvc.perform(registration(PAYLOAD.replace(EMAIL, "not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));

        verifyNoInteractions(users, values);
    }

    private MockHttpServletRequestBuilder registration(String payload) {
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload);
    }
}
