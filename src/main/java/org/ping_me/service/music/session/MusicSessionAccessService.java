package org.ping_me.service.music.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
@Slf4j
public class MusicSessionAccessService {

    private final RestClient coreServiceRestClient;

    @Cacheable(cacheNames = "music_friendship_check", key = "#hostUserId + ':' + #userId")
    public boolean canJoinSession(String hostUserId, String userId) {
        if (hostUserId == null || userId == null) {
            return false;
        }

        if (hostUserId.equals(userId)) {
            return true;
        }

        try {
            String uri = String.format("/core-service/users/%s/is-friend/%s", hostUserId, userId);
            log.info("Checking friendship at: {}", uri);

            Boolean response = coreServiceRestClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(Boolean.class);

            return Boolean.TRUE.equals(response);
        } catch (RestClientException ex) {
            log.warn("Không thể kiểm tra quan hệ bạn bè hostUserId={} userId={}: {}", hostUserId, userId,
                    ex.getMessage());
            return false;
        }
    }

    public java.util.List<Long> getFriendIds(String userId) {
        try {
            return coreServiceRestClient.get()
                    .uri("/core-service/users/{userId}/friend-ids", userId)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {
                    });
        } catch (Exception ex) {
            log.warn("Không thể lấy danh sách bạn bè cho userId={}: {}", userId, ex.getMessage());
            return java.util.List.of();
        }
    }
}
