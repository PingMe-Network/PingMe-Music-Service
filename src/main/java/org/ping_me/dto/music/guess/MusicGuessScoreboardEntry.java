package org.ping_me.dto.music.guess;

public record MusicGuessScoreboardEntry(
        String userId,
        String displayName,
        int score,
        int answeredRounds,
        boolean connected
) {
}
