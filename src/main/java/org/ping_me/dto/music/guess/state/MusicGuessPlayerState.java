package org.ping_me.dto.music.guess.state;

public record MusicGuessPlayerState(
        String userId,
        String displayName,
        int score,
        int answeredRounds,
        boolean connected
) {
}
