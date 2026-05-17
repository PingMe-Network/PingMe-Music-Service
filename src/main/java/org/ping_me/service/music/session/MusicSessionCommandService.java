package org.ping_me.service.music.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ping_me.dto.music.session.MusicSessionCommandRequest;
import org.ping_me.dto.music.session.MusicSessionCommandType;
import org.ping_me.dto.music.session.MusicSessionEventType;
import org.ping_me.dto.music.session.MusicSessionState;
import org.ping_me.repository.music.session.MusicSessionRedisRepository;
import org.ping_me.websocket.FriendSessionEventPublisher;
import org.ping_me.websocket.MusicSessionEventPublisher;
import org.ping_me.websocket.auth.MusicSocketPrincipalMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.ping_me.dto.music.session.payload.PlayPayload;
import org.ping_me.dto.music.session.payload.QueuePayload;
import org.ping_me.dto.music.session.payload.StartSessionPayload;

@Service
@RequiredArgsConstructor
@Slf4j
public class MusicSessionCommandService {

    private final MusicSocketPrincipalMapper principalMapper;
    private final MusicSessionAccessService accessService;
    private final MusicSessionRedisRepository sessionRepository;
    private final MusicSessionEventPublisher eventPublisher;
    private final FriendSessionEventPublisher friendSessionEventPublisher;
    private final ObjectMapper objectMapper;
    private final MusicSessionCommandValidator commandValidator;
    private final MusicSessionRateLimiter rateLimiter;

    public void handleCommand(String hostUserId, MusicSessionCommandRequest request, Principal principal) {
        // validate envelope/shape first to give early feedback to client
        if (request != null && request.command() != null) {
            commandValidator.validate(request.command(), request.payload());
        }
        if (request == null || request.command() == null) {
            return;
        }

        String requesterUserId = extractRequesterUserId(principal);
        if (requesterUserId == null) {
            throw new AccessDeniedException("Yêu cầu xác thực");
        }

        if (!rateLimiter.tryConsume(hostUserId, requesterUserId)) {
            log.warn("Music command rate limited userId={} sessionId={} command={}",
                    requesterUserId, hostUserId, request.command());
            throw new IllegalArgumentException("Too many music session commands. Please slow down.");
        }

        boolean host = Objects.equals(hostUserId, requesterUserId);
        if (!host && !isSharedCommand(request.command())) {
            throw new AccessDeniedException("Bạn không có quyền thực thi lệnh này");
        }

        if (!accessService.canJoinSession(hostUserId, requesterUserId) &&
                !isKnownSessionParticipant(hostUserId, requesterUserId)) {
            throw new AccessDeniedException("Bạn không có quyền tham gia phiên nghe này");
        }

        validateCommandAgainstState(hostUserId, request.command());
        log.info("Music session command accepted userId={} sessionId={} command={} host={}",
                requesterUserId, hostUserId, request.command(), host);

        MusicSessionState state = switch (request.command()) {
            case START_SESSION -> handleStartSession(hostUserId, requesterUserId, request.payload());
            case JOIN_SESSION -> handleJoinSession(hostUserId, requesterUserId);
            case LEAVE_SESSION -> handleLeaveSession(hostUserId, requesterUserId);
            case PLAY -> handlePlay(hostUserId, request.payload());
            case PAUSE -> handlePause(hostUserId, request.payload());
            case SEEK -> handleSeek(hostUserId, request.payload());
            case NEXT -> handleNext(hostUserId);
            case PREV -> handlePrev(hostUserId, request.payload());
            case ADD_TO_QUEUE -> handleAddToQueue(hostUserId, request.payload());
            case REMOVE_FROM_QUEUE -> handleRemoveFromQueue(hostUserId, request.payload());
            case STOP_SESSION -> handleStopSession(hostUserId);
        };

        publish(hostUserId, state, request.command());
    }

    private MusicSessionState handleStartSession(String hostUserId, String requesterUserId, Object payload) {
        log.info("Starting music session userId={} sessionId={} command={}",
                requesterUserId, hostUserId, MusicSessionCommandType.START_SESSION);
        
        // Chỉ Host mới được START_SESSION (khởi tạo track/queue)
        if (!Objects.equals(hostUserId, requesterUserId)) {
            return handleJoinSession(hostUserId, requesterUserId);
        }

        return sessionRepository.update(hostUserId, current -> {
            StartSessionPayload dto = objectMapper.convertValue(payload == null ? Map.of() : payload, StartSessionPayload.class);
            List<String> incomingQueue = dto == null || dto.queue() == null ? List.of() : dto.queue();
            
            String currentTrackId = dto != null ? dto.currentTrackId() : current.currentTrackId();
            long positionMs = dto != null && dto.positionMs() != null ? dto.positionMs() : current.positionMs();
            boolean playing = dto != null && dto.isPlaying() != null ? dto.isPlaying() : current.isPlaying();
            long startedAtEpochMs = playing ? System.currentTimeMillis() : current.startedAtEpochMs();

            List<String> listeners = new ArrayList<>(current.activeListenerIds());
            if (!listeners.contains(requesterUserId)) {
                listeners.add(requesterUserId);
            }

            MusicSessionState next = current.withActiveListenerIds(List.copyOf(listeners))
                    .withEndingAfterCurrentTrack(false)
                    .withPlayback(playing, currentTrackId, positionMs, startedAtEpochMs);
            
            if (current.queue().isEmpty() && !incomingQueue.isEmpty()) {
                next = next.withQueue(incomingQueue);
            }
            
            return next;
        });
    }

