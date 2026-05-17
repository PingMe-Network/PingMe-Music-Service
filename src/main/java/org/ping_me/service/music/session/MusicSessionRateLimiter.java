package org.ping_me.service.music.session;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class MusicSessionRateLimiter {

    private static final int MAX_COMMANDS_PER_SECOND = 10;
    private static final Duration WINDOW = Duration.ofSeconds(1);

    private final RedisTemplate<String, String> musicSessionTemplate;

    public MusicSessionRateLimiter(
            @Qualifier("musicSessionTemplate") RedisTemplate<String, String> musicSessionTemplate
    ) {
        this.musicSessionTemplate = musicSessionTemplate;
    }

    public boolean tryConsume(String sessionId, String userId) {
        if (sessionId == null || userId == null) {
            return false;
        }

        String key = "music_session_rate:" + sessionId + ":" + userId;
        Long count = musicSessionTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            musicSessionTemplate.expire(key, WINDOW);
        }

        return count != null && count <= MAX_COMMANDS_PER_SECOND;
    }
}
