package org.ping_me.service.music;

import org.ping_me.dto.response.music.MusicDashboardResponse;

public interface MusicDashboardService {
    MusicDashboardResponse getDashboard(
            int topSongsLimit,
            int albumLimit,
            int artistLimit,
            int genreLimit,
            int rankingLimit
    );
}

