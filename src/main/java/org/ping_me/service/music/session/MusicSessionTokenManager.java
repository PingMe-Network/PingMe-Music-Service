package org.ping_me.service.music.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing JWT session share tokens
 * Tokens are used as a fallback when Core Service is unavailable
 * for friendship validation during co-listening sessions
 */
@Service
@Slf4j
public class MusicSessionTokenManager {

    @Value("${music.jwt.secret:change-me-in-prod-minimum-32-characters}")
    private String jwtSecret;

    @Value("${music.jwt.expiry-seconds:600}")
    private long tokenExpirySeconds;

    @Value("${music.jwt.issuer:pingme-music-service}")
    private String issuer;

    @Value("${music.jwt.clock-skew-seconds:30}")
    private long clockSkewSeconds;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public MusicSessionTokenManager(
            @Qualifier("stringRedisTemplate") RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a new session share token
     * Token is valid for configured expiry duration (default 10 minutes)
     * JTI is stored in Redis for revocation tracking
     */
    public String generateSessionToken(String hostUserId, String sessionId) {
        try {
            Instant now = Instant.now();
            Instant expiryTime = now.plusSeconds(tokenExpirySeconds);

            // Generate unique JTI (JWT ID) for potential revocation
            String jti = UUID.randomUUID().toString();

            // Build claims
            Map<String, Object> claims = new HashMap<>();
            claims.put("sessionId", sessionId);
            claims.put("hostUserId", hostUserId);
            claims.put("tokenType", "session_share");
            claims.put("permissions", List.of("join", "view_queue"));
            claims.put("listenerLimit", -1);  // unlimited
            claims.put("single_use", false);

            // Build JWT
                String token = Jwts.builder()
                    .setIssuer(issuer)
                    .setIssuedAt(new Date(now.toEpochMilli()))
                    .setExpiration(new Date(expiryTime.toEpochMilli()))
                    .setId(jti)  // JTI for revocation tracking
                    .addClaims(claims)
                    .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                    .compact();

            // Store JTI in Redis for revocation lookup + fast validation
            String cacheKey = "music:token:" + jti;
            String cacheValue = objectMapper.writeValueAsString(new SessionTokenCache(
                    sessionId, hostUserId, expiryTime.getEpochSecond(), false
            ));

            // Cache TTL = token expiry + 5 min grace period
            long ttlSeconds = tokenExpirySeconds + 300;
            redisTemplate.opsForValue().set(cacheKey, cacheValue, Duration.ofSeconds(ttlSeconds));

            // Track JTI in session-specific set for bulk operations (list, revoke all)
            String tokensSetKey = "music:session:" + sessionId + ":tokens";
            redisTemplate.opsForSet().add(tokensSetKey, jti);
            redisTemplate.expire(tokensSetKey, Duration.ofSeconds(ttlSeconds));

            log.info("[MusicSessionToken] Generated token JTI={} for session={} by host={}", 
                    jti, sessionId, hostUserId);
            return token;

        } catch (Exception e) {
            log.error("[MusicSessionToken] Failed to generate token for session={}", sessionId, e);
            throw new MusicSessionTokenException("Failed to generate session token", e);
        }
    }

    /**
     * Validate token and extract claims
     * Returns null if invalid, expired, or revoked
     * This method performs local validation (no external service calls needed)
     */
    public SessionTokenClaims validateSessionToken(String token) {
        try {
            // Parse JWT signature
                Claims claims = Jwts.parser()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .setAllowedClockSkewSeconds(clockSkewSeconds)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String jti = claims.getId();

            // Check if token is revoked
            String cacheKey = "music:token:" + jti;
            String cached = redisTemplate.opsForValue().get(cacheKey);

            if (cached != null) {
                SessionTokenCache cache = objectMapper.readValue(cached, SessionTokenCache.class);
                if (cache.isRevoked()) {
                    log.debug("[MusicSessionToken] Token JTI={} is revoked", jti);
                    return null;
                }
            }

            // Token is valid; extract and return claims
            return new SessionTokenClaims(
                    claims.get("sessionId", String.class),
                    claims.get("hostUserId", String.class),
                    claims.getExpiration().getTime(),
                    (List<String>) claims.get("permissions"),
                    jti
            );

        } catch (ExpiredJwtException e) {
            log.debug("[MusicSessionToken] Token expired: {}", e.getMessage());
            return null;
        } catch (JwtException e) {
            log.warn("[MusicSessionToken] Invalid token: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[MusicSessionToken] Unexpected error validating token", e);
            return null;
        }
    }

    /**
     * Revoke a token by marking its JTI as revoked in Redis
     * Existing connections remain active (lazy revocation)
     * New attempts to use this token will be rejected
     */
    public void revokeSessionToken(String token) {
        try {
                Claims claims = Jwts.parser()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String jti = claims.getId();
            revokeTokenByJti(jti);

        } catch (Exception e) {
            log.error("[MusicSessionToken] Failed to revoke token", e);
        }
    }

    /**
     * Revoke a token by its JTI (JWT ID)
     * Updates both the token cache and session token set
     */
    public void revokeTokenByJti(String jti) {
        try {
            String cacheKey = "music:token:" + jti;
            String cacheValue = redisTemplate.opsForValue().get(cacheKey);

            if (cacheValue != null) {
                SessionTokenCache cache = objectMapper.readValue(cacheValue, SessionTokenCache.class);
                cache.setRevoked(true);
                long ttlSeconds = tokenExpirySeconds + 300;
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(cache), 
                        Duration.ofSeconds(ttlSeconds));
                
                // Also remove from session's active token set
                if (cache.getSessionId() != null) {
                    String tokensSetKey = "music:session:" + cache.getSessionId() + ":tokens";
                    redisTemplate.opsForSet().remove(tokensSetKey, jti);
                }
                
                log.info("[MusicSessionToken] Revoked token JTI={}", jti);
            }
        } catch (Exception e) {
            log.error("[MusicSessionToken] Failed to revoke token by JTI={}", jti, e);
        }
    }

