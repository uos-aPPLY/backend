package com.apply.diarypic.keyword.repository;

import com.apply.diarypic.keyword.entity.Keyword;
import com.apply.diarypic.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    // 특정 사용자의 개인 키워드 중에서 이름으로 찾기
    Optional<Keyword> findByNameAndUser(String name, User user);

    // 특정 사용자의 모든 개인 키워드 목록 조회
    List<Keyword> findByUserOrderByNameAsc(User user);
}