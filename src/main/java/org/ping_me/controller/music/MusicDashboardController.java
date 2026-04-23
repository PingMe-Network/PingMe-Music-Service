package org.ping_me.controller.music;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.ping_me.dto.base.ApiResponse;
import org.ping_me.dto.response.music.MusicDashboardResponse;
import org.ping_me.service.music.MusicDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Music Dashboard",
        description = "Tong hop du lieu trang home music trong mot request"
)
@RestController
@RequestMapping("/music-service/dashboard")
@RequiredArgsConstructor
public class MusicDashboardController {

    private final MusicDashboardService musicDashboardService;

    @Operation(summary = "Lay du lieu dashboard", description = "Tra ve top songs, album, artist, genre va ranking")
    @GetMapping
    public ResponseEntity<ApiResponse<MusicDashboardResponse>> getDashboard(
            @RequestParam(defaultValue = "10") int topSongsLimit,
            @RequestParam(defaultValue = "5") int albumLimit,
            @RequestParam(defaultValue = "10") int artistLimit,
            @RequestParam(defaultValue = "10") int genreLimit,
            @RequestParam(defaultValue = "10") int rankingLimit
    ) {
        var data = musicDashboardService.getDashboard(
                topSongsLimit,
                albumLimit,
                artistLimit,
                genreLimit,
                rankingLimit
        );

        return ResponseEntity.ok(new ApiResponse<>(data));
    }
}

