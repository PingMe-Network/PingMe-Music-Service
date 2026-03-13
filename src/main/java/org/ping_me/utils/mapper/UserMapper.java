package org.ping_me.utils.mapper;

import lombok.RequiredArgsConstructor;
import org.ping_me.dto.response.authentication.CurrentUserSessionResponse;
import org.ping_me.dto.response.user.DefaultUserResponse;
import org.ping_me.model.User;
import org.springframework.stereotype.Component;


/**
 * Admin 1/9/2026
 *
 **/
@Component
@RequiredArgsConstructor
public class UserMapper {


    public CurrentUserSessionResponse mapToCurrentUserSessionResponse(User user) {
        var res = new CurrentUserSessionResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getAvatarUrl(),
                user.getUpdatedAt().toString(),
                null
        );

        var roleName = user.getRole() != null ? user.getRole().getName() : "";
        res.setRoleName(roleName);
        return res;
    }

    public DefaultUserResponse mapToDefaultUserResponse(User user) {
        return DefaultUserResponse
                .builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .gender(user.getGender())
                .address(user.getAddress())
                .dob(user.getDob())
                .avatarUrl(user.getAvatarUrl())
                .accountStatus(user.getAccountStatus())
                .build();
    }

}
