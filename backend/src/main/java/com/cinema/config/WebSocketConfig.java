package com.cinema.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over SockJS for the live seat pushes of TECH.md §5.
 *
 * <p>The in-memory simple broker is enough here: one app instance, topics that carry no history,
 * and messages that are pure hints — a client which misses one is corrected by the next seat-map
 * read. A real broker (RabbitMQ/ActiveMQ) would only be needed to fan out across instances.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Destinations are /topic/showings/{id}. There is no /app prefix because clients never
        // send: seat changes are made over REST, and the socket is one-way to the browser.
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // In production the SPA is served from the same origin by Nginx (TECH.md §4), so
                // this only matters for the Vite dev server on another port. Narrowed alongside
                // the rest of CORS in Part 6.
                .setAllowedOriginPatterns("*")
                // SockJS gives the browser an XHR-streaming fallback where a raw WebSocket is
                // blocked by a proxy, which is exactly the environment a cinema kiosk sits in.
                .withSockJS();
    }
}
