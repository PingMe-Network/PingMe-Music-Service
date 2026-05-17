package org.ping_me.dto.music.session;

/**
 * Supported music session commands from the frontend.
 */
public enum MusicSessionCommandType {
    START_SESSION,
    JOIN_SESSION,
    LEAVE_SESSION,
    PLAY,
    PAUSE,
    SEEK,
    NEXT,
    PREV,
    ADD_TO_QUEUE,
    REMOVE_FROM_QUEUE,
    STOP_SESSION
}

