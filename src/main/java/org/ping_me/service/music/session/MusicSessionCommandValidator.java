package org.ping_me.service.music.session;

import org.ping_me.dto.music.session.MusicSessionCommandType;
import org.ping_me.dto.music.session.payload.PlayPayload;
import org.ping_me.dto.music.session.payload.QueuePayload;
import org.ping_me.dto.music.session.payload.StartSessionPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MusicSessionCommandValidator {

    private final ObjectMapper objectMapper;

    public void validate(MusicSessionCommandType type, Object payload) {
        switch (type) {
            case START_SESSION -> validateStart(payload);
            case PLAY, PAUSE, SEEK -> validatePlayLike(payload);
            case ADD_TO_QUEUE -> validateAddToQueue(payload);
            case REMOVE_FROM_QUEUE -> validateRemoveFromQueue(payload);
            case PREV, NEXT, STOP_SESSION, JOIN_SESSION, LEAVE_SESSION -> {
                // no extra validation
            }
            default -> {
            }
        }
    }

    private void validateStart(Object payload) {
        StartSessionPayload dto = convertPayload(payload, StartSessionPayload.class);
        if (dto == null) return;
        if (dto.queue() != null && dto.queue().stream().anyMatch(trackId -> trackId == null || trackId.isBlank())) {
            throw new IllegalArgumentException("StartSessionPayload.queue must contain non-blank track ids");
        }
        if (dto.currentTrackId() != null && dto.currentTrackId().isBlank()) {
            throw new IllegalArgumentException("currentTrackId, if provided, must be non-blank");
        }
        if (dto.positionMs() != null && dto.positionMs() < 0) {
            throw new IllegalArgumentException("positionMs must be >= 0");
        }
    }

    private void validatePlayLike(Object payload) {
        PlayPayload dto = convertPayload(payload, PlayPayload.class);
        if (dto == null) return;
        Long pos = dto.positionMs();
        if (pos != null && pos < 0) {
            throw new IllegalArgumentException("positionMs must be >= 0");
        }
        String track = dto.currentTrackId();
        if (track != null && track.isBlank()) {
            throw new IllegalArgumentException("currentTrackId, if provided, must be non-blank");
        }
    }

    private void validateAddToQueue(Object payload) {
        QueuePayload dto = convertPayload(payload, QueuePayload.class);
        if (dto == null) return;
        List<String> queue = dto.queue();
        String trackId = dto.trackId();
        List<String> trackIds = dto.trackIds();
        boolean hasAny = (queue != null && !queue.isEmpty()) || (trackIds != null && !trackIds.isEmpty()) || (trackId != null && !trackId.isBlank());
        if (!hasAny) {
            throw new IllegalArgumentException("ADD_TO_QUEUE requires at least one of: queue, trackIds, trackId");
        }
    }

    private void validateRemoveFromQueue(Object payload) {
        QueuePayload dto = convertPayload(payload, QueuePayload.class);
        if (dto == null) return;
        String trackId = dto.trackId();
        List<String> trackIds = dto.trackIds();
        if ((trackId == null || trackId.isBlank()) && (trackIds == null || trackIds.isEmpty())) {
            throw new IllegalArgumentException("REMOVE_FROM_QUEUE requires either trackId or trackIds");
        }
    }

    private <T> T convertPayload(Object payload, Class<T> type) {
        return objectMapper.convertValue(payload == null ? java.util.Map.of() : payload, type);
    }
}

