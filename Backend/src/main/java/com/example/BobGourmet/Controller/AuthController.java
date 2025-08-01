package com.example.BobGourmet.Controller;

import com.example.BobGourmet.DTO.AuthDTO.AuthResponse;
import com.example.BobGourmet.DTO.AuthDTO.LoginRequest;
import com.example.BobGourmet.DTO.AuthDTO.SignupRequest;
import com.example.BobGourmet.Service.LoginService;
import com.example.BobGourmet.Service.SignupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;
    private final SignupService signupService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody SignupRequest request){
        signupService.signUp(request);
        return new ResponseEntity<>("User registered successfully", HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request)
    {
        AuthResponse authResponse = loginService.login(request);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@AuthenticationPrincipal UserDetails userDetails){
        loginService.logout(userDetails.getUsername());
        return ResponseEntity.ok("User logged out successfully");
    }
}
