package org.ping_me.dto.music.guess.state;

public record MusicGuessOptionState(
        String id,
        Long songId,
        String label
) {
}
