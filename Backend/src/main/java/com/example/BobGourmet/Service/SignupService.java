package com.example.BobGourmet.Service;

import com.example.BobGourmet.DTO.AuthDTO.SignupRequest;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Service.EmailVerificationService;
import com.example.BobGourmet.Service.EmailDomainValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SignupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final EmailDomainValidationService emailDomainValidationService;
    public void signUp(SignupRequest request){
        // Validate email domain first
        emailDomainValidationService.validateEmailDomain(request.getEmail());
        
        if(userRepository.findByUsername(request.getUsername()).isPresent()){
            throw new RuntimeException("Username is already in use");
        }
        
        if(userRepository.findByEmail(request.getEmail()).isPresent()){
            throw new RuntimeException("Email is already in use");
        }

        // Validate password is provided for regular signup
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new RuntimeException("Password is required for regular signup");
        }

        User user = new User(
            request.getUsername(),
            request.getEmail(), 
            passwordEncoder.encode(request.getPassword()),
            request.getNickname()
        );

        // Save user first to get the ID
        user = userRepository.save(user);
        
        // Send verification email for local users
        System.out.println("DEBUG: About to send verification email to: " + user.getEmail());
        emailVerificationService.sendVerificationEmail(user);
        System.out.println("DEBUG: Verification email service called successfully");
    }
}
