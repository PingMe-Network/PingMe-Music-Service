package org.ping_me.websocket.auth;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class MusicSocketPrincipalMapper {

    public MusicSocketPrincipal extractUserPrincipal(Principal principal) {
        if (principal instanceof Authentication auth) {
            Object receivedPrincipal = auth.getPrincipal();
            if (receivedPrincipal instanceof MusicSocketPrincipal socketPrincipal) {
                return socketPrincipal;
            }
        }

        if (principal instanceof MusicSocketPrincipal socketPrincipal) {
            return socketPrincipal;
        }
        return null;
    }

    public Long extractUserId(Principal principal) {
        MusicSocketPrincipal user = extractUserPrincipal(principal);
        return user == null ? null : user.getId();
    }
}

