package org.ping_me.repository.jpa;

import org.jspecify.annotations.NonNull;
import org.ping_me.model.User;
import org.ping_me.model.constant.AccountStatus;
import org.ping_me.model.constant.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Admin 8/3/2025
 **/
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> getUserByEmail(String email);
}
