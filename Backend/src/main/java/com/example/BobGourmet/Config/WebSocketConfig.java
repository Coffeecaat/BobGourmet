package com.example.BobGourmet.Config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;
    private final WebSocketAuthorizationChannelInterceptor webSocketAuthorizationChannelInterceptor;

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
                .addInterceptors(new CookieHandshakeInterceptor()) // Add cookie interceptor
                .withSockJS(); // SockJS 사용 시 (오래된 브라우저 호환성)
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthChannelInterceptor, webSocketAuthorizationChannelInterceptor);
    }
}
