package com.example.BobGourmet.Repository;

import com.example.BobGourmet.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

Optional<User> findByUsername(String username);
Optional<User> findByEmail(String email);
Optional<User> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

    boolean existsByUsername(String username);
    
    // Email verification methods
    Optional<User> findByVerificationToken(String verificationToken);
    List<User> findByVerificationTokenExpiresAtBefore(LocalDateTime dateTime);
}
