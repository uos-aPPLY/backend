package com.apply.diarypic.keyword.repository;

import com.apply.diarypic.keyword.entity.PhotoKeyword;
import com.apply.diarypic.keyword.entity.PhotoKeywordId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PhotoKeywordRepository extends JpaRepository<PhotoKeyword, PhotoKeywordId> {
    List<PhotoKeyword> findByDiaryPhotoId(Long photoId);
    Optional<PhotoKeyword> findByDiaryPhotoIdAndKeywordId(Long photoId, Long keywordId);
    void deleteByDiaryPhotoIdAndKeywordId(Long photoId, Long keywordId);
}