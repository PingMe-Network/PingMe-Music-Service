package org.ping_me.repository.jpa;

import org.ping_me.model.music.AlbumPlayHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlbumPlayHistoryRepository extends JpaRepository<AlbumPlayHistory, Long> {
}

