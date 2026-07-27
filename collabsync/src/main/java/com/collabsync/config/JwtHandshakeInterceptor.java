package com.collabsync.config;

import com.collabsync.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements ChannelInterceptor, HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Missing or invalid Authorization header in CONNECT frame, headers: {}", accessor.toNativeHeaderMap());
                throw new IllegalArgumentException("Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);
            if (!jwtTokenProvider.validateToken(token)) {
                log.warn("Invalid JWT token in CONNECT frame: {}", token);
                throw new IllegalArgumentException("Invalid JWT token");
            }

            String email = jwtTokenProvider.getUsernameFromToken(token);
            UUID userId = jwtTokenProvider.getUserIdFromToken(token);

            // Set user info in the session attributes for later use
            accessor.getSessionAttributes().put("userId", userId);
            accessor.getSessionAttributes().put("email", email);

            // Create authentication for Spring Security context
            Authentication authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    email, null, java.util.Collections.singletonList(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")
                    )
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            accessor.setUser(authentication);

            log.debug("WebSocket authenticated for user: {}", email);
        }

        return message;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        log.debug("Handshake query: {}", query); // DEBUG LOG
        if (query != null && query.contains("accessToken=")) {
            String token = query.split("accessToken=")[1].split("&")[0];
            if (jwtTokenProvider.validateToken(token)) {
                attributes.put("userId", jwtTokenProvider.getUserIdFromToken(token));
                attributes.put("email", jwtTokenProvider.getUsernameFromToken(token));
                return true; // Token valid, allow handshake
            }
        }
        log.warn("Handshake rejected: Missing or invalid token in query");
        return false; // Reject handshake if no valid token
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Nothing needed
    }
}