package org.ping_me.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ping_me.dto.base.ApiResponse;
import org.ping_me.dto.base.PageResponse;
import org.ping_me.dto.request.music.SongRequest;
import org.ping_me.dto.response.music.SongResponse;
import org.ping_me.dto.response.music.SongResponseWithAllAlbum;
import org.ping_me.service.music.SongService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(
        name = "Songs",
        description = "🎵 Quản lý bài hát: tìm kiếm, phát nhạc, upload, cập nhật, xóa & khôi phục"
)
@RestController
@RequestMapping("/music-service/songs")
@RequiredArgsConstructor
public class SongController {

    private final SongService songService;

    // ========================= GET BY ID =========================
    @Operation(
            summary = "Lấy chi tiết bài hát",
            description = "Trả về thông tin chi tiết của một bài hát theo ID"
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SongResponse>> getSongDetail(
            @Parameter(description = "ID bài hát", example = "1")
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(new ApiResponse<>(songService.getSongById(id)));
    }

    // ========================= GET ALL =========================
    @Operation(
            summary = "Lấy danh sách tất cả bài hát",
            description = "Trả về danh sách bài hát kèm album, artist, genre"
    )
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<PageResponse<SongResponseWithAllAlbum>>> getAllSongs(
            @PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        var page = songService.getAllSongs(pageable);
        var pageResponse = new PageResponse<>(page);

        return ResponseEntity.ok(new ApiResponse<>(pageResponse));
    }

    // ========================= SEARCH BY TITLE =========================
    @Operation(
            summary = "Tìm bài hát theo tên",
            description = "Tìm kiếm bài hát gần đúng theo tiêu đề"
    )
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<SongResponse>>> getSongByTitle(
            @RequestParam("title") String title,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<SongResponse> page = songService.getSongByTitle(title, pageable);
        return ResponseEntity.ok(new ApiResponse<>(new PageResponse<>(page)));
    }


    // ========================= SEARCH BY ALBUM =========================
    @Operation(
            summary = "Lấy bài hát theo album",
            description = "Trả về danh sách bài hát thuộc một album"
    )
    @GetMapping("/search-by-album")
    public ResponseEntity<ApiResponse<PageResponse<SongResponseWithAllAlbum>>> getSongByAlbum(
            @RequestParam("id") Long albumId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<SongResponseWithAllAlbum> page = songService.getSongByAlbum(albumId, pageable);
        return ResponseEntity.ok(new ApiResponse<>(new PageResponse<>(page)));
    }


    // ========================= SEARCH BY ARTIST =========================
    @Operation(
            summary = "Lấy bài hát theo nghệ sĩ",
            description = "Trả về tất cả bài hát của một nghệ sĩ"
    )
    @GetMapping("/search-by-artist")
    public ResponseEntity<ApiResponse<PageResponse<SongResponseWithAllAlbum>>> getSongsByArtist(
            @RequestParam("id") Long artistId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<SongResponseWithAllAlbum> page = songService.getSongsByArtist(artistId, pageable);
        return ResponseEntity.ok(new ApiResponse<>(new PageResponse<>(page)));
    }

    // ========================= SEARCH BY GENRE =========================
    @Operation(
            summary = "Lấy bài hát theo thể loại",
            description = "Trả về danh sách bài hát thuộc một genre"
    )
    @GetMapping("/genre")
    public ResponseEntity<ApiResponse<PageResponse<SongResponseWithAllAlbum>>> getByGenre(
            @RequestParam("id") Long genreId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<SongResponseWithAllAlbum> page = songService.getSongByGenre(genreId, pageable);
        return ResponseEntity.ok(new ApiResponse<>(new PageResponse<>(page)));
    }


    // ========================= TOP PLAYED =========================
    @Operation(
            summary = "Lấy top bài hát nghe nhiều nhất",
            description = "Trả về danh sách bài hát có lượt nghe cao nhất"
    )
    @GetMapping("/getTopSong/{number}")
    public ResponseEntity<ApiResponse<List<SongResponseWithAllAlbum>>> getTopSongs(
            @Parameter(description = "Số lượng bài hát", example = "10")
            @PathVariable int number
    ) {
        return ResponseEntity.ok(new ApiResponse<>(songService.getTopPlayedSongs(number)));
    }

    // ========================= SAVE SONG =========================
    @Operation(
            summary = "Thêm bài hát mới",
            description = "Upload bài hát kèm file nhạc & ảnh bìa (multipart/form-data)"
    )
    @PostMapping("/save")
    public ResponseEntity<ApiResponse<List<SongResponse>>> save(
            @Parameter(
                    description = "Thông tin bài hát",
                    content = @Content(schema = @Schema(implementation = SongRequest.class))
            )
            @Valid @RequestPart("songRequest") SongRequest songRequest,

            @Parameter(description = "File nhạc (.mp3, .wav)")
            @RequestPart("musicFile") MultipartFile musicFile,

            @Parameter(description = "Ảnh bìa bài hát")
            @RequestPart("imgFile") MultipartFile imgFile
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(songService.save(songRequest, musicFile, imgFile)));
    }

