package com.example.BobGourmet.Service;

import com.example.BobGourmet.DTO.MenuDTO.MenuStatus;
import com.example.BobGourmet.DTO.RoomDTO.RoomDetails;
import com.example.BobGourmet.DTO.RoomSnapshotMessage;
import com.example.BobGourmet.Repository.MatchRoomRepository;
import com.example.BobGourmet.Service.Room.MatchroomService;
import com.example.BobGourmet.Service.Security.RoomSubscriptionAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.simp.user.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StompEventListenerTest {
    private final SimpMessageSendingOperations template = mock(SimpMessageSendingOperations.class);
    private final MatchroomService rooms = mock(MatchroomService.class);
    private final MenuService menus = mock(MenuService.class);
    private final MatchRoomRepository repository = mock(MatchRoomRepository.class);
    private StompEventListener listener;
    private final String roomId = "room-abc123";
    private final Principal user = UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of());

    @BeforeEach
    void setup() {
        listener = new StompEventListener(template, rooms, menus, new RoomSubscriptionAccess(repository));
    }

    private void allow() {
        when(repository.hasRoomSubscriptionAccess("alice", roomId)).thenReturn(true);
        when(rooms.buildRoomDetails(roomId)).thenReturn(new RoomDetails());
        when(menus.buildMenuStatus(roomId)).thenReturn(new MenuStatus());
    }

    @Test
    void sendsRoomAndMenuOnlyToRequestingSessionWithRoomIdentity() {
        allow();
        List<RoomSnapshotMessage<?>> snapshots = new ArrayList<>();
        doAnswer(invocation -> {
            snapshots.add(invocation.getArgument(2));
            Map<String, Object> headers = invocation.getArgument(3);
            assertThat(headers.get(SimpMessageHeaderAccessor.SESSION_ID_HEADER)).isEqualTo("session-1");
            return null;
        }).when(template).convertAndSendToUser(eq("session-1"), eq("/queue/events"), any(), anyMap());
        listener.handleWebSocketSubscribeListener(event(user, "session-1", "/topic/room/" + roomId + "/events"));
        assertThat(snapshots).extracting(RoomSnapshotMessage::type)
                .containsExactly("ROOM_STATE_UPDATE", "MENU_STATUS_UPDATE");
        assertThat(snapshots).allMatch(snapshot -> snapshot.roomId().equals(roomId));
        verify(repository, times(3)).hasRoomSubscriptionAccess("alice", roomId);
        verify(template, times(2)).convertAndSendToUser(eq("session-1"), eq("/queue/events"), any(), anyMap());
        verifyNoMoreInteractions(template);
    }

    @Test
    void nonMemberGetsNoSnapshotAndNoRoomDetailsAreRead() {
        listener.handleWebSocketSubscribeListener(event(user, "session-1", "/topic/room/" + roomId + "/events"));
        verifyNoInteractions(template, rooms, menus);
    }

    @Test
    void missingAuthenticationOrSessionGetsNoSnapshot() {
        listener.handleWebSocketSubscribeListener(event(null, "session-1", "/topic/room/" + roomId + "/events"));
        listener.handleWebSocketSubscribeListener(event(user, null, "/topic/room/" + roomId + "/events"));
        verifyNoInteractions(template, repository, rooms, menus);
    }

    @Test
    void revokedAccessDuringSnapshotBuildSuppressesBothMessages() {
        allow();
        when(repository.hasRoomSubscriptionAccess("alice", roomId)).thenReturn(true, false);
        listener.handleWebSocketSubscribeListener(event(user, "session-1", "/topic/room/" + roomId + "/events"));
        verifyNoInteractions(template);
    }

    @Test
    void lookupFailureDoesNotSendSnapshots() {
        when(repository.hasRoomSubscriptionAccess("alice", roomId)).thenThrow(new IllegalStateException("redis down"));
        listener.handleWebSocketSubscribeListener(event(user, "session-1", "/topic/room/" + roomId + "/events"));
        verifyNoInteractions(template, rooms, menus);
    }

    @Test
    void otherDestinationsDoNotTriggerSnapshots() {
        for (String path : List.of("/user/queue/events", "/topic/room/" + roomId + "/menuStatus",
                "/topic/room/" + roomId + "/closed", "/topic/room/*/events", "/topic/room/" + roomId + "/events/extra")) {
            listener.handleWebSocketSubscribeListener(event(user, "session-1", path));
        }
        verifyNoInteractions(template, repository, rooms, menus);
    }

    @Test
    void actualSpringResolverNeverFallsBackToOtherUserSessions() {
        // No live user registration is needed for a session-addressed message.
        SimpUserRegistry registry = mock(SimpUserRegistry.class);
        DefaultUserDestinationResolver resolver = new DefaultUserDestinationResolver(registry);
        for (String session : List.of("session-1", "session-2", "already-disconnected-session")) {
            SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headers.setSessionId(session);
            headers.setDestination("/user/" + session + "/queue/events");
            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
            UserDestinationResult resolved = resolver.resolveDestination(message);
            assertThat(resolved).isNotNull();
            assertThat(resolved.getTargetDestinations()).containsExactly("/queue/events-user" + session);
        }
        verifyNoInteractions(registry);
    }

    private SessionSubscribeEvent event(Principal principal, String session, String destination) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setSessionId(session);
        headers.setSubscriptionId("subscription-1");
        headers.setDestination(destination);
        headers.setUser(principal);
        headers.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        return new SessionSubscribeEvent(this, message, principal);
    }
}
