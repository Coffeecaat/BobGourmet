package com.example.BobGourmet.config;

import com.example.BobGourmet.Config.WebSocketAuthChannelInterceptor;

import com.example.BobGourmet.utils.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private MessageChannel channel;

    @Mock
    private UserDetails userDetails;

    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(jwtProvider, userDetailsService);
    }

    @Test
    void authenticatesValidCookieTokenAtStompConnect() {
        String token = "valid-token";
        Message<byte[]> message = connectMessage(Map.of("jwt-token", token));
        when(jwtProvider.validateToken(token)).thenReturn(true);
        when(jwtProvider.getUsernameFromToken(token)).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("alice");
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());
        when(jwtProvider.validateToken(token, "alice")).thenReturn(true);

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("alice");
    }

    @Test
    void rejectsMissingCookieTokenAtStompConnect() {
        Message<byte[]> message = connectMessage(Collections.emptyMap());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("authentication failed");
    }

    @Test
    void rejectsInvalidCookieTokenAtStompConnect() {
        Message<byte[]> message = connectMessage(Map.of("jwt-token", "invalid-token"));
        when(jwtProvider.validateToken("invalid-token")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("authentication failed");
    }

    @Test
    void rejectsExpiredCookieTokenAtStompConnect() {
        Message<byte[]> message = connectMessage(Map.of("jwt-token", "expired-token"));
        when(jwtProvider.validateToken("expired-token")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("authentication failed");
    }

    private Message<byte[]> connectMessage(Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>(sessionAttributes));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
