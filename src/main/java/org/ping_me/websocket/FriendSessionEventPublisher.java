package org.ping_me.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ping_me.dto.music.session.FriendSessionSummary;
import org.ping_me.dto.music.session.MusicSessionEventMessage;
import org.ping_me.dto.music.session.MusicSessionEventType;
import org.ping_me.dto.music.session.MusicSessionState;
import org.ping_me.service.music.session.FriendSessionSummaryService;
import org.ping_me.service.music.session.MusicSessionAccessService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FriendSessionEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final MusicSessionAccessService accessService;
    private final FriendSessionSummaryService summaryService;

    public void publishUpdated(MusicSessionState state, MusicSessionEventType eventType) {
        if (state == null || state.hostUserId() == null || state.currentTrackId() == null) {
            return;
        }

        FriendSessionSummary summary = summaryService.fromState(state);
        var friendIds = accessService.getFriendIds(state.hostUserId());
        for (Long friendId : friendIds) {
            String destination = MusicWebSocketDestinations.friendSessionsTopic(String.valueOf(friendId));
            messagingTemplate.convertAndSend(
                    destination,
                    new MusicSessionEventMessage(eventType.name(), summary, System.currentTimeMillis())
            );
        }
        log.debug("Published friend session event hostUserId={} eventType={} recipients={}",
                state.hostUserId(), eventType, friendIds.size());
    }

    public void publishEnded(String hostUserId) {
        if (hostUserId == null) {
            return;
        }

        var friendIds = accessService.getFriendIds(hostUserId);
        var payload = java.util.Map.of("hostUserId", hostUserId);
        for (Long friendId : friendIds) {
            messagingTemplate.convertAndSend(
                    MusicWebSocketDestinations.friendSessionsTopic(String.valueOf(friendId)),
                    new MusicSessionEventMessage(
                            MusicSessionEventType.FRIEND_SESSION_ENDED.name(),
                            payload,
                            System.currentTimeMillis()
                    )
            );
        }
        log.debug("Published friend session ended hostUserId={} recipients={}", hostUserId, friendIds.size());
    }
}
