package org.ping_me.repository.jpa;

import org.ping_me.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Admin 8/3/2025
 **/
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> getUserByEmail(String email);
}
