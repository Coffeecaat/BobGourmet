package com.example.BobGourmet.Service;

import com.example.BobGourmet.DTO.MenuDTO.MenuStatus;
import com.example.BobGourmet.DTO.RoomDTO.RoomDetails;
import com.example.BobGourmet.DTO.RoomSnapshotMessage;
import com.example.BobGourmet.Service.Room.MatchroomService;
import com.example.BobGourmet.Service.Security.RoomSubscriptionAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompEventListener {
    private final SimpMessageSendingOperations messagingTemplate;
    private final MatchroomService matchroomService;
    private final MenuService menuService;
    private final RoomSubscriptionAccess subscriptionAccess;

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headers.getUser();
        String destination = headers.getDestination();
        String sessionId = headers.getSessionId();
        if (sessionId == null || sessionId.isBlank() || destination == null
                || !destination.endsWith("/events")) return;
        RoomSubscriptionAccess.roomId(destination)
                .ifPresent(roomId -> sendInitialState(principal, sessionId, roomId));
    }

    private void sendInitialState(Principal principal, String sessionId, String roomId) {
        try {
            // Recheck here: membership may change after inbound SUBSCRIBE authorization.
            subscriptionAccess.requireMembership(principal, roomId);
            RoomDetails roomDetails = matchroomService.buildRoomDetails(roomId);
            MenuStatus menuStatus = menuService.buildMenuStatus(roomId);
            subscriptionAccess.requireMembership(principal, roomId);
            sendToSession(sessionId, new RoomSnapshotMessage<>("ROOM_STATE_UPDATE", roomId, roomDetails));
            subscriptionAccess.requireMembership(principal, roomId);
            sendToSession(sessionId, new RoomSnapshotMessage<>("MENU_STATUS_UPDATE", roomId, menuStatus));
        } catch (RuntimeException exception) {
            log.warn("Initial room snapshot suppressed for session {} and room {}: {}",
                    sessionId, roomId, exception.getMessage());
        }
    }

    private void sendToSession(String sessionId, RoomSnapshotMessage<?> snapshot) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId);
        headers.setLeaveMutable(true);
        // Using sessionId as both target and simpSessionId avoids username-based fallback
        // to other sessions if the requesting session has already disconnected.
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/events", snapshot, headers.getMessageHeaders());
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headers.getUser();
        if (principal != null) {
            matchroomService.handleDisconnect(principal.getName());
        }
    }
}
