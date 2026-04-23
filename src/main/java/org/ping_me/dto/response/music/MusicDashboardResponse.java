package org.ping_me.dto.response.music;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MusicDashboardResponse {
    private List<SongResponseWithAllAlbum> topSongs;
    private List<AlbumResponse> popularAlbums;
    private List<ArtistResponse> popularArtists;
    private List<GenreResponse> genres;
    private RankingData rankings;
}
