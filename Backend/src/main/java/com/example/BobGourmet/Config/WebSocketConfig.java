package com.example.BobGourmet.Config;

import com.example.BobGourmet.utils.JwtProvider;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtProvider jwtProvider;
    private final UserDetailsService userDetailsService;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    public void configureMessageBroker(MessageBrokerRegistry config){

        // 클라이언트가 메시지를 구독할 때 사용할 prefix(topic, queue 등)
        config.enableSimpleBroker("/topic", "/queue"); // "/user" prefix는 자동으로 사용 가능

        // 클라이언트가 서버로 메시지를 보낼 때 사용할 prefix
        config.setApplicationDestinationPrefixes("/app");
    }

    public void registerStompEndpoints(StompEndpointRegistry registry) {

        //클라이언트가 WebSocket 핸드셰이크를 위해 연결할 엔드포인트
        registry.addEndpoint("/ws-BobGourmet") // 엔드포인트 경로 (application.properties와 맞출 필요 없음)
                .setAllowedOrigins(allowedOrigins)
                .withSockJS(); // SockJS 사용 시 (오래된 브라우저 호환성)
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel){
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if(StompCommand.CONNECT.equals(accessor.getCommand())){
                    try{
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if(authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new IllegalArgumentException("Missing or invalid Authorization header");
                    }
                        String jwt = authHeader.substring(7);
                    if(!jwtProvider.validateToken(jwt)){
                        throw new IllegalArgumentException("Invalid JWT token");
                    }
                        String username = jwtProvider.getUsernameFromToken(jwt);
                        if(username !=null){
                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                            if(jwtProvider.validateToken(jwt,userDetails.getUsername())){
                                UsernamePasswordAuthenticationToken authToken = new
                                        UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                                accessor.setUser(authToken);
                            }
                        }


                }catch(JwtException e){
                        log.error("Invalid JWT in WebSocket CONNECT header: {}", e.getMessage());
                    }catch(Exception e){
                    log.error("Error processing WebSocket CONNECT header", e);
                    }
                }
                return message;
            }
        });
    }


}
