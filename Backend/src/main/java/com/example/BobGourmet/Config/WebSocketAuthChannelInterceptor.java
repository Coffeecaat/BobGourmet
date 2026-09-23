package com.example.BobGourmet.Config;

import com.example.BobGourmet.utils.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String JWT_SESSION_ATTRIBUTE = "jwt-token";

    private final JwtProvider jwtProvider;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        try {
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes == null) {
                throw new IllegalArgumentException("Missing WebSocket session attributes");
            }

            Object tokenAttribute = sessionAttributes.get(JWT_SESSION_ATTRIBUTE);
            if (!(tokenAttribute instanceof String jwt) || jwt.isBlank()) {
                throw new IllegalArgumentException("Missing JWT token in WebSocket session");
            }
            if (!jwtProvider.validateToken(jwt)) {
                throw new IllegalArgumentException("Invalid or expired JWT token");
            }

            String username = jwtProvider.getUsernameFromToken(jwt);
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("JWT token has no username");
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!jwtProvider.validateToken(jwt, userDetails.getUsername())) {
                throw new IllegalArgumentException("JWT token does not match the authenticated user");
            }

            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            ));
            return message;
        } catch (RuntimeException exception) {
            log.warn("Rejected WebSocket STOMP CONNECT authentication: {}", exception.getMessage());
            throw new MessageDeliveryException(message, "WebSocket CONNECT authentication failed", exception);
        }
    }
}