    private MusicSessionState handleJoinSession(String hostUserId, String requesterUserId) {
        log.info("Joining music session userId={} sessionId={} command={}",
                requesterUserId, hostUserId, MusicSessionCommandType.JOIN_SESSION);
        return sessionRepository.addListener(hostUserId, requesterUserId);
    }

    private MusicSessionState handleLeaveSession(String hostUserId, String requesterUserId) {
        log.info("Leaving music session userId={} sessionId={} command={}",
                requesterUserId, hostUserId, MusicSessionCommandType.LEAVE_SESSION);
        return sessionRepository.removeListener(hostUserId, requesterUserId);
    }

    private MusicSessionState handlePlay(String hostUserId, Object payload) {
        PlayPayload dto = convertPayload(payload, PlayPayload.class);
        
        String trackId = (dto != null && dto.currentTrackId() != null) ? dto.currentTrackId() : null;
        log.info("Applying music playback command sessionId={} command={} trackId={}",
                hostUserId, MusicSessionCommandType.PLAY, trackId);

        return sessionRepository.update(hostUserId, current -> {
            boolean isNewTrack = trackId != null && !trackId.equals(current.currentTrackId());
            long targetPosition = (dto != null && dto.positionMs() != null) 
                    ? dto.positionMs() 
                    : (isNewTrack ? 0L : current.positionMs());

            MusicSessionState next = current.withPlayback(
                true,
                trackId != null ? trackId : current.currentTrackId(),
                targetPosition,
                System.currentTimeMillis()
            );
            log.debug("Music playback state updated sessionId={} command={} isPlaying={} trackId={} positionMs={}",
                    hostUserId, MusicSessionCommandType.PLAY, next.isPlaying(), next.currentTrackId(), next.positionMs());
            return next;
        });
    }

    private MusicSessionState handlePause(String hostUserId, Object payload) {
        PlayPayload dto = convertPayload(payload, PlayPayload.class);
        PlaybackTarget target = resolvePlaybackTarget(hostUserId, dto);
        return sessionRepository.update(hostUserId, current -> current.withPlayback(
                false,
                target.trackId() == null ? current.currentTrackId() : target.trackId(),
                target.positionMs(),
                current.startedAtEpochMs()
        ));
    }

    private MusicSessionState handleSeek(String hostUserId, Object payload) {
        PlayPayload dto = convertPayload(payload, PlayPayload.class);
        PlaybackTarget target = resolvePlaybackTarget(hostUserId, dto);
        return sessionRepository.update(hostUserId, current -> current.withPlayback(
                current.isPlaying(),
                target.trackId() == null ? current.currentTrackId() : target.trackId(),
                target.positionMs(),
                current.isPlaying() ? System.currentTimeMillis() : current.startedAtEpochMs()
        ));
    }

    private MusicSessionState handleNext(String hostUserId) {
        return sessionRepository.update(hostUserId, current -> {
            java.util.LinkedList<String> queue = new java.util.LinkedList<>(current.queue());
            String nextTrackId = queue.isEmpty() ? null : queue.removeFirst();
            return current
                    .withQueue(List.copyOf(queue))
                    .withPlayback(true, nextTrackId != null ? nextTrackId : current.currentTrackId(), 0L, System.currentTimeMillis());
        });
    }

    private MusicSessionState handlePrev(String hostUserId, Object payload) {
        QueuePayload dto = convertPayload(payload, QueuePayload.class);
        String trackId = dto == null ? null : dto.trackId();
        String use = trackId != null ? trackId : sessionRepository.getOrCreate(hostUserId).currentTrackId();
        return sessionRepository.update(hostUserId, current -> current.withPlayback(
                current.isPlaying(),
                use,
                0L,
                current.startedAtEpochMs()
        ));
    }

    private MusicSessionState handleAddToQueue(String hostUserId, Object payload) {
        QueuePayload dto = convertPayload(payload, QueuePayload.class);
        List<String> incoming = dto == null || dto.queue() == null ? List.of() : dto.queue();
        String singleTrackId = dto == null ? null : dto.trackId();
        return sessionRepository.update(hostUserId, current -> {
            List<String> queue = new ArrayList<>(current.queue());
            queue.addAll(incoming);
            if (singleTrackId != null) queue.add(singleTrackId);
            return current.withQueue(List.copyOf(queue));
        });
    }

