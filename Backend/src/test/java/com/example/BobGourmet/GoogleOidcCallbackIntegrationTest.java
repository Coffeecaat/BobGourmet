package com.example.BobGourmet;

import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Service.Room.MatchroomService;
import com.example.BobGourmet.config.FakeGoogleOidcProvider;
import com.example.BobGourmet.config.FakeGoogleOidcProvider.Scenario;
import com.example.BobGourmet.utils.JwtProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "oauth.frontend.base-url=http://localhost:5173")
@ActiveProfiles("test")
@Import(GoogleOidcCallbackIntegrationTest.ProviderConfiguration.class)
class GoogleOidcCallbackIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private FakeGoogleOidcProvider provider;
    @Autowired private UserRepository users;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private DataSource dataSource;
    @MockitoBean private MatchroomService matchroomService;
    private MockMvc mvc;

    @BeforeEach
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        provider.reset();
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:bobgourmet-test-"));
        }
        users.deleteAll();
    }

    @Test
    void callbackCreatesUserIssuesCookieAndAuthenticatesMeWithoutUrlToken() throws Exception {
        Handshake handshake = startLogin();
        MvcResult result = callback(handshake).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:5173/auth/callback"))
                .andExpect(cookie().httpOnly("jwt-token", true))
                .andExpect(cookie().secure("jwt-token", true))
                .andExpect(cookie().path("jwt-token", "/"))
                .andExpect(cookie().maxAge("jwt-token", 86400)).andReturn();
        Cookie cookie = result.getResponse().getCookie("jwt-token");
        assertNotNull(cookie);
        assertEquals("Strict", cookie.getAttribute("SameSite"));
        assertEquals("alice", jwtProvider.getUsernameFromToken(cookie.getValue()));
        assertTrue(handshake.session().isInvalid());
        assertEquals(1, users.count());
        assertTrue(users.findByUsername("alice").orElseThrow().isEmailVerified());
        assertEquals(1, provider.tokenRequests.get());
        assertEquals(1, provider.userInfoRequests.get());

        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.nickname").value("Alice"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.verificationToken").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void returningGoogleUserDoesNotCreateAnotherAccount() throws Exception {
        users.save(new User("existing", "alice@example.com", "Existing", "google", "google-sub"));
        MvcResult result = callback(startLogin()).andExpect(status().is3xxRedirection()).andReturn();
        Cookie cookie = result.getResponse().getCookie("jwt-token");
        assertNotNull(cookie);
        assertEquals("existing", jwtProvider.getUsernameFromToken(cookie.getValue()));
        assertEquals(1, users.count());
    }

    @ParameterizedTest
    @EnumSource(value = Scenario.class, mode = EnumSource.Mode.EXCLUDE, names = "NORMAL")
    void rejectsInvalidProviderResponseWithoutCreatingUserOrCookie(Scenario scenario) throws Exception {
        Handshake handshake = startLogin();
        provider.setScenario(scenario);
        expectFailure(callback(handshake));
        assertEquals(1, provider.tokenRequests.get());
        assertEquals(0, users.count());
        assertTrue(handshake.session().isInvalid());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "google", "facebook"})
    void rejectsEmailConflictInsteadOfLinkingAccounts(String accountProvider) throws Exception {
        users.save(new User("existing", "alice@example.com", "Existing", accountProvider, "another-sub"));
        expectFailure(callback(startLogin()));
        assertEquals(1, provider.tokenRequests.get());
        assertEquals(1, provider.userInfoRequests.get());
        assertEquals(1, users.count());
        assertEquals("another-sub", users.findByUsername("existing").orElseThrow().getOauthId());
    }

    @Test
    void rejectsWrongStateBeforeCallingProvider() throws Exception {
        Handshake handshake = startLogin();
        expectFailure(mvc.perform(get("/login/oauth2/code/google").session(handshake.session())
                .param("code", "test-code").param("state", "wrong-state")));
        assertEquals(0, provider.tokenRequests.get());
        assertEquals(0, users.count());
    }

    @Test
    void rejectsMissingStateBeforeCallingProvider() throws Exception {
        Handshake handshake = startLogin();
        expectFailure(mvc.perform(get("/login/oauth2/code/google").session(handshake.session()).param("code", "test-code")));
        assertEquals(0, provider.tokenRequests.get());
    }

    @Test
    void rejectsReplayWithoutAnotherTokenExchange() throws Exception {
        Handshake handshake = startLogin();
        callback(handshake).andExpect(redirectedUrl("http://localhost:5173/auth/callback"));
        expectFailure(mvc.perform(get("/login/oauth2/code/google")
                .param("code", "test-code").param("state", handshake.state())));
        assertEquals(1, provider.tokenRequests.get());
        assertEquals(1, users.count());
    }

    @Test
    void handlesProviderDenialWithoutTokenExchange() throws Exception {
        Handshake handshake = startLogin();
        expectFailure(mvc.perform(get("/login/oauth2/code/google").session(handshake.session())
                .param("error", "access_denied").param("state", handshake.state())));
        assertEquals(0, provider.tokenRequests.get());
        assertTrue(handshake.session().isInvalid());
    }

    @Test
    void retiredEndpointCannotExchangeCodeOrIssueCookie() throws Exception {
        mvc.perform(post("/api/auth/oauth/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"test-code\",\"state\":\"untrusted\"}"))
                .andExpect(status().isGone()).andExpect(cookie().doesNotExist("jwt-token"));
        assertEquals(0, provider.tokenRequests.get());
        assertEquals(0, users.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-token", "deleted-user", "expired-token"})
    void meRejectsUnusableCookies(String kind) throws Exception {
        String token = switch (kind) {
            case "deleted-user" -> jwtProvider.generateToken("deleted-user");
            case "expired-token" -> new JwtProvider("testsecretkeytestsecretkeytestsecretkeytestsecretkey", -600000).generateToken("deleted-user");
            default -> kind;
        };
        mvc.perform(get("/api/auth/me").cookie(new Cookie("jwt-token", token)))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
    }

    @Test
    void meSupportsLocalAccountWithoutExposingEntityFields() throws Exception {
        User local = users.save(new User("local-user", "local@example.com", "secret-password", "Local"));
        String token = jwtProvider.generateToken(local.getUsername());
        mvc.perform(get("/api/auth/me").cookie(new Cookie("jwt-token", token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("local-user"))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.oauthId").doesNotExist());
    }

    private Handshake startLogin() throws Exception {
        MvcResult result = mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String redirect = result.getResponse().getRedirectedUrl();
        assertNotNull(redirect);
        String state = UriComponentsBuilder.fromUriString(redirect).build().getQueryParams().getFirst("state");
        String nonce = UriComponentsBuilder.fromUriString(redirect).build().getQueryParams().getFirst("nonce");
        assertNotNull(state);
        assertNotNull(nonce);
        // MockMvc .param takes decoded values, just like Servlet request parameters.
        provider.setNonce(URLDecoder.decode(nonce, StandardCharsets.UTF_8));
        return new Handshake((MockHttpSession) result.getRequest().getSession(false),
                URLDecoder.decode(state, StandardCharsets.UTF_8));
    }

    private ResultActions callback(Handshake handshake) throws Exception {
        return mvc.perform(get("/login/oauth2/code/google").session(handshake.session())
                .param("code", "test-code").param("state", handshake.state()));
    }

    private void expectFailure(ResultActions result) throws Exception {
        result.andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:5173/auth/callback?error=oauth_failed"))
                .andExpect(cookie().doesNotExist("jwt-token"));
    }

    private record Handshake(MockHttpSession session, String state) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfiguration {
        @Bean(destroyMethod = "close")
        FakeGoogleOidcProvider fakeGoogleOidcProvider() throws Exception {
            return new FakeGoogleOidcProvider();
        }

        @Bean
        ClientRegistrationRepository testRegistrations(FakeGoogleOidcProvider provider) {
            ClientRegistration google = ClientRegistration.withRegistrationId("google")
                    .clientId("test-client").clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email").authorizationUri(provider.baseUrl() + "/authorize")
                    .tokenUri(provider.baseUrl() + "/token").userInfoUri(provider.baseUrl() + "/userinfo")
                    .jwkSetUri(provider.baseUrl() + "/jwks").issuerUri(provider.baseUrl())
                    .userNameAttributeName("sub").clientName("Google test provider").build();
            return new InMemoryClientRegistrationRepository(google);
        }
    }
}
