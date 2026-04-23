package org.ping_me.service.music.util;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Service
public class MusicDashboardCacheService {

    private static final String DASHBOARD_CACHE_NAME = "music_dashboard";

    private final CacheManager cacheManager;
    private final RedisTemplate<String, String> redis;

    public MusicDashboardCacheService(
            CacheManager cacheManager,
            @Qualifier("redisPlayCountTemplate") RedisTemplate<String, String> redis
    ) {
        this.cacheManager = cacheManager;
        this.redis = redis;
    }

    @Value("${app.cache.dashboard.play-evict.enabled:true}")
    private boolean playEvictEnabled;

    @Value("${app.cache.dashboard.play-evict.throttle-seconds:30}")
    private long playEvictThrottleSeconds;

    @Value("${app.cache.dashboard.play-evict.lock-key:cache:music_dashboard:play-evict}")
    private String playEvictLockKey;

    @Value("${app.cache.dashboard.redis-key-prefix:music:dashboard:}")
    private String dashboardKeyPrefix;

    public void evictMusicDashboard() {
        Cache cache = cacheManager.getCache(DASHBOARD_CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }

        Set<String> dashboardKeys = redis.keys(dashboardKeyPrefix + "*");
        if (dashboardKeys != null && !dashboardKeys.isEmpty()) {
            redis.delete(dashboardKeys);
        }
    }

    public void evictMusicDashboardOnPlayIfNeeded() {
        if (!playEvictEnabled) {
            return;
        }

        long throttleSeconds = Math.max(playEvictThrottleSeconds, 1L);
        Boolean firstRequester = redis.opsForValue()
                .setIfAbsent(playEvictLockKey, "1", Duration.ofSeconds(throttleSeconds));

        if (Boolean.TRUE.equals(firstRequester)) {
            evictMusicDashboard();
        }
    }
}
