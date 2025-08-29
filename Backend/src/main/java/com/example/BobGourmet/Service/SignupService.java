package com.example.BobGourmet.Service;

import com.example.BobGourmet.DTO.AuthDTO.SignupRequest;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SignupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    public void signUp(SignupRequest request){
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

        userRepository.save(user);
    }
}
