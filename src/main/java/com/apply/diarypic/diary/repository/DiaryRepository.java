package com.apply.diarypic.diary.repository;

import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.user.entity.User;
import org.springframework.data.domain.Page; // Page 임포트
import org.springframework.data.domain.Pageable; // Pageable 임포트
import org.springframework.data.jpa.repository.JpaRepository;
// import org.springframework.data.jpa.repository.Query; // @Query 사용 시
// import org.springframework.data.repository.query.Param; // @Param 사용 시

import java.util.List;

public interface DiaryRepository extends JpaRepository<Diary, Long> {
    List<Diary> findByUserId(Long userId); // UserService에서 사용 중

    // 사용자의 좋아요한 일기 목록 조회 (DiaryService에서 사용 중)
    List<Diary> findByUserAndIsFavoritedTrueOrderByDiaryDateDesc(User user);

    // 특정 사용자의 모든 일기 목록 페이징 조회 (새로 추가)
    // 정렬 기준: diaryDate 내림차순, 그리고 createdAt 내림차순 (같은 날짜면 최신 작성 순)
    Page<Diary> findByUserOrderByDiaryDateDescCreatedAtDesc(User user, Pageable pageable);
}