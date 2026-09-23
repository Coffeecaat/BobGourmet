package com.example.BobGourmet.Config;

import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Security.JwtAuthFilter;
import com.example.BobGourmet.Service.Auth.GoogleOidcUserService;
import com.example.BobGourmet.Service.Auth.GoogleOidcUserService.LocalOidcUser;
import com.example.BobGourmet.Service.Auth.JwtCookieService;
import com.example.BobGourmet.utils.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final GoogleOidcUserService googleOidcUserService;
    private final JwtCookieService jwtCookieService;

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Value("${oauth.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
                                                   UserDetailsService userDetailsService) throws Exception {
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtProvider, userDetailsService);
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/logout",
                                "/api/auth/oauth/google",
                                "/api/auth/verify-email", "/api/auth/resend-verification",
                                "/api/auth/send-pre-verification", "/api/auth/verify-pre-verification",
                                "/api/auth/check-pre-verification", "/oauth2/**", "/login/oauth2/**",
                                "/swagger-ui/**", "/swagger-resources/**", "/webjars/**", "/v3/api-docs/**",
                                "/ws-BobGourmet/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint()))
                .oauth2Login(oauth2 -> oauth2.loginPage(frontendBaseUrl)
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(googleOidcUserService)
                                // Google login must use OIDC, never fall back to unverified OAuth attributes.
                                .userService(request -> {
                                    throw new OAuth2AuthenticationException(new OAuth2Error("oidc_required"));
                                }))
                        .successHandler(oauth2AuthenticationSuccessHandler())
                        .failureHandler((request, response, exception) -> {
                            endOAuthSession(request);
                            String errorCode = exception instanceof OAuth2AuthenticationException oauthException
                                    ? oauthException.getError().getErrorCode() : "authentication_failed";
                            log.warn("Google login failed: {}", errorCode);
                            response.sendRedirect(frontendBaseUrl + "/auth/callback?error=oauth_failed");
                        })
                        .permitAll())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            endOAuthSession(request);
            if (!(authentication.getPrincipal() instanceof LocalOidcUser user)) {
                response.sendRedirect(frontendBaseUrl + "/auth/callback?error=oauth_failed");
                return;
            }
            String token = jwtProvider.generateToken(user.getLocalUsername(), user.getLocalNickname());
            jwtCookieService.issue(response, token);
            response.setHeader("Cache-Control", "no-store");
            response.sendRedirect(frontendBaseUrl + "/auth/callback");
        };
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(PathPatternRequestMatcher.withDefaults().matcher("/api/auth/me"),
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint(frontendBaseUrl));
        return entryPoint;
    }

    private void endOAuthSession(HttpServletRequest request) {
        // The session stores only the temporary authorization handshake, not API authentication.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        //Parse allowed origins and add frontend base URL
        List<String> origins = new ArrayList<>(List.of(allowedOrigins));
        origins.add(frontendBaseUrl);
        origins.add("https://accounts.google.com");

        configuration.setAllowedOrigins(origins);
        configuration.setMaxAge(3600L); //preflight cache
        configuration.setExposedHeaders(List.of("Authorization", "Location"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Cookie"));

        //allow credentials (such as cookies)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;

    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Username not found: "+username));
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
}
