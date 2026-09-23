package com.example.BobGourmet.Controller;

import com.example.BobGourmet.DTO.AuthDTO.*;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.DTO.ErrorResponse;
import com.example.BobGourmet.Service.Email.EmailVerificationService;
import com.example.BobGourmet.Service.Auth.LoginService;
import com.example.BobGourmet.Service.Auth.SignupService;
import com.example.BobGourmet.Service.Security.IPTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;



@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;
    private final SignupService signupService;
    private final EmailVerificationService emailVerificationService;
    private final IPTrackingService ipTrackingService;

    @RateLimiter(name = "signup", fallbackMethod = "signupRateLimitFallback")
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest){
        String ipAddress = ipTrackingService.getClientIpAddress(httpRequest);
        signupService.signUp(request, ipAddress);
        return new ResponseEntity<>("User registered successfully", HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletResponse response)
    {
        AuthResponse authResponse = loginService.login(request, response);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@AuthenticationPrincipal UserDetails userDetails, HttpServletResponse response){
        loginService.logout(userDetails.getUsername(), response);
        return ResponseEntity.ok("User logged out successfully");
    }

    // Explicit tombstone: legacy clients cannot bypass the OIDC authorization handshake.
    @PostMapping("/oauth/google")
    public ResponseEntity<ErrorResponse> handleGoogleOAuth() {
        return ResponseEntity.status(HttpStatus.GONE).body(new ErrorResponse(
                "OAuth Endpoint Retired", "Start Google login at /oauth2/authorization/google"));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> currentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CurrentUserResponse(user.getUsername(), user.getEmail(), user.getNickname()));
    }

    @PostMapping("/send-pre-verification")
    public ResponseEntity<String> sendPreVerificationEmail(@RequestParam("email") String email, HttpServletRequest httpRequest) {
        try {
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Email is required");
            }

            String ipAddress = ipTrackingService.getClientIpAddress(httpRequest);
            emailVerificationService.sendPreVerificationEmail(email.trim(), ipAddress);
            return ResponseEntity.ok("Pre-verification email sent successfully. Please check your inbox.");
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Too many") || e.getMessage().contains("recently used")) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to send pre-verification email. Please try again later.");
        }
    }

    @GetMapping("/verify-pre-verification")
    public ResponseEntity<String> verifyPreVerification(@RequestParam("token") String token) {
        try {
            boolean verified = emailVerificationService.verifyPreVerificationToken(token);
            
            if (verified) {
                return ResponseEntity.ok("Email pre-verified successfully! You can now complete your signup.");
            } else {
                return ResponseEntity.badRequest()
                    .body("Pre-verification failed. The token may be invalid or expired.");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An error occurred during pre-verification.");
        }
    }

    @GetMapping("/check-pre-verification")
    public ResponseEntity<String> checkPreVerification(@RequestParam("email") String email) {
        try {
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Email is required");
            }
            
            boolean isVerified = emailVerificationService.isEmailPreVerified(email.trim());
            
            if (isVerified) {
                return ResponseEntity.ok("Email is pre-verified and ready for signup.");
            } else {
                return ResponseEntity.badRequest().body("Email is not pre-verified. Please verify your email first.");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to check pre-verification status.");
        }
    }

    // Fallback method for signup rate limiting
    public ResponseEntity<String> signupRateLimitFallback(SignupRequest request, HttpServletRequest httpRequest, RequestNotPermitted ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body("Too many signup attempts. Please try again later.");
    }


}
