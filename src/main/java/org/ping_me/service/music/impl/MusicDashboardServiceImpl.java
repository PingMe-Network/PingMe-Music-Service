package org.ping_me.service.music.impl;

import org.ping_me.dto.response.music.MusicDashboardResponse;
import org.ping_me.dto.response.music.RankingData;
import org.ping_me.service.music.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Service
public class MusicDashboardServiceImpl implements MusicDashboardService {

    private final SongService songService;
    private final AlbumService albumService;
    private final ArtistService artistService;
    private final GenreService genreService;
    private final SongPlayHistoryService songPlayHistoryService;
    private final ObjectMapper objectMapper;

    @Qualifier("redisSongHistoryTemplate")
    private final RedisTemplate<String, Object> redis;

    @Value("${app.cache.dashboard.ttl-seconds:30}")
    private long dashboardTtlSeconds;

    @Value("${app.cache.dashboard.redis-key-prefix:music:dashboard:}")
    private String dashboardKeyPrefix;

    public MusicDashboardServiceImpl(
            SongService songService,
            AlbumService albumService,
            ArtistService artistService,
            GenreService genreService,
            SongPlayHistoryService songPlayHistoryService,
            ObjectMapper objectMapper,
            @Qualifier("redisSongHistoryTemplate") RedisTemplate<String, Object> redis
    ) {
        this.songService = songService;
        this.albumService = albumService;
        this.artistService = artistService;
        this.genreService = genreService;
        this.songPlayHistoryService = songPlayHistoryService;
        this.objectMapper = objectMapper;
        this.redis = redis;
    }

    @Override
    public MusicDashboardResponse getDashboard(
            int topSongsLimit,
            int albumLimit,
            int artistLimit,
            int genreLimit,
            int rankingLimit
    ) {
        int safeTopSongsLimit = Math.max(topSongsLimit, 1);
        int safeAlbumLimit = Math.max(albumLimit, 1);
        int safeArtistLimit = Math.max(artistLimit, 1);
        int safeGenreLimit = Math.max(genreLimit, 1);
        int safeRankingLimit = Math.max(rankingLimit, 1);
        String redisKey = dashboardKeyPrefix
                + "ts:" + safeTopSongsLimit
                + ":al:" + safeAlbumLimit
                + ":ar:" + safeArtistLimit
                + ":g:" + safeGenreLimit
                + ":r:" + safeRankingLimit;

        Object cached = redis.opsForValue().get(redisKey);
        if (cached instanceof MusicDashboardResponse response) {
            return response;
        }
        if (cached != null) {
            return objectMapper.convertValue(cached, MusicDashboardResponse.class);
        }

        var topSongs = songService.getTopPlayedSongs(safeTopSongsLimit);
        var popularAlbums = albumService
                .getPopularAlbums(PageRequest.of(0, safeAlbumLimit, Sort.by(Sort.Direction.DESC, "playCount")))
                .getContent();
        var popularArtists = artistService
                .getAllArtists(PageRequest.of(0, safeArtistLimit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        var genres = genreService
                .getAllGenres(PageRequest.of(0, safeGenreLimit, Sort.by(Sort.Direction.ASC, "name")))
                .getContent();

        var rankings = RankingData.builder()
                .today(songPlayHistoryService.getTopSongsTodayCached(safeRankingLimit))
                .week(songPlayHistoryService.getTopSongsThisWeekCached(safeRankingLimit))
                .month(songPlayHistoryService.getTopSongsThisMonthCached(safeRankingLimit))
                .build();

        MusicDashboardResponse response = MusicDashboardResponse.builder()
                .topSongs(topSongs)
                .popularAlbums(popularAlbums)
                .popularArtists(popularArtists)
                .genres(genres)
                .rankings(rankings)
                .build();

        redis.opsForValue().set(redisKey, response, Duration.ofSeconds(Math.max(dashboardTtlSeconds, 1L)));
        return response;
    }
}

