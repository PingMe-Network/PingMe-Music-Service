package org.ping_me.websocket.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ping_me.dto.music.session.MusicCommandError;
import org.ping_me.dto.music.session.MusicSessionCommandRequest;
import org.ping_me.service.music.session.MusicSessionCommandService;
import org.ping_me.websocket.MusicWebSocketDestinations;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    private final MusicSessionCommandService commandService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping(MusicWebSocketDestinations.COMMAND_MAPPING_PATTERN)
    public void handleCommand(
            @DestinationVariable String hostUserId,
            @Payload MusicSessionCommandRequest request,
            Principal principal
    ) {
        try {
            commandService.handleCommand(hostUserId, request, principal);
        } catch (IllegalArgumentException e) {
            log.debug("Validation error handling command: {}", e.getMessage());
            if (principal != null && principal.getName() != null) {
                messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/music/errors",
                        new MusicCommandError("VALIDATION_ERROR", e.getMessage(), null));
            }
        } catch (org.springframework.security.access.AccessDeniedException e) {
            log.debug("Access denied for command: {}", e.getMessage());
            if (principal != null && principal.getName() != null) {
                messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/music/errors",
                        new MusicCommandError("ACCESS_DENIED", e.getMessage(), null));
            }
        } catch (Exception e) {
            log.error("Error processing music session command", e);
            if (principal != null && principal.getName() != null) {
                messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/music/errors",
                        new MusicCommandError("INTERNAL_ERROR", "Đã có lỗi máy chủ", null));
            }
        }
    }
}
