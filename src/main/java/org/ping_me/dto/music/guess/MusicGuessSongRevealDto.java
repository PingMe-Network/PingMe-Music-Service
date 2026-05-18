package org.ping_me.dto.music.guess;

public record MusicGuessSongRevealDto(
        Long songId,
        String title,
        String artistName,
        String coverImageUrl,
        String songUrl
) {
}
