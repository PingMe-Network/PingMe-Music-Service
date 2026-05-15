package org.ping_me.websocket;

/**
 * Shared WebSocket destinations for music listening sessions.
 */
public final class MusicWebSocketDestinations {

    public static final String WS_ENDPOINT = "/music-service/ws-music";
    public static final String APP_DESTINATION_PREFIX = "/app";
    public static final String SESSION_TOPIC_PREFIX = "/topic/music/users/";
    public static final String SESSION_TOPIC_SUFFIX = "/session";
    public static final String COMMAND_MAPPING_PATTERN = "/music/users/{hostUserId}/command";
    public static final String COMMAND_DESTINATION_PATTERN = "/app/music/users/{hostUserId}/command";
    public static final String SESSION_TOPIC_PATTERN = "/topic/music/users/{hostUserId}/session";

    private MusicWebSocketDestinations() {
    }

    public static String sessionTopic(String hostUserId) {
        return SESSION_TOPIC_PREFIX + hostUserId + SESSION_TOPIC_SUFFIX;
    }

    public static String commandDestination(String hostUserId) {
        return APP_DESTINATION_PREFIX + "/music/users/" + hostUserId + "/command";
    }
}

