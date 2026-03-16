package org.ping_me.model.music;

import jakarta.persistence.*;
import lombok.*;
import org.ping_me.model.common.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "album_play_history")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AlbumPlayHistory extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    @Column(nullable = false)
    private LocalDateTime playedAt;
}

