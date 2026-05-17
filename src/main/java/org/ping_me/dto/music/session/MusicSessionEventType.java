package org.ping_me.dto.music.session;

/**
 * Supported music session events pushed from the backend.
 */
public enum MusicSessionEventType {
    MUSIC_SESSION_STATE,
    MUSIC_PLAYBACK_CHANGED,
    MUSIC_QUEUE_CHANGED,
    MUSIC_PRESENCE_CHANGED,
    MUSIC_SESSION_ENDED,
    FRIEND_SESSION_STARTED,
    FRIEND_SESSION_UPDATED,
    FRIEND_SESSION_ENDED
}

