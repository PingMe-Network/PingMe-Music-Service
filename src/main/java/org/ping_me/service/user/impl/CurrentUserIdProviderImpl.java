package org.ping_me.service.user.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.ping_me.service.user.CurrentUserIdProvider;

/**
 * Lightweight implementation of CurrentUserIdProvider.
 * Extracts user ID from JWT claims in the security context without database queries.
 *
 * Admin 8/19/2025
 **/
@Component
@RequiredArgsConstructor
public class CurrentUserIdProviderImpl implements CurrentUserIdProvider {

    @Override
    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No JWT authentication found in SecurityContext");
        }

        return extractUserId(jwt);
    }

    /**
     * Extracts user ID from JWT claims with fallback to subject.
     *
     * Priority:
     * 1. "id" claim as Number (converted to String)
     * 2. "id" claim as String (if not blank)
     * 3. JWT subject as fallback
     */
    private String extractUserId(Jwt jwt) {
        Object idClaim = jwt.getClaim("id");

        if (idClaim instanceof Number number) {
            return String.valueOf(number.longValue());
        }

        if (idClaim instanceof String value && !value.isBlank()) {
            return value;
        }

        // Fallback to subject if id claim is not available
        return jwt.getSubject();
    }
}

