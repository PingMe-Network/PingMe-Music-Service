package org.ping_me.service.user;

/**
 * Provides the current user ID extracted from JWT token without database queries.
 * Used for lightweight operations that only need user identification.
 *
 * Admin 8/19/2025
 **/
public interface CurrentUserIdProvider {
    /**
     * Extracts and returns the current user ID from the security context.
     * This method does NOT perform database queries - it only reads from JWT claims.
     *
     * @return the current user ID as a String
     */
    String getCurrentUserId();
}

