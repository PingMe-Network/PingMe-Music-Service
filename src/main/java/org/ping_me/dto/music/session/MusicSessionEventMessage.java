package org.ping_me.dto.music.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Envelope for backend events broadcast to the session topic.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicSessionEventMessage(
        String eventType,
        Object data,
        long serverTimeMs
) {
}
