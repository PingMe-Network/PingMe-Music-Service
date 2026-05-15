package org.ping_me.websocket.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ping_me.dto.music.session.MusicSessionCommandRequest;
import org.ping_me.websocket.MusicWebSocketDestinations;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Entry point for music session commands from the frontend.
 * The business rules and Redis session engine will be added in plan-2.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class MusicSessionCommandController {

    @MessageMapping(MusicWebSocketDestinations.COMMAND_MAPPING_PATTERN)
    public void handleCommand(
            @DestinationVariable String hostUserId,
            @Payload MusicSessionCommandRequest request,
            Principal principal
    ) {
        if (request == null || request.command() == null) {
            log.warn("Ignored empty music session command for hostUserId={}", hostUserId);
            return;
        }

        String requester = principal != null ? principal.getName() : "anonymous";
        log.info(
                "Received music session command={} hostUserId={} requester={}",
                request.command(),
                hostUserId,
                requester
        );
    }
}

