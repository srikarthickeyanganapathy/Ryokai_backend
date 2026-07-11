package com.example.taskflow.util;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.taskflow.service.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, CustomUserDetailsService customUserDetailsService) {
        this.jwtUtil = jwtUtil;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }

        String tokenHeader = request.getHeader("Authorization");

        if (tokenHeader != null && tokenHeader.startsWith("Bearer ")) {
            String token = tokenHeader.substring(7);

            try {
                if (jwtUtil.isAccessTokenValid(token)) {
                    String username = jwtUtil.extractUsername(token);

                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

                        if (userDetails instanceof CustomUserDetailsService.CustomUserDetails customUser) {
                            Integer jwtVersion = jwtUtil.extractTokenVersion(token);
                            if (jwtVersion == null || !jwtVersion.equals(customUser.getTokenVersion())) {
                                SecurityContextHolder.clearContext();
                                sendUnauthorizedResponse(response, "Token invalidated: missing or mismatched version");
                                return;
                            }
                        }

                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                } else {
                    SecurityContextHolder.clearContext();
                    sendUnauthorizedResponse(response, "Invalid token");
                    return;
                }
            } catch (JwtException e) {
                SecurityContextHolder.clearContext();
                sendUnauthorizedResponse(response, "Invalid or expired token");
                return;
            }
        } else if (tokenHeader != null && tokenHeader.startsWith("Bearer")) {
             // Missing space
             sendUnauthorizedResponse(response, "Invalid Authorization header format");
             return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        String correlationId = MDC.get("correlationId");
        
        String jsonResponse = String.format(
                "{\"timestamp\":\"%s\",\"status\":401,\"error\":\"Unauthorized\",\"code\":\"INVALID_TOKEN\",\"message\":\"%s\",\"correlationId\":\"%s\"}",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                message,
                correlationId != null ? correlationId : ""
        );
        response.getWriter().write(jsonResponse);
    }
}