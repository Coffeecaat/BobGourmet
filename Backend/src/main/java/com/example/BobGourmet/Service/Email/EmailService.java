package com.example.BobGourmet.Service.Email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailDomainValidationService emailDomainValidationService;
    
    @Value("${app.mail.from:noreply@bobgourmet.com}")
    private String fromEmail;
    
    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;
    
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    public void sendVerificationEmail(String to, String username, String verificationToken) {
        try {
            emailDomainValidationService.validateEmailDomain(to);
            
            // In development, just log the email content instead of sending
            if ("dev".equals(activeProfile)) {
                logEmailInDev(to, username, verificationToken);
                return;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("BobGourmet@noreply.com");
            message.setTo(to);
            message.setSubject("BobGourmet - Email Verification Required");
            
            String verificationUrl = frontendUrl + "/verify-email?token=" + verificationToken;
            
            String emailContent = String.format(
                "Hello %s,\n\n" +
                "Thank you for signing up for BobGourmet!\n\n" +
                "To complete your registration and start enjoying our menu selection service, " +
                "please verify your email address by clicking the link below:\n\n" +
                "%s\n\n" +
                "This verification link will expire in 10 min for security reasons.\n\n" +
                "If you didn't create an account with BobGourmet, please ignore this email.\n\n" +
                "Welcome to BobGourmet!\n" +
                "The BobGourmet Developer Coffeecat."+

                "\n\n안녕하세요. %s님,\n\n" +
                "BobGourmet에 회원가입해 주셔서 감사합니다!\n\n" +
                "회원가입 절차를 완료하기 위해선 아래의 링크를 통해 이메일 인증을 완료해주시기 바랍니다.\n\n " +
                "%s\n\n" +
                "해당 인증 링크는 보안 상의 이유로 10분 이내에 만료됩니다.\n\n" +
                "혹시 BobGourmet의 계정을 생성하신 적이 없다면 해당 이메일을 무시하셔도 됩니다.\n\n" +
                "BobGourmet 개발자 Coffeecat 올림",
                username, verificationUrl,username, verificationUrl
            );
            
            message.setText(emailContent);
            
            System.out.println("DEBUG: About to send email via mailSender...");
            mailSender.send(message);
            System.out.println("DEBUG: Email sent successfully via mailSender!");
            log.info("Verification email sent successfully to: {}", to);
            
        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", to, e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    public void sendWelcomeEmail(String to, String username) {
        try {
            // In development, just log the welcome email
            if ("dev".equals(activeProfile)) {
                log.info(" [DEV] Welcome email would be sent to: {} ({})", to, username);
                return;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("BobGourmet@noreply.com");
            message.setTo(to);
            message.setSubject("Welcome to BobGourmet! Your Email is Verified");
            
            String emailContent = String.format(
                "Hello %s,\n\n" +
                "Congratulations! Your email has been successfully verified.\n\n" +
                "You can now:\n" +
                "• Create and join dining rooms\n" +
                "• Submit your favorite restaurant suggestions\n" +
                "• Participate in fair random selection draws\n" +
                "• Discover new dining experiences with friends\n\n" +
                "Start your culinary adventure at: %s\n\n" +
                "Happy dining!\n" +
                "The BobGourmet Team\n\n"+

                "축하드립니다! 이메일 인증이 성공적으로 완료되었습니다.\n\n" +
                "이제 BobGourmet에 접속하여 친구,가족,지인들과 함께 식사 메뉴 추첨을 해보세요: %s\n\n" +
                ":)\n" +
                "BobGourmet 개발자 Coffeecat 올림",
                username, frontendUrl,username, frontendUrl
            );
            
            message.setText(emailContent);
            
            mailSender.send(message);
            log.info("Welcome email sent successfully to: {}", to);
            
        } catch (Exception e) {
            log.error("Failed to send welcome email to: {}", to, e);
            // Don't throw exception for welcome email failures
        }
    }
    
    private void logEmailInDev(String to, String username, String verificationToken) {
        String verificationUrl = frontendUrl + "/verify-email?token=" + verificationToken;
        
        log.info("=== EMAIL VERIFICATION (DEV MODE) ===");
        log.info("To: {}", to);
        log.info("Username: {}", username);
        log.info("Verification URL: {}", verificationUrl);
        log.info("Token: {}", verificationToken);
        log.info("=========================================");
        
        log.info("📧 [DEV] Verification email would be sent to: {}", to);
        log.info("🔗 [DEV] Verification link: {}", verificationUrl);
    }
    
    public void sendPreVerificationEmail(String to, String verificationToken) {
        try {
            // In development, just log the email content instead of sending
            if ("dev".equals(activeProfile)) {
                logPreVerificationEmailInDev(to, verificationToken);
                return;
            }
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("BobGourmet@noreply.com");
            message.setTo(to);
            message.setSubject("BobGourmet - Email Pre-Verification");
            
            String verificationUrl = frontendUrl + "/verify-pre-verification?token=" + verificationToken;
            
            String emailContent = String.format(
                "Hello,\n\n" +
                "You're almost ready to sign up for BobGourmet!\n\n" +
                "To complete your email verification, please click the link below:\n\n" +
                "%s\n\n" +
                "This verification link will expire in 1 hour for security reasons.\n\n" +
                "After verification, you can return to the signup form to complete your registration.\n\n" +
                "If you didn't request this verification, please ignore this email.\n\n" +
                "The BobGourmet Developer Coffeecat\n\n" +

                "안녕하세요,\n\n" +
                "BobGourmet 회원가입을 위한 이메일 인증을 진행해 주세요!\n\n" +
                "아래의 링크를 클릭하여 이메일 인증을 완료해 주시기 바랍니다:\n\n" +
                "%s\n\n" +
                "해당 인증 링크는 보안 상의 이유로 1시간 이내에 만료됩니다.\n\n" +
                "인증 완료 후, 회원가입 폼으로 돌아가서 가입을 완료하실 수 있습니다.\n\n" +
                "혹시 해당 인증을 요청하지 않으셨다면 이 이메일을 무시하셔도 됩니다.\n\n" +
                "BobGourmet 개발자 Coffeecat 올림",
                verificationUrl, verificationUrl
            );
            
            message.setText(emailContent);
            
            mailSender.send(message);
            log.info("Pre-verification email sent successfully to: {}", to);
            
        } catch (Exception e) {
            log.error("Failed to send pre-verification email to: {}", to, e);
            throw new RuntimeException("Failed to send pre-verification email", e);
        }
    }
    
    private void logPreVerificationEmailInDev(String to, String verificationToken) {
        String verificationUrl = frontendUrl + "/verify-pre-verification?token=" + verificationToken;
        
        log.info("=== PRE-VERIFICATION EMAIL (DEV MODE) ===");
        log.info("To: {}", to);
        log.info("Pre-Verification URL: {}", verificationUrl);
        log.info("Token: {}", verificationToken);
        log.info("==========================================");
        
        log.info("📧 [DEV] Pre-verification email would be sent to: {}", to);
        log.info("🔗 [DEV] Pre-verification link: {}", verificationUrl);
    }
}