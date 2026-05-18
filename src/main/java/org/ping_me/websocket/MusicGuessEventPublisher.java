package org.ping_me.websocket;

import lombok.RequiredArgsConstructor;
import org.ping_me.dto.music.guess.MusicGuessEventMessage;
import org.ping_me.dto.music.guess.MusicGuessEventType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MusicGuessEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(String sessionId, MusicGuessEventType eventType, Object data) {
        messagingTemplate.convertAndSend(
                MusicWebSocketDestinations.guessTopic(sessionId),
                new MusicGuessEventMessage(eventType.name(), data, System.currentTimeMillis())
        );
    }

    public void sendToUser(String userId, MusicGuessEventType eventType, Object data) {
        messagingTemplate.convertAndSendToUser(
                userId,
                MusicWebSocketDestinations.GUESS_USER_QUEUE,
                new MusicGuessEventMessage(eventType.name(), data, System.currentTimeMillis())
        );
    }
}
