package com.apply.diarypic.diary.repository;

import com.apply.diarypic.diary.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryRepository extends JpaRepository<Diary, Long> {
    // 필요시 사용자별 일기 조회 등 추가 커스텀 쿼리 작성 가능
}
