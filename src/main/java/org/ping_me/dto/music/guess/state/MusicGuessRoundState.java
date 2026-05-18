package org.ping_me.dto.music.guess.state;

import java.util.List;
import java.util.Map;

public record MusicGuessRoundState(
        String roundId,
        Long songId,
        String title,
        String artistName,
        String audioUrl,
        String coverImageUrl,
        int previewStartMs,
        String correctOptionId,
        List<MusicGuessOptionState> options,
        Map<String, MusicGuessAnswerState> answers
) {
}
