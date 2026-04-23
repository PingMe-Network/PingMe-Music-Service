package org.ping_me.dto.response.music.misc;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ping_me.model.constant.ArtistRole;
import org.ping_me.model.music.PlaylistSong;

import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistSongDto {
    private Long id;
    private Long songId;
    private Integer position;
    private String title; // optional for convenience
    private String songUrl;
    private Integer duration;
    private String coverImageUrl;
    private ArtistSummaryDto mainArtist;

    public static PlaylistSongDto from(PlaylistSong ps) {
        var song = ps.getSong();
        return new PlaylistSongDto(
                ps.getId(),
                song.getId(),
                ps.getPosition(),
                song.getTitle(),
                song.getSongUrl(),
                song.getDuration(),
                song.getImgUrl(),
                extractMainArtist(song)
        );
    }

    private static ArtistSummaryDto extractMainArtist(org.ping_me.model.music.Song song) {
        if (song.getArtistRoles() == null) {
            return null;
        }

        return song.getArtistRoles().stream()
                .filter(role -> role.getRole() == ArtistRole.MAIN_ARTIST)
                .map(role -> new ArtistSummaryDto(
                        role.getArtist().getId(),
                        role.getArtist().getName(),
                        role.getRole(),
                        role.getArtist().getImgUrl()
                ))
                .filter(dto -> Objects.nonNull(dto.getId()))
                .findFirst()
                .orElse(null);
    }
}
