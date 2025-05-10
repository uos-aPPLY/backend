package com.apply.diarypic.photo.repository;

import com.apply.diarypic.diary.entity.DiaryPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PhotoRepository extends JpaRepository<DiaryPhoto, Long> {
    List<DiaryPhoto> findByDiaryIsNullAndUserId(Long userId);
}
