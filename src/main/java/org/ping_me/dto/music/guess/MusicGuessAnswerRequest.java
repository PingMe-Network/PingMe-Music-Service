package org.ping_me.dto.music.guess;

public record MusicGuessAnswerRequest(
        String roundId,
        String optionId,
        Long answeredAtEpochMs
) {
}
