package org.ping_me.repository.music.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ping_me.dto.music.session.MusicSessionState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

@Repository
public class MusicSessionRedisRepository {

    private final RedisTemplate<String, String> musicSessionTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.music.session.redis-key-prefix:music_session:}")
    private String sessionKeyPrefix;

    @Value("${app.music.session.links-prefix:music_session_links:}")
    private String sessionLinksPrefix;

    private static final String UPDATE_SCRIPT = 
            "local current = redis.call('get', KEYS[1]) " +
            "if not current then " +
            "  if ARGV[1] == '0' then " +
            "    redis.call('set', KEYS[1], ARGV[2]) " +
            "    return 1 " +
            "  else return 0 end " +
            "end " +
            "local decoded = cjson.decode(current) " +
            "if tostring(decoded.version) == ARGV[1] then " +
            "  redis.call('set', KEYS[1], ARGV[2]) " +
            "  return 1 " +
            "else return 0 end";

    private static final String DELETE_IF_TERMINAL_SCRIPT =
            "local current = redis.call('get', KEYS[1]) " +
            "if not current then return 0 end " +
            "local decoded = cjson.decode(current) " +
            "if tostring(decoded.version) == ARGV[1] " +
            "  and decoded.isEndingAfterCurrentTrack == true " +
            "  and (#decoded.activeListenerIds == 0) then " +
            "  redis.call('del', KEYS[1]) " +
            "  return 1 " +
            "end " +
            "return 0";

    public MusicSessionRedisRepository(
            @org.springframework.beans.factory.annotation.Qualifier("musicSessionTemplate") RedisTemplate<String, String> musicSessionTemplate,
            ObjectMapper objectMapper
    ) {
        this.musicSessionTemplate = musicSessionTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<MusicSessionState> findByHostUserId(String hostUserId) {
        String json = musicSessionTemplate.opsForValue().get(sessionKey(hostUserId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, MusicSessionState.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể đọc music session state từ Redis", e);
        }
    }

    public List<MusicSessionState> findByHostUserIds(List<String> hostUserIds) {
        if (hostUserIds == null || hostUserIds.isEmpty()) return List.of();
        List<String> keys = hostUserIds.stream().map(this::sessionKey).toList();
        List<String> results = musicSessionTemplate.opsForValue().multiGet(keys);
        if (results == null) return List.of();

        return results.stream()
                .filter(Objects::nonNull)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, MusicSessionState.class);
                    } catch (JsonProcessingException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public MusicSessionState getOrCreate(String hostUserId) {
        return findByHostUserId(hostUserId).orElseGet(() -> save(MusicSessionState.initial(hostUserId), 0L));
    }

    public MusicSessionState save(MusicSessionState state, long expectedVersion) {
        long nextVersion = expectedVersion + 1;
        MusicSessionState snapshot = new MusicSessionState(
                state.hostUserId(),
                state.isPlaying(),
                state.currentTrackId(),
                state.positionMs(),
                state.startedAtEpochMs(),
                state.queue(),
                state.activeListenerIds(),
                state.isEndingAfterCurrentTrack(),
                nextVersion,
                Instant.now()
        );
        
        boolean success = writeStateAtomic(snapshot, expectedVersion);
        if (!success) {
            throw new ConcurrentModificationException("Phiên nghe đã được cập nhật bởi một yêu cầu khác. Vui lòng thử lại.");
        }
        return snapshot;
    }

    private boolean writeStateAtomic(MusicSessionState state, long expectedVersion) {
        try {
            String json = objectMapper.writeValueAsString(state);
            Long result = musicSessionTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(UPDATE_SCRIPT, Long.class),
                Collections.singletonList(sessionKey(state.hostUserId())),
                String.valueOf(expectedVersion),
                json
            );
            return result != null && result == 1L;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể ghi music session state vào Redis", e);
        }
    }

    public MusicSessionState update(String hostUserId, Function<MusicSessionState, MusicSessionState> updater) {
        int retries = 0;
        while (retries < 5) {
            MusicSessionState current = getOrCreate(hostUserId);
            long versionBefore = current.version();
            MusicSessionState mutated = updater.apply(current);
            try {
                return save(mutated, versionBefore);
            } catch (ConcurrentModificationException e) {
                retries++;
                if (retries >= 5) throw e;
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                // Tiếp tục vòng lặp để thử lại với state mới nhất
            }
        }
        throw new ConcurrentModificationException("Không thể cập nhật phiên nghe sau nhiều lần thử.");
    }

    public MusicSessionState addListener(String hostUserId, String listenerUserId) {
        return update(hostUserId, current -> {
            LinkedHashSet<String> listeners = new LinkedHashSet<>(current.activeListenerIds());
            listeners.add(listenerUserId);
            return current.withActiveListenerIds(List.copyOf(listeners));
        });
    }

    public MusicSessionState removeListener(String hostUserId, String listenerUserId) {
        return update(hostUserId, current -> {
            List<String> listeners = new ArrayList<>(current.activeListenerIds());
            listeners.remove(listenerUserId);
            return current.withActiveListenerIds(List.copyOf(listeners));
        });
    }

    public MusicSessionState markEndingAfterCurrentTrack(String hostUserId) {
        return update(hostUserId, current -> {
            List<String> listeners = new ArrayList<>(current.activeListenerIds());
            listeners.remove(hostUserId); // Xóa host khỏi danh sách người nghe khi họ rời đi
            return current
                    .withEndingAfterCurrentTrack(true)
                    .withQueue(List.of())
                    .withActiveListenerIds(List.copyOf(listeners))
                    .withPlayback(current.isPlaying(), current.currentTrackId(), current.positionMs(), current.startedAtEpochMs());
        });
    }

    public void delete(String hostUserId) {
        musicSessionTemplate.delete(sessionKey(hostUserId));
    }

    public boolean deleteIfTerminal(MusicSessionState state) {
        if (state == null) {
            return false;
        }
        Long result = musicSessionTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(DELETE_IF_TERMINAL_SCRIPT, Long.class),
                Collections.singletonList(sessionKey(state.hostUserId())),
                String.valueOf(state.version())
        );
        return result != null && result == 1L;
    }

    public void linkSession(String sessionId, String hostUserId) {
        musicSessionTemplate.opsForSet().add(sessionLinksKey(sessionId), hostUserId);
    }

    public Set<String> findLinkedHostUserIds(String sessionId) {
        Set<String> members = musicSessionTemplate.opsForSet().members(sessionLinksKey(sessionId));
        return members == null ? Set.of() : members;
    }


    public void clearSessionLinks(String sessionId) {
        musicSessionTemplate.delete(sessionLinksKey(sessionId));
    }


    private String sessionKey(String hostUserId) {
        return sessionKeyPrefix + hostUserId;
    }

    private String sessionLinksKey(String sessionId) {
        return sessionLinksPrefix + sessionId;
    }
}


