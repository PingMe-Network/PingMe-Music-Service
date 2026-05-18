package org.ping_me.dto.music.guess;

public record CreateMusicGuessSessionRequest(
        MusicGuessMode mode,
        Integer totalRounds,
        Integer optionCount,
        Integer clipSeconds,
        Integer roundDurationSeconds
) {
}
