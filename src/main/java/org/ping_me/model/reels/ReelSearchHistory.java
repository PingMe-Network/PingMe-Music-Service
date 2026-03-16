package org.ping_me.model.reels;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.ping_me.model.common.BaseEntity;
import org.ping_me.model.user.User;

@Entity
@Table(name = "reel_search_histories")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class ReelSearchHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    String query;

    Integer resultCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;
}

