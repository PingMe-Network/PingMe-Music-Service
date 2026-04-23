package org.ping_me.service.music.impl;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.ping_me.config.s3.S3Service;
import org.ping_me.dto.event.MusicListeningEvent;
import org.ping_me.dto.request.music.SongRequest;
import org.ping_me.dto.request.music.misc.SongArtistRequest;
import org.ping_me.dto.response.music.SongResponse;
import org.ping_me.dto.response.music.SongResponseWithAllAlbum;
import org.ping_me.dto.response.music.misc.AlbumSummaryDto;
import org.ping_me.dto.response.music.misc.ArtistSummaryDto;
import org.ping_me.dto.response.music.misc.GenreDto;
import org.ping_me.model.constant.ArtistRole;
import org.ping_me.model.music.*;
import org.ping_me.repository.music.*;
import org.ping_me.service.music.SongService;
import org.ping_me.service.music.util.MusicDashboardCacheService;
import org.ping_me.service.music.util.AudioUtil;
import org.ping_me.service.user.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SongServiceImpl implements SongService {

    // Repository
    SongRepository songRepository;
    ArtistRepository artistRepository;
    AlbumRepository albumRepository;
    GenreRepository genreRepository;
    SongArtistRoleRepository songArtistRoleRepository;
    SongPlayHistoryRepository songPlayHistoryRepository;

    // Utils
    AudioUtil audioUtil;

    // Service
    S3Service s3Service;

    // Provider
    CurrentUserProvider currentUserProvider;

    // Redis
    RedisTemplate<String, String> redis;

    MusicDashboardCacheService musicDashboardCacheService;


    @NonFinal
    @Value("${spring.kafka.topic.listen-music-dev}")
    String listeningMusicTopic;

    @Qualifier("kafkaObjectTemplate")
    KafkaTemplate<String, Object> kafkaObjectTemplate;
    public SongServiceImpl(
            SongRepository songRepository, ArtistRepository artistRepository,
            AlbumRepository albumRepository, GenreRepository genreRepository,
            SongArtistRoleRepository songArtistRoleRepository,
            AudioUtil audioUtil, SongPlayHistoryRepository songPlayHistoryRepository,
            @Qualifier("redisPlayCountTemplate") RedisTemplate<String, String> redis,
            S3Service s3Service,
            CurrentUserProvider currentUserProvider,
            KafkaTemplate<String, Object> kafkaObjectTemplate,
            MusicDashboardCacheService musicDashboardCacheService) {
        this.songRepository = songRepository;
        this.artistRepository = artistRepository;
        this.albumRepository = albumRepository;
        this.genreRepository = genreRepository;
        this.songArtistRoleRepository = songArtistRoleRepository;
        this.audioUtil = audioUtil;
        this.songPlayHistoryRepository = songPlayHistoryRepository;
        this.redis = redis;
        this.s3Service = s3Service;
        this.currentUserProvider = currentUserProvider;
        this.kafkaObjectTemplate = kafkaObjectTemplate;
        this.musicDashboardCacheService = musicDashboardCacheService;
    }

    @Override
    public Page<SongResponseWithAllAlbum> getAllSongs(Pageable pageable) {
        Pageable effectivePageable = withDefaultSort(pageable, Sort.by(Sort.Direction.ASC, "title"));
        Page<Long> songIdPage = songRepository.findSongIds(effectivePageable);
        return buildPagedSongResponse(songIdPage, effectivePageable);
    }


    @Override
    public SongResponse getSongById(Long id) {
        Song song = songRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài hát với id: " + id));

        return mapToSongResponse(song, song.getAlbums() != null && !song.getAlbums().isEmpty()
                ? song.getAlbums().iterator().next()
                : null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SongResponse> getSongByTitle(String title, Pageable pageable) {
        List<Song> songs = songRepository.findSongsWithAlbumsByTitle(title);
        List<SongResponse> flattened = flattenSongsWithAlbums(songs);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), flattened.size());

        List<SongResponse> pagedContent = start >= flattened.size()
                ? Collections.emptyList()
                : flattened.subList(start, end);
        return new PageImpl<>(pagedContent, pageable, flattened.size());
    }


    @Override
    public Page<SongResponseWithAllAlbum> getSongByGenre(Long id, Pageable pageable) {
        if (id == null) {
            throw new RuntimeException("Genre ID không được trống");
        }

        Pageable effectivePageable = withDefaultSort(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Long> songIdPage = songRepository.findSongIdsByGenreId(id, effectivePageable);
        return buildPagedSongResponse(songIdPage, effectivePageable);
    }


    @Override
    public Page<SongResponseWithAllAlbum> getSongByAlbum(Long id, Pageable pageable) {
        if (id == null) {
            throw new RuntimeException("Album ID không được trống");
        }

        Pageable effectivePageable = withDefaultSort(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Long> songIdPage = songRepository.findSongIdsByAlbumId(id, effectivePageable);
        return buildPagedSongResponse(songIdPage, effectivePageable);
    }


    @Override
    public Page<SongResponseWithAllAlbum> getSongsByArtist(Long artistId, Pageable pageable) {
        if (artistId == null) {
            throw new RuntimeException("Artist ID không được trống");
        }

        Pageable effectivePageable = withDefaultSort(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Long> songIdPage = songRepository.findSongIdsByArtistId(artistId, effectivePageable);
        return buildPagedSongResponse(songIdPage, effectivePageable);
    }


    // Cho phép truyền số lượng bài muốn lấy
    public List<SongResponseWithAllAlbum> getTopPlayedSongs(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "playCount"));
        Page<Long> songIdPage = songRepository.findSongIds(pageable);
        return buildPagedSongResponse(songIdPage, pageable).getContent();
    }

    private Pageable withDefaultSort(Pageable pageable, Sort defaultSort) {
        return pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }

    private Page<SongResponseWithAllAlbum> buildPagedSongResponse(Page<Long> songIdPage, Pageable pageable) {
        if (songIdPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Song> songs = songRepository.findSongsWithDetailsByIds(songIdPage.getContent());
        Map<Long, Song> songsById = songs.stream().collect(Collectors.toMap(
                Song::getId,
                song -> song,
                (existing, ignored) -> existing
        ));

        List<SongResponseWithAllAlbum> orderedContent = songIdPage.getContent().stream()
                .map(songsById::get)
                .filter(Objects::nonNull)
                .map(this::mapToSongResponseWithAllAlbums)
                .collect(Collectors.toList());

        return new PageImpl<>(orderedContent, pageable, songIdPage.getTotalElements());
    }

    @Override
    @Transactional(rollbackFor = Exception.class) // Rollback nếu lỗi S3 hoặc DB
    public List<SongResponse> save(
            SongRequest dto, MultipartFile musicFile, MultipartFile imgFile
    ) {

        // 1. Validate file đầu vào (Bắt buộc phải có)
        if (musicFile == null || musicFile.isEmpty()) {
            throw new RuntimeException("Vui lòng tải lên file nhạc");
        }
        if (imgFile == null || imgFile.isEmpty()) {
            throw new RuntimeException("Vui lòng tải lên ảnh bìa");
        }

        // 2. Khởi tạo Song Entity
        var song = new Song();
        song.setTitle(dto.getTitle());

        song.setPlayCount(0L); // Mặc định 0 view

        // 3. Upload File Nhạc lên S3
        File compressedFile = null;
        try {
            int finalDuration = 0;
            try {
                finalDuration = audioUtil.getDurationFromMusicFile(musicFile);
            } catch (Exception e) {
                log.error("Backend không đọc được duration file: " + e.getMessage());
            }
            if (finalDuration <= 0 && dto.getDuration() > 0) {
                finalDuration = dto.getDuration();
            }
            if (finalDuration <= 0) {
                finalDuration = 0;
            }
            song.setDuration(finalDuration);
            // C. Tạo tên file mới (Luôn là .mp3 vì mình nén sang mp3)
            String audioFileName = UUID.randomUUID() + ".mp3";

            // E. Upload lên S3 (S3Service không biết đây là file fake, nó cứ upload thôi)
            String songUrl = s3Service.uploadFile(
                    musicFile,
                    "music/song",
                    audioFileName,
                    false,
                    MAX_AUDIO_SIZE
            );
            song.setSongUrl(songUrl);

        } finally {
            // F. QUAN TRỌNG: Dọn dẹp file nén tạm trên ổ cứng server
            // Dù upload thành công hay thất bại cũng phải xóa để tránh đầy ổ cứng
            if (compressedFile != null && compressedFile.exists()) {
                boolean deleted = compressedFile.delete();
                if (!deleted) System.err.println("Không xóa được file tạm: " + compressedFile.getAbsolutePath());
            }
        }

        // 4. Upload File Ảnh lên S3
        String imageFileName = generateFileName(imgFile);
        String imgUrl = s3Service.uploadFile(
                imgFile,
                "music/img", // Folder trên S3
                imageFileName,
                true,
                MAX_COVER_SIZE
        );
        song.setImgUrl(imgUrl);

        // 5. Xử lý Genre (Thể loại)
        if (dto.getGenreIds() != null && dto.getGenreIds().length > 0) {
            // Lưu ý: DTO tên là Names nhưng kiểu Long[] nên t hiểu là IDs
            var genreIds = Arrays.asList(dto.getGenreIds());
            var genres = new HashSet<>(genreRepository.findAllById(genreIds));
            song.setGenres(genres);
        }

        // 6. Lưu tạm Song để có ID (quan trọng cho bước ArtistRole)
        var savedSong = songRepository.save(song);

        // 7. Xử lý Artist (Main & Featured)
        List<SongArtistRole> artistRoles = new ArrayList<>();

        // 7a. Main Artist
        var mainArtist = artistRepository.findById(dto.getMainArtistId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nghệ sĩ chính"));

        var mainRole = new SongArtistRole();
        mainRole.setSong(savedSong);
        mainRole.setArtist(mainArtist);
        mainRole.setRole(ArtistRole.MAIN_ARTIST);
        artistRoles.add(mainRole);

        // 7b. Featured Artists
        if (dto.getOtherArtists() != null && !dto.getOtherArtists().isEmpty()) {
            // Dùng Set để check trùng lặp ID nghệ sĩ trong request (tránh 1 người add 2 lần)
            Set<Long> processedArtistIds = new HashSet<>();
            processedArtistIds.add(mainArtist.getId());

            for (SongArtistRequest artistReq : dto.getOtherArtists()) {
                Long artistId = artistReq.getArtistId();
                ArtistRole role = artistReq.getRole();

                // Validate: Không cho phép add lại Main Artist vào list phụ
                // Hoặc 1 người không thể xuất hiện 2 lần trong 1 bài hát (tùy nghiệp vụ)
                if (processedArtistIds.contains(artistId)) {
                    continue; // Skip nếu trùng
                }

                var artist = artistRepository.findById(artistId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy nghệ sĩ ID: " + artistId));

                var artistRole = new SongArtistRole();
                artistRole.setSong(savedSong);
                artistRole.setArtist(artist);

                // QUAN TRỌNG: Lấy Role từ request thay vì fix cứng FEATURED_ARTIST
                artistRole.setRole(role);

                artistRoles.add(artistRole);
                processedArtistIds.add(artistId);
            }
        }

        // Lưu Batch roles
        songArtistRoleRepository.saveAll(artistRoles);
        savedSong.setArtistRoles(artistRoles); // Update lại object để map response

        // 8. Xử lý Album (CẬP NHẬT METADATA)
        if (dto.getAlbumIds() != null && dto.getAlbumIds().length > 0) {
            var albumIds = Arrays.asList(dto.getAlbumIds());
            var albums = new HashSet<>(albumRepository.findAllById(albumIds));

            // Lấy danh sách Featured Artists của bài hát hiện tại ra trước
            List<Artist> allArtistsInSong = artistRoles.stream()
                    .map(SongArtistRole::getArtist)
                    .toList();

            for (var album : albums) {
                // A. Thêm bài hát vào Album
                album.getSongs().add(savedSong);

                // B. Cập nhật Genre cho Album (Tự động merge, ko sợ trùng vì dùng Set)
                if (savedSong.getGenres() != null) {
                    album.getGenres().addAll(savedSong.getGenres());
                }

                // C. Cập nhật Featured Artist cho Album (Tự động merge)
                if (!allArtistsInSong.isEmpty()) {
                    // Lưu ý: Phải đảm bảo list featuredArtists trong Album đã được khởi tạo (new HashSet)
                    // Nếu chưa thì check null: if(album.getFeaturedArtists() == null) album.setFeaturedArtists(new HashSet<>());
                    album.getFeaturedArtists().addAll(allArtistsInSong);
                }

                // D. Lưu Album -> Hibernate sẽ update cả bảng album_song, album_genre, album_artist
                albumRepository.save(album);
            }

            // Set ngược lại cho song (chỉ để hiển thị)
            savedSong.setAlbums(albums);
        }

        // 9. Map sang Response và trả về
        Song savedSongWithDetails = songRepository.findByIdWithDetails(savedSong.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài hát vừa lưu"));

        List<Song> songs = new ArrayList<>();
        songs.add(savedSongWithDetails);
        musicDashboardCacheService.evictMusicDashboard();
        return flattenSongsWithAlbums(songs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SongResponse> update(Long id, SongRequest dto, MultipartFile musicFile, MultipartFile imgFile) throws IOException {
        // 1. Tìm bài hát
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài hát với ID: " + id));

        // 2. Update Title
        song.setTitle(dto.getTitle());

        // 3. Xử lý Audio File (Giữ nguyên logic cũ)
        if (musicFile != null && !musicFile.isEmpty()) {
            try {
                if (song.getSongUrl() != null) s3Service.deleteFileByUrl(song.getSongUrl());
            } catch (Exception e) { /* Log warning */ }

            String audioName = generateFileName(musicFile);
            String newUrl = s3Service.uploadFile(
                    musicFile,
                    "music/song",
                    audioName,
                    true, MAX_AUDIO_SIZE
            );
            song.setSongUrl(newUrl);

            int newDuration = audioUtil.getDurationFromMusicFile(musicFile);
            if (newDuration > 0) song.setDuration(newDuration);
        }

        // 4. Xử lý Image File (Giữ nguyên logic cũ)
        if (imgFile != null && !imgFile.isEmpty()) {
            try {
                if (song.getImgUrl() != null) s3Service.deleteFileByUrl(song.getImgUrl());
            } catch (Exception e) { /* Log warning */ }

            String imgName = generateFileName(imgFile);
            String newImgUrl = s3Service.uploadFile(imgFile, "music/img", imgName, true, MAX_COVER_SIZE);
            song.setImgUrl(newImgUrl);
        }

        // 5. Update Genres (Giữ nguyên logic cũ)
        if (dto.getGenreIds() != null) {
            song.getGenres().clear();
            if (dto.getGenreIds().length > 0) {
                var genreIds = Arrays.asList(dto.getGenreIds());
                var newGenres = new HashSet<>(genreRepository.findAllById(genreIds));
                song.getGenres().addAll(newGenres);
            }
        }

        // 6. Update Artist (CẬP NHẬT MỚI: Xử lý theo Role động)
        // A. Xóa sạch role cũ trong DB
        songArtistRoleRepository.deleteAll(song.getArtistRoles());
        song.getArtistRoles().clear();

        List<SongArtistRole> newRoles = new ArrayList<>();
        Set<Long> processedArtistIds = new HashSet<>(); // Để check trùng

        // B. Main Artist (Luôn phải có)
        var mainArtist = artistRepository.findById(dto.getMainArtistId())
                .orElseThrow(() -> new RuntimeException("Main Artist not found"));

        newRoles.add(new SongArtistRole(null, song, mainArtist, ArtistRole.MAIN_ARTIST));
        processedArtistIds.add(mainArtist.getId());

        // C. Other Artists (Featured, Composer, Producer...)
        if (dto.getOtherArtists() != null) {
            for (SongArtistRequest artistReq : dto.getOtherArtists()) {
                Long artistId = artistReq.getArtistId();

                // Bỏ qua nếu trùng với Main Artist hoặc trùng lặp trong list
                if (processedArtistIds.contains(artistId)) continue;

                var artist = artistRepository.findById(artistId)
                        .orElseThrow(() -> new RuntimeException("Artist not found ID: " + artistId));

                // Set role động theo request
                newRoles.add(new SongArtistRole(null, song, artist, artistReq.getRole()));
                processedArtistIds.add(artistId);
            }
        }

        // Lưu batch và cập nhật reference
        songArtistRoleRepository.saveAll(newRoles);
        song.setArtistRoles(newRoles);

        // 7. Update Albums (Logic cũ vẫn hoạt động tốt với danh sách role mới)
        // Bước A: Gỡ khỏi album cũ
        if (song.getAlbums() != null && !song.getAlbums().isEmpty()) {
            for (Album oldAlbum : song.getAlbums()) {
                oldAlbum.getSongs().remove(song);
                albumRepository.save(oldAlbum);
            }
            song.getAlbums().clear();
        }

        // Bước B: Thêm vào album mới
        if (dto.getAlbumIds() != null && dto.getAlbumIds().length > 0) {
            var newAlbumIds = Arrays.asList(dto.getAlbumIds());
            var newAlbums = new HashSet<>(albumRepository.findAllById(newAlbumIds));

            // Lấy danh sách tất cả artist (bao gồm cả Composer, Producer...) để add vào Album
            List<Artist> allArtists = newRoles.stream().map(SongArtistRole::getArtist).toList();

            for (Album album : newAlbums) {
                album.getSongs().add(song);

                if (song.getGenres() != null) {
                    album.getGenres().addAll(song.getGenres());
                }

                if (!allArtists.isEmpty()) {
                    if (album.getFeaturedArtists() == null) album.setFeaturedArtists(new HashSet<>());
                    album.getFeaturedArtists().addAll(allArtists);
                    // Remove owner khỏi featured
                    if (album.getAlbumOwner() != null) album.getFeaturedArtists().remove(album.getAlbumOwner());
                }
                albumRepository.save(album);
            }
            song.setAlbums(newAlbums);
        }

        // 8. Save & Return
        Song updatedSong = songRepository.save(song);

        // Reload với FETCH JOIN để tránh N+1
        Song updatedSongWithDetails = songRepository.findByIdWithDetails(updatedSong.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài hát vừa cập nhật"));

        List<Song> songs = new ArrayList<>();
        songs.add(updatedSongWithDetails);
        musicDashboardCacheService.evictMusicDashboard();
        return flattenSongsWithAlbums(songs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long id) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài hát với ID: " + id));

        // Đánh dấu là đã xóa
        song.setDeleted(true);

        // Lưu lại trạng thái mới
        songRepository.save(song);
        musicDashboardCacheService.evictMusicDashboard();

        // Tùy chọn: Nếu muốn user không tìm thấy bài hát này trong Album nữa,
        // bạn có thể remove nó khỏi album (logic giống hard delete) hoặc giữ nguyên.
        // Nếu giữ nguyên thì user vào Album vẫn thấy bài hát (trừ khi Album cũng lọc song deleted).
    }

    @Override
    @Transactional
    public void restore(Long id) {

        Song song = songRepository.findSoftDeletedSong(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài hát đã xóa mềm"));

        song.setDeleted(false);
        songRepository.save(song);
        musicDashboardCacheService.evictMusicDashboard();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void hardDelete(Long id) {
        // 1. Tìm bài hát (Nếu ko thấy thì báo lỗi)
        Song song = songRepository.findByIdIgnoringDeleted(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài hát với ID: " + id));

        // 2. Gỡ bài hát ra khỏi các Album (Quan trọng)
        // Vì Album là bên sở hữu quan hệ (Owner), ta nên cập nhật từ phía Album
        if (song.getAlbums() != null && !song.getAlbums().isEmpty()) {
            for (Album album : song.getAlbums()) {
                // Xóa song khỏi set songs của album
                album.getSongs().remove(song);
                // Lưu lại album để cập nhật bảng trung gian album_song
                albumRepository.save(album);
            }
        }

        // 3. Xóa file trên S3 (Dọn rác)
        // Bọc trong try-catch để lỡ S3 lỗi thì vẫn cho phép xóa DB (tùy nghiệp vụ, ở đây t để strict)
        try {
            if (song.getSongUrl() != null) {
                s3Service.deleteFileByUrl(song.getSongUrl());
            }
            if (song.getImgUrl() != null) {
                s3Service.deleteFileByUrl(song.getImgUrl());
            }
        } catch (Exception e) {
            // Tùy chọn: Throw lỗi để rollback DB nếu muốn bắt buộc xóa S3 thành công
            throw new RuntimeException("Lỗi khi xóa file trên S3: " + e.getMessage());
        }

        // 4. Xóa bài hát trong DB
        // Hibernate sẽ tự động xóa các dòng trong bảng con song_artist_role (do CascadeType.ALL)
        // và bảng trung gian song_genre.
        songRepository.delete(song);
        musicDashboardCacheService.evictMusicDashboard();
    }

    // --- Helper Methods ---

    private String generateFileName(MultipartFile file) {
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf("."));
        }
        return UUID.randomUUID() + ext;
    }

    private List<SongResponse> flattenSongsWithAlbums(List<Song> songs) {
        List<SongResponse> result = new ArrayList<>();
        for (Song song : songs) {
            if (song.getAlbums() != null && !song.getAlbums().isEmpty()) {
                for (Album album : song.getAlbums()) {
                    result.add(mapToSongResponse(song, album));
                }
            } else {
                // Nếu song không có album, vẫn trả về song với album = null
                result.add(mapToSongResponse(song, null));
            }
        }
        return result;
    }

    @Transactional
    @Override
    public void increasePlayCount(Long songId) {
        var userId = currentUserProvider.get().getId();
        String redisKey = "play:" + userId + ":" + songId;

        // Nếu trong 30s đã nghe → không tăng tiếp
        Boolean alreadyPlayed = redis.hasKey(redisKey);
        if (Boolean.TRUE.equals(alreadyPlayed)) return;

        // Tăng playCount
        songRepository.incrementPlayCount(songId, userId);

        // Lấy song để log lịch sử và lấy data gửi Kafka
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new IllegalArgumentException("Song not found"));

        // Lưu lịch sử nghe
        songPlayHistoryRepository.save(
                SongPlayHistory.builder()
                        .song(song)
                        .userId(userId)
                        .playedAt(LocalDateTime.now())
                        .build()
        );
        // Set key Redis sống 30s → debounce
        redis.opsForValue().set(redisKey, "1", Duration.ofSeconds(30));

        // Play events are high-frequency, so eviction is throttled globally.
        musicDashboardCacheService.evictMusicDashboardOnPlayIfNeeded();

        // CHỖ NÀY SỬA LẠI: Truyền nguyên object song vào để lấy tên
        publishListenMusicAudit(song);
    }

    private SongResponse mapToSongResponse(Song song, Album album) {
        SongResponse response = new SongResponse();

        response.setId(song.getId());
        response.setTitle(song.getTitle());
        response.setDuration(song.getDuration());
        response.setPlayCount(song.getPlayCount());
        response.setSongUrl(song.getSongUrl());
        response.setCoverImageUrl(song.getImgUrl());

        List<SongArtistRole> roles = song.getArtistRoles();

        // Main Artist
        roles.stream()
                .filter(r -> r.getRole() == ArtistRole.MAIN_ARTIST)
                .findFirst()
                .ifPresent(r -> response.setMainArtist(
                        new ArtistSummaryDto(
                                r.getArtist().getId(),
                                r.getArtist().getName(),
                                ArtistRole.MAIN_ARTIST,
                                r.getArtist().getImgUrl()
                        )
                ));

        List<ArtistSummaryDto> otherArtists = roles.stream()
                .filter(r -> r.getRole() != ArtistRole.MAIN_ARTIST)
                .map(r -> new ArtistSummaryDto(
                        r.getArtist().getId(),
                        r.getArtist().getName(),
                        r.getRole(),
                        r.getArtist().getImgUrl()
                ))
                .collect(Collectors.toList());
        response.setOtherArtists(otherArtists);

        if (song.getGenres() != null) {
            List<GenreDto> genreDtos = song.getGenres().stream()
                    .map(g -> new GenreDto(g.getId(), g.getName()))
                    .collect(Collectors.toList());
            response.setGenres(genreDtos);
        }

        if (album != null) {
            response.setAlbum(new AlbumSummaryDto(album.getId(), album.getTitle(), album.getPlayCount()));
        }

        return response;
    }

    private SongResponseWithAllAlbum mapToSongResponseWithAllAlbums(Song song) {
        SongResponseWithAllAlbum response = new SongResponseWithAllAlbum();

        // 1. Basic Info
        response.setId(song.getId());
        response.setTitle(song.getTitle());
        response.setDuration(song.getDuration());
        response.setPlayCount(song.getPlayCount());
        response.setSongUrl(song.getSongUrl());
        response.setCoverImageUrl(song.getImgUrl());

        List<SongArtistRole> roles = song.getArtistRoles();

        // 2. Main Artist
        roles.stream()
                .filter(r -> r.getRole() == ArtistRole.MAIN_ARTIST)
                .findFirst()
                .ifPresent(r -> response.setMainArtist(
                        new ArtistSummaryDto(
                                r.getArtist().getId(),
                                r.getArtist().getName(),
                                ArtistRole.MAIN_ARTIST,
                                r.getArtist().getImgUrl()
                                // Set role cứng hoặc lấy r.getRole()
                        )
                ));

        // 3. Other Artists (Featured, Composer, Producer...)
        // SỬA: Lấy tất cả trừ Main và map Role động vào DTO
        List<ArtistSummaryDto> otherArtists = roles.stream()
                .filter(r -> r.getRole() != ArtistRole.MAIN_ARTIST)
                .map(r -> new ArtistSummaryDto(
                        r.getArtist().getId(),
                        r.getArtist().getName(),
                        r.getRole(),
                        r.getArtist().getImgUrl()
                        // <--- QUAN TRỌNG: Lấy role thực tế từ DB
                ))
                .collect(Collectors.toList());

        // Giả sử DTO của bạn tên field là otherArtists (hoặc featuredArtists tùy bạn đặt)
        response.setOtherArtists(otherArtists);

        // 4. Genres
        List<GenreDto> genreDtos = song.getGenres().stream()
                .map(g -> new GenreDto(g.getId(), g.getName()))
                .collect(Collectors.toList());
        response.setGenres(genreDtos);

        // 5. Albums (List All)
        if (song.getAlbums() != null && !song.getAlbums().isEmpty()) {
            List<AlbumSummaryDto> albumSummaries = song.getAlbums().stream()
                    .map(a -> new AlbumSummaryDto(a.getId(), a.getTitle(), a.getPlayCount()))
                    .collect(Collectors.toList());
            response.setAlbums(albumSummaries);
        } else {
            response.setAlbums(new ArrayList<>()); // Trả về list rỗng thay vì null cho an toàn
        }

        return response;
    }

    private void publishListenMusicAudit(Song song) {
        try {
            // Lọc ra tên ca sĩ chính (MAIN_ARTIST)
            String mainArtistName = song.getArtistRoles().stream()
                    .filter(role -> role.getRole() == ArtistRole.MAIN_ARTIST)
                    .map(role -> role.getArtist().getName())
                    .findFirst()
                    .orElse("Unknown Artist");

            // Lưu ý: Sếp nhớ update DTO MusicListeningEvent thêm 2 tham số này vào constructor nhé
            MusicListeningEvent event = new MusicListeningEvent(
                    song.getId(),
                    song.getTitle(),   // THÊM TÊN BÀI HÁT
                    mainArtistName,    // THÊM TÊN CA SĨ
                    System.currentTimeMillis()
            );

            kafkaObjectTemplate.send(listeningMusicTopic, event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Kafka: Đã gửi event cho music {}", song.getId());
                        } else {
                            log.error("Kafka: Gửi event thất bại: {}", ex.getMessage());
                        }
                    });

        } catch (Exception ex) {
            log.error("Lỗi nghiêm trọng khi chuẩn bị gửi Kafka event: {}", ex.getMessage());
        }
    }
}
