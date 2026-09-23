package com.example.BobGourmet.Service.Auth;

import com.example.BobGourmet.DTO.AuthDTO.AuthResponse;
import com.example.BobGourmet.DTO.AuthDTO.LoginRequest;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Service.Email.EmailVerificationService;
import com.example.BobGourmet.Service.Room.MatchroomService;
import com.example.BobGourmet.utils.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletResponse;



@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final MatchroomService matchroomService;
    private final EmailVerificationService emailVerificationService;
    private final JwtCookieService jwtCookieService;

    public AuthResponse login(LoginRequest request, HttpServletResponse response){
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("아이디 또는 비밀번호를 확인해 주세요."));

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())){
            throw new BadCredentialsException("아이디 또는 비밀번호를 확인해 주세요.");
        }

        // Check if email is verified (only for local users)
        if (!emailVerificationService.isEmailVerified(user)) {
            throw new BadCredentialsException("Email verification required. Please check your email and click the verification link before logging in. 이메일 인증이 필요합니다. 이메일을 확인해 주세요.");
        }

        String accessToken = jwtProvider.generateToken(user.getUsername());
        
        // Set HttpOnly cookie for secure token storage
        jwtCookieService.issue(response, accessToken);
        
        log.info("User logged in successfully: {}", user.getUsername());
        // Return response without token in body for security
        return new AuthResponse(null);
    }
    
    public void logout(String username, HttpServletResponse response){
        try{
            matchroomService.leaveRoom(username);
            log.info("User logged out successfully: {}", username);
        }catch (Exception e){
            log.error("Error occurred during leaveRoom for user '{}' on logout: {}", username, e.getMessage());
        }
        
        // Clear JWT cookie
        jwtCookieService.clear(response);
        log.info("User {} logout process initiated.", username);
    }
    
}
