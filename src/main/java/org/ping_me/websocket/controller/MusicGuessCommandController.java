package org.ping_me.websocket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ping_me.dto.music.guess.MusicGuessAnswerRequest;
import org.ping_me.dto.music.guess.MusicGuessCommandRequest;
import org.ping_me.dto.music.guess.MusicGuessEventType;
import org.ping_me.service.music.guess.MusicGuessService;
import org.ping_me.websocket.MusicGuessEventPublisher;
import org.ping_me.websocket.MusicWebSocketDestinations;
import org.ping_me.websocket.auth.MusicSocketPrincipalMapper;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MusicGuessCommandController {

    private final MusicGuessService musicGuessService;
    private final MusicSocketPrincipalMapper principalMapper;
    private final MusicGuessEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @MessageMapping(MusicWebSocketDestinations.GUESS_COMMAND_MAPPING_PATTERN)
    public void handleCommand(
            @DestinationVariable String sessionId,
            @Payload MusicGuessCommandRequest request,
            Principal principal
    ) {
        String userId = extractUserId(principal);
        String displayName = extractDisplayName(principal);
        if (userId == null || request == null || request.command() == null) {
            return;
        }

        try {
            switch (request.command()) {
                case START -> musicGuessService.startSessionAs(sessionId, userId, displayName);
                case NEXT_ROUND -> musicGuessService.nextRoundAs(sessionId, userId, displayName);
                case ANSWER -> musicGuessService.answerAs(
                        sessionId,
                        objectMapper.convertValue(request.payload(), MusicGuessAnswerRequest.class),
                        userId,
                        displayName
                );
            }
        } catch (Exception e) {
            log.debug("Music guess command rejected: {}", e.getMessage());
            eventPublisher.sendToUser(userId, MusicGuessEventType.ANSWER_RESULT, e.getMessage());
        }
    }

    private String extractUserId(Principal principal) {
        Long id = principalMapper.extractUserId(principal);
        return id == null ? null : String.valueOf(id);
    }

    private String extractDisplayName(Principal principal) {
        var user = principalMapper.extractUserPrincipal(principal);
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return "Player";
        }
        return user.getUsername();
    }
}
