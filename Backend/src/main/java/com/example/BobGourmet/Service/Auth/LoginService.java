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
import jakarta.servlet.http.Cookie;


@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final MatchroomService matchroomService;
    private final EmailVerificationService emailVerificationService;
    // private final RefreshTokenService refreshTokenService; // when using refresh token

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
        setJwtCookie(response, accessToken);
        
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
        clearJwtCookie(response);
        log.info("User {} logout process initiated.", username);
    }
    
    private void setJwtCookie(HttpServletResponse response, String token) {
        Cookie jwtCookie = new Cookie("jwt-token", token);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(true); // Only send over HTTPS in production
        jwtCookie.setPath("/");
        // Same-origin through proxy - can use Strict for better security
        jwtCookie.setAttribute("SameSite", "Strict");
        jwtCookie.setMaxAge(24 * 60 * 60); // 24 hours (match your JWT expiry)
        response.addCookie(jwtCookie);
    }
    
    private void clearJwtCookie(HttpServletResponse response) {
        Cookie jwtCookie = new Cookie("jwt-token", "");
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(true);
        jwtCookie.setPath("/");
        jwtCookie.setAttribute("SameSite", "Strict"); // Match the setting used when creating
        jwtCookie.setMaxAge(0); // Expire immediately
        response.addCookie(jwtCookie);
    }

}
