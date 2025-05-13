package com.apply.diarypic.keyword.repository;

import com.apply.diarypic.keyword.entity.Keyword;
import com.apply.diarypic.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    Optional<Keyword> findByNameAndUser(String name, User user);

    List<Keyword> findByUserOrderByNameAsc(User user);

    void deleteAllByUser(User user);
}