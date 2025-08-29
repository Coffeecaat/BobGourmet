package com.example.BobGourmet.Config;

import com.example.BobGourmet.DTO.AuthDTO.GoogleUserInfo;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Security.JwtAuthFilter;
import com.example.BobGourmet.Service.OAuth2UserService;
import com.example.BobGourmet.utils.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import com.example.BobGourmet.DTO.AuthDTO.GoogleUserInfo;
import com.example.BobGourmet.Entity.User;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {


    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final OAuth2UserService oAuth2UserService;

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Value("${oauth.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider, UserDetailsService userDetailsService) throws Exception {

        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtProvider, userDetailsService);
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/auth/oauth/**",
                                "/oauth2/**", "/login/oauth2/**",
                                "/swagger-ui/**",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/v3/api-docs/**").permitAll()
                        .requestMatchers("/ws-BobGourmet/**").permitAll()
                        .requestMatchers("/api/MatchRooms/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage(frontendBaseUrl) //Redirect unauthorized users here
                        .userInfoEndpoint(userInfo -> userInfo
                            .userService(customOAuth2UserService())
                        )
                        .successHandler(oauth2AuthenticationSuccessHandler())
                        .failureHandler((request, response, exception) -> {
                            System.err.println("=== OAuth FAILURE HANDLER ===");
                            System.err.println("Exception: " + exception.getClass().getName());
                            System.err.println("Message: " + exception.getMessage());
                            exception.printStackTrace();
                            response.sendRedirect(frontendBaseUrl+"/auth/callback?error=oauth_failed");
                        })
                        .permitAll()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DefaultOAuth2UserService customOAuth2UserService() {
        return new DefaultOAuth2UserService() {
            @Override
            public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
                OAuth2User oauth2User = super.loadUser(userRequest);
                
                // Process user and create in database
                try {
                    String email = oauth2User.getAttribute("email");
                    String googleId = oauth2User.getAttribute("sub");
                    String name = oauth2User.getAttribute("name");
                    String givenName = oauth2User.getAttribute("given_name");
                    
                    GoogleUserInfo googleUserInfo = GoogleUserInfo.builder()
                            .sub(googleId)
                            .email(email)
                            .name(name)
                            .givenName(givenName)
                            .build();
                    
                    // Use the existing service to find or create user
                    User user = oAuth2UserService.findOrCreateUser(googleUserInfo);
                    
                    return oauth2User;
                } catch (Exception e) {
                    throw new OAuth2AuthenticationException(new OAuth2Error("user_creation_failed"), e);
                }
            }
        };
    }

    @Bean
    public AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler() {
        return new SimpleUrlAuthenticationSuccessHandler() {

            @Override
            public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                                Authentication authentication) throws IOException {
                OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

                try{
                    String email = oauth2User.getAttribute("email");
                    String googleId = oauth2User.getAttribute("sub");
                    String name = oauth2User.getAttribute("name");
                    String givenName = oauth2User.getAttribute("given_name");
                    String familyName = oauth2User.getAttribute("family_name");
                    String picture = oauth2User.getAttribute("picture");

                    GoogleUserInfo googleUserInfo = GoogleUserInfo.builder()
                            .sub(googleId)
                            .email(email)
                            .name(name)
                            .givenName(givenName)
                            .familyName(familyName)
                            .picture(picture)
                            .build();

                    User user = SecurityConfig.this.oAuth2UserService.findOrCreateUser(googleUserInfo);
                    String jwt = jwtProvider.generateToken(user.getUsername(), user.getNickname());

                    String redirectUrl = frontendBaseUrl+"/auth/callback?token=" + jwt;
                    response.sendRedirect(redirectUrl);
                }catch(Exception e){
                    // Also log to Spring's logger
                    org.slf4j.LoggerFactory.getLogger(SecurityConfig.class)
                        .error("OAuth Success Handler Error: {} - {}", e.getClass().getName(), e.getMessage(), e);
                    
                    response.sendRedirect(frontendBaseUrl+"/auth/callback?error=oauth_failed");
                }
            }
        };
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
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));

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
