package org.ping_me.websocket.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ping_me.repository.music.session.MusicSessionRedisRepository;
import org.ping_me.websocket.FriendSessionEventPublisher;
import org.ping_me.websocket.MusicSessionEventPublisher;
import org.ping_me.websocket.auth.MusicSocketPrincipalMapper;
import org.ping_me.websocket.auth.MusicSocketPrincipal;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class MusicSessionPresenceListener {

    private final MusicSocketPrincipalMapper principalMapper;
    private final MusicSessionRedisRepository sessionRepository;
    private final MusicSessionEventPublisher eventPublisher;
    private final FriendSessionEventPublisher friendSessionEventPublisher;

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/topic/music/users/")) {
            return;
        }

        MusicSocketPrincipal user = principalMapper.extractUserPrincipal(accessor.getUser());
        if (user == null || user.getId() == null) {
            return;
        }

        String hostUserId = extractHostUserId(destination);
        if (hostUserId == null) {
            return;
        }

        String sessionId = accessor.getSessionId();
        if (sessionId != null) {
            sessionRepository.linkSession(sessionId, hostUserId);
        }

        var state = sessionRepository.addListener(hostUserId, String.valueOf(user.getId()));
        eventPublisher.broadcastPresenceChanged(hostUserId, state.activeListenerIds());
        eventPublisher.broadcastSessionState(hostUserId, state);
        friendSessionEventPublisher.publishUpdated(state, org.ping_me.dto.music.session.MusicSessionEventType.FRIEND_SESSION_UPDATED);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        MusicSocketPrincipal user = principalMapper.extractUserPrincipal(accessor.getUser());
        if (user == null || user.getId() == null) {
            return;
        }

        String sessionId = accessor.getSessionId();
        Set<String> hostUserIds = sessionId == null ? Set.of() : sessionRepository.findLinkedHostUserIds(sessionId);
        if (hostUserIds.isEmpty()) {
            return;
        }

        for (String hostUserId : new LinkedHashSet<>(hostUserIds)) {
            boolean hostDisconnected = Objects.equals(hostUserId, String.valueOf(user.getId()));
            var updated = sessionRepository.removeListener(hostUserId, String.valueOf(user.getId()));

            if (hostDisconnected) {
                updated = sessionRepository.markEndingAfterCurrentTrack(hostUserId);
            }

            if (updated.activeListenerIds().isEmpty() && updated.isEndingAfterCurrentTrack()) {
                if (sessionRepository.deleteIfTerminal(updated)) {
                    eventPublisher.broadcastSessionEnded(hostUserId, java.util.Map.of(
                            "hostUserId", hostUserId,
                            "sessionEnded", true
                    ));
                    friendSessionEventPublisher.publishEnded(hostUserId);
                }
            } else {
                eventPublisher.broadcastPresenceChanged(hostUserId, updated.activeListenerIds());
                eventPublisher.broadcastSessionState(hostUserId, updated);
                friendSessionEventPublisher.publishUpdated(updated, org.ping_me.dto.music.session.MusicSessionEventType.FRIEND_SESSION_UPDATED);
            }
        }

        sessionRepository.clearSessionLinks(sessionId);
    }

    private String extractHostUserId(String destination) {
        String prefix = "/topic/music/users/";
        String suffix = "/session";
        if (!destination.startsWith(prefix) || !destination.endsWith(suffix)) {
            return null;
        }

        String value = destination.substring(prefix.length(), destination.length() - suffix.length());
        return value.isBlank() ? null : value;
    }
}



