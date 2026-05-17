package org.ping_me.websocket.auth;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ping_me.service.music.session.MusicSessionAccessService;
import org.ping_me.service.music.session.MusicSessionTokenManager;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class MusicStompAuthInterceptor implements ChannelInterceptor {

    private static final Pattern MUSIC_COMMAND_DESTINATION = Pattern.compile("^/app/music/users/([^/]+)/command$");
    private static final Pattern MUSIC_TOPIC_DESTINATION = Pattern.compile("^/topic/music/users/([^/]+)/session$");
    private static final Pattern FRIEND_SESSIONS_TOPIC_DESTINATION = Pattern.compile("^/topic/music/users/([^/]+)/friend-sessions$");

    private final JwtDecoder jwtDecoder;
    private final MusicSessionAccessService musicSessionAccessService;
    private final MusicSessionTokenManager tokenManager;
    private final CircuitBreaker coreServiceCircuitBreaker;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            throw new AccessDeniedException("Token không hợp lệ");
        }

        String token = authHeader.substring(7);
        try {
            Jwt jwt = jwtDecoder.decode(token);
            MusicSocketPrincipal userPrincipal = buildUserPrincipal(jwt);
            Authentication auth = new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList());
            accessor.setUser(auth);
        } catch (JwtException e) {
            log.debug("WebSocket CONNECT bị từ chối do JWT không hợp lệ hoặc đã hết hạn");
            throw new AccessDeniedException("Token không hợp lệ");
        } catch (Exception e) {
            log.warn("Xác thực WebSocket thất bại: {}", e.getMessage());
            throw new AccessDeniedException("Token không hợp lệ");
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return;

        Long userId = extractAuthenticatedUserId(accessor);
        if (userId == null) {
            throw new AccessDeniedException("Yêu cầu xác thực");
        }

        Long hostUserId = extractHostUserId(destination);
        if (hostUserId == null) {
            Long friendSessionUserId = extractFriendSessionUserId(destination);
            if (friendSessionUserId == null) return;
            if (!friendSessionUserId.equals(userId)) {
                throw new AccessDeniedException("Bạn chỉ có thể theo dõi phiên nghe của bạn bè của chính mình");
            }
            return;
        }

        // Path 1: Try friendship validation via Core Service (preferred)
        try {
            boolean isFriend = coreServiceCircuitBreaker.executeSupplier(() ->
                    musicSessionAccessService.canJoinSession(String.valueOf(hostUserId), String.valueOf(userId))
            );
            if (isFriend) {
                log.debug("[MusicStompAuth] User {} authorized via friendship with host {}", userId, hostUserId);
                return;  // Access granted
            }
        } catch (CallNotPermittedException e) {
            // Circuit breaker is open, Core Service is unavailable
            log.warn("[MusicStompAuth] Circuit breaker open: Core Service unavailable, falling back to token validation");
        } catch (Exception e) {
            // Timeout or other error from Core Service
            log.warn("[MusicStompAuth] Friendship check failed for user {} and host {}: {}. Falling back to token validation",
                    userId, hostUserId, e.getMessage());
        }

        // Path 2: Fall back to token validation (when Core Service is unavailable)
        String shareToken = accessor.getFirstNativeHeader("X-Session-Token");
        if (shareToken != null) {
            MusicSessionTokenManager.SessionTokenClaims claims = tokenManager.validateSessionToken(shareToken);
            if (claims != null && claims.getHostUserId().equals(String.valueOf(hostUserId))) {
                if (claims.getPermissions().contains("join")) {
                    log.debug("[MusicStompAuth] User {} authorized via session token for host {}", userId, hostUserId);
                    return;  // Access granted
                }
            }
        }

        // Both paths failed -> deny access
        log.warn("[MusicStompAuth] Access denied for user {} to host {}: not a friend and no valid token", userId, hostUserId);
        throw new AccessDeniedException("Bạn không có quyền tham gia phiên nghe này");
    }

    private Long extractAuthenticatedUserId(StompHeaderAccessor accessor) {
        Authentication auth = (Authentication) accessor.getUser();
        if (auth == null || !(auth.getPrincipal() instanceof MusicSocketPrincipal user)) {
            return null;
        }
        return user.getId();
    }

    private Long extractHostUserId(String destination) {
        Matcher topicMatcher = MUSIC_TOPIC_DESTINATION.matcher(destination);
        if (topicMatcher.find()) {
            return parseLongSafely(topicMatcher.group(1));
        }

        Matcher commandMatcher = MUSIC_COMMAND_DESTINATION.matcher(destination);
        if (commandMatcher.find()) {
            return parseLongSafely(commandMatcher.group(1));
        }

        return null;
    }

    private Long extractFriendSessionUserId(String destination) {
        Matcher matcher = FRIEND_SESSIONS_TOPIC_DESTINATION.matcher(destination);
        if (matcher.find()) {
            return parseLongSafely(matcher.group(1));
        }
        return null;
    }

    private Long parseLongSafely(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private MusicSocketPrincipal buildUserPrincipal(Jwt jwt) {
        MusicSocketPrincipal user = new MusicSocketPrincipal();
        Object idClaim = jwt.getClaim("id");
        if (idClaim instanceof Number number) {
            user.setId(number.longValue());
        } else if (idClaim instanceof String value) {
            try {
                user.setId(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                user.setId(null);
            }
        }
        user.setEmail(jwt.getSubject());
        user.setUsername(jwt.getClaimAsString("name"));
        return user;
    }
}

