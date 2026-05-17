package org.ping_me.controller.music;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ping_me.dto.music.session.FriendSessionSummary;
import org.ping_me.dto.music.session.MusicSessionState;
import org.ping_me.dto.response.music.SongResponse;
import org.ping_me.repository.music.session.MusicSessionRedisRepository;
import org.ping_me.service.music.SongService;
import org.ping_me.service.music.session.MusicSessionAccessService;
import org.ping_me.service.music.session.FriendSessionSummaryService;
import org.ping_me.service.music.session.MusicSessionTokenManager;
import org.ping_me.service.user.CurrentUserIdProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/music-service/music/sessions")
@RequiredArgsConstructor
@Slf4j
public class MusicSessionController {

    private final MusicSessionRedisRepository sessionRepository;
    private final MusicSessionAccessService accessService;
    private final MusicSessionTokenManager tokenManager;
    private final FriendSessionSummaryService friendSessionSummaryService;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final SongService songService;
    private final RestClient coreServiceRestClient;

    @GetMapping("/{hostUserId}")
    public ResponseEntity<MusicSessionState> getSessionState(
            @PathVariable String hostUserId
    ) {
        String currentUserId = currentUserIdProvider.getCurrentUserId();
        log.info("Fetching music session state userId={} sessionId={}", currentUserId, hostUserId);

        // 1. Kiểm tra session có tồn tại không
        Optional<MusicSessionState> sessionOpt = sessionRepository.findByHostUserId(hostUserId);
        if (sessionOpt.isEmpty()) {
            log.debug("Music session not found sessionId={}", hostUserId);
            return ResponseEntity.notFound().build();
        }

        MusicSessionState state = sessionOpt.get();
        log.debug("Music session found sessionId={} isPlaying={} trackId={}",
                hostUserId, state.isPlaying(), state.currentTrackId());

        // 2. Chỉ trả về 404 nếu session đã đánh dấu kết thúc
        if (state.isEndingAfterCurrentTrack()) {
            log.debug("Music session is ending sessionId={}", hostUserId);
            return ResponseEntity.notFound().build();
        }

        // 3. Kiểm tra quyền (có phải là bạn bè hoặc chính mình không)
        boolean canJoin = accessService.canJoinSession(hostUserId, currentUserId);
        log.debug("Music session access checked userId={} sessionId={} canJoin={}",
                currentUserId, hostUserId, canJoin);

        if (!canJoin) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(state);
    }

    @GetMapping("/friends")
    public ResponseEntity<List<MusicSessionState>> getFriendsSessions() {
        String currentUserId = currentUserIdProvider.getCurrentUserId();
        if (currentUserId == null) return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();

        List<Long> friendIds = accessService.getFriendIds(currentUserId);
        List<String> friendIdStrings = friendIds.stream().map(String::valueOf).toList();
        
        List<MusicSessionState> activeSessions = sessionRepository.findByHostUserIds(friendIdStrings)
                .stream()
                .filter(s -> s.currentTrackId() != null && !s.isEndingAfterCurrentTrack())
                .toList();

        return ResponseEntity.ok(activeSessions);
    }

    @GetMapping("/friends/summary")
    public ResponseEntity<List<FriendSessionSummary>> getFriendsSessionSummaries() {
        String currentUserId = currentUserIdProvider.getCurrentUserId();
        if (currentUserId == null) return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();

        List<Long> friendIds = accessService.getFriendIds(currentUserId);
        List<String> friendIdStrings = friendIds.stream().map(String::valueOf).toList();

        List<FriendSessionSummary> activeSessions = sessionRepository.findByHostUserIds(friendIdStrings)
                .stream()
                .filter(s -> s.currentTrackId() != null && !s.isEndingAfterCurrentTrack())
                .map(friendSessionSummaryService::fromState)
                .toList();

        return ResponseEntity.ok(activeSessions);
    }

