package org.ping_me.dto.music.session.payload;

public record PlayPayload(
        String currentTrackId,
        Long positionMs
) {
}

