package org.ping_me.dto.music.session.payload;

import java.util.List;

public record QueuePayload(
        List<String> queue,
        String trackId,
        List<String> trackIds
) {
}

