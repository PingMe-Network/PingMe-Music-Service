package org.ping_me.dto.music.guess.state;

import org.ping_me.dto.music.guess.MusicGuessMode;
import org.ping_me.dto.music.guess.MusicGuessSessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MusicGuessSessionState(
        String sessionId,
        String roomCode,
        MusicGuessMode mode,
        MusicGuessSessionStatus status,
        String hostUserId,
        String hostDisplayName,
        int totalRounds,
        int optionCount,
        int clipSeconds,
        int roundDurationSeconds,
        int currentRoundIndex,
        long roundEndsAtEpochMs,
        List<MusicGuessRoundState> rounds,
        Map<String, MusicGuessPlayerState> players,
        long version,
        Instant createdAt
) {
    public MusicGuessRoundState currentRound() {
        if (currentRoundIndex < 0 || currentRoundIndex >= rounds.size()) {
            return null;
        }
        return rounds.get(currentRoundIndex);
    }
}
