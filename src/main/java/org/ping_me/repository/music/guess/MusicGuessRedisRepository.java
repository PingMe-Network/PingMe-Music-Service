package org.ping_me.repository.music.guess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ping_me.dto.music.guess.state.MusicGuessSessionState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class MusicGuessRedisRepository {

    private final RedisTemplate<String, String> musicSessionTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.music.guess.session-prefix:music_guess_session:}")
    private String sessionPrefix;

    @Value("${app.music.guess.room-code-prefix:music_guess_room:}")
    private String roomCodePrefix;

    @Value("${app.music.guess.ttl-seconds:7200}")
    private long ttlSeconds;

    public MusicGuessRedisRepository(
            @Qualifier("musicSessionTemplate") RedisTemplate<String, String> musicSessionTemplate,
            ObjectMapper objectMapper
    ) {
        this.musicSessionTemplate = musicSessionTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<MusicGuessSessionState> findBySessionId(String sessionId) {
        String json = musicSessionTemplate.opsForValue().get(sessionKey(sessionId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, MusicGuessSessionState.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể đọc phiên đoán nhạc từ Redis", e);
        }
    }

    public Optional<MusicGuessSessionState> findByRoomCode(String roomCode) {
        String sessionId = musicSessionTemplate.opsForValue().get(roomCodeKey(normalizeRoomCode(roomCode)));
        if (sessionId == null) {
            return Optional.empty();
        }
        return findBySessionId(sessionId);
    }

    public MusicGuessSessionState save(MusicGuessSessionState state) {
        try {
            Duration ttl = Duration.ofSeconds(ttlSeconds);
            musicSessionTemplate.opsForValue().set(sessionKey(state.sessionId()), objectMapper.writeValueAsString(state), ttl);
            musicSessionTemplate.opsForValue().set(roomCodeKey(state.roomCode()), state.sessionId(), ttl);
            return state;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể ghi phiên đoán nhạc vào Redis", e);
        }
    }

    private String sessionKey(String sessionId) {
        return sessionPrefix + sessionId;
    }

    private String roomCodeKey(String roomCode) {
        return roomCodePrefix + normalizeRoomCode(roomCode);
    }

    private String normalizeRoomCode(String roomCode) {
        return roomCode == null ? "" : roomCode.trim().toUpperCase();
    }
}
