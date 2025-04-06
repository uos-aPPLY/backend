package com.apply.diarypic.user.repository;

import com.apply.diarypic.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findBySnsProviderAndSnsUserId(String snsProvider, String snsUserId);
}
