package org.ping_me.dto.music.session;

import java.time.Instant;
import java.util.List;

public record FriendSessionSummary(
        String hostUserId,
        TrackSummary track,
        int listenerCount,
        List<String> listenerIds,
        boolean isPlaying,
        boolean isEndingAfterCurrentTrack,
        long version,
        Instant updatedAt
) {
    public FriendSessionSummary {
        listenerIds = listenerIds == null ? List.of() : List.copyOf(listenerIds);
    }
}
