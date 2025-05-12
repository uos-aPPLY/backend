package com.apply.diarypic.diary.repository;

import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // Query 임포트 추가
import org.springframework.data.repository.query.Param; // Param 임포트 추가

import java.time.LocalDate; // LocalDate 임포트 추가
import java.util.List;

public interface DiaryRepository extends JpaRepository<Diary, Long> {
    List<Diary> findByUserId(Long userId);

    List<Diary> findByUserAndIsFavoritedTrueOrderByDiaryDateDesc(User user);

    Page<Diary> findByUserOrderByDiaryDateDescCreatedAtDesc(User user, Pageable pageable);

    // --- 새로운 메소드 추가 ---
    long countByUser(User user); // 특정 사용자의 전체 일기 수

    // 특정 사용자의 특정 기간 동안 작성된 일기 수
    long countByUserAndDiaryDateBetween(User user, LocalDate startDate, LocalDate endDate);

    // JPQL을 사용하여 연도별, 월별 카운트 (더 효율적일 수 있음)
    @Query("SELECT COUNT(d) FROM Diary d WHERE d.user = :user AND FUNCTION('YEAR', d.diaryDate) = :year")
    long countByUserAndYear(@Param("user") User user, @Param("year") int year);

    @Query("SELECT COUNT(d) FROM Diary d WHERE d.user = :user AND FUNCTION('YEAR', d.diaryDate) = :year AND FUNCTION('MONTH', d.diaryDate) = :month")
    long countByUserAndYearAndMonth(@Param("user") User user, @Param("year") int year, @Param("month") int month);
}