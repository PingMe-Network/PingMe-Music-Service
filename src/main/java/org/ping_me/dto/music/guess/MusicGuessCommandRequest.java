package org.ping_me.dto.music.guess;

public record MusicGuessCommandRequest(
        MusicGuessCommandType command,
        Object payload
) {
}
