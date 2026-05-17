package org.ping_me.service.music.session;

import lombok.RequiredArgsConstructor;
import org.ping_me.dto.music.session.FriendSessionSummary;
import org.ping_me.dto.music.session.MusicSessionState;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FriendSessionSummaryService {

    private final TrackSummaryCacheService trackSummaryCacheService;

    public FriendSessionSummary fromState(MusicSessionState state) {
        return new FriendSessionSummary(
                state.hostUserId(),
                trackSummaryCacheService.resolve(state.currentTrackId()),
                state.activeListenerIds().size(),
                state.activeListenerIds(),
                state.isPlaying(),
                state.isEndingAfterCurrentTrack(),
                state.version(),
                state.updatedAt()
        );
    }
}
