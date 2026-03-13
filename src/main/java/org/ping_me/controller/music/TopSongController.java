package org.ping_me.controller.music;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.ping_me.dto.base.ApiResponse;
import org.ping_me.dto.response.music.misc.TopSongPlayCounterDto;
import org.ping_me.service.music.SongPlayHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(
        name = "Top Songs",
        description = "🔥 BXH bài hát được nghe nhiều nhất theo ngày / tuần / tháng (có cache)"
)
@RestController
@RequestMapping("/music-service/top-songs")
@RequiredArgsConstructor
public class TopSongController {

    private final SongPlayHistoryService songPlayHistoryService;

    // ========================= TODAY =========================
    @Operation(
            summary = "Top bài hát hôm nay",
            description = "Trả về danh sách bài hát được nghe nhiều nhất trong ngày hiện tại (cached)"
    )
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<TopSongPlayCounterDto>>> getTopSongsToday(
            @Parameter(
                    description = "Số lượng bài hát trong BXH",
                    example = "10"
            )
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(new ApiResponse<>(songPlayHistoryService.getTopSongsTodayCached(limit)));
    }

    // ========================= WEEK =========================
    @Operation(
            summary = "Top bài hát tuần này",
            description = "Trả về danh sách bài hát được nghe nhiều nhất trong tuần hiện tại (cached)"
    )
    @GetMapping("/week")
    public ResponseEntity<ApiResponse<List<TopSongPlayCounterDto>>> getTopSongsThisWeek(
            @Parameter(
                    description = "Số lượng bài hát trong BXH",
                    example = "10"
            )
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(new ApiResponse<>(songPlayHistoryService.getTopSongsThisWeekCached(limit)));
    }

    // ========================= MONTH =========================
    @Operation(
            summary = "Top bài hát tháng này",
            description = "Trả về danh sách bài hát được nghe nhiều nhất trong tháng hiện tại (cached)"
    )
    @GetMapping("/month")
    public ResponseEntity<ApiResponse<List<TopSongPlayCounterDto>>> getTopSongsThisMonth(
            @Parameter(
                    description = "Số lượng bài hát trong BXH",
                    example = "10"
            )
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(new ApiResponse<>(songPlayHistoryService.getTopSongsThisMonthCached(limit)));
    }
}
