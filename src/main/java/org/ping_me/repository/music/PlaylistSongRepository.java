package org.ping_me.repository.music;

import org.ping_me.model.music.PlaylistSong;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, Long> {

    Optional<PlaylistSong> findByPlaylistIdAndSongId(Long playlistId, Long songId);

    void deleteByPlaylistIdAndSongId(Long playlistId, Long songId);

    @EntityGraph(attributePaths = {"song", "song.artistRoles", "song.artistRoles.artist"})
    List<PlaylistSong> findByPlaylistIdOrderByPositionAsc(Long playlistId);
}
