package com.example.BobGourmet.Service.Security;

import com.example.BobGourmet.Repository.MatchRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomSubscriptionAccess {
    // Match the IDs produced by RedisRoomRepository.generateNewRoomId().
    private static final Pattern ROOM_DESTINATION =
            Pattern.compile("\\A/topic/room/(room-[0-9a-f]{6})/(events|menuStatus|closed)\\z");
    private final MatchRoomRepository repository;

    public static Optional<String> roomId(String destination) {
        if (destination == null) return Optional.empty();
        Matcher matcher = ROOM_DESTINATION.matcher(destination);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    public static String authenticatedUsername(Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AccessDeniedException("SUBSCRIPTION_FORBIDDEN");
        }
        return authentication.getName();
    }

    public void requireMembership(Principal principal, String roomId) {
        String username = authenticatedUsername(principal);
        final boolean member;
        try {
            member = repository.hasRoomSubscriptionAccess(username, roomId);
        } catch (RuntimeException exception) {
            log.warn("Subscription access lookup failed for user {} and room {}", username, roomId, exception);
            // Never reveal Redis details or allow access when the decision is unavailable.
            throw new AccessDeniedException("SUBSCRIPTION_FORBIDDEN");
        }
        if (!member) throw new AccessDeniedException("SUBSCRIPTION_FORBIDDEN");
    }
}
