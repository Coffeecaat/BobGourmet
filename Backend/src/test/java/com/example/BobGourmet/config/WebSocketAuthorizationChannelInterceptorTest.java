package com.example.BobGourmet.config;

import com.example.BobGourmet.Config.WebSocketAuthorizationChannelInterceptor;
import com.example.BobGourmet.Repository.MatchRoomRepository;
import com.example.BobGourmet.Service.Security.RoomSubscriptionAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketAuthorizationChannelInterceptorTest {
    private final MatchRoomRepository repository = mock(MatchRoomRepository.class);
    private final Principal user = UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of());
    private WebSocketAuthorizationChannelInterceptor interceptor;
    private final ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();

    @BeforeEach
    void setup() {
        interceptor = new WebSocketAuthorizationChannelInterceptor(new RoomSubscriptionAccess(repository));
    }

    @ParameterizedTest
    @ValueSource(strings = {"events", "menuStatus", "closed"})
    void permitsRoomMember(String suffix) {
        when(repository.hasRoomSubscriptionAccess("alice", "room-abc123")).thenReturn(true);
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/topic/room/room-abc123/" + suffix, user);
        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void permitsOnlyAuthenticatedOwnLogicalQueueWithoutRoomMembership() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/user/queue/events", user);
        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        verifyNoInteractions(repository);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/events", null), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/room/*/events", "/topic/room/**/events", "/topic/room/room-abc123/events/extra",
            "/topic/room/room-abc123/unknown", "/topic/room/room-abc123/events/", "/queue/events",
            "/queue/events-usersession-2", "/user/alice/queue/events", "/user/bob/queue/events",
            "/topic/room/room-abc123/../events", "/topic/room/room-abc123%2Fevents",
            "/topic/room/room-abc123/events\n", "/topic/room/room-abc123/events?x=1", ""})
    void rejectsNonAllowlistedPathsBeforeRedisLookup(String path) {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, path, user), channel))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingDestination() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, user), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesWrongRoomAndUnauthenticatedPrincipalWithoutTrustingNativeUsername() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/topic/room/room-abc123/events", user);
        assertThatThrownBy(() -> interceptor.preSend(message, channel)).isInstanceOf(AccessDeniedException.class);
        verify(repository).hasRoomSubscriptionAccess("alice", "room-abc123");
        Principal unverified = UsernamePasswordAuthenticationToken.unauthenticated("alice", null);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/events", unverified), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void failsClosedOnRepositoryFailureWithGenericMessage() {
        when(repository.hasRoomSubscriptionAccess("alice", "room-abc123")).thenThrow(new IllegalStateException("Redis secret"));
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/room/room-abc123/events", user), channel))
                .isInstanceOf(AccessDeniedException.class).hasMessage("SUBSCRIPTION_FORBIDDEN").hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/room/room-abc123/events", "/topic/room/room-abc123/closed",
            "/topic/room/room-abc123/menuStatus", "/user/queue/events", "/queue/events", "/app/fake"})
    void rejectsClientSendEvenForMembers(String destination) {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, destination, user), channel))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void allowsConnectionAndCleanupFramesWithoutRoomLookup() {
        for (StompCommand command : List.of(StompCommand.CONNECT, StompCommand.UNSUBSCRIBE, StompCommand.DISCONNECT)) {
            Message<byte[]> message = frame(command, null, user);
            assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        }
        StompHeaderAccessor headers = StompHeaderAccessor.createForHeartbeat();
        headers.setUser(user);
        Message<byte[]> heartbeat = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThat(headers.getMessageType()).isEqualTo(SimpMessageType.HEARTBEAT);
        assertThat(interceptor.preSend(heartbeat, channel)).isSameAs(heartbeat);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsUnsupportedCommandsAndHeaderlessMessages() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.ACK, null, user), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(new GenericMessage<>("data"), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniedSubscribeDoesNotReachAnyInboundSubscriber() {
        channel.addInterceptor(interceptor);
        MessageHandler recipient = mock(MessageHandler.class);
        channel.subscribe(recipient);
        assertThatThrownBy(() -> channel.send(frame(StompCommand.SUBSCRIBE, "/topic/room/room-abc123/events", user)))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(recipient);
    }

    private Message<byte[]> frame(StompCommand command, String destination, Principal principal) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setSessionId("session-1");
        headers.setSubscriptionId("subscription-1");
        headers.setDestination(destination);
        headers.setUser(principal);
        headers.setNativeHeader("username", "victim");
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
