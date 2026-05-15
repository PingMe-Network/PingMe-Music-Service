package org.ping_me.dto.music.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * Redis-backed state snapshot for a shared music listening session.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicSessionState(
        String hostUserId,
        boolean isPlaying,
        String currentTrackId,
        long positionMs,
        long startedAtEpochMs,
        List<String> queue,
        List<String> activeListenerIds,
        boolean isEndingAfterCurrentTrack,
        long version,
        Instant updatedAt
) {

    public MusicSessionState {
        queue = queue == null ? List.of() : List.copyOf(queue);
        activeListenerIds = activeListenerIds == null ? List.of() : List.copyOf(activeListenerIds);
    }
}