    // ========================= UPDATE SONG =========================
    @Operation(
            summary = "Cập nhật bài hát",
            description = "Cập nhật thông tin bài hát, có thể thay file nhạc hoặc ảnh"
    )
    @PutMapping("/update/{id}")
    public ResponseEntity<ApiResponse<List<SongResponse>>> update(
            @Parameter(description = "ID bài hát", example = "1")
            @PathVariable Long id,

            @Valid @RequestPart("songRequest") SongRequest songRequest,

            @RequestPart(value = "musicFile", required = false)
            MultipartFile musicFile,

            @RequestPart(value = "imgFile", required = false)
            MultipartFile imgFile
    ) throws IOException {
        return ResponseEntity.ok(new ApiResponse<>(songService.update(id, songRequest, musicFile, imgFile)));
    }

    // ========================= SOFT DELETE =========================
    @Operation(summary = "Xóa mềm bài hát", description = "Ẩn bài hát khỏi hệ thống")
    @DeleteMapping("/soft-delete/{id}")
    public ResponseEntity<ApiResponse<Void>> softDelete(
            @Parameter(description = "ID bài hát", example = "1")
            @PathVariable Long id
    ) {
        songService.softDelete(id);
        return ResponseEntity.ok(new ApiResponse<>(null));
    }

    // ========================= HARD DELETE =========================
    @Operation(summary = "Xóa vĩnh viễn bài hát")
    @DeleteMapping("/hard-delete/{id}")
    public ResponseEntity<ApiResponse<Void>> hardDelete(
            @Parameter(description = "ID bài hát", example = "1")
            @PathVariable Long id
    ) {
        songService.hardDelete(id);
        return ResponseEntity.ok(new ApiResponse<>(null));
    }

    // ========================= RESTORE =========================
    @Operation(summary = "Khôi phục bài hát đã xóa")
    @PutMapping("/restore/{id}")
    public ResponseEntity<ApiResponse<Void>> restore(
            @Parameter(description = "ID bài hát", example = "1")
            @PathVariable Long id
    ) {
        songService.restore(id);
        return ResponseEntity.ok(new ApiResponse<>(null));
    }

    // ========================= PLAY COUNT =========================
    @Operation(
            summary = "Tăng lượt nghe",
            description = "Tăng play count khi người dùng phát bài hát"
    )
    @PostMapping("/{id}/play")
    public ResponseEntity<ApiResponse<Void>> increasePlayCount(
            @Parameter(description = "ID bài hát", example = "1")
            @PathVariable Long id
    ) {
        songService.increasePlayCount(id);
        return ResponseEntity.ok(new ApiResponse<>(null));
    }
}
