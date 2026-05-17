package org.ping_me.dto.music.session;

public record TrackSummary(
        String trackId,
        String title,
        String artistName,
        String coverImageUrl
) {
    public static TrackSummary minimal(String trackId) {
        return new TrackSummary(trackId, null, null, null);
    }
}
