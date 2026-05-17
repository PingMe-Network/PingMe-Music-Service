package org.ping_me.service.music.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ping_me.dto.music.session.TrackSummary;
import org.ping_me.dto.response.music.SongResponse;
import org.ping_me.service.music.SongService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TrackSummaryCacheService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(45);

    private final RedisTemplate<String, String> musicSessionTemplate;
    private final ObjectMapper objectMapper;
    private final SongService songService;

    public TrackSummaryCacheService(
            @Qualifier("musicSessionTemplate") RedisTemplate<String, String> musicSessionTemplate,
            ObjectMapper objectMapper,
            SongService songService
    ) {
        this.musicSessionTemplate = musicSessionTemplate;
        this.objectMapper = objectMapper;
        this.songService = songService;
    }

    public TrackSummary resolve(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return TrackSummary.minimal(trackId);
        }

        String cacheKey = cacheKey(trackId);
        String cached = musicSessionTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, TrackSummary.class);
            } catch (JsonProcessingException ignored) {
                musicSessionTemplate.delete(cacheKey);
            }
        }

        try {
            SongResponse song = songService.getSongById(Long.parseLong(trackId));
            TrackSummary summary = new TrackSummary(
                    trackId,
                    song == null ? null : song.getTitle(),
                    song == null || song.getMainArtist() == null ? null : song.getMainArtist().getName(),
                    song == null ? null : song.getCoverImageUrl()
            );
            musicSessionTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(summary), CACHE_TTL);
            return summary;
        } catch (Exception ignored) {
            return TrackSummary.minimal(trackId);
        }
    }

    private String cacheKey(String trackId) {
        return "music:track-summary:" + trackId;
    }
}