    /**
     * Get all active token JTIs for a session
     * Useful for bulk operations and listing tokens
     */
    public Set<String> getActiveTokensForSession(String sessionId) {
        try {
            String tokensSetKey = "music:session:" + sessionId + ":tokens";
            Set<String> members = redisTemplate.opsForSet().members(tokensSetKey);
            return members != null ? members : new HashSet<>();
        } catch (Exception e) {
            log.error("[MusicSessionToken] Failed to get active tokens for session={}", sessionId, e);
            return new HashSet<>();
        }
    }

    /**
     * Revoke all tokens for a session
     * Useful for immediate session end or security breach
     */
    public void revokeAllTokensForSession(String sessionId) {
        try {
            Set<String> jtis = getActiveTokensForSession(sessionId);
            for (String jti : jtis) {
                revokeTokenByJti(jti);
            }
            // Clean up the set itself
            String tokensSetKey = "music:session:" + sessionId + ":tokens";
            redisTemplate.delete(tokensSetKey);
            log.info("[MusicSessionToken] Revoked all tokens for session={} (count={})", sessionId, jtis.size());
        } catch (Exception e) {
            log.error("[MusicSessionToken] Failed to revoke all tokens for session={}", sessionId, e);
        }
    }

    /**
     * DTO for storing token cache in Redis
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionTokenCache {
        private String sessionId;
        private String hostUserId;
        private long expiryEpochSeconds;
        private boolean revoked;
    }

    /**
     * DTO for validated token claims
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionTokenClaims {
        private String sessionId;
        private String hostUserId;
        private long expiryMs;
        private List<String> permissions;
        private String jti;  // For revocation tracking
    }

    /**
     * Custom exception for token-related errors
     */
    public static class MusicSessionTokenException extends RuntimeException {
        public MusicSessionTokenException(String message) {
            super(message);
        }

        public MusicSessionTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
