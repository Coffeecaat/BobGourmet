package com.example.BobGourmet.Service;

import com.example.BobGourmet.DTO.MenuDTO.MenuStatus;
import com.example.BobGourmet.DTO.RoomDTO.RoomDetails;
import com.example.BobGourmet.DTO.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompEventListener {

    private final SimpMessageSendingOperations messagingTemplate;
    private final MatchroomService matchroomService;
    private final MenuService menuService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal userPrincipal = headerAccessor.getUser();
        String destination = headerAccessor.getDestination();

        if(userPrincipal == null || destination == null) {
            return;
        }if(destination.startsWith("/topic/room/") && destination.endsWith("/events")){
            String roomId = extractRoomIdFromDestination(destination);
            if(roomId != null) {
                log.info("User {} subscribed to room {}. Sending initial state.", userPrincipal.getName(), roomId);

                RoomDetails roomDetails = matchroomService.buildRoomDetails(roomId);
                WebSocketMessage<RoomDetails> roomStateMessage = new WebSocketMessage<>("ROOM_STATE_UPDATE", roomDetails);
                messagingTemplate.convertAndSendToUser(userPrincipal.getName(), "/queue/events", roomStateMessage);

                MenuStatus menuStatus = menuService.buildMenuStatus(roomId);
                WebSocketMessage<MenuStatus> menuStatusMessage = new WebSocketMessage<>("MENU_STATUS_UPDATE", menuStatus);
                messagingTemplate.convertAndSendToUser(userPrincipal.getName(), "/queue/events", menuStatusMessage);
            }

        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal userPrincipal = headerAccessor.getUser();

        if(userPrincipal != null){
            String username = userPrincipal.getName();
            matchroomService.handleDisconnect(username);
        }
    }

    private String extractRoomIdFromDestination(String destination) {
        try{
            String[] parts = destination.split("/");
            return parts[3];
        }catch(Exception e){
            log.error("could not extract room id from destination:{}", destination);
            return null;
        }
    }
}
