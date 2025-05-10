package com.apply.diarypic.album.repository;

import com.apply.diarypic.album.entity.Album;
import com.apply.diarypic.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, Long> {
    Optional<Album> findByNameAndUser(String name, User user);
    List<Album> findByUserOrderByCreatedAtDesc(User user); // 사용자 앨범 목록 조회 (최신순)
}