package org.ping_me.dto.music.guess;

public record MusicGuessEventMessage(
        String eventType,
        Object data,
        long createdAtEpochMs
) {
}