    /**
     * Generate a share token for the current session
     * Only the host can generate share tokens for their session
     * Token is valid for 10 minutes and can be shared with non-friends
     */
    @PostMapping("/{sessionId}/share-token")
    public ResponseEntity<SessionTokenResponse> generateShareToken(
            @PathVariable String sessionId) {
        try {
            String currentUserId = currentUserIdProvider.getCurrentUserId();
            if (currentUserId == null) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
            }

            // Verify user is the host of this session
            Optional<MusicSessionState> sessionOpt = sessionRepository.findByHostUserId(sessionId);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            MusicSessionState session = sessionOpt.get();
            if (!session.hostUserId().equals(currentUserId)) {
                return ResponseEntity.status(403).build();  // Forbidden: not the host
            }

            // Generate share token
            String shareToken = tokenManager.generateSessionToken(currentUserId, sessionId);
            String appBaseUrl = "https://pingme.app";  // Configure in application.yml
            String shareLink = String.format("%s/app/music?join-session=%s&token=%s",
                    appBaseUrl, currentUserId, shareToken);

            return ResponseEntity.ok(new SessionTokenResponse(
                    shareToken,
                    shareLink,
                    Instant.now().plusSeconds(600)  // 10 minute expiry
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * List all active tokens for a session
     * Only the host can view tokens for their session
     */
    @GetMapping("/{sessionId}/share-tokens")
    public ResponseEntity<TokenListResponse> listShareTokens(
            @PathVariable String sessionId) {
        try {
            String currentUserId = currentUserIdProvider.getCurrentUserId();
            if (currentUserId == null) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
            }

            // Verify user is the host
            Optional<MusicSessionState> sessionOpt = sessionRepository.findByHostUserId(sessionId);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            MusicSessionState session = sessionOpt.get();
            if (!session.hostUserId().equals(currentUserId)) {
                return ResponseEntity.status(403).build();
            }

            // Get active token JTIs
            java.util.Set<String> jtis = tokenManager.getActiveTokensForSession(sessionId);
            
            log.info("[TokenList] Retrieved {} active tokens for session={}", jtis.size(), sessionId);
            return ResponseEntity.ok(new TokenListResponse(sessionId, jtis.size(), new java.util.ArrayList<>(jtis)));

        } catch (Exception e) {
            log.error("[TokenList] Error listing tokens: ", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Revoke all tokens for a session immediately
     * Useful for immediate lockdown or security incident
     * Only the host can revoke all tokens
     */
    @DeleteMapping("/{sessionId}/share-tokens/revoke-all")
    public ResponseEntity<Void> revokeAllTokens(@PathVariable String sessionId) {
        try {
            String currentUserId = currentUserIdProvider.getCurrentUserId();
            if (currentUserId == null) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
            }

            // Verify user is the host
            Optional<MusicSessionState> sessionOpt = sessionRepository.findByHostUserId(sessionId);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            MusicSessionState session = sessionOpt.get();
            if (!session.hostUserId().equals(currentUserId)) {
                return ResponseEntity.status(403).build();
            }

            // Revoke all tokens
            tokenManager.revokeAllTokensForSession(sessionId);

            log.info("[TokenRevoke] Revoked all tokens for session={}", sessionId);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("[TokenRevoke] Error revoking all tokens: ", e);
            return ResponseEntity.status(500).build();
        }
    }
    @GetMapping("/{hostUserId}/preview")
    public ResponseEntity<SessionPreview> getSessionPreview(
            @PathVariable String hostUserId) {
        try {
            Optional<MusicSessionState> sessionOpt = sessionRepository.findByHostUserId(hostUserId);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            MusicSessionState session = sessionOpt.get();
            if (session.isEndingAfterCurrentTrack()) {
                return ResponseEntity.notFound().build();
            }

            // Fetch real user data from Core Service
            String hostName = "Unknown Host";
            String hostAvatar = "https://api.pingme.app/users/" + hostUserId + "/avatar";
            try {
                UserPreviewDto userDto = coreServiceRestClient.get()
                        .uri("/core-service/users/{userId}/summary", hostUserId)
                        .retrieve()
                        .body(UserPreviewDto.class);
                if (userDto != null) {
                    hostName = userDto.getName();
                    hostAvatar = userDto.getAvatarUrl();
                }
            } catch (Exception e) {
                log.warn("[Preview] Failed to fetch host user data: {}", e.getMessage());
            }

            // Fetch real song data
            String songTitle = "Unknown Track";
            String artistName = "Unknown Artist";
            try {
                if (session.currentTrackId() != null) {
                    Long songId = Long.parseLong(session.currentTrackId());
                    SongResponse song = songService.getSongById(songId);
                    if (song != null) {
                        songTitle = song.getTitle();
                        artistName = song.getMainArtist() != null ? 
                                song.getMainArtist().getName() : "Unknown Artist";
                    }
                }
            } catch (Exception e) {
                log.warn("[Preview] Failed to fetch song data: {}", e.getMessage());
            }

            // Build preview response with real data
            SessionPreview preview = new SessionPreview(
                    hostUserId,
                    hostName,
                    hostAvatar,
                    session.currentTrackId(),
                    songTitle,
                    artistName,
                    session.activeListenerIds().size(),
                    session.queue().size(),
                    session.isPlaying()
            );

            return ResponseEntity.ok(preview);

        } catch (Exception e) {
            log.error("[Preview] Unexpected error: ", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Revoke a share token by its JTI (JWT ID)
     * Only the host can revoke tokens for their session
     * Existing listeners remain connected; new listeners cannot join with revoked token
     */
    @DeleteMapping("/{sessionId}/share-token/{jti}")
    public ResponseEntity<Void> revokeShareToken(
            @PathVariable String sessionId,
            @PathVariable String jti) {
        try {
            String currentUserId = currentUserIdProvider.getCurrentUserId();
            if (currentUserId == null) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
            }

            // Verify user is the host
            Optional<MusicSessionState> sessionOpt = sessionRepository.findByHostUserId(sessionId);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            MusicSessionState session = sessionOpt.get();
            if (!session.hostUserId().equals(currentUserId)) {
                return ResponseEntity.status(403).build();
            }

            // Mark token as revoked in Redis
            tokenManager.revokeTokenByJti(jti);

            log.info("[TokenRevoke] Revoked token JTI={} for session={}", jti, sessionId);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("[TokenRevoke] Error revoking token: ", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Response DTO for share token generation
     */
    @Data
    public static class SessionTokenResponse {
        private String token;
        private String shareLink;
        private Instant expiresAt;

        public SessionTokenResponse(String token, String shareLink, Instant expiresAt) {
            this.token = token;
            this.shareLink = shareLink;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * Response DTO for session preview before joining
     */
    @Data
    public static class SessionPreview {
        private String hostUserId;
        private String hostName;
        private String hostAvatar;
        private String currentSongId;
        private String currentSongTitle;
        private String currentSongArtist;
        private int activeListenerCount;
        private int queueLength;
        private boolean isPlaying;

        public SessionPreview(String hostUserId, String hostName, String hostAvatar,
                            String currentSongId, String currentSongTitle, String currentSongArtist,
                            int activeListenerCount, int queueLength, boolean isPlaying) {
            this.hostUserId = hostUserId;
            this.hostName = hostName;
            this.hostAvatar = hostAvatar;
            this.currentSongId = currentSongId;
            this.currentSongTitle = currentSongTitle;
            this.currentSongArtist = currentSongArtist;
            this.activeListenerCount = activeListenerCount;
            this.queueLength = queueLength;
            this.isPlaying = isPlaying;
        }
    }

    /**
     * Simple DTO for user info from Core Service
     */
    @Data
    public static class UserPreviewDto {
        private String id;
        private String name;
        private String avatarUrl;
    }

    /**
     * Response DTO for listing active tokens
     */
    @Data
    public static class TokenListResponse {
        private String sessionId;
        private int totalTokens;
        private java.util.List<String> jtis;

        public TokenListResponse(String sessionId, int totalTokens, java.util.List<String> jtis) {
            this.sessionId = sessionId;
            this.totalTokens = totalTokens;
            this.jtis = jtis;
        }
    }
}
