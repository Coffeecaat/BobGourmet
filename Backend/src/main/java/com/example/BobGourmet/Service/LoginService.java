package com.example.BobGourmet.Service;

import com.example.BobGourmet.DTO.AuthDTO.AuthResponse;
import com.example.BobGourmet.DTO.AuthDTO.LoginRequest;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Repository.UserRepository;
import com.example.BobGourmet.Service.EmailVerificationService;
import com.example.BobGourmet.utils.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public AuthResponse login(LoginRequest request){
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
        // String refreshToken = jwtProvider.generateRefreshToken(user.getUsername()); // refresh token
        // refreshTokenService.saveRefreshToken(user.getUsername(), refreshToken);

        log.info("User logged in successfully: {}", user.getUsername());
        return new AuthResponse(accessToken);
    }

    public void logout(String username){
        try{
            matchroomService.leaveRoom(username);
            log.info("User logged out successfully: {}", username);
        }catch (Exception e){
            log.error("Error occurred during leaveRoom for user '{}' on logout: {}", username, e.getMessage());
        }
        log.info("User {} logout process initiated.", username);
    }
}
