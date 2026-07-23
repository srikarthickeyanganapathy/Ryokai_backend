package com.example.taskflow.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared utility component for safe Client IP extraction.
 * Honors X-Forwarded-For ONLY when the immediate remoteAddr is present
 * in the configured trusted-proxies list (app.security.trusted-proxies).
 */
@Component
public class ClientIpResolver {

    @Value("${app.security.trusted-proxies:}")
    private String trustedProxiesRaw;

    private volatile Set<String> trustedProxies = null;

    private Set<String> getTrustedProxies() {
        if (trustedProxies == null) {
            synchronized (this) {
                if (trustedProxies == null) {
                    if (trustedProxiesRaw == null || trustedProxiesRaw.isBlank()) {
                        trustedProxies = Collections.emptySet();
                    } else {
                        trustedProxies = new HashSet<>(Arrays.asList(
                            trustedProxiesRaw.split("\\s*,\\s*")));
                    }
                }
            }
        }
        return trustedProxies;
    }

    /**
     * Extracts client IP safely. If immediate remoteAddr is in trusted-proxies list,
     * trusts the leftmost address of X-Forwarded-For. Otherwise uses remoteAddr directly.
     */
    public String extractClientIp(HttpServletRequest request) {
        if (request == null) return "0.0.0.0";
        String remoteAddr = request.getRemoteAddr();
        Set<String> trusted = getTrustedProxies();

        if (trusted.contains(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                return xff.split(",")[0].trim();
            }
        }
        return remoteAddr != null ? remoteAddr : "0.0.0.0";
    }
}
