package org.ping_me.dto.music.guess;

import java.util.List;

public record MusicGuessRoundDto(
        String roundId,
        int roundNumber,
        int totalRounds,
        String audioUrl,
        int previewStartMs,
        int clipSeconds,
        long endsAtEpochMs,
        List<MusicGuessOptionDto> options,
        String answeredOptionId,
        Boolean answeredCorrect,
        MusicGuessSongRevealDto reveal
) {
}
