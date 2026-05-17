package org.ping_me.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.ping_me.dto.music.session.MusicSessionCommandRequest;
import org.ping_me.dto.music.session.MusicSessionCommandType;
import org.ping_me.dto.music.session.MusicSessionEventMessage;
import org.ping_me.dto.music.session.MusicSessionState;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicWebSocketContractsTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldBuildFullWsDestinationsFromHostUserId() {
        assertEquals("/music-service/ws-music", MusicWebSocketDestinations.WS_ENDPOINT);
        assertEquals("/app/music/users/42/command", MusicWebSocketDestinations.commandDestination("42"));
        assertEquals("/topic/music/users/42/session", MusicWebSocketDestinations.sessionTopic("42"));
    }

    @Test
    void shouldSerializeAndDeserializeSessionStateAndCommandEnvelope() throws Exception {
        var state = new MusicSessionState(
                "host-1",
                true,
                "track-9",
                1_500L,
                1_000_000L,
                List.of("track-9", "track-10"),
                List.of("listener-a", "listener-b"),
                false,
                7L,
                Instant.parse("2026-05-15T00:00:00Z")
        );

        String stateJson = objectMapper.writeValueAsString(state);
        var stateRoundTrip = objectMapper.readValue(stateJson, MusicSessionState.class);

        assertEquals("host-1", stateRoundTrip.hostUserId());
        assertEquals(List.of("track-9", "track-10"), stateRoundTrip.queue());
        assertEquals(List.of("listener-a", "listener-b"), stateRoundTrip.activeListenerIds());
        assertEquals(7L, stateRoundTrip.version());

        var command = new MusicSessionCommandRequest(MusicSessionCommandType.PLAY, java.util.Map.of("positionMs", 1000));
        String commandJson = objectMapper.writeValueAsString(command);
        var commandRoundTrip = objectMapper.readValue(commandJson, MusicSessionCommandRequest.class);

        assertEquals(MusicSessionCommandType.PLAY, commandRoundTrip.command());
        assertInstanceOf(java.util.Map.class, commandRoundTrip.payload());

        var event = new MusicSessionEventMessage("MUSIC_SESSION_STATE", stateRoundTrip, 1000000L);
        String eventJson = objectMapper.writeValueAsString(event);
        assertTrue(eventJson.contains("MUSIC_SESSION_STATE"));
        assertTrue(eventJson.contains("host-1"));
    }
}


