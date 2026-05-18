package org.ping_me.service.music.guess;

import lombok.RequiredArgsConstructor;
import org.ping_me.dto.music.guess.*;
import org.ping_me.dto.music.guess.state.*;
import org.ping_me.model.constant.ArtistRole;
import org.ping_me.model.music.Song;
import org.ping_me.model.music.SongArtistRole;
import org.ping_me.model.user.User;
import org.ping_me.repository.music.SongRepository;
import org.ping_me.repository.music.guess.MusicGuessRedisRepository;
import org.ping_me.service.user.CurrentUserProvider;
import org.ping_me.websocket.MusicGuessEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class MusicGuessService {

    private static final int DEFAULT_TOTAL_ROUNDS = 10;
    private static final int DEFAULT_OPTION_COUNT = 4;
    private static final int DEFAULT_CLIP_SECONDS = 8;
    private static final int DEFAULT_ROUND_DURATION_SECONDS = 20;

    private final SongRepository songRepository;
    private final MusicGuessRedisRepository sessionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final MusicGuessEventPublisher eventPublisher;

    public MusicGuessSessionResponse createSession(CreateMusicGuessSessionRequest request) {
        Actor actor = currentActor();
        MusicGuessMode mode = request != null && request.mode() != null ? request.mode() : MusicGuessMode.SOLO;
        int totalRounds = clamp(request == null ? null : request.totalRounds(), 3, 20, DEFAULT_TOTAL_ROUNDS);
        int optionCount = clamp(request == null ? null : request.optionCount(), 2, 6, DEFAULT_OPTION_COUNT);
        int clipSeconds = clamp(request == null ? null : request.clipSeconds(), 5, 15, DEFAULT_CLIP_SECONDS);
        int roundDurationSeconds = clamp(
                request == null ? null : request.roundDurationSeconds(),
                clipSeconds + 4,
                45,
                DEFAULT_ROUND_DURATION_SECONDS
        );

        String sessionId = UUID.randomUUID().toString();
        String roomCode = generateRoomCode();
        List<MusicGuessRoundState> rounds = createRounds(totalRounds, optionCount, clipSeconds);
        Map<String, MusicGuessPlayerState> players = new LinkedHashMap<>();
        players.put(actor.userId(), new MusicGuessPlayerState(actor.userId(), actor.displayName(), 0, 0, true));

        boolean startsImmediately = mode == MusicGuessMode.SOLO;
        MusicGuessSessionState state = new MusicGuessSessionState(
                sessionId,
                roomCode,
                mode,
                startsImmediately ? MusicGuessSessionStatus.PLAYING : MusicGuessSessionStatus.WAITING,
                actor.userId(),
                actor.displayName(),
                totalRounds,
                optionCount,
                clipSeconds,
                roundDurationSeconds,
                startsImmediately ? 0 : -1,
                startsImmediately ? nowPlusSeconds(roundDurationSeconds) : 0L,
                rounds,
                players,
                1L,
                Instant.now()
        );

        MusicGuessSessionState saved = sessionRepository.save(state);
        MusicGuessSessionResponse response = toResponse(saved, actor.userId());
        eventPublisher.broadcast(saved.sessionId(), MusicGuessEventType.SESSION_STATE, toPublicResponse(saved));
        return response;
    }

    public MusicGuessSessionResponse joinByRoomCode(JoinMusicGuessSessionRequest request) {
        Actor actor = currentActor();
        if (request == null || request.roomCode() == null || request.roomCode().isBlank()) {
            throw new IllegalArgumentException("Mã phòng không hợp lệ");
        }

        MusicGuessSessionState current = sessionRepository.findByRoomCode(request.roomCode())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phòng đoán nhạc"));

        if (current.mode() != MusicGuessMode.MULTIPLAYER) {
            throw new IllegalArgumentException("Phòng này không phải phòng nhiều người");
        }
        if (current.status() != MusicGuessSessionStatus.WAITING) {
            throw new IllegalArgumentException("Phòng đã bắt đầu");
        }

        Map<String, MusicGuessPlayerState> players = new LinkedHashMap<>(current.players());
        MusicGuessPlayerState existing = players.get(actor.userId());
        int score = existing == null ? 0 : existing.score();
        int answeredRounds = existing == null ? 0 : existing.answeredRounds();
        players.put(actor.userId(), new MusicGuessPlayerState(actor.userId(), actor.displayName(), score, answeredRounds, true));

        MusicGuessSessionState saved = copySession(current, current.status(), current.currentRoundIndex(),
                current.roundEndsAtEpochMs(), current.rounds(), players);
        eventPublisher.broadcast(saved.sessionId(), MusicGuessEventType.PLAYER_JOINED, toPublicResponse(saved));
        return toResponse(saved, actor.userId());
    }

    public MusicGuessSessionResponse getSession(String sessionId) {
        Actor actor = currentActor();
        MusicGuessSessionState state = findSession(sessionId);
        ensureParticipant(state, actor.userId());
        return toResponse(state, actor.userId());
    }

    public MusicGuessSessionResponse startSession(String sessionId) {
        return startSessionAs(sessionId, currentActor());
    }

    public MusicGuessSessionResponse startSessionAs(String sessionId, String userId, String displayName) {
        return startSessionAs(sessionId, new Actor(userId, displayName));
    }

    public MusicGuessSessionResponse nextRound(String sessionId) {
        return nextRoundAs(sessionId, currentActor());
    }

    public MusicGuessSessionResponse nextRoundAs(String sessionId, String userId, String displayName) {
        return nextRoundAs(sessionId, new Actor(userId, displayName));
    }

    public MusicGuessAnswerResult answer(String sessionId, MusicGuessAnswerRequest request) {
        return answerAs(sessionId, request, currentActor());
    }

    public MusicGuessAnswerResult answerAs(String sessionId, MusicGuessAnswerRequest request, String userId, String displayName) {
        return answerAs(sessionId, request, new Actor(userId, displayName));
    }

    private synchronized MusicGuessSessionResponse startSessionAs(String sessionId, Actor actor) {
        MusicGuessSessionState current = findSession(sessionId);
        ensureHost(current, actor.userId());
        if (current.status() == MusicGuessSessionStatus.FINISHED) {
            throw new IllegalArgumentException("Phiên đã kết thúc");
        }
        if (current.status() == MusicGuessSessionStatus.PLAYING) {
            return toResponse(current, actor.userId());
        }

        MusicGuessSessionState saved = copySession(current, MusicGuessSessionStatus.PLAYING, 0,
                nowPlusSeconds(current.roundDurationSeconds()), current.rounds(), current.players());
        eventPublisher.broadcast(saved.sessionId(), MusicGuessEventType.SESSION_STATE, toPublicResponse(saved));
        return toResponse(saved, actor.userId());
    }

    private synchronized MusicGuessSessionResponse nextRoundAs(String sessionId, Actor actor) {
        MusicGuessSessionState current = findSession(sessionId);
        ensureParticipant(current, actor.userId());
        if (current.mode() == MusicGuessMode.MULTIPLAYER) {
            ensureHost(current, actor.userId());
        }
        if (current.status() == MusicGuessSessionStatus.FINISHED) {
            return toResponse(current, actor.userId());
        }
        if (current.currentRoundIndex() + 1 >= current.totalRounds()) {
            MusicGuessSessionState finished = copySession(current, MusicGuessSessionStatus.FINISHED,
                    current.currentRoundIndex(), current.roundEndsAtEpochMs(), current.rounds(), current.players());
            eventPublisher.broadcast(finished.sessionId(), MusicGuessEventType.SESSION_FINISHED, toPublicResponse(finished));
            return toResponse(finished, actor.userId());
        }

        MusicGuessSessionState saved = copySession(current, MusicGuessSessionStatus.PLAYING,
                current.currentRoundIndex() + 1, nowPlusSeconds(current.roundDurationSeconds()),
                current.rounds(), current.players());
        eventPublisher.broadcast(saved.sessionId(), MusicGuessEventType.SESSION_STATE, toPublicResponse(saved));
        return toResponse(saved, actor.userId());
    }

    private synchronized MusicGuessAnswerResult answerAs(String sessionId, MusicGuessAnswerRequest request, Actor actor) {
        if (request == null || request.roundId() == null || request.optionId() == null) {
            throw new IllegalArgumentException("Câu trả lời không hợp lệ");
        }

        MusicGuessSessionState current = findSession(sessionId);
        ensureParticipant(current, actor.userId());
        if (current.status() != MusicGuessSessionStatus.PLAYING) {
            throw new IllegalArgumentException("Phiên chưa bắt đầu hoặc đã kết thúc");
        }

        MusicGuessRoundState round = current.currentRound();
        if (round == null || !round.roundId().equals(request.roundId())) {
            throw new IllegalArgumentException("Vòng chơi không hợp lệ");
        }

        Map<String, MusicGuessAnswerState> answers = new LinkedHashMap<>(round.answers());
        MusicGuessAnswerState existing = answers.get(actor.userId());
        if (existing != null) {
            MusicGuessAnswerResult result = buildAnswerResult(current, round, existing, actor.userId(), false);
            eventPublisher.sendToUser(actor.userId(), MusicGuessEventType.ANSWER_RESULT, result);
            return result;
        }

        boolean correct = round.correctOptionId().equals(request.optionId());
        long answeredAt = request.answeredAtEpochMs() == null ? System.currentTimeMillis() : request.answeredAtEpochMs();
        int earnedPoints = correct ? calculatePoints(answeredAt, current.roundEndsAtEpochMs()) : 0;
        MusicGuessAnswerState answer = new MusicGuessAnswerState(request.optionId(), correct, earnedPoints, answeredAt);
        answers.put(actor.userId(), answer);

        MusicGuessRoundState updatedRound = new MusicGuessRoundState(
                round.roundId(),
                round.songId(),
                round.title(),
                round.artistName(),
                round.audioUrl(),
                round.coverImageUrl(),
                round.previewStartMs(),
                round.correctOptionId(),
                round.options(),
                answers
        );

        List<MusicGuessRoundState> rounds = new ArrayList<>(current.rounds());
        rounds.set(current.currentRoundIndex(), updatedRound);

        Map<String, MusicGuessPlayerState> players = new LinkedHashMap<>(current.players());
        MusicGuessPlayerState player = players.get(actor.userId());
        if (player == null) {
            player = new MusicGuessPlayerState(actor.userId(), actor.displayName(), 0, 0, true);
        }
        players.put(actor.userId(), new MusicGuessPlayerState(
                actor.userId(),
                player.displayName(),
                player.score() + earnedPoints,
                player.answeredRounds() + 1,
                true
        ));

        boolean roundComplete = isRoundComplete(updatedRound, players);
        boolean finishSession = roundComplete && current.currentRoundIndex() + 1 >= current.totalRounds();
        MusicGuessSessionState saved = copySession(
                current,
                finishSession ? MusicGuessSessionStatus.FINISHED : current.status(),
                current.currentRoundIndex(),
                current.roundEndsAtEpochMs(),
                rounds,
                players
        );

        MusicGuessAnswerResult result = buildAnswerResult(saved, updatedRound, answer, actor.userId(), roundComplete);
        eventPublisher.sendToUser(actor.userId(), MusicGuessEventType.ANSWER_RESULT, result);
        eventPublisher.broadcast(saved.sessionId(), MusicGuessEventType.SCOREBOARD_UPDATED, scoreboard(saved));
        if (roundComplete) {
            eventPublisher.broadcast(saved.sessionId(), MusicGuessEventType.ROUND_REVEALED, toResponse(saved, actor.userId()).round());
        }
        if (finishSession) {
            eventPublisher.broadcast(saved.sessionId(), MusicGuessEventType.SESSION_FINISHED, toPublicResponse(saved));
        }
        return result;
    }

    public MusicGuessSessionResponse toPublicResponse(MusicGuessSessionState state) {
        return toResponse(state, null);
    }

    private MusicGuessSessionResponse toResponse(MusicGuessSessionState state, String viewerUserId) {
        MusicGuessRoundDto roundDto = null;
        MusicGuessRoundState currentRound = state.currentRound();
        if (currentRound != null && state.status() != MusicGuessSessionStatus.WAITING) {
            MusicGuessAnswerState viewerAnswer = viewerUserId == null ? null : currentRound.answers().get(viewerUserId);
            boolean reveal = state.status() == MusicGuessSessionStatus.FINISHED ||
                    viewerAnswer != null ||
                    isRoundComplete(currentRound, state.players());
            roundDto = new MusicGuessRoundDto(
                    currentRound.roundId(),
                    state.currentRoundIndex() + 1,
                    state.totalRounds(),
                    currentRound.audioUrl(),
                    currentRound.previewStartMs(),
                    state.clipSeconds(),
                    state.roundEndsAtEpochMs(),
                    currentRound.options().stream()
                            .map(option -> new MusicGuessOptionDto(option.id(), option.label()))
                            .toList(),
                    viewerAnswer == null ? null : viewerAnswer.optionId(),
                    viewerAnswer == null ? null : viewerAnswer.correct(),
                    reveal ? reveal(currentRound) : null
            );
        }

        return new MusicGuessSessionResponse(
                state.sessionId(),
                state.roomCode(),
                state.mode(),
                state.status(),
                state.hostUserId(),
                state.hostDisplayName(),
                state.currentRoundIndex() < 0 ? 0 : state.currentRoundIndex() + 1,
                state.totalRounds(),
                state.optionCount(),
                state.clipSeconds(),
                state.roundDurationSeconds(),
                scoreboard(state),
                roundDto
        );
    }

    private MusicGuessAnswerResult buildAnswerResult(
            MusicGuessSessionState state,
            MusicGuessRoundState round,
            MusicGuessAnswerState answer,
            String userId,
            boolean roundComplete
    ) {
        MusicGuessPlayerState player = state.players().get(userId);
        return new MusicGuessAnswerResult(
                answer.correct(),
                answer.earnedPoints(),
                player == null ? 0 : player.score(),
                answer.optionId(),
                round.correctOptionId(),
                roundComplete,
                state.status() == MusicGuessSessionStatus.FINISHED,
                reveal(round)
        );
    }

    private List<MusicGuessScoreboardEntry> scoreboard(MusicGuessSessionState state) {
        return state.players().values().stream()
                .map(player -> new MusicGuessScoreboardEntry(
                        player.userId(),
                        player.displayName(),
                        player.score(),
                        player.answeredRounds(),
                        player.connected()
                ))
                .sorted(Comparator.comparingInt(MusicGuessScoreboardEntry::score).reversed())
                .toList();
    }

    private MusicGuessSongRevealDto reveal(MusicGuessRoundState round) {
        return new MusicGuessSongRevealDto(
                round.songId(),
                round.title(),
                round.artistName(),
                round.coverImageUrl(),
                round.audioUrl()
        );
    }

    private MusicGuessSessionState copySession(
            MusicGuessSessionState current,
            MusicGuessSessionStatus status,
            int currentRoundIndex,
            long roundEndsAtEpochMs,
            List<MusicGuessRoundState> rounds,
            Map<String, MusicGuessPlayerState> players
    ) {
        return sessionRepository.save(new MusicGuessSessionState(
                current.sessionId(),
                current.roomCode(),
                current.mode(),
                status,
                current.hostUserId(),
                current.hostDisplayName(),
                current.totalRounds(),
                current.optionCount(),
                current.clipSeconds(),
                current.roundDurationSeconds(),
                currentRoundIndex,
                roundEndsAtEpochMs,
                List.copyOf(rounds),
                new LinkedHashMap<>(players),
                current.version() + 1,
                current.createdAt()
        ));
    }

    private List<MusicGuessRoundState> createRounds(int totalRounds, int optionCount, int clipSeconds) {
        List<Song> songs = pickRandomPlayableSongs(Math.max(optionCount, Math.min(totalRounds * optionCount, 60)));
        if (songs.size() < optionCount) {
            throw new IllegalStateException("Cần ít nhất " + optionCount + " bài hát có audio để tạo game");
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<MusicGuessRoundState> rounds = new ArrayList<>();
        for (int i = 0; i < totalRounds; i++) {
            Song correct = songs.get(random.nextInt(songs.size()));
            List<Song> optionSongs = new ArrayList<>();
            optionSongs.add(correct);
            List<Song> distractorPool = new ArrayList<>(songs.stream()
                    .filter(song -> !Objects.equals(song.getId(), correct.getId()))
                    .toList());
            Collections.shuffle(distractorPool);
            optionSongs.addAll(distractorPool.subList(0, Math.min(optionCount - 1, distractorPool.size())));
            Collections.shuffle(optionSongs);

            String correctOptionId = null;
            List<MusicGuessOptionState> options = new ArrayList<>();
            for (Song optionSong : optionSongs) {
                String optionId = UUID.randomUUID().toString();
                if (Objects.equals(optionSong.getId(), correct.getId())) {
                    correctOptionId = optionId;
                }
                options.add(new MusicGuessOptionState(optionId, optionSong.getId(), optionLabel(optionSong)));
            }

            rounds.add(new MusicGuessRoundState(
                    UUID.randomUUID().toString(),
                    correct.getId(),
                    correct.getTitle(),
                    mainArtistName(correct),
                    correct.getSongUrl(),
                    correct.getImgUrl(),
                    previewStartMs(correct, clipSeconds),
                    correctOptionId,
                    options,
                    new LinkedHashMap<>()
            ));
        }
        return rounds;
    }

    private List<Song> pickRandomPlayableSongs(int targetCount) {
        long total = songRepository.countPlayableSongs();
        if (total <= 0) {
            return List.of();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        int attempts = 0;
        int maxAttempts = Math.max(targetCount * 8, 24);
        while (ids.size() < targetCount && attempts < maxAttempts) {
            attempts++;
            int page = (int) Math.min(Integer.MAX_VALUE, random.nextLong(total));
            List<Long> content = songRepository.findPlayableSongIds(PageRequest.of(page, 1)).getContent();
            if (!content.isEmpty()) {
                ids.add(content.getFirst());
            }
        }

        if (ids.size() < targetCount) {
            int fallbackSize = (int) Math.min(Math.max(targetCount * 2L, 20L), total);
            ids.addAll(songRepository.findPlayableSongIds(PageRequest.of(0, fallbackSize)).getContent());
        }

        Map<Long, Song> byId = songRepository.findSongsWithDetailsByIds(new ArrayList<>(ids)).stream()
                .collect(LinkedHashMap::new, (map, song) -> map.put(song.getId(), song), LinkedHashMap::putAll);

        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private int previewStartMs(Song song, int clipSeconds) {
        int duration = Math.max(song.getDuration(), clipSeconds);
        int maxStartSeconds = Math.max(0, duration - clipSeconds - 3);
        if (maxStartSeconds <= 0) {
            return 0;
        }
        return ThreadLocalRandom.current().nextInt(maxStartSeconds + 1) * 1000;
    }

    private String optionLabel(Song song) {
        return song.getTitle() + " - " + mainArtistName(song);
    }

    private String mainArtistName(Song song) {
        List<SongArtistRole> roles = song.getArtistRoles() == null ? List.of() : song.getArtistRoles();
        return roles.stream()
                .filter(role -> role.getRole() == ArtistRole.MAIN_ARTIST)
                .map(role -> role.getArtist().getName())
                .findFirst()
                .orElseGet(() -> roles.stream()
                        .findFirst()
                        .map(role -> role.getArtist().getName())
                        .orElse("Unknown Artist"));
    }

    private boolean isRoundComplete(MusicGuessRoundState round, Map<String, MusicGuessPlayerState> players) {
        long activePlayers = players.values().stream().filter(MusicGuessPlayerState::connected).count();
        return activePlayers > 0 && round.answers().size() >= activePlayers;
    }

    private int calculatePoints(long answeredAtEpochMs, long roundEndsAtEpochMs) {
        long remainingMs = Math.max(0L, roundEndsAtEpochMs - answeredAtEpochMs);
        return 500 + (int) Math.min(500, remainingMs / 40);
    }

    private MusicGuessSessionState findSession(String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phiên đoán nhạc"));
    }

    private void ensureParticipant(MusicGuessSessionState state, String userId) {
        if (!state.players().containsKey(userId)) {
            throw new AccessDeniedException("Bạn chưa tham gia phòng này");
        }
    }

    private void ensureHost(MusicGuessSessionState state, String userId) {
        if (!state.hostUserId().equals(userId)) {
            throw new AccessDeniedException("Chỉ chủ phòng mới được thực hiện thao tác này");
        }
    }

    private Actor currentActor() {
        User user = currentUserProvider.get();
        return new Actor(String.valueOf(user.getId()), user.getName());
    }

    private String generateRoomCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                code.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            String roomCode = code.toString();
            if (sessionRepository.findByRoomCode(roomCode).isEmpty()) {
                return roomCode;
            }
        }
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        int resolved = value == null ? fallback : value;
        return Math.max(min, Math.min(max, resolved));
    }

    private long nowPlusSeconds(int seconds) {
        return System.currentTimeMillis() + seconds * 1000L;
    }

    private record Actor(String userId, String displayName) {
    }
}
