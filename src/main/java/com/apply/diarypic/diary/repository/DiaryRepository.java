package com.apply.diarypic.diary.repository;

import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Optional<Diary> findByIdAndUserAndDeletedAtIsNull(Long id, User user);
    List<Diary> findByUserIdAndDeletedAtIsNull(Long userId);

    @Query("SELECT d FROM Diary d WHERE d.user = :user AND d.deletedAt IS NULL ORDER BY d.diaryDate DESC, d.createdAt DESC")
    Page<Diary> findActiveDiariesByUser(@Param("user") User user, Pageable pageable);

    List<Diary> findByUserAndIsFavoritedTrueAndDeletedAtIsNullOrderByDiaryDateDesc(User user);
    Optional<Diary> findByIdAndUser(Long id, User user);
    List<Diary> findByUserId(Long userId);
    Page<Diary> findByUserAndDeletedAtIsNotNullOrderByDeletedAtDesc(User user, Pageable pageable);
    Optional<Diary> findByIdAndUserAndDeletedAtIsNotNull(Long id, User user);
    List<Diary> findAllByUserAndDeletedAtIsNotNull(User user);
    List<Diary> findAllByDeletedAtIsNotNullAndDeletedAtBefore(LocalDateTime cutoffDateTime);

    @Query("SELECT COUNT(d) FROM Diary d WHERE d.user = :user AND d.deletedAt IS NULL")
    long countByUserAndDeletedAtIsNull(@Param("user") User user);

    @Query("SELECT COUNT(d) FROM Diary d WHERE d.user = :user AND d.deletedAt IS NULL AND FUNCTION('YEAR', d.diaryDate) = :year")
    long countByUserAndYearAndDeletedAtIsNull(@Param("user") User user, @Param("year") int year);

    @Query("SELECT COUNT(d) FROM Diary d WHERE d.user = :user AND d.deletedAt IS NULL AND FUNCTION('YEAR', d.diaryDate) = :year AND FUNCTION('MONTH', d.diaryDate) = :month")
    long countByUserAndYearAndMonthAndDeletedAtIsNull(@Param("user") User user, @Param("year") int year, @Param("month") int month);

    @Query("SELECT COUNT(d) FROM Diary d WHERE d.user = :user AND d.deletedAt IS NULL AND d.diaryDate BETWEEN :startDate AND :endDate")
    long countByUserAndDiaryDateBetweenAndDeletedAtIsNull(@Param("user") User user, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * 사용자의 활성 일기 중에서 내용(content)에 특정 키워드가 포함된 일기를 검색합니다. (대소문자 구분 없음)
     * 검색 결과는 최신 일기 순서 (diaryDate DESC, createdAt DESC)로 정렬됩니다.
     */
    @Query("SELECT d FROM Diary d WHERE d.user = :user AND d.deletedAt IS NULL AND d.content LIKE CONCAT('%', :keyword, '%') ORDER BY d.diaryDate DESC, d.createdAt DESC")
    Page<Diary> findByUserAndContentContainingAndDeletedAtIsNull(
            @Param("user") User user,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}