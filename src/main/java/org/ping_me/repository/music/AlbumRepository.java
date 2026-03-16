package org.ping_me.repository.music;

import org.ping_me.model.music.Album;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * @author Le Tran Gia Huy
 * @created 20/11/2025 - 6:21 PM
 * @project DHKTPM18ATT_Nhom10_PingMe_Backend
 * @package me.huynhducphu.PingMe_Backend.repository
 */

@Repository
public interface AlbumRepository extends JpaRepository<Album, Long> {

    @Query(value = "SELECT * FROM albums WHERE id = :id AND is_deleted = true", nativeQuery = true)
    Optional<Album> findSoftDeletedAlbum(@Param("id") Long id);

    @Query(value = "SELECT * FROM albums WHERE id = :id", nativeQuery = true)
    Optional<Album> findByIdIgnoringDeleted(@Param("id") Long id);

    Page<Album> findAlbumsByTitleContainingIgnoreCase(String title, Pageable pageable);

    Page<Album> findAllByOrderByPlayCountDesc(Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE Album a SET a.playCount = a.playCount + 1 WHERE a.id = :id")
    void incrementPlayCount(@Param("id") Long id);

}
