package com.example.BobGourmet.Service.Auth;

import com.example.BobGourmet.DTO.AuthDTO.GoogleUserInfo;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Exception.OAuth2Exception;
import com.example.BobGourmet.Exception.UserAlreadyExistsException;
import com.example.BobGourmet.Repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class OAuth2UserService {
    private final UserRepository userRepository;

    public OAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User findOrCreateUser(GoogleUserInfo googleUserInfo) {
        if (googleUserInfo == null || !StringUtils.hasText(googleUserInfo.getSub())
                || !StringUtils.hasText(googleUserInfo.getEmail())
                || !Boolean.TRUE.equals(googleUserInfo.getEmailVerified())) {
            throw new OAuth2Exception("Verified Google identity is required");
        }
        String email = googleUserInfo.getEmail();
        String googleId = googleUserInfo.getSub();

        // If already signed up using Google OAuth ID
        Optional<User> existingUser = userRepository.findByOauthProviderAndOauthId("google",googleId);
        if(existingUser.isPresent()) {
            return existingUser.get();
        }

        // If already signed up using same email
        Optional<User> existingEmailUser = userRepository.findByEmail(email);
        if (existingEmailUser.isPresent()) {
            // Email is not a stable Google identity. Never link accounts by email alone.
            throw new UserAlreadyExistsException("An account with this email already exists");
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

}
