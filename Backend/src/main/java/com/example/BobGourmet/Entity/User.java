package com.example.BobGourmet.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;


@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name= "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "email")
})
public class User implements UserDetails {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String username;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = true) private String password;
    @Column(nullable = false) private String nickname;
    @CreationTimestamp private LocalDateTime createdAt;
    @Column(name= "oauth_provider") private String oauthProvider;
    @Column(name = "oauth_id") private String oauthId;
    
    // Email verification fields
    @Column(name = "email_verified", nullable = false) private boolean emailVerified = false;
    @Column(name = "verification_token") private String verificationToken;
    @Column(name = "verification_token_expires_at") private LocalDateTime verificationTokenExpiresAt;

    // Regular signup - requires email verification
    public User(String username, String email, String password, String nickname){
        this.username = username;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.oauthProvider = "local";
        this.oauthId = null;
        this.emailVerified = false; // Local users must verify email
    }

    //OAuth signup - email is pre-verified by OAuth provider
    public User(String username, String email, String nickname, String oauthProvider, String oauthId){
        this.username = username;
        this.email = email;
        this.password = null; // no password for OAuth users
        this.nickname = nickname;
        this.oauthProvider = oauthProvider;
        this.oauthId = oauthId;
        this.emailVerified = true; // OAuth users have verified emails
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(){
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

}
