package org.ping_me.websocket;

import lombok.RequiredArgsConstructor;
import org.ping_me.dto.music.session.MusicSessionEventMessage;
import org.ping_me.dto.music.session.MusicSessionEventType;
import org.ping_me.dto.music.session.MusicSessionState;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Helper for broadcasting music session events to the shared topic.
 */
@Component
@RequiredArgsConstructor
public class MusicSessionEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(String hostUserId, MusicSessionEventType eventType, Object data) {
        messagingTemplate.convertAndSend(
                MusicWebSocketDestinations.sessionTopic(hostUserId),
                new MusicSessionEventMessage(eventType.name(), data, System.currentTimeMillis())
        );
    }

    public void broadcastSessionState(String hostUserId, MusicSessionState state) {
        broadcast(hostUserId, MusicSessionEventType.MUSIC_SESSION_STATE, state);
    }

    public void broadcastPlaybackChanged(String hostUserId, Object data) {
        broadcast(hostUserId, MusicSessionEventType.MUSIC_PLAYBACK_CHANGED, data);
    }

    public void broadcastQueueChanged(String hostUserId, Object data) {
        broadcast(hostUserId, MusicSessionEventType.MUSIC_QUEUE_CHANGED, data);
    }

    public void broadcastPresenceChanged(String hostUserId, Object data) {
        broadcast(hostUserId, MusicSessionEventType.MUSIC_PRESENCE_CHANGED, data);
    }

    public void broadcastSessionEnded(String hostUserId, Object data) {
        broadcast(hostUserId, MusicSessionEventType.MUSIC_SESSION_ENDED, data);
    }
}
