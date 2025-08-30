package com.example.BobGourmet.Controller;

import com.example.BobGourmet.DTO.AuthDTO.*;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Exception.OAuth2Exception;
import com.example.BobGourmet.Service.LoginService;
import com.example.BobGourmet.Service.OAuth2UserService;
import com.example.BobGourmet.Service.SignupService;
import com.example.BobGourmet.utils.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
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
    private final OAuth2UserService oAuth2UserService;
    private final JwtProvider jwtProvider;

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

    @PostMapping("/oauth/google")
    public ResponseEntity<AuthResponse> handleGoogleOAuth(@RequestBody GoogleOAuthRequest request){

        try{
            if(request.getCode() == null || request.getCode().trim().isEmpty()){
                return ResponseEntity.badRequest().body(new AuthResponse(null));
            }

            User user = oAuth2UserService.processGoogleOAuth(request.getCode());

            String jwt = jwtProvider.generateToken(user.getUsername());

            return ResponseEntity.ok(new AuthResponse(jwt));
        }catch(OAuth2Exception e){
            return ResponseEntity.badRequest().body(new AuthResponse(null));
        }
        catch(Exception e){
            return ResponseEntity.status(500).body(new AuthResponse(null));
        }
    }

}
