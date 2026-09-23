package com.example.BobGourmet.Service.Auth;

import com.example.BobGourmet.DTO.AuthDTO.SignupRequest;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Exception.EmailNotVerifiedException;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Service.Email.EmailVerificationService;
import com.example.BobGourmet.Service.Email.EmailDomainValidationService;
import com.example.BobGourmet.Service.Security.IPTrackingService;
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
    private final IPTrackingService ipTrackingService;
    public void signUp(SignupRequest request, String ipAddress){
        // Check for abuse patterns and record them
        if (!ipTrackingService.isIPAllowedForSignup(ipAddress)) {
            // Record the abusive IP attempt
            ipTrackingService.recordAbusiveIPAttempt(ipAddress);
            throw new RuntimeException("Too many signup attempts from this location. Please try again later.");
        }
        
        if (ipTrackingService.isEmailAlreadyUsed(request.getEmail())) {
            // Record the email reuse abuse attempt
            ipTrackingService.recordEmailReuseAttempt(request.getEmail(), ipAddress);
            throw new RuntimeException("This email has already been used for signup recently. Please try a different email or wait before trying again.");
        }
        
        // Validate email domain first
        emailDomainValidationService.validateEmailDomain(request.getEmail());
        
        // Require server-side proof before encoding a password or persisting a user.
        if (!emailVerificationService.isEmailPreVerified(request.getEmail())) {
            throw new EmailNotVerifiedException();
        }
        
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

        // Only a pre-verified email can reach this point. Persist the final state once.
        user.setEmailVerified(true);
        userRepository.save(user);

        // Preserve proof if saving fails, so the user can retry signup.
        emailVerificationService.clearPreVerification(request.getEmail());
    }
}
