package org.ping_me.dto.music.session.payload;

import java.util.List;

public record StartSessionPayload(
        List<String> queue,
        String currentTrackId,
        Long positionMs,
        Boolean isPlaying
) {
}
