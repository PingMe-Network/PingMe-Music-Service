package org.ping_me.dto.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * @author : user664dntp
 * @mailto : phatdang19052004@gmail.com
 * @created : 9/04/2026, Thursday
 **/
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class MusicListeningEvent {
    Long songId;
    String songTitle;
    String artistName;
    long timestamp;
}
