package org.ping_me.repository.music;

import org.ping_me.model.music.FavoriteSong;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteSongRepository extends JpaRepository<FavoriteSong, Long> {
    @EntityGraph(attributePaths = {"song", "song.artistRoles", "song.artistRoles.artist"})
    List<FavoriteSong> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<FavoriteSong> findByUserIdAndSongId(Long userId, Long songId);

    void deleteByUserIdAndSongId(Long userId, Long songId);
}
