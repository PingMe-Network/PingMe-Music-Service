package org.ping_me.dto.music.session;

/**
 * Error envelope sent to a specific user when their command is invalid or rejected.
 */
public record MusicCommandError(
        String code,
        String message,
        Object details
) {
}

