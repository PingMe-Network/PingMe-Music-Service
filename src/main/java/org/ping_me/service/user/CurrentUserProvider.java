package org.ping_me.service.user;

import org.ping_me.model.user.User;

/**
 * Admin 8/19/2025
 **/
public interface CurrentUserProvider {
    User get();
}