    private MusicSessionState handleRemoveFromQueue(String hostUserId, Object payload) {
        QueuePayload dto = convertPayload(payload, QueuePayload.class);
        String trackId = dto == null ? null : dto.trackId();
        List<String> tracks = dto == null || dto.trackIds() == null ? List.of() : dto.trackIds();
        return sessionRepository.update(hostUserId, current -> {
            List<String> queue = new ArrayList<>(current.queue());
            if (trackId != null) queue.remove(trackId);
            if (!tracks.isEmpty()) queue.removeAll(tracks);
            return current.withQueue(List.copyOf(queue));
        });
    }

    private MusicSessionState handleStopSession(String hostUserId) {
        return sessionRepository.markEndingAfterCurrentTrack(hostUserId);
    }

    private void publish(String hostUserId, MusicSessionState state, MusicSessionCommandType commandType) {
        switch (commandType) {
            case START_SESSION, JOIN_SESSION, LEAVE_SESSION -> {
                eventPublisher.broadcastPresenceChanged(hostUserId, state.activeListenerIds());
                eventPublisher.broadcastSessionState(hostUserId, state);
                friendSessionEventPublisher.publishUpdated(
                        state,
                        commandType == MusicSessionCommandType.START_SESSION
                                ? MusicSessionEventType.FRIEND_SESSION_STARTED
                                : MusicSessionEventType.FRIEND_SESSION_UPDATED
                );
            }
            case PLAY, PAUSE, SEEK, NEXT, PREV -> {
                eventPublisher.broadcastPlaybackChanged(hostUserId, state);
                eventPublisher.broadcastSessionState(hostUserId, state);
                friendSessionEventPublisher.publishUpdated(state, MusicSessionEventType.FRIEND_SESSION_UPDATED);
            }
            case ADD_TO_QUEUE, REMOVE_FROM_QUEUE, STOP_SESSION -> {
                eventPublisher.broadcastQueueChanged(hostUserId, state.queue());
                eventPublisher.broadcastSessionState(hostUserId, state);
                if (commandType == MusicSessionCommandType.STOP_SESSION) {
                    friendSessionEventPublisher.publishEnded(hostUserId);
                } else {
                    friendSessionEventPublisher.publishUpdated(state, MusicSessionEventType.FRIEND_SESSION_UPDATED);
                }
            }
        }
    }

    private String extractRequesterUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        var user = principalMapper.extractUserPrincipal(principal);
        return user == null || user.getId() == null ? principal.getName() : String.valueOf(user.getId());
    }

    private boolean isSharedCommand(MusicSessionCommandType commandType) {
        return commandType == MusicSessionCommandType.JOIN_SESSION ||
               commandType == MusicSessionCommandType.LEAVE_SESSION ||
               commandType == MusicSessionCommandType.ADD_TO_QUEUE || 
               commandType == MusicSessionCommandType.REMOVE_FROM_QUEUE;
    }

    private boolean isKnownSessionParticipant(String hostUserId, String requesterUserId) {
        return sessionRepository.findByHostUserId(hostUserId)
                .map(state -> state.activeListenerIds().contains(requesterUserId))
                .orElse(false);
    }

    private void validateCommandAgainstState(String hostUserId, MusicSessionCommandType commandType) {
        MusicSessionState current = sessionRepository.findByHostUserId(hostUserId).orElse(null);
        if (current == null) {
            if (commandType != MusicSessionCommandType.START_SESSION && commandType != MusicSessionCommandType.JOIN_SESSION) {
                throw new IllegalArgumentException("Music session has not started");
            }
            return;
        }

        if (current.isEndingAfterCurrentTrack() &&
                commandType != MusicSessionCommandType.LEAVE_SESSION &&
                commandType != MusicSessionCommandType.STOP_SESSION) {
            throw new IllegalArgumentException("Music session is ending and cannot accept new commands");
        }
    }

    private <T> T convertPayload(Object payload, Class<T> type) {
        return objectMapper.convertValue(payload == null ? Map.of() : payload, type);
    }

    private PlaybackTarget resolvePlaybackTarget(String hostUserId, PlayPayload dto) {
        MusicSessionState current = sessionRepository.getOrCreate(hostUserId);
        String trackId = dto == null || dto.currentTrackId() == null ? current.currentTrackId() : dto.currentTrackId();
        long positionMs = dto == null || dto.positionMs() == null ? current.positionMs() : dto.positionMs();
        return new PlaybackTarget(trackId, positionMs);
    }

    private record PlaybackTarget(String trackId, long positionMs) {
    }
}
