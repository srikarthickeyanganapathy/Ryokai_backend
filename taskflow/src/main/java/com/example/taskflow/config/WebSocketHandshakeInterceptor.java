package com.example.taskflow.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.atomic.AtomicInteger> connectionCounts = 
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterWrite(java.time.Duration.ofHours(1))
            .<String, java.util.concurrent.atomic.AtomicInteger>build()
            .asMap();

    private String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest req = servletRequest.getServletRequest();
            String ip = getClientIp(req);
            
            // Connection Rate Limiting (max 10 connections per IP)
            connectionCounts.putIfAbsent(ip, new java.util.concurrent.atomic.AtomicInteger(0));
            if (connectionCounts.get(ip).incrementAndGet() > 10) {
                connectionCounts.get(ip).decrementAndGet();
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return false;
            }

            // We cannot check HTTP Authorization header here because standard browser WebSockets
            // do not support setting custom HTTP headers. Authentication is handled by 
            // StompAuthChannelInterceptor when the STOMP CONNECT frame is received.
            return true;
        }
        
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String ip = getClientIp(servletRequest.getServletRequest());
            if (connectionCounts.containsKey(ip)) {
                connectionCounts.get(ip).decrementAndGet();
            }
        }
    }
}
