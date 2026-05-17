package org.ping_me.dto.music.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * Redis-backed state snapshot for a shared music listening session.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("unused")
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

    public static MusicSessionState initial(String hostUserId) {
        return new MusicSessionState(
                hostUserId,
                false,
                null,
                0L,
                0L,
                List.of(),
                List.of(),
                false,
                0L,
                Instant.now()
        );
    }

    public MusicSessionState withVersion(long version) {
        return new MusicSessionState(
                hostUserId,
                isPlaying,
                currentTrackId,
                positionMs,
                startedAtEpochMs,
                queue,
                activeListenerIds,
                isEndingAfterCurrentTrack,
                version,
                Instant.now()
        );
    }

    public MusicSessionState withPlayback(boolean isPlaying, String currentTrackId, long positionMs, long startedAtEpochMs) {
        return new MusicSessionState(
                hostUserId,
                isPlaying,
                currentTrackId,
                positionMs,
                startedAtEpochMs,
                queue,
                activeListenerIds,
                isEndingAfterCurrentTrack,
                version,
                Instant.now()
        );
    }

    public MusicSessionState withQueue(List<String> queue) {
        return new MusicSessionState(
                hostUserId,
                isPlaying,
                currentTrackId,
                positionMs,
                startedAtEpochMs,
                queue,
                activeListenerIds,
                isEndingAfterCurrentTrack,
                version,
                Instant.now()
        );
    }

    public MusicSessionState withActiveListenerIds(List<String> activeListenerIds) {
        return new MusicSessionState(
                hostUserId,
                isPlaying,
                currentTrackId,
                positionMs,
                startedAtEpochMs,
                queue,
                activeListenerIds,
                isEndingAfterCurrentTrack,
                version,
                Instant.now()
        );
    }

    public MusicSessionState withEndingAfterCurrentTrack(boolean endingAfterCurrentTrack) {
        return new MusicSessionState(
                hostUserId,
                isPlaying,
                currentTrackId,
                positionMs,
                startedAtEpochMs,
                queue,
                activeListenerIds,
                endingAfterCurrentTrack,
                version,
                Instant.now()
        );
    }
}

