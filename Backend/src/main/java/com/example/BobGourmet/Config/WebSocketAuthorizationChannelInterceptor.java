package com.example.BobGourmet.Config;

import com.example.BobGourmet.Service.Security.RoomSubscriptionAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketAuthorizationChannelInterceptor implements ChannelInterceptor {
    private final RoomSubscriptionAccess access;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) throw new AccessDeniedException("SUBSCRIPTION_FORBIDDEN");

        StompCommand command = headers.getCommand();
        // DISCONNECT must remain available for cleanup, including failed CONNECTs.
        if (command == StompCommand.DISCONNECT) return message;
        RoomSubscriptionAccess.authenticatedUsername(headers.getUser());

        if (command == StompCommand.CONNECT || command == StompCommand.UNSUBSCRIBE
                || (command == null && headers.getMessageType() == SimpMessageType.HEARTBEAT)) {
            return message;
        }
        if (command == StompCommand.SUBSCRIBE) {
            String destination = headers.getDestination();
            if ("/user/queue/events".equals(destination)) return message;
            String roomId = RoomSubscriptionAccess.roomId(destination)
                    .orElseThrow(() -> new AccessDeniedException("SUBSCRIPTION_FORBIDDEN"));
            access.requireMembership(headers.getUser(), roomId);
            return message;
        }
        // No client SEND endpoints exist: reject SEND, transactions, ACK/NACK and unknown frames.
        throw new AccessDeniedException("SUBSCRIPTION_FORBIDDEN");
    }
}
