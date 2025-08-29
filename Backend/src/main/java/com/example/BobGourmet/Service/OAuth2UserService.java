package com.example.BobGourmet.Service;

import com.example.BobGourmet.DTO.AuthDTO.GoogleUserInfo;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Exception.OAuth2Exception;
import com.example.BobGourmet.Exception.UserAlreadyExistsException;
import com.example.BobGourmet.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Service
public class OAuth2UserService {
    private final UserRepository userRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    public OAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User processGoogleOAuth(String code){
        try{
            OAuth2AccessToken accessToken = exchangeCodeForAccessToken(code);
            GoogleUserInfo googleUserInfo = getUserInfoFromGoogle(accessToken.getTokenValue());
            return findOrCreateUser(googleUserInfo);
        }catch(Exception e){
            throw new OAuth2Exception("Google OAuth authentication failed: " + e.getMessage());
        }
    }

    public User findOrCreateUser(GoogleUserInfo googleUserInfo) {
        String email = googleUserInfo.getEmail();
        String googleId = googleUserInfo.getSub();

        // If already signed up using Google OAuth ID
        Optional<User> existingUser = userRepository.findByOauthProviderAndOauthId("google",googleId);
        if(existingUser.isPresent()) {
            return existingUser.get();
        }

        // If already signed up using same email
        Optional<User> existingEmailUser = userRepository.findByEmail(email);
        if(existingEmailUser.isPresent()) {
            User user = existingEmailUser.get();

            // If email is already used with local account
            if("local".equals(user.getOauthProvider())){
                throw new UserAlreadyExistsException("An account with this email already exists");
            }

            // If email is already used with another OAuth provider
            if(!"google".equals(user.getOauthProvider())){
                throw new UserAlreadyExistsException("An account with this provider already exists");
            }
            return user;
        }

        // Create new user
        String baseUsername = generateUsernameFromGoogleInfo(googleUserInfo);
        String uniqueUsername = ensureUniqueUsername(baseUsername);
        String nickname = generateNicknameFromGoogleInfo(googleUserInfo, uniqueUsername);

        User newUser = new User(uniqueUsername, email, nickname, "google", googleId);
        return userRepository.save(newUser);

    }

    private String generateUsernameFromGoogleInfo(GoogleUserInfo googleUserInfo) {
        // Try given name first - allow Korean characters and other Unicode letters
        if(googleUserInfo.getGivenName() != null){
            String username = googleUserInfo.getGivenName().replaceAll("[^\\p{L}\\p{N}]","").toLowerCase();
            if (!username.isEmpty()) {
                return username;
            }
        }

        // Try full name - allow Korean characters and other Unicode letters
        if(googleUserInfo.getName() != null){
            String username = googleUserInfo.getName().replaceAll("[^\\p{L}\\p{N}]","").toLowerCase();
            if (!username.isEmpty()) {
                return username;
            }
        }

        // Fallback to email prefix
        String emailPrefix = googleUserInfo.getEmail().split("@")[0].replaceAll("[^\\p{L}\\p{N}]","");
        if (!emailPrefix.isEmpty()) {
            return emailPrefix;
        }
        
        // Final fallback - use google ID
        return "user" + googleUserInfo.getSub();
    }

    private String generateNicknameFromGoogleInfo(GoogleUserInfo googleUserInfo, String fallbackUsername) {
        // Try given name first (most personal) - same filtering as username
        if (googleUserInfo.getGivenName() != null) {
            String nickname = googleUserInfo.getGivenName().replaceAll("[^\\p{L}\\p{N}]","");
            if (!nickname.isEmpty()) {
                return nickname;
            }
        }
        
        // Try full name - same filtering as username
        if (googleUserInfo.getName() != null) {
            String nickname = googleUserInfo.getName().replaceAll("[^\\p{L}\\p{N}]","");
            if (!nickname.isEmpty()) {
                return nickname;
            }
        }
        
        // Fallback to username
        return fallbackUsername;
    }

    private String ensureUniqueUsername(String baseUsername) {
        String username = baseUsername;
        int counter = 1;

        while(userRepository.existsByUsername(username)){
            username = baseUsername + counter;
            counter++;
        }
        return username;
    }

    private OAuth2AccessToken exchangeCodeForAccessToken(String code){

        //Use Spring's OAuth2 client to exchange code for token
        RestTemplate restTemplate = new RestTemplate();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("code", code);
        params.add("grant_type", "authorization_code");
        params.add("redirect_uri", redirectUri); // Must match Google console setting

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        // Google's token endpoint
        String tokenUrl = "https://oauth2.googleapis.com/token";

        try{
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            Map<String,Object> responseBody = response.getBody();

            return new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    (String) responseBody.get("access_token"),
                    null, // issued at
                    null          // expires at
            );
        }catch(Exception e){
            throw new OAuth2Exception("Failed to exchange code for token: " + e.getMessage());
        }
    }

    private GoogleUserInfo getUserInfoFromGoogle(String accessToken){
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<String> entity = new HttpEntity<>("", headers);

        try{
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://www.googleapis.com/oauth2/v3/userinfo",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            Map<String, Object> userInfo = response.getBody();

            return GoogleUserInfo.builder()
                    .sub((String) userInfo.get("sub"))
                    .email((String) userInfo.get("email"))
                    .name((String) userInfo.get("name"))
                    .givenName((String) userInfo.get("given_name"))
                    .familyName((String) userInfo.get("family_name"))
                    .picture((String) userInfo.get("picture"))
                    .build();
        }catch(Exception e){
            throw new OAuth2Exception("Failed to get user info from Google: "+ e.getMessage());
        }
    }
}
