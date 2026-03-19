package org.ping_me.repository.music;

import org.ping_me.model.music.Song;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * @author Le Tran Gia Huy
 * @created 20/11/2025 - 6:22 PM
 * @project DHKTPM18ATT_Nhom10_PingMe_Backend
 * @package me.huynhducphu.PingMe_Backend.repository
 */

@Repository
public interface SongRepository extends JpaRepository<Song, Long> {

    @Query(
            value = "SELECT s.id FROM Song s",
            countQuery = "SELECT COUNT(s.id) FROM Song s"
    )
    Page<Long> findSongIds(Pageable pageable);

    // Load Song cùng lúc với ArtistRoles, Artist, Genres và Albums để tránh lỗi LazyLoading hoặc N+1 query
    @Query("SELECT s FROM Song s " +
            "LEFT JOIN FETCH s.artistRoles ar " +
            "LEFT JOIN FETCH ar.artist " +
            "LEFT JOIN FETCH s.genres " +
            "LEFT JOIN FETCH s.albums " +
            "WHERE s.id = :id")
    Optional<Song> findByIdWithDetails(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE Song s SET s.playCount = s.playCount + 1 WHERE s.id = :id")
    void incrementPlayCount(@Param("id") Long id, Long userId);


    @Query(value = "SELECT * FROM songs WHERE id = :id AND is_deleted = true", nativeQuery = true)
    Optional<Song> findSoftDeletedSong(@Param("id") Long id);

    @Query(value = "SELECT * FROM songs WHERE id = :id", nativeQuery = true)
    Optional<Song> findByIdIgnoringDeleted(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT s FROM Song s
            LEFT JOIN FETCH s.artistRoles ar
            LEFT JOIN FETCH ar.artist
            LEFT JOIN FETCH s.genres
            JOIN s.albums a
            WHERE LOWER(s.title) LIKE LOWER(CONCAT('%', :title, '%'))
            ORDER BY s.createdAt DESC
            """)
    List<Song> findSongsWithAlbumsByTitle(@Param("title") String title);

    @Query(
            value = "SELECT DISTINCT s.id FROM Song s JOIN s.genres g WHERE g.id = :genreId",
            countQuery = "SELECT COUNT(DISTINCT s.id) FROM Song s JOIN s.genres g WHERE g.id = :genreId"
    )
    Page<Long> findSongIdsByGenreId(@Param("genreId") Long genreId, Pageable pageable);

    @Query(
            value = "SELECT DISTINCT s.id FROM Song s JOIN s.albums a WHERE a.id = :albumId",
            countQuery = "SELECT COUNT(DISTINCT s.id) FROM Song s JOIN s.albums a WHERE a.id = :albumId"
    )
    Page<Long> findSongIdsByAlbumId(@Param("albumId") Long albumId, Pageable pageable);

    @Query(
            value = "SELECT DISTINCT s.id FROM Song s JOIN s.artistRoles ar JOIN ar.artist at WHERE at.id = :artistId",
            countQuery = "SELECT COUNT(DISTINCT s.id) FROM Song s JOIN s.artistRoles ar JOIN ar.artist at WHERE at.id = :artistId"
    )
    Page<Long> findSongIdsByArtistId(@Param("artistId") Long artistId, Pageable pageable);

    @EntityGraph(attributePaths = {"artistRoles.artist", "genres", "albums"})
    @Query("SELECT DISTINCT s FROM Song s WHERE s.id IN :ids")
    List<Song> findSongsWithDetailsByIds(@Param("ids") List<Long> ids);

}
