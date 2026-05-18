package org.ping_me.dto.music.guess;

import java.util.List;

public record MusicGuessSessionResponse(
        String sessionId,
        String roomCode,
        MusicGuessMode mode,
        MusicGuessSessionStatus status,
        String hostUserId,
        String hostDisplayName,
        int currentRoundNumber,
        int totalRounds,
        int optionCount,
        int clipSeconds,
        int roundDurationSeconds,
        List<MusicGuessScoreboardEntry> scoreboard,
        MusicGuessRoundDto round
) {
}
