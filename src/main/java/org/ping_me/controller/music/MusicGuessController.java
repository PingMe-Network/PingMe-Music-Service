package org.ping_me.controller.music;

import lombok.RequiredArgsConstructor;
import org.ping_me.dto.base.ApiResponse;
import org.ping_me.dto.music.guess.*;
import org.ping_me.service.music.guess.MusicGuessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/music-service/guess")
@RequiredArgsConstructor
public class MusicGuessController {

    private final MusicGuessService musicGuessService;

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<MusicGuessSessionResponse>> createSession(
            @RequestBody(required = false) CreateMusicGuessSessionRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(musicGuessService.createSession(request)));
    }

    @PostMapping("/sessions/join")
    public ResponseEntity<ApiResponse<MusicGuessSessionResponse>> joinSession(
            @RequestBody JoinMusicGuessSessionRequest request
    ) {
        return ResponseEntity.ok(new ApiResponse<>(musicGuessService.joinByRoomCode(request)));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<MusicGuessSessionResponse>> getSession(
            @PathVariable String sessionId
    ) {
        return ResponseEntity.ok(new ApiResponse<>(musicGuessService.getSession(sessionId)));
    }

    @PostMapping("/sessions/{sessionId}/start")
    public ResponseEntity<ApiResponse<MusicGuessSessionResponse>> startSession(
            @PathVariable String sessionId
    ) {
        return ResponseEntity.ok(new ApiResponse<>(musicGuessService.startSession(sessionId)));
    }

    @PostMapping("/sessions/{sessionId}/answers")
    public ResponseEntity<ApiResponse<MusicGuessAnswerResult>> answer(
            @PathVariable String sessionId,
            @RequestBody MusicGuessAnswerRequest request
    ) {
        return ResponseEntity.ok(new ApiResponse<>(musicGuessService.answer(sessionId, request)));
    }

    @PostMapping("/sessions/{sessionId}/next-round")
    public ResponseEntity<ApiResponse<MusicGuessSessionResponse>> nextRound(
            @PathVariable String sessionId
    ) {
        return ResponseEntity.ok(new ApiResponse<>(musicGuessService.nextRound(sessionId)));
    }
}
