package org.ping_me.dto.music.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Envelope for commands sent from the client to the music session command endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicSessionCommandRequest(
        MusicSessionCommandType command,
        Object payload
) {
}

