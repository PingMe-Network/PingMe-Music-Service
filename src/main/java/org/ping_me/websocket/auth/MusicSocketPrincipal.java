package org.ping_me.websocket.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.Principal;

/**
 * Principal attached to STOMP sessions after JWT authentication.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class MusicSocketPrincipal implements Principal {

    private Long id;
    private String email;
    private String username;

    @Override
    public String getName() {
        return id == null ? email : String.valueOf(id);
    }
}

