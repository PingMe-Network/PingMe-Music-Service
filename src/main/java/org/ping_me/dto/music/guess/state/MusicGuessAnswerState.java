package org.ping_me.dto.music.guess.state;

public record MusicGuessAnswerState(
        String optionId,
        boolean correct,
        int earnedPoints,
        long answeredAtEpochMs
) {
}
