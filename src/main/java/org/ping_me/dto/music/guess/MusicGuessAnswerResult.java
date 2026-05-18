package org.ping_me.dto.music.guess;

public record MusicGuessAnswerResult(
        boolean correct,
        int earnedPoints,
        int totalScore,
        String selectedOptionId,
        String correctOptionId,
        boolean roundComplete,
        boolean sessionFinished,
        MusicGuessSongRevealDto reveal
) {
}
